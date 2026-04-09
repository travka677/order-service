package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderFilterRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.dto.response.UserResponse;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderPersistenceService persistenceService;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID orderId;
    private UUID userId;
    private UUID itemId;
    private Order order;
    private UserResponse userResponse;
    private OrderResponse orderResponse;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        itemId = UUID.randomUUID();

        order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setDeleted(false);

        userResponse = new UserResponse(userId, "test@innowise.com", "John", "Doe");
        orderResponse = new OrderResponse(orderId, OrderStatus.PENDING, BigDecimal.TEN, List.of(), userResponse, null, null);
    }

    @Test
    @DisplayName("Should fetch user by email then delegate to persistence service")
    void createOrder() {
        CreateOrderRequest request = new CreateOrderRequest("test@innowise.com", List.of(new OrderItemRequest(itemId, 2)));

        when(userServiceClient.getUserByEmail(request.userEmail())).thenReturn(userResponse);
        when(persistenceService.createOrder(request, userResponse)).thenReturn(orderResponse);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result).isEqualTo(orderResponse);
        verify(userServiceClient).getUserByEmail(request.userEmail());
        verify(persistenceService).createOrder(request, userResponse);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when User Service returns null on create")
    void createOrderWhenUserServiceUnavailable() {
        CreateOrderRequest request = new CreateOrderRequest("test@innowise.com", List.of(new OrderItemRequest(itemId, 2)));

        when(userServiceClient.getUserByEmail(anyString())).thenReturn(null);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalStateException.class);

        verify(persistenceService, never()).createOrder(any(), any());
    }

    @Test
    @DisplayName("Should find order then fetch user by userId")
    void getOrderById() {
        when(persistenceService.findActiveOrder(orderId)).thenReturn(order);
        when(userServiceClient.getUserById(userId)).thenReturn(userResponse);
        when(orderMapper.toResponse(order, userResponse)).thenReturn(orderResponse);

        OrderResponse result = orderService.getOrderById(orderId);

        assertThat(result).isEqualTo(orderResponse);
        verify(persistenceService).findActiveOrder(orderId);
        verify(userServiceClient).getUserById(userId);
    }

    @Test
    @DisplayName("Should propagate OrderNotFoundException from persistence service")
    void getOrderByIdNotFound() {
        when(persistenceService.findActiveOrder(orderId))
                .thenThrow(new OrderNotFoundException("Order not found with id: " + orderId));

        assertThatThrownBy(() -> orderService.getOrderById(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    @DisplayName("Should fetch orders from DB then batch-load users")
    void getOrders() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of(order));
        OrderFilterRequest filter = new OrderFilterRequest(null, null, null);

        when(persistenceService.findOrders(filter, pageable)).thenReturn(page);
        when(userServiceClient.getUsersByIds(List.of(userId))).thenReturn(List.of(userResponse));
        when(orderMapper.toResponse(order, userResponse)).thenReturn(orderResponse);

        Page<OrderResponse> result = orderService.getOrders(filter, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(orderResponse);
        verify(userServiceClient).getUsersByIds(List.of(userId));
    }

    @Test
    @DisplayName("Should return empty page when no orders found")
    void getOrdersEmpty() {
        PageRequest pageable = PageRequest.of(0, 10);
        OrderFilterRequest filter = new OrderFilterRequest(null, null, null);

        when(persistenceService.findOrders(filter, pageable)).thenReturn(Page.empty());
        when(userServiceClient.getUsersByIds(List.of())).thenReturn(List.of());

        Page<OrderResponse> result = orderService.getOrders(filter, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Should fetch user by userId then delegate to persistence service")
    void getOrdersByUserId() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<OrderResponse> page = new PageImpl<>(List.of(orderResponse));

        when(userServiceClient.getUserById(userId)).thenReturn(userResponse);
        when(persistenceService.findOrdersByUserId(userId, userResponse, pageable)).thenReturn(page);

        Page<OrderResponse> result = orderService.getOrdersByUserId(userId, "test@innowise.com", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(orderResponse);
        verify(userServiceClient).getUserById(userId);
        verify(userServiceClient, never()).getUserByEmail(anyString());
        verify(persistenceService).findOrdersByUserId(userId, userResponse, pageable);
    }

    @Test
    @DisplayName("Should fetch user by email then delegate update to persistence service")
    void updateOrder() {
        UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.CONFIRMED, null, "test@innowise.com");

        when(userServiceClient.getUserByEmail(request.userEmail())).thenReturn(userResponse);
        when(persistenceService.updateOrder(orderId, request, userResponse)).thenReturn(orderResponse);

        OrderResponse result = orderService.updateOrder(orderId, request);

        assertThat(result).isEqualTo(orderResponse);
        verify(userServiceClient).getUserByEmail(request.userEmail());
        verify(persistenceService).updateOrder(orderId, request, userResponse);
        verify(persistenceService, never()).findActiveOrder(any());
    }

    @Test
    @DisplayName("Should throw IllegalStateException on update when User Service unavailable")
    void updateOrderWhenUserServiceUnavailable() {
        UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.CONFIRMED, null, "test@innowise.com");

        when(userServiceClient.getUserByEmail(anyString())).thenReturn(null);

        assertThatThrownBy(() -> orderService.updateOrder(orderId, request))
                .isInstanceOf(IllegalStateException.class);

        verify(persistenceService, never()).updateOrder(any(), any(), any());
    }

    @Test
    @DisplayName("Should propagate OrderNotFoundException on update if order missing")
    void updateOrderNotFound() {
        UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.CONFIRMED, null, "test@innowise.com");

        when(userServiceClient.getUserByEmail(anyString())).thenReturn(userResponse);
        when(persistenceService.updateOrder(orderId, request, userResponse))
                .thenThrow(new OrderNotFoundException("Order not found with id: " + orderId));

        assertThatThrownBy(() -> orderService.updateOrder(orderId, request))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    @DisplayName("Should delegate soft delete to persistence service")
    void deleteOrder() {
        orderService.deleteOrder(orderId);

        verify(persistenceService).deleteOrder(orderId);
    }

    @Test
    @DisplayName("Should propagate OrderNotFoundException on delete if order missing")
    void deleteOrderNotFound() {
        doThrow(new OrderNotFoundException("Order not found with id: " + orderId))
                .when(persistenceService).deleteOrder(orderId);

        assertThatThrownBy(() -> orderService.deleteOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }
}
