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
import java.util.ArrayList;
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
        order.setItems(new ArrayList<>());

        userResponse = new UserResponse(userId, "test@innowise.com", "John", "Doe");
        orderResponse = new OrderResponse(orderId, OrderStatus.PENDING, BigDecimal.TEN, List.of(), userResponse, null, null);
    }

    @Test
    @DisplayName("Should save and return order with calculated total price")
    void createOrder() {
        OrderItemRequest itemRequest = new OrderItemRequest(itemId, 2);
        CreateOrderRequest request = new CreateOrderRequest("test@innowise.com", List.of(itemRequest));

        Item item = new Item();
        item.setId(itemId);
        item.setPrice(BigDecimal.TEN);

        when(userServiceClient.getUserByEmail(request.userEmail())).thenReturn(userResponse);
        when(itemRepository.findAllById(List.of(itemId))).thenReturn(List.of(item));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderMapper.toResponse(any(Order.class), eq(userResponse))).thenReturn(orderResponse);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result).isEqualTo(orderResponse);
        verify(orderRepository).save(any(Order.class));
        verify(itemRepository).findAllById(List.of(itemId));
        verify(userServiceClient).getUserByEmail(request.userEmail());
    }

    @Test
    @DisplayName("Should return order when exists and not deleted")
    void getOrderById() {
        when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(userServiceClient.getUserById(userId)).thenReturn(userResponse);
        when(orderMapper.toResponse(order, userResponse)).thenReturn(orderResponse);

        OrderResponse result = orderService.getOrderById(orderId);

        assertThat(result).isEqualTo(orderResponse);
        verify(userServiceClient).getUserById(userId);
    }

    @Test
    @DisplayName("Should throw OrderNotFoundException when order is missing or deleted")
    void getOrderByIdNotFound() {
        when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    @DisplayName("Should return filtered page of orders enriched with user data")
    void getOrders() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of(order));
        OrderFilterRequest filter = new OrderFilterRequest(null, null, null);

        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(userServiceClient.getUsersByIds(List.of(userId))).thenReturn(List.of(userResponse));
        when(orderMapper.toResponse(order, userResponse)).thenReturn(orderResponse);

        Page<OrderResponse> result = orderService.getOrders(filter, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(orderResponse);
        verify(userServiceClient).getUsersByIds(List.of(userId));
    }

    @Test
    @DisplayName("Should return empty page when no orders match criteria")
    void getOrdersEmpty() {
        PageRequest pageable = PageRequest.of(0, 10);
        OrderFilterRequest filter = new OrderFilterRequest(null, null, null);

        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty());
        when(userServiceClient.getUsersByIds(List.of())).thenReturn(List.of());

        Page<OrderResponse> result = orderService.getOrders(filter, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Should return page of orders for specific user fetched by userId")
    void getOrdersByUserId() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Order> page = new PageImpl<>(List.of(order));

        when(orderRepository.findAllByUserIdAndDeletedFalse(userId, pageable)).thenReturn(page);
        when(userServiceClient.getUserById(userId)).thenReturn(userResponse);
        when(orderMapper.toResponse(order, userResponse)).thenReturn(orderResponse);

        Page<OrderResponse> result = orderService.getOrdersByUserId(userId, "test@innowise.com", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(orderResponse);
        verify(userServiceClient).getUserById(userId);
        verify(userServiceClient, never()).getUserByEmail(anyString());
    }

    @Test
    @DisplayName("Should update order status and return updated response")
    void updateOrder() {
        UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.CONFIRMED, null, "test@innowise.com");

        when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(userServiceClient.getUserById(userId)).thenReturn(userResponse);
        when(orderMapper.toResponse(order, userResponse)).thenReturn(orderResponse);

        OrderResponse result = orderService.updateOrder(orderId, request);

        assertThat(result).isEqualTo(orderResponse);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository).save(order);
        verify(userServiceClient).getUserById(userId);
        verify(userServiceClient, never()).getUserByEmail(anyString());
    }

    @Test
    @DisplayName("Should throw OrderNotFoundException on update if order doesn't exist")
    void updateOrderNotFound() {
        UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.CONFIRMED, null, "test@innowise.com");

        when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrder(orderId, new UpdateOrderRequest(OrderStatus.CONFIRMED, null, "test@innowise.com")))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
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
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }
}
