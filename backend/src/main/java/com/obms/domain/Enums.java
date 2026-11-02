package com.obms.domain;

/**
 * Central holder for the system's domain enumerations.
 *
 * <p>These are grouped in one compilation unit deliberately: they are small,
 * tightly related value sets referenced across every module, and keeping them
 * together makes the domain vocabulary of OBMS readable in one place.</p>
 */
public final class Enums {

    private Enums() {
        // utility holder - not instantiable
    }

    /** Type of bank account held by a customer. */
    public enum AccountType {
        SAVINGS,
        CURRENT
    }

    /** Lifecycle state of a bank account. */
    public enum AccountStatus {
        ACTIVE,
        DORMANT,
        FROZEN,
        CLOSED
    }

    /** Direction of money movement relative to the account on the ledger row. */
    public enum TransactionDirection {
        DEBIT,
        CREDIT
    }

    /** Business classification of a ledger entry. */
    public enum TransactionType {
        INTERNAL_TRANSFER,
        BILL_PAYMENT,
        DEPOSIT,
        WITHDRAWAL,
        REVERSAL,
        INTEREST,
        FEE
    }

    /** Outcome state of a transaction. */
    public enum TransactionStatus {
        PENDING,
        AWAITING_VERIFICATION,
        COMPLETED,
        FAILED,
        BLOCKED,
        REVERSED
    }

    /** Spending category assigned to a transaction by the analytics module. */
    public enum SpendCategory {
        FOOD,
        SHOPPING,
        UTILITIES,
        TRANSPORT,
        HEALTHCARE,
        ENTERTAINMENT,
        EDUCATION,
        RENT,
        TRANSFER,
        INCOME,
        SAVINGS,
        OTHER
    }

    /** Verification state of a registered beneficiary. */
    public enum BeneficiaryStatus {
        PENDING_VERIFICATION,
        ACTIVE,
        REMOVED
    }

    /** KYC review state for a customer. */
    public enum KycStatus {
        NOT_SUBMITTED,
        PENDING,
        VERIFIED,
        REJECTED
    }

    /** Purpose an OTP was issued for. Scoping OTPs prevents cross-use replay. */
    public enum OtpPurpose {
        LOGIN,
        TRANSFER,
        BENEFICIARY_VERIFICATION,
        PASSWORD_RESET,
        PROFILE_UPDATE
    }

    /** Risk bands produced by the fraud detection service. */
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /** Investigation state of a fraud alert. */
    public enum AlertStatus {
        OPEN,
        UNDER_REVIEW,
        CONFIRMED_FRAUD,
        FALSE_POSITIVE,
        CLOSED
    }

    /** Category of biller in the simulated biller ecosystem. */
    public enum BillerCategory {
        ELECTRICITY,
        WATER,
        MOBILE_RECHARGE,
        BROADBAND,
        GAS,
        CREDIT_CARD
    }

    /** Progress state of a customer savings goal. */
    public enum GoalStatus {
        ACTIVE,
        ACHIEVED,
        ABANDONED
    }

    /** Delivery channel for a notification. */
    public enum NotificationChannel {
        IN_APP,
        EMAIL,
        SMS
    }

    /** Functional classification of a notification. */
    public enum NotificationType {
        TRANSACTION_SUCCESS,
        TRANSACTION_FAILED,
        LOGIN_ALERT,
        BENEFICIARY_ADDED,
        SUSPICIOUS_ACTIVITY,
        BILL_REMINDER,
        SAVINGS_GOAL,
        SECURITY
    }

    /** Author of a copilot chat message. */
    public enum MessageRole {
        USER,
        ASSISTANT
    }
}
