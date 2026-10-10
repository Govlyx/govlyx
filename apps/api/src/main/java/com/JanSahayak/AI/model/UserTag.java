package com.JanSahayak.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

/**
 * UserTag — tracks @mentions of a User inside either a regular government Post
 * or a SocialPost.
 *
 * ── Design contract ──────────────────────────────────────────────────────────
 *  • Exactly ONE of [post, socialPost] must be non-null.
 *    Enforced by the @PrePersist XOR guard (consistent with PostLike, PostShare,
 *    PostView, SavedPost, and Comment).
 *
 *  • A user can be tagged at most once per post/social-post.
 *    Enforced by partial unique indexes (see DB migration below).
 *
 *  • Tags can be deactivated (soft-deleted) without losing the audit trail.
 *
 * ── DB migration required (run once in Flyway) ───────────────────────────────
 *
 *   -- 1. Make post_id nullable (was NOT NULL — root cause of the startup WARN)
 *   ALTER TABLE user_tags ALTER COLUMN post_id DROP NOT NULL;
 *
 *   -- 2. Add social_post_id column
 *   ALTER TABLE user_tags
 *       ADD COLUMN social_post_id BIGINT NULL,
 *       ADD CONSTRAINT fk_user_tag_social_post
 *           FOREIGN KEY (social_post_id) REFERENCES social_posts(id);
 *
 *   -- 3. Drop the old unique constraint that only covered post_id
 *   ALTER TABLE user_tags DROP CONSTRAINT IF EXISTS uk_post_tagged_user;
 *
 *   -- 4. Partial unique indexes — PostgreSQL supports WHERE clauses natively
 *   --    (supported since PG 7.2, no version caveat needed)
 *   CREATE UNIQUE INDEX IF NOT EXISTS uk_user_tag_post_user
 *       ON user_tags (post_id, tagged_user_id)
 *       WHERE post_id IS NOT NULL;
 *
 *   CREATE UNIQUE INDEX IF NOT EXISTS uk_user_tag_social_post_user
 *       ON user_tags (social_post_id, tagged_user_id)
 *       WHERE social_post_id IS NOT NULL;
 *
 *   -- 5. Orphan cleanup — removes rows that caused the FK constraint WARN
 *   DELETE FROM user_tags
 *       WHERE post_id IS NOT NULL
 *         AND post_id NOT IN (SELECT id FROM posts);
 */
@Entity
@Table(
        name = "user_tags",
        indexes = {
                // NOTE: Uniqueness is enforced by partial indexes created in Flyway
                // (see migration above). @UniqueConstraint is intentionally omitted
                // because PostgreSQL @UniqueConstraint requires all columns to be
                // NOT NULL — post_id and social_post_id are both nullable by design.
                @Index(name = "idx_user_tag_post",        columnList = "post_id"),
                @Index(name = "idx_user_tag_social_post", columnList = "social_post_id"),
                @Index(name = "idx_user_tag_tagged_user", columnList = "tagged_user_id"),
                @Index(name = "idx_user_tag_tagged_by",   columnList = "tagged_by_user_id"),
                @Index(name = "idx_user_tag_active",      columnList = "is_active"),
                @Index(name = "idx_user_tag_tagged_at",   columnList = "tagged_at")
        }
)
@Getter                 // ← replaces @Data: generates only getters
@Setter                 // ← replaces @Data: generates only setters
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTag {

    // =========================================================================
    // IDENTITY
    // =========================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================================================
    // POLYMORPHIC POST REFERENCE  (exactly ONE must be non-null)
    // =========================================================================

    /**
     * The regular government/issue Post in which the user was tagged.
     * NULL when this tag belongs to a SocialPost.
     *
     * FIX: was nullable = false — caused the FK constraint WARN on every startup
     * because existing rows with NULL post_id violated the NOT NULL constraint.
     * Changed to nullable = true to match the XOR pattern used across all entities.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "post_id",
            nullable = true,                                    // ← FIXED (was false)
            foreignKey = @ForeignKey(name = "fk_user_tag_post")
    )
    private Post post;

    /**
     * The SocialPost in which the user was tagged.
     * NULL when this tag belongs to a regular Post.
     *
     * NEW: mirrors the pattern from PostLike, PostShare, PostView, SavedPost,
     * and Comment — all of which support both Post and SocialPost.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "social_post_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_user_tag_social_post")
    )
    private SocialPost socialPost;

    // =========================================================================
    // ACTOR REFERENCES
    // =========================================================================

    /**
     * The user who was mentioned / tagged.
     * Always required.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tagged_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_tag_tagged_user")
    )
    private User taggedUser;

    /**
     * The user who created the tag (the post author or commenter).
     * Nullable to support future system-generated tags.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tagged_by_user_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_user_tag_tagged_by")
    )
    private User taggedBy;

    // =========================================================================
    // STATE
    // =========================================================================

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    private Boolean isActive = true;

    // =========================================================================
    // TIMESTAMPS
    // =========================================================================

    @Column(name = "tagged_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date taggedAt = new Date();

    // =========================================================================
    // DEACTIVATION AUDIT TRAIL
    // =========================================================================

    @Column(name = "deactivated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date deactivatedAt;

    /** ID (not entity) stored to avoid loading the user just for audit. */
    @Column(name = "deactivated_by_user_id")
    private Long deactivatedByUserId;

    @Column(name = "deactivation_reason", length = 500)
    private String deactivationReason;

    // =========================================================================
    // TYPE PREDICATES
    // =========================================================================

    /** @return true when this tag lives inside a SocialPost. */
    @JsonIgnore
    public boolean isSocialPostTag() {
        return socialPost != null;
    }

    /** @return true when this tag lives inside a regular government/issue Post. */
    @JsonIgnore
    public boolean isIssuePostTag() {
        return post != null;
    }

    // =========================================================================
    // STATE HELPERS
    // =========================================================================

    /** @return true when the tag is currently active (not soft-deleted). */
    @JsonIgnore
    public boolean isActiveTag() {
        return Boolean.TRUE.equals(isActive);
    }

    // =========================================================================
    // IDENTITY HELPERS
    // =========================================================================

    /**
     * Returns the display username of the tagged user.
     * Uses getActualUsername() so it returns the profile handle, not the login email.
     */
    @JsonIgnore
    public String getTaggedUsername() {
        return taggedUser != null ? taggedUser.getActualUsername() : null;
    }

    /**
     * Returns the display username of the user who created the tag.
     * Uses getActualUsername() so it returns the profile handle, not the login email.
     */
    @JsonIgnore
    public String getTaggedByUsername() {
        return taggedBy != null ? taggedBy.getActualUsername() : null;
    }

    /**
     * Convenience ID accessor — avoids loading the full Post proxy.
     * @return post ID, or null when this is a SocialPost tag.
     */
    public Long getPostId() {
        return post != null ? post.getId() : null;
    }

    /**
     * Convenience ID accessor — avoids loading the full SocialPost proxy.
     * @return social post ID, or null when this is a regular Post tag.
     */
    public Long getSocialPostId() {
        return socialPost != null ? socialPost.getId() : null;
    }

    /**
     * Returns the tagged user's ID without triggering a lazy-load.
     */
    public Long getTaggedUserId() {
        return taggedUser != null ? taggedUser.getId() : null;
    }

    /**
     * Returns the tagger's ID without triggering a lazy-load.
     */
    public Long getTaggedByUserId() {
        return taggedBy != null ? taggedBy.getId() : null;
    }

    // =========================================================================
    // LIFECYCLE MANAGEMENT
    // =========================================================================

    /**
     * Soft-deletes this tag, preserving the full audit trail.
     *
     * @param deactivatedBy the moderator or system actor performing the action
     * @param reason        human-readable reason (stored for audit)
     */
    public void deactivate(User deactivatedBy, String reason) {
        this.isActive             = false;
        this.deactivatedAt        = new Date();
        this.deactivatedByUserId  = deactivatedBy != null ? deactivatedBy.getId() : null;
        this.deactivationReason   = reason;
    }

    /**
     * Reverses a previous deactivation, clearing all audit columns.
     */
    public void reactivate() {
        this.isActive             = true;
        this.deactivatedAt        = null;
        this.deactivatedByUserId  = null;
        this.deactivationReason   = null;
    }

    // =========================================================================
    // LIFECYCLE CALLBACKS
    // =========================================================================

    /**
     * XOR guard — exactly ONE of [post, socialPost] must be set before every INSERT.
     *
     * Consistent with the same guard in:
     *   PostLike    → @PrePersist
     *   PostShare   → @PrePersist
     *   PostView    → @PrePersist
     *   SavedPost   → @PrePersist
     *   Comment     → @PrePersist
     *
     * Throws IllegalStateException which rolls back the transaction cleanly
     * and surfaces as a 500 / audit log entry — never silently persists bad data.
     */
    @PrePersist
    private void prePersist() {
        boolean hasPost       = post       != null;
        boolean hasSocialPost = socialPost != null;

        if (hasPost == hasSocialPost) {           // both true OR both false
            throw new IllegalStateException(
                    "UserTag must reference exactly ONE of [Post, SocialPost], not both or neither. "
                            + "postId="       + (post       != null ? post.getId()       : "null")
                            + " socialPostId=" + (socialPost != null ? socialPost.getId() : "null")
            );
        }

        if (taggedUser == null) {
            throw new IllegalStateException("UserTag.taggedUser must not be null.");
        }

        if (taggedAt == null) {
            taggedAt = new Date();
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    // =========================================================================
    // OBJECT IDENTITY
    // =========================================================================

    /**
     * Safe toString — uses only scalar fields and ID accessors.
     * Does NOT call toString() on any lazy-loaded JPA entity proxy,
     * preventing LazyInitializationException and infinite recursion
     * (which @Data's generated toString() would cause).
     */
    @Override
    public String toString() {
        if (isSocialPostTag()) {
            return String.format(
                    "UserTag{id=%d, socialPostId=%d, taggedUser=%s, taggedBy=%s, active=%s}",
                    id, getSocialPostId(), getTaggedUsername(), getTaggedByUsername(), isActive);
        }
        return String.format(
                "UserTag{id=%d, postId=%d, taggedUser=%s, taggedBy=%s, active=%s}",
                id, getPostId(), getTaggedUsername(), getTaggedByUsername(), isActive);
    }

    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    /**
     * Identity-based equality — two UserTag instances are equal only when
     * they share the same database ID.
     *
     * WHY NOT @Data's generated equals/hashCode:
     *   @Data generates equals/hashCode that traverses ALL fields, including
     *   lazy-loaded JPA proxies (post, socialPost, taggedUser, taggedBy).
     *   Touching those proxies outside a Hibernate session throws
     *   LazyInitializationException, and traversing bidirectional associations
     *   causes infinite recursion / StackOverflow.
     *
     * This implementation is safe in all contexts — inside and outside sessions,
     *   in Sets, Maps, and Spring caches.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserTag)) return false;
        UserTag other = (UserTag) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        // Constant hashCode for unsaved entities (id == null) is intentional:
        // it keeps the contract stable before and after a flush/persist call.
        return id != null ? id.hashCode() : 31;
    }
}
