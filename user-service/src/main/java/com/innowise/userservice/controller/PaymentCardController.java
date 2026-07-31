package com.innowise.userservice.controller;

import com.innowise.userservice.dto.PaymentCardRequestDto;
import com.innowise.userservice.dto.PaymentCardResponseDto;
import com.innowise.userservice.service.PaymentCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class PaymentCardController {

    private final PaymentCardService paymentCardService;

    @PostMapping("/users/{userId}/cards")
    @PreAuthorize("hasRole('ADMIN') or @access.isSelf(#userId, authentication)")
    public ResponseEntity<PaymentCardResponseDto> createPaymentCard(
            @P("userId") @PathVariable("userId") UUID userId,
            @Valid @RequestBody PaymentCardRequestDto paymentCardRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentCardService.createPaymentCard(userId, paymentCardRequestDto));
    }

    @GetMapping("/cards/{id}")
    @PreAuthorize("hasRole('ADMIN') or @access.ownsCard(#id, authentication)")
    public ResponseEntity<PaymentCardResponseDto> getPaymentCardById(@P("id") @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(paymentCardService.getPaymentCardById(id));
    }

    @GetMapping("/users/{userId}/cards")
    @PreAuthorize("hasRole('ADMIN') or @access.isSelf(#userId, authentication)")
    public ResponseEntity<List<PaymentCardResponseDto>> getAllPaymentCardsByUserId(
            @P("userId") @PathVariable("userId") UUID userId) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(paymentCardService.getAllPaymentCardsByUserId(userId));
    }

    @PutMapping("/cards/{id}")
    @PreAuthorize("hasRole('ADMIN') or @access.ownsCard(#id, authentication)")
    public ResponseEntity<PaymentCardResponseDto> updatePaymentCard(
            @P("id") @PathVariable UUID id,
            @Valid @RequestBody PaymentCardRequestDto paymentCardRequestDto) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(paymentCardService.updatePaymentCard(id, paymentCardRequestDto));
    }

    @PatchMapping("/cards/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or @access.ownsCard(#id, authentication)")
    public ResponseEntity<Void> setPaymentCardStatus(@P("id") @PathVariable("id") UUID id,
                                                     @RequestParam boolean active) {
        paymentCardService.setActivePaymentCard(id, active);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cards/{id}")
    @PreAuthorize("hasRole('ADMIN') or @access.ownsCard(#id, authentication)")
    public ResponseEntity<Void> deletePaymentCard(@P("id") @PathVariable UUID id) {
        paymentCardService.deletePaymentCardById(id);
        return ResponseEntity.noContent().build();
    }
}