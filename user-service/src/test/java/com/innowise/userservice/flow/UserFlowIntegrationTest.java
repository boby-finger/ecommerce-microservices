package com.innowise.userservice.flow;

import com.innowise.userservice.IntegrationTestBase;
import com.innowise.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockUser(roles = "ADMIN")
class UserFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    private String userJson(String email, String name, String surname) {
        return """
                {
                  "email": "%s",
                  "name": "%s",
                  "surname": "%s",
                  "birthDate": "1995-06-15"
                }
                """.formatted(email, name, surname);
    }

    private UUID createUser(String email, String name, String surname) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(email, name, surname)))
                .andExpect(status().isCreated());

        return userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals(email))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void shouldCreateUserAndReturnIt() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("jake@example.com", "Jake", "Jade")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("jake@example.com"))
                .andExpect(jsonPath("$.name").value("Jake"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturnUserWithEmptyCardList() throws Exception {
        UUID userId = createUser("jake@example.com", "Jake", "Jade");

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.cards").isArray())
                .andExpect(jsonPath("$.cards.length()").value(0));
    }

    @Test
    void shouldReturnConflict_whenEmailAlreadyTaken() throws Exception {
        createUser("jake@example.com", "Jake", "Jade");

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("jake@example.com", "ducky", "duck")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturnBadRequest_whenPayloadInvalid() throws Exception {
        String invalid = """
                {
                  "email": "not-an-email",
                  "name": "",
                  "surname": "Jade",
                  "birthDate": "2050-01-01"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.birthDate").exists());
    }

    @Test
    void shouldUpdateUserAndPersistChanges() throws Exception {
        UUID userId = createUser("jake@example.com", "Jake", "Jade");

        mockMvc.perform(put("/api/v1/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("jake@example.com", "Jake", "Ekitano")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surname").value("Ekitano"));

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surname").value("Ekitano"));
    }

    @Test
    void shouldDeactivateUser() throws Exception {
        UUID userId = createUser("jake@example.com", "Jake", "Jade");

        mockMvc.perform(patch("/api/v1/users/{id}/status", userId)
                        .param("active", "false"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldDeleteUser() throws Exception {
        UUID userId = createUser("jake@example.com", "Jake", "Jade");

        mockMvc.perform(delete("/api/v1/users/{id}", userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldFilterUsersByNameAndPaginate() throws Exception {
        createUser("jake@example.com", "Jake", "Jade");
        createUser("ducky@example.com", "Ducky", "Duck");

        mockMvc.perform(get("/api/v1/users").param("name", "Jake"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Jake"));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequest_whenIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/users/abc"))
                .andExpect(status().isBadRequest());
    }
}