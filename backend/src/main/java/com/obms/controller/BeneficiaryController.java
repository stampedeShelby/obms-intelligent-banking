package com.obms.controller;

import com.obms.dto.BeneficiaryDtos;
import com.obms.security.CurrentUser;
import com.obms.service.BeneficiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Beneficiary management (FR-5). */
@Tag(name = "Beneficiaries", description = "Payee registration and OTP verification")
@RestController
@RequestMapping("/api/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final CurrentUser currentUser;

    @Operation(summary = "List the customer's payees")
    @GetMapping
    public List<BeneficiaryDtos.BeneficiaryView> list() {
        return beneficiaryService.list(currentUser.requireCustomerId());
    }

    @Operation(summary = "Register a payee",
            description = "Created in PENDING_VERIFICATION. A code is sent; the payee cannot "
                    + "receive a transfer until it is verified.")
    @PostMapping
    public ResponseEntity<BeneficiaryDtos.BeneficiaryView> add(
            @Valid @RequestBody BeneficiaryDtos.AddBeneficiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                beneficiaryService.add(currentUser.requireCustomerId(), currentUser.username(),
                        request));
    }

    @Operation(summary = "Verify a payee with the emailed code")
    @PostMapping("/{beneficiaryId}/verify")
    public BeneficiaryDtos.BeneficiaryView verify(
            @PathVariable Long beneficiaryId,
            @Valid @RequestBody BeneficiaryDtos.VerifyBeneficiaryRequest request) {
        return beneficiaryService.verify(beneficiaryId, currentUser.requireCustomerId(),
                currentUser.username(), request.code());
    }

    @Operation(summary = "Rename a payee")
    @PatchMapping("/{beneficiaryId}")
    public BeneficiaryDtos.BeneficiaryView rename(
            @PathVariable Long beneficiaryId,
            @Valid @RequestBody BeneficiaryDtos.RenameRequest request) {
        return beneficiaryService.rename(beneficiaryId, currentUser.requireCustomerId(),
                request.nickname());
    }

    @Operation(summary = "Remove a payee",
            description = "Soft-deleted so past transfers remain explainable.")
    @DeleteMapping("/{beneficiaryId}")
    public ResponseEntity<Void> remove(@PathVariable Long beneficiaryId) {
        beneficiaryService.remove(beneficiaryId, currentUser.requireCustomerId());
        return ResponseEntity.noContent().build();
    }
}
