package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Notification;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.JanSahayak.AI.dto.NotificationSummaryDto;
import com.JanSahayak.AI.enums.NotificationType;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Repository
public interface NotificationRepo extends JpaRepository<Notification, Long> {

    /**
     * Find all notifications for a user ordered by creation date
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    List<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * Find notifications with cursor-based pagination
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    List<Notification> findByUserAndIdLessThanOrderByCreatedAtDesc(User user, Long beforeId, Pageable pageable);

    /**
     * Find unread notifications for a user
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    List<Notification> findByUserAndIsReadFalseOrderByCreatedAtDesc(User user);

    /**
     * Find unread notifications for a user with pagination
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    List<Notification> findByUserAndIsReadFalseOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * Find unread notifications with cursor pagination
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    List<Notification> findByUserAndIsReadFalseAndIdLessThanOrderByCreatedAtDesc(User user, Long beforeId, Pageable pageable);

    /**
     * Find notifications by multiple types with pagination
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    List<Notification> findByUserAndNotificationTypeInOrderByCreatedAtDesc(
            User user,
            Collection<NotificationType> types,
            Pageable pageable);

    /**
     * Find notifications by multiple types with cursor pagination
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    List<Notification> findByUserAndNotificationTypeInAndIdLessThanOrderByCreatedAtDesc(
            User user,
            Collection<NotificationType> types,
            Long beforeId,
            Pageable pageable);

    /**
     * Aggregated summary counts for tab navigation executed in a single DB roundtrip.
     */
    @Query("SELECT new com.JanSahayak.AI.dto.NotificationSummaryDto(" +
            "COUNT(n), " +
            "SUM(CASE WHEN n.isRead = false THEN 1L ELSE 0L END), " +
            "SUM(CASE WHEN n.notificationType IN :inviteTypes THEN 1L ELSE 0L END), " +
            "SUM(CASE WHEN n.notificationType IN :interactionTypes THEN 1L ELSE 0L END)) " +
            "FROM Notification n WHERE n.user = :user")
    NotificationSummaryDto getNotificationSummaryCounts(
            @Param("user") User user,
            @Param("inviteTypes") Collection<NotificationType> inviteTypes,
            @Param("interactionTypes") Collection<NotificationType> interactionTypes);

    /**
     * Find unread notifications (limited)
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    List<Notification> findByUserAndIsReadFalse(User user);

    /**
     * Count unread notifications for a user
     */
    long countByUserAndIsReadFalse(User user);

    /**
     * Count total notifications for a user
     */
    long countByUser(User user);

    /**
     * Find notifications by type
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    List<Notification> findByUserAndNotificationTypeOrderByCreatedAtDesc(
            User user,
            com.JanSahayak.AI.enums.NotificationType type,
            Pageable pageable);

    /**
     * Find notifications by reference
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    List<Notification> findByUserAndReferenceIdAndReferenceType(
            User user,
            Long referenceId,
            String referenceType);

    /**
     * Delete all notifications for a user
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user = :user")
    int deleteByUser(@Param("user") User user);

    /**
     * Delete notifications older than a specific date
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate")
    int deleteByCreatedAtBefore(@Param("cutoffDate") Date cutoffDate);

    /**
     * Find notifications created after a specific date
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    List<Notification> findByUserAndCreatedAtAfterOrderByCreatedAtDesc(
            User user,
            Date afterDate);

    /**
     * Mark all user notifications as read
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.user = :user AND n.isRead = false")
    int markAllAsReadForUser(@Param("user") User user, @Param("readAt") Date readAt);

    /**
     * Get recent notifications for user (last N days)
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.createdAt >= :since ORDER BY n.createdAt DESC")
    List<Notification> findRecentNotifications(
            @Param("user") User user,
            @Param("since") Date since,
            Pageable pageable);

    /**
     * Check if notification exists for specific reference
     */
    boolean existsByUserAndReferenceIdAndReferenceTypeAndNotificationType(
            User user,
            Long referenceId,
            String referenceType,
            com.JanSahayak.AI.enums.NotificationType notificationType);

    /**
     * Find latest notification by type
     */
    @EntityGraph(attributePaths = {"triggeredBy"})
    Notification findFirstByUserAndNotificationTypeOrderByCreatedAtDesc(
            User user,
            com.JanSahayak.AI.enums.NotificationType type);

    /**
     * Count notifications by type
     */
    long countByUserAndNotificationType(
            User user,
            com.JanSahayak.AI.enums.NotificationType type);

    /**
     * Delete specific notification by ID and user
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.user = :user")
    int deleteByIdAndUser(@Param("id") Long id, @Param("user") User user);

    /**
     * Delete notifications by reference ID and types
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.referenceId = :referenceId AND n.referenceType IN :referenceTypes")
    int deleteByReferenceIdAndTypes(@Param("referenceId") Long referenceId, @Param("referenceTypes") List<String> referenceTypes);
}
