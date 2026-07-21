package com.innowise.userservice.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@NoArgsConstructor
@Setter
@Getter
@Table(name = "payment_cards")
public class PaymentCard extends BaseEntity{
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Column(name = "card_number", nullable = false, length = 19)
    private String cardNumber;

    @Column(name = "card_holder", nullable = false,  length = 100)
    private String cardHolder;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(name = "active", nullable = false)
    private Boolean active;
}
