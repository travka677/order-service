package com.innowise.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
@Testcontainers
@ActiveProfiles("test")
class OrderControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.user-service.base-url", () -> "http://localhost:${wiremock.server.port}");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private OrderRepository orderRepository;

    private UUID itemId;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        itemRepository.deleteAll();

        Item item = itemRepository.save(Item.builder()
                .name("Test Item")
                .price(BigDecimal.valueOf(100.00))
                .build());
        itemId = item.getId();
    }

    @Test
    @DisplayName("Should create order and return 201 with enriched user data")
    void createOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "test@innowise.com";

        stubFor(get(urlPathEqualTo("/users"))
                .withQueryParam("email", equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"%s","email":"%s"}
                                """.formatted(userId, email))));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(userId, email))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    @DisplayName("Should create order with null user when User Service returns 500 (fallback)")
    void createOrderWhenUserServiceUnavailable() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "fallback@innowise.com";

        stubFor(get(urlPathEqualTo("/users"))
                .withQueryParam("email", equalTo(email))
                .willReturn(serverError()));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(userId, email))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    @DisplayName("Should return 400 when creating order without items")
    void createOrderWithoutItems() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(UUID.randomUUID());
        request.setUserEmail("valid@innowise.com");

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 404 when order does not exist")
    void getOrderByIdWhenNotFound() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return list of orders for existing user")
    void getOrdersByUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "daniil@innowise.com";

        stubFor(get(urlPathEqualTo("/users"))
                .withQueryParam("email", equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"%s","email":"%s"}
                                """.formatted(userId, email))));

        mockMvc.perform(MockMvcRequestBuilders.get("/orders/user/{userId}", userId)
                        .param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should return 400 when email query param is invalid")
    void getOrdersByUserIdWithInvalidEmail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/orders/user/{userId}", UUID.randomUUID())
                        .param("email", "not-an-email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when email query param is missing")
    void getOrdersByUserIdWithoutEmail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/orders/user/{userId}", UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent order")
    void deleteOrderWhenNotFound() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private CreateOrderRequest buildRequest(UUID userId, String email) {
        OrderItemRequest item = new OrderItemRequest();
        item.setItemId(itemId);
        item.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(userId);
        request.setUserEmail(email);
        request.setItems(List.of(item));
        return request;
    }
}
