package com.innowise.authservice.service;

import com.innowise.authservice.dto.ValidateResponseDto;
import com.innowise.authservice.model.Credentials;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_REFRESH = "refresh";
    public static final String ISSUER = "authentication-service";

    private static final long ACCESS_TOKEN_VALIDITY_MINUTES = 20;
    private static final long REFRESH_TOKEN_VALIDITY_DAYS = 7;

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public String generateAccessToken(Credentials credentials) {
        Instant now = Instant.now();
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES))
                .issuer(ISSUER)
                .subject(credentials.getUsername())
                .claim(CLAIM_USER_ID, credentials.getUserId().toString())
                .claim(CLAIM_ROLE, credentials.getRole().name())
                .build();
        return encode(claimsSet);
    }

    public String generateRefreshToken(Credentials credentials) {
        Instant now = Instant.now();
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(REFRESH_TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS))
                .claim(CLAIM_USER_ID, credentials.getUserId().toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .build();
        return encode(claimsSet);
    }

    public String encode(JwtClaimsSet claimsSet) {
        return jwtEncoder.encode(JwtEncoderParameters.from(claimsSet)).getTokenValue();
    }

    public ValidateResponseDto validateAccessToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);

            if (TYPE_REFRESH.equals(jwt.getClaimAsString(CLAIM_TYPE))) {
                return ValidateResponseDto.invalid("Refresh token cannot be used as an access token");
            }
            String rawUserId = jwt.getClaimAsString(CLAIM_USER_ID);
            if (rawUserId == null) {
                return ValidateResponseDto.invalid("Token has no " + CLAIM_USER_ID + " claim");
            }
            return new ValidateResponseDto(
                    true,
                    jwt.getSubject(),
                    UUID.fromString(rawUserId),
                    jwt.getClaimAsString(CLAIM_ROLE),
                    jwt.getExpiresAt(),
                    null
            );
        } catch (JwtException | IllegalArgumentException e) {
            return ValidateResponseDto.invalid("Token is invalid or expired");
        }
    }
}