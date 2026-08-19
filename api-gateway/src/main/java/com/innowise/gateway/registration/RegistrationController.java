package com.innowise.gateway.registration;

import com.innowise.gateway.dto.RegisterRequestDto;
import com.innowise.gateway.dto.TokenResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping
    public Mono<ResponseEntity<TokenResponseDto>> register(@Valid @RequestBody RegisterRequestDto request) {
        return registrationService.register(request)
                .map(tokens -> ResponseEntity.status(HttpStatus.CREATED).body(tokens));
    }
}