package com.obms.dto;

import com.obms.domain.Account;
import com.obms.domain.Enums;
import com.obms.domain.Transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Account and transaction projections.
 *
 * <p>These exist so that JPA entities never travel over the wire. Serialising
 * {@link Account} directly would drag the customer, their other accounts and
 * their whole transaction graph into the response, and would expose internal
 * fields such as the optimistic-lock version.</p>
 */
public final class AccountDtos {

    private AccountDtos() {
        // DTO holder
    }

    public record AccountSummary(
            Long id,
            String accountNumber,
            String ifscCode,
            String nickname,
            Enums.AccountType accountType,
            Enums.AccountStatus status,
            BigDecimal balance,
            BigDecimal availableBalance,
            BigDecimal dailyTransferLimit,
            String currency
    ) {
        public static AccountSummary from(Account account) {
            return new AccountSummary(
                    account.getId(), account.getAccountNumber(), account.getIfscCode(),
                    account.getNickname(), account.getAccountType(), account.getStatus(),
                    account.getBalance(), account.availableBalance(),
                    account.getDailyTransferLimit(), account.getCurrency());
        }
    }

    public record TransactionView(
            String reference,
            String transferGroupRef,
            Enums.TransactionDirection direction,
            Enums.TransactionType transactionType,
            Enums.TransactionStatus status,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String description,
            String counterpartyName,
            Enums.SpendCategory category,
            boolean categoryUserSet,
            Instant bookedAt,
            Integer riskScore,
            Enums.RiskLevel riskLevel,
            String accountNumber
    ) {
        public static TransactionView from(Transaction txn) {
            return new TransactionView(
                    txn.getReference(), txn.getTransferGroupRef(), txn.getDirection(),
                    txn.getTransactionType(), txn.getStatus(), txn.getAmount(),
                    txn.getBalanceAfter(), txn.getDescription(), txn.getCounterpartyName(),
                    txn.getCategory(), txn.isCategoryUserSet(), txn.getBookedAt(),
                    txn.getRiskScore(), txn.getRiskLevel(),
                    txn.getAccount() == null ? null : txn.getAccount().getAccountNumber());
        }
    }

    /** Request to correct an auto-assigned spend category. */
    public record RecategoriseRequest(Enums.SpendCategory category) {
    }
}
