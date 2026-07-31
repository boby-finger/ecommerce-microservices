package com.innowise.authservice.flow;

import com.innowise.authservice.IntegrationTestBase;
import com.innowise.authservice.repository.CredentialsRepository;
import com.innowise.authservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSecurityIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialsRepository credentialsRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        credentialsRepository.deleteAll();
        when(userServiceClient.createUser(any())).thenReturn(UUID.randomUUID());
    }

    private RequestPostProcessor withRole(String role) {
        return jwt()
                .jwt(builder -> builder
                        .claim("userId", UUID.randomUUID().toString())
                        .claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String adminJson(String username) {
        return """
                {
                  "username": "%s",
                  "password": "pass12345",
                  "name": "Second",
                  "surname": "Admin",
                  "birthDate": "1990-01-01",
                  "email": "%s@example.com"
                }
                """.formatted(username, username);
    }

    @Test
    @DisplayName("The jwks endpoint is public and publishes an RSA signing key")
    void jwksIsPublic() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty());
    }

    @Test
    @DisplayName("The signing key is never exposed through jwks")
    void jwksDoesNotLeakThePrivateKey() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test
    @DisplayName("Registration is reachable without a token")
    void registerIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "anonymous-user",
                                  "password": "pass12345",
                                  "name": "Anon",
                                  "surname": "User",
                                  "birthDate": "1999-01-01",
                                  "email": "anon@example.com"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Validation is reachable without a token")
    void validateIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "not-a-jwt"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Creating an admin without a token gives 401")
    void createAdminIsClosedToAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/auth/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("newadmin")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("A USER cannot create an admin")
    void createAdminIsClosedToUsers() throws Exception {
        mockMvc.perform(post("/api/v1/auth/admin")
                        .with(withRole("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("newadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("An ADMIN can create another admin")
    void adminCanCreateAnotherAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/admin")
                        .with(withRole("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("newadmin")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("A garbage bearer token is rejected with 401, not 500")
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/admin")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("newadmin")))
                .andExpect(status().isUnauthorized());
    }
}