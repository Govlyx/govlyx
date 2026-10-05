package com.JanSahayak.AI.dto;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.enums.BroadcastScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * PostResponse
 *
 * Flat, serialization-safe DTO returned by every Post endpoint.
 * Covers both citizen issue posts and government broadcast posts.
 *
 * Fields added vs. previous version (marked NEW):
 * saveCount — total bookmarks on the post
 * isSavedByCurrentUser — bookmark active-state for the requesting user
 * isGovernmentBroadcast — true when the post was created by a gov. account
 * countryWideBroadcast — true when scope == COUNTRY (convenience flag)
 * canSave — true when the post is bookmark-eligible (broadcast only)
 *
 * All fields are populated in PostService.convertToPostResponse().
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {

    // =========================================================================
    // BASIC POST INFORMATION
    // =========================================================================

    private Long id;
    private String content;
    private String translatedContent;
    private Boolean isTranslated;
    private PostStatus status;
    private Date createdAt;
    private Date updatedAt;

    // =========================================================================
    // MEDIA INFORMATION
    // =========================================================================

    private String imageName;
    private Boolean hasImage;
    private String mediaType;

    // =========================================================================
    // RESOLUTION INFORMATION
    // =========================================================================

    private Boolean isResolved;
    private Date resolvedAt;

    // =========================================================================
    // AUTHOR / USER INFORMATION
    // =========================================================================

    private Long userId;
    private String username;
    private String userDisplayName;
    private String userProfileImage;
    private String userPincode;

    // =========================================================================
    // BROADCASTING INFORMATION
    // =========================================================================

    private BroadcastScope broadcastScope;
    private String broadcastScopeDescription;
    private Boolean isBroadcastPost;

    /**
     * NEW — true when the post was created by a government/department/admin account
     * AND has a non-null broadcast scope.
     * Lets the frontend badge government broadcasts distinctly from citizen issue
     * posts.
     */
    private Boolean isGovernmentBroadcast;

    /**
     * NEW — convenience flag: true when broadcastScope == COUNTRY.
     * Avoids enum comparison on the frontend for the most prominent broadcast type.
     */
    private Boolean countryWideBroadcast;

    private String targetCountry;
    private List<String> targetStates;
    private List<String> targetDistricts;
    private List<String> targetPincodes;

    // =========================================================================
    // ENGAGEMENT METRICS
    // =========================================================================

    private Integer likeCount;

    /**
     * Dislike count — shown on the dislike button label.
     * Populated from Post.dislikeCount (denormalized column).
     */
    private Integer dislikeCount;

    private Integer commentCount;
    private Integer viewCount;

    /**
     * Denormalized share count — populated from Post.shareCount.
     * Incremented atomically by PostInteractionService.recordPostShare().
     */
    private Integer shareCount;

    /**
     * NEW — total number of users who have bookmarked this post.
     * Populated from Post.saveCount (denormalized column).
     * Only meaningful for government broadcast posts (issue posts are never
     * saveable).
     */
    private Integer saveCount;

    private Integer taggedUserCount;

    // =========================================================================
    // CURRENT USER INTERACTION STATE
    // =========================================================================

    /** true when the requesting user has an active LIKE on this post. */
    private Boolean isLikedByCurrentUser;

    /**
     * true when the requesting user has an active DISLIKE on this post.
     * Mutually exclusive with isLikedByCurrentUser — only one can be true at a
     * time.
     */
    private Boolean isDislikedByCurrentUser;

    /**
     * NEW — true when the requesting user has bookmarked this post.
     * Set via PostInteractionService.hasSavedBroadcastPost().
     * Always false for citizen issue posts (they are not saveable).
     */
    private Boolean isSavedByCurrentUser;

    private Boolean isViewedByCurrentUser;

    // =========================================================================
    // POST STATUS / CAPABILITY FLAGS
    // =========================================================================

    private String statusDisplayName;
    private Boolean canBeResolved;
    private Boolean allowsUpdates;
    private Boolean isEligibleForDisplay;

    private Boolean canLike;
    private Boolean canComment;

    /**
     * true when the post is ACTIVE and eligible to be shared.
     * Resolved issue posts return false — they cannot be shared per business rules.
     */
    private Boolean canShare;

    /**
     * NEW — true when the post can be deleted by the requesting user.
     * Only true if the user is the owner, department, or admin.
     */
    private Boolean canDelete;

    /**
     * NEW — true when the post can be bookmarked by the requesting user.
     * Only government broadcast posts (isGovernmentBroadcast == true) are saveable.
     * Issue posts always have canSave = false.
     */
    private Boolean canSave;

    // =========================================================================
    // TAGGED USERS
    // =========================================================================

    private List<String> taggedUsernames;
    private List<TaggedUserInfo> taggedUsers;
    
    /** NEW — true when a department is tagged in this post */
    private Boolean isDepartmentTagged;

    // =========================================================================
    // ADDITIONAL METADATA
    // =========================================================================

    private String timeAgo;
    private Boolean isVisibleToCurrentUser;

    // =========================================================================
    // FEED FILTERING & MODERATION METADATA
    // =========================================================================

    private Boolean contentHidden;
    private String hiddenReason;

    // =========================================================================
    // NULL-SAFE HELPER GETTERS
    // Used in Thymeleaf templates, unit tests, and any boolean expression context.
    // =========================================================================

    public boolean getHasImage() {
        return hasImage != null && hasImage;
    }

    public boolean getIsResolved() {
        return isResolved != null && isResolved;
    }

    public boolean getIsBroadcastPost() {
        return isBroadcastPost != null && isBroadcastPost;
    }

    /** True when this post was created by a government/department/admin account. */
    public boolean getIsGovernmentBroadcast() {
        return isGovernmentBroadcast != null && isGovernmentBroadcast;
    }

    /** True when this is a national-level (COUNTRY scope) broadcast. */
    public boolean getCountryWideBroadcast() {
        return countryWideBroadcast != null && countryWideBroadcast;
    }

    /** True when the requesting user has an active LIKE on this post. */
    public boolean getIsLikedByCurrentUser() {
        return isLikedByCurrentUser != null && isLikedByCurrentUser;
    }

    /**
     * True when the requesting user has an active DISLIKE on this post.
     * Will never be true at the same time as isLikedByCurrentUser.
     */
    public boolean getIsDislikedByCurrentUser() {
        return isDislikedByCurrentUser != null && isDislikedByCurrentUser;
    }

    /** True when the requesting user has this post bookmarked. */
    public boolean getIsSavedByCurrentUser() {
        return isSavedByCurrentUser != null && isSavedByCurrentUser;
    }

    public boolean getIsViewedByCurrentUser() {
        return isViewedByCurrentUser != null && isViewedByCurrentUser;
    }

    public boolean getCanLike() {
        return canLike != null && canLike;
    }

    public boolean getCanComment() {
        return canComment != null && canComment;
    }

    public boolean getCanShare() {
        return canShare != null && canShare;
    }

    /** True when this post can be bookmarked (broadcast posts only). */
    public boolean getCanSave() {
        return canSave != null && canSave;
    }

    public boolean getCanDelete() {
        return canDelete != null && canDelete;
    }

    public int getLikeCount() {
        return likeCount != null ? likeCount : 0;
    }

    public int getDislikeCount() {
        return dislikeCount != null ? dislikeCount : 0;
    }

    public int getCommentCount() {
        return commentCount != null ? commentCount : 0;
    }

    public int getViewCount() {
        return viewCount != null ? viewCount : 0;
    }

    public int getShareCount() {
        return shareCount != null ? shareCount : 0;
    }

    public int getSaveCount() {
        return saveCount != null ? saveCount : 0;
    }

    public int getTaggedUserCount() {
        return taggedUserCount != null ? taggedUserCount : 0;
    }

    public boolean getIsDepartmentTagged() {
        return isDepartmentTagged != null && isDepartmentTagged;
    }

    public String getAuthorUsername() {
        return this.username;
    }

    public String getAuthorDisplayName() {
        return this.userDisplayName != null ? this.userDisplayName : this.username;
    }

    public String getAuthorProfileImage() {
        return this.userProfileImage;
    }

    public String getActualUsername() {
        return this.username;
    }

    public AuthorDto getAuthor() {
        return AuthorDto.builder()
                .id(this.userId)
                .username(this.username)
                .profileImage(this.userProfileImage)
                .pincode(this.userPincode)
                .roleName(this.isGovernmentBroadcast != null && this.isGovernmentBroadcast ? "ROLE_DEPARTMENT" : "ROLE_USER")
                .isActive(true)
                .build();
    }

    // =========================================================================
    // NESTED CLASS — Tagged User Info
    // =========================================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaggedUserInfo {
        private Long tagId;
        private Long userId;
        private String username;
        private String displayName;
        private String profileImage;
        private String pincode;
        private Boolean isActive;
        private Date taggedAt;
        private Date deactivatedAt;
        private String deactivationReason;
        private Long taggedByUserId;
        private String taggedByUsername;
        private Boolean isDepartment;

        public boolean isActiveTag() {
            return isActive != null && isActive;
        }
    }
}
