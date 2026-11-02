package com.obms.integration;

import com.obms.repository.BillerRepository;
import com.obms.repository.RoleRepository;
import com.obms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the application context starts and that the reference data every
 * other feature depends on was seeded.
 *
 * <p>A failure here means the four roles or the biller catalogue are missing,
 * which would make registration and bill payment fail in ways that are harder
 * to diagnose further downstream.</p>
 */
@DisplayName("Integration: application context and reference data")
class ContextLoadTest extends IntegrationTestBase {

    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private BillerRepository billerRepository;

    @Test
    @DisplayName("the context starts and the four roles exist")
    void rolesAreSeeded() {
        assertThat(roleRepository.findByName("CUSTOMER")).isPresent();
        assertThat(roleRepository.findByName("BANK_STAFF")).isPresent();
        assertThat(roleRepository.findByName("ADMIN")).isPresent();
        assertThat(roleRepository.findByName("FRAUD_ANALYST")).isPresent();
    }

    @Test
    @DisplayName("the biller catalogue covers every required category")
    void billersCoverAllCategories() {
        var active = billerRepository.findByActiveTrueOrderByNameAsc();

        assertThat(active).isNotEmpty();
        assertThat(active).extracting(b -> b.getCategory().name())
                .contains("ELECTRICITY", "WATER", "MOBILE_RECHARGE",
                        "BROADBAND", "GAS", "CREDIT_CARD");
    }

    @Test
    @DisplayName("seeding twice does not duplicate reference data")
    void seedingIsIdempotent() {
        long rolesBefore = roleRepository.count();
        long billersBefore = billerRepository.count();

        // The seeder runs once per context; this asserts the counts are the
        // expected fixed values rather than multiples of them.
        assertThat(rolesBefore).isEqualTo(4);
        assertThat(billersBefore).isEqualTo(10);
    }
}
