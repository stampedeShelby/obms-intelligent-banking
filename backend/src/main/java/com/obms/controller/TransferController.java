package com.obms.controller;

import com.obms.dto.TransferDtos;
import com.obms.security.CurrentUser;
import com.obms.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fund transfers (FR-4).
 *
 * <p>The two-step shape of this API is the risk-based authentication flow:
 * {@code POST /transfers} may settle immediately or come back asking for a
 * code, and {@code POST /transfers/confirm} completes the latter. The client
 * branches on the {@code outcome} field rather than on an HTTP status.</p>
 */
@Tag(name = "Transfers", description = "Internal fund transfers with risk-based verification")
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final CurrentUser currentUser;

    @Operation(summary = "Initiate a transfer",
            description = "Send an Idempotency-Key header so that a retried request cannot "
                    + "produce a second debit. The response outcome is COMPLETED, "
                    + "VERIFICATION_REQUIRED or HELD_FOR_REVIEW.")
    @PostMapping
    public TransferDtos.TransferResult initiate(
            @Valid @RequestBody TransferDtos.TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return transferService.initiate(currentUser.requireCustomerId(), currentUser.username(),
                request, idempotencyKey);
    }

    @Operation(summary = "Confirm a transfer that required a one-time code",
            description = "Only the pending reference and the code are accepted. The amount "
                    + "and destination are fixed at initiation and cannot be changed here.")
    @PostMapping("/confirm")
    public TransferDtos.TransferResult confirm(
            @Valid @RequestBody TransferDtos.ConfirmRequest request) {
        return transferService.confirm(currentUser.requireCustomerId(), currentUser.username(),
                request.pendingRef(), request.otpCode());
    }
}
