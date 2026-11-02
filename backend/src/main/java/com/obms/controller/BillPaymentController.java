package com.obms.controller;

import com.obms.dto.BillPaymentDtos;
import com.obms.security.CurrentUser;
import com.obms.service.BillPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bill payments against the simulated biller catalogue (FR-6). */
@Tag(name = "Bill payments", description = "Simulated biller ecosystem")
@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillPaymentController {

    private final BillPaymentService billPaymentService;
    private final CurrentUser currentUser;

    @Operation(summary = "List available billers")
    @GetMapping("/billers")
    public List<BillPaymentDtos.BillerView> billers() {
        return billPaymentService.billers();
    }

    @Operation(summary = "Pay a bill",
            description = "Send an Idempotency-Key header so a retry cannot pay twice.")
    @PostMapping("/pay")
    public BillPaymentDtos.PaymentReceipt pay(
            @Valid @RequestBody BillPaymentDtos.PayBillRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return billPaymentService.pay(currentUser.requireCustomerId(), currentUser.username(),
                request, idempotencyKey);
    }

    @Operation(summary = "Payment history")
    @GetMapping
    public Page<BillPaymentDtos.PaymentHistoryItem> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return billPaymentService.history(currentUser.requireCustomerId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @Operation(summary = "Bills due soon")
    @GetMapping("/upcoming")
    public List<BillPaymentDtos.PaymentHistoryItem> upcoming(
            @RequestParam(defaultValue = "14") int days) {
        return billPaymentService.upcoming(currentUser.requireCustomerId(), Math.max(days, 1));
    }
}
