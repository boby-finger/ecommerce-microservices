package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.OrderFilterDto;
import com.innowise.orderservice.dto.OrderItemRequestDto;
import com.innowise.orderservice.dto.OrderRequestDto;
import com.innowise.orderservice.dto.OrderResponseDto;
import com.innowise.orderservice.dto.OrderSummaryDto;
import com.innowise.orderservice.dto.OrderUpdateRequestDto;
import com.innowise.orderservice.dto.UserInfoDto;
import com.innowise.orderservice.exception.DuplicateOrderItemException;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.exception.OrderNotModifiableException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.spec.OrderSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    /**
     * Once an order is delivered or cancelled it is a historical record. Editing it would
     * silently rewrite what a customer was charged for.
     */
    private static final Set<OrderStatus> FINAL_STATUSES =
            EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClient userServiceClient;

    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto request) {
        // Resolved first: if the user does not exist there is no point touching the database,
        // and the remote call must not happen while a transaction holds a connection open.
        UserInfoDto user = userServiceClient.getUserByEmail(request.userEmail());

        Order order = new Order();
        order.setUserId(user.id());
        order.setStatus(OrderStatus.CREATED);
        order.setDeleted(false);

        fillItems(order, request.items());

        // One save: cascade = ALL persists the lines together with the order.
        Order saved = orderRepository.save(order);
        return orderMapper.toDto(saved, user);
    }

    public OrderResponseDto getOrderById(UUID id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return orderMapper.toDto(order, userServiceClient.getUserById(order.getUserId()));
    }

    public Page<OrderSummaryDto> searchOrders(OrderFilterDto filter, Pageable pageable) {
        // allOf drops the nulls that the specification methods return for absent filters,
        // so only notDeleted() is guaranteed to be part of every query.
        Specification<Order> specification = Specification.allOf(
                OrderSpecifications.notDeleted(),
                OrderSpecifications.hasUserId(filter.userId()),
                OrderSpecifications.createdBetween(filter.createdFrom(), filter.createdTo()),
                OrderSpecifications.hasStatusIn(filter.statuses())
        );
        return toSummaryPage(orderRepository.findAll(specification, pageable));
    }

    public Page<OrderSummaryDto> getOrdersByUserId(UUID userId, Pageable pageable) {
        return toSummaryPage(orderRepository.findAllByUserIdAndDeletedFalse(userId, pageable));
    }

    @Transactional
    public OrderResponseDto updateOrder(UUID id, OrderUpdateRequestDto request) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (FINAL_STATUSES.contains(order.getStatus())) {
            throw new OrderNotModifiableException(id, order.getStatus());
        }

        order.setStatus(request.status());

        // orphanRemoval turns clearing the list into deletes of the old rows. Hibernate runs
        // orphan removals before inserts, so replacing a line with one for the same item does
        // not collide with the unique constraint on (order_id, item_id).
        order.getItems().clear();
        fillItems(order, request.items());

        // No explicit save: the entity is managed inside the transaction and flushed on commit.
        return orderMapper.toDto(order, userServiceClient.getUserById(order.getUserId()));
    }

    /**
     * Soft delete. The flag is set by hand rather than through @SQLDelete because the order
     * cascades REMOVE to its lines: a real delete call would wipe the lines and leave a
     * flagged order with nothing in it.
     */
    @Transactional
    public void deleteOrder(UUID id) {
        Order order = orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.setDeleted(true);
    }

    /**
     * One remote call per distinct user rather than per order: a page of twenty orders from
     * three customers costs three calls, not twenty.
     */
    private Page<OrderSummaryDto> toSummaryPage(Page<Order> orders) {
        Map<UUID, UserInfoDto> usersById = new HashMap<>();
        for (Order order : orders) {
            usersById.computeIfAbsent(order.getUserId(), userServiceClient::getUserById);
        }
        return orders.map(order -> orderMapper.toSummaryDto(order, usersById.get(order.getUserId())));
    }

    /**
     * Builds the lines and the total from the prices stored in the database. The client sends
     * item ids and quantities only, so it cannot influence what the order costs.
     */
    private void fillItems(Order order, List<OrderItemRequestDto> requestedItems) {
        Set<UUID> seen = new HashSet<>();
        for (OrderItemRequestDto requested : requestedItems) {
            if (!seen.add(requested.itemId())) {
                throw new DuplicateOrderItemException(requested.itemId());
            }
        }

        List<UUID> itemIds = requestedItems.stream().map(OrderItemRequestDto::itemId).toList();
        Map<UUID, Item> itemsById = itemRepository.findAllByIdIn(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        if (itemsById.size() != seen.size()) {
            Set<UUID> missing = new HashSet<>(seen);
            missing.removeAll(itemsById.keySet());
            throw new ItemNotFoundException(missing);
        }

        BigDecimal totalPrice = BigDecimal.ZERO;
        for (OrderItemRequestDto requested : requestedItems) {
            Item item = itemsById.get(requested.itemId());

            OrderItem orderItem = new OrderItem();
            orderItem.setItem(item);
            orderItem.setQuantity(requested.quantity());
            order.addItem(orderItem);

            totalPrice = totalPrice.add(
                    item.getPrice().multiply(BigDecimal.valueOf(requested.quantity())));
        }
        order.setTotalPrice(totalPrice);
    }
}