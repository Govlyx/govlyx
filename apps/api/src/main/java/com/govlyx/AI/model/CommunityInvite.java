package com.Govlyx.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.UUID;

/**
 * CommunityInvite — one row per invite.
 *
 * Two invite modes:
 *  1. Username invite  → invitee != null, singleUse = true
 *     Admin targets a specific user. Only that user can accept.
 *
 *  2. Link invite      → invitee = null,  singleUse = false
 *     Admin generates a shareable link. Anyone with the link can join
 *     until it expires or is revoked.
 *
 * SECRET communities: both modes allowed — invite is the ONLY way to join.
 * PRIVATE communities: both modes allowed — invitee skips the join-request queue.
 * PUBLIC communities: invites are blocked at the service layer (no point).
 */
@Entity
@Table(
        name = "community_invites",
        indexes = {
                @Index(name = "idx_ci_community",  columnList = "community_id"),
                @Index(name = "idx_ci_inviter",    columnList = "inviter_id"),
                @Index(name = "idx_ci_invitee",    columnList = "invitee_id"),
                @Index(name = "idx_ci_token",      columnList = "token"),
                @Index(name = "idx_ci_status",     columnList = "status"),
                @Index(name = "idx_ci_expires",    columnList = "expires_at")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CommunityInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Relations ─────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ci_community"))
    private Community community;

    /** Admin/owner who created this invite. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ci_inviter"))
    private User inviter;

    /**
     * Who this invite is for.
     * NULL = shareable link (anyone with token can use it).
     * Non-null = targeted invite — only this user can accept.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id",
            foreignKey = @ForeignKey(name = "fk_ci_invitee"))
    private User invitee;

    // ── Token & Link ──────────────────────────────────────────────────────────

    /**
     * URL-safe random token used in the invite link.
     * e.g. https://Govlyx.in/invite/a3f9b2c1d7e8f0a1
     * Generated in @PrePersist if not set.
     */
    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    // ── Content ───────────────────────────────────────────────────────────────

    /** Optional personal message from the inviter. Max 300 chars. */
    @Column(name = "message", length = 300)
    private String message;

    // ── Status ────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InviteStatus status = InviteStatus.PENDING;

    /**
     * true  = only ONE person can use this invite (targeted username invite).
     * false = multiple people can use this link until it expires (shareable link).
     */
    @Column(name = "single_use", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    private Boolean singleUse = true;

    /** How many times this link has been used (relevant for multi-use links). */
    @Column(name = "use_count", nullable = false)
    @Builder.Default
    private Integer useCount = 0;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date createdAt = new Date();

    /** When this invite expires. Set to 7 days from creation by default. */
    @Column(name = "expires_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date expiresAt;

    /** When the invite was accepted / revoked. */
    @Column(name = "actioned_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date actionedAt;

    /** User who accepted (may differ from invitee for link-only invites). */
    @Column(name = "accepted_by_user_id")
    private Long acceptedByUserId;

    // ── Enum ──────────────────────────────────────────────────────────────────

    public enum InviteStatus {
        PENDING,   // waiting for acceptance
        ACCEPTED,  // invite was used (single-use) — link invites stay PENDING
        REVOKED,   // admin manually revoked
        EXPIRED,   // past expiresAt date (set by scheduled cleanup or on-check)
        DECLINED   // declined by the user
    }

    // ── Business helpers ─────────────────────────────────────────────────────

    @JsonIgnore
    public boolean isPending() {
        return InviteStatus.PENDING.equals(status);
    }

    @JsonIgnore
    public boolean isExpired() {
        return expiresAt != null && new Date().after(expiresAt);
    }

    /**
     * Whether this invite can still be used.
     * A link invite stays PENDING even after being used (multi-use),
     * so we check both status AND expiry AND use-count.
     */
    public boolean isUsable() {
        if (!isPending()) return false;
        if (isExpired())  return false;
        // For single-use, once useCount > 0 it is no longer usable
        if (Boolean.TRUE.equals(singleUse) && useCount != null && useCount > 0) return false;
        return true;
    }

    public void markAccepted(Long userId) {
        this.acceptedByUserId = userId;
        this.actionedAt       = new Date();
        this.useCount         = (this.useCount != null ? this.useCount : 0) + 1;
        // Single-use → flip status; multi-use → stay PENDING so others can still use it
        if (Boolean.TRUE.equals(singleUse)) {
            this.status = InviteStatus.ACCEPTED;
        }
    }

    public void revoke() {
        this.status      = InviteStatus.REVOKED;
        this.actionedAt  = new Date();
    }

    public void markExpired() {
        this.status = InviteStatus.EXPIRED;
    }

    public void decline() {
        this.status     = InviteStatus.DECLINED;
        this.actionedAt = new Date();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    private void prePersist() {
        if (token == null) {
            token = UUID.randomUUID().toString().replace("-", "");
        }
        if (createdAt == null) {
            createdAt = new Date();
        }
        if (expiresAt == null) {
            // Default: 7 days
            expiresAt = new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000);
        }
    }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommunityInvite)) return false;
        CommunityInvite other = (CommunityInvite) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
