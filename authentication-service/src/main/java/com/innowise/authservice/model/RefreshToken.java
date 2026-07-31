package com.innowise.authservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(name = "token", nullable = false, unique = true, length = 1000)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credentials_id", nullable = false)
    private Credentials credentials;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}