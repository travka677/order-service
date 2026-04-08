package com.innowise.orderservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;


public record OrderItemRequest(

        @NotNull(message = "itemId is required")
        UUID itemId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity
) {
}
