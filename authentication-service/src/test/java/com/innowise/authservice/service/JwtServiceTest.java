package com.innowise.authservice.service;

import com.innowise.authservice.dto.ValidateResponseDto;
import com.innowise.authservice.model.Credentials;
import com.innowise.authservice.model.Role;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static JwtService jwtService;
    private static JwtDecoder jwtDecoder;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("test").build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));

        jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        jwtService = new JwtService(new NimbusJwtEncoder(jwkSource), jwtDecoder);
    }

    private Credentials credentials(Role role) {
        Credentials credentials = new Credentials();
        credentials.setUsername("vasya");
        credentials.setPasswordHash("irrelevant");
        credentials.setRole(role);
        credentials.setUserId(UUID.randomUUID());
        return credentials;
    }

    @Test
    @DisplayName("An access token carries the subject, the user id and the role")
    void accessTokenCarriesIdentityClaims() {
        Credentials credentials = credentials(Role.ADMIN);

        Jwt jwt = jwtDecoder.decode(jwtService.generateAccessToken(credentials));

        assertThat(jwt.getSubject()).isEqualTo("vasya");
        assertThat(jwt.getClaimAsString("userId")).isEqualTo(credentials.getUserId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtService.ISSUER);
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("The role is written as a plain name, which is what user-service expects")
    void roleIsSerialisedAsAName() {
        Jwt jwt = jwtDecoder.decode(jwtService.generateAccessToken(credentials(Role.USER)));

        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("A refresh token is marked as such and carries no role")
    void refreshTokenIsMarked() {
        Jwt jwt = jwtDecoder.decode(jwtService.generateRefreshToken(credentials(Role.USER)));

        assertThat(jwt.getClaimAsString("type")).isEqualTo("refresh");
        assertThat(jwt.getClaimAsString("role")).isNull();
    }

    @Test
    @DisplayName("The refresh token outlives the access token")
    void refreshTokenLivesLonger() {
        Credentials credentials = credentials(Role.USER);

        Jwt access = jwtDecoder.decode(jwtService.generateAccessToken(credentials));
        Jwt refresh = jwtDecoder.decode(jwtService.generateRefreshToken(credentials));

        assertThat(refresh.getExpiresAt()).isAfter(access.getExpiresAt());
    }

    @Test
    @DisplayName("Validation accepts a freshly issued access token")
    void validationAcceptsAccessToken() {
        Credentials credentials = credentials(Role.USER);

        ValidateResponseDto response = jwtService.validateAccessToken(
                jwtService.generateAccessToken(credentials));

        assertThat(response.valid()).isTrue();
        assertThat(response.userId()).isEqualTo(credentials.getUserId());
        assertThat(response.role()).isEqualTo("USER");
        assertThat(response.username()).isEqualTo("vasya");
    }

    @Test
    @DisplayName("Validation refuses a refresh token")
    void validationRefusesRefreshToken() {
        ValidateResponseDto response = jwtService.validateAccessToken(
                jwtService.generateRefreshToken(credentials(Role.USER)));

        assertThat(response.valid()).isFalse();
        assertThat(response.reason()).contains("Refresh token");
    }

    @Test
    @DisplayName("Validation refuses an expired token")
    void validationRefusesExpiredToken() {
        Instant issued = Instant.now().minus(2, ChronoUnit.HOURS);
        String expired = jwtService.encode(JwtClaimsSet.builder()
                .issuer(JwtService.ISSUER)
                .subject("vasya")
                .issuedAt(issued)
                .expiresAt(issued.plus(20, ChronoUnit.MINUTES))
                .claim("userId", UUID.randomUUID().toString())
                .claim("role", "USER")
                .build());

        assertThat(jwtService.validateAccessToken(expired).valid()).isFalse();
    }

    @Test
    @DisplayName("Validation refuses something that is not a token at all")
    void validationRefusesGarbage() {
        assertThat(jwtService.validateAccessToken("not-a-jwt").valid()).isFalse();
    }

    @Test
    @DisplayName("Validation refuses a token signed by a different key")
    void validationRefusesForeignSignature() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherPair = generator.generateKeyPair();
        RSAKey otherKey = new RSAKey.Builder((RSAPublicKey) otherPair.getPublic())
                .privateKey(otherPair.getPrivate())
                .keyID("other")
                .build();
        JwtService foreign = new JwtService(
                new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey))), jwtDecoder);

        String foreignToken = foreign.generateAccessToken(credentials(Role.ADMIN));

        assertThat(jwtService.validateAccessToken(foreignToken).valid()).isFalse();
    }
}