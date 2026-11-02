package com.obms.support;

import com.obms.common.References;
import com.obms.domain.Account;
import com.obms.domain.Beneficiary;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.Kyc;
import com.obms.domain.Role;
import com.obms.domain.UserAccount;
import com.obms.repository.AccountRepository;
import com.obms.repository.BeneficiaryRepository;
import com.obms.repository.CustomerRepository;
import com.obms.repository.KycRepository;
import com.obms.repository.RoleRepository;
import com.obms.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the customers, accounts and payees the integration tests need.
 *
 * <p>Centralised so that a test reads as the behaviour it is asserting rather
 * than as twenty lines of entity setup, and so that every test starts from a
 * customer that is actually in a valid state - verified KYC, an active
 * account, a real password hash.</p>
 */
@TestComponent
@RequiredArgsConstructor
public class TestDataFactory {

    /** Password used for every test user. */
    public static final String PASSWORD = "TestPass@2026!";

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

    private final CustomerRepository customerRepository;
    private final UserAccountRepository userRepository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final KycRepository kycRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a verified customer with one savings account.
     *
     * @param balance opening balance
     * @return the created fixture
     */
    @Transactional
    public Fixture customerWithAccount(BigDecimal balance) {
        return customerWithAccount(balance, new BigDecimal("500.00"), "CUSTOMER");
    }

    /**
     * Creates a customer holding the named role.
     *
     * @param balance        opening balance
     * @param minimumBalance balance floor for the account
     * @param roleName       role to grant, in addition to CUSTOMER
     */
    @Transactional
    public Fixture customerWithAccount(BigDecimal balance, BigDecimal minimumBalance,
                                       String roleName) {
        int n = SEQUENCE.getAndIncrement();
        String email = "user" + n + "@test.obms";

        Customer customer = new Customer();
        customer.setCustomerNumber(References.customerNumber());
        customer.setFirstName("Test");
        customer.setLastName("User" + n);
        customer.setEmail(email);
        customer.setMobile("9" + String.format("%09d", 800000000 + n));
        customer.setCity("Bengaluru");
        customer.setState("Karnataka");
        customer.setPincode("560001");
        customer.setDeclaredMonthlyIncome(new BigDecimal("60000.00"));
        Customer savedCustomer = customerRepository.save(customer);

        Role customerRole = requireRole("CUSTOMER");
        Set<Role> roles = "CUSTOMER".equals(roleName)
                ? Set.of(customerRole)
                : Set.of(customerRole, requireRole(roleName));

        UserAccount user = new UserAccount();
        user.setUsername(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setCustomer(savedCustomer);
        user.setRoles(roles);
        UserAccount savedUser = userRepository.save(user);

        Kyc kyc = new Kyc();
        kyc.setCustomer(savedCustomer);
        kyc.setStatus(Enums.KycStatus.VERIFIED);
        kyc.setIdProofType("AADHAAR");
        kyc.setIdProofMasked("********1234");
        kyc.setAddressProofType("UTILITY_BILL");
        kyc.setSubmittedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        kyc.setReviewedAt(Instant.now().minus(29, ChronoUnit.DAYS));
        kycRepository.save(kyc);

        Account account = new Account();
        account.setAccountNumber(References.accountNumber());
        account.setIfscCode("OBMS0000001");
        account.setCustomer(savedCustomer);
        account.setAccountType(Enums.AccountType.SAVINGS);
        account.setBalance(balance);
        account.setMinimumBalance(minimumBalance);
        account.setDailyTransferLimit(new BigDecimal("100000.00"));
        account.setNickname("Savings");
        Account savedAccount = accountRepository.save(account);

        return new Fixture(savedCustomer, savedUser, savedAccount, email);
    }

    /**
     * Opens an additional account for an existing customer.
     */
    @Transactional
    public Account extraAccount(Customer customer, Enums.AccountType type, BigDecimal balance) {
        Account account = new Account();
        account.setAccountNumber(References.accountNumber());
        account.setIfscCode("OBMS0000001");
        account.setCustomer(customer);
        account.setAccountType(type);
        account.setBalance(balance);
        account.setMinimumBalance(BigDecimal.ZERO);
        account.setDailyTransferLimit(new BigDecimal("100000.00"));
        account.setNickname(type.name());
        return accountRepository.save(account);
    }

    /**
     * Registers a payee and marks it verified.
     *
     * @param verifiedDaysAgo how long ago it was verified; the fraud model
     *                        treats a freshly added payee as higher risk
     */
    @Transactional
    public Beneficiary verifiedBeneficiary(Customer owner, Account target, int verifiedDaysAgo) {
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setOwnerCustomer(owner);
        beneficiary.setBeneficiaryName(target.getCustomer().fullName());
        beneficiary.setAccountNumber(target.getAccountNumber());
        beneficiary.setIfscCode(target.getIfscCode());
        beneficiary.setBankName("OBMS Bank");
        beneficiary.setInternalAccount(true);
        beneficiary.setStatus(Enums.BeneficiaryStatus.ACTIVE);
        beneficiary.setVerifiedAt(Instant.now().minus(verifiedDaysAgo, ChronoUnit.DAYS));
        return beneficiaryRepository.save(beneficiary);
    }

    /**
     * Registers a payee and leaves it awaiting verification.
     */
    @Transactional
    public Beneficiary unverifiedBeneficiary(Customer owner, Account target) {
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setOwnerCustomer(owner);
        beneficiary.setBeneficiaryName(target.getCustomer().fullName());
        beneficiary.setAccountNumber(target.getAccountNumber());
        beneficiary.setIfscCode(target.getIfscCode());
        beneficiary.setBankName("OBMS Bank");
        beneficiary.setInternalAccount(true);
        beneficiary.setStatus(Enums.BeneficiaryStatus.PENDING_VERIFICATION);
        return beneficiaryRepository.save(beneficiary);
    }

    private Role requireRole(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + name + " not seeded. ReferenceDataSeeder should have run."));
    }

    /**
     * A ready-to-use test customer.
     *
     * @param customer the customer record
     * @param user     their login
     * @param account  their savings account
     * @param username their login name
     */
    public record Fixture(Customer customer, UserAccount user, Account account, String username) {
    }
}
