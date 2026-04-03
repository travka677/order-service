package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderFilterRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.dto.response.UserResponse;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID orderId;
    private UUID userId;
    private Order order;
    private OrderResponse orderResponse;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();

        order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setDeleted(false);

        userResponse = new UserResponse();

        orderResponse = new OrderResponse(orderId, userId, OrderStatus.PENDING, null, List.of(), null, null, null);
    }

    @Test
    @DisplayName("Should save and return order with calculated total price")
    void createOrder() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(userId);
        request.setUserEmail("test@mail.com");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setItemId(UUID.randomUUID());
        itemRequest.setQuantity(2);

        request.setItems(List.of(itemRequest));

        Item item = new Item();
        item.setPrice(BigDecimal.TEN);

        when(itemRepository.findById(any())).thenReturn(Optional.of(item));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(userServiceClient.getUserByEmail(anyString())).thenReturn(userResponse);
        when(orderMapper.toResponse(any(Order.class), any(UserResponse.class)))
                .thenReturn(orderResponse);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result).isNotNull();
        verify(orderRepository).save(any(Order.class));
        verify(itemRepository).findById(any());
        verify(userServiceClient).getUserByEmail(anyString());
    }

    @Test
    @DisplayName("Should return order when exists and not deleted")
    void getOrderById() {
        when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(userServiceClient.getUserById(userId)).thenReturn(userResponse);
        when(orderMapper.toResponse(any(Order.class), any(UserResponse.class)))
                .thenReturn(orderResponse);

        OrderResponse result = orderService.getOrderById(orderId);

        assertThat(result.getId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("Should throw OrderNotFoundException when order is missing or deleted")
    void getOrderByIdNotFound() {
        when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Should return filtered page of orders")
    void getOrders() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of(order));

        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(userServiceClient.getUserById(any())).thenReturn(userResponse);
        when(orderMapper.toResponse(any(Order.class), any(UserResponse.class)))
                .thenReturn(orderResponse);

        Page<OrderResponse> result = orderService.getOrders(new OrderFilterRequest(null, null, null), pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Should return empty page when no orders match criteria")
    void getOrdersEmpty() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty());

        Page<OrderResponse> result = orderService.getOrders(new OrderFilterRequest(null, null, null), pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Should return orders for specific user and enrich with email data")
    void getOrdersByUserId() {
        String email = "test@innowise.com";

        when(orderRepository.findAllByUserIdAndDeletedFalse(userId)).thenReturn(List.of(order));
        when(userServiceClient.getUserByEmail(email)).thenReturn(userResponse);
        when(orderMapper.toResponse(any(Order.class), any(UserResponse.class)))
                .thenReturn(orderResponse);

        List<OrderResponse> result = orderService.getOrdersByUserId(userId, email);

        assertThat(result).isNotEmpty();
        verify(userServiceClient).getUserByEmail(email);
    }

    @Test
    @DisplayName("Should update order status and return updated response")
    void updateOrder() {
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setUserEmail("test@mail.com");

        when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(userServiceClient.getUserByEmail(anyString())).thenReturn(userResponse);
        when(orderMapper.toResponse(any(Order.class), any(UserResponse.class)))
                .thenReturn(orderResponse);

        OrderResponse result = orderService.updateOrder(orderId, request);

        assertThat(result).isNotNull();
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("Should throw OrderNotFoundException on update if order doesn't exist")
    void updateOrderNotFound() {
        when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrder(orderId, new UpdateOrderRequest()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Should perform soft delete on order")
    void deleteOrder() {
        when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.of(order));

        orderService.deleteOrder(orderId);

        assertThat(order.isDeleted()).isTrue();
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("Should throw OrderNotFoundException on delete if order missing")
    void deleteOrderNotFound() {
        when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.deleteOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
