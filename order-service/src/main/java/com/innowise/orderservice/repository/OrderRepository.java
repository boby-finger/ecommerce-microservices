package com.innowise.orderservice.repository;

import com.innowise.orderservice.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @Query("""
            select o from Order o left join fetch o.items line left join fetch line.item
            where o.id = :id and o.deleted = false
            """)
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    Page<Order> findAllByUserIdAndDeletedFalse(UUID userId, Pageable pageable);

    Optional<Order> findByIdAndDeletedFalse(UUID id);
}