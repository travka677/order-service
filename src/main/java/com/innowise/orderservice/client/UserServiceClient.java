package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.response.UserResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

@Slf4j
@Component
public class UserServiceClient {

    private static final String USERS_PATH = "/users";
    private static final String CB_NAME = "userService";

    private final WebClient webClient;

    public UserServiceClient(WebClient.Builder builder, @Value("${app.user-service.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getUserByEmailFallback")
    public UserResponse getUserByEmail(String email) {
        log.info("Fetching user by email: {}", email);
        return webClient.get()
                .uri(b -> b.path(USERS_PATH).queryParam("email", email).build())
                .retrieve()
                .bodyToMono(UserResponse.class)
                .block();
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getUserByIdFallback")
    public UserResponse getUserById(UUID userId) {
        log.info("Fetching user by userId: {}", userId);
        return webClient.get()
                .uri(USERS_PATH + "/{id}", userId)
                .retrieve()
                .bodyToMono(UserResponse.class)
                .block();
    }

    @SuppressWarnings("unused")
    private UserResponse getUserByEmailFallback(String email, Throwable ex) {
        logFallback("email=" + email, ex);
        return null;
    }

    @SuppressWarnings("unused")
    private UserResponse getUserByIdFallback(UUID userId, Throwable ex) {
        logFallback("userId=" + userId, ex);
        return null;
    }

    private void logFallback(String identifier, Throwable ex) {
        if (ex instanceof WebClientResponseException wcre) {
            log.warn("User Service HTTP {} for {}: {}",
                    wcre.getStatusCode(), identifier, wcre.getMessage());
        } else {
            log.warn("User Service unavailable for {}. Cause: {}", identifier, ex.getMessage());
        }
    }
}
