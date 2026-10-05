package com.govlyx.AI.controller;

import com.govlyx.AI.dto.NotificationDto;
import com.govlyx.AI.dto.NotificationSummaryDto;
import com.govlyx.AI.dto.PaginatedResponse;
import com.govlyx.AI.model.User;
import com.govlyx.AI.service.NotificationService;
import com.govlyx.AI.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    /**
     * Get user's notifications with pagination and category tab filtering
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<NotificationDto>> getNotifications(
            Authentication authentication,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "all") String tab) {

        try {
            User user = userService.getUserFromAuthentication(authentication);
            PaginatedResponse<NotificationDto> notifications =
                    notificationService.getUserNotifications(user, beforeId, limit, tab);

            return ResponseEntity.ok(notifications);

        } catch (Exception e) {
            log.error("Failed to get notifications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get notification summary counts for tab navigation (all, unread, invites, interactions)
     */
    @GetMapping("/counts")
    public ResponseEntity<NotificationSummaryDto> getNotificationCounts(Authentication authentication) {
        try {
            User user = userService.getUserFromAuthentication(authentication);
            NotificationSummaryDto counts = notificationService.getNotificationSummaryCounts(user);

            return ResponseEntity.ok(counts);

        } catch (Exception e) {
            log.error("Failed to get notification counts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get unread notifications
     */
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDto>> getUnreadNotifications(Authentication authentication) {
        try {
            User user = userService.getUserFromAuthentication(authentication);
            List<NotificationDto> notifications = notificationService.getUnreadNotifications(user);

            return ResponseEntity.ok(notifications);

        } catch (Exception e) {
            log.error("Failed to get unread notifications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get unread notification count
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication authentication) {
        try {
            User user = userService.getUserFromAuthentication(authentication);
            long count = notificationService.getUnreadNotificationCount(user);

            Map<String, Object> response = new HashMap<>();
            response.put("count", count);
            response.put("hasUnread", count > 0);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get unread notification count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Mark notification as read
     */
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication) {

        try {
            User user = userService.getUserFromAuthentication(authentication);
            notificationService.markNotificationAsRead(notificationId, user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Notification marked as read");
            response.put("status", "success");

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            log.warn("Unauthorized attempt to mark notification as read: {}", notificationId);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Unauthorized");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);

        } catch (Exception e) {
            log.error("Failed to mark notification as read: {}", notificationId, e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to mark notification as read");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Mark all notifications as read
     */
    @PutMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(Authentication authentication) {
        try {
            User user = userService.getUserFromAuthentication(authentication);
            notificationService.markAllNotificationsAsRead(user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "All notifications marked as read");
            response.put("status", "success");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to mark all notifications as read", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to mark all notifications as read");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Delete a notification
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Map<String, String>> deleteNotification(
            @PathVariable Long notificationId,
            Authentication authentication) {

        try {
            User user = userService.getUserFromAuthentication(authentication);
            notificationService.deleteNotification(notificationId, user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Notification deleted successfully");
            response.put("status", "success");

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            log.warn("Unauthorized attempt to delete notification: {}", notificationId);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Unauthorized");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);

        } catch (Exception e) {
            log.error("Failed to delete notification: {}", notificationId, e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to delete notification");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Delete all notifications
     */
    @DeleteMapping("/all")
    public ResponseEntity<Map<String, String>> deleteAllNotifications(Authentication authentication) {
        try {
            User user = userService.getUserFromAuthentication(authentication);
            notificationService.deleteAllNotifications(user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "All notifications deleted successfully");
            response.put("status", "success");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to delete all notifications", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to delete all notifications");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Health check endpoint for notifications
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck(Authentication authentication) {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "healthy");
            health.put("timestamp", System.currentTimeMillis());

            if (authentication != null && authentication.isAuthenticated()) {
                User user = userService.getUserFromAuthentication(authentication);
                long unreadCount = notificationService.getUnreadNotificationCount(user);
                health.put("unreadNotifications", unreadCount);
            }

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            log.error("Health check failed", e);
            Map<String, Object> health = new HashMap<>();
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }
}
