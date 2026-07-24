package com.innowise.userservice.controller;

import com.innowise.userservice.dto.PaymentCardRequestDto;
import com.innowise.userservice.dto.PaymentCardResponseDto;
import com.innowise.userservice.service.PaymentCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class PaymentCardController {
    private final PaymentCardService paymentCardService;

    @PostMapping("/users/{userId}/cards")
    public ResponseEntity<PaymentCardResponseDto> createPaymentCard(@PathVariable("userId") UUID userId,
            @Valid @RequestBody PaymentCardRequestDto paymentCardRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentCardService.createPaymentCard(userId, paymentCardRequestDto));
    }

    @GetMapping("/cards/{id}")
    public ResponseEntity<PaymentCardResponseDto> getPaymentCardById(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(paymentCardService.getPaymentCardById(id));
    }

    @GetMapping("/users/{userId}/cards")
    public ResponseEntity<List<PaymentCardResponseDto>> getAllPaymentCardsByUserId(
            @PathVariable("userId") UUID userId) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(paymentCardService.getAllPaymentCardsByUserId(userId));
    }

    @PutMapping("/cards/{id}")
    public ResponseEntity<PaymentCardResponseDto> updatePaymentCard(
            @PathVariable UUID id, @Valid @RequestBody PaymentCardRequestDto paymentCardRequestDto) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(paymentCardService.updatePaymentCard(id, paymentCardRequestDto));
    }

    @PatchMapping("/cards/{id}/status")
    public ResponseEntity<Void> setPaymentCardStatus(@PathVariable("id") UUID id, @RequestParam boolean active) {
        paymentCardService.setActivePaymentCard(id, active);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> deletePaymentCard(@PathVariable UUID id) {
        paymentCardService.deletePaymentCardById(id);
        return ResponseEntity.noContent().build();
    }
}
