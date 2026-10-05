package com.JanSahayak.AI.model;

import com.JanSahayak.AI.enums.PassTier;
import com.JanSahayak.AI.enums.UserPassStatus;
import com.JanSahayak.AI.enums.BillingCycle;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_passes", indexes = {
        @Index(name = "idx_user_passes_user_id", columnList = "user_id"),
        @Index(name = "idx_user_passes_status", columnList = "status")
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class UserPass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PassTier tier;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserPassStatus status;

    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    @Column(name = "private_community_quota", nullable = false)
    @Builder.Default
    private Integer privateCommunityQuota = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", length = 20)
    private BillingCycle billingCycle;
    
    // Optimistic locking version
    @Version
    private Long version;
}
