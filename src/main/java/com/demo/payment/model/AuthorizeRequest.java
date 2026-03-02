package com.demo.payment.model;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Authorize request — Java 17 record replaces the verbose POJO.
 * Jackson 2.15 (bundled with Spring Boot 3.x) deserializes records via
 * the canonical constructor automatically. Hibernate Validator 8.x
 * supports Bean Validation annotations on record components.
 */
public record AuthorizeRequest(

    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "\\d{13,19}", message = "Invalid card number")
    String cardNumber,

    @NotBlank(message = "Expiry month is required")
    @Pattern(regexp = "0[1-9]|1[0-2]", message = "Invalid expiry month (MM)")
    String expiryMonth,

    @NotBlank(message = "Expiry year is required")
    @Pattern(regexp = "\\d{2,4}", message = "Invalid expiry year")
    String expiryYear,

    @NotBlank(message = "CVV is required")
    @Pattern(regexp = "\\d{3,4}", message = "Invalid CVV")
    String cvv,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    BigDecimal amount,

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    String currency

) {}

// Made with Bob