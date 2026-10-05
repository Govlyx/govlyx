package com.govlyx.AI.controller;

import com.govlyx.AI.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentService paymentService;

    /* --- MONETIZATION DISABLED ---
    @PostMapping("/razorpay")
    public ResponseEntity<Void> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        if (signature == null || signature.isEmpty()) {
            log.warn("Received webhook without signature");
            return ResponseEntity.badRequest().build();
        }

        paymentService.processWebhook(payload, signature);
        
        // Always return 200 OK to Razorpay so it doesn't retry infinitely
        return ResponseEntity.ok().build();
    }
    --- END MONETIZATION DISABLED --- */
}
