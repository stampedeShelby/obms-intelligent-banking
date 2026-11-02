package com.obms.integration;

import com.obms.common.BankTime;
import com.obms.domain.Enums;
import com.obms.service.LedgerService;
import com.obms.service.StatementService;
import com.obms.support.IntegrationTestBase;
import com.obms.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests asserting that a statement reconciles against the ledger.
 *
 * <p>The specification requires that statement data matches actual transaction
 * records. The check that matters is arithmetic: opening balance plus credits
 * minus debits must equal the closing balance. If the derivation of the
 * opening balance were wrong, this is where it would show.</p>
 */
@DisplayName("Integration: statement accuracy")
class StatementAccuracyTest extends IntegrationTestBase {

    @Autowired
    private StatementService statementService;
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private TestDataFactory factory;

    @Test
    @DisplayName("opening balance plus credits minus debits equals the closing balance")
    void statementReconciles() {
        var user = factory.customerWithAccount(new BigDecimal("10000.00"),
                BigDecimal.ZERO, "CUSTOMER");

        ledgerService.postCredit(user.account().getId(), new BigDecimal("5000.00"),
                Enums.TransactionType.DEPOSIT, "Salary credit", "Employer",
                Enums.SpendCategory.INCOME);
        ledgerService.postDebit(user.account().getId(), new BigDecimal("1200.00"),
                Enums.TransactionType.BILL_PAYMENT, "BESCOM Electricity - 1", "BESCOM",
                Enums.SpendCategory.UTILITIES);
        ledgerService.postDebit(user.account().getId(), new BigDecimal("800.00"),
                Enums.TransactionType.WITHDRAWAL, "Swiggy order", "Swiggy",
                Enums.SpendCategory.FOOD);

        StatementService.StatementDocument statement = statementService.generate(
                user.account().getId(), user.customer().getId(),
                BankTime.today().minusDays(1), BankTime.today());

        assertThat(statement.rowCount()).isEqualTo(3);
        assertThat(statement.totalCredits()).isEqualByComparingTo("5000.00");
        assertThat(statement.totalDebits()).isEqualByComparingTo("2000.00");

        BigDecimal reconciled = statement.openingBalance()
                .add(statement.totalCredits())
                .subtract(statement.totalDebits());

        assertThat(reconciled)
                .as("the statement must balance, or the figures are not trustworthy")
                .isEqualByComparingTo(statement.closingBalance());
        assertThat(statement.closingBalance()).isEqualByComparingTo("13000.00");
    }

    @Test
    @DisplayName("the generated file is a real PDF")
    void producesValidPdfBytes() {
        var user = factory.customerWithAccount(new BigDecimal("5000.00"));

        StatementService.StatementDocument statement = statementService.generate(
                user.account().getId(), user.customer().getId(),
                BankTime.today().minusMonths(1), BankTime.today());

        assertThat(statement.content()).isNotEmpty();
        // Every PDF begins with the %PDF- magic bytes.
        assertThat(new String(statement.content(), 0, 5,
                java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(statement.filename()).endsWith(".pdf");
        assertThat(statement.filename()).contains(user.account().getAccountNumber());
    }

    @Test
    @DisplayName("an empty period produces a valid statement with zero totals")
    void emptyPeriodIsHandled() {
        var user = factory.customerWithAccount(new BigDecimal("5000.00"));

        StatementService.StatementDocument statement = statementService.generate(
                user.account().getId(), user.customer().getId(),
                BankTime.today().minusYears(1).minusDays(5),
                BankTime.today().minusYears(1));

        assertThat(statement.rowCount()).isZero();
        assertThat(statement.totalDebits()).isEqualByComparingTo("0.00");
        assertThat(statement.totalCredits()).isEqualByComparingTo("0.00");
        assertThat(statement.content()).isNotEmpty();
    }

    @Test
    @DisplayName("the statement content is defensively copied")
    void contentIsDefensivelyCopied() {
        var user = factory.customerWithAccount(new BigDecimal("5000.00"));

        StatementService.StatementDocument statement = statementService.generate(
                user.account().getId(), user.customer().getId(),
                BankTime.today().minusDays(1), BankTime.today());

        byte[] first = statement.content();
        first[0] = 0;

        assertThat(statement.content()[0])
                .as("mutating the returned array must not corrupt the record")
                .isNotEqualTo((byte) 0);
    }
}
