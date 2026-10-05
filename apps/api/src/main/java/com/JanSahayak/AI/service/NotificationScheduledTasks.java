package com.JanSahayak.AI.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for notification maintenance
 * Cleans up old notifications and performs housekeeping
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduledTasks {

    private final NotificationService notificationService;

    /**
     * Clean up old notifications (older than 30 days)
     * Runs daily at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldNotifications() {
        log.info("Starting scheduled cleanup of old notifications");
        try {
            notificationService.cleanupOldNotifications();
            log.info("Completed scheduled cleanup of old notifications");
        } catch (Exception e) {
            log.error("Error during scheduled notification cleanup", e);
        }
    }

    /**
     * Log notification statistics
     * Runs every 6 hours
     */
    @Scheduled(fixedRate = 21600000) // 6 hours in milliseconds
    public void logNotificationStatistics() {
        try {
            // This would require additional repository methods to get statistics
            log.debug("Notification statistics logging scheduled task executed");
        } catch (Exception e) {
            log.error("Error logging notification statistics", e);
        }
    }
}
