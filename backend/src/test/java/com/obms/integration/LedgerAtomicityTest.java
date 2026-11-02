package com.obms.integration;

import com.obms.common.ApiException;
import com.obms.domain.Account;
import com.obms.domain.Enums;
import com.obms.domain.Transaction;
import com.obms.repository.AccountRepository;
import com.obms.repository.TransactionRepository;
import com.obms.service.LedgerService;
import com.obms.support.IntegrationTestBase;
import com.obms.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the ledger: the critical banking rule from the
 * specification is that debit and credit must both succeed or both fail, and
 * that a balance can never go negative.
 */
@DisplayName("Integration: ledger atomicity and balance integrity")
class LedgerAtomicityTest extends IntegrationTestBase {

    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private TestDataFactory factory;

    @Test
    @DisplayName("a successful transfer debits and credits by the same amount")
    void transferMovesMoneyExactly() {
        var sender = factory.customerWithAccount(new BigDecimal("10000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("2000.00"));

        ledgerService.postDoubleEntry(sender.account().getId(), receiver.account().getId(),
                new BigDecimal("1500.00"), Enums.TransactionType.INTERNAL_TRANSFER,
                "Test transfer", Enums.SpendCategory.TRANSFER);

        Account source = accountRepository.findById(sender.account().getId()).orElseThrow();
        Account destination = accountRepository.findById(receiver.account().getId()).orElseThrow();

        assertThat(source.getBalance()).isEqualByComparingTo("8500.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("3500.00");
    }

    @Test
    @DisplayName("a transfer writes exactly two ledger rows sharing one group reference")
    void transferWritesBothLegs() {
        var sender = factory.customerWithAccount(new BigDecimal("10000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));

        LedgerService.DoubleEntry entry = ledgerService.postDoubleEntry(
                sender.account().getId(), receiver.account().getId(),
                new BigDecimal("750.00"), Enums.TransactionType.INTERNAL_TRANSFER,
                "Double entry check", Enums.SpendCategory.TRANSFER);

        List<Transaction> legs = transactionRepository.findByTransferGroupRef(entry.groupRef());

        assertThat(legs).hasSize(2);
        assertThat(legs).extracting(Transaction::getDirection)
                .containsExactlyInAnyOrder(Enums.TransactionDirection.DEBIT,
                        Enums.TransactionDirection.CREDIT);
        assertThat(legs).allSatisfy(leg ->
                assertThat(leg.getAmount()).isEqualByComparingTo("750.00"));
        assertThat(legs).allSatisfy(leg ->
                assertThat(leg.getStatus()).isEqualTo(Enums.TransactionStatus.COMPLETED));
    }

    @Test
    @DisplayName("balanceAfter is recorded on both legs so a statement reconciles")
    void recordsBalanceAfter() {
        var sender = factory.customerWithAccount(new BigDecimal("5000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("1000.00"));

        LedgerService.DoubleEntry entry = ledgerService.postDoubleEntry(
                sender.account().getId(), receiver.account().getId(),
                new BigDecimal("1000.00"), Enums.TransactionType.INTERNAL_TRANSFER,
                "Balance after check", Enums.SpendCategory.TRANSFER);

        assertThat(entry.debit().getBalanceAfter()).isEqualByComparingTo("4000.00");
        assertThat(entry.credit().getBalanceAfter()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("an insufficient-funds transfer is rejected and nothing is written")
    void insufficientFundsLeavesNoTrace() {
        var sender = factory.customerWithAccount(new BigDecimal("1000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("500.00"));

        assertThatThrownBy(() -> ledgerService.postDoubleEntry(
                sender.account().getId(), receiver.account().getId(),
                new BigDecimal("5000.00"), Enums.TransactionType.INTERNAL_TRANSFER,
                "Too much", Enums.SpendCategory.TRANSFER))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("enough available balance");

        Account source = accountRepository.findById(sender.account().getId()).orElseThrow();
        Account destination = accountRepository.findById(receiver.account().getId()).orElseThrow();

        assertThat(source.getBalance())
                .as("a rejected transfer must leave the source untouched")
                .isEqualByComparingTo("1000.00");
        assertThat(destination.getBalance())
                .as("and must not have credited the destination")
                .isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("the minimum balance floor is enforced, not just a zero balance")
    void respectsMinimumBalance() {
        // 1000 balance with a 500 floor leaves 500 available, not 1000.
        var sender = factory.customerWithAccount(new BigDecimal("1000.00"),
                new BigDecimal("500.00"), "CUSTOMER");
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));

        assertThatThrownBy(() -> ledgerService.postDoubleEntry(
                sender.account().getId(), receiver.account().getId(),
                new BigDecimal("600.00"), Enums.TransactionType.INTERNAL_TRANSFER,
                "Breaches floor", Enums.SpendCategory.TRANSFER))
                .isInstanceOf(ApiException.class);

        assertThat(accountRepository.findById(sender.account().getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("a transfer to the same account is rejected")
    void rejectsSelfTransfer() {
        var customer = factory.customerWithAccount(new BigDecimal("5000.00"));

        assertThatThrownBy(() -> ledgerService.postDoubleEntry(
                customer.account().getId(), customer.account().getId(),
                new BigDecimal("100.00"), Enums.TransactionType.INTERNAL_TRANSFER,
                "Self", Enums.SpendCategory.TRANSFER))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("must differ");
    }

    @Test
    @DisplayName("a zero or negative amount is rejected")
    void rejectsNonPositiveAmount() {
        var sender = factory.customerWithAccount(new BigDecimal("5000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));

        assertThatThrownBy(() -> ledgerService.postDoubleEntry(
                sender.account().getId(), receiver.account().getId(),
                BigDecimal.ZERO, Enums.TransactionType.INTERNAL_TRANSFER,
                "Zero", Enums.SpendCategory.TRANSFER))
                .isInstanceOf(ApiException.class);

        assertThatThrownBy(() -> ledgerService.postDoubleEntry(
                sender.account().getId(), receiver.account().getId(),
                new BigDecimal("-50.00"), Enums.TransactionType.INTERNAL_TRANSFER,
                "Negative", Enums.SpendCategory.TRANSFER))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("an amount with sub-paisa precision is rejected")
    void rejectsExcessivePrecision() {
        var sender = factory.customerWithAccount(new BigDecimal("5000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));

        assertThatThrownBy(() -> ledgerService.postDoubleEntry(
                sender.account().getId(), receiver.account().getId(),
                new BigDecimal("10.001"), Enums.TransactionType.INTERNAL_TRANSFER,
                "Too precise", Enums.SpendCategory.TRANSFER))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("two decimal places");
    }

    @Test
    @DisplayName("money cannot leave a frozen account")
    void frozenAccountCannotSend() {
        var sender = factory.customerWithAccount(new BigDecimal("5000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));

        Account frozen = accountRepository.findById(sender.account().getId()).orElseThrow();
        frozen.setStatus(Enums.AccountStatus.FROZEN);
        accountRepository.saveAndFlush(frozen);

        assertThatThrownBy(() -> ledgerService.postDoubleEntry(
                sender.account().getId(), receiver.account().getId(),
                new BigDecimal("100.00"), Enums.TransactionType.INTERNAL_TRANSFER,
                "From frozen", Enums.SpendCategory.TRANSFER))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("FROZEN");
    }

    /**
     * The concurrency test that justifies the locking strategy.
     *
     * <p>Twelve threads each try to move 1,000 out of an account holding
     * 5,000 with no minimum balance. At most five can legitimately succeed.
     * Without row locking, several threads would read the same sufficient
     * balance and all commit, overdrawing the account.</p>
     */
    @Test
    @DisplayName("concurrent transfers cannot overdraw an account")
    void concurrentTransfersCannotOverdraw() throws Exception {
        var sender = factory.customerWithAccount(new BigDecimal("5000.00"),
                BigDecimal.ZERO, "CUSTOMER");
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"),
                BigDecimal.ZERO, "CUSTOMER");

        int threads = 12;
        BigDecimal amount = new BigDecimal("1000.00");
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> {
                    try {
                        ledgerService.postDoubleEntry(
                                sender.account().getId(), receiver.account().getId(), amount,
                                Enums.TransactionType.INTERNAL_TRANSFER,
                                "Concurrent", Enums.SpendCategory.TRANSFER);
                        succeeded.incrementAndGet();
                    } catch (RuntimeException ex) {
                        // Insufficient funds, a lock timeout or an optimistic
                        // lock failure are all correct outcomes here. What is
                        // not acceptable is a committed overdraft.
                        rejected.incrementAndGet();
                    }
                    return null;
                });
            }
            for (Future<Void> future : pool.invokeAll(tasks)) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Account source = accountRepository.findById(sender.account().getId()).orElseThrow();
        Account destination = accountRepository.findById(receiver.account().getId()).orElseThrow();

        assertThat(source.getBalance())
                .as("the account must never be overdrawn, whatever the interleaving")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(succeeded.get())
                .as("at most five transfers of 1000 fit in a balance of 5000")
                .isLessThanOrEqualTo(5);
        assertThat(succeeded.get() + rejected.get()).isEqualTo(threads);

        // Conservation: what left the source must equal what reached the
        // destination. A half-applied transfer would break this.
        BigDecimal moved = new BigDecimal("5000.00").subtract(source.getBalance());
        assertThat(destination.getBalance())
                .as("debits and credits must balance exactly")
                .isEqualByComparingTo(moved);
    }

    @Test
    @DisplayName("a single-sided debit works for payments leaving the bank")
    void singleSidedDebit() {
        var customer = factory.customerWithAccount(new BigDecimal("3000.00"),
                BigDecimal.ZERO, "CUSTOMER");

        Transaction debit = ledgerService.postDebit(customer.account().getId(),
                new BigDecimal("1200.00"), Enums.TransactionType.BILL_PAYMENT,
                "BESCOM Electricity - 123", "BESCOM Electricity",
                Enums.SpendCategory.UTILITIES);

        assertThat(debit.getDirection()).isEqualTo(Enums.TransactionDirection.DEBIT);
        assertThat(debit.getBalanceAfter()).isEqualByComparingTo("1800.00");
        assertThat(accountRepository.findById(customer.account().getId()).orElseThrow()
                .getBalance()).isEqualByComparingTo("1800.00");
    }

    @Test
    @DisplayName("a single-sided credit works for deposits entering the bank")
    void singleSidedCredit() {
        var customer = factory.customerWithAccount(new BigDecimal("1000.00"));

        Transaction credit = ledgerService.postCredit(customer.account().getId(),
                new BigDecimal("4500.00"), Enums.TransactionType.DEPOSIT,
                "Salary credit", "Employer Payroll", Enums.SpendCategory.INCOME);

        assertThat(credit.getDirection()).isEqualTo(Enums.TransactionDirection.CREDIT);
        assertThat(credit.getBalanceAfter()).isEqualByComparingTo("5500.00");
    }
}
