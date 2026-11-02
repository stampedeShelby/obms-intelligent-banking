package com.obms.config;

import com.obms.domain.Account;
import com.obms.domain.Beneficiary;
import com.obms.domain.Biller;
import com.obms.domain.BillPayment;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.Kyc;
import com.obms.domain.Role;
import com.obms.domain.SavingsGoal;
import com.obms.domain.Transaction;
import com.obms.domain.UserAccount;
import com.obms.common.References;
import com.obms.repository.AccountRepository;
import com.obms.repository.BeneficiaryRepository;
import com.obms.repository.BillPaymentRepository;
import com.obms.repository.BillerRepository;
import com.obms.repository.CustomerRepository;
import com.obms.repository.KycRepository;
import com.obms.repository.RoleRepository;
import com.obms.repository.SavingsGoalRepository;
import com.obms.repository.TransactionRepository;
import com.obms.repository.UserAccountRepository;
import com.obms.service.CategorisationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Populates a demonstrable dataset (module 11).
 *
 * <p>Enabled with {@code obms.demo.enabled=true}. It is off by default so the
 * same build can be deployed without demo accounts, and it is idempotent - it
 * does nothing if the demo customer already exists.</p>
 *
 * <h2>Why the history is generated rather than fixed</h2>
 * <p>The fraud model needs a believable behavioural baseline to detect a
 * deviation from. A handful of hand-written rows would give it nothing to
 * learn: the demo would show a model scoring everything the same way. So this
 * seeder generates six months of plausible salary, rent, groceries, transport
 * and subscription activity with realistic variance, and then adds the
 * specific scenarios the demonstration walks through.</p>
 *
 * <h2>Demo credentials</h2>
 * <p>Printed to the log at startup, and listed in the README. The password is
 * identical across demo accounts and is a known value - these are throwaway
 * accounts in a simulated bank, and labelling them clearly is safer than
 * pretending they are secret.</p>
 */
@Slf4j
@Component
@Order(2)
@ConditionalOnProperty(name = "obms.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    /** Known throwaway password for every demo account. */
    public static final String DEMO_PASSWORD = "Demo@Obms2026!";
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int HISTORY_MONTHS = 6;

    private final CustomerRepository customerRepository;
    private final UserAccountRepository userRepository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final KycRepository kycRepository;
    private final TransactionRepository transactionRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final BillerRepository billerRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final CategorisationService categorisationService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername("priya@demo.obms")) {
            log.info("Demo data already present, skipping seed");
            return;
        }

        log.info("Seeding demo data for OBMS");

        // --- staff accounts, one per role ---------------------------------
        createStaff("admin@demo.obms", "System", "Administrator", "ADMIN");
        createStaff("staff@demo.obms", "Branch", "Officer", "BANK_STAFF");
        createStaff("analyst@demo.obms", "Fraud", "Analyst", "FRAUD_ANALYST");

        // --- primary demo customer, with rich history ---------------------
        Customer priya = createCustomer("priya@demo.obms", "Priya", "Sharma",
                "9845012345", "Bengaluru", "Karnataka", "560064", "72000.00",
                Enums.KycStatus.VERIFIED);
        Account priyaSavings = createAccount(priya, Enums.AccountType.SAVINGS,
                new BigDecimal("184500.00"));
        // Second account for the same customer, so own-account transfers can
        // be demonstrated. The returned entity is not needed here.
        createAccount(priya, Enums.AccountType.CURRENT, new BigDecimal("42000.00"));

        // --- second customer, so transfers have a real counterparty -------
        Customer arjun = createCustomer("arjun@demo.obms", "Arjun", "Mehta",
                "9820098200", "Pune", "Maharashtra", "411001", "55000.00",
                Enums.KycStatus.VERIFIED);
        Account arjunSavings = createAccount(arjun, Enums.AccountType.SAVINGS,
                new BigDecimal("96300.00"));

        // --- third customer, awaiting KYC so the queue is not empty -------
        Customer kavya = createCustomer("kavya@demo.obms", "Kavya", "Rao",
                "9900099000", "Mysuru", "Karnataka", "570001", "40000.00",
                Enums.KycStatus.PENDING);
        createAccount(kavya, Enums.AccountType.SAVINGS, new BigDecimal("12750.00"));

        generateHistory(priyaSavings, new BigDecimal("72000.00"));
        generateHistory(arjunSavings, new BigDecimal("55000.00"));

        createBeneficiaries(priya, arjunSavings);
        createBills(priya, priyaSavings);
        createGoals(priya);

        log.info("""

                ======================================================================
                 OBMS demo data seeded. This is a SIMULATED banking environment.
                 No real funds, accounts or payment networks are involved.

                 Demo sign-in (password is the same for all): {}

                   Customer      priya@demo.obms     (verified, full history)
                   Customer      arjun@demo.obms     (verified, counterparty)
                   Customer      kavya@demo.obms     (KYC pending)
                   Bank staff    staff@demo.obms     (KYC queue)
                   Fraud analyst analyst@demo.obms   (investigation panel)
                   Admin         admin@demo.obms     (audit log, configuration)

                 Two-factor codes are emailed. With no SMTP server configured, set
                 OBMS_OTP_DEBUG=true to have the code written to this log instead.
                ======================================================================
                """, DEMO_PASSWORD);
    }

    // ------------------------------------------------------------------
    // entity creation
    // ------------------------------------------------------------------

    private void createStaff(String email, String firstName, String lastName, String roleName) {
        // A staff member still gets a customer record so the audit trail can
        // resolve their name; the entity itself is not used further here.
        createCustomer(email, firstName, lastName,
                randomMobile(), "Bengaluru", "Karnataka", "560001", null,
                Enums.KycStatus.VERIFIED);

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role " + roleName + " not seeded"));
        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("Role CUSTOMER not seeded"));

        UserAccount user = userRepository.findByUsername(email)
                .orElseThrow(() -> new IllegalStateException("Staff user not created"));
        // Staff keep a customer role too, so a staff member can be shown the
        // customer-facing screens during a demonstration without a second login.
        // A mutable set is required: Spring Data's save() on an already
        // persistent entity goes through Hibernate merge, which clears the
        // target collection before copying into it. An immutable Set.of
        // throws UnsupportedOperationException at that point.
        user.setRoles(new HashSet<>(List.of(role, customerRole)));
        userRepository.save(user);

        log.debug("Created staff account {} with role {}", email, roleName);
    }

    private Customer createCustomer(String email, String firstName, String lastName,
                                    String mobile, String city, String state, String pincode,
                                    String monthlyIncome, Enums.KycStatus kycStatus) {
        Customer customer = new Customer();
        customer.setCustomerNumber(References.customerNumber());
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setEmail(email);
        customer.setMobile(mobile);
        customer.setCity(city);
        customer.setState(state);
        customer.setPincode(pincode);
        customer.setAddressLine(randomAddress(city));
        customer.setDateOfBirth(LocalDate.now().minusYears(24 + RANDOM.nextInt(16)));
        if (monthlyIncome != null) {
            customer.setDeclaredMonthlyIncome(new BigDecimal(monthlyIncome));
        }
        Customer saved = customerRepository.save(customer);

        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("Role CUSTOMER not seeded"));

        UserAccount user = new UserAccount();
        user.setUsername(email);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setCustomer(saved);
        user.setRoles(new HashSet<>(List.of(customerRole)));
        userRepository.save(user);

        Kyc kyc = new Kyc();
        kyc.setCustomer(saved);
        kyc.setStatus(kycStatus);
        if (kycStatus != Enums.KycStatus.NOT_SUBMITTED) {
            kyc.setIdProofType("AADHAAR");
            kyc.setIdProofMasked("********" + (1000 + RANDOM.nextInt(9000)));
            kyc.setAddressProofType("UTILITY_BILL");
            kyc.setSubmittedAt(Instant.now().minus(20, ChronoUnit.DAYS));
        }
        if (kycStatus == Enums.KycStatus.VERIFIED) {
            kyc.setReviewedAt(Instant.now().minus(18, ChronoUnit.DAYS));
            kyc.setReviewedBy("staff@demo.obms");
        }
        kycRepository.save(kyc);

        return saved;
    }

    private Account createAccount(Customer customer, Enums.AccountType type, BigDecimal balance) {
        Account account = new Account();
        account.setAccountNumber(References.accountNumber());
        account.setIfscCode(properties.getBank().getIfsc());
        account.setCustomer(customer);
        account.setAccountType(type);
        account.setBalance(balance);
        account.setMinimumBalance(type == Enums.AccountType.SAVINGS
                ? new BigDecimal("500.00") : BigDecimal.ZERO);
        account.setDailyTransferLimit(properties.getTransfer().getDefaultDailyLimit());
        account.setNickname(type == Enums.AccountType.SAVINGS ? "Savings" : "Current");
        return accountRepository.save(account);
    }

    // ------------------------------------------------------------------
    // history generation
    // ------------------------------------------------------------------

    /**
     * Generates a plausible six-month history ending today.
     *
     * <p>Balances are reconstructed forward from a computed starting point so
     * that the final {@code balanceAfter} matches the account balance set
     * above. A statement that does not reconcile would be visible immediately
     * in the demonstration.</p>
     */
    private void generateHistory(Account account, BigDecimal monthlySalary) {
        List<Transaction> rows = new ArrayList<>();
        LocalDate today = LocalDate.now(ZONE);
        LocalDate cursor = today.minusMonths(HISTORY_MONTHS).withDayOfMonth(1);

        while (!cursor.isAfter(today)) {
            // Salary on the first working day of the month.
            if (cursor.getDayOfMonth() == 1) {
                rows.add(row(account, Enums.TransactionDirection.CREDIT,
                        Enums.TransactionType.DEPOSIT, vary(monthlySalary, 2),
                        "Salary credit", "Employer Payroll", cursor, 10));

                rows.add(row(account, Enums.TransactionDirection.DEBIT,
                        Enums.TransactionType.BILL_PAYMENT, new BigDecimal("18000.00"),
                        "Monthly rent - landlord", "Landlord", cursor.plusDays(1), 11));

                rows.add(row(account, Enums.TransactionDirection.DEBIT,
                        Enums.TransactionType.BILL_PAYMENT, vary(new BigDecimal("649.00"), 0),
                        "Netflix subscription", "Netflix", cursor.plusDays(2), 9));

                rows.add(row(account, Enums.TransactionDirection.DEBIT,
                        Enums.TransactionType.BILL_PAYMENT, vary(new BigDecimal("1199.00"), 0),
                        "ACT Fibernet Broadband - 1204567", "ACT Fibernet Broadband",
                        cursor.plusDays(3), 20));
            }

            if (cursor.getDayOfMonth() == 8) {
                rows.add(row(account, Enums.TransactionDirection.DEBIT,
                        Enums.TransactionType.BILL_PAYMENT, vary(new BigDecimal("2150.00"), 12),
                        "BESCOM Electricity - 9900123", "BESCOM Electricity", cursor, 19));
            }

            // Groceries roughly weekly.
            if (cursor.getDayOfWeek().getValue() == 6) {
                rows.add(row(account, Enums.TransactionDirection.DEBIT,
                        Enums.TransactionType.WITHDRAWAL, vary(new BigDecimal("2400.00"), 25),
                        "BigBasket grocery order", "BigBasket", cursor, 12));
            }

            // Food delivery and transport on scattered days.
            if (RANDOM.nextInt(100) < 35) {
                rows.add(row(account, Enums.TransactionDirection.DEBIT,
                        Enums.TransactionType.WITHDRAWAL, vary(new BigDecimal("430.00"), 45),
                        "Swiggy order", "Swiggy", cursor, 13 + RANDOM.nextInt(8)));
            }
            if (RANDOM.nextInt(100) < 30) {
                rows.add(row(account, Enums.TransactionDirection.DEBIT,
                        Enums.TransactionType.WITHDRAWAL, vary(new BigDecimal("185.00"), 50),
                        "Uber ride", "Uber", cursor, 9 + RANDOM.nextInt(10)));
            }
            if (RANDOM.nextInt(100) < 12) {
                rows.add(row(account, Enums.TransactionDirection.DEBIT,
                        Enums.TransactionType.WITHDRAWAL, vary(new BigDecimal("1850.00"), 60),
                        "Amazon order", "Amazon", cursor, 14 + RANDOM.nextInt(7)));
            }
            if (RANDOM.nextInt(100) < 6) {
                rows.add(row(account, Enums.TransactionDirection.DEBIT,
                        Enums.TransactionType.WITHDRAWAL, vary(new BigDecimal("780.00"), 40),
                        "Apollo Pharmacy", "Apollo Pharmacy", cursor, 11 + RANDOM.nextInt(8)));
            }

            cursor = cursor.plusDays(1);
        }

        rows.sort(java.util.Comparator.comparing(Transaction::getBookedAt));

        // Work out the opening balance that lands on the account's current one.
        BigDecimal netEffect = BigDecimal.ZERO;
        for (Transaction row : rows) {
            netEffect = row.getDirection() == Enums.TransactionDirection.CREDIT
                    ? netEffect.add(row.getAmount())
                    : netEffect.subtract(row.getAmount());
        }

        BigDecimal running = account.getBalance().subtract(netEffect);
        for (Transaction row : rows) {
            running = row.getDirection() == Enums.TransactionDirection.CREDIT
                    ? running.add(row.getAmount())
                    : running.subtract(row.getAmount());
            row.setBalanceAfter(running);
            categorisationService.applyTo(row);
        }

        transactionRepository.saveAll(rows);
        log.info("Seeded {} transactions for account {}", rows.size(), account.getAccountNumber());
    }

    private Transaction row(Account account, Enums.TransactionDirection direction,
                            Enums.TransactionType type, BigDecimal amount,
                            String description, String counterparty, LocalDate date, int hour) {
        Transaction txn = new Transaction();
        txn.setReference(References.transactionReference());
        txn.setAccount(account);
        txn.setDirection(direction);
        txn.setTransactionType(type);
        txn.setStatus(Enums.TransactionStatus.COMPLETED);
        txn.setAmount(amount);
        txn.setDescription(description);
        txn.setCounterpartyName(counterparty);
        txn.setBookedAt(LocalDateTime.of(date, java.time.LocalTime.of(
                        Math.min(hour, 23), RANDOM.nextInt(60)))
                .atZone(ZONE).toInstant());
        return txn;
    }

    // ------------------------------------------------------------------
    // supporting demo records
    // ------------------------------------------------------------------

    private void createBeneficiaries(Customer owner, Account counterpartyAccount) {
        Beneficiary verified = new Beneficiary();
        verified.setOwnerCustomer(owner);
        verified.setBeneficiaryName(counterpartyAccount.getCustomer().fullName());
        verified.setAccountNumber(counterpartyAccount.getAccountNumber());
        verified.setIfscCode(counterpartyAccount.getIfscCode());
        verified.setNickname("Arjun - flatmate");
        verified.setBankName(properties.getBank().getName());
        verified.setInternalAccount(true);
        verified.setStatus(Enums.BeneficiaryStatus.ACTIVE);
        // Verified months ago, so the fraud model treats it as established.
        verified.setVerifiedAt(Instant.now().minus(95, ChronoUnit.DAYS));
        beneficiaryRepository.save(verified);
    }

    private void createBills(Customer customer, Account account) {
        List<Biller> billers = billerRepository.findByActiveTrueOrderByNameAsc();
        if (billers.isEmpty()) {
            return;
        }

        // Two unpaid bills due soon, so the dashboard reminder panel has content.
        Biller electricity = billers.stream()
                .filter(b -> b.getCategory() == Enums.BillerCategory.ELECTRICITY)
                .findFirst().orElse(billers.get(0));
        Biller broadband = billers.stream()
                .filter(b -> b.getCategory() == Enums.BillerCategory.BROADBAND)
                .findFirst().orElse(billers.get(0));

        billPaymentRepository.save(pendingBill(customer, account, electricity,
                "9900123", new BigDecimal("2310.00"), LocalDate.now(ZONE).plusDays(5)));
        billPaymentRepository.save(pendingBill(customer, account, broadband,
                "1204567", new BigDecimal("1199.00"), LocalDate.now(ZONE).plusDays(11)));
    }

    private BillPayment pendingBill(Customer customer, Account account, Biller biller,
                                    String identifier, BigDecimal amount, LocalDate dueDate) {
        BillPayment bill = new BillPayment();
        bill.setReference(References.billPaymentReference());
        bill.setCustomer(customer);
        bill.setAccount(account);
        bill.setBiller(biller);
        bill.setConsumerIdentifier(identifier);
        bill.setAmount(amount);
        bill.setDueDate(dueDate);
        bill.setStatus(Enums.TransactionStatus.PENDING);
        return bill;
    }

    private void createGoals(Customer customer) {
        SavingsGoal emergency = new SavingsGoal();
        emergency.setCustomer(customer);
        emergency.setName("Emergency fund");
        emergency.setTargetAmount(new BigDecimal("150000.00"));
        emergency.setSavedAmount(new BigDecimal("62000.00"));
        emergency.setTargetDate(LocalDate.now(ZONE).plusMonths(10));
        emergency.setIcon("shield");
        savingsGoalRepository.save(emergency);

        SavingsGoal laptop = new SavingsGoal();
        laptop.setCustomer(customer);
        laptop.setName("New laptop");
        laptop.setTargetAmount(new BigDecimal("95000.00"));
        laptop.setSavedAmount(new BigDecimal("18500.00"));
        laptop.setTargetDate(LocalDate.now(ZONE).plusMonths(4));
        laptop.setIcon("laptop");
        savingsGoalRepository.save(laptop);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Applies up to the given percentage of random variation.
     *
     * <p>Variation is what gives the fraud model a standard deviation to work
     * with. A history of identical amounts would make every new transaction
     * look like an outlier.</p>
     */
    private BigDecimal vary(BigDecimal base, int percent) {
        if (percent <= 0) {
            return base.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        int swing = RANDOM.nextInt(percent * 2 + 1) - percent;
        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(swing).divide(BigDecimal.valueOf(100), 4,
                        java.math.RoundingMode.HALF_UP));
        return base.multiply(multiplier).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String randomMobile() {
        StringBuilder sb = new StringBuilder("9");
        for (int i = 0; i < 9; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private String randomAddress(String city) {
        return (100 + RANDOM.nextInt(800)) + ", "
                + List.of("MG Road", "Park Street", "Lake View Road", "Church Street",
                        "Residency Road").get(RANDOM.nextInt(5))
                + ", " + city;
    }
}
