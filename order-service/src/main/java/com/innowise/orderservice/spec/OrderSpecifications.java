package com.innowise.orderservice.spec;

import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> notDeleted() {
        return (root, query, builder) -> builder.isFalse(root.get("deleted"));
    }

    public static Specification<Order> hasUserId(UUID userId) {
        if (userId == null) {
            return null;
        }
        return (root, query, builder)
                -> builder.equal(root.get("userId"), userId);
    }

    public static Specification<Order> createdBetween(Instant from, Instant to) {
        if (from == null && to == null) {
            return null;
        }
        if (from == null) {
            return (root, query, builder)
                    -> builder.lessThanOrEqualTo(root.get("createdAt"), to);
        }
        if (to == null) {
            return (root, query, builder)
                    -> builder.greaterThanOrEqualTo(root.get("createdAt"), from);
        }
        return (root, query, builder)
                -> builder.between(root.get("createdAt"), from, to);
    }

    public static Specification<Order> hasStatusIn(Collection<OrderStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        return (root, query, builder)
                -> root.get("status").in(statuses);
    }
}