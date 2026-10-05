package com.govlyx.AI.enums;

public enum NotificationType {
    // Post-related notifications
    POST_LIKE("Someone liked your post", "post"),
    POST_COMMENT("Someone commented on your post", "post"),
    POST_RESOLVED("Your post was marked as resolved", "post"),
    POST_REOPENED("A resolved post was reopened", "post"),
    POST_TAGGED("You were tagged in a post", "post"),

    // Comment-related notifications
    COMMENT_REPLY("Someone replied to your comment", "comment"),
//    COMMENT_LIKE("Someone liked your comment", "comment"),

    // Broadcast notifications
    BROADCAST_NEW("New broadcast in your area", "broadcast"),
    BROADCAST_COUNTRY("New country-wide broadcast", "broadcast"),
    BROADCAST_STATE("New state-level broadcast", "broadcast"),
    BROADCAST_DISTRICT("New district-level broadcast", "broadcast"),

    // System notifications
    SYSTEM_ANNOUNCEMENT("System announcement", "system"),
    ACCOUNT_UPDATE("Account update", "system"),

    // Community notifications
    COMMUNITY_INVITE("You've been invited to a community", "community"),
    COMMUNITY_INVITE_ACCEPT("Your invite was accepted", "community"),
    COMMUNITY_INVITE_DECLINE("Your invite was declined", "community"),
    COMMUNITY_NEW_POST("New post in your community", "community"),
    COMMUNITY_POST_APPROVED("Your community post was approved", "community"),
    COMMUNITY_POST_REJECTED("Your community post was rejected", "community"),
    COMMUNITY_JOIN_REQUEST("Someone requested to join a community", "community"),
    COMMUNITY_JOIN_REJECT("Your join request was rejected", "community"),
    COMMUNITY_DELETED("Your community has been scheduled for deletion", "community"),
    COMMUNITY_ROLE_CHANGED("Your role in a community has been updated", "community"),

    // Department notifications
    DEPARTMENT_MESSAGE("Message from department", "department"),
    POST_ATTENTION_REQUIRED("A post requires attention", "department");

    private final String description;
    private final String category;

    NotificationType(String description, String category) {
        this.description = description;
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public boolean isPostRelated() {
        return "post".equals(category);
    }

    public boolean isCommentRelated() {
        return "comment".equals(category);
    }

    public boolean isBroadcastRelated() {
        return "broadcast".equals(category);
    }

    public boolean isSystemRelated() {
        return "system".equals(category);
    }

    public boolean isDepartmentRelated() {
        return "department".equals(category);
    }

    public boolean isCommunityRelated() {
        return "community".equals(category);
    }
}