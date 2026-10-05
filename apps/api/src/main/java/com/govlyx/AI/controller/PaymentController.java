package com.Govlyx.AI.controller;

import com.Govlyx.AI.enums.PassTier;
import com.Govlyx.AI.enums.BillingCycle;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.model.UserPass;
import com.Govlyx.AI.repository.UserPassRepository;
import com.Govlyx.AI.service.PaymentService;
import com.Govlyx.AI.service.PlanEnforcementService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PlanEnforcementService planEnforcementService;
    private final UserPassRepository userPassRepository;
    private final com.Govlyx.AI.repository.UserRepo userRepository;
    private final org.springframework.cache.CacheManager cacheManager;

    @org.springframework.beans.factory.annotation.Value("${pricing.tier.pro.monthly}")
    private int proMonthlyPrice;

    @org.springframework.beans.factory.annotation.Value("${pricing.tier.pro.yearly}")
    private int proYearlyPrice;

    @org.springframework.beans.factory.annotation.Value("${pricing.tier.vip.monthly}")
    private int vipMonthlyPrice;

    @org.springframework.beans.factory.annotation.Value("${pricing.tier.vip.yearly}")
    private int vipYearlyPrice;

    /* --- MONETIZATION DISABLED ---
    @GetMapping("/config")
    public ResponseEntity<java.util.Map<String, String>> getBillingConfig() {
        return ResponseEntity.ok(java.util.Map.of("keyId", paymentService.getRazorpayKeyId()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyBilling(@AuthenticationPrincipal User user) {
        Map<String, Object> response = new HashMap<>();
        PassTier tier = planEnforcementService.getUserTier(user.getId());
        response.put("currentTier", tier.name());
        
        userPassRepository.findActivePassByUserId(user.getId()).ifPresent(pass -> {
            response.put("validUntil", pass.getValidUntil().toString());
            response.put("privateCommunityQuota", pass.getPrivateCommunityQuota());
            response.put("billingCycle", pass.getBillingCycle() != null ? pass.getBillingCycle().name() : "MONTHLY");
        });
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> payload) {
        try {
            String targetTierStr = (String) payload.get("targetTier");
            if (targetTierStr == null) {
                return ResponseEntity.badRequest().body("Missing targetTier in payload");
            }

            PassTier targetTier;
            try {
                targetTier = PassTier.valueOf(targetTierStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("Invalid target tier: " + targetTierStr);
            }
            
            String billingCycleStr = (String) payload.get("billingCycle");
            if (billingCycleStr == null) {
                billingCycleStr = "MONTHLY";
            }
            
            BillingCycle billingCycle;
            try {
                billingCycle = BillingCycle.valueOf(billingCycleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("Invalid billing cycle: " + billingCycleStr);
            }

            int amount = 0;
            if (targetTier == PassTier.GOVLYX_VIP) {
                amount = (billingCycle == BillingCycle.YEARLY) ? vipYearlyPrice : vipMonthlyPrice;
            } else if (targetTier == PassTier.GOVLYX_PRO) {
                amount = (billingCycle == BillingCycle.YEARLY) ? proYearlyPrice : proMonthlyPrice;
            }

            String orderId = paymentService.createOrder(user.getId(), targetTier, billingCycle, amount);
            Map<String, String> response = new HashMap<>();
            response.put("orderId", orderId);
            return ResponseEntity.ok(response);
        } catch (RazorpayException e) {
            return ResponseEntity.internalServerError().body("Error creating order: " + e.getMessage());
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> payload) {
        String orderId = payload.get("razorpay_order_id");
        String paymentId = payload.get("razorpay_payment_id");
        String signature = payload.get("razorpay_signature");

        boolean verified = paymentService.verifySignature(orderId, paymentId, signature);
        if (verified) {
            return ResponseEntity.ok().body(Map.of("message", "Payment verified and pass activated"));
        } else {
            return ResponseEntity.badRequest().body("Signature verification failed");
        }
    }

    @PostMapping("/grant-pass")
    public ResponseEntity<?> grantPass(
            @RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String targetTierStr = payload.get("targetTier");

        if (email == null || email.trim().isEmpty() || targetTierStr == null || targetTierStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Missing email or targetTier in payload");
        }

        PassTier targetTier;
        try {
            targetTier = PassTier.valueOf(targetTierStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid target tier: " + targetTierStr);
        }

        User targetUser = userRepository.findByEmail(email).orElse(null);
        if (targetUser == null) {
            return ResponseEntity.badRequest().body("User not found with email: " + email);
        }

        UserPass userPass = UserPass.builder()
                .userId(targetUser.getId())
                .tier(targetTier)
                .razorpayOrderId("MANUAL_" + System.currentTimeMillis())
                .razorpayPaymentId("MANUAL_GRANT")
                .status(com.Govlyx.AI.enums.UserPassStatus.ACTIVE)
                .validUntil(java.time.LocalDateTime.now().plusMonths(1))
                .privateCommunityQuota(targetTier == PassTier.GOVLYX_VIP ? 5 : 3)
                .build();

        userPassRepository.findActivePassByUserId(targetUser.getId()).ifPresent(oldPass -> {
            userPass.setPrivateCommunityQuota(userPass.getPrivateCommunityQuota() + oldPass.getPrivateCommunityQuota());
            oldPass.setStatus(com.Govlyx.AI.enums.UserPassStatus.EXPIRED);
            userPassRepository.save(oldPass);
        });

        userPassRepository.save(userPass);

        org.springframework.cache.Cache cache = cacheManager.getCache("userTiers");
        if (cache != null) {
            cache.evict(targetUser.getId());
        }

        return ResponseEntity.ok().body(Map.of(
            "message", "Successfully granted " + targetTier.name() + " to " + email,
            "userId", targetUser.getId()
        ));
    }
    --- END MONETIZATION DISABLED --- */
}
