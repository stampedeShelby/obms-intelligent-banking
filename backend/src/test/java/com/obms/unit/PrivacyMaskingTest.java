package com.obms.unit;

import com.obms.dto.BeneficiaryDtos;
import com.obms.domain.Beneficiary;
import com.obms.domain.Enums;
import com.obms.service.AuthService;
import com.obms.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the data-minimisation rules.
 *
 * <p>These assert that sensitive values are reduced before they are stored or
 * displayed: a KYC identifier keeps only its last four characters, an email
 * shown during sign-in keeps only its first, and a beneficiary account number
 * is masked in the payee list.</p>
 */
@DisplayName("Unit: privacy masking")
class PrivacyMaskingTest {

    @ParameterizedTest(name = "{0} is masked to {1}")
    @CsvSource({
            "123456789012,********9012",
            "AB1234,**1234",
            "1234,****",
            "12,**"
    })
    @DisplayName("KYC identifiers retain only the last four characters")
    void masksKycIdentifier(String input, String expected) {
        assertThat(CustomerService.mask(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("the masked identifier never contains the leading digits")
    void maskRemovesLeadingDigits() {
        String masked = CustomerService.mask("987654321098");

        assertThat(masked).doesNotContain("9876");
        assertThat(masked).endsWith("1098");
    }

    @ParameterizedTest(name = "{0} is masked to {1}")
    @CsvSource({
            "sujith@example.com,s****@example.com",
            "priya.sharma@demo.obms,p****@demo.obms"
    })
    @DisplayName("sign-in destination shows only the first character of the local part")
    void masksEmail(String input, String expected) {
        assertThat(AuthService.maskEmail(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a single-character local part is fully hidden")
    void masksShortEmail() {
        assertThat(AuthService.maskEmail("a@example.com")).isEqualTo("***@example.com");
    }

    @Test
    @DisplayName("masking a null email does not throw")
    void nullEmailIsSafe() {
        assertThat(AuthService.maskEmail(null)).isNull();
    }

    @Test
    @DisplayName("beneficiary account numbers are masked in the payee list")
    void masksBeneficiaryAccountNumber() {
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setBeneficiaryName("Arjun Mehta");
        beneficiary.setAccountNumber("10024857391026");
        beneficiary.setIfscCode("OBMS0000001");
        beneficiary.setStatus(Enums.BeneficiaryStatus.ACTIVE);

        BeneficiaryDtos.BeneficiaryView view = BeneficiaryDtos.BeneficiaryView.from(beneficiary);

        assertThat(view.maskedAccountNumber()).isEqualTo("XXXX1026");
        assertThat(view.maskedAccountNumber()).doesNotContain("100248");
    }

    @Test
    @DisplayName("a beneficiary is payable only once verified")
    void payableOnlyWhenActive() {
        Beneficiary pending = new Beneficiary();
        pending.setAccountNumber("10024857391026");
        pending.setStatus(Enums.BeneficiaryStatus.PENDING_VERIFICATION);

        assertThat(BeneficiaryDtos.BeneficiaryView.from(pending).payable())
                .as("an unverified payee must not be presented as payable")
                .isFalse();
    }

    @Test
    @DisplayName("the nickname is preferred as the display name when present")
    void nicknamePreferred() {
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setBeneficiaryName("Arjun Mehta");
        beneficiary.setNickname("Flatmate");

        assertThat(beneficiary.displayName()).isEqualTo("Flatmate");
    }
}
