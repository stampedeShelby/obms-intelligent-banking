package com.obms.dto;

import com.obms.domain.BillPayment;
import com.obms.domain.Biller;
import com.obms.domain.Enums;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Bill payment request and response shapes (FR-6). */
public final class BillPaymentDtos {

    private BillPaymentDtos() {
        // DTO holder
    }

    public record BillerView(
            Long id,
            String code,
            String name,
            Enums.BillerCategory category,
            String identifierLabel,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {
        public static BillerView from(Biller biller) {
            return new BillerView(biller.getId(), biller.getCode(), biller.getName(),
                    biller.getCategory(), biller.getIdentifierLabel(),
                    biller.getMinAmount(), biller.getMaxAmount());
        }
    }

    public record PayBillRequest(
            @NotNull(message = "Select a biller")
            Long billerId,

            @NotNull(message = "Select the account to pay from")
            Long accountId,

            @NotBlank(message = "Enter the consumer number")
            @Size(max = 60)
            String consumerIdentifier,

            @NotNull(message = "Enter an amount")
            @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
            @Digits(integer = 15, fraction = 2, message = "Amount may have at most two decimal places")
            BigDecimal amount,

            LocalDate dueDate
    ) {
    }

    /**
     * Receipt for a bill payment.
     *
     * @param reference          customer-visible payment reference
     * @param transactionRef     the ledger entry that debited the account
     * @param balanceAfter       account balance after the debit
     */
    public record PaymentReceipt(
            String reference,
            String transactionRef,
            String billerName,
            String consumerIdentifier,
            BigDecimal amount,
            BigDecimal balanceAfter,
            Enums.TransactionStatus status,
            Instant paidAt
    ) {
        public static PaymentReceipt from(BillPayment payment, BigDecimal balanceAfter) {
            return new PaymentReceipt(
                    payment.getReference(),
                    payment.getTransaction() == null ? null : payment.getTransaction().getReference(),
                    payment.getBiller().getName(),
                    payment.getConsumerIdentifier(),
                    payment.getAmount(),
                    balanceAfter,
                    payment.getStatus(),
                    payment.getPaidAt());
        }
    }

    public record PaymentHistoryItem(
            String reference,
            String billerName,
            Enums.BillerCategory category,
            String consumerIdentifier,
            BigDecimal amount,
            Enums.TransactionStatus status,
            LocalDate dueDate,
            Instant paidAt
    ) {
        public static PaymentHistoryItem from(BillPayment payment) {
            return new PaymentHistoryItem(
                    payment.getReference(), payment.getBiller().getName(),
                    payment.getBiller().getCategory(), payment.getConsumerIdentifier(),
                    payment.getAmount(), payment.getStatus(), payment.getDueDate(),
                    payment.getPaidAt());
        }
    }
}
