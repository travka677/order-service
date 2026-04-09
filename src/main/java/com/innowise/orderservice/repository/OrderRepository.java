package com.innowise.orderservice.repository;

import com.innowise.orderservice.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>,
        JpaSpecificationExecutor<Order> {

    Optional<Order> findByIdAndDeletedFalse(UUID id);

    Page<Order> findAllByUserIdAndDeletedFalse(UUID userId, Pageable pageable);
}
