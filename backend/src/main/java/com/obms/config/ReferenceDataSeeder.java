package com.obms.config;

import com.obms.domain.Biller;
import com.obms.domain.Enums;
import com.obms.domain.Role;
import com.obms.repository.BillerRepository;
import com.obms.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds reference data the application cannot run without.
 *
 * <p>Distinct from the demo seeder: these rows are structural. Roles must exist
 * before anyone can register, and the biller catalogue is part of the product
 * rather than sample data. This runs in every profile, including production,
 * and is idempotent - it inserts only what is missing.</p>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class ReferenceDataSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final BillerRepository billerRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedBillers();
    }

    private void seedRoles() {
        List<Role> roles = List.of(
                new Role("CUSTOMER", "Retail banking customer"),
                new Role("BANK_STAFF", "Branch staff: onboarding and KYC review"),
                new Role("ADMIN", "System administrator: configuration and audit"),
                new Role("FRAUD_ANALYST", "Reviews and dispositions fraud alerts"));

        int created = 0;
        for (Role role : roles) {
            if (roleRepository.findByName(role.getName()).isEmpty()) {
                roleRepository.save(role);
                created++;
            }
        }
        if (created > 0) {
            log.info("Seeded {} role(s)", created);
        }
    }

    /**
     * The simulated biller catalogue backing FR-6.
     *
     * <p>These stand in for a real bill-payment aggregator. No external network
     * is contacted when a payment is made against them.</p>
     */
    private void seedBillers() {
        List<Biller> billers = List.of(
                biller("BESCOM", "BESCOM Electricity", Enums.BillerCategory.ELECTRICITY,
                        Enums.SpendCategory.UTILITIES, "Consumer Number",
                        "50.00", "50000.00"),
                biller("MSEDCL", "MSEDCL Power", Enums.BillerCategory.ELECTRICITY,
                        Enums.SpendCategory.UTILITIES, "Consumer Number",
                        "50.00", "50000.00"),
                biller("BWSSB", "BWSSB Water Supply", Enums.BillerCategory.WATER,
                        Enums.SpendCategory.UTILITIES, "RR Number",
                        "50.00", "20000.00"),
                biller("AIRTEL-MOB", "Airtel Mobile", Enums.BillerCategory.MOBILE_RECHARGE,
                        Enums.SpendCategory.UTILITIES, "Mobile Number",
                        "10.00", "5000.00"),
                biller("JIO-MOB", "Jio Mobile", Enums.BillerCategory.MOBILE_RECHARGE,
                        Enums.SpendCategory.UTILITIES, "Mobile Number",
                        "10.00", "5000.00"),
                biller("ACT-BB", "ACT Fibernet Broadband", Enums.BillerCategory.BROADBAND,
                        Enums.SpendCategory.UTILITIES, "Account Number",
                        "100.00", "30000.00"),
                biller("AIRTEL-BB", "Airtel Xstream Broadband", Enums.BillerCategory.BROADBAND,
                        Enums.SpendCategory.UTILITIES, "Landline Number",
                        "100.00", "30000.00"),
                biller("IOC-GAS", "Indane Gas", Enums.BillerCategory.GAS,
                        Enums.SpendCategory.UTILITIES, "Consumer Number",
                        "100.00", "10000.00"),
                biller("GAIL-GAS", "GAIL Piped Gas", Enums.BillerCategory.GAS,
                        Enums.SpendCategory.UTILITIES, "Consumer Number",
                        "100.00", "10000.00"),
                biller("OBMS-CC", "OBMS Credit Card", Enums.BillerCategory.CREDIT_CARD,
                        Enums.SpendCategory.UTILITIES, "Card Last 4 Digits",
                        "100.00", "200000.00"));

        int created = 0;
        for (Biller biller : billers) {
            if (billerRepository.findByCode(biller.getCode()).isEmpty()) {
                billerRepository.save(biller);
                created++;
            }
        }
        if (created > 0) {
            log.info("Seeded {} biller(s)", created);
        }
    }

    private Biller biller(String code, String name, Enums.BillerCategory category,
                          Enums.SpendCategory spendCategory, String identifierLabel,
                          String min, String max) {
        Biller biller = new Biller();
        biller.setCode(code);
        biller.setName(name);
        biller.setCategory(category);
        biller.setSpendCategory(spendCategory);
        biller.setIdentifierLabel(identifierLabel);
        biller.setMinAmount(new BigDecimal(min));
        biller.setMaxAmount(new BigDecimal(max));
        biller.setActive(true);
        return biller;
    }
}
