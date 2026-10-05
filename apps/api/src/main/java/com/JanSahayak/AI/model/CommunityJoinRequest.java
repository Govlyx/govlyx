package com.JanSahayak.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.*;
import java.util.Date;

@Entity
@Table(name = "community_join_requests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"community_id", "user_id"}, name = "uk_join_request"),
        indexes = {
                @Index(name = "idx_jr_community", columnList = "community_id"),
                @Index(name = "idx_jr_user",      columnList = "user_id"),
                @Index(name = "idx_jr_status",    columnList = "status"),
                @Index(name = "idx_jr_requested", columnList = "requested_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CommunityJoinRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false, foreignKey = @ForeignKey(name = "fk_jr_community"))
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_jr_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "message",          length = 500) private String message;
    @Column(name = "rejection_reason", length = 500) private String rejectionReason;
    @Column(name = "reviewed_by_user_id")             private Long   reviewedByUserId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP) @Builder.Default private Date requestedAt = new Date();

    @Column(name = "reviewed_at") @Temporal(TemporalType.TIMESTAMP) private Date reviewedAt;

    public enum RequestStatus { PENDING, APPROVED, REJECTED, CANCELLED }

    @JsonIgnore
    public boolean isPending() { return RequestStatus.PENDING.equals(status); }

    public void approve(Long reviewerId) {
        this.status = RequestStatus.APPROVED;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = new Date();
    }

    public void reject(Long reviewerId, String reason) {
        this.status = RequestStatus.REJECTED;
        this.reviewedByUserId = reviewerId;
        this.rejectionReason = reason;
        this.reviewedAt = new Date();
    }

    public void cancel() { this.status = RequestStatus.CANCELLED; }

    @PrePersist
    private void prePersist() { if (requestedAt == null) requestedAt = new Date(); }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommunityJoinRequest)) return false;
        CommunityJoinRequest other = (CommunityJoinRequest) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
