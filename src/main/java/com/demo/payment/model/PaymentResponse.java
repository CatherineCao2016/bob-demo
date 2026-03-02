package com.demo.payment.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment response — Java 17 record replaces the verbose POJO + Builder.
 * Jackson serializes records to JSON using the accessor methods that records
 * generate automatically (transactionId(), maskedCardNumber(), etc.).
 * Spring Boot 3.x / Jackson 2.15 handles record serialization out of the box.
 */
public record PaymentResponse(
    String transactionId,
    String maskedCardNumber,
    String cardType,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    String responseCode,
    String responseMessage,
    String authorizationCode,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

// Made with Bob