package com.JanSahayak.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Entity
@Table(
        name = "post_likes",
        indexes = {
                // NOTE: We intentionally do NOT use @UniqueConstraint here.
                //
                // ROOT CAUSE OF BUG: MySQL required all columns in a @UniqueConstraint
                // to be NOT NULL. Since post_id IS null for social-post likes, and
                // social_post_id IS null for regular-post likes, Hibernate was generating
                // (or the existing schema had) post_id as NOT NULL — causing the
                // "Column 'post_id' cannot be null" error every time a SocialPost was liked.
                //
                // SOLUTION: Replace @UniqueConstraint with plain @Index (non-unique).
                // True uniqueness is enforced by:
                //   (a) Service layer: findByPostAndUser / findBySocialPostAndUser checks
                //       before every INSERT.
                //   (b) PostgreSQL partial indexes (run once in Flyway migration):
                //         -- Drop any old broken unique constraints if they exist:
                //         DROP INDEX IF EXISTS uq_post_like_post_user;
                //         DROP INDEX IF EXISTS uq_post_like_social_post_user;
                //         -- PostgreSQL natively supports partial indexes on nullable
                //         -- columns — no version caveat needed (supported since PG 7.2):
                //         CREATE UNIQUE INDEX uq_post_like_post_user
                //             ON post_likes (post_id, user_id)
                //             WHERE post_id IS NOT NULL;
                //         CREATE UNIQUE INDEX uq_post_like_social_post_user
                //             ON post_likes (social_post_id, user_id)
                //             WHERE social_post_id IS NOT NULL;
                @Index(name = "idx_post_like_post_user",        columnList = "post_id, user_id"),
                @Index(name = "idx_post_like_social_post_user", columnList = "social_post_id, user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PostLike {

    /**
     * Reaction type stored on the existing post_likes row.
     * No new entity or table needed — a single column distinguishes LIKE from DISLIKE.
     *
     * DB migration required:
     *   ALTER TABLE post_likes
     *       ADD COLUMN reaction_type VARCHAR(10) NOT NULL DEFAULT 'LIKE';
     */
    public enum ReactionType {
        LIKE,
        DISLIKE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The regular Post this like belongs to.
     * NULL when this is a SocialPost like.
     * Explicitly nullable = true — MUST be nullable or the NOT NULL constraint
     * will reject every SocialPost like with "Column 'post_id' cannot be null".
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = true)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The SocialPost this like belongs to.
     * NULL when this is a regular Post like.
     * Explicitly nullable = true for symmetry and clarity.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_post_id", nullable = true)
    private SocialPost socialPost;

    /**
     * Whether this row represents a like or a dislike.
     * Defaults to LIKE so all existing rows stay valid with no data migration.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 10)
    private ReactionType reactionType = ReactionType.LIKE;

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    // ── Helper predicates ─────────────────────────────────────────────────

    @JsonIgnore
    public boolean isSocialPostLike() {
        return socialPost != null;
    }

    @JsonIgnore
    public boolean isIssuePostLike() {
        return post != null;
    }

    @JsonIgnore
    public boolean isLike() {
        return ReactionType.LIKE.equals(reactionType);
    }

    @JsonIgnore
    public boolean isDislike() {
        return ReactionType.DISLIKE.equals(reactionType);
    }

    // ── Lifecycle Validation ──────────────────────────────────────────────

    /**
     * Ensures exactly ONE of [post, socialPost] is set before every INSERT.
     * Consistent with the XOR guards in PostShare and PostView.
     * Prevents corrupt rows that reference both entities or neither.
     */
    @PrePersist
    private void prePersist() {
        boolean hasPost       = post       != null;
        boolean hasSocialPost = socialPost != null;
        if (hasPost == hasSocialPost) {   // both true OR both false
            throw new IllegalStateException(
                    "PostLike must reference exactly ONE of [Post, SocialPost], not both or neither. "
                            + "postId=" + (post != null ? post.getId() : "null")
                            + " socialPostId=" + (socialPost != null ? socialPost.getId() : "null"));
        }
        if (reactionType == null) {
            reactionType = ReactionType.LIKE;
        }
        if (createdAt == null) {
            createdAt = new Date();
        }
    }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostLike)) return false;
        PostLike other = (PostLike) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
