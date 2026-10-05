package com.govlyx.AI.service;

import com.govlyx.AI.config.Constant;

import com.govlyx.AI.dto.NotificationDto;
import com.govlyx.AI.dto.NotificationSummaryDto;
import com.govlyx.AI.dto.PaginatedResponse;
import com.govlyx.AI.enums.NotificationType;
import com.govlyx.AI.exception.ServiceException;
import com.govlyx.AI.exception.ValidationException;
import com.govlyx.AI.model.*;
import java.util.Set;
import com.govlyx.AI.payload.PaginationUtils;
import com.govlyx.AI.repository.CommentRepo;
import com.govlyx.AI.repository.CommunityMemberRepo;
import com.govlyx.AI.repository.NotificationRepo;
import com.govlyx.AI.repository.PostRepo;
import com.govlyx.AI.repository.SocialPostRepo;
import com.govlyx.AI.repository.UserRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.util.HtmlUtils;

/**
 * Comprehensive Notification Service
 * Handles all types of notifications: Post likes, comments, tags, broadcasts, etc.
 * Supports real-time WebSocket delivery and persistent storage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepo notificationRepository;
    private final UserRepo userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PostRepo postRepository;
    private final WebPushService webPushService;
    private final ObjectMapper objectMapper;
    private final SocialPostRepo socialPostRepo;
    private final CommentRepo commentRepo;
    private final CommunityMemberRepo communityMemberRepository;
 
    public static final Set<NotificationType> INVITE_TYPES = Set.of(
            NotificationType.COMMUNITY_INVITE,
            NotificationType.COMMUNITY_INVITE_ACCEPT,
            NotificationType.COMMUNITY_INVITE_DECLINE,
            NotificationType.COMMUNITY_JOIN_REQUEST,
            NotificationType.COMMUNITY_JOIN_REJECT,
            NotificationType.COMMUNITY_ROLE_CHANGED,
            NotificationType.COMMUNITY_POST_APPROVED,
            NotificationType.COMMUNITY_POST_REJECTED
    );

    public static final Set<NotificationType> INTERACTION_TYPES = Set.of(
            NotificationType.POST_LIKE,
            NotificationType.POST_COMMENT,
            NotificationType.COMMENT_REPLY,
            NotificationType.POST_TAGGED,
            NotificationType.POST_RESOLVED,
            NotificationType.POST_REOPENED
    );

    /**
     * Send notification when a community is scheduled for deletion
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyCommunityDeleted(com.govlyx.AI.model.Community community, User owner, java.util.List<Long> memberIds) {
        try {
            if (community == null || owner == null || memberIds == null || memberIds.isEmpty()) {
                return;
            }

            String title = "Community Scheduled for Deletion";
            String message = String.format("âš ï¸ The community '%s' has been scheduled for deletion by the owner and will be permanently removed in 2 days.", community.getName());
            String actionUrl = "/communities/" + community.getSlug();

            for (Long memberId : memberIds) {
                // Skip owner
                if (memberId.equals(owner.getId())) continue;

                User targetUser = userRepository.findById(memberId).orElse(null);
                if (targetUser == null) continue;

                Notification notification = createNotification(
                        targetUser,
                        com.govlyx.AI.enums.NotificationType.COMMUNITY_DELETED,
                        title,
                        message,
                        community.getId(),
                        "COMMUNITY",
                        actionUrl,
                        owner
                );

                sendRealtimeNotification(notification);
            }
            log.info("Sent community deletion notifications for community {} to {} members", community.getId(), memberIds.size());
        } catch (Exception e) {
            log.error("Failed to send community deletion notification", e);
        }
    }

    // ===== POST INTERACTION NOTIFICATIONS =====

    /**
     * Send notification when someone likes a post
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyPostLiked(Post post, User likedBy) {
        try {
            // ZK Blind Shield Fix (Bug 5): accept actor-token posts where user FK is null
            if (post == null || (post.getUser() == null && post.getActorToken() == null) || likedBy == null) {
                log.warn("Invalid parameters for post like notification — skipping");
                return;
            }

            User actionUser = userRepository.findById(likedBy.getId()).orElse(likedBy);

            // === Citizen decoupled path: route by actor_token ===
            if (post.getUser() == null) {
                String title = "New Like on Your Post";
                String message = String.format("%s liked your post", actionUser.getActualUsername());
                String actionUrl = "/post/" + post.getId();
                Notification notification = createNotificationForActor(
                        post.getActorToken(), NotificationType.POST_LIKE,
                        title, message, post.getId(), "POST", actionUrl, actionUser);
                sendRealtimeNotificationForActor(notification, post.getActorToken());
                log.debug("Post like notification (actor) sent: postId={}, likedBy={}",
                        post.getId(), likedBy.getActualUsername());
                return;
            }

            // === Legacy / authority relational path ===
            User targetUser = userRepository.findById(post.getUser().getId()).orElse(post.getUser());
            User actionUser2 = actionUser;

            // Don't notify if user liked their own post
            if (targetUser.getId().equals(actionUser2.getId())) return;

            // Aggregate like notification
            List<Notification> existingList = notificationRepository.findByUserAndReferenceIdAndReferenceType(targetUser, post.getId(), "POST");
            Notification existingLike = existingList.stream()
                    .filter(n -> n.getNotificationType() == NotificationType.POST_LIKE)
                    .max(java.util.Comparator.comparing(Notification::getCreatedAt))
                    .orElse(null);

            if (existingLike != null) {
                int count = post.getLikeCount();
                existingLike.setMessage(count > 1
                        ? String.format("%s and %d others liked your post", actionUser2.getActualUsername(), count - 1)
                        : String.format("%s liked your post", actionUser2.getActualUsername()));
                existingLike.setTriggeredBy(actionUser2);
                existingLike.markAsUnread();
                existingLike.setCreatedAt(new Date());
                notificationRepository.save(existingLike);
                sendRealtimeNotification(existingLike);
                log.debug("Post like notification aggregated: postId={}, likedBy={}", post.getId(), likedBy.getActualUsername());
                return;
            }

            Notification notification = createNotification(
                    targetUser, NotificationType.POST_LIKE,
                    "New Like on Your Post",
                    String.format("%s liked your post", actionUser2.getActualUsername()),
                    post.getId(), "POST", "/post/" + post.getId(), actionUser2);
            sendRealtimeNotification(notification);
            log.debug("Post like notification sent: postId={}, likedBy={}", post.getId(), likedBy.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send post like notification", e);
        }
    }

    /**
     * Send notification when someone likes a social post
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifySocialPostLiked(Long socialPostId, Long likedById) {
        try {
            SocialPost socialPost = socialPostRepo.findById(socialPostId).orElse(null);
            User likedBy = userRepository.findById(likedById).orElse(null);
            // ZK Blind Shield Fix (Bug 5): accept actor-token posts
            if (socialPost == null || (socialPost.getUser() == null && socialPost.getActorToken() == null) || likedBy == null) {
                log.warn("Invalid parameters for social post like notification — skipping");
                return;
            }

            User actionUser = userRepository.findById(likedBy.getId()).orElse(likedBy);

            // === Citizen decoupled path ===
            if (socialPost.getUser() == null) {
                String actionUrl = "/post/" + socialPost.getId();
                Notification notification = createNotificationForActor(
                        socialPost.getActorToken(), NotificationType.POST_LIKE,
                        "New Like on Your Post",
                        String.format("%s liked your post", actionUser.getActualUsername()),
                        socialPost.getId(), "SOCIAL_POST", actionUrl, actionUser);
                sendRealtimeNotificationForActor(notification, socialPost.getActorToken());
                return;
            }

            User targetUser = userRepository.findById(socialPost.getUser().getId()).orElse(socialPost.getUser());

            // Don't notify if user liked their own post
            if (targetUser.getId().equals(actionUser.getId())) {
                return;
            }

            String actionUrl = "/post/" + socialPost.getId();
            if (socialPost.getCommunity() != null) {
                actionUrl = "/communities/" + socialPost.getCommunity().getSlug() + "?postId=" + socialPost.getId();
            }

            // Aggregate like notification
            List<Notification> existingList = notificationRepository.findByUserAndReferenceIdAndReferenceType(targetUser, socialPost.getId(), "SOCIAL_POST");
            Notification existingLike = existingList.stream()
                    .filter(n -> n.getNotificationType() == NotificationType.POST_LIKE)
                    .max(java.util.Comparator.comparing(Notification::getCreatedAt))
                    .orElse(null);

            if (existingLike != null) {
                int count = socialPost.getLikeCount();
                if (count > 1) {
                    existingLike.setMessage(String.format("%s and %d others liked your post", actionUser.getActualUsername(), count - 1));
                } else {
                    existingLike.setMessage(String.format("%s liked your post", actionUser.getActualUsername()));
                }
                existingLike.setTriggeredBy(actionUser);
                existingLike.setActionUrl(actionUrl);
                existingLike.markAsUnread();
                existingLike.setCreatedAt(new Date());
                notificationRepository.save(existingLike);
                sendRealtimeNotification(existingLike);
                log.debug("Social post like notification aggregated: postId={}, likedBy={}", socialPost.getId(), likedBy.getActualUsername());
                return;
            }

            String title = "New Like on Your Post";
            String message = String.format("%s liked your post", actionUser.getActualUsername());

            Notification notification = createNotification(
                    targetUser,
                    NotificationType.POST_LIKE,
                    title,
                    message,
                    socialPost.getId(),
                    "SOCIAL_POST",
                    actionUrl,
                    actionUser
            );

            sendRealtimeNotification(notification);

            log.debug("Social post like notification sent: postId={}, likedBy={}",
                    socialPost.getId(), likedBy.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send social post like notification", e);
        }
    }

    /**
     * Send notification when someone comments on a post
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyPostCommented(Post post, Comment comment, User commentedBy) {
        try {
            // ZK Blind Shield Fix (Bug 5): accept actor-token posts
            if (post == null || (post.getUser() == null && post.getActorToken() == null) || commentedBy == null) {
                log.warn("Invalid parameters for post comment notification — skipping");
                return;
            }

            User actionUser = userRepository.findById(commentedBy.getId()).orElse(commentedBy);

            // === Citizen decoupled path ===
            if (post.getUser() == null) {
                String commentText = comment != null ? truncateText(comment.getText(), 50) : "";
                Notification notification = createNotificationForActor(
                        post.getActorToken(), NotificationType.POST_COMMENT,
                        "New Comment on Your Post",
                        String.format("%s commented: \"%s\"", actionUser.getActualUsername(), commentText),
                        post.getId(), "POST",
                        "/post/" + post.getId() + (comment != null ? "#comment-" + comment.getId() : ""),
                        actionUser);
                sendRealtimeNotificationForActor(notification, post.getActorToken());
                return;
            }

            User targetUser = userRepository.findById(post.getUser().getId()).orElse(post.getUser());

            // Don't notify if user commented on their own post
            if (targetUser.getId().equals(actionUser.getId())) {
                return;
            }

            String actionUrl = "/post/" + post.getId() + "#comment-" + comment.getId();

            // Aggregate comment notification
            List<Notification> existingList = notificationRepository.findByUserAndReferenceIdAndReferenceType(targetUser, post.getId(), "POST");
            Notification existingComment = existingList.stream()
                    .filter(n -> n.getNotificationType() == NotificationType.POST_COMMENT)
                    .max(java.util.Comparator.comparing(Notification::getCreatedAt))
                    .orElse(null);

            if (existingComment != null) {
                int count = post.getCommentCount();
                if (count > 1) {
                    existingComment.setMessage(String.format("%s and %d others commented on your post", actionUser.getActualUsername(), count - 1));
                } else {
                    existingComment.setMessage(String.format("%s commented: \"%s\"", actionUser.getActualUsername(), truncateText(comment.getText(), 50)));
                }
                existingComment.setTriggeredBy(actionUser);
                existingComment.setActionUrl(actionUrl);
                existingComment.markAsUnread();
                existingComment.setCreatedAt(new Date());
                notificationRepository.save(existingComment);
                sendRealtimeNotification(existingComment);
                log.debug("Post comment notification aggregated: postId={}, commentedBy={}", post.getId(), commentedBy.getActualUsername());
                return;
            }

            String title = "New Comment on Your Post";
            String message = String.format("%s commented: \"%s\"",
                    actionUser.getActualUsername(),
                    truncateText(comment.getText(), 50));

            Notification notification = createNotification(
                    targetUser,
                    NotificationType.POST_COMMENT,
                    title,
                    message,
                    post.getId(),
                    "POST",
                    actionUrl,
                    actionUser
            );

            sendRealtimeNotification(notification);

            log.debug("Post comment notification sent: postId={}, commentedBy={}",
                    post.getId(), commentedBy.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send post comment notification", e);
        }
    }

    /**
     * Send notification when someone comments on a social post
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifySocialPostCommented(Long socialPostId, Long commentId, Long commentedById) {
        try {
            SocialPost socialPost = socialPostRepo.findById(socialPostId).orElse(null);
            Comment comment = (commentId != null) ? commentRepo.findById(commentId).orElse(null) : null;
            User commentedBy = userRepository.findById(commentedById).orElse(null);
            // ZK Blind Shield Fix (Bug 5): accept actor-token posts
            if (socialPost == null || (socialPost.getUser() == null && socialPost.getActorToken() == null) || commentedBy == null) {
                log.warn("Invalid parameters for social post comment notification — skipping");
                return;
            }

            User actionUser = userRepository.findById(commentedBy.getId()).orElse(commentedBy);

            // === Citizen decoupled path ===
            if (socialPost.getUser() == null) {
                String commentText = comment != null ? truncateText(comment.getText(), 50) : "";
                String actionUrl = "/post/" + socialPost.getId() + (comment != null ? "#comment-" + comment.getId() : "");
                Notification notification = createNotificationForActor(
                        socialPost.getActorToken(), NotificationType.POST_COMMENT,
                        "New Comment on Your Post",
                        String.format("%s commented: \"%s\"", actionUser.getActualUsername(), commentText),
                        socialPost.getId(), "SOCIAL_POST", actionUrl, actionUser);
                sendRealtimeNotificationForActor(notification, socialPost.getActorToken());
                return;
            }

            User targetUser = userRepository.findById(socialPost.getUser().getId()).orElse(socialPost.getUser());

            // Don't notify if user commented on their own post
            if (targetUser.getId().equals(actionUser.getId())) {
                return;
            }

            String actionUrl = "/post/" + socialPost.getId() + (comment != null ? "#comment-" + comment.getId() : "");
            if (socialPost.getCommunity() != null) {
                actionUrl = "/communities/" + socialPost.getCommunity().getSlug() + "?postId=" + socialPost.getId() + (comment != null ? "#comment-" + comment.getId() : "");
            }

            // Aggregate comment notification
            List<Notification> existingList = notificationRepository.findByUserAndReferenceIdAndReferenceType(targetUser, socialPost.getId(), "SOCIAL_POST");
            Notification existingComment = existingList.stream()
                    .filter(n -> n.getNotificationType() == NotificationType.POST_COMMENT)
                    .max(java.util.Comparator.comparing(Notification::getCreatedAt))
                    .orElse(null);

            if (existingComment != null) {
                int count = socialPost.getCommentCount();
                if (count > 1) {
                    existingComment.setMessage(String.format("%s and %d others commented on your post", actionUser.getActualUsername(), count - 1));
                } else {
                    existingComment.setMessage(String.format("%s commented: \"%s\"", actionUser.getActualUsername(),
                            comment != null ? truncateText(comment.getText(), 50) : ""));
                }
                existingComment.setTriggeredBy(actionUser);
                existingComment.setActionUrl(actionUrl);
                existingComment.markAsUnread();
                existingComment.setCreatedAt(new Date());
                notificationRepository.save(existingComment);
                sendRealtimeNotification(existingComment);
                log.debug("Social post comment notification aggregated: postId={}, commentedBy={}", socialPost.getId(), commentedBy.getActualUsername());
                return;
            }

            String title = "New Comment on Your Post";
            String message = String.format("%s commented: \"%s\"",
                    actionUser.getActualUsername(),
                    comment != null ? truncateText(comment.getText(), 50) : "");

            Notification notification = createNotification(
                    targetUser,
                    NotificationType.POST_COMMENT,
                    title,
                    message,
                    socialPost.getId(),
                    "SOCIAL_POST",
                    actionUrl,
                    actionUser
            );

            sendRealtimeNotification(notification);

            log.debug("Social post comment notification sent: postId={}, commentedBy={}",
                    socialPost.getId(), commentedBy.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send social post comment notification", e);
        }
    }

    /**
     * Send notification when someone replies to a comment
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyCommentReplied(Comment originalComment, Comment reply, User repliedBy) {
        try {
            if (originalComment == null || (originalComment.getUser() == null && originalComment.getActorToken() == null) || repliedBy == null) {
                log.warn("Invalid parameters for comment reply notification");
                return;
            }

            User actionUser = userRepository.findById(repliedBy.getId()).orElse(repliedBy);

            String title = "New Reply to Your Comment";
            String message = String.format("%s replied: \"%s\"",
                    actionUser.getActualUsername(),
                    truncateText(reply.getText(), 50));

            // Determine action URL based on whether it's a post or social post comment
            String actionUrl;
            Long referenceId;
            String referenceType;
            if (originalComment.getPost() != null) {
                actionUrl = "/post/" + originalComment.getPost().getId() + "#comment-" + reply.getId();
                referenceId = originalComment.getPost().getId();
                referenceType = "POST";
            } else if (originalComment.getSocialPost() != null) {
                actionUrl = "/post/" + originalComment.getSocialPost().getId() + "#comment-" + reply.getId();
                referenceId = originalComment.getSocialPost().getId();
                referenceType = "SOCIAL_POST";
            } else {
                log.warn("Comment has no associated post or social post");
                return;
            }

            // === Citizen decoupled path ===
            if (originalComment.getUser() == null) {
                Notification notification = createNotificationForActor(
                        originalComment.getActorToken(), NotificationType.COMMENT_REPLY,
                        title, message, referenceId, referenceType, actionUrl, actionUser);
                sendRealtimeNotificationForActor(notification, originalComment.getActorToken());
                log.debug("Comment reply notification sent to actor: commentId={}, actorToken={}",
                        originalComment.getId(), originalComment.getActorToken());
                return;
            }

            User targetUser = userRepository.findById(originalComment.getUser().getId()).orElse(originalComment.getUser());

            // Don't notify if user replied to their own comment
            if (targetUser.getId().equals(actionUser.getId())) {
                return;
            }

            Notification notification = createNotification(
                    targetUser,
                    NotificationType.COMMENT_REPLY,
                    title,
                    message,
                    referenceId,
                    referenceType,
                    actionUrl,
                    actionUser
            );

            sendRealtimeNotification(notification);

            log.debug("Comment reply notification sent: commentId={}, repliedBy={}",
                    originalComment.getId(), repliedBy.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send comment reply notification", e);
        }
    }

    // ===== TAGGING NOTIFICATIONS =====

    /**
     * Send notification when user is tagged in a post
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyUserTagged(Post post, User taggedUser, User taggedBy) {
        try {
            if (post == null || taggedUser == null || taggedBy == null) {
                log.warn("Invalid parameters for user tag notification");
                return;
            }

            User targetUser = userRepository.findById(taggedUser.getId()).orElse(taggedUser);
            User actionUser = userRepository.findById(taggedBy.getId()).orElse(taggedBy);

            // Don't notify if user tagged themselves
            if (targetUser.getId().equals(actionUser.getId())) {
                return;
            }

            String title = "You Were Tagged in a Post";
            String message = String.format("%s tagged you in their post", actionUser.getActualUsername());
            String actionUrl = "/post/" + post.getId();

            Notification notification = createNotification(
                    targetUser,
                    NotificationType.POST_TAGGED,
                    title,
                    message,
                    post.getId(),
                    "POST",
                    actionUrl,
                    actionUser
            );

            sendRealtimeNotification(notification);

            log.debug("User tag notification sent: postId={}, taggedUser={}",
                    post.getId(), taggedUser.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send user tag notification", e);
        }
    }

    /**
     * Send notifications to multiple tagged users
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyMultipleUsersTagged(Post post, List<User> taggedUsers, User taggedBy) {
        try {
            if (post == null || taggedUsers == null || taggedUsers.isEmpty() || taggedBy == null) {
                return;
            }

            for (User taggedUser : taggedUsers) {
                notifyUserTagged(post, taggedUser, taggedBy);
            }

            log.info("Sent {} tag notifications for post: {}", taggedUsers.size(), post.getId());

        } catch (Exception e) {
            log.error("Failed to send multiple user tag notifications", e);
        }
    }

    // ===== POST STATUS NOTIFICATIONS =====

    /**
     * Send notification when post is resolved
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyPostResolved(Post post, User resolvedBy) {
        try {
            // ZK Blind Shield Fix (Bug 5): accept actor-token posts
            if (post == null || (post.getUser() == null && post.getActorToken() == null)) {
                log.warn("Invalid parameters for post resolved notification — skipping");
                return;
            }

            User actionUser = resolvedBy != null
                    ? userRepository.findById(resolvedBy.getId()).orElse(resolvedBy) : null;
            String title = "Your Post Was Resolved";
            String message = actionUser != null
                    ? String.format("Your post was marked as resolved by %s", actionUser.getActualUsername())
                    : "Your post was marked as resolved";
            String actionUrl = "/post/" + post.getId();

            // === Citizen decoupled path ===
            if (post.getUser() == null) {
                Notification notification = createNotificationForActor(
                        post.getActorToken(), NotificationType.POST_RESOLVED,
                        title, message, post.getId(), "POST", actionUrl, actionUser);
                sendRealtimeNotificationForActor(notification, post.getActorToken());
                log.debug("Post resolved notification (actor) sent: postId={}", post.getId());
                return;
            }

            // === Legacy / authority relational path ===
            User targetUser = userRepository.findById(post.getUser().getId()).orElse(post.getUser());
            Notification notification = createNotification(
                    targetUser, NotificationType.POST_RESOLVED,
                    title, message, post.getId(), "POST", actionUrl, actionUser);
            sendRealtimeNotification(notification);
            log.debug("Post resolved notification sent: postId={}", post.getId());

        } catch (Exception e) {
            log.error("Failed to send post resolved notification", e);
        }
    }

    // ===== BROADCAST NOTIFICATIONS =====

    /**
     * Send notification when a post is reopened by the creator
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyPostReopened(Post post, User reopenedBy, String reason) {
        try {
            if (post == null || reopenedBy == null) {
                log.warn("Invalid parameters for post reopened notification");
                return;
            }

            User actionUser = userRepository.findById(reopenedBy.getId()).orElse(reopenedBy);

            // Find users who should be notified (e.g. tagged department users)
            // For now, we notify any tagged users (usually departments handling the post)
            List<UserTag> tags = post.getUserTags();
            if (tags == null || tags.isEmpty()) return;

            String title = "Issue Reopened";
            String reasonText = (reason != null && !reason.trim().isEmpty()) ? reason : "No reason provided";
            String message = String.format("Issue reopened by %s: %s", actionUser.getActualUsername(), truncateText(reasonText, 50));
            String actionUrl = "/post/" + post.getId();

            for (UserTag tag : tags) {
                User targetUser = tag.getTaggedUser();
                if (targetUser == null || targetUser.getId().equals(actionUser.getId())) continue;

                Notification notification = createNotification(
                        targetUser,
                        NotificationType.POST_REOPENED,
                        title,
                        message,
                        post.getId(),
                        "POST",
                        actionUrl,
                        actionUser
                );
                sendRealtimeNotification(notification);
            }
            log.debug("Post reopened notification sent: postId={}", post.getId());

        } catch (Exception e) {
            log.error("Failed to send post reopened notification", e);
        }
    }

    /**
     * Send broadcast notification to multiple users based on location
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyBroadcast(Post broadcastPost, List<User> targetUsers) {
        try {
            if (broadcastPost == null || targetUsers == null || targetUsers.isEmpty()) {
                log.warn("Invalid parameters for broadcast notification");
                return;
            }

            User actionUser = userRepository.findById(broadcastPost.getUser().getId()).orElse(broadcastPost.getUser());

            NotificationType notificationType = determineBroadcastNotificationType(broadcastPost);
            String title = getBroadcastTitle(broadcastPost);
            String message = String.format("New broadcast from %s: %s",
                    actionUser.getActualUsername(),
                    truncateText(broadcastPost.getContent(), 100));
            String actionUrl = "/post/" + broadcastPost.getId();

            // FIX MEMORY LEAK #9 â€” previously built the entire List<Notification> in heap
            // before calling saveAll().  For country-level broadcasts this can be tens of
            // thousands of objects alive simultaneously.
            // We now process in batches of NOTIFICATION_BROADCAST_BATCH_SIZE (500) so only
            // one batch is live at a time; each batch is eligible for GC after saveAll().
            final int BATCH_SIZE = 500;
            List<Notification> batch = new ArrayList<>(BATCH_SIZE);
            int totalSent = 0;

            for (User user : targetUsers) {
                if (user.getId().equals(actionUser.getId())) continue;

                Notification notification = Notification.builder()
                        .user(user)
                        .notificationType(notificationType)
                        .title(title)
                        .message(message)
                        .referenceId(broadcastPost.getId())
                        .referenceType("BROADCAST_POST")
                        .actionUrl(actionUrl)
                        .triggeredBy(actionUser)
                        .isRead(false)
                        .createdAt(new Date())
                        .build();

                batch.add(notification);

                if (batch.size() == BATCH_SIZE) {
                    List<Notification> saved = notificationRepository.saveAll(batch);
                    saved.forEach(n -> {
                        sendRealtimeNotification(n);
                        try {
                            String payload = objectMapper.writeValueAsString(NotificationDto.fromNotification(n));
                            webPushService.sendPushNotification(n.getUser(), payload);
                        } catch (Exception e) {
                            log.error("Failed to serialize push payload", e);
                        }
                    });
                    totalSent += saved.size();
                    batch = new ArrayList<>(BATCH_SIZE); // release previous batch for GC
                }
            }

            // Flush remaining
            if (!batch.isEmpty()) {
                List<Notification> saved = notificationRepository.saveAll(batch);
                saved.forEach(n -> {
                    sendRealtimeNotification(n);
                    try {
                        String payload = objectMapper.writeValueAsString(NotificationDto.fromNotification(n));
                        webPushService.sendPushNotification(n.getUser(), payload);
                    } catch (Exception e) {
                        log.error("Failed to serialize push payload", e);
                    }
                });
                totalSent += saved.size();
            }

            log.info("Sent {} broadcast notifications for post: {}", totalSent, broadcastPost.getId());

        } catch (Exception e) {
            log.error("Failed to send broadcast notifications", e);
        }
    }

    // ===== SYSTEM NOTIFICATIONS =====

    /**
     * Send system announcement to user
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void sendSystemAnnouncement(User user, String title, String message) {
        try {
            if (user == null) {
                log.warn("Cannot send system announcement to null user");
                return;
            }

            Notification notification = createNotification(
                    user,
                    NotificationType.SYSTEM_ANNOUNCEMENT,
                    title,
                    message,
                    null,
                    "SYSTEM",
                    null,
                    null
            );

            sendRealtimeNotification(notification);

            log.debug("System announcement sent to user: {}", user.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send system announcement", e);
        }
    }

    /**
     * Send system announcement to multiple users
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void sendSystemAnnouncementToUsers(List<User> users, String title, String message) {
        try {
            if (users == null || users.isEmpty()) {
                return;
            }

            // FIX MEMORY LEAK #9 â€” same batching fix as notifyBroadcast
            final int BATCH_SIZE = 500;
            List<Notification> batch = new ArrayList<>(BATCH_SIZE);

            for (User user : users) {
                Notification notification = Notification.builder()
                        .user(user)
                        .notificationType(NotificationType.SYSTEM_ANNOUNCEMENT)
                        .title(title)
                        .message(message)
                        .referenceType("SYSTEM")
                        .isRead(false)
                        .createdAt(new Date())
                        .build();
                batch.add(notification);

                if (batch.size() == BATCH_SIZE) {
                    List<Notification> saved = notificationRepository.saveAll(batch);
                    saved.forEach(this::sendRealtimeNotification);
                    batch = new ArrayList<>(BATCH_SIZE);
                }
            }
            if (!batch.isEmpty()) {
                List<Notification> saved = notificationRepository.saveAll(batch);
                saved.forEach(this::sendRealtimeNotification);
            }

            log.info("Sent system announcement to {} users", users.size());

        } catch (Exception e) {
            log.error("Failed to send system announcement to multiple users", e);
        }
    }

    /**
     * Send account update notification
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyAccountUpdate(User user, String updateMessage) {
        try {
            if (user == null) {
                return;
            }

            String title = "Account Update";
            Notification notification = createNotification(
                    user,
                    NotificationType.ACCOUNT_UPDATE,
                    title,
                    updateMessage,
                    null,
                    "SYSTEM",
                    "/profile",
                    null
            );

            sendRealtimeNotification(notification);

            log.debug("Account update notification sent to user: {}", user.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send account update notification", e);
        }
    }

    // ===== DEPARTMENT NOTIFICATIONS =====

    /**
     * Send department message to user
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void sendDepartmentMessage(User user, String departmentName, String message, User sentBy) {
        try {
            if (user == null) {
                return;
            }

            User targetUser = userRepository.findById(user.getId()).orElse(user);
            User actionUser = sentBy != null ? userRepository.findById(sentBy.getId()).orElse(sentBy) : null;

            String title = String.format("Message from %s", departmentName);

            Notification notification = createNotification(
                    targetUser,
                    NotificationType.DEPARTMENT_MESSAGE,
                    title,
                    message,
                    null,
                    "DEPARTMENT",
                    null,
                    actionUser
            );

            sendRealtimeNotification(notification);

            log.debug("Department message sent to user: {}", user.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send department message", e);
        }
    }

    /**
     * Notify post requires attention from authorities
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyPostAttentionRequired(Post post, User notifyUser, String reason) {
        try {
            if (post == null || notifyUser == null) {
                return;
            }

            User targetUser = userRepository.findById(notifyUser.getId()).orElse(notifyUser);

            String title = "Post Requires Attention";
            String message = String.format("A post requires your attention. Reason: %s", reason);
            String actionUrl = "/post/" + post.getId();

            Notification notification = createNotification(
                    targetUser,
                    NotificationType.POST_ATTENTION_REQUIRED,
                    title,
                    message,
                    post.getId(),
                    "POST",
                    actionUrl,
                    null
            );

            sendRealtimeNotification(notification);

            log.debug("Post attention notification sent to user: {}", notifyUser.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send post attention notification", e);
        }
    }

    // ===== COMMUNITY INVITE NOTIFICATIONS =====

    /**
     * Send notification when a user is invited to a community.
     * Called by CommunityInviteService after a targeted (username) invite is created.
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyCommunityInvite(com.govlyx.AI.model.CommunityInvite invite) {

        if (invite == null || invite.getInvitee() == null || invite.getCommunity() == null) {
            log.warn("Invalid parameters for community invite notification");
            return;
        }

        try {
            Long inviteeId = invite.getInvitee().getId();
            Long inviterId = invite.getInviter() != null ? invite.getInviter().getId() : null;
            Long communityId = invite.getCommunity().getId();
            String communityName = invite.getCommunity().getName();
            String inviterName = invite.getInviter() != null ? invite.getInviter().getActualUsername() : null;
            String actionUrl = "/invite/" + invite.getToken();

            // Refetch the invitee using userRepository to avoid Detached Entity exceptions
            User managedInvitee = userRepository.findById(inviteeId).orElse(null);
            if (managedInvitee == null) return;

            User managedInviter = inviterId != null ? userRepository.findById(inviterId).orElse(null) : null;

            String title = "Community Invite";
            String message = String.format("%s invited you to join \"%s\"",
                    managedInviter != null ? managedInviter.getActualUsername() : "Someone",
                    communityName);

            Notification notification = createNotification(
                    managedInvitee,
                    NotificationType.COMMUNITY_INVITE,
                    title,
                    message,
                    communityId,
                    "COMMUNITY_INVITE",
                    actionUrl,
                    managedInviter
            );

            sendRealtimeNotification(notification);

            log.info("Community invite notification sent: communityId={}, invitee={}, inviter={}",
                    communityId,
                    managedInvitee.getActualUsername(),
                    managedInviter != null ? managedInviter.getActualUsername() : "null");
        } catch (Exception ex) {
            log.error("Failed to execute notifyCommunityInvite due to exception:", ex);
        }
    }

    /**
     * Notify all administrators and moderators when someone requests to join a private community.
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyCommunityJoinRequest(com.govlyx.AI.model.CommunityJoinRequest joinRequest) {
        if (joinRequest == null || joinRequest.getUser() == null || joinRequest.getCommunity() == null) {
            return;
        }
        try {
            Long requesterId   = joinRequest.getUser().getId();
            Long communityId   = joinRequest.getCommunity().getId();
            String communityName  = joinRequest.getCommunity().getName();
            String requesterName  = joinRequest.getUser().getActualUsername();

            // Find all active moderators/admins for the community
            List<com.govlyx.AI.model.CommunityMember> managers =
                    communityMemberRepository.findActiveMembersCursor(
                                    communityId, null,
                                    PageRequest.of(0, 200))  // cap at 200 managers
                            .stream()
                            .filter(cm -> cm.getMemberRole() == com.govlyx.AI.model.CommunityMember.MemberRole.MODERATOR
                                    || cm.getMemberRole() == com.govlyx.AI.model.CommunityMember.MemberRole.ADMIN)
                            .collect(Collectors.toList());

            java.util.Set<Long> userIdsToNotify = new java.util.HashSet<>();
            for (com.govlyx.AI.model.CommunityMember manager : managers) {
                if (manager.getUser() != null) {
                    userIdsToNotify.add(manager.getUser().getId());
                }
            }

            // Also explicitly notify the community owner if not already included
            User owner = joinRequest.getCommunity().getOwner();
            if (owner != null) {
                userIdsToNotify.add(owner.getId());
            }

            for (Long targetUserId : userIdsToNotify) {
                if (targetUserId.equals(requesterId)) continue;

                // Re-fetch managed instance to avoid detached entity issues
                User managedAdmin = userRepository.findById(targetUserId).orElse(null);
                if (managedAdmin == null) continue;

                User managedRequester = userRepository.findById(requesterId).orElse(null);

                String title   = "Join Request Pending";
                String message = String.format("@%s requested to join \"%s\"", requesterName, communityName);

                Notification notification = createNotification(
                        managedAdmin,
                        NotificationType.COMMUNITY_JOIN_REQUEST,
                        title,
                        message,
                        joinRequest.getId(),        // referenceId = join-request DB id
                        "COMMUNITY_JOIN_REQUEST",
                        "/communities/" + communityId + "?tab=requests",
                        managedRequester
                );
                sendRealtimeNotification(notification);
            }
            log.info("Join-request notifications sent: communityId={} requester={} notifiedCount={}",
                    communityId, requesterName, userIdsToNotify.size());
        } catch (Exception ex) {
            log.error("Failed to execute notifyCommunityJoinRequest: ", ex);
        }
    }

    /**
     * Notify the user when their request to join a private community was rejected.
     */
    public void notifyCommunityJoinRejected(com.govlyx.AI.model.CommunityJoinRequest joinRequest, User moderator) {
        if (joinRequest == null || joinRequest.getUser() == null || joinRequest.getCommunity() == null) {
            return;
        }
        try {
            User user = userRepository.findById(joinRequest.getUser().getId()).orElse(null);
            if (user == null) return;

            User managedModerator = moderator != null ? userRepository.findById(moderator.getId()).orElse(null) : null;

            String title   = "Join Request Rejected";
            String message = String.format("Your request to join \"%s\" was rejected.",
                    joinRequest.getCommunity().getName());

            Notification notification = createNotification(
                    user,
                    NotificationType.COMMUNITY_JOIN_REJECT,
                    title,
                    message,
                    joinRequest.getCommunity().getId(),
                    "COMMUNITY_JOIN_REJECT",
                    "/communities/" + joinRequest.getCommunity().getId(),
                    managedModerator
            );
            sendRealtimeNotification(notification);
            log.info("Join-reject notification sent: userId={} communityId={}",
                    user.getId(), joinRequest.getCommunity().getId());
        } catch (Exception ex) {
            log.error("Failed to execute notifyCommunityJoinRejected: ", ex);
        }
    }

    /**
     * Notify the inviter when their sent invite was accepted.
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyCommunityInviteAccepted(com.govlyx.AI.model.CommunityInvite invite, User acceptor) {
        if (invite == null || invite.getInviter() == null || invite.getCommunity() == null) {
            return;
        }
        try {
            User inviter = userRepository.findById(invite.getInviter().getId()).orElse(null);
            if (inviter == null) return;

            User managedAcceptor = acceptor != null ? userRepository.findById(acceptor.getId()).orElse(null) : null;
            String acceptorName  = managedAcceptor != null ? managedAcceptor.getActualUsername() : "Someone";

            String title   = "Invite Accepted";
            String message = String.format("@%s accepted your invite to join \"%s\"",
                    acceptorName, invite.getCommunity().getName());

            Notification notification = createNotification(
                    inviter,
                    NotificationType.COMMUNITY_INVITE_ACCEPT,
                    title,
                    message,
                    invite.getCommunity().getId(),
                    "COMMUNITY_INVITE_ACCEPT",
                    "/communities/" + invite.getCommunity().getId(),
                    managedAcceptor
            );
            sendRealtimeNotification(notification);
            log.info("Invite-accepted notification sent: inviterId={} communityId={}",
                    inviter.getId(), invite.getCommunity().getId());
        } catch (Exception ex) {
            log.error("Failed to execute notifyCommunityInviteAccepted: ", ex);
        }
    }

    /**
     * Notify the inviter when their sent invite was declined.
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyCommunityInviteDeclined(com.govlyx.AI.model.CommunityInvite invite, User decliner) {
        if (invite == null || invite.getInviter() == null || invite.getCommunity() == null) {
            return;
        }
        try {
            User inviter = userRepository.findById(invite.getInviter().getId()).orElse(null);
            if (inviter == null) return;

            User managedDecliner = decliner != null ? userRepository.findById(decliner.getId()).orElse(null) : null;
            String declinerName  = managedDecliner != null ? managedDecliner.getActualUsername() : "Someone";

            String title   = "Invite Declined";
            String message = String.format("@%s declined your invite to join \"%s\"",
                    declinerName, invite.getCommunity().getName());

            Notification notification = createNotification(
                    inviter,
                    NotificationType.COMMUNITY_INVITE_DECLINE,
                    title,
                    message,
                    invite.getCommunity().getId(),
                    "COMMUNITY_INVITE_DECLINE",
                    "/communities/" + invite.getCommunity().getId(),
                    managedDecliner
            );
            sendRealtimeNotification(notification);
            log.info("Invite-declined notification sent: inviterId={} communityId={}",
                    inviter.getId(), invite.getCommunity().getId());
        } catch (Exception ex) {
            log.error("Failed to execute notifyCommunityInviteDeclined: ", ex);
        }
    }

    /**
     * Send notification to community members when a new post is created.
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyCommunityNewPost(SocialPost socialPost, List<Long> targetUserIds, String communityName) {
        try {
            if (socialPost == null || socialPost.getUser() == null || targetUserIds == null || targetUserIds.isEmpty()) {
                return;
            }

            User actionUser = userRepository.findById(socialPost.getUser().getId()).orElse(socialPost.getUser());

            String title = "New post in " + communityName;
            String message = String.format("%s posted: \"%s\"",
                    actionUser.getActualUsername(),
                    truncateText(socialPost.getContent(), 50));
            String actionUrl = "/post/" + socialPost.getId();
            if (socialPost.getCommunity() != null) {
                actionUrl = "/communities/" + socialPost.getCommunity().getSlug() + "?postId=" + socialPost.getId();
            }

            final int BATCH_SIZE = 500;
            int totalSent = 0;

            for (int i = 0; i < targetUserIds.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, targetUserIds.size());
                List<Long> batchIds = targetUserIds.subList(i, end);

                List<User> batchUsers = userRepository.findAllById(batchIds);
                List<Notification> batchNotifications = new ArrayList<>(batchUsers.size());

                for (User targetUser : batchUsers) {
                    Notification notification = Notification.builder()
                            .user(targetUser)
                            .notificationType(NotificationType.COMMUNITY_NEW_POST)
                            .title(title)
                            .message(message)
                            .referenceId(socialPost.getId())
                            .referenceType("SOCIAL_POST")
                            .actionUrl(actionUrl)
                            .triggeredBy(actionUser)
                            .isRead(false)
                            .createdAt(new Date())
                            .build();

                    batchNotifications.add(notification);
                }

                List<Notification> saved = notificationRepository.saveAll(batchNotifications);
                saved.forEach(this::sendRealtimeNotification);
                totalSent += saved.size();
            }

            log.info("Sent {} community new post notifications for post: {}", totalSent, socialPost.getId());

        } catch (Exception e) {
            log.error("Failed to send community new post notifications", e);
        }
    }

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyPostApproved(SocialPost post, String communityName, User moderator) {
        try {
            if (post == null || post.getUser() == null) return;
            User targetUser = userRepository.findById(post.getUser().getId()).orElse(post.getUser());

            String title = "Post Approved";
            String message = String.format("Your post in \"%s\" has been approved.", communityName);
            String actionUrl = "/post/" + post.getId();

            if (post.getCommunity() != null && post.getCommunity().getSlug() != null) {
                actionUrl = "/communities/" + post.getCommunity().getSlug() + "?postId=" + post.getId();
            }

            Notification notification = createNotification(
                    targetUser,
                    NotificationType.COMMUNITY_POST_APPROVED,
                    title,
                    message,
                    post.getId(),
                    "SOCIAL_POST",
                    actionUrl,
                    moderator
            );

            sendRealtimeNotification(notification);
            log.debug("Post approval notification sent to user: {}", targetUser.getActualUsername());
        } catch (Exception e) {
            log.error("Failed to send post approval notification", e);
        }
    }

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyPostRejected(SocialPost post, String communityName, User moderator) {
        try {
            if (post == null || post.getUser() == null) return;
            User targetUser = userRepository.findById(post.getUser().getId()).orElse(post.getUser());

            String title = "Post Rejected";
            String message = String.format("Your post in \"%s\" was not approved.", communityName);

            String actionUrl = "/community/" + post.getCommunityId();
            if (post.getCommunity() != null && post.getCommunity().getSlug() != null) {
                actionUrl = "/communities/" + post.getCommunity().getSlug();
            }

            Notification notification = createNotification(
                    targetUser,
                    NotificationType.COMMUNITY_POST_REJECTED,
                    title,
                    message,
                    post.getCommunityId(),
                    "COMMUNITY",
                    actionUrl,
                    moderator
            );

            sendRealtimeNotification(notification);
            log.debug("Post rejection notification sent to user: {}", targetUser.getActualUsername());
        } catch (Exception e) {
            log.error("Failed to send post rejection notification", e);
        }
    }

    /**
     * Helper to batch convert Notifications to DTOs while eliminating N+1 database queries.
     */
    private List<NotificationDto> toNotificationDtos(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // Batch pre-fetch join request statuses to eliminate N+1 DB roundtrips
        Set<Long> joinRequestIds = notifications.stream()
                .filter(n -> n.getNotificationType() == NotificationType.COMMUNITY_JOIN_REQUEST && n.getReferenceId() != null)
                .map(Notification::getReferenceId)
                .collect(Collectors.toSet());

        java.util.Map<Long, String> joinRequestStatusMap = new java.util.HashMap<>();
        if (!joinRequestIds.isEmpty()) {
            try {
                com.govlyx.AI.repository.CommunityJoinRequestRepo joinRequestRepo =
                        com.govlyx.AI.util.SpringContextHolder.getBean(com.govlyx.AI.repository.CommunityJoinRequestRepo.class);
                if (joinRequestRepo != null) {
                    joinRequestRepo.findAllById(joinRequestIds).forEach(jr -> {
                        if (jr.getStatus() != com.govlyx.AI.model.CommunityJoinRequest.RequestStatus.PENDING) {
                            joinRequestStatusMap.put(jr.getId(), jr.getStatus().name());
                        }
                    });
                }
            } catch (Exception e) {
                log.debug("Could not batch load join request statuses: {}", e.getMessage());
            }
        }

        return notifications.stream()
                .map(n -> {
                    NotificationDto dto = NotificationDto.fromNotification(n);
                    if (dto != null && n.getNotificationType() == NotificationType.COMMUNITY_JOIN_REQUEST && n.getReferenceId() != null) {
                        String status = joinRequestStatusMap.get(n.getReferenceId());
                        if (status != null) {
                            dto.setJoinRequestStatus(status);
                        }
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get user's notifications with pagination (default tab: all)
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<NotificationDto> getUserNotifications(User user, Long beforeId, Integer limit) {
        return getUserNotifications(user, beforeId, limit, "all");
    }

    /**
     * Get user's notifications with pagination and category tab filtering
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<NotificationDto> getUserNotifications(User user, Long beforeId, Integer limit, String tab) {
        try {
            if (user == null) {
                throw new ValidationException("User cannot be null");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getUserNotifications", beforeId, limit,
                    Constant.NOTIFICATION_DEFAULT_LIMIT, Constant.NOTIFICATION_MAX_LIMIT);

            String filterTab = (tab != null) ? tab.trim().toLowerCase() : "all";
            PageRequest pageRequest = PageRequest.of(0, setup.getValidatedLimit());
            List<Notification> notifications;

            switch (filterTab) {
                case "unread":
                    if (setup.hasCursor()) {
                        notifications = notificationRepository.findByUserAndIsReadFalseAndIdLessThanOrderByCreatedAtDesc(
                                user, setup.getSanitizedCursor(), pageRequest);
                    } else {
                        notifications = notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(
                                user, pageRequest);
                    }
                    break;
                case "invites":
                    if (setup.hasCursor()) {
                        notifications = notificationRepository.findByUserAndNotificationTypeInAndIdLessThanOrderByCreatedAtDesc(
                                user, INVITE_TYPES, setup.getSanitizedCursor(), pageRequest);
                    } else {
                        notifications = notificationRepository.findByUserAndNotificationTypeInOrderByCreatedAtDesc(
                                user, INVITE_TYPES, pageRequest);
                    }
                    break;
                case "interactions":
                    if (setup.hasCursor()) {
                        notifications = notificationRepository.findByUserAndNotificationTypeInAndIdLessThanOrderByCreatedAtDesc(
                                user, INTERACTION_TYPES, setup.getSanitizedCursor(), pageRequest);
                    } else {
                        notifications = notificationRepository.findByUserAndNotificationTypeInOrderByCreatedAtDesc(
                                user, INTERACTION_TYPES, pageRequest);
                    }
                    break;
                case "all":
                default:
                    if (setup.hasCursor()) {
                        notifications = notificationRepository.findByUserAndIdLessThanOrderByCreatedAtDesc(
                                user, setup.getSanitizedCursor(), pageRequest);
                    } else {
                        notifications = notificationRepository.findByUserOrderByCreatedAtDesc(
                                user, pageRequest);
                    }
                    break;
            }

            List<NotificationDto> notificationDtos = toNotificationDtos(notifications);

            boolean hasMore = notifications.size() == setup.getValidatedLimit();
            Long nextCursor = hasMore && !notifications.isEmpty()
                    ? notifications.get(notifications.size() - 1).getId()
                    : null;

            return PaginatedResponse.of(notificationDtos, hasMore, nextCursor, setup.getValidatedLimit());

        } catch (Exception e) {
            log.error("Failed to get user notifications (tab: {})", tab, e);
            throw new ServiceException("Failed to get notifications: " + e.getMessage(), e);
        }
    }

    /**
     * Get aggregated notification counts (all, unread, invites, interactions) in a single DB roundtrip.
     */
    @Transactional(readOnly = true)
    public NotificationSummaryDto getNotificationSummaryCounts(User user) {
        try {
            if (user == null) {
                return new NotificationSummaryDto(0L, 0L, 0L, 0L);
            }
            NotificationSummaryDto summary =
                    notificationRepository.getNotificationSummaryCounts(user, INVITE_TYPES, INTERACTION_TYPES);
            return summary != null ? summary : new NotificationSummaryDto(0L, 0L, 0L, 0L);
        } catch (Exception e) {
            log.error("Failed to get notification summary counts for user: {}", user.getId(), e);
            return new NotificationSummaryDto(0L, 0L, 0L, 0L);
        }
    }

    /**
     * Get unread notifications for user
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getUnreadNotifications(User user) {
        try {
            if (user == null) {
                throw new ValidationException("User cannot be null");
            }

            List<Notification> notifications = notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user);

            return toNotificationDtos(notifications);

        } catch (Exception e) {
            log.error("Failed to get unread notifications", e);
            throw new ServiceException("Failed to get unread notifications: " + e.getMessage(), e);
        }
    }

    /**
     * Get unread notification count.
     * Cached for 30 s â€” polled every 60 s by the frontend useUnreadNotificationsCount hook.
     * Eliminates up to 1000 DB COUNT(*) queries/min at scale.
     * Evicted eagerly by @CacheEvict whenever a notification is created, read, or deleted.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = Constant.CACHE_NOTIF_UNREAD_COUNT, key = "#user.id")
    public long getUnreadNotificationCount(User user) {
        try {
            if (user == null) {
                return 0;
            }

            return notificationRepository.countByUserAndIsReadFalse(user);

        } catch (Exception e) {
            log.error("Failed to get unread notification count", e);
            return 0;
        }
    }

    /**
     * Mark notification as read.
     * Evicts the cached unread count so the next poll sees fresh data immediately.
     */
    @Transactional
    @CacheEvict(value = Constant.CACHE_NOTIF_UNREAD_COUNT, key = "#user.id")
    public void markNotificationAsRead(Long notificationId, User user) {
        try {
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new ValidationException("Notification not found"));

            if (!notification.getUser().getId().equals(user.getId())) {
                throw new SecurityException("User does not have permission to modify this notification");
            }

            if (!notification.getIsRead()) {
                notification.markAsRead();
                notificationRepository.save(notification);

                log.debug("Notification marked as read: id={}", notificationId);
            }

        } catch (Exception e) {
            log.error("Failed to mark notification as read: id={}", notificationId, e);
            throw new ServiceException("Failed to mark notification as read: " + e.getMessage(), e);
        }
    }

    /**
     * Mark all notifications as read for user.
     * Evicts the cached unread count.
     */
    @Transactional
    @CacheEvict(value = Constant.CACHE_NOTIF_UNREAD_COUNT, key = "#user.id")
    public void markAllNotificationsAsRead(User user) {
        try {
            if (user == null) {
                throw new ValidationException("User cannot be null");
            }

            // MEMORY LEAK FIX: the original code called findByUserAndIsReadFalse() which
            // loaded EVERY unread notification for the user into a List<Notification> in heap,
            // mutated each one in a Java loop, then called saveAll() on the entire list.
            // For an active user with hundreds of unread notifications this could hold thousands
            // of fully-hydrated Notification objects live simultaneously inside one @Transactional
            // method, creating massive GC pressure and risking OOM under concurrent load.
            //
            // Fix: single bulk UPDATE executed entirely in the DB â€” zero entities loaded into JVM.
            // The repository method is:
            //   @Modifying
            //   @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.user = :user AND n.isRead = false")
            //   int markAllAsReadForUser(@Param("user") User user, @Param("now") Date now);
            Date now = new Date();
            int updatedCount = notificationRepository.markAllAsReadForUser(user, now);

            log.info("Marked {} notifications as read for user: {}",
                    updatedCount, user.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to mark all notifications as read", e);
            throw new ServiceException("Failed to mark all notifications as read: " + e.getMessage(), e);
        }
    }

    /**
     * Delete notification.
     * Evicts the cached unread count â€” the deleted notification may have been unread.
     */
    @Transactional
    @CacheEvict(value = Constant.CACHE_NOTIF_UNREAD_COUNT, key = "#user.id")
    public void deleteNotification(Long notificationId, User user) {
        try {
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new ValidationException("Notification not found"));

            if (!notification.getUser().getId().equals(user.getId())) {
                throw new SecurityException("User does not have permission to delete this notification");
            }

            notificationRepository.delete(notification);

            log.debug("Notification deleted: id={}", notificationId);

        } catch (Exception e) {
            log.error("Failed to delete notification: id={}", notificationId, e);
            throw new ServiceException("Failed to delete notification: " + e.getMessage(), e);
        }
    }

    /**
     * Delete all notifications for user.
     * Evicts the cached unread count.
     */
    @Transactional
    @CacheEvict(value = Constant.CACHE_NOTIF_UNREAD_COUNT, key = "#user.id")
    public void deleteAllNotifications(User user) {
        try {
            if (user == null) {
                throw new ValidationException("User cannot be null");
            }

            int deletedCount = notificationRepository.deleteByUser(user);

            log.info("Deleted {} notifications for user: {}", deletedCount, user.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to delete all notifications", e);
            throw new ServiceException("Failed to delete all notifications: " + e.getMessage(), e);
        }
    }

    /**
     * Clean up old notifications (older than 30 days)
     */
    @Transactional
    public void cleanupOldNotifications() {
        try {
            Date cutoffDate = new Date(System.currentTimeMillis() -
                    (long) Constant.NOTIFICATION_MAX_AGE_DAYS * 24 * 60 * 60 * 1000);

            int deletedCount = notificationRepository.deleteByCreatedAtBefore(cutoffDate);

            log.info("Cleaned up {} old notifications (older than {} days)",
                    deletedCount, Constant.NOTIFICATION_MAX_AGE_DAYS);

        } catch (Exception e) {
            log.error("Failed to cleanup old notifications", e);
        }
    }
    // ===== COMMUNITY ROLE NOTIFICATIONS =====

    /**
     * Notifies a community member when their role is upgraded or downgraded.
     * Delivers via:
     *   1. In-app WebSocket real-time notification (always)
     *   2. Browser Web Push notification (if user has a push subscription)
     *
     * This is an industry-standard high-signal lifecycle event notification.
     * Pattern mirrors notifyCommunityDeleted() — async, non-blocking, wrapped in try/catch.
     *
     * @param targetUser  The member whose role changed
     * @param community   The community in which the change happened
     * @param oldRole     Previous MemberRole (used to determine upgrade vs downgrade message)
     * @param newRole     New MemberRole assigned
     * @param changedBy   The admin/owner who made the change
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void notifyCommunityRoleChanged(
            com.govlyx.AI.model.User targetUser,
            com.govlyx.AI.model.Community community,
            com.govlyx.AI.model.CommunityMember.MemberRole oldRole,
            com.govlyx.AI.model.CommunityMember.MemberRole newRole,
            com.govlyx.AI.model.User changedBy) {
        try {
            if (targetUser == null || community == null || newRole == null) {
                log.warn("notifyCommunityRoleChanged: missing required parameters");
                return;
            }

            // Re-fetch to avoid Hibernate closed-session / LazyInit issues
            com.govlyx.AI.model.User recipient =
                    userRepository.findById(targetUser.getId()).orElse(null);
            com.govlyx.AI.model.User actionUser = (changedBy != null)
                    ? userRepository.findById(changedBy.getId()).orElse(changedBy)
                    : null;
            if (recipient == null) return;

            // Determine upgrade vs downgrade for message wording
            // MemberRole.ordinal(): MEMBER=0, MODERATOR=1, ADMIN=2
            boolean isUpgrade = (oldRole == null)
                    || (newRole.ordinal() > oldRole.ordinal());

            String title, message;
            if (isUpgrade) {
                title   = "\uD83C\uDF89 Role Upgraded in " + community.getName();
                message = String.format("You are now a %s in '%s'.",
                        toDisplayRole(newRole), community.getName());
            } else {
                title   = "Role Updated in " + community.getName();
                message = String.format("Your role in '%s' has been changed to %s.",
                        community.getName(), toDisplayRole(newRole));
            }

            String actionUrl = "/communities/" + community.getSlug();

            // 1. Persist + real-time WebSocket delivery
            com.govlyx.AI.model.Notification notification = createNotification(
                    recipient,
                    NotificationType.COMMUNITY_ROLE_CHANGED,
                    title,
                    message,
                    community.getId(),
                    "COMMUNITY",
                    actionUrl,
                    actionUser
            );

            sendRealtimeNotification(notification);

            // 2. Browser Web Push (same pattern as broadcast notifications)
            try {
                String payload = objectMapper.writeValueAsString(
                        com.govlyx.AI.dto.NotificationDto.fromNotification(notification));
                webPushService.sendPushNotification(recipient, payload);
            } catch (Exception e) {
                log.warn("Failed to send web push for role change notification: {}", e.getMessage());
            }

            log.info("Role-change notification sent: userId={} community={} {} -> {}",
                    recipient.getId(), community.getId(), oldRole, newRole);

        } catch (Exception e) {
            log.error("Failed to send community role-change notification", e);
        }
    }

    /**
     * Converts MemberRole enum to a human-readable display string.
     */
    private String toDisplayRole(com.govlyx.AI.model.CommunityMember.MemberRole role) {
        if (role == null) return "Member";
        switch (role) {
            case ADMIN:     return "Admin";
            case MODERATOR: return "Moderator";
            default:        return "Member";
        }
    }

    // ===== HELPER METHODS =====

    /**
     * Create and save a notification
     */
    private Notification createNotification(
            User user,
            NotificationType type,
            String title,
            String message,
            Long referenceId,
            String referenceType,
            String actionUrl,
            User triggeredBy) {

        Notification notification = Notification.builder()
                .user(user)
                .notificationType(type)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .actionUrl(actionUrl)
                .triggeredBy(triggeredBy)
                .isRead(false)
                .createdAt(new Date())
                .build();

        return notificationRepository.save(notification);
    }

    /**
     * Send real-time notification via WebSocket to a relational User.
     */
    private void sendRealtimeNotification(Notification notification) {
        try {
            if (notification.getUser() == null) return;
            NotificationDto dto = NotificationDto.fromNotification(notification);
            messagingTemplate.convertAndSendToUser(
                    notification.getUser().getId().toString(),
                    "/queue/notifications",
                    dto
            );
            log.debug("Real-time notification sent to user: {}",
                    notification.getUser().getActualUsername());
        } catch (Exception e) {
            log.warn("Failed to send real-time notification (notification still saved): {}",
                    e.getMessage());
        }
    }

    // =========================================================================
    // ZK Blind Shield — Actor-Token notification helpers (Bug 5)
    // =========================================================================

    /**
     * Create and persist a notification routed to a citizen actor_token
     * instead of a relational User FK.
     */
    private Notification createNotificationForActor(
            String recipientActorToken,
            NotificationType type,
            String title,
            String message,
            Long referenceId,
            String referenceType,
            String actionUrl,
            User triggeredBy) {

        Notification notification = Notification.builder()
                .user(null)                              // no relational FK for decoupled citizens
                .recipientActorToken(recipientActorToken)
                .notificationType(type)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .actionUrl(actionUrl)
                .triggeredBy(triggeredBy)
                .isRead(false)
                .createdAt(new Date())
                .build();

        return notificationRepository.save(notification);
    }

    /**
     * Push a real-time WebSocket notification to a citizen identified by actor_token.
     * Delivery topic: /topic/notifications/actor/{actorToken}
     */
    private void sendRealtimeNotificationForActor(Notification notification, String actorToken) {
        try {
            if (actorToken == null || actorToken.isBlank()) return;
            NotificationDto dto = NotificationDto.fromNotification(notification);
            messagingTemplate.convertAndSend(
                    "/topic/notifications/actor/" + actorToken, dto);
            log.debug("Real-time notification (actor) sent to actorToken: {}", actorToken);
        } catch (Exception e) {
            log.warn("Failed to send actor real-time notification (notification still saved): {}",
                    e.getMessage());
        }
    }

    /**
     * Determine notification type for broadcast
     */
    private NotificationType determineBroadcastNotificationType(Post post) {
        if (post.getBroadcastScope() == null) {
            return NotificationType.BROADCAST_NEW;
        }

        switch (post.getBroadcastScope()) {
            case COUNTRY:
                return NotificationType.BROADCAST_COUNTRY;
            case STATE:
                return NotificationType.BROADCAST_STATE;
            case DISTRICT:
                return NotificationType.BROADCAST_DISTRICT;
            default:
                return NotificationType.BROADCAST_NEW;
        }
    }

    /**
     * Get broadcast title based on scope
     */
    private String getBroadcastTitle(Post post) {
        if (post.getBroadcastScope() == null) {
            return "New Broadcast";
        }

        switch (post.getBroadcastScope()) {
            case COUNTRY:
                return "Country-wide Broadcast";
            case STATE:
                return "State-level Broadcast";
            case DISTRICT:
                return "District-level Broadcast";
            default:
                return "New Broadcast in Your Area";
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String unescaped = HtmlUtils.htmlUnescape(text);
        if (unescaped.length() <= maxLength) {
            return unescaped;
        }
        return unescaped.substring(0, maxLength) + "...";
    }

    /**
     * Delete all notifications associated with a post
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotificationsForPost(Long postId, boolean isSocialPost) {
        try {
            List<String> referenceTypes = isSocialPost ?
                    List.of("SOCIAL_POST", "COMMENT") :
                    List.of("POST", "BROADCAST_POST", "COMMENT");

            int count = notificationRepository.deleteByReferenceIdAndTypes(postId, referenceTypes);
            log.info("Deleted {} notifications for post: id={}, isSocialPost={}", count, postId, isSocialPost);
        } catch (Exception e) {
            log.error("Failed to delete notifications for post: id={}", postId, e);
        }
    }
}
