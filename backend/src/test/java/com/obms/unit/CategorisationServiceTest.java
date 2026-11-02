package com.obms.unit;

import com.obms.domain.Account;
import com.obms.domain.Enums;
import com.obms.domain.Transaction;
import com.obms.service.CategorisationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the spending categoriser.
 *
 * <p>No Spring context: the categoriser is deliberately a pure function of the
 * transaction, which is what makes it fast to test and easy to explain.</p>
 */
@DisplayName("Unit: transaction categorisation")
class CategorisationServiceTest {

    private final CategorisationService service = new CategorisationService();

    private Transaction debit(String description, String counterparty) {
        Transaction txn = new Transaction();
        txn.setAccount(new Account());
        txn.setDirection(Enums.TransactionDirection.DEBIT);
        txn.setTransactionType(Enums.TransactionType.WITHDRAWAL);
        txn.setStatus(Enums.TransactionStatus.COMPLETED);
        txn.setAmount(new BigDecimal("500.00"));
        txn.setDescription(description);
        txn.setCounterpartyName(counterparty);
        return txn;
    }

    @ParameterizedTest(name = "\"{0}\" is categorised as {1}")
    @CsvSource({
            "Swiggy order,FOOD",
            "BigBasket grocery order,FOOD",
            "Amazon order,SHOPPING",
            "Uber ride,TRANSPORT",
            "BESCOM Electricity - 9900123,UTILITIES",
            "Apollo Pharmacy,HEALTHCARE",
            "Netflix subscription,ENTERTAINMENT",
            "Monthly rent - landlord,RENT",
            "Udemy course payment,EDUCATION"
    })
    @DisplayName("recognises common merchant descriptions")
    void classifiesKnownMerchants(String description, Enums.SpendCategory expected) {
        CategorisationService.Result result = service.classify(debit(description, null));
        assertThat(result.category()).isEqualTo(expected);
    }

    @Test
    @DisplayName("returns an explanation naming the keyword that matched")
    void explainsTheMatch() {
        CategorisationService.Result result = service.classify(debit("Swiggy order", null));

        assertThat(result.explanation())
                .as("the customer should be able to see why a category was chosen")
                .contains("swiggy");
    }

    @Test
    @DisplayName("falls back to OTHER rather than guessing")
    void unmatchedBecomesOther() {
        CategorisationService.Result result = service.classify(debit("QX-7741 settlement", null));

        assertThat(result.category()).isEqualTo(Enums.SpendCategory.OTHER);
        assertThat(result.explanation()).contains("No category rule matched");
    }

    @Test
    @DisplayName("credits are treated as income, not spending")
    void creditsAreIncome() {
        Transaction credit = debit("Salary credit", "Employer Payroll");
        credit.setDirection(Enums.TransactionDirection.CREDIT);

        assertThat(service.classify(credit).category()).isEqualTo(Enums.SpendCategory.INCOME);
    }

    @Test
    @DisplayName("internal transfers are not counted as spending")
    void internalTransfersAreTransfers() {
        Transaction transfer = debit("Transfer to savings", "Own account");
        transfer.setTransactionType(Enums.TransactionType.INTERNAL_TRANSFER);

        assertThat(service.classify(transfer).category()).isEqualTo(Enums.SpendCategory.TRANSFER);
    }

    @Test
    @DisplayName("a manual correction is never overwritten")
    void respectsManualOverride() {
        Transaction txn = debit("Swiggy order", null);
        txn.setCategory(Enums.SpendCategory.ENTERTAINMENT);
        txn.setCategoryUserSet(true);

        boolean changed = service.applyTo(txn);

        assertThat(changed).isFalse();
        assertThat(txn.getCategory())
                .as("the customer's own choice outranks the keyword rule")
                .isEqualTo(Enums.SpendCategory.ENTERTAINMENT);
    }

    @Test
    @DisplayName("applyTo assigns a category when none is set")
    void appliesWhenUnset() {
        Transaction txn = debit("Uber ride", null);

        assertThat(service.applyTo(txn)).isTrue();
        assertThat(txn.getCategory()).isEqualTo(Enums.SpendCategory.TRANSPORT);
    }

    @Test
    @DisplayName("matches against the counterparty when there is no description")
    void usesCounterpartyName() {
        CategorisationService.Result result = service.classify(debit(null, "Flipkart"));

        assertThat(result.category()).isEqualTo(Enums.SpendCategory.SHOPPING);
    }
}
