package com.obms.api;

import com.obms.support.ApiTestTokens;
import com.obms.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts that client mistakes are reported as client errors.
 *
 * <p>A QA pass found {@code GET /api/transfers/pending}, a path with no
 * handler, returning <b>500 INTERNAL_ERROR</b>. The cause was the catch-all
 * {@code @ExceptionHandler(Exception.class)} in GlobalExceptionHandler, which
 * intercepted the exceptions Spring raises for an unmatched path or an
 * unsupported method and relabelled them as server faults.</p>
 *
 * <p>That matters beyond tidiness: it tells the caller the server broke when
 * the request was at fault, and in a monitored deployment every mistyped URL
 * would trigger a server-error alert, hiding genuine 500s in the noise.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestDataFactory.class, ApiTestTokens.class})
@DisplayName("API: client errors are reported as client errors")
class ErrorMappingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestTokens tokens;

    @Autowired
    private TestDataFactory factory;

    private String customerToken() {
        TestDataFactory.Fixture fixture = factory.customerWithAccount(new BigDecimal("5000.00"));
        return tokens.accessTokenFor(fixture);
    }

    @Test
    @DisplayName("an unmatched API path is 404, not 500")
    void unmatchedPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/transfers/pending")
                        .header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("another unmatched path is also 404")
    void anotherUnmatchedPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/does-not-exist")
                        .header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unsupported method is 405, not 500")
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/accounts")
                        .header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("malformed JSON is 400, not 500")
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("a path variable of the wrong type is 400, not 500")
    void wrongTypePathVariableIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/accounts/not-a-number")
                        .header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("no error response leaks a stack trace or internal class names")
    void errorsDoNotLeakInternals() throws Exception {
        String body = mockMvc.perform(get("/api/transfers/pending")
                        .header("Authorization", "Bearer " + customerToken()))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("com.obms")
                .doesNotContain("org.springframework")
                .doesNotContain("Exception");
    }
}
