package com.demo.payment.controller;

import com.demo.payment.model.*;
import com.demo.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/payments/authorize")
    public ResponseEntity<?> authorize(@Valid @RequestBody AuthorizeRequest request) {
        try {
            PaymentResponse response = paymentService.authorize(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error authorizing payment", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/payments/capture")
    public ResponseEntity<?> capture(@Valid @RequestBody CaptureRequest request) {
        try {
            PaymentResponse response = paymentService.capture(request);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            // Java 17 pattern matching instanceof — eliminates the explicit cast
            if (e instanceof IllegalStateException ise) {
                return ResponseEntity.badRequest().body(Map.of("error", ise.getMessage()));
            }
            log.error("Error capturing payment", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/payments/refund")
    public ResponseEntity<?> refund(@Valid @RequestBody RefundRequest request) {
        try {
            PaymentResponse response = paymentService.refund(request);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            // Java 17 pattern matching instanceof — eliminates the explicit cast
            if (e instanceof IllegalStateException ise) {
                return ResponseEntity.badRequest().body(Map.of("error", ise.getMessage()));
            }
            log.error("Error refunding payment", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/payments/{id}")
    public ResponseEntity<?> getTransaction(@PathVariable String id) {
        try {
            PaymentResponse response = paymentService.getTransaction(id);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching transaction", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/payments/history")
    public ResponseEntity<List<PaymentResponse>> getHistory() {
        return ResponseEntity.ok(paymentService.getHistory());
    }

    @PostMapping("/admin/cache/clear")
    public ResponseEntity<Map<String, String>> clearCache() {
        paymentService.clearCache();
        return ResponseEntity.ok(Map.of("message", "Cache cleared successfully"));
    }
}

// Made with Bob