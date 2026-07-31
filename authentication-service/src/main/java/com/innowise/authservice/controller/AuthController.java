package com.innowise.authservice.controller;

import com.innowise.authservice.dto.*;
import com.innowise.authservice.service.AuthService;
import com.innowise.authservice.service.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<TokenResponseDto> register(@Valid @RequestBody RegisterRequestDto registerRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(registerRequestDto));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        return ResponseEntity.status(HttpStatus.OK).body(authService.login(loginRequestDto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDto> refresh(@Valid @RequestBody RefreshRequestDto refreshRequestDto) {
        return ResponseEntity.status(HttpStatus.OK).body(authService.refreshTokens(refreshRequestDto));
    }

    @PostMapping("/validate")
    public ResponseEntity<ValidateResponseDto> validate(@Valid @RequestBody ValidateRequestDto validateRequestDto) {
        return ResponseEntity.status(HttpStatus.OK).body(jwtService.validateAccessToken(validateRequestDto.token()));
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TokenResponseDto> admin(@Valid @RequestBody AdminRequestDto adminRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createAdmin(adminRequestDto));
    }
}