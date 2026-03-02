package com.demo.payment.controller;

import com.demo.payment.model.TransactionStatus;
import com.demo.payment.service.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * JUnit 5 integration tests for the Payment API (Java 17 version).
 *
 * Strategy
 * --------
 * • @SpringBootTest loads the full application context with the H2 in-memory DB.
 * • @AutoConfigureMockMvc wires MockMvc without starting a real HTTP server.
 * • The authorize endpoint has a 10 % random-decline path; tests that require
 *   an AUTHORIZED transaction retry the call up to MAX_AUTH_RETRIES times so
 *   the suite is deterministic even when the random path fires.
 * • Each test class run starts with a clean repository (cleared in @BeforeEach).
 *
 * Scenarios covered
 * -----------------
 * 1. Successful authorization
 * 2. Declined card (expired card — deterministic decline path)
 * 3. Capture after authorization
 * 4. Refund after capture
 * 5. Invalid card number (Bean Validation rejection)
 * 6. Duplicate refund attempt (already-REFUNDED transaction)
 * 7. Capture on non-existent transaction (404)
 * 8. Refund on non-existent transaction (404)
 * 9. Capture on already-captured transaction (400)
 * 10. GET transaction by ID
 * 11. GET payment history
 * 12. Clear cache endpoint
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentApiIntegrationTest {

    // ── Constants ──────────────────────────────────────────────────────────────

    /** Known Visa test card — always passes Luhn / pattern checks. */
    private static final String VALID_CARD   = "4263970000005262";
    /** Card number that fails the @Pattern(regexp="\\d{13,19}") constraint. */
    private static final String INVALID_CARD = "1234-ABCD";
    /** A future expiry month/year guaranteed not to be expired. */
    private static final String EXP_MONTH    = "12";
    private static final String EXP_YEAR     = "2099";
    /** An expiry that is definitely in the past → deterministic DECLINED. */
    private static final String EXPIRED_MONTH = "01";
    private static final String EXPIRED_YEAR  = "2000";

    private static final String CVV      = "737";
    private static final String CURRENCY = "USD";
    private static final BigDecimal AMOUNT = new BigDecimal("99.99");

    /** Retry limit for the authorize call to avoid the 10 % random-decline path. */
    private static final int MAX_AUTH_RETRIES = 20;

    // ── Spring beans ───────────────────────────────────────────────────────────

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TransactionRepository transactionRepository;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Successful authorization
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("POST /api/payments/authorize → 200 AUTHORIZED")
    void authorize_successfulAuthorization() throws Exception {
        String txId = authorizeAndGetId(VALID_CARD, EXP_MONTH, EXP_YEAR);
        Assertions.assertNotNull(txId, "Should have obtained an AUTHORIZED transaction ID");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Declined card (expired)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("POST /api/payments/authorize → 200 DECLINED (expired card)")
    void authorize_declinedExpiredCard() throws Exception {
        String body = authorizeRequestBody(VALID_CARD, EXPIRED_MONTH, EXPIRED_YEAR);

        mockMvc.perform(post("/api/payments/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(TransactionStatus.DECLINED.name()))
                .andExpect(jsonPath("$.responseCode").value("54"))
                .andExpect(jsonPath("$.responseMessage").value("Expired Card"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.maskedCardNumber").value("**** **** **** 5262"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Capture after authorization
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("POST /api/payments/capture → 200 CAPTURED")
    void capture_afterAuthorization() throws Exception {
        String txId = authorizeAndGetId(VALID_CARD, EXP_MONTH, EXP_YEAR);
        Assumptions.assumeTrue(txId != null, "Skipping: could not obtain AUTHORIZED transaction");

        mockMvc.perform(post("/api/payments/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captureRequestBody(txId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(txId))
                .andExpect(jsonPath("$.status").value(TransactionStatus.CAPTURED.name()))
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.responseMessage").value("Captured"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. Refund after capture
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("POST /api/payments/refund → 200 REFUNDED")
    void refund_afterCapture() throws Exception {
        String txId = authorizeAndGetId(VALID_CARD, EXP_MONTH, EXP_YEAR);
        Assumptions.assumeTrue(txId != null, "Skipping: could not obtain AUTHORIZED transaction");

        // Capture first
        mockMvc.perform(post("/api/payments/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captureRequestBody(txId)))
                .andExpect(status().isOk());

        // Then refund
        mockMvc.perform(post("/api/payments/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundRequestBody(txId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(txId))
                .andExpect(jsonPath("$.status").value(TransactionStatus.REFUNDED.name()))
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.responseMessage").value("Refunded"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. Invalid card number — Bean Validation rejects the request
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("POST /api/payments/authorize → 400 invalid card number")
    void authorize_invalidCardNumber_returns400() throws Exception {
        String body = authorizeRequestBody(INVALID_CARD, EXP_MONTH, EXP_YEAR);

        mockMvc.perform(post("/api/payments/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. Duplicate refund attempt
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("POST /api/payments/refund (duplicate) → 400 not in CAPTURED state")
    void refund_duplicateRefundAttempt_returns400() throws Exception {
        String txId = authorizeAndGetId(VALID_CARD, EXP_MONTH, EXP_YEAR);
        Assumptions.assumeTrue(txId != null, "Skipping: could not obtain AUTHORIZED transaction");

        // Capture
        mockMvc.perform(post("/api/payments/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captureRequestBody(txId)))
                .andExpect(status().isOk());

        // First refund — should succeed
        mockMvc.perform(post("/api/payments/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundRequestBody(txId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(TransactionStatus.REFUNDED.name()));

        // Second refund — should be rejected (transaction is now REFUNDED, not CAPTURED)
        mockMvc.perform(post("/api/payments/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundRequestBody(txId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        containsString("not in CAPTURED state")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. Capture on non-existent transaction → 404
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("POST /api/payments/capture → 404 transaction not found")
    void capture_nonExistentTransaction_returns404() throws Exception {
        mockMvc.perform(post("/api/payments/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captureRequestBody("non-existent-id")))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. Refund on non-existent transaction → 404
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @DisplayName("POST /api/payments/refund → 404 transaction not found")
    void refund_nonExistentTransaction_returns404() throws Exception {
        mockMvc.perform(post("/api/payments/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundRequestBody("non-existent-id")))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 9. Capture on already-captured transaction → 400
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @DisplayName("POST /api/payments/capture (duplicate) → 400 not in AUTHORIZED state")
    void capture_alreadyCaptured_returns400() throws Exception {
        String txId = authorizeAndGetId(VALID_CARD, EXP_MONTH, EXP_YEAR);
        Assumptions.assumeTrue(txId != null, "Skipping: could not obtain AUTHORIZED transaction");

        // First capture — succeeds
        mockMvc.perform(post("/api/payments/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captureRequestBody(txId)))
                .andExpect(status().isOk());

        // Second capture — rejected
        mockMvc.perform(post("/api/payments/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captureRequestBody(txId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        containsString("not in AUTHORIZED state")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 10. GET transaction by ID
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("GET /api/payments/{id} → 200 with correct fields")
    void getTransaction_existingId_returns200() throws Exception {
        String txId = authorizeAndGetId(VALID_CARD, EXP_MONTH, EXP_YEAR);
        Assumptions.assumeTrue(txId != null, "Skipping: could not obtain AUTHORIZED transaction");

        mockMvc.perform(get("/api/payments/{id}", txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(txId))
                .andExpect(jsonPath("$.maskedCardNumber").value("**** **** **** 5262"))
                .andExpect(jsonPath("$.cardType").value("Visa"))
                .andExpect(jsonPath("$.amount").value(AMOUNT.doubleValue()))
                .andExpect(jsonPath("$.currency").value(CURRENCY))
                .andExpect(jsonPath("$.status").value(TransactionStatus.AUTHORIZED.name()))
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/payments/{id} → 404 for unknown ID")
    void getTransaction_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/payments/{id}", "unknown-tx-id"))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 11. GET payment history
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(12)
    @DisplayName("GET /api/payments/history → 200 with list of transactions")
    void getHistory_returnsListOfTransactions() throws Exception {
        // Create two transactions (expired → deterministic)
        String body = authorizeRequestBody(VALID_CARD, EXPIRED_MONTH, EXPIRED_YEAR);
        mockMvc.perform(post("/api/payments/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/payments/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/payments/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].transactionId").isNotEmpty())
                .andExpect(jsonPath("$[0].status").isNotEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 12. Clear cache endpoint
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(13)
    @DisplayName("POST /admin/cache/clear → 200 with success message")
    void clearCache_returns200() throws Exception {
        mockMvc.perform(post("/admin/cache/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cache cleared successfully"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Additional validation edge-cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(14)
    @DisplayName("POST /api/payments/authorize → 400 missing required fields")
    void authorize_missingFields_returns400() throws Exception {
        // Empty JSON object — all @NotBlank / @NotNull constraints fire
        mockMvc.perform(post("/api/payments/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(15)
    @DisplayName("POST /api/payments/authorize → 400 amount below minimum")
    void authorize_amountBelowMinimum_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "cardNumber",  VALID_CARD,
                "expiryMonth", EXP_MONTH,
                "expiryYear",  EXP_YEAR,
                "cvv",         CVV,
                "amount",      "0.00",
                "currency",    CURRENCY
        ));

        mockMvc.perform(post("/api/payments/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(16)
    @DisplayName("POST /api/payments/capture → 400 missing transactionId")
    void capture_missingTransactionId_returns400() throws Exception {
        mockMvc.perform(post("/api/payments/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(17)
    @DisplayName("POST /api/payments/refund → 400 missing transactionId")
    void refund_missingTransactionId_returns400() throws Exception {
        mockMvc.perform(post("/api/payments/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(18)
    @DisplayName("POST /api/payments/authorize → response body contains all expected fields")
    void authorize_responseBodyFields_areComplete() throws Exception {
        // Use expired card for deterministic result
        String body = authorizeRequestBody(VALID_CARD, EXPIRED_MONTH, EXPIRED_YEAR);

        mockMvc.perform(post("/api/payments/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.maskedCardNumber").isNotEmpty())
                .andExpect(jsonPath("$.cardType").isNotEmpty())
                .andExpect(jsonPath("$.amount").isNotEmpty())
                .andExpect(jsonPath("$.currency").value(CURRENCY))
                .andExpect(jsonPath("$.status").isNotEmpty())
                .andExpect(jsonPath("$.responseCode").isNotEmpty())
                .andExpect(jsonPath("$.responseMessage").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper methods
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Attempts to authorize a card up to MAX_AUTH_RETRIES times.
     * Returns the transactionId if AUTHORIZED, or null if all attempts were declined.
     */
    private String authorizeAndGetId(String cardNumber, String month, String year) throws Exception {
        String body = authorizeRequestBody(cardNumber, month, year);
        for (int attempt = 0; attempt < MAX_AUTH_RETRIES; attempt++) {
            MvcResult result = mockMvc.perform(post("/api/payments/authorize")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            Map<?, ?> responseMap = objectMapper.readValue(responseJson, Map.class);
            String status = (String) responseMap.get("status");

            if (TransactionStatus.AUTHORIZED.name().equals(status)) {
                return (String) responseMap.get("transactionId");
            }
        }
        return null; // all attempts declined (extremely unlikely with MAX_AUTH_RETRIES=20)
    }

    private String authorizeRequestBody(String cardNumber, String month, String year) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "cardNumber",  cardNumber,
                "expiryMonth", month,
                "expiryYear",  year,
                "cvv",         CVV,
                "amount",      AMOUNT,
                "currency",    CURRENCY
        ));
    }

    private String captureRequestBody(String transactionId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("transactionId", transactionId));
    }

    private String refundRequestBody(String transactionId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("transactionId", transactionId));
    }
}

// Made with Bob