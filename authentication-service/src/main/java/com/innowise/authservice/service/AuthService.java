package com.innowise.authservice.service;

import com.innowise.authservice.dto.*;
import com.innowise.authservice.exception.InvalidTokenException;
import com.innowise.authservice.exception.UsernameAlreadyExistsException;
import com.innowise.authservice.model.Credentials;
import com.innowise.authservice.model.RefreshToken;
import com.innowise.authservice.model.Role;
import com.innowise.authservice.repository.CredentialsRepository;
import com.innowise.authservice.repository.RefreshTokenRepository;
import com.innowise.authservice.user.UserServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final CredentialsRepository credentialsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserServiceClient userServiceClient;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;


    @Transactional
    public TokenResponseDto createAdmin(AdminRequestDto adminRequestDto) {
        return createUserWithRole(adminRequestDto.username(), adminRequestDto.password(), Role.ADMIN,
                new UserServiceCreateRequestDto(adminRequestDto.email(), adminRequestDto.name(),
                        adminRequestDto.surname(), adminRequestDto.birthDate()));
    }

    @Transactional
    public TokenResponseDto register(RegisterRequestDto registerRequestDto) {
        return createUserWithRole(registerRequestDto.username(), registerRequestDto.password(), Role.USER,
                new UserServiceCreateRequestDto(registerRequestDto.email(), registerRequestDto.name(),
                        registerRequestDto.surname(), registerRequestDto.birthDate()));
    }

    private TokenResponseDto createUserWithRole(String username, String password, Role role,
                                             UserServiceCreateRequestDto profileRequest) {
        if (credentialsRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }
        UUID userId = userServiceClient.createUser(profileRequest);

        Credentials credentials = new Credentials();
        credentials.setUsername(username);
        credentials.setPasswordHash(passwordEncoder.encode(password));
        credentials.setRole(role);
        credentials.setUserId(userId);
        credentialsRepository.save(credentials);

        return createTokens(credentials);
    }

    public TokenResponseDto createTokens(Credentials credentials){
        String accessToken = jwtService.generateAccessToken(credentials);
        String refreshToken = jwtService.generateRefreshToken(credentials);

        RefreshToken refreshTokenEntity = new RefreshToken();
        refreshTokenEntity.setToken(refreshToken);
        refreshTokenEntity.setCredentials(credentials);
        refreshTokenEntity.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshTokenEntity);
        return new TokenResponseDto(accessToken, refreshToken);
    }

    @Transactional
    public TokenResponseDto login(LoginRequestDto loginRequestDto){
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequestDto.username(), loginRequestDto.password())
        );
        Credentials credentials = credentialsRepository.findByUsername(loginRequestDto.username())
                .orElseThrow(()-> new InvalidTokenException("Credentials not found"));
        return  createTokens(credentials);
    }

    @Transactional
    public TokenResponseDto refreshTokens(RefreshRequestDto refreshRequestDto){
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshRequestDto.refreshToken())
                .orElseThrow(()-> new InvalidTokenException("Refresh token not found"));
        if(Instant.now().isAfter(refreshToken.getExpiresAt())){
            refreshTokenRepository.delete(refreshToken);
            throw new InvalidTokenException("Refresh token expired");
        }
        Credentials credentials = refreshToken.getCredentials();
        refreshTokenRepository.delete(refreshToken);
        return createTokens(credentials);
    }
}
