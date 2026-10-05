package com.Govlyx.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "external_copyright_claims", indexes = {
    @Index(name = "idx_ext_claim_status", columnList = "status"),
    @Index(name = "idx_ext_claim_created_at", columnList = "created_at"),
    @Index(name = "idx_ext_claim_ref_id", columnList = "reference_id")
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ExternalCopyrightClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference ID: "GOVLYX-CR-2026-0001"
    @Column(name = "reference_id", nullable = false, unique = true, length = 50)
    private String referenceId;

    // ── Claimant Info ───────────────────────────────
    @Column(name = "claimant_name", nullable = false, length = 255)
    private String claimantName;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "claimant_email", nullable = false, length = 255)
    private String claimantEmail;

    @Column(name = "claimant_phone", length = 20)
    private String claimantPhone;

    @Column(name = "postal_address", length = 1000)
    private String postalAddress;

    // ── Infringing Content ──────────────────────────
    @Column(name = "infringing_urls", nullable = false, length = 5000)
    private String infringingUrls;  // comma-separated Govlyx URLs

    @Column(name = "infringement_description", nullable = false, length = 2000)
    private String infringementDescription;

    // ── Original Work ───────────────────────────────
    @Column(name = "original_work_url", nullable = false, length = 1000)
    private String originalWorkUrl;

    @Column(name = "original_work_description", length = 2000)
    private String originalWorkDescription;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;  // "MUSIC", "VIDEO", "IMAGE", "TEXT", "OTHER"

    // ── Legal Declarations ──────────────────────────
    @Column(name = "is_copyright_owner", nullable = false, columnDefinition = "boolean")
    private Boolean isCopyrightOwner;

    @Column(name = "good_faith_belief", nullable = false, columnDefinition = "boolean")
    private Boolean goodFaithBelief;

    @Column(name = "accuracy_declaration", nullable = false, columnDefinition = "boolean")
    private Boolean accuracyDeclaration;

    @Column(name = "electronic_signature", nullable = false, length = 255)
    private String electronicSignature;

    // ── Processing ──────────────────────────────────
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING";
    // "PENDING", "ACKNOWLEDGED", "IN_REVIEW", "RESOLVED_REMOVED", "RESOLVED_DISMISSED"

    @Column(name = "acknowledged_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date acknowledgedAt;

    @Column(name = "resolved_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date createdAt = new Date();
}
