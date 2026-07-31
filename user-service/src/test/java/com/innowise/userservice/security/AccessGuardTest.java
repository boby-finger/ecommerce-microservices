package com.innowise.userservice.security;

import com.innowise.userservice.repository.PaymentCardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessGuardTest {

    @Mock
    private PaymentCardRepository paymentCardRepository;

    @InjectMocks
    private AccessGuard accessGuard;

    private Authentication tokenFor(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("vasya")
                .claim("userId", userId.toString())
                .claim("role", "USER")
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void isSelfIsTrueForOwnId() {
        UUID userId = UUID.randomUUID();
        assertThat(accessGuard.isSelf(userId, tokenFor(userId))).isTrue();
    }

    @Test
    void isSelfIsFalseForForeignId() {
        assertThat(accessGuard.isSelf(UUID.randomUUID(), tokenFor(UUID.randomUUID()))).isFalse();
    }

    @Test
    void isSelfIsFalseWhenAuthenticationIsMissing() {
        assertThat(accessGuard.isSelf(UUID.randomUUID(), null)).isFalse();
    }

    @Test
    void isSelfIsFalseWhenPrincipalIsNotAJwt() {
        Authentication internal = new UsernamePasswordAuthenticationToken("authentication-service", null, List.of());
        assertThat(accessGuard.isSelf(UUID.randomUUID(), internal)).isFalse();
    }

    @Test
    void isSelfIsFalseWhenTokenHasNoUserIdClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("vasya")
                .claim("role", "USER")
                .build();
        assertThat(accessGuard.isSelf(UUID.randomUUID(), new JwtAuthenticationToken(jwt))).isFalse();
    }

    @Test
    void isSelfIsFalseWhenUserIdClaimIsNotAUuid() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("vasya")
                .claim("userId", "definitely-not-a-uuid")
                .build();
        assertThat(accessGuard.isSelf(UUID.randomUUID(), new JwtAuthenticationToken(jwt))).isFalse();
    }

    @Test
    void ownsCardIsTrueForOwnCard() {
        UUID userId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        when(paymentCardRepository.findUserIdByCardId(cardId)).thenReturn(Optional.of(userId));

        assertThat(accessGuard.ownsCard(cardId, tokenFor(userId))).isTrue();
    }

    @Test
    void ownsCardIsFalseForForeignCard() {
        UUID cardId = UUID.randomUUID();
        when(paymentCardRepository.findUserIdByCardId(cardId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThat(accessGuard.ownsCard(cardId, tokenFor(UUID.randomUUID()))).isFalse();
    }

    @Test
    void ownsCardIsFalseWhenCardDoesNotExist() {
        UUID cardId = UUID.randomUUID();
        when(paymentCardRepository.findUserIdByCardId(cardId)).thenReturn(Optional.empty());

        assertThat(accessGuard.ownsCard(cardId, tokenFor(UUID.randomUUID()))).isFalse();
    }
}