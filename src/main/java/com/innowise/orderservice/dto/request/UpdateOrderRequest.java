package com.innowise.orderservice.dto.request;

import com.innowise.orderservice.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateOrderRequest(
        OrderStatus status,

        @Valid
        List<OrderItemRequest> items,

        @NotEmpty(message = "Email must not be blank")
        @Email(message = "Email must be valid")
        String userEmail
) {
}
