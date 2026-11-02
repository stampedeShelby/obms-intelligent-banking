package com.obms.unit;

import com.obms.common.BankTime;
import com.obms.common.References;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the customer-visible identifier formats.
 *
 * <p>The date assertions here exist because these references were being
 * stamped with {@code LocalDate.now()} in the server's zone. The application
 * runs in a UTC container, so a transaction made between midnight and 05:30
 * IST received a reference dated the previous day - a wrong date on the exact
 * identifier a customer quotes to support.</p>
 */
class ReferencesTest {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");

    private String expectedDatePart() {
        return BankTime.today().format(DATE_PART);
    }

    @Test
    @DisplayName("a transaction reference carries the bank's date, not the server's")
    void transactionReferenceUsesBankDate() {
        String reference = References.transactionReference();
        assertThat(reference).startsWith("TXN" + expectedDatePart());
    }

    @Test
    @DisplayName("a transfer group reference carries the bank's date")
    void transferGroupReferenceUsesBankDate() {
        assertThat(References.transferGroupReference())
                .startsWith("TRF" + expectedDatePart());
    }

    @Test
    @DisplayName("a bill payment reference carries the bank's date")
    void billReferenceUsesBankDate() {
        assertThat(References.billPaymentReference())
                .startsWith("BIL" + expectedDatePart());
    }

    @Test
    @DisplayName("references are the documented length and shape")
    void referenceShape() {
        assertThat(References.transactionReference()).hasSize(17).matches("TXN\\d{8}[A-Z0-9]{6}");
        assertThat(References.billPaymentReference()).hasSize(17).matches("BIL\\d{8}[A-Z0-9]{6}");
        assertThat(References.accountNumber()).hasSize(14).matches("100\\d{11}");
        assertThat(References.customerNumber()).matches("CUS[A-Z0-9]{6}");
    }

    @Test
    @DisplayName("references do not collide across many draws")
    void referencesAreUnique() {
        // The random part is six characters from a 32-character alphabet, so
        // there are about 1.07e9 possibilities per day. At 500 draws the
        // birthday-collision probability is roughly 1 in 9,000 - low enough
        // not to make this test flaky, which a larger sample would.
        int draws = 500;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < draws; i++) {
            seen.add(References.transactionReference());
        }
        assertThat(seen)
                .as("a collision would put two different transactions under one reference")
                .hasSize(draws);
    }

    @Test
    @DisplayName("account numbers avoid easily confused characters in the random part")
    void customerNumberAvoidsAmbiguousCharacters() {
        for (int i = 0; i < 500; i++) {
            assertThat(References.customerNumber().substring(3))
                    .doesNotContain("O").doesNotContain("I")
                    .doesNotContain("0").doesNotContain("1");
        }
    }
}
