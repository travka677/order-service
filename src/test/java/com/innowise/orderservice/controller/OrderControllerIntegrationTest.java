//package com.innowise.orderservice.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.github.tomakehurst.wiremock.client.WireMock;
//import com.innowise.orderservice.dto.request.CreateOrderRequest;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.springframework.test.web.servlet.MockMvc;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//import java.util.UUID;
//
//import static com.github.tomakehurst.wiremock.client.WireMock.*;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@AutoConfigureMockMvc
//@AutoConfigureWireMock(port = 0) // Случайный порт для WireMock
//@Testcontainers
//@ActiveProfiles("test")
//class OrderControllerIntegrationTest {
//
//    @Container
//    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
//
//    @DynamicPropertySource
//    static void configureProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url", postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//        // Используем плейсхолдер, который Spring Cloud WireMock заполнит сам
//        registry.add("app.user-service.base-url", () -> "http://localhost:${wiremock.server.port}");
//    }
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Test
//    @DisplayName("Should return 201 when order created and user enriched")
//    void createOrder() throws Exception {
//        UUID userId = UUID.randomUUID();
//
//        // Исправлено: withHeader вместо contentType
//        stubFor(get(urlPathEqualTo("/users/" + userId))
//                .willReturn(aResponse()
//                        .withStatus(200)
//                        .withHeader("Content-Type", "application/json")
//                        .withBody("{\"id\":\"" + userId + "\", \"email\":\"test@innowise.com\"}")));
//
//        CreateOrderRequest request = new CreateOrderRequest();
//        request.setUserId(userId);
//
//        mockMvc.perform(post("/orders")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isCreated())
//                .andExpect(jsonPath("$.userId").value(userId.toString()))
//                .andExpect(jsonPath("$.user.email").value("test@innowise.com"));
//    }
//
//    @Test
//    @DisplayName("Should return 404 when getting non-existent order")
//    void getOrderByIdNotFound() throws Exception {
//        mockMvc.perform(get("/orders/" + UUID.randomUUID()))
//                .andExpect(status().isNotFound());
//    }
//
//    @Test
//    @DisplayName("Should return list of orders for specific user via query param")
//    void getOrdersByUserId() throws Exception {
//        UUID userId = UUID.randomUUID();
//        String email = "daniil@innowise.com";
//
//        // Исправлено: withQueryParam + equalTo вместо param
//        stubFor(get(urlPathEqualTo("/users"))
//                .withQueryParam("email", equalTo(email))
//                .willReturn(aResponse()
//                        .withStatus(200)
//                        .withHeader("Content-Type", "application/json")
//                        .withBody("{\"id\":\"" + userId + "\", \"email\":\"" + email + "\"}")));
//
//        mockMvc.perform(get("/orders/user/" + userId)
//                        .param("email", email)) // В MockMvc .param() валиден
//                .andExpect(status().isOk());
//    }
//
//    @Test
//    @DisplayName("Should trigger fallback when User Service returns 500")
//    void createOrderUserServiceError() throws Exception {
//        UUID userId = UUID.randomUUID();
//
//        stubFor(get(urlPathEqualTo("/users/" + userId))
//                .willReturn(serverError()));
//
//        CreateOrderRequest request = new CreateOrderRequest();
//        request.setUserId(userId);
//
//        // Проверяем, что заказ все равно создается (Circuit Breaker/Fallback),
//        // но объект user будет пустым (null)
//        mockMvc.perform(post("/orders")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isCreated())
//                .andExpect(jsonPath("$.user").isEmpty());
//    }
//}