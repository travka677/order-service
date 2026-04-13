package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderFilterRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.dto.response.UserResponse;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ComponentScan("com.innowise.orderservice.mapper")
@Import(OrderPersistenceService.class)
class OrderPersistenceServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderPersistenceService persistenceService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    private Item item;
    private UserResponse user;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        itemRepository.deleteAll();

        item = itemRepository.save(Item.builder()
                .name("Test Item")
                .price(BigDecimal.valueOf(50.00))
                .build());

        user = new UserResponse(USER_ID, "test@innowise.com", "John", "Doe");
    }

    @Test
    @DisplayName("Should persist order with correct total price and PENDING status")
    void createOrder() {
        CreateOrderRequest request = new CreateOrderRequest(
                "test@innowise.com",
                List.of(new OrderItemRequest(item.getId(), 3))
        );

        OrderResponse response = persistenceService.createOrder(request, user);

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
        assertThat(response.items()).hasSize(1);
    }

    @Test
    @DisplayName("Should throw ItemNotFoundException when item does not exist")
    void createOrderWithMissingItem() {
        CreateOrderRequest request = new CreateOrderRequest(
                "test@innowise.com",
                List.of(new OrderItemRequest(UUID.randomUUID(), 1))
        );

        assertThatThrownBy(() -> persistenceService.createOrder(request, user))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    @DisplayName("Should return userId for existing active order")
    void findUserIdByOrderId() {
        Order order = saveOrder();
        UUID foundUserId = persistenceService.findUserIdByOrderId(order.getId());

        assertThat(foundUserId).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Should throw OrderNotFoundException when getting userId for non-existent order")
    void findUserIdByOrderIdNotFound() {
        UUID randomId = UUID.randomUUID();

        assertThatThrownBy(() -> persistenceService.findUserIdByOrderId(randomId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(randomId.toString());
    }

    @Test
    @DisplayName("Should return paginated Order entities matching filter")
    void findOrders() {
        saveOrder();
        saveOrder();

        PageRequest pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        OrderFilterRequest filter = new OrderFilterRequest(null, null, List.of(OrderStatus.PENDING));

        Page<Order> result = persistenceService.findOrders(filter, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).allMatch(o -> o.getStatus() == OrderStatus.PENDING);
        assertThat(result.getContent()).allMatch(o -> o.getUserId().equals(USER_ID));
    }

    @Test
    @DisplayName("Should return empty page when no orders match filter")
    void findOrdersEmpty() {
        saveOrder();

        PageRequest pageable = PageRequest.of(0, 10);
        OrderFilterRequest filter = new OrderFilterRequest(null, null, List.of(OrderStatus.CANCELLED));

        Page<Order> result = persistenceService.findOrders(filter, pageable);

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Should return only non-deleted orders for given userId")
    void findOrdersByUserId() {
        saveOrder();
        Order deleted = saveOrder();
        deleted.setDeleted(true);
        orderRepository.save(deleted);

        PageRequest pageable = PageRequest.of(0, 10);
        Page<Order> result = persistenceService.findOrdersByUserId(USER_ID, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).allMatch(o -> o.getUserId().equals(USER_ID));
    }

    @Test
    @DisplayName("Should update order status")
    void updateOrderStatus() {
        Order order = saveOrder();
        UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.CONFIRMED, null, "test@innowise.com");

        OrderResponse response = persistenceService.updateOrder(order.getId(), request, user);

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Should update order items and recalculate total")
    void updateOrderItems() {
        Order order = saveOrder();
        UpdateOrderRequest request = new UpdateOrderRequest(
                null,
                List.of(new OrderItemRequest(item.getId(), 5)),
                "test@innowise.com"
        );

        OrderResponse response = persistenceService.updateOrder(order.getId(), request, user);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(250.00));
    }

    @Test
    @DisplayName("Should throw OrderNotFoundException when updating non-existent order")
    void updateOrderNotFound() {
        UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.CONFIRMED, null, "test@innowise.com");
        UUID nonExistentId = UUID.randomUUID();

        assertThatThrownBy(() -> persistenceService.updateOrder(nonExistentId, request, user))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Should soft delete order by setting deleted flag")
    void deleteOrder() {
        Order order = saveOrder();

        persistenceService.deleteOrder(order.getId());

        Order deleted = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("Should throw OrderNotFoundException when deleting non-existent order")
    void deleteOrderNotFound() {
        UUID nonExistentId = UUID.randomUUID();

        assertThatThrownBy(() -> persistenceService.deleteOrder(nonExistentId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    private Order saveOrder() {
        return orderRepository.save(Order.builder()
                .userId(USER_ID)
                .status(OrderStatus.PENDING)
                .totalPrice(BigDecimal.valueOf(100.00))
                .items(new ArrayList<>())
                .build());
    }
}
