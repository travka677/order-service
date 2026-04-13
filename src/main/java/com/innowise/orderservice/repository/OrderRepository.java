package com.innowise.orderservice.repository;

import com.innowise.orderservice.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = {"items", "items.item"})
    Optional<Order> findByIdAndDeletedFalse(UUID id);

    @EntityGraph(attributePaths = {"items", "items.item"})
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.item"})
    Page<Order> findAllByUserIdAndDeletedFalse(UUID userId, Pageable pageable);
}
