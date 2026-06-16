package com.innowise.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ItemResponse(
        UUID id,
        String name,
        BigDecimal price,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

