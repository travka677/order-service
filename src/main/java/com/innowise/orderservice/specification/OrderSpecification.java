package com.innowise.orderservice.specification;

import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

public class OrderSpecification {

    public static final String CREATED_AT = "createdAt";

    private OrderSpecification() {
    }

    public static Specification<Order> createdBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            if (from != null && to != null) return cb.between(root.get(CREATED_AT), from, to);
            if (from != null) return cb.greaterThanOrEqualTo(root.get(CREATED_AT), from);
            return cb.lessThanOrEqualTo(root.get(CREATED_AT), to);
        };
    }

    public static Specification<Order> hasStatuses(List<OrderStatus> statuses) {
        return (root, query, cb) ->
                statuses == null || statuses.isEmpty() ? null : root.get("status").in(statuses);

    }
}
