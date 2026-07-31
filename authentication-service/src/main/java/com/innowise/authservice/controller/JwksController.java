package com.innowise.authservice.controller;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JWKSource<SecurityContext> jwkSource;

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> keys() throws Exception {
        return ((com.nimbusds.jose.jwk.source.ImmutableJWKSet<SecurityContext>) jwkSource)
                .getJWKSet()
                .toJSONObject();
    }
}