package com.obms.controller;

import com.obms.dto.AccountDtos;
import com.obms.dto.DashboardDtos;
import com.obms.security.CurrentUser;
import com.obms.service.AccountService;
import com.obms.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Accounts, transaction history and the dashboard (FR-3).
 */
@Tag(name = "Accounts", description = "Balances, transaction history and dashboard")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountService accountService;
    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    @Operation(summary = "Full dashboard payload for the signed-in customer")
    @GetMapping("/dashboard")
    public DashboardDtos.Dashboard dashboard() {
        return dashboardService.build(currentUser.requireCustomerId());
    }

    @Operation(summary = "List the customer's accounts")
    @GetMapping("/accounts")
    public List<AccountDtos.AccountSummary> accounts() {
        return accountService.listAccounts(currentUser.requireCustomerId());
    }

    @Operation(summary = "One account's details")
    @GetMapping("/accounts/{accountId}")
    public AccountDtos.AccountSummary account(@PathVariable Long accountId) {
        return accountService.getAccount(accountId, currentUser.requireCustomerId());
    }

    @Operation(summary = "Paged transaction history for an account")
    @GetMapping("/accounts/{accountId}/transactions")
    public Page<AccountDtos.TransactionView> history(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return accountService.history(accountId, currentUser.requireCustomerId(),
                PageRequest.of(Math.max(page, 0), clampSize(size)));
    }

    @Operation(summary = "Recent activity across every account")
    @GetMapping("/transactions/recent")
    public List<AccountDtos.TransactionView> recent() {
        return accountService.recentAcrossAccounts(currentUser.requireCustomerId());
    }

    @Operation(summary = "One transaction by reference")
    @GetMapping("/transactions/{reference}")
    public AccountDtos.TransactionView transaction(@PathVariable String reference) {
        return accountService.getTransaction(reference, currentUser.requireCustomerId());
    }

    @Operation(summary = "Correct the spend category on a transaction",
            description = "A manual correction is preserved and not overwritten by the "
                    + "automatic categoriser on later passes.")
    @PatchMapping("/transactions/{reference}/category")
    public AccountDtos.TransactionView recategorise(
            @PathVariable String reference,
            @Valid @RequestBody AccountDtos.RecategoriseRequest request) {
        return accountService.recategorise(reference, currentUser.requireCustomerId(),
                request.category());
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
