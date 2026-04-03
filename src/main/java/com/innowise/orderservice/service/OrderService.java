package com.innowise.orderservice.service;

import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderFilterRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse getOrderById(UUID id);

    Page<OrderResponse> getOrders(OrderFilterRequest filter, Pageable pageable);

    List<OrderResponse> getOrdersByUserId(UUID userId, String userEmail);

    OrderResponse updateOrder(UUID id, UpdateOrderRequest request);

    void deleteOrder(UUID id);
}
