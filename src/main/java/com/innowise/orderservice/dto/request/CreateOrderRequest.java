package com.innowise.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(

        @NotEmpty(message = "Email must not be blank")
        @Email(message = "Email must be valid")
        String userEmail,

        @Valid
        @NotEmpty(message = "Order must contain at least one item")
        List<OrderItemRequest> items
) {
}
