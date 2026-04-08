package com.innowise.orderservice.dto.response;

import com.innowise.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        OrderStatus status,
        BigDecimal totalPrice,
        List<OrderItemResponse> items,
        UserResponse user,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
