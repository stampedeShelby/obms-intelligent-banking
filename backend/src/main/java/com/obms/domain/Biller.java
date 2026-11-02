package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A biller in the simulated biller ecosystem (FR-6).
 *
 * <p>This is a fixed catalogue seeded at startup, standing in for a real
 * Bharat BillPay style aggregator. No external network is contacted.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "billers")
public class Biller extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Enums.BillerCategory category;

    /** Spend category applied to payments made to this biller. */
    @Enumerated(EnumType.STRING)
    @Column(name = "spend_category", nullable = false, length = 25)
    private Enums.SpendCategory spendCategory;

    /** Label for the account identifier this biller expects, e.g. "Consumer No.". */
    @Column(name = "identifier_label", nullable = false, length = 60)
    private String identifierLabel;

    @Column(name = "min_amount", precision = 19, scale = 2)
    private BigDecimal minAmount = new BigDecimal("1.00");

    @Column(name = "max_amount", precision = 19, scale = 2)
    private BigDecimal maxAmount = new BigDecimal("200000.00");

    @Column(nullable = false)
    private boolean active = true;
}
