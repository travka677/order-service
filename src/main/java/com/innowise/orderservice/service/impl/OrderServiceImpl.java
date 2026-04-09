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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        UserResponse user = userServiceClient.getUserByEmail(request.userEmail());

        if (user == null) {
            throw new IllegalStateException("User Service is unavailable, cannot create order");
        }

        Order order = Order.builder()
                .userId(user.userId())
                .status(OrderStatus.PENDING)
                .totalPrice(BigDecimal.ZERO)
                .build();

        List<OrderItem> orderItems = buildOrderItems(request.items(), order);
        order.setItems(new ArrayList<>(orderItems));
        order.setTotalPrice(calculateTotal(orderItems));

        Order saved = orderRepository.save(order);
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

        Page<Order> orders = orderRepository.findAll(orderSpec, pageable);

        List<UUID> userIds = orders.stream()
                .map(Order::getUserId)
                .distinct()
                .toList();

        Map<UUID, UserResponse> userMap = userServiceClient.getUsersByIds(userIds).stream()
                .collect(Collectors.toMap(UserResponse::userId, u -> u));

        return orders.map(order -> orderMapper.toResponse(order, userMap.get(order.getUserId())));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByUserId(UUID userId, String userEmail, Pageable pageable) {
        UserResponse user = userServiceClient.getUserById(userId);

        return orderRepository.findAllByUserIdAndDeletedFalse(userId, pageable)
                .map(order -> orderMapper.toResponse(order, user));
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(UUID id, UpdateOrderRequest request) {
        Order order = findActiveOrder(id);

        if (request.status() != null) {
            order.setStatus(request.status());
        }

        if (request.items() != null) {
            Map<UUID, OrderItem> existingMap = order.getItems().stream()
                    .collect(Collectors.toMap(oi -> oi.getItem().getId(), oi -> oi));

            Map<UUID, OrderItemRequest> requestedMap = request.items().stream()
                    .collect(Collectors.toMap(OrderItemRequest::itemId, r -> r));

            order.getItems().removeIf(oi -> !requestedMap.containsKey(oi.getItem().getId()));

            List<UUID> newItemIds = requestedMap.keySet().stream()
                    .filter(itemId -> !existingMap.containsKey(itemId))
                    .toList();

            Map<UUID, Item> newItemMap = itemRepository.findAllById(newItemIds).stream()
                    .collect(Collectors.toMap(Item::getId, i -> i));

            if (newItemMap.size() != newItemIds.size()) {
                throw new ItemNotFoundException("Some items from the list were not found");
            }

            requestedMap.forEach((itemId, req) -> {
                if (existingMap.containsKey(itemId)) {
                    existingMap.get(itemId).setQuantity(req.quantity());
                } else {
                    order.getItems().add(OrderItem.builder()
                            .order(order)
                            .item(newItemMap.get(itemId))
                            .quantity(req.quantity())
                            .build());
                }
            });

            order.setTotalPrice(calculateTotal(order.getItems()));
        }

        Order updated = orderRepository.save(order);
        UserResponse user = userServiceClient.getUserById(updated.getUserId());
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
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));
    }

    private List<OrderItem> buildOrderItems(List<OrderItemRequest> requests, Order order) {
        List<UUID> itemIds = requests.stream()
                .map(OrderItemRequest::itemId)
                .toList();

        List<Item> items = itemRepository.findAllById(itemIds);

        if (items.size() != itemIds.size()) {
            throw new ItemNotFoundException("Some items from the list were not found");
        }

        Map<UUID, Item> itemMap = items.stream()
                .collect(Collectors.toMap(Item::getId, item -> item));

        return new ArrayList<>(requests.stream()
                .map(req -> OrderItem.builder()
                        .order(order)
                        .item(itemMap.get(req.itemId()))
                        .quantity(req.quantity())
                        .build())
                .toList());
    }

    private BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(oi -> oi.getItem().getPrice()
                        .multiply(BigDecimal.valueOf(oi.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
