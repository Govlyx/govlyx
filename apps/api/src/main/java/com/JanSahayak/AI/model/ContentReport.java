package com.JanSahayak.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.JanSahayak.AI.enums.ReportCategory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(
        name = "content_reports",
        indexes = {
                @Index(name = "idx_report_status", columnList = "status"),
                @Index(name = "idx_report_category", columnList = "category"),
                @Index(name = "idx_report_target", columnList = "target_type, target_id"),
                @Index(name = "idx_report_emergency", columnList = "is_emergency")
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ContentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType; // "POST", "SOCIAL_POST", "COMMENT"

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReportCategory category;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING"; // "PENDING", "RESOLVED_DISMISSED", "RESOLVED_REMOVED"

    @Column(name = "is_emergency", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    private Boolean isEmergency = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date createdAt = new Date();

    @Column(name = "resolved_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    // ===== Copyright Specific Fields =====
    
    @Column(name = "original_content_url", length = 1000)
    private String originalContentUrl;

    @Column(name = "ownership_declaration", columnDefinition = "boolean")
    private Boolean ownershipDeclaration;

    @Column(name = "claimant_name", length = 255)
    private String claimantName;

    @Column(name = "claimant_email", length = 255)
    private String claimantEmail;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = new Date();
        }
        if (status == null) {
            status = "PENDING";
        }
        // Auto-classify emergency categories according to IT rules (Harassment/Defamation/Bodily Privacy/Impersonation)
        if (category == ReportCategory.HARASSMENT ||
            category == ReportCategory.OBSCENITY ||
            category == ReportCategory.IMPERSONATION ||
            category == ReportCategory.NATIONAL_SECURITY ||
            category == ReportCategory.IP_INFRINGEMENT) {
            isEmergency = true;
        } else {
            isEmergency = false;
        }
    }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentReport)) return false;
        ContentReport other = (ContentReport) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
