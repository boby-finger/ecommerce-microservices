package com.innowise.userservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleConverterTest {

    private final JwtAuthenticationConverter converter = new JwtConfig().jwtAuthenticationConverter();

    private Jwt jwtWithRole(String role) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("vasya")
                .claim("userId", "0f7d3f2c-6b3e-4a2f-9c11-2b8f5a6d1e30");
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.build();
    }

    private Collection<? extends GrantedAuthority> authoritiesFor(String role) {
        return converter.convert(jwtWithRole(role)).getAuthorities();
    }

    @Test
    void mapsAdminRoleToPrefixedAuthority() {
        assertThat(authoritiesFor("ADMIN"))
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN");
    }

    @Test
    void mapsUserRoleToPrefixedAuthority() {
        assertThat(authoritiesFor("USER"))
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER");
    }

    @Test
    void grantsNothingWhenRoleClaimIsMissing() {
        assertThat(authoritiesFor(null))
                .extracting(GrantedAuthority::getAuthority)
                .noneMatch(authority -> authority.startsWith("ROLE_"));
    }
}