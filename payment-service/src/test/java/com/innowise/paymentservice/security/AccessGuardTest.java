package com.innowise.paymentservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessGuardTest {

    private final AccessGuard accessGuard = new AccessGuard();

    private Authentication tokenFor(UUID userId) {
        return tokenFor(userId, "USER");
    }

    private Authentication tokenFor(UUID userId, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("vasya")
                .claim("userId", userId.toString())
                .claim("role", role)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
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
        Authentication other = new UsernamePasswordAuthenticationToken("someone", null, List.of());
        assertThat(accessGuard.isSelf(UUID.randomUUID(), other)).isFalse();
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
    void isAdminIsTrueForAdminAuthority() {
        assertThat(accessGuard.isAdmin(tokenFor(UUID.randomUUID(), "ADMIN"))).isTrue();
    }

    @Test
    void isAdminIsFalseForRegularUser() {
        assertThat(accessGuard.isAdmin(tokenFor(UUID.randomUUID()))).isFalse();
    }

    @Test
    void isAdminIsFalseWhenAuthenticationIsMissing() {
        assertThat(accessGuard.isAdmin(null)).isFalse();
    }

    @Test
    void currentUserIdReturnsTheClaim() {
        UUID userId = UUID.randomUUID();
        assertThat(accessGuard.currentUserId(tokenFor(userId))).isEqualTo(userId);
    }

    @Test
    void currentUserIdIsNullWhenClaimIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("role", "USER")
                .build();
        assertThat(accessGuard.currentUserId(new JwtAuthenticationToken(jwt))).isNull();
    }

    @Test
    void requireCurrentUserIdReturnsTheClaim() {
        UUID userId = UUID.randomUUID();
        assertThat(accessGuard.requireCurrentUserId(tokenFor(userId))).isEqualTo(userId);
    }

    @Test
    void requireCurrentUserIdRejectsTokenWithoutUserIdClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("role", "USER")
                .build();

        assertThatThrownBy(() -> accessGuard.requireCurrentUserId(new JwtAuthenticationToken(jwt)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireCurrentUserIdRejectsMissingAuthentication() {
        assertThatThrownBy(() -> accessGuard.requireCurrentUserId(null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
