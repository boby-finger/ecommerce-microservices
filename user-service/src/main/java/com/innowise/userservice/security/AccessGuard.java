package com.innowise.userservice.security;

import com.innowise.userservice.repository.PaymentCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("access")
@RequiredArgsConstructor
public class AccessGuard {

    public static final String CLAIM_USER_ID = "userId";

    private final PaymentCardRepository paymentCardRepository;

    public boolean isSelf(UUID userId, Authentication authentication) {
        UUID current = currentUserId(authentication);
        return current != null && current.equals(userId);
    }

    public boolean ownsCard(UUID cardId, Authentication authentication) {
        UUID current = currentUserId(authentication);
        if (current == null || cardId == null) {
            return false;
        }
        return paymentCardRepository.findUserIdByCardId(cardId)
                .map(current::equals)
                .orElse(false);
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
}