package com.JanSahayak.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "post_views", indexes = {
        @Index(name = "idx_view_post",        columnList = "post_id"),
        @Index(name = "idx_view_social_post", columnList = "social_post_id"),
        @Index(name = "idx_view_user",        columnList = "user_id"),
        @Index(name = "idx_view_date",        columnList = "viewed_at"),
        @Index(name = "idx_view_post_user",   columnList = "post_id, user_id"),
        @Index(name = "idx_view_sp_user",     columnList = "social_post_id, user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PostView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The regular Post that was viewed.
     * Null when this is a SocialPost view.
     *
     * FIX: was nullable = false — caused a NOT NULL constraint violation every time
     * a SocialPost view was recorded (post was null). Changed to nullable = true.
     *
     * DB migration required if column was created as NOT NULL:
     *   ALTER TABLE post_views MODIFY COLUMN post_id BIGINT NULL;
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = true)
    private Post post;

    /**
     * The user who viewed the post.
     * Always required — anonymous views are not tracked.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * When the view happened.
     */
    @Column(name = "viewed_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date viewedAt = new Date();

    /**
     * How long the user spent viewing, in seconds.
     * Null if duration was not tracked.
     */
    @Column(name = "view_duration")
    private Integer viewDuration;

    /**
     * The SocialPost that was viewed.
     * Null when this is a regular Post view.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_post_id", nullable = true)
    private SocialPost socialPost;

    // ── Helper predicates ─────────────────────────────────────────────────

    @JsonIgnore
    public boolean isSocialPostView() {
        return socialPost != null;
    }

    @JsonIgnore
    public boolean isIssuePostView() {
        return post != null;
    }

    // ===== Helper Methods =====

    public boolean hasDuration() {
        return viewDuration != null && viewDuration > 0;
    }

    public Double getViewDurationInMinutes() {
        return viewDuration != null ? viewDuration / 60.0 : null;
    }

    @JsonIgnore
    public boolean isLongView() {
        return viewDuration != null && viewDuration > 30;
    }

    @JsonIgnore
    public boolean isQuickView() {
        return viewDuration != null && viewDuration < 5;
    }

    // ── Lifecycle Validation ──────────────────────────────────────────────

    /**
     * FIX: XOR guard — exactly ONE of [post, socialPost] must be set.
     * Consistent with PostLike, PostShare, and SavedPost.
     * Prevents corrupt rows that reference both or neither.
     */
    @PrePersist
    private void prePersist() {
        boolean hasPost       = post       != null;
        boolean hasSocialPost = socialPost != null;
        if (hasPost == hasSocialPost) {   // both true OR both false
            throw new IllegalStateException(
                    "PostView must reference exactly ONE of [Post, SocialPost], not both or neither. "
                            + "postId=" + (post != null ? post.getId() : "null")
                            + " socialPostId=" + (socialPost != null ? socialPost.getId() : "null"));
        }
        if (viewedAt == null) {
            viewedAt = new Date();
        }
    }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostView)) return false;
        PostView other = (PostView) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
