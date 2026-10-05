package com.Govlyx.AI.dto;

import com.Govlyx.AI.enums.NotificationType;
import com.Govlyx.AI.model.Notification;
import com.Govlyx.AI.util.SpringContextHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private Long id;
    private NotificationType notificationType;
    private String title;
    private String message;
    private Long referenceId;
    private String referenceType;
    private Boolean isRead;
    private Date readAt;
    private Date createdAt;
    private String actionUrl;

    // Triggered by user information
    private Long triggeredByUserId;
    private String triggeredByUsername;
    private String triggeredByDisplayName;
    private String triggeredByProfileImage;

    // Metadata
    private String timeAgo;
    private String category;
    private String typeDescription;

    /**
     * Populated for COMMUNITY_JOIN_REQUEST notifications.
     * Reflects the live status of the referenced join request so that the
     * manager's notification UI can show "Already Reviewed" when another manager
     * acted first (optimistic concurrency guard).
     * Values: "APPROVED", "REJECTED", "CANCELLED", or null (still PENDING).
     */
    private String joinRequestStatus;

    /**
     * Convert Notification entity to DTO
     */
    public static NotificationDto fromNotification(Notification notification) {
        if (notification == null) {
            return null;
        }

        NotificationDtoBuilder builder = NotificationDto.builder()
                .id(notification.getId())
                .notificationType(notification.getNotificationType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceId(notification.getReferenceId())
                .referenceType(notification.getReferenceType())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .actionUrl(notification.getActionUrl())
                .timeAgo(calculateTimeAgo(notification.getCreatedAt()));

        // Add notification type metadata
        if (notification.getNotificationType() != null) {
            builder.category(notification.getNotificationType().getCategory())
                    .typeDescription(notification.getNotificationType().getDescription());
        }

        // Add triggered by user information
        if (notification.getTriggeredBy() != null) {
            builder.triggeredByUserId(notification.getTriggeredBy().getId())
                    .triggeredByUsername(notification.getTriggeredBy().getActualUsername())
                    .triggeredByDisplayName(notification.getTriggeredBy().getDisplayName())
                    .triggeredByProfileImage(notification.getTriggeredBy().getProfileImage());
        }

        // Populate live join-request status for concurrent manager UX
        if (notification.getNotificationType() == NotificationType.COMMUNITY_JOIN_REQUEST
                && notification.getReferenceId() != null) {
            try {
                com.Govlyx.AI.repository.CommunityJoinRequestRepo joinRequestRepo =
                        SpringContextHolder.getBean(com.Govlyx.AI.repository.CommunityJoinRequestRepo.class);
                joinRequestRepo.findById(notification.getReferenceId()).ifPresent(jr -> {
                    if (jr.getStatus() != com.Govlyx.AI.model.CommunityJoinRequest.RequestStatus.PENDING) {
                        builder.joinRequestStatus(jr.getStatus().name());
                    }
                });
            } catch (Exception e) {
                // Non-critical — silently skip if context is not yet available
            }
        }

        return builder.build();
    }

    /**
     * Calculate time ago string from date
     */
    private static String calculateTimeAgo(Date date) {
        if (date == null) {
            return "Unknown";
        }

        long diff = System.currentTimeMillis() - date.getTime();
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;

        if (seconds < 60) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        } else if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        } else if (days < 7) {
            return days + (days == 1 ? " day ago" : " days ago");
        } else if (weeks < 4) {
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        } else if (months < 12) {
            return months + (months == 1 ? " month ago" : " months ago");
        } else {
            return years + (years == 1 ? " year ago" : " years ago");
        }
    }

    /**
     * Check if notification is post-related
     */
    public boolean isPostRelated() {
        return notificationType != null && notificationType.isPostRelated();
    }

    /**
     * Check if notification is comment-related
     */
    public boolean isCommentRelated() {
        return notificationType != null && notificationType.isCommentRelated();
    }

    /**
     * Check if notification is broadcast-related
     */
    public boolean isBroadcastRelated() {
        return notificationType != null && notificationType.isBroadcastRelated();
    }

    /**
     * Check if notification is system-related
     */
    public boolean isSystemRelated() {
        return notificationType != null && notificationType.isSystemRelated();
    }

    /**
     * Check if notification is community-related
     */
    public boolean isCommunityRelated() {
        return notificationType != null && notificationType.isCommunityRelated();
    }

    /**
     * Check if notification is department-related
     */
    public boolean isDepartmentRelated() {
        return notificationType != null && notificationType.isDepartmentRelated();
    }

    /**
     * Check if notification is unread
     */
    public boolean isUnread() {
        return isRead != null && !isRead;
    }

    /**
     * Get display name for triggered by user
     */
    public String getTriggeredByDisplayText() {
        if (triggeredByDisplayName != null && !triggeredByDisplayName.trim().isEmpty()) {
            return triggeredByDisplayName;
        }
        if (triggeredByUsername != null) {
            return triggeredByUsername;
        }
        return "Someone";
    }
}
