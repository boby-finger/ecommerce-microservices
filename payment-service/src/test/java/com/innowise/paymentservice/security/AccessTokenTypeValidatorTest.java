package com.innowise.paymentservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenTypeValidatorTest {

    private final AccessTokenTypeValidator validator = new AccessTokenTypeValidator();

    private Jwt jwtWithType(String type) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("userId", "0f7d3f2c-6b3e-4a2f-9c11-2b8f5a6d1e30");
        if (type != null) {
            builder.claim("type", type);
        }
        return builder.build();
    }

    @Test
    void rejectsRefreshToken() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithType("refresh"));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .extracting("errorCode")
                .containsExactly("invalid_token");
    }

    @Test
    void acceptsAccessToken() {
        assertThat(validator.validate(jwtWithType(null)).hasErrors()).isFalse();
    }

    @Test
    void acceptsTokenWithUnrelatedTypeClaim() {
        assertThat(validator.validate(jwtWithType("access")).hasErrors()).isFalse();
    }
}
