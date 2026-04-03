package com.innowise.orderservice.dto.request;

import com.innowise.orderservice.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class OrderFilterRequest {
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    private List<OrderStatus> statuses;
}
