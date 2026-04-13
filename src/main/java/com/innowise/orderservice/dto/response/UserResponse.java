package com.innowise.orderservice.dto.response;

import java.util.UUID;

public record UserResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName
) {
}
