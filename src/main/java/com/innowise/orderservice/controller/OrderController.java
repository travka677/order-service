package com.innowise.orderservice.controller;

import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderFilterRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /orders
     * Creates a new order.
     *
     * @param request request payload containing order details
     * @return 201 Created with OrderResponse (enriched with user data from User Service)
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /orders/{id}
     * Retrieves an order by its ID.
     *
     * @param id order identifier
     * @return 200 OK with OrderResponse
     */
    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    /**
     * Retrieves a paginated list of orders with optional date range and status filtering.
     *
     * @param filter   filtering criteria (query params)
     * @param pageable pagination and sorting configuration
     * @return 200 OK with a page of order responses
     */
    @GetMapping("/orders")
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @Valid OrderFilterRequest filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(orderService.getOrders(filter, pageable));
    }

    /**
     * GET /users/{userId}/orders?email=user@example.com
     * Retrieves all orders for a specific user.
     *
     * @param userId user identifier
     * @param email  user email used to look up the user in User Service (must be valid)
     * @param pageable pagination and sorting configuration
     * @return 200 OK with list of OrderResponse
     */
    @GetMapping("/users/{userId}/orders")
    public ResponseEntity<Page<OrderResponse>> getOrdersByUserId(
            @PathVariable UUID userId,
            @RequestParam @Email(message = "email must be valid") String email,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(orderService.getOrdersByUserId(userId, email, pageable));
    }

    /**
     * PUT /orders/{id}
     * Updates an existing order (status and/or items).
     *
     * @param id order identifier
     * @param request update payload
     * @return 200 OK with updated OrderResponse
     */
    @PutMapping("/orders/{id}")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderRequest request
    ) {
        return ResponseEntity.ok(orderService.updateOrder(id, request));
    }

    /**
     * DELETE /orders/{id}
     * Performs a soft delete of the order (marks it as deleted without removing from the database).
     *
     * @param id order identifier
     * @return 204 No Content
     */
    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
