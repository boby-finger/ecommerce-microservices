package com.innowise.paymentservice.controller;

import com.innowise.paymentservice.dto.PaymentFilterDto;
import com.innowise.paymentservice.dto.PaymentRequestDto;
import com.innowise.paymentservice.dto.PaymentResponseDto;
import com.innowise.paymentservice.dto.PaymentTotalFilterDto;
import com.innowise.paymentservice.dto.PaymentTotalResponseDto;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.security.AccessGuard;
import com.innowise.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final AccessGuard accessGuard;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @access.isSelf(#request.userId(), authentication)")
    public ResponseEntity<PaymentResponseDto> createPayment(
            @P("request") @Valid @RequestBody PaymentRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(request));
    }

    @GetMapping
    public ResponseEntity<Page<PaymentResponseDto>> searchPayments(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication) {

        PaymentFilterDto filter = new PaymentFilterDto(userId, orderId, status);
        PaymentFilterDto scoped = accessGuard.isAdmin(authentication)
                ? filter
                : filter.scopedTo(accessGuard.requireCurrentUserId(authentication));

        return ResponseEntity.status(HttpStatus.OK).body(paymentService.searchPayments(scoped, pageable));
    }

    @GetMapping("/total/me")
    public ResponseEntity<PaymentTotalResponseDto> getMyTotal(
            @Valid @ModelAttribute PaymentTotalFilterDto filter,
            Authentication authentication) {

        UUID currentUserId = accessGuard.requireCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.OK).body(paymentService.getTotal(currentUserId, filter));
    }

    @GetMapping("/total")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentTotalResponseDto> getTotalForAllUsers(
            @Valid @ModelAttribute PaymentTotalFilterDto filter) {

        return ResponseEntity.status(HttpStatus.OK).body(paymentService.getTotal(null, filter));
    }
}
