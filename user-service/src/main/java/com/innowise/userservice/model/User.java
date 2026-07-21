package com.innowise.userservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(name = "users")
public class User extends BaseEntity {
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "surname", nullable = false, length = 100)
    private String surname;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @OneToMany(mappedBy = "user", orphanRemoval = true, cascade = CascadeType.ALL)
    private List<PaymentCard> cards = new ArrayList<>();

    public void addPaymentCard(PaymentCard card){
        this.cards.add(card);
        card.setUser(this);
    }

    public void removePaymentCard(PaymentCard card){
        this.cards.remove(card);
        card.setUser(null);
    }
}
