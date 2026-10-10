package com.JanSahayak.AI.enums;

import lombok.Getter;

@Getter
public enum PostStatus {

    // ===== Common Statuses (Used by both Post and SocialPost) =====
    ACTIVE("Active", true, true, true),


    RESOLVED("Resolved", false, true, false),

    // ===== Social Post Specific Statuses =====
    PENDING_APPROVAL("Pending Approval", true, false, false),
    REJECTED("Rejected", false, false, false),
    DELETED("Deleted", false, false, false),
    FLAGGED("Flagged", false, false, false),
    ARCHIVED("Archived", false, true, false),
    HIDDEN("Hidden", false, false, false),
    TAKEN_DOWN("Taken Down", false, false, false);

    private final String displayName;
    private final boolean allowsUpdates;
    private final boolean isVisible;
    private final boolean isInteractable;

    PostStatus(String displayName, boolean allowsUpdates, boolean isVisible, boolean isInteractable) {
        this.displayName = displayName;
        this.allowsUpdates = allowsUpdates;
        this.isVisible = isVisible;
        this.isInteractable = isInteractable;
    }

    // ===== Boolean Accessor Methods =====

    public boolean isAllowsUpdates() {
        return allowsUpdates;
    }

    public boolean allowsUpdates() {
        return allowsUpdates;
    }

    public boolean isInteractable() {
        return isInteractable;
    }

    public boolean allowsInteractions() {
        return isInteractable;
    }

    // ===== Status Type Checks =====

    /**
     * Check if this is an issue post status (RESOLVED)
     */
    public boolean isIssuePostStatus() {
        return this == RESOLVED;
    }

    /**
     * Check if this is a social post status (DELETED, FLAGGED, ARCHIVED, HIDDEN)
     */
    public boolean isSocialPostStatus() {
        return this == DELETED || this == FLAGGED || this == ARCHIVED || this == HIDDEN || this == TAKEN_DOWN;
    }

    /**
     * Check if this status is active and visible to users
     */
    public boolean isActiveStatus() {
        return this == ACTIVE;
    }

    /**
     * Check if this status hides content from users
     */
    public boolean isHiddenStatus() {
        return this == DELETED || this == FLAGGED || this == HIDDEN || this == TAKEN_DOWN;
    }

    // ===== Transition Logic for Issue Posts =====

    /**
     * Check if this status can transition to another status
     *
     * @param targetStatus the target status to transition to
     * @return true if transition is allowed, false otherwise
     */
    public boolean canTransitionTo(PostStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }

        // Same status transitions are always allowed (no-op)
        if (this == targetStatus) {
            return true;
        }

        switch (this) {
            case ACTIVE:
                // ACTIVE can transition to any status
                return true;

            case RESOLVED:
                // RESOLVED posts can be transitioned back to ACTIVE
                return targetStatus == ACTIVE;

            case DELETED:
            case TAKEN_DOWN:
                // DELETED or TAKEN_DOWN posts cannot be restored (permanent)
                return false;

            case FLAGGED:
                // FLAGGED posts can be made ACTIVE (after review) or DELETED
                return targetStatus == ACTIVE || targetStatus == DELETED;

            case ARCHIVED:
                // ARCHIVED posts can be made ACTIVE again
                return targetStatus == ACTIVE;

            case HIDDEN:
                // HIDDEN posts can be made ACTIVE or DELETED
                return targetStatus == ACTIVE || targetStatus == DELETED;

            default:
                return false;
        }
    }

    /**
     * Get all possible transition targets from current status
     *
     * @return array of possible target statuses
     */
    public PostStatus[] getPossibleTransitions() {
        switch (this) {
            case ACTIVE:
                return new PostStatus[]{RESOLVED, DELETED, FLAGGED, ARCHIVED, HIDDEN, TAKEN_DOWN};

            case RESOLVED:
                return new PostStatus[]{ACTIVE};

            case DELETED:
            case TAKEN_DOWN:
                return new PostStatus[0]; // No transitions from DELETED or TAKEN_DOWN

            case FLAGGED:
                return new PostStatus[]{ACTIVE, DELETED};

            case ARCHIVED:
                return new PostStatus[]{ACTIVE};

            case HIDDEN:
                return new PostStatus[]{ACTIVE, DELETED};

            default:
                return new PostStatus[0];
        }
    }

    /**
     * Check if this status allows any transitions
     *
     * @return true if transitions are possible, false otherwise
     */
    public boolean allowsTransitions() {
        return getPossibleTransitions().length > 0;
    }

    // ===== Social Post Specific Methods =====

    /**
     * Check if social post with this status should appear in feeds
     */
    public boolean isVisibleInFeed() {
        return this == ACTIVE;
    }

    /**
     * Check if social post with this status can be liked
     */
    public boolean allowsLikes() {
        return this == ACTIVE;
    }

    /**
     * Check if social post with this status can be commented on
     */
    public boolean allowsComments() {
        return this == ACTIVE;
    }

    /**
     * Check if social post with this status can be shared
     */
    public boolean allowsShares() {
        return this == ACTIVE;
    }

    /**
     * Check if social post with this status can be saved/bookmarked
     */
    public boolean allowsSaves() {
        return this == ACTIVE || this == ARCHIVED;
    }

    /**
     * Check if this status requires moderation review
     */
    public boolean requiresModeration() {
        return this == FLAGGED;
    }

    /**
     * Check if content is permanently removed
     */
    public boolean isPermanentlyRemoved() {
        return this == DELETED || this == TAKEN_DOWN;
    }

    /**
     * Check if content is temporarily hidden (can be restored)
     */
    public boolean isTemporarilyHidden() {
        return this == HIDDEN || this == FLAGGED;
    }

    // ===== Issue Post Specific Methods =====

    /**
     * Check if issue post is resolved
     */
    public boolean isResolved() {
        return this == RESOLVED;
    }

    /**
     * Check if issue post is still open/active
     */
    public boolean isOpenIssue() {
        return this == ACTIVE;
    }

    // ===== Display Helpers =====

    /**
     * Get user-friendly status description for social posts
     */
    public String getSocialPostDescription() {
        switch (this) {
            case ACTIVE:
                return "Published";
            case DELETED:
                return "Deleted by user";
            case TAKEN_DOWN:
                return "Removed for legal/policy violation";
            case FLAGGED:
                return "Under review";
            case ARCHIVED:
                return "Archived";
            case HIDDEN:
                return "Hidden";
            default:
                return displayName;
        }
    }

    /**
     * Get user-friendly status description for issue posts
     */
    public String getIssuePostDescription() {
        switch (this) {
            case ACTIVE:
                return "Open Issue";
            case RESOLVED:
                return "Issue Resolved";
            default:
                return displayName;
        }
    }

    /**
     * Get status color indicator for UI
     */
    public String getStatusColor() {
        switch (this) {
            case ACTIVE:
                return "green";
            case RESOLVED:
                return "blue";
            case DELETED:
            case TAKEN_DOWN:
                return "red";
            case FLAGGED:
                return "orange";
            case ARCHIVED:
                return "gray";
            case HIDDEN:
                return "yellow";
            default:
                return "black";
        }
    }

    /**
     * Get status icon/emoji for UI
     */
    public String getStatusIcon() {
        switch (this) {
            case ACTIVE:
                return "✓";
            case RESOLVED:
                return "✓✓";
            case DELETED:
                return "✗";
            case TAKEN_DOWN:
                return "⚖️";
            case FLAGGED:
                return "⚠";
            case ARCHIVED:
                return "📦";
            case HIDDEN:
                return "👁";
            default:
                return "•";
        }
    }
}
