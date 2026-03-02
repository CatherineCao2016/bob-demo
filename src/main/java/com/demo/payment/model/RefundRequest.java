package com.demo.payment.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Refund request — Java 17 record.
 */
public record RefundRequest(

    @NotBlank(message = "Transaction ID is required")
    String transactionId

) {}

// Made with Bob