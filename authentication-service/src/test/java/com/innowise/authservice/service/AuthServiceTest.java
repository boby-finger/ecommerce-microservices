package com.innowise.authservice.service;

import com.innowise.authservice.dto.LoginRequestDto;
import com.innowise.authservice.dto.RefreshRequestDto;
import com.innowise.authservice.dto.RegisterRequestDto;
import com.innowise.authservice.dto.TokenResponseDto;
import com.innowise.authservice.exception.InvalidTokenException;
import com.innowise.authservice.exception.UsernameAlreadyExistsException;
import com.innowise.authservice.model.Credentials;
import com.innowise.authservice.model.RefreshToken;
import com.innowise.authservice.model.Role;
import com.innowise.authservice.repository.CredentialsRepository;
import com.innowise.authservice.repository.RefreshTokenRepository;
import com.innowise.authservice.user.UserServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CredentialsRepository credentialsRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private RegisterRequestDto registerRequest() {
        return new RegisterRequestDto("vasya", "pass12345", "Vasya", "Pupkin",
                java.time.LocalDate.of(1999, 1, 1), "vasya@example.com");
    }

    private Credentials credentials() {
        Credentials credentials = new Credentials();
        credentials.setUsername("vasya");
        credentials.setPasswordHash("hashed");
        credentials.setRole(Role.USER);
        credentials.setUserId(UUID.randomUUID());
        return credentials;
    }

    @Test
    @DisplayName("Registration stores the hash, not the password")
    void registrationHashesThePassword() {
        when(credentialsRepository.existsByUsername("vasya")).thenReturn(false);
        when(userServiceClient.createUser(any())).thenReturn(UUID.randomUUID());
        when(passwordEncoder.encode("pass12345")).thenReturn("hashed");
        when(jwtService.generateAccessToken(any())).thenReturn("access");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh");

        TokenResponseDto response = authService.register(registerRequest());

        ArgumentCaptor<Credentials> saved = ArgumentCaptor.forClass(Credentials.class);
        verify(credentialsRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(response.accessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("A taken username stops registration before user-service is called")
    void registrationRejectsTakenUsername() {
        when(credentialsRepository.existsByUsername("vasya")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(userServiceClient, never()).createUser(any());
        verify(credentialsRepository, never()).save(any());
    }

    @Test
    @DisplayName("An admin is created with the ADMIN role")
    void adminIsCreatedWithAdminRole() {
        when(credentialsRepository.existsByUsername("boss")).thenReturn(false);
        when(userServiceClient.createUser(any())).thenReturn(UUID.randomUUID());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(jwtService.generateAccessToken(any())).thenReturn("access");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh");

        authService.createAdmin(new com.innowise.authservice.dto.AdminRequestDto(
                "boss", "pass12345", "Big", "Boss",
                java.time.LocalDate.of(1990, 1, 1), "boss@example.com"));

        ArgumentCaptor<Credentials> saved = ArgumentCaptor.forClass(Credentials.class);
        verify(credentialsRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Bad credentials propagate from the authentication manager")
    void loginPropagatesBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("vasya", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("An unknown refresh token is refused")
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshTokens(new RefreshRequestDto("unknown")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("An expired refresh token is refused and deleted")
    void refreshDeletesExpiredToken() {
        RefreshToken expired = new RefreshToken();
        expired.setToken("expired");
        expired.setCredentials(credentials());
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByToken("expired")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refreshTokens(new RefreshRequestDto("expired")))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository).delete(expired);
    }

    @Test
    @DisplayName("A valid refresh token is exchanged and cannot be reused")
    void refreshRotatesTheToken() {
        RefreshToken stored = new RefreshToken();
        stored.setToken("valid");
        stored.setCredentials(credentials());
        stored.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByToken("valid")).thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(any())).thenReturn("new-access");
        when(jwtService.generateRefreshToken(any())).thenReturn("new-refresh");

        TokenResponseDto response = authService.refreshTokens(new RefreshRequestDto("valid"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        verify(refreshTokenRepository).delete(stored);
    }
}