package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.OrderFilterDto;
import com.innowise.orderservice.dto.OrderItemRequestDto;
import com.innowise.orderservice.dto.OrderRequestDto;
import com.innowise.orderservice.dto.OrderUpdateRequestDto;
import com.innowise.orderservice.dto.UserInfoDto;
import com.innowise.orderservice.exception.DuplicateOrderItemException;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.exception.OrderNotModifiableException;
import com.innowise.orderservice.exception.UserNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String EMAIL = "vasya@example.com";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private OrderService orderService;

    private UUID userId;
    private UserInfoDto user;
    private Item laptop;
    private Item mouse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = UserInfoDto.of(userId, EMAIL, "Vasya", "Pupkin", null);
        laptop = item(new BigDecimal("1200.50"));
        mouse = item(new BigDecimal("25.00"));
    }

    private Item item(BigDecimal price) {
        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setName("item-" + price);
        item.setPrice(price);
        return item;
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(userId);
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("10.00"));
        order.setDeleted(false);
        return order;
    }

    private OrderRequestDto request(OrderItemRequestDto... items) {
        return new OrderRequestDto(EMAIL, List.of(items));
    }

    @Test
    void createOrderComputesTotalFromStoredPrices() {
        when(userServiceClient.getUserByEmail(EMAIL)).thenReturn(user);
        when(itemRepository.findAllByIdIn(anyList())).thenReturn(List.of(laptop, mouse));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        orderService.createOrder(request(
                new OrderItemRequestDto(laptop.getId(), 2),
                new OrderItemRequestDto(mouse.getId(), 3)));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getTotalPrice()).isEqualByComparingTo("2476.00");
        assertThat(saved.getValue().getItems()).hasSize(2);
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(saved.getValue().getDeleted()).isFalse();
    }

    @Test
    void createOrderResolvesUserByEmail() {
        when(userServiceClient.getUserByEmail(EMAIL)).thenReturn(user);
        when(itemRepository.findAllByIdIn(anyList())).thenReturn(List.of(laptop));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        orderService.createOrder(request(new OrderItemRequestDto(laptop.getId(), 1)));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void createOrderLinksItemsBackToTheOrder() {
        when(userServiceClient.getUserByEmail(EMAIL)).thenReturn(user);
        when(itemRepository.findAllByIdIn(anyList())).thenReturn(List.of(laptop));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        orderService.createOrder(request(new OrderItemRequestDto(laptop.getId(), 1)));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        Order order = saved.getValue();
        assertThat(order.getItems()).allSatisfy(line -> assertThat(line.getOrder()).isSameAs(order));
    }

    @Test
    void createOrderFailsWhenUserIsUnknown() {
        when(userServiceClient.getUserByEmail(EMAIL)).thenThrow(new UserNotFoundException(EMAIL));

        assertThatThrownBy(() -> orderService.createOrder(
                request(new OrderItemRequestDto(laptop.getId(), 1))))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(orderRepository, itemRepository);
    }

    @Test
    void createOrderFailsWhenItemIsUnknown() {
        UUID missingId = UUID.randomUUID();
        when(userServiceClient.getUserByEmail(EMAIL)).thenReturn(user);
        when(itemRepository.findAllByIdIn(anyList())).thenReturn(List.of(laptop));

        assertThatThrownBy(() -> orderService.createOrder(request(
                new OrderItemRequestDto(laptop.getId(), 1),
                new OrderItemRequestDto(missingId, 1))))
                .isInstanceOf(ItemNotFoundException.class)
                .hasMessageContaining(missingId.toString());

        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsDuplicateItems() {
        when(userServiceClient.getUserByEmail(EMAIL)).thenReturn(user);

        assertThatThrownBy(() -> orderService.createOrder(request(
                new OrderItemRequestDto(laptop.getId(), 1),
                new OrderItemRequestDto(laptop.getId(), 2))))
                .isInstanceOf(DuplicateOrderItemException.class);

        verifyNoInteractions(orderRepository);
    }

    @Test
    void createOrderLoadsItemsInOneQuery() {
        when(userServiceClient.getUserByEmail(EMAIL)).thenReturn(user);
        when(itemRepository.findAllByIdIn(anyList())).thenReturn(List.of(laptop, mouse));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        orderService.createOrder(request(
                new OrderItemRequestDto(laptop.getId(), 1),
                new OrderItemRequestDto(mouse.getId(), 1)));

        verify(itemRepository, times(1)).findAllByIdIn(anyList());
    }

    @Test
    void getOrderByIdEnrichesWithUser() {
        Order order = order(OrderStatus.CREATED);
        when(orderRepository.findByIdWithItems(order.getId())).thenReturn(Optional.of(order));
        when(userServiceClient.getUserById(userId)).thenReturn(user);

        orderService.getOrderById(order.getId());

        verify(orderMapper).toDto(order, user);
    }

    @Test
    void getOrderByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdWithItems(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(id))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void searchOrdersAppliesSpecification() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Order> page = new PageImpl<>(List.of(order(OrderStatus.CREATED)));
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userServiceClient.getUserById(userId)).thenReturn(user);

        OrderFilterDto filter = new OrderFilterDto(userId,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now(),
                List.of(OrderStatus.CREATED));

        assertThat(orderService.searchOrders(filter, pageable)).hasSize(1);
        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void searchOrdersWorksWithoutFilters() {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThat(orderService.searchOrders(
                new OrderFilterDto(null, null, null, null), PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void searchOrdersFetchesEachUserOnce() {
        Page<Order> page = new PageImpl<>(List.of(
                order(OrderStatus.CREATED), order(OrderStatus.PAID), order(OrderStatus.SHIPPED)));
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userServiceClient.getUserById(userId)).thenReturn(user);

        orderService.searchOrders(new OrderFilterDto(null, null, null, null), PageRequest.of(0, 20));

        verify(userServiceClient, times(1)).getUserById(userId);
    }

    @Test
    void getOrdersByUserIdUsesTheNotDeletedQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.findAllByUserIdAndDeletedFalse(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(order(OrderStatus.CREATED))));
        when(userServiceClient.getUserById(userId)).thenReturn(user);

        assertThat(orderService.getOrdersByUserId(userId, pageable)).hasSize(1);
        verify(orderRepository).findAllByUserIdAndDeletedFalse(userId, pageable);
    }

    @Test
    void updateOrderReplacesItemsAndRecalculatesTotal() {
        Order order = order(OrderStatus.CREATED);
        OrderItem existing = new OrderItem();
        existing.setItem(mouse);
        existing.setQuantity(1);
        order.addItem(existing);

        when(orderRepository.findByIdWithItems(order.getId())).thenReturn(Optional.of(order));
        when(itemRepository.findAllByIdIn(anyList())).thenReturn(List.of(laptop));
        when(userServiceClient.getUserById(userId)).thenReturn(user);

        orderService.updateOrder(order.getId(), new OrderUpdateRequestDto(
                OrderStatus.PAID, List.of(new OrderItemRequestDto(laptop.getId(), 2))));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotalPrice()).isEqualByComparingTo("2401.00");
    }

    @Test
    void updateOrderRelaysOnDirtyChecking() {
        Order order = order(OrderStatus.CREATED);
        when(orderRepository.findByIdWithItems(order.getId())).thenReturn(Optional.of(order));
        when(itemRepository.findAllByIdIn(anyList())).thenReturn(List.of(laptop));
        when(userServiceClient.getUserById(userId)).thenReturn(user);

        orderService.updateOrder(order.getId(), new OrderUpdateRequestDto(
                OrderStatus.PAID, List.of(new OrderItemRequestDto(laptop.getId(), 1))));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderRejectsDeliveredOrder() {
        Order order = order(OrderStatus.DELIVERED);
        when(orderRepository.findByIdWithItems(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrder(order.getId(),
                new OrderUpdateRequestDto(OrderStatus.PAID,
                        List.of(new OrderItemRequestDto(laptop.getId(), 1)))))
                .isInstanceOf(OrderNotModifiableException.class);

        verifyNoInteractions(itemRepository);
    }

    @Test
    void updateOrderRejectsCancelledOrder() {
        Order order = order(OrderStatus.CANCELLED);
        when(orderRepository.findByIdWithItems(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrder(order.getId(),
                new OrderUpdateRequestDto(OrderStatus.PAID,
                        List.of(new OrderItemRequestDto(laptop.getId(), 1)))))
                .isInstanceOf(OrderNotModifiableException.class);
    }

    @Test
    void updateOrderThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdWithItems(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrder(id, new OrderUpdateRequestDto(
                OrderStatus.PAID, List.of(new OrderItemRequestDto(laptop.getId(), 1)))))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void deleteOrderIsSoft() {
        Order order = order(OrderStatus.CREATED);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));

        orderService.deleteOrder(order.getId());

        assertThat(order.getDeleted()).isTrue();
        verify(orderRepository, never()).delete(any(Order.class));
        verify(orderRepository, never()).deleteById(any());
    }

    @Test
    void deleteOrderThrowsWhenAlreadyDeleted() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.deleteOrder(id))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void deleteOrderDoesNotCallUserService() {
        Order order = order(OrderStatus.CREATED);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));

        orderService.deleteOrder(order.getId());

        verifyNoInteractions(userServiceClient);
    }
}