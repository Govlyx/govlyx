package com.JanSahayak.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(
        name = "post_shares",
        indexes = {
                @Index(name = "idx_post_share_post",        columnList = "post_id"),
                @Index(name = "idx_post_share_social_post", columnList = "social_post_id"),
                @Index(name = "idx_post_share_user",        columnList = "user_id"),
                @Index(name = "idx_post_share_shared_at",   columnList = "shared_at"),
                @Index(name = "idx_post_share_type",        columnList = "share_type"),
                @Index(name = "idx_post_share_post_user",   columnList = "post_id, user_id"),
                @Index(name = "idx_post_share_sp_user",     columnList = "social_post_id, user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PostShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== Relationships =====

    /**
     * The regular (issue/broadcast) Post that was shared.
     * Null when this record belongs to a SocialPost share.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id",
            foreignKey = @ForeignKey(name = "fk_post_share_post"))
    private Post post;

    /**
     * The SocialPost that was shared.
     * Null when this record belongs to a regular Post share.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_post_id",
            foreignKey = @ForeignKey(name = "fk_post_share_social_post"))
    private SocialPost socialPost;

    /**
     * The user who performed the share.
     * Nullable to support future anonymous share tracking.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",
            foreignKey = @ForeignKey(name = "fk_post_share_user"))
    private User user;

    // ===== Share Metadata =====

    /**
     * Platform / method used. Stored as STRING so new channels can be added
     * without a schema migration.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "share_type", nullable = false, length = 30)
    @Builder.Default
    private ShareType shareType = ShareType.LINK_COPY;

    // ===== Timestamps =====

    @Column(name = "shared_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date sharedAt = new Date();

    // ===== Enums =====

    public enum ShareType {
        LINK_COPY,      // User copied the shareable link
        WHATSAPP,       // Shared via WhatsApp
        TWITTER,        // Shared via Twitter / X
        FACEBOOK,       // Shared via Facebook
        INSTAGRAM,      // Shared via Instagram Stories / DM
        TELEGRAM,       // Shared via Telegram
        EMAIL,          // Shared via e-mail
        SMS,            // Shared via SMS
        NATIVE_SHARE,   // Device-level OS share sheet (generic fallback)
        EMBED,          // Embedded in an external site
        OTHER           // Any other platform
    }

    // ===== Helper Methods =====

    @JsonIgnore
    public boolean isSocialPostShare() { return socialPost != null; }
    @JsonIgnore
    public boolean isIssuePostShare()  { return post       != null; }

    public Long getPostId()       { return post       != null ? post.getId()       : null; }
    public Long getSocialPostId() { return socialPost != null ? socialPost.getId() : null; }
    public Long getUserId()       { return user       != null ? user.getId()       : null; }

    // ===== Lifecycle Validation =====

    /**
     * Guarantees the XOR constraint at the DB layer before every INSERT.
     * Throws IllegalStateException which rolls back the transaction cleanly.
     */
    @PrePersist
    private void prePersist() {
        boolean hasPost       = post       != null;
        boolean hasSocialPost = socialPost != null;
        if (hasPost == hasSocialPost) {          // both true OR both false
            throw new IllegalStateException(
                    "PostShare must reference exactly ONE of [Post, SocialPost], not both or neither.");
        }
        if (shareType == null) {
            shareType = ShareType.LINK_COPY;
        }
        if (sharedAt == null) {
            sharedAt = new Date();
        }
    }

    @Override
    public String toString() {
        return String.format("PostShare{id=%d, postId=%s, socialPostId=%s, userId=%d, type=%s}",
                id, getPostId(), getSocialPostId(), getUserId(), shareType);
    }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostShare)) return false;
        PostShare other = (PostShare) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
