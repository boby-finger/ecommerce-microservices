package com.innowise.authservice.service;

import com.innowise.authservice.model.Credentials;
import com.innowise.authservice.model.Role;
import com.innowise.authservice.repository.CredentialsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialsDetailsServiceTest {

    @Mock
    private CredentialsRepository credentialsRepository;

    @InjectMocks
    private CredentialsDetailsService credentialsDetailsService;

    private Credentials credentials(Role role) {
        Credentials credentials = new Credentials();
        credentials.setUsername("vasya");
        credentials.setPasswordHash("$2a$10$hashed");
        credentials.setRole(role);
        credentials.setUserId(UUID.randomUUID());
        return credentials;
    }

    @Test
    @DisplayName("The stored role is exposed with the ROLE_ prefix Spring expects")
    void roleIsPrefixed() {
        when(credentialsRepository.findByUsername("vasya"))
                .thenReturn(Optional.of(credentials(Role.ADMIN)));

        UserDetails userDetails = credentialsDetailsService.loadUserByUsername("vasya");

        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(userDetails.getPassword()).isEqualTo("$2a$10$hashed");
    }

    @Test
    @DisplayName("An unknown username is reported as such")
    void unknownUsernameThrows() {
        when(credentialsRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> credentialsDetailsService.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}