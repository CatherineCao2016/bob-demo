package com.demo.payment.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Capture request — Java 17 record.
 */
public record CaptureRequest(

    @NotBlank(message = "Transaction ID is required")
    String transactionId

) {}

// Made with Bob