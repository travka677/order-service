package com.innowise.orderservice.dto.response;

import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        ItemResponse item,
        Integer quantity
) {
}
