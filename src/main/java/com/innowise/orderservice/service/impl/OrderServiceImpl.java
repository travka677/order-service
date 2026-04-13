package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderFilterRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.dto.response.UserResponse;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderPersistenceService persistenceService;
    private final UserServiceClient userServiceClient;
    private final OrderMapper orderMapper;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        UserResponse user = userServiceClient.getUserByEmail(request.userEmail());
        if (user == null) {
            throw new IllegalStateException("User Service is unavailable, cannot create order");
        }
        return persistenceService.createOrder(request, user);
    }

    @Override
    public OrderResponse getOrderById(UUID id) {
        UUID userId = persistenceService.findUserIdByOrderId(id);
        UserResponse user = userServiceClient.getUserById(userId);
        return persistenceService.findOrderById(id, user);
    }

    @Override
    public Page<OrderResponse> getOrders(OrderFilterRequest filter, Pageable pageable) {
        Page<Order> orders = persistenceService.findOrders(filter, pageable);

        if (orders.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> userIds = orders.stream().map(Order::getUserId).distinct().toList();
        Map<UUID, UserResponse> userMap = userServiceClient.getUsersByIds(userIds).stream()
                .collect(Collectors.toMap(UserResponse::userId, u -> u));

        return orders.map(order -> orderMapper.toResponse(order, userMap.get(order.getUserId())));
    }

    @Override
    public Page<OrderResponse> getOrdersByUserId(UUID userId, String userEmail, Pageable pageable) {
        UserResponse user = userServiceClient.getUserById(userId);
        return persistenceService.findOrdersByUserId(userId, pageable)
                .map(order -> orderMapper.toResponse(order, user));
    }

    @Override
    public OrderResponse updateOrder(UUID id, UpdateOrderRequest request) {
        UserResponse user = userServiceClient.getUserByEmail(request.userEmail());
        if (user == null) {
            throw new IllegalStateException("User Service is unavailable, cannot update order");
        }
        return persistenceService.updateOrder(id, request, user);
    }

    @Override
    public void deleteOrder(UUID id) {
        persistenceService.deleteOrder(id);
    }
}
