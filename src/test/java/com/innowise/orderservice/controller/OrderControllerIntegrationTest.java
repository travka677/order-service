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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
@Testcontainers
@ActiveProfiles("test")
class OrderControllerIntegrationTest {

    private static final UUID PREDEFINED_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PREDEFINED_EMAIL = "test@innowise.com";

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
    void createOrder() {
        String expectedEmail = PREDEFINED_EMAIL;
        UUID expectedUserId = PREDEFINED_USER_ID;

        stubFor(get(urlPathEqualTo("/users"))
                .withQueryParam("email", equalTo(expectedEmail))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"userId":"%s","email":"%s","firstName":"John","lastName":"Doe"}
                                """.formatted(expectedUserId, expectedEmail))));

        CreateOrderRequest request = buildRequest(expectedUserId, expectedEmail);

        try {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.totalPrice").value(200.00))
                    .andExpect(jsonPath("$.user.email").value(expectedEmail));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should return 503 when User Service is unavailable (fallback)")
    void createOrderWhenUserServiceUnavailable() {
        String email = "fallback@innowise.com";

        stubFor(get(urlPathEqualTo("/users"))
                .withQueryParam("email", equalTo(email))
                .willReturn(serverError()));

        try {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest(PREDEFINED_USER_ID, email))))
                    .andExpect(status().isServiceUnavailable());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should return 400 when creating order without items")
    void createOrderWithoutItems() {
        CreateOrderRequest request = new CreateOrderRequest(PREDEFINED_EMAIL, null);

        try {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should return 404 when order does not exist")
    void getOrderByIdWhenNotFound() {
        try {
            mockMvc.perform(get("/orders/{id}", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should return list of orders for existing user")
    void getOrdersByUserId() {
        stubFor(get(urlPathEqualTo("/users/" + PREDEFINED_USER_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"userId":"%s","email":"%s","firstName":"John","lastName":"Doe"}
                                """.formatted(PREDEFINED_USER_ID, PREDEFINED_EMAIL))));

        try {
            mockMvc.perform(get("/users/{userId}/orders", PREDEFINED_USER_ID)
                            .param("email", PREDEFINED_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should return 400 when email query param is invalid")
    void getOrdersByUserIdWithInvalidEmail() {
        try {
            mockMvc.perform(get("/users/{userId}/orders", PREDEFINED_USER_ID)
                            .param("email", "not-an-email"))
                    .andExpect(status().isBadRequest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should return 400 when email query param is missing")
    void getOrdersByUserIdWithoutEmail() {
        try {
            mockMvc.perform(get("/users/{userId}/orders", PREDEFINED_USER_ID))
                    .andExpect(status().isBadRequest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent order")
    void deleteOrderWhenNotFound() {
        try {
            mockMvc.perform(delete("/orders/{id}", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CreateOrderRequest buildRequest(UUID userId, String email) {
        return new CreateOrderRequest(email, List.of(new OrderItemRequest(itemId, 2)));
    }
}
