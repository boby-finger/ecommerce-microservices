package com.innowise.userservice.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class AccessTokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_REFRESH = "refresh";

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (TYPE_REFRESH.equals(token.getClaimAsString(CLAIM_TYPE))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "Refresh token cannot be used as access token", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}