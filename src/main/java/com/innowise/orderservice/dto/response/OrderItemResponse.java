package com.innowise.orderservice.dto.response;

import lombok.Data;

import java.util.UUID;

@Data
public class OrderItemResponse {
    private UUID id;
    private ItemResponse item;
    private Integer quantity;
}
