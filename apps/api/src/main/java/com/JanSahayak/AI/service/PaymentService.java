package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.PassTier;
import com.JanSahayak.AI.enums.UserPassStatus;
import com.JanSahayak.AI.enums.BillingCycle;
import com.JanSahayak.AI.model.UserPass;
import com.JanSahayak.AI.model.TransactionHistory;
import com.JanSahayak.AI.repository.UserPassRepository;
import com.JanSahayak.AI.repository.TransactionHistoryRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final UserPassRepository userPassRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final CacheManager cacheManager;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook.secret:}")
    private String razorpayWebhookSecret;

    private RazorpayClient razorpayClient;

    public String getRazorpayKeyId() {
        return razorpayKeyId;
    }

    @PostConstruct
    public void init() {
        try {
            this.razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
        } catch (RazorpayException e) {
            log.error("Failed to initialize Razorpay Client", e);
        }
    }

    @Transactional
    public String createOrder(Long userId, PassTier targetTier, BillingCycle billingCycle, int amount) throws RazorpayException {
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amount * 100); // Amount in paise
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "receipt_user_" + userId);

        Order order = razorpayClient.orders.create(orderRequest);
        String orderId = order.get("id");

        // Save pending order
        UserPass userPass = UserPass.builder()
                .userId(userId)
                .tier(targetTier)
                .billingCycle(billingCycle)
                .razorpayOrderId(orderId)
                .status(UserPassStatus.EXPIRED) // Expired until verified
                .validUntil(LocalDateTime.now()) // Just a placeholder
                .privateCommunityQuota(0)
                .build();
        userPassRepository.save(userPass);

        // Save transaction history
        TransactionHistory transaction = TransactionHistory.builder()
                .userId(userId)
                .amount(amount * 100)
                .currency("INR")
                .targetTier(targetTier)
                .billingCycle(billingCycle)
                .razorpayOrderId(orderId)
                .status("CREATED")
                .build();
        transactionHistoryRepository.save(transaction);

        return orderId;
    }

    @Transactional
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);

            boolean isValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);

            if (isValid) {
                // Use Pessimistic Lock to strictly prevent race conditions
                Optional<UserPass> passOpt = userPassRepository.findByRazorpayOrderIdForUpdate(orderId);
                if (passOpt.isPresent()) {
                    UserPass userPass = passOpt.get();
                    
                    // IDEMPOTENCY CHECK: Prevent double activation
                    if (userPass.getStatus() == UserPassStatus.ACTIVE) {
                        log.info("Order ID {} is already active. Skipping double activation.", orderId);
                        return true;
                    }
                    
                    userPass.setRazorpayPaymentId(paymentId);
                    activatePass(userPass);
                    
                    transactionHistoryRepository.findByRazorpayOrderId(orderId).ifPresent(tx -> {
                        tx.setRazorpayPaymentId(paymentId);
                        tx.setStatus("SUCCESS");
                        transactionHistoryRepository.save(tx);
                    });
                } else {
                    log.error("Order ID {} verified but UserPass record not found", orderId);
                    return false;
                }
            } else {
                transactionHistoryRepository.findByRazorpayOrderId(orderId).ifPresent(tx -> {
                    tx.setRazorpayPaymentId(paymentId);
                    tx.setStatus("FAILED");
                    transactionHistoryRepository.save(tx);
                });
            }
            return isValid;
        } catch (RazorpayException e) {
            log.error("Error verifying signature for order {}", orderId, e);
            return false;
        }
    }

    private void activatePass(UserPass userPass) {
        final LocalDateTime[] baseDate = {LocalDateTime.now()};

        // Carry over remaining quota and validity from previous active pass
        userPassRepository.findActivePassByUserId(userPass.getUserId()).ifPresent(oldPass -> {
            userPass.setPrivateCommunityQuota(userPass.getPrivateCommunityQuota() + oldPass.getPrivateCommunityQuota());
            
            if (oldPass.getValidUntil().isAfter(LocalDateTime.now())) {
                baseDate[0] = oldPass.getValidUntil();
            }

            // Optional: Mark old pass as superseded/expired
            oldPass.setStatus(UserPassStatus.EXPIRED);
            userPassRepository.save(oldPass);
        });

        userPass.setStatus(UserPassStatus.ACTIVE);
        
        BillingCycle cycle = userPass.getBillingCycle() != null ? userPass.getBillingCycle() : BillingCycle.MONTHLY;
        
        if (cycle == BillingCycle.YEARLY) {
            userPass.setValidUntil(baseDate[0].plusYears(1));
        } else {
            userPass.setValidUntil(baseDate[0].plusMonths(1));
        }

        int quotaMultiplier = (cycle == BillingCycle.YEARLY) ? 12 : 1;

        if (userPass.getTier() == PassTier.GOVLYX_PRO) {
            userPass.setPrivateCommunityQuota(userPass.getPrivateCommunityQuota() + (3 * quotaMultiplier));
        } else if (userPass.getTier() == PassTier.GOVLYX_VIP) {
            userPass.setPrivateCommunityQuota(userPass.getPrivateCommunityQuota() + (5 * quotaMultiplier));
        }

        userPassRepository.save(userPass);
        
        Cache cache = cacheManager.getCache("userTiers");
        if (cache != null) {
            cache.evict(userPass.getUserId());
        }
        
        log.info("Pass activated for user {} with tier {}", userPass.getUserId(), userPass.getTier());
    }

    @Transactional
    public void processWebhook(String payload, String signature) {
        if (razorpayWebhookSecret == null || razorpayWebhookSecret.isEmpty()) {
            log.error("Webhook secret is not configured.");
            return;
        }
        
        try {
            boolean isValid = Utils.verifyWebhookSignature(payload, signature, razorpayWebhookSecret);
            if (!isValid) {
                log.error("Invalid webhook signature received.");
                return;
            }

            JSONObject webhookBody = new JSONObject(payload);
            String event = webhookBody.getString("event");

            if ("payment.captured".equals(event) || "order.paid".equals(event)) {
                JSONObject paymentEntity = webhookBody.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
                String orderId = paymentEntity.getString("order_id");
                String paymentId = paymentEntity.getString("id");
                
                log.info("Webhook received for order {} and payment {}", orderId, paymentId);
                
                // Use Pessimistic Lock to strictly prevent race conditions
                Optional<UserPass> passOpt = userPassRepository.findByRazorpayOrderIdForUpdate(orderId);
                if (passOpt.isPresent()) {
                    UserPass userPass = passOpt.get();
                    
                    // IDEMPOTENCY CHECK
                    if (userPass.getStatus() == UserPassStatus.ACTIVE) {
                        log.info("Webhook: Order ID {} already active. Skipping.", orderId);
                        return;
                    }
                    
                    // Extra security: Verify amount matches
                    int amountPaid = paymentEntity.getInt("amount"); // in paise
                    transactionHistoryRepository.findByRazorpayOrderId(orderId).ifPresent(tx -> {
                        if (tx.getAmount() == amountPaid) {
                            userPass.setRazorpayPaymentId(paymentId);
                            activatePass(userPass);
                            
                            tx.setRazorpayPaymentId(paymentId);
                            tx.setStatus("SUCCESS");
                            transactionHistoryRepository.save(tx);
                            log.info("Webhook: Activated pass for user {}", userPass.getUserId());
                        } else {
                            log.error("Webhook: Amount mismatch for order {}. Expected {}, got {}", orderId, tx.getAmount(), amountPaid);
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.error("Error processing Razorpay webhook", e);
        }
    }
}
