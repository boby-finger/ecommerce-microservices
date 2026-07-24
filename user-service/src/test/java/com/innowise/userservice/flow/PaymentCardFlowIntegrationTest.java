package com.innowise.userservice.flow;

import com.innowise.userservice.IntegrationTestBase;
import com.innowise.userservice.repository.PaymentCardRepository;
import com.innowise.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentCardFlowIntegrationTest extends IntegrationTestBase {

    private static final String[] VALID_CARDS = {
            "4111111111111111",
            "4012888888881881",
            "5555555555554444",
            "5105105105105100",
            "4242424242424242"
    };
    private static final String SIXTH_CARD = "6011111111111117";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentCardRepository paymentCardRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    private UUID createUser() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "jake@example.com",
                                  "name": "jake",
                                  "surname": "jade",
                                  "birthDate": "2000-01-01"
                                }
                                """))
                .andExpect(status().isCreated());

        return userRepository.findAll().getFirst().getId();
    }

    private String cardJson(String number, String holder) {
        return """
                {
                  "cardNumber": "%s",
                  "cardHolder": "%s",
                  "expirationDate": "2030-12-31"
                }
                """.formatted(number, holder);
    }

    private UUID createCard(UUID userId, String number) throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/cards", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson(number, "JAKE JADE")))
                .andExpect(status().isCreated());

        return paymentCardRepository.findAll().stream()
                .filter(c -> c.getCardNumber().equals(number))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void shouldCreateCardWithGeneratedIdAndMaskedNumber() throws Exception {
        UUID userId = createUser();

        mockMvc.perform(post("/api/v1/users/{userId}/cards", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson("4111111111111111", "JAKE JADE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.cardNumber").value("**** **** **** 1111"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldStoreFullCardNumberInDatabase() throws Exception {
        UUID userId = createUser();
        createCard(userId, "4111111111111111");

        assert paymentCardRepository.findAll().getFirst()
                .getCardNumber().equals("4111111111111111");
    }

    @Test
    void shouldReturnUserWithHisCards() throws Exception {
        UUID userId = createUser();
        createCard(userId, "4111111111111111");
        createCard(userId, "4012888888881881");

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(2))
                .andExpect(jsonPath("$.cards[0].cardNumber").value(org.hamcrest.Matchers.startsWith("****")));
    }

    @Test
    void shouldReturnCardsByUserId() throws Exception {
        UUID userId = createUser();
        createCard(userId, "4111111111111111");

        mockMvc.perform(get("/api/v1/users/{userId}/cards", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cardNumber").value("**** **** **** 1111"));
    }

    @Test
    void shouldRejectSixthCard() throws Exception {
        UUID userId = createUser();
        for (String number : VALID_CARDS) {
            createCard(userId, number);
        }

        mockMvc.perform(post("/api/v1/users/{userId}/cards", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson(SIXTH_CARD, "JAKE JADE")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/users/{userId}/cards", userId))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void shouldGetCardById() throws Exception {
        UUID userId = createUser();
        UUID cardId = createCard(userId, "4111111111111111");

        mockMvc.perform(get("/api/v1/cards/{id}", cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cardId.toString()))
                .andExpect(jsonPath("$.cardHolder").value("JAKE JADE"));
    }

    @Test
    void shouldUpdateCard() throws Exception {
        UUID userId = createUser();
        UUID cardId = createCard(userId, "4111111111111111");

        mockMvc.perform(put("/api/v1/cards/{id}", cardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson("4111111111111111", "JAKE EKITANO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardHolder").value("JAKE EKITANO"));

        mockMvc.perform(get("/api/v1/cards/{id}", cardId))
                .andExpect(jsonPath("$.cardHolder").value("JAKE EKITANO"));
    }

    @Test
    void shouldDeactivateCard() throws Exception {
        UUID userId = createUser();
        UUID cardId = createCard(userId, "4111111111111111");

        mockMvc.perform(patch("/api/v1/cards/{id}/status", cardId)
                        .param("active", "false"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cards/{id}", cardId))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldDeleteCardAndRemoveItFromUser() throws Exception {
        UUID userId = createUser();
        UUID cardId = createCard(userId, "4111111111111111");

        mockMvc.perform(delete("/api/v1/cards/{id}", cardId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cards/{id}", cardId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(jsonPath("$.cards.length()").value(0));
    }

    @Test
    void shouldCascadeDeleteCards_whenUserDeleted() throws Exception {
        UUID userId = createUser();
        createCard(userId, "4111111111111111");
        createCard(userId, "4012888888881881");

        mockMvc.perform(delete("/api/v1/users/{id}", userId))
                .andExpect(status().isNoContent());

        assert paymentCardRepository.count() == 0;
    }

    @Test
    void shouldReturnNotFound_whenOwnerDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/cards", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardJson("4111111111111111", "JAKE JADE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequest_whenCardNumberInvalid() throws Exception {
        UUID userId = createUser();

        mockMvc.perform(post("/api/v1/users/{userId}/cards", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardNumber": "1234",
                                  "cardHolder": "",
                                  "expirationDate": "2000-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").exists());
    }
}