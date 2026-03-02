package com.demo.payment.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    private String id;

    @Column(nullable = false)
    private String cardNumber;

    @Column(nullable = false)
    private String maskedCardNumber;

    @Column(nullable = false)
    private String cardType;

    @Column(nullable = false)
    private String expiryMonth;

    @Column(nullable = false)
    private String expiryYear;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column
    private String responseCode;

    @Column
    private String responseMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @Column
    private String authorizationCode;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ---- Constructors ----

    public Transaction() {}

    private Transaction(Builder builder) {
        this.id = builder.id;
        this.cardNumber = builder.cardNumber;
        this.maskedCardNumber = builder.maskedCardNumber;
        this.cardType = builder.cardType;
        this.expiryMonth = builder.expiryMonth;
        this.expiryYear = builder.expiryYear;
        this.amount = builder.amount;
        this.currency = builder.currency;
        this.status = builder.status;
        this.responseCode = builder.responseCode;
        this.responseMessage = builder.responseMessage;
        this.authorizationCode = builder.authorizationCode;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    public String getMaskedCardNumber() { return maskedCardNumber; }
    public void setMaskedCardNumber(String maskedCardNumber) { this.maskedCardNumber = maskedCardNumber; }

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }

    public String getExpiryMonth() { return expiryMonth; }
    public void setExpiryMonth(String expiryMonth) { this.expiryMonth = expiryMonth; }

    public String getExpiryYear() { return expiryYear; }
    public void setExpiryYear(String expiryYear) { this.expiryYear = expiryYear; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }

    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }

    // ---- Builder ----

    public static class Builder {
        private String id;
        private String cardNumber;
        private String maskedCardNumber;
        private String cardType;
        private String expiryMonth;
        private String expiryYear;
        private BigDecimal amount;
        private String currency;
        private TransactionStatus status;
        private String responseCode;
        private String responseMessage;
        private String authorizationCode;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder cardNumber(String cardNumber) { this.cardNumber = cardNumber; return this; }
        public Builder maskedCardNumber(String maskedCardNumber) { this.maskedCardNumber = maskedCardNumber; return this; }
        public Builder cardType(String cardType) { this.cardType = cardType; return this; }
        public Builder expiryMonth(String expiryMonth) { this.expiryMonth = expiryMonth; return this; }
        public Builder expiryYear(String expiryYear) { this.expiryYear = expiryYear; return this; }
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder status(TransactionStatus status) { this.status = status; return this; }
        public Builder responseCode(String responseCode) { this.responseCode = responseCode; return this; }
        public Builder responseMessage(String responseMessage) { this.responseMessage = responseMessage; return this; }
        public Builder authorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public Transaction build() { return new Transaction(this); }
    }
}

// Made with Bob