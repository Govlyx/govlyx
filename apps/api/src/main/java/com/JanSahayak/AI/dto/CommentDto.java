package com.JanSahayak.AI.dto;

import com.JanSahayak.AI.model.Comment;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentDto implements Serializable {
    private Long id;
    private String text;
    private Date createdAt;
    private Date updatedAt;
    private AuthorDto author;
    private Long postId;
    private Long socialPostId; // ✅ NEW FIELD FOR SOCIAL POSTS
    private Long parentCommentId;
    private Integer replyCount;
    private Boolean isReply;
    private Integer depthLevel;
    
    private Integer likeCount;
    private Integer dislikeCount;
    private Boolean isPinned;
    private Boolean isDeleted;
    private String deletedByType;
    private Double rankingScore;
    
    private Boolean likedByCurrentUser;
    private Boolean dislikedByCurrentUser;
    private String userVote;

    /**
     * Convert Comment entity to CommentDto
     */
    public static CommentDto fromComment(Comment comment) {
        if (comment == null) {
            return null;
        }

        CommentDto dto = new CommentDto();
        dto.setId(comment.getId());
        dto.setText(comment.getText());
        dto.setCreatedAt(comment.getCreatedAt());
        dto.setUpdatedAt(comment.getUpdatedAt());

        // Convert author snapshot or user to AuthorDto to prevent LazyInitializationException
        String roleName = "ROLE_USER";
        if (comment.getUser() != null) {
            try {
                if (comment.getUser().getRole() != null && org.hibernate.Hibernate.isInitialized(comment.getUser().getRole())) {
                    roleName = comment.getUser().getRole().getName();
                }
            } catch (Exception ignored) {}
        }

        String username = comment.getAuthorUsername();
        if (username == null || username.isBlank() || username.startsWith("acc_") || username.startsWith("act_")) {
            if (comment.getUser() != null && !com.JanSahayak.AI.payload.PostUtility.isCitizen(comment.getUser())) {
                username = comment.getUser().getActualUsername();
            } else if (comment.getUser() != null && comment.getUser().getUsername() != null && !comment.getUser().getUsername().startsWith("acc_")) {
                username = comment.getUser().getUsername();
            } else {
                username = "Citizen";
            }
        }

        String profileImage = comment.getAuthorProfileImage();
        if ((profileImage == null || profileImage.isBlank()) && comment.getUser() != null) {
            profileImage = comment.getUser().getProfileImage();
        }

        Long userId = (comment.getUser() != null && !com.JanSahayak.AI.payload.PostUtility.isCitizen(comment.getUser()))
                ? comment.getUser().getId()
                : null;

        dto.setAuthor(AuthorDto.builder()
                .id(userId)
                .username(username)
                .profileImage(profileImage)
                .pincode(comment.getUser() != null ? comment.getUser().getPincode() : null)
                .roleName(roleName)
                .isActive(comment.getUser() != null ? comment.getUser().getIsActive() : true)
                .build());

        // ✅ Set post ID safely (for issue posts)
        if (comment.getPost() != null) {
            dto.setPostId(comment.getPost().getId());
        }

        // ✅ Set social post ID safely (for social posts)
        if (comment.getSocialPost() != null) {
            dto.setSocialPostId(comment.getSocialPost().getId());
        }

        // Set parent comment ID safely and determine if it's a reply
        if (comment.getParentComment() != null) {
            dto.setParentCommentId(comment.getParentComment().getId());
            dto.setIsReply(true);
        } else {
            dto.setParentCommentId(null);
            dto.setIsReply(false);
        }

        dto.setLikeCount(comment.getLikeCount());
        dto.setDislikeCount(comment.getDislikeCount());
        dto.setIsPinned(comment.getIsPinned());
        dto.setIsDeleted(comment.getIsDeleted());
        dto.setDeletedByType(comment.getDeletedByType());
        dto.setRankingScore(comment.getRankingScore());

        // Set reply count and depth level from Comment entity methods
        dto.setReplyCount(comment.getReplyCount());
        dto.setDepthLevel(comment.getDepthLevel());

        return dto;
    }

    /**
     * Convert Comment entity to CommentDto with custom reply count
     */
    public static CommentDto fromComment(Comment comment, Integer customReplyCount) {
        CommentDto dto = fromComment(comment);
        if (dto != null) {
            dto.setReplyCount(customReplyCount != null ? customReplyCount : 0);
        }
        return dto;
    }

    // ===== NEW HELPER METHODS FOR SOCIAL POSTS =====

    /**
     * Check if this comment is on a social post
     */
    public boolean isOnSocialPost() {
        return socialPostId != null;
    }

    /**
     * Check if this comment is on an issue post
     */
    public boolean isOnIssuePost() {
        return postId != null;
    }

    /**
     * Get the post ID (works for both post types)
     */
    public Long getAnyPostId() {
        return postId != null ? postId : socialPostId;
    }

    /**
     * Check if comment has a valid post reference
     */
    public boolean hasValidPostReference() {
        return postId != null || socialPostId != null;
    }

    // ===== EXISTING HELPER METHODS =====

    /**
     * Check if this is a top-level comment (not a reply)
     */
    public boolean isTopLevelComment() {
        return parentCommentId == null || !Boolean.TRUE.equals(isReply);
    }

    /**
     * Check if this comment has replies
     */
    public boolean hasReplies() {
        return replyCount != null && replyCount > 0;
    }

    /**
     * Get safe reply count (never null)
     */
    public Integer getSafeReplyCount() {
        return replyCount != null ? replyCount : 0;
    }

    /**
     * Get safe depth level (never null)
     */
    public Integer getSafeDepthLevel() {
        return depthLevel != null ? depthLevel : 0;
    }

    /**
     * Get author username safely
     */
    public String getAuthorUsername() {
        return (author != null && author.getUsername() != null && !author.getUsername().isBlank())
                ? author.getUsername() : "Anonymous";
    }

    public String getUsername() {
        return getAuthorUsername();
    }

    public String getUserDisplayName() {
        return getAuthorUsername();
    }

    /**
     * Get author display name safely
     */
    public String getAuthorDisplayName() {
        return getAuthorUsername();
    }

    /**
     * Check if author is admin
     */
    public boolean isAuthorAdmin() {
        return author != null && author.isAdmin();
    }

    /**
     * Check if author is department
     */
    public boolean isAuthorDepartment() {
        return author != null && author.isDepartment();
    }

    /**
     * Check if author is normal user
     */
    public boolean isAuthorNormalUser() {
        return author != null && author.isNormalUser();
    }

    /**
     * Get author profile image URL
     */
    public String getAuthorProfileImage() {
        return author != null ? author.getProfileImage() : null;
    }

    public String getUserProfileImage() {
        return getAuthorProfileImage();
    }


    /**
     * Check if author has profile image
     */
    public boolean authorHasProfileImage() {
        return author != null && author.hasProfileImage();
    }

    /**
     * Get author pincode
     */
    public String getAuthorPincode() {
        return author != null ? author.getPincode() : null;
    }

    /**
     * Check if author has valid pincode
     */
    public boolean authorHasPincode() {
        return author != null && author.hasPincode();
    }

    /**
     * Get total engagement count (just replies for now)
     */
    public int getTotalEngagementCount() {
        return getSafeReplyCount();
    }

    /**
     * Check if this comment has any activity
     */
    public boolean hasActivity() {
        return hasReplies();
    }

    /**
     * Check if comment text is not empty
     */
    public boolean hasText() {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * Get truncated text for preview
     */
    public String getPreviewText(int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Get time ago string
     */
    public String getTimeAgo() {
        if (createdAt == null) {
            return "Unknown";
        }

        long diffInMillies = new Date().getTime() - createdAt.getTime();
        long diffInMinutes = diffInMillies / (60 * 1000);
        long diffInHours = diffInMillies / (60 * 60 * 1000);
        long diffInDays = diffInMillies / (24 * 60 * 60 * 1000);

        if (diffInMinutes < 1) {
            return "Just now";
        } else if (diffInMinutes < 60) {
            return diffInMinutes + " minute" + (diffInMinutes == 1 ? "" : "s") + " ago";
        } else if (diffInHours < 24) {
            return diffInHours + " hour" + (diffInHours == 1 ? "" : "s") + " ago";
        } else if (diffInDays < 7) {
            return diffInDays + " day" + (diffInDays == 1 ? "" : "s") + " ago";
        } else {
            long weeks = diffInDays / 7;
            return weeks + " week" + (weeks == 1 ? "" : "s") + " ago";
        }
    }

    /**
     * Check if comment was created recently (within last 24 hours)
     */
    public boolean isRecent() {
        if (createdAt == null) return false;
        long diffInMillies = new Date().getTime() - createdAt.getTime();
        return diffInMillies < (24 * 60 * 60 * 1000); // 24 hours
    }

    /**
     * Check if comment was updated (has updatedAt timestamp)
     */
    public boolean wasUpdated() {
        return updatedAt != null &&
                createdAt != null &&
                updatedAt.after(createdAt);
    }
}
