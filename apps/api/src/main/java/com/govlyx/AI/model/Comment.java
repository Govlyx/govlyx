package com.Govlyx.AI.model;

import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "comments", indexes = {
        @Index(name = "idx_comment_post",        columnList = "post_id"),
        @Index(name = "idx_comment_social_post", columnList = "social_post_id"),
        @Index(name = "idx_comment_user",        columnList = "user_id"),
        @Index(name = "idx_comment_actor_token", columnList = "actor_token"),
        @Index(name = "idx_comment_parent",      columnList = "parent_comment_id"),
        @Index(name = "idx_comment_created_at",  columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", unique = true, length = 36)
    private String idempotencyKey;

    @Column(name = "text", columnDefinition = "TEXT", nullable = true)
    @Size(max = 3000, message = "Comment text cannot exceed 3000 characters")
    private String text;

    @Column(name = "media_url", length = 1000)
    private String mediaUrl;

    @Column(name = "media_type")
    private String mediaType; // "STICKER", "GIF"

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    @Column(name = "like_count", nullable = false, columnDefinition = "integer default 0 not null")
    private Integer likeCount = 0;

    @Column(name = "dislike_count", nullable = false, columnDefinition = "integer default 0 not null")
    private Integer dislikeCount = 0;

    @Column(name = "is_pinned", nullable = false, columnDefinition = "boolean default false not null")
    private Boolean isPinned = false;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "boolean default false not null")
    private Boolean isDeleted = false;

    @Column(name = "deleted_by_type", length = 32)
    private String deletedByType; // e.g., "USER", "MODERATOR", "ADMIN"

    @Column(name = "ranking_score", nullable = false, columnDefinition = "double precision default 0.0 not null")
    private Double rankingScore = 0.0;

    // ===== Relationships =====

    /**
     * The regular (issue) Post this comment belongs to.
     * Null when this is a SocialPost comment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "post_id", nullable = true)
    private Post post;

    @Column(name = "actor_token", length = 70)
    private String actorToken;

    @Column(name = "author_username", length = 100)
    private String authorUsername;

    @Column(name = "author_profile_image", length = 500)
    private String authorProfileImage;

    /**
     * The user who wrote this comment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "ip_address", length = 64)
    @JsonIgnore
    private String ipAddress;

    /**
     * Parent comment — non-null when this is a reply.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id", nullable = true)
    private Comment parentComment;

    /**
     * Direct replies to this comment.
     */
    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Comment> replies = new ArrayList<>();

    /**
     * The SocialPost this comment belongs to.
     * Null when this is a regular Post comment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "social_post_id", nullable = true)
    private SocialPost socialPost;

    // ── Helper predicates ─────────────────────────────────────────────────

    @JsonIgnore
    public boolean isSocialPostComment() {
        return socialPost != null;
    }

    @JsonIgnore
    public boolean isIssuePostComment() {
        return post != null;
    }

    /**
     * Check if this is a top-level comment (not a reply)
     */
    @JsonIgnore
    public boolean isTopLevelComment() {
        return parentComment == null;
    }

    /**
     * Check if this is a reply to another comment
     */
    @JsonIgnore
    public boolean isReply() {
        return parentComment != null;
    }

    /**
     * Get total reply count
     */
    @JsonIgnore
    public int getReplyCount() {
        if (replies == null || !org.hibernate.Hibernate.isInitialized(replies)) {
            return 0;
        }
        return replies.size();
    }

    /**
     * Add a reply to this comment
     */
    public void addReply(Comment reply) {
        if (replies == null) {
            replies = new ArrayList<>();
        }
        replies.add(reply);
        reply.setParentComment(this);
    }

    /**
     * Remove a reply from this comment
     */
    public void removeReply(Comment reply) {
        if (replies != null) {
            replies.remove(reply);
            reply.setParentComment(null);
        }
    }

    /**
     * Get the depth level of this comment (0 for top-level, 1+ for replies)
     */
    @JsonIgnore
    public int getDepthLevel() {
        if (parentComment == null) {
            return 0;
        }
        return parentComment.getDepthLevel() + 1;
    }

    /**
     * Get the root comment of this comment thread
     */
    @JsonIgnore
    public Comment getRootComment() {
        Comment current = this;
        while (current.getParentComment() != null) {
            current = current.getParentComment();
        }
        return current;
    }

    /**
     * Check if this comment has any replies
     */
    @JsonIgnore
    public boolean hasReplies() {
        return replies != null && !replies.isEmpty();
    }

    // ===== Combined Helper Methods =====

    /**
     * Get total engagement count (just replies now)
     */
    @JsonIgnore
    public int getTotalEngagementCount() {
        return getReplyCount();
    }

    /**
     * Check if this comment has any activity
     */
    public boolean hasActivity() {
        return hasReplies();
    }

    // ── Lifecycle Validation ──────────────────────────────────────────────

    /**
     * FIX: XOR guard — exactly ONE of [post, socialPost] must be set.
     * Prevents corrupt comment rows that reference both posts or neither.
     * Replies (parentComment != null) inherit the post type from their parent,
     * so they must also pass the XOR check.
     *
     * Consistent with PostLike, PostShare, PostView, and SavedPost guards.
     */
    @PrePersist
    private void prePersist() {
        boolean hasPost       = post       != null;
        boolean hasSocialPost = socialPost != null;
        if (hasPost == hasSocialPost) {   // both true OR both false
            throw new IllegalStateException(
                    "Comment must reference exactly ONE of [Post, SocialPost], not both or neither. "
                            + "postId=" + (post != null ? post.getId() : "null")
                            + " socialPostId=" + (socialPost != null ? socialPost.getId() : "null"));
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
        if (!(o instanceof Comment)) return false;
        Comment other = (Comment) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }



}
