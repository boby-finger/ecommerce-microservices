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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
    private static final Set<OrderStatus> FINAL_STATUSES =
            EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClient userServiceClient;

    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto request) {
        UserInfoDto user = userServiceClient.getUserByEmail(request.userEmail());
        Order order = new Order();
        order.setUserId(user.id());
        order.setStatus(OrderStatus.CREATED);
        order.setDeleted(false);
        fillItems(order, request.items());
        Order saved = orderRepository.save(order);
        return orderMapper.toDto(saved, user);
    }

    public OrderResponseDto getOrderById(UUID id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return orderMapper.toDto(order, userServiceClient.getUserById(order.getUserId()));
    }

    public Page<OrderSummaryDto> searchOrders(OrderFilterDto filter, Pageable pageable) {
        List<Specification<Order>> specifications = Stream.of(
                        OrderSpecifications.notDeleted(),
                        OrderSpecifications.hasUserId(filter.userId()),
                        OrderSpecifications.createdBetween(filter.createdFrom(), filter.createdTo()),
                        OrderSpecifications.hasStatusIn(filter.statuses()))
                .filter(Objects::nonNull)
                .toList();

        return toSummaryPage(orderRepository.findAll(Specification.allOf(specifications), pageable));
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
        order.getItems().clear();
        orderRepository.flush();
        fillItems(order, request.items());
        return orderMapper.toDto(order, userServiceClient.getUserById(order.getUserId()));
    }

    @Transactional
    public void deleteOrder(UUID id) {
        Order order = orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.setDeleted(true);
    }

    private Page<OrderSummaryDto> toSummaryPage(Page<Order> orders) {
        Map<UUID, UserInfoDto> usersById = new HashMap<>();
        for (Order order : orders) {
            usersById.computeIfAbsent(order.getUserId(), userServiceClient::getUserById);
        }
        return orders.map(order -> orderMapper.toSummaryDto(order, usersById.get(order.getUserId())));
    }

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