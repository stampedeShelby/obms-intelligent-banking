package com.obms.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A retail banking customer: the human-facing identity that owns accounts.
 *
 * <p>Kept separate from {@link UserAccount} (the login credential) so that the
 * security model and the banking model can evolve independently - a customer
 * could later have multiple logins, or staff could exist without being
 * customers.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customer_email", columnList = "email", unique = true),
        @Index(name = "idx_customer_mobile", columnList = "mobile", unique = true),
        @Index(name = "idx_customer_number", columnList = "customer_number", unique = true)
})
public class Customer extends BaseEntity {

    @Column(name = "customer_number", nullable = false, unique = true, length = 20)
    private String customerNumber;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false, unique = true, length = 15)
    private String mobile;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 60)
    private String city;

    @Column(length = 60)
    private String state;

    @Column(length = 10)
    private String pincode;

    /** Self-declared monthly income, used by the financial health module. */
    @Column(name = "declared_monthly_income", precision = 19, scale = 2)
    private java.math.BigDecimal declaredMonthlyIncome;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Kyc kyc;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<Account> accounts = new ArrayList<>();

    public String fullName() {
        return firstName + " " + lastName;
    }
}
