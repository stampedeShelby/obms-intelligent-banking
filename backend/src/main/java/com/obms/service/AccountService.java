package com.obms.service;

import com.obms.common.ApiException;
import com.obms.domain.Account;
import com.obms.domain.Enums;
import com.obms.domain.Transaction;
import com.obms.dto.AccountDtos;
import com.obms.repository.AccountRepository;
import com.obms.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Account and transaction-history reads (FR-3).
 *
 * <p>Every method takes the caller's customer id and scopes its query by it.
 * {@link #requireOwned} is the single ownership gate; no read path bypasses
 * it.</p>
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AccountDtos.AccountSummary> listAccounts(Long customerId) {
        return accountRepository.findByCustomerIdOrderByCreatedAtAsc(customerId).stream()
                .map(AccountDtos.AccountSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountDtos.AccountSummary getAccount(Long accountId, Long customerId) {
        return AccountDtos.AccountSummary.from(requireOwned(accountId, customerId));
    }

    @Transactional(readOnly = true)
    public Page<AccountDtos.TransactionView> history(Long accountId, Long customerId,
                                                      Pageable pageable) {
        requireOwned(accountId, customerId);
        return transactionRepository
                .findByAccountIdOrderByBookedAtDesc(accountId, pageable)
                .map(AccountDtos.TransactionView::from);
    }

    @Transactional(readOnly = true)
    public List<AccountDtos.TransactionView> recentAcrossAccounts(Long customerId) {
        List<Long> accountIds = accountRepository
                .findByCustomerIdOrderByCreatedAtAsc(customerId).stream()
                .map(Account::getId)
                .toList();
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return transactionRepository.findTop10ByAccountIdInOrderByBookedAtDesc(accountIds).stream()
                .map(AccountDtos.TransactionView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountDtos.TransactionView getTransaction(String reference, Long customerId) {
        Transaction txn = transactionRepository.findByReference(reference)
                .orElseThrow(() -> ApiException.notFound("Transaction"));
        if (!txn.getAccount().getCustomer().getId().equals(customerId)) {
            throw ApiException.notOwned("transaction");
        }
        return AccountDtos.TransactionView.from(txn);
    }

    /**
     * Lets a customer correct a category the analytics module assigned.
     *
     * <p>The correction sets {@code categoryUserSet}, which stops the
     * rule-based categoriser from overwriting the human's answer on a later
     * pass.</p>
     */
    @Transactional
    public AccountDtos.TransactionView recategorise(String reference, Long customerId,
                                                     Enums.SpendCategory category) {
        if (category == null) {
            throw ApiException.badRequest("CATEGORY_REQUIRED", "Choose a category.");
        }
        Transaction txn = transactionRepository.findByReference(reference)
                .orElseThrow(() -> ApiException.notFound("Transaction"));
        if (!txn.getAccount().getCustomer().getId().equals(customerId)) {
            throw ApiException.notOwned("transaction");
        }
        txn.setCategory(category);
        txn.setCategoryUserSet(true);
        auditService.record("transaction.recategorised", "Transaction", reference,
                "Category set to " + category);
        return AccountDtos.TransactionView.from(txn);
    }

    /**
     * Loads an account and proves the caller owns it.
     *
     * @throws ApiException 404 when it does not exist, 403 when it is somebody else's
     */
    @Transactional(readOnly = true)
    public Account requireOwned(Long accountId, Long customerId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account"));
        if (!account.getCustomer().getId().equals(customerId)) {
            throw ApiException.notOwned("account");
        }
        return account;
    }
}
