package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderFilterRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.dto.response.UserResponse;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderItem;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.service.OrderService;
import com.innowise.orderservice.specification.OrderSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClient userServiceClient;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = Order.builder()
                .status(OrderStatus.PENDING)
                .totalPrice(BigDecimal.ZERO)
                .build();

        List<OrderItem> orderItems = buildOrderItems(request.items(), order);
        order.setItems(orderItems);
        order.setTotalPrice(calculateTotal(orderItems));

        Order saved = orderRepository.save(order);

        UserResponse user = userServiceClient.getUserByEmail(request.userEmail());
        return orderMapper.toResponse(saved, user);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        Order order = findActiveOrder(id);

        UserResponse user = userServiceClient.getUserById(order.getUserId());
        return orderMapper.toResponse(order, user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(OrderFilterRequest filter, Pageable pageable) {
        Specification<Order> orderSpec = OrderSpecification
                .createdBetween(filter.createdFrom(), filter.createdTo())
                .and(OrderSpecification.hasStatuses(filter.statuses()));

        return orderRepository.findAll(orderSpec, pageable)
                .map(order -> {
                    UserResponse user = userServiceClient.getUserById(order.getUserId());
                    return orderMapper.toResponse(order, user);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(UUID userId, String userEmail) {
        UserResponse user = userServiceClient.getUserByEmail(userEmail);

        return orderRepository.findAllByUserIdAndDeletedFalse(userId)
                .stream()
                .map(order -> orderMapper.toResponse(order, user))
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(UUID id, UpdateOrderRequest request) {
        Order order = findActiveOrder(id);

        if (request.getStatus() != null) {
            order.setStatus(request.getStatus());
        }

        if (request.getItems() != null) {
            order.getItems().clear();
            List<OrderItem> newItems = buildOrderItems(request.getItems(), order);
            order.getItems().addAll(newItems);
            order.setTotalPrice(calculateTotal(newItems));
        }

        Order updated = orderRepository.save(order);
        UserResponse user = userServiceClient.getUserByEmail(request.getUserEmail());
        return orderMapper.toResponse(updated, user);
    }

    @Override
    @Transactional
    public void deleteOrder(UUID id) {
        Order order = findActiveOrder(id);
        order.setDeleted(true);
        orderRepository.save(order);
        log.info("Order soft-deleted: {}", id);
    }

    private Order findActiveOrder(UUID id) {
        return orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with userId " + id));
    }

    private List<OrderItem> buildOrderItems(List<OrderItemRequest> requests, Order order) {
        List<OrderItem> result = new ArrayList<>();
        for (OrderItemRequest req : requests) {
            Item item = itemRepository.findById(req.getItemId())
                    .orElseThrow(() -> new ItemNotFoundException("Item not found with userId " + req.getItemId()));

            result.add(OrderItem.builder()
                    .order(order)
                    .item(item)
                    .quantity(req.getQuantity())
                    .build());
        }
        return result;
    }

    private BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(oi -> oi.getItem().getPrice()
                        .multiply(BigDecimal.valueOf(oi.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
