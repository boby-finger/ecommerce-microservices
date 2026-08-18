package com.innowise.paymentservice.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("access")
public class AccessGuard {

    public static final String CLAIM_USER_ID = "userId";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }

    public boolean isSelf(UUID userId, Authentication authentication) {
        UUID current = currentUserId(authentication);
        return current != null && current.equals(userId);
    }

    public UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String raw = jwt.getClaimAsString(CLAIM_USER_ID);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public UUID requireCurrentUserId(Authentication authentication) {
        UUID current = currentUserId(authentication);
        if (current == null) {
            throw new AccessDeniedException("Token does not carry a valid userId claim");
        }
        return current;
    }
}
