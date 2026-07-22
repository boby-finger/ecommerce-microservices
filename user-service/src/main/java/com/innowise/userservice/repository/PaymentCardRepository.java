package com.innowise.userservice.repository;

import com.innowise.userservice.model.PaymentCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentCardRepository extends JpaRepository<PaymentCard, UUID>{
    long countCardsByUserId(UUID userId);

    @Query(value = "select * from payment_cards where user_id = :user_id", nativeQuery = true)
    List<PaymentCard> findAllByUserId(@Param("user_id") UUID user_id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PaymentCard c set c.active = :active, c.updatedAt = :now where c.id = :card_id")
    int updateActiveStatusCardById(@Param("card_id") UUID card_id,
                       @Param("now") Instant now,
                       @Param("active") Boolean active);
}
