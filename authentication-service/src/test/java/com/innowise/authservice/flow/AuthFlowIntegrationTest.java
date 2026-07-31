package com.innowise.authservice.flow;

import com.innowise.authservice.IntegrationTestBase;
import com.innowise.authservice.repository.CredentialsRepository;
import com.innowise.authservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTest extends IntegrationTestBase {

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

    private String registerJson(String username, String email) {
        return """
                {
                  "username": "%s",
                  "password": "pass12345",
                  "name": "Vasya",
                  "surname": "Pupkin",
                  "birthDate": "1999-01-01",
                  "email": "%s"
                }
                """.formatted(username, email);
    }

    private String register(String username, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String tokenFrom(String json, String field) {
        return json.replaceAll(".*\"" + field + "\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName("Registration returns an access and a refresh token")
    void registrationReturnsTokenPair() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("vasya", "vasya@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("The password is never stored in plain text")
    void passwordIsHashed() throws Exception {
        register("vasya", "vasya@example.com");

        String hash = credentialsRepository.findByUsername("vasya").orElseThrow().getPasswordHash();

        org.assertj.core.api.Assertions.assertThat(hash)
                .isNotEqualTo("pass12345")
                .startsWith("$2");
    }

    @Test
    @DisplayName("A taken username is rejected with 409")
    void duplicateUsernameIsRejected() throws Exception {
        register("vasya", "vasya@example.com");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("vasya", "another@example.com")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A short password is rejected with 400 and a field error")
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "shorty",
                                  "password": "123",
                                  "name": "Vasya",
                                  "surname": "Pupkin",
                                  "birthDate": "1999-01-01",
                                  "email": "shorty@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    @DisplayName("Login with the right credentials returns a token pair")
    void loginSucceeds() throws Exception {
        register("vasya", "vasya@example.com");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "vasya", "password": "pass12345"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("A wrong password gives 401 without revealing which part was wrong")
    void loginWithWrongPasswordFails() throws Exception {
        register("vasya", "vasya@example.com");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "vasya", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    @DisplayName("An unknown username gives the same 401")
    void loginWithUnknownUserFails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "nobody", "password": "pass12345"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refreshing returns a new pair and invalidates the old refresh token")
    void refreshRotatesTheToken() throws Exception {
        String refreshToken = tokenFrom(register("vasya", "vasya@example.com"), "refreshToken");

        String body = """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An unknown refresh token gives 401")
    void refreshWithUnknownTokenFails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "not-a-real-token"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An access token validates and reports the role")
    void validateAccessToken() throws Exception {
        String accessToken = tokenFrom(register("vasya", "vasya@example.com"), "accessToken");

        mockMvc.perform(post("/api/v1/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s"}
                                """.formatted(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.username").value("vasya"))
                .andExpect(jsonPath("$.userId").isNotEmpty());
    }

    @Test
    @DisplayName("A refresh token does not validate as an access token")
    void validateRejectsRefreshToken() throws Exception {
        String refreshToken = tokenFrom(register("vasya", "vasya@example.com"), "refreshToken");

        mockMvc.perform(post("/api/v1/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").isNotEmpty());
    }

    @Test
    @DisplayName("Garbage does not validate, and does not blow up either")
    void validateRejectsGarbage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "not-a-jwt"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }
}