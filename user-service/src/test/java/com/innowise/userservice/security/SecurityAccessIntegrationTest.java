package com.innowise.userservice.security;

import com.innowise.userservice.IntegrationTestBase;
import com.innowise.userservice.model.User;
import com.innowise.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityAccessIntegrationTest extends IntegrationTestBase {

    private static final String INTERNAL_KEY = "test-internal-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private UUID ownerId;
    private UUID strangerId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        ownerId = saveUser("owner@example.com", "Owner", "One");
        strangerId = saveUser("stranger@example.com", "Stranger", "Two");
    }

    private UUID saveUser(String email, String name, String surname) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setSurname(surname);
        user.setBirthDate(LocalDate.of(1995, 6, 15));
        user.setActive(true);
        return userRepository.save(user).getId();
    }

    private RequestPostProcessor asUser(UUID userId) {
        return jwt()
                .jwt(builder -> builder.claim("userId", userId.toString()).claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private RequestPostProcessor asAdmin(UUID userId) {
        return jwt()
                .jwt(builder -> builder.claim("userId", userId.toString()).claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private String cardJson() {
        return """
                {
                  "cardNumber": "4539578763621486",
                  "cardHolder": "OWNER ONE",
                  "expirationDate": "2030-01-01"
                }
                """;
    }

    private String userJson(String email) {
        return """
                {
                  "email": "%s",
                  "name": "New",
                  "surname": "User",
                  "birthDate": "1995-06-15"
                }
                """.formatted(email);
    }

    @Test
    @DisplayName("Anonymous request is rejected by the filter chain with 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("A garbage bearer token is rejected with 401, not 500")
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("USER cannot list all users")
    void userCannotListAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(asUser(ownerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("USER reads his own profile")
    void userReadsOwnProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", ownerId).with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"));
    }

    @Test
    @DisplayName("USER cannot read a foreign profile even by passing its id in the path")
    void userCannotReadForeignProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", strangerId).with(asUser(ownerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER cannot update a foreign profile")
    void userCannotUpdateForeignProfile() throws Exception {
        mockMvc.perform(put("/api/v1/users/{id}", strangerId)
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("hacked@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER cannot deactivate anybody, that is an admin power")
    void userCannotDeactivateUser() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/status", ownerId)
                        .with(asUser(ownerId))
                        .param("active", "false"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER cannot attach a card to a foreign account")
    void userCannotAddCardToForeignAccount() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/cards", strangerId)
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER attaches a card to his own account and reads it back")
    void userManagesOwnCards() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/cards", ownerId)
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/users/{userId}/cards", ownerId).with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("USER cannot read a card that belongs to somebody else")
    void userCannotReadForeignCard() throws Exception {
        String response = mockMvc.perform(post("/api/v1/users/{userId}/cards", strangerId)
                        .with(asAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String foreignCardId = response.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/cards/{id}", foreignCardId).with(asUser(ownerId)))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("ADMIN lists all users")
    void adminListsAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(asAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("ADMIN reads any profile, the id in his own token is irrelevant")
    void adminReadsForeignProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", strangerId).with(asAdmin(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN deactivates a user")
    void adminDeactivatesUser() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/status", ownerId)
                        .with(asAdmin(UUID.randomUUID()))
                        .param("active", "false"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("USER cannot create a profile directly, registration goes through auth-service")
    void userCannotCreateProfile() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("new@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The internal service key grants ROLE_INTERNAL and lets auth-service create a profile")
    void internalKeyCanCreateProfile() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(InternalApiKeyFilter.HEADER, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("new@example.com")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A wrong internal key is not enough to create a profile")
    void wrongInternalKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(InternalApiKeyFilter.HEADER, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("new@example.com")))
                .andExpect(status().isUnauthorized());
    }
}