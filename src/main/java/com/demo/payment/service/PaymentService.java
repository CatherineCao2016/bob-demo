package com.demo.payment.service;

import com.demo.payment.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final TransactionRepository transactionRepository;

    public PaymentService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    // Known test card numbers
    private static final Map<String, String> CARD_TYPES = new HashMap<>();

    static {
        CARD_TYPES.put("4263970000005262", "Visa");
        CARD_TYPES.put("5425230000004415", "MasterCard");
        CARD_TYPES.put("374101000000608", "Amex");
    }

    public PaymentResponse authorize(AuthorizeRequest request) {
        simulateProcessingDelay();

        String cardNumber = request.cardNumber();
        String cardType = detectCardType(cardNumber);
        String masked = maskCardNumber(cardNumber);

        // Check expiry
        if (isCardExpired(request.expiryMonth(), request.expiryYear())) {
            Transaction tx = Transaction.builder()
                    .cardNumber(cardNumber)
                    .maskedCardNumber(masked)
                    .cardType(cardType)
                    .expiryMonth(request.expiryMonth())
                    .expiryYear(request.expiryYear())
                    .amount(request.amount())
                    .currency(request.currency())
                    .status(TransactionStatus.DECLINED)
                    .responseCode("54")
                    .responseMessage("Expired Card")
                    .build();
            Transaction saved = transactionRepository.save(tx);
            log.info("Transaction {} declined: expired card", saved.getId());
            return toResponse(saved);
        }

        // Simulate 10% random decline
        if (ThreadLocalRandom.current().nextInt(100) < 10) {
            String[] declineCodes = {"51", "05"};
            String[] declineMessages = {"Insufficient Funds", "Do Not Honor"};
            int idx = ThreadLocalRandom.current().nextInt(declineCodes.length);

            Transaction tx = Transaction.builder()
                    .cardNumber(cardNumber)
                    .maskedCardNumber(masked)
                    .cardType(cardType)
                    .expiryMonth(request.expiryMonth())
                    .expiryYear(request.expiryYear())
                    .amount(request.amount())
                    .currency(request.currency())
                    .status(TransactionStatus.DECLINED)
                    .responseCode(declineCodes[idx])
                    .responseMessage(declineMessages[idx])
                    .build();
            Transaction saved = transactionRepository.save(tx);
            log.info("Transaction {} randomly declined: {}", saved.getId(), declineMessages[idx]);
            return toResponse(saved);
        }

        // Approved
        String authCode = generateAuthCode();
        Transaction tx = Transaction.builder()
                .cardNumber(cardNumber)
                .maskedCardNumber(masked)
                .cardType(cardType)
                .expiryMonth(request.expiryMonth())
                .expiryYear(request.expiryYear())
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.AUTHORIZED)
                .responseCode("00")
                .responseMessage("Approved")
                .authorizationCode(authCode)
                .build();
        Transaction saved = transactionRepository.save(tx);
        log.info("Transaction {} authorized with auth code {}", saved.getId(), authCode);
        return toResponse(saved);
    }

    @CacheEvict(value = "transactions", key = "#request.transactionId")
    public PaymentResponse capture(CaptureRequest request) {
        simulateProcessingDelay();

        Transaction tx = transactionRepository.findById(request.transactionId())
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + request.transactionId()));

        if (tx.getStatus() != TransactionStatus.AUTHORIZED) {
            // Java 17 text block for multi-line error message template
            String msg = """
                    Transaction %s is not in AUTHORIZED state \
                    (current: %s)""".formatted(tx.getId(), tx.getStatus());
            throw new IllegalStateException(msg);
        }

        tx.setStatus(TransactionStatus.CAPTURED);
        tx.setResponseCode("00");
        tx.setResponseMessage("Captured");
        Transaction saved = transactionRepository.save(tx);
        log.info("Transaction {} captured", saved.getId());
        return toResponse(saved);
    }

    @CacheEvict(value = "transactions", key = "#request.transactionId")
    public PaymentResponse refund(RefundRequest request) {
        simulateProcessingDelay();

        Transaction tx = transactionRepository.findById(request.transactionId())
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + request.transactionId()));

        if (tx.getStatus() != TransactionStatus.CAPTURED) {
            // Java 17 text block for multi-line error message template
            String msg = """
                    Transaction %s is not in CAPTURED state \
                    (current: %s)""".formatted(tx.getId(), tx.getStatus());
            throw new IllegalStateException(msg);
        }

        tx.setStatus(TransactionStatus.REFUNDED);
        tx.setResponseCode("00");
        tx.setResponseMessage("Refunded");
        Transaction saved = transactionRepository.save(tx);
        log.info("Transaction {} refunded", saved.getId());
        return toResponse(saved);
    }

    @Cacheable(value = "transactions", key = "#id")
    public PaymentResponse getTransaction(String id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + id));
        return toResponse(tx);
    }

    public List<PaymentResponse> getHistory() {
        // Java 16+ Stream.toList() — returns an unmodifiable list; replaces Collectors.toList()
        return transactionRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @CacheEvict(value = "transactions", allEntries = true)
    public void clearCache() {
        log.info("Cache cleared");
    }

    // ---- helpers ----

    private void simulateProcessingDelay() {
        try {
            long delay = ThreadLocalRandom.current().nextLong(200, 501);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isCardExpired(String month, String year) {
        try {
            int expMonth = Integer.parseInt(month);
            int expYear = Integer.parseInt(year);
            if (expYear < 100) {
                expYear += 2000;
            }
            LocalDate expiry = LocalDate.of(expYear, expMonth, 1).plusMonths(1).minusDays(1);
            return LocalDate.now().isAfter(expiry);
        } catch (Exception e) {
            return true;
        }
    }

    private String detectCardType(String cardNumber) {
        if (CARD_TYPES.containsKey(cardNumber)) {
            return CARD_TYPES.get(cardNumber);
        }
        if (cardNumber.startsWith("4")) return "Visa";
        if (cardNumber.startsWith("5")) return "MasterCard";
        if (cardNumber.startsWith("3")) return "Amex";
        return "Unknown";
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    private String generateAuthCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    /**
     * Maps a JPA Transaction entity to the PaymentResponse record.
     * Uses the record's canonical constructor directly — no Builder needed.
     */
    private PaymentResponse toResponse(Transaction tx) {
        return new PaymentResponse(
                tx.getId(),
                tx.getMaskedCardNumber(),
                tx.getCardType(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getStatus(),
                tx.getResponseCode(),
                tx.getResponseMessage(),
                tx.getAuthorizationCode(),
                tx.getCreatedAt(),
                tx.getUpdatedAt()
        );
    }
}

// Made with Bob