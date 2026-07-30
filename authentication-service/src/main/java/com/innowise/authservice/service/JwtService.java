package com.innowise.authservice.service;

import com.innowise.authservice.model.Credentials;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class JwtService {
    private static final long ACCESS_TOKEN_VALIDITY_MINUTES = 20;
    private static final long REFRESH_TOKEN_VALIDITY_DAYS = 7;

    private final JwtEncoder jwtEncoder;

    public String generateAccessToken(Credentials credentials) {
        Instant now = Instant.now();
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES))
                .issuer("authentication-service")
                .subject(credentials.getUsername())
                .claim("userId", credentials.getUserId().toString())
                .claim("role", credentials.getRole())
                .build();
        return encode(claimsSet);
    }

    public String generateRefreshToken(Credentials credentials) {
        Instant now = Instant.now();
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer("authentication-service")
                .issuedAt(now)
                .expiresAt(now.plus(REFRESH_TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS))
                .claim("userId", credentials.getUserId().toString())
                .claim("type", "refresh")
                .build();
        return encode(claimsSet);
    }

    public String encode(JwtClaimsSet claimsSet) {
        return jwtEncoder.encode(JwtEncoderParameters.from(claimsSet)).getTokenValue();
    }
}
