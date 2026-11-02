package com.obms.controller;

import com.obms.domain.Enums;
import com.obms.dto.AdminDtos;
import com.obms.security.CurrentUser;
import com.obms.service.AdminService;
import com.obms.service.fraud.FraudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative and staff surface (module 6).
 *
 * <p>Paths are split by audience so the URL-level rules in
 * {@code SecurityConfig} can gate them independently of the method-level
 * {@code @PreAuthorize} checks in {@link AdminService}:</p>
 * <ul>
 *   <li>{@code /api/admin/**} - ADMIN only</li>
 *   <li>{@code /api/staff/**} - ADMIN or BANK_STAFF</li>
 *   <li>{@code /api/fraud/investigation/**} - ADMIN or FRAUD_ANALYST</li>
 * </ul>
 */
@Tag(name = "Administration", description = "Admin, branch staff and fraud analyst operations")
@RestController
@RequiredArgsConstructor
public class AdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminService adminService;
    private final CurrentUser currentUser;

    // ---- shared statistics -------------------------------------------

    @Operation(summary = "System-wide statistics")
    @GetMapping("/api/staff/stats")
    public AdminDtos.SystemStats stats() {
        return adminService.stats();
    }

    // ---- customer management -----------------------------------------

    @Operation(summary = "Search customers")
    @GetMapping("/api/staff/customers")
    public Page<AdminDtos.CustomerRow> customers(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.customers(query, PageRequest.of(Math.max(page, 0), clamp(size)));
    }

    // ---- KYC queue ---------------------------------------------------

    @Operation(summary = "KYC submissions awaiting review")
    @GetMapping("/api/staff/kyc")
    public Page<AdminDtos.KycQueueItem> kycQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.kycQueue(PageRequest.of(Math.max(page, 0), clamp(size)));
    }

    @Operation(summary = "Approve or reject a KYC submission",
            description = "A rejection must carry a reason, which is shown to the customer.")
    @PostMapping("/api/staff/kyc/{kycId}/decision")
    public AdminDtos.KycQueueItem decideKyc(
            @PathVariable Long kycId,
            @Valid @RequestBody AdminDtos.KycDecisionRequest request) {
        return adminService.decideKyc(kycId, currentUser.username(), request);
    }

    // ---- account status ----------------------------------------------

    @Operation(summary = "Freeze, reactivate or close an account")
    @PutMapping("/api/admin/accounts/{accountId}/status")
    public ResponseEntity<Void> setAccountStatus(
            @PathVariable Long accountId,
            @Valid @RequestBody AdminDtos.AccountStatusRequest request) {
        adminService.setAccountStatus(accountId, request);
        return ResponseEntity.noContent().build();
    }

    // ---- fraud investigation -----------------------------------------

    @Operation(summary = "Fraud alert investigation queue, highest risk first")
    @GetMapping("/api/fraud/investigation/queue")
    public Page<AdminDtos.AlertDetail> investigationQueue(
            @RequestParam(required = false) Enums.AlertStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.investigationQueue(status,
                PageRequest.of(Math.max(page, 0), clamp(size)));
    }

    @Operation(summary = "One alert with its full explanation")
    @GetMapping("/api/fraud/investigation/alerts/{alertId}")
    public AdminDtos.AlertDetail alert(@PathVariable Long alertId) {
        return adminService.alert(alertId);
    }

    @Operation(summary = "Record an investigation outcome")
    @PostMapping("/api/fraud/investigation/alerts/{alertId}/review")
    public AdminDtos.AlertDetail reviewAlert(
            @PathVariable Long alertId,
            @Valid @RequestBody AdminDtos.AlertReviewRequest request) {
        return adminService.reviewAlert(alertId, currentUser.username(), request);
    }

    @Operation(summary = "Monitoring summary for the fraud dashboard")
    @GetMapping("/api/fraud/investigation/monitoring")
    public FraudService.MonitoringSummary monitoring(
            @RequestParam(defaultValue = "7") int days) {
        return adminService.monitoring(days);
    }

    @Operation(summary = "Risk band distribution for the dashboard chart")
    @GetMapping("/api/fraud/investigation/distribution")
    public AdminDtos.RiskDistribution distribution() {
        return adminService.riskDistribution();
    }

    // ---- audit trail -------------------------------------------------

    @Operation(summary = "Audit trail, newest first",
            description = "Read-only. No endpoint in this API updates or deletes an audit row.")
    @GetMapping("/api/admin/audit")
    public Page<AdminDtos.AuditRow> audit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return adminService.auditTrail(action, actor,
                PageRequest.of(Math.max(page, 0), clamp(size)));
    }

    private int clamp(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
