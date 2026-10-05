package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.AuthorDto;
import com.JanSahayak.AI.dto.CommunityMessageDto;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.CommunityMember;
import com.JanSahayak.AI.model.CommunityMessage;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.enums.ReportCategory;
import com.JanSahayak.AI.model.ContentReport;
import com.JanSahayak.AI.repository.ContentReportRepository;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import com.JanSahayak.AI.repository.CommunityMessageRepo;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.CommunitySharedPostSnapshotRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CommunityChatService {

    private final CommunityMessageRepo communityMessageRepo;
    private final CommunityRepo communityRepo;
    private final CommunityMemberRepo communityMemberRepo;
    private final SocialPostRepo socialPostRepo;
    private final UserRepo userRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final ContentReportRepository contentReportRepository;
    private final NotificationService notificationService;
    private final CommunitySharedPostSnapshotRepo communitySharedPostSnapshotRepo;
    private final CommunityChatModerator chatModerator;
    private final com.JanSahayak.AI.service.PlanEnforcementService planEnforcementService;

    /**
     * Retrieves paginated community message history.
     * Uses cursor-based pagination to support rapid scrolling in high-active groups. Eagerly
     * maps DTOs within transaction boundaries to prevent LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public List<CommunityMessageDto> getChatHistory(Long communityId, Long userId, Long cursor, int limit) {
        Community community = findCommunityOrThrow(communityId);
        assertReadAccess(community, userId);

        Pageable pageable = PageRequest.of(0, limit);
        List<CommunityMessage> messages;

        if (cursor == null || cursor <= 0) {
            messages = communityMessageRepo.findRecentMessages(communityId, pageable);
        } else {
            messages = communityMessageRepo.findRecentMessagesBeforeCursor(communityId, cursor, pageable);
        }

        // Map within transaction to safely initialize lazy properties
        return messages.stream()
                .map(CommunityMessageDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Processes a new group message.
     * Enforces permission rules:
     * - Must be a member to send.
     * - If group chat is disabled, only admins/moderators can send.
     * - Shared posts must belong to the exact same community.
     * - Content must pass moderation filters (extremism keyword & suspicious URL checks).
     * Calculates expiration and broadcasts in real-time over the WebSocket topic.
     */
    public CommunityMessageDto processNewMessage(Long communityId, Long userId, String content, Long sharedPostId, Long replyToId) {
        Community community = findCommunityOrThrow(communityId);
        CommunityMember member = findActiveMemberOrThrow(communityId, userId);

        if (Boolean.TRUE.equals(member.getIsMuted())) {
            throw new SecurityException("You are muted in this community.");
        }

        if (community.isSecret() && planEnforcementService.isCommunityFrozen(community.getOwner().getId())) {
            throw new SecurityException("This Secret community is currently frozen. The owner must have an active Govlyx pass to reactivate it.");
        }

        // Lock-down check: Only admins/mods can post if group chat is disabled
        if (!Boolean.TRUE.equals(community.getIsGroupChatEnabled())) {
            if (member.getMemberRole() == CommunityMember.MemberRole.MEMBER) {
                throw new SecurityException("Group chat has been disabled by the administrator.");
            }
        }

        CommunityMessage.MessageType msgType = CommunityMessage.MessageType.TEXT;
        SocialPost sharedPost = null;

        // Security check: sharedPostId must exist (posts from any community or the main feed can be shared)
        if (sharedPostId != null) {
            sharedPost = socialPostRepo.findById(sharedPostId)
                    .orElseThrow(() -> new NoSuchElementException("Shared post not found: " + sharedPostId));
            msgType = CommunityMessage.MessageType.SHARE_POST;
        }

        if (msgType == CommunityMessage.MessageType.TEXT && (content == null || content.trim().isEmpty())) {
            throw new ValidationException("Message content cannot be empty.");
        }

        // 🛡️ SECURITY MODERATION SCAN
        boolean flagged = chatModerator.scanMessage(content);

        Instant expiresAt = null;
        if (community.getChatRetentionDays() != null && community.getChatRetentionDays() > 0) {
            expiresAt = Instant.now().plus(community.getChatRetentionDays(), ChronoUnit.DAYS);
        }

        String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();
        if (idempotencyKey != null) {
            java.util.Optional<CommunityMessage> existingMessage = communityMessageRepo.findByIdempotencyKey(idempotencyKey);
            if (existingMessage.isPresent()) {
                log.info("Idempotency hit: Returning existing CommunityMessage for key {}", idempotencyKey);
                return CommunityMessageDto.fromEntity(existingMessage.get());
            }
        }

        CommunityMessage message = CommunityMessage.builder()
                .communityId(communityId)
                .sender(member.getUser())
                .content(content)
                .messageType(msgType)
                .sharedPost(sharedPost)
                .replyToId(replyToId)
                .expiresAt(expiresAt)
                .isFlagged(flagged)
                .isQuarantined(flagged)
                .ipAddress(com.JanSahayak.AI.util.IpUtils.getClientIpFromContext())
                .idempotencyKey(idempotencyKey)
                .build();

        if (msgType == CommunityMessage.MessageType.SHARE_POST && sharedPost != null) {
            com.JanSahayak.AI.model.CommunitySharedPostSnapshot snapshot = com.JanSahayak.AI.model.CommunitySharedPostSnapshot.builder()
                    .message(message)
                    .postId(sharedPost.getId())
                    .content(sharedPost.getContent())
                    .authorUsername(sharedPost.getUser() != null ? sharedPost.getUser().getActualUsername() : null)
                    .authorAvatar(sharedPost.getUser() != null ? sharedPost.getUser().getProfileImage() : null)
                    .build();
            message.setSharedPostSnapshot(snapshot);
        }

        communityMessageRepo.save(message);

        if (flagged) {
            log.warn("[Security Quarantine] Chat message {} blocked from broadcast due to policy violation.", message.getId());
            throw new ValidationException("Message blocked: Content violates community safety guidelines.");
        }

        // Convert inside the transaction to safely initialize roles and proxies
        CommunityMessageDto dto = CommunityMessageDto.fromEntity(message);

        // Broadcast to WebSocket subscribers
        String destination = "/topic/community." + communityId + ".messages";
        messagingTemplate.convertAndSend(destination, dto);

        log.debug("Broadcasted community message {} to topic {}", message.getId(), destination);
        return dto;
    }

    /**
     * Allows community admins and moderators to pin/unpin messages.
     */
    public CommunityMessageDto toggleMessagePin(Long communityId, Long messageId, Long userId) {
        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, userId);

        // Govlyx VIP Feature
        if (!planEnforcementService.canPinMessages(userId)) {
            throw new com.JanSahayak.AI.exception.PlanLimitExceededException("Message pinning is a Govlyx VIP feature.");
        }

        CommunityMessage message = communityMessageRepo.findById(messageId)
                .orElseThrow(() -> new NoSuchElementException("Message not found: " + messageId));

        if (!communityId.equals(message.getCommunityId())) {
            throw new IllegalArgumentException("Message does not belong to this community.");
        }

        boolean newPinnedState = !message.isPinned();
        if (newPinnedState) {
            // Enforce single pinned message: unpin any other message first
            communityMessageRepo.unpinAllMessagesInCommunity(communityId);
        }
        
        message.setPinned(newPinnedState);
        communityMessageRepo.save(message);

        CommunityMessageDto dto = CommunityMessageDto.fromEntity(message);

        // Broadcast system/update event
        messagingTemplate.convertAndSend("/topic/community." + communityId + ".messages", dto);
        return dto;
    }

    /**
     * Retrieves currently pinned messages for a community.
     */
    @Transactional(readOnly = true)
    public List<CommunityMessageDto> getPinnedMessages(Long communityId) {
        return communityMessageRepo.findPinnedMessages(communityId)
                .stream()
                .map(CommunityMessageDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Soft-deletes a message.
     * Allowed only for the message owner or a community moderator/admin.
     */
    public void deleteMessage(Long communityId, Long messageId, Long userId) {
        CommunityMessage message = communityMessageRepo.findById(messageId)
                .orElseThrow(() -> new NoSuchElementException("Message not found with id: " + messageId));

        if (!communityId.equals(message.getCommunityId())) {
            throw new IllegalArgumentException("Message does not belong to this community.");
        }

        boolean isOwner = message.getSender().getId().equals(userId);
        boolean isAdmin = false;

        if (!isOwner) {
            CommunityMember member = communityMemberRepo.findByCommunityIdAndUserId(communityId, userId)
                    .orElseThrow(() -> new SecurityException("Access denied: you are not a member of this community."));
            if (member.canModerate() || communityRepo.findById(communityId).map(c -> c.isOwnedBy(userId)).orElse(false)) {
                isAdmin = true;
            } else {
                throw new SecurityException("You do not have permission to delete this message");
            }
        }

        // Apply Soft-Delete
        message.setDeleted(true);
        message.setDeletedByType(isAdmin && !isOwner ? "ADMINISTRATOR" : "USER");
        communityMessageRepo.save(message);

        // Broadcast Real-time WebSocket Event to all community chat participants
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", messageId);
        payload.put("communityId", communityId);
        payload.put("isDeleted", true);
        payload.put("deletedByType", message.getDeletedByType());
        messagingTemplate.convertAndSend("/topic/community." + communityId + ".messages", payload);
    }

    /**
     * Updates chat settings in a single transaction and sends system messages for each change.
     */
    public void updateChatSettings(Long communityId, Long userId, Boolean enabled, Integer days) {
        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, userId);
        
        User sender = userRepo.findById(userId).orElse(community.getOwner());
        if (sender == null) {
            throw new ValidationException("Cannot update settings: No valid admin found.");
        }

        // 1. Handle group chat permission toggle
        if (enabled != null) {
            if (community.getIsGroupChatEnabled() == null || !community.getIsGroupChatEnabled().equals(enabled)) {
                community.setIsGroupChatEnabled(enabled);
                String permissionText = enabled ? "Group chat was enabled by the administrator." 
                                                : "Group chat was disabled by the administrator.";
                createAndBroadcastSystemMessage(communityId, sender, permissionText);
            }
        } else if (community.getIsGroupChatEnabled() == null) {
            community.setIsGroupChatEnabled(true); // default fallback
        }

        // 2. Handle retention days
        if (days != null) {
            if (days < 0) throw new ValidationException("Retention period cannot be negative.");
            if (days > 0 && !planEnforcementService.canSetDisappearingMessages(userId)) {
                throw new com.JanSahayak.AI.exception.PlanLimitExceededException("Disappearing messages is a Govlyx VIP feature.");
            }
            if (community.getChatRetentionDays() == null || !community.getChatRetentionDays().equals(days)) {
                community.setChatRetentionDays(days);
                String retentionText = days == 0 ? "Disappearing messages turned off." 
                                              : "Disappearing messages set to auto-delete after " + days + " days.";
                createAndBroadcastSystemMessage(communityId, sender, retentionText);
            }
        } else if (community.getChatRetentionDays() == null) {
            community.setChatRetentionDays(0); // default fallback
        }

        communityRepo.save(community);
        log.info("Updated chat settings for community {}: isGroupChatEnabled={}, chatRetentionDays={}", 
                communityId, community.getIsGroupChatEnabled(), community.getChatRetentionDays());
    }

    /**
     * Helper method to create, save and broadcast a system message to the chat.
     */
    private void createAndBroadcastSystemMessage(Long communityId, User sender, String text) {
        CommunityMessage systemMsg = CommunityMessage.builder()
                .communityId(communityId)
                .sender(sender)
                .content(text)
                .messageType(CommunityMessage.MessageType.SYSTEM)
                .build();
        communityMessageRepo.save(systemMsg);
        messagingTemplate.convertAndSend("/topic/community." + communityId + ".messages", 
                CommunityMessageDto.fromEntity(systemMsg));
    }

    /**
     * Scheduled database pruner. Runs hourly to purge expired messages from memory and storage.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void purgeExpiredChatMessages() {
        log.info("[Pruning] Running community chat auto-delete scheduled job...");
        Instant now = Instant.now();
        int snapshotsDeleted = communitySharedPostSnapshotRepo.deleteSnapshotsOfExpiredMessages(now);
        int deletedCount = communityMessageRepo.deleteExpiredMessages(now);
        if (deletedCount > 0 || snapshotsDeleted > 0) {
            log.info("[Pruning] Successfully deleted {} expired community chat messages and {} shared post snapshots.", deletedCount, snapshotsDeleted);
        }
    }

    // ── Authorization Helpers ──────────────────────────────────────────────────

    private void assertReadAccess(Community community, Long userId) {
        if (community.isPublic()) return;
        // PRIVATE or SECRET communities require active membership to read chat
        if (userId == null || !communityMemberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(community.getId(), userId)) {
            throw new SecurityException("Access denied: you must be a member to read this chat.");
        }
    }

    private CommunityMember findActiveMemberOrThrow(Long communityId, Long userId) {
        CommunityMember member = communityMemberRepo.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new SecurityException("Access denied: you are not a member of this community."));
        
        if (!Boolean.TRUE.equals(member.getIsActive())) {
            throw new SecurityException("Your membership is currently inactive.");
        }
        if (Boolean.TRUE.equals(member.getIsBanned())) {
            throw new SecurityException("You have been banned from this community.");
        }
        return member;
    }

    private void assertAdminOrOwner(Community community, Long userId) {
        if (community.isOwnedBy(userId)) return;
        CommunityMember m = communityMemberRepo.findByCommunityIdAndUserId(community.getId(), userId)
                .orElseThrow(() -> new SecurityException("Access denied: you are not a member of this community."));
        if (!m.isAdmin()) {
            throw new SecurityException("Only community admins can perform this action.");
        }
    }

    private void assertModeratorOrAbove(Community community, Long userId) {
        if (community.isOwnedBy(userId)) return;
        CommunityMember m = communityMemberRepo.findByCommunityIdAndUserId(community.getId(), userId)
                .orElseThrow(() -> new SecurityException("Access denied: you are not a member of this community."));
        if (!m.canModerate()) {
            throw new SecurityException("Only moderators or admins can perform this action.");
        }
    }

    private Community findCommunityOrThrow(Long id) {
        return communityRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Community not found: " + id));
    }

    /**
     * Reports a chat message. If it receives 3+ reports, it gets auto-flagged/hidden,
     * and a removal instruction is broadcasted.
     */
    public void reportMessage(Long communityId, Long messageId, Long reporterId, ReportCategory category, String description) {
        Community community = findCommunityOrThrow(communityId);
        User reporter = userRepo.findById(reporterId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + reporterId));
        
        CommunityMessage message = communityMessageRepo.findById(messageId)
                .orElseThrow(() -> new NoSuchElementException("Message not found: " + messageId));

        if (!communityId.equals(message.getCommunityId())) {
            throw new IllegalArgumentException("Message does not belong to this community.");
        }

        // Verify reporter is a member of the community
        findActiveMemberOrThrow(communityId, reporterId);

        // Deduplicate reports
        if (contentReportRepository.existsByReporter_IdAndTargetTypeAndTargetId(reporterId, "COMMUNITY_MESSAGE", messageId)) {
            throw new ValidationException("You have already reported this message.");
        }

        // Create ContentReport
        ContentReport report = ContentReport.builder()
                .reporter(reporter)
                .targetType("COMMUNITY_MESSAGE")
                .targetId(messageId)
                .category(category)
                .description(description)
                .status("PENDING")
                .build();
        contentReportRepository.save(report);

        // Increment report count
        message.setReportCount(message.getReportCount() + 1);

        if (message.getReportCount() == 3) {
            message.setFlagged(true);
            communityMessageRepo.save(message);

            // Broadcast real-time deletion system event
            java.util.Map<String, Object> deletionNotification = new java.util.HashMap<>();
            deletionNotification.put("id", messageId);
            deletionNotification.put("communityId", communityId);
            deletionNotification.put("isDeleted", true);
            deletionNotification.put("reason", "Removed due to community reports.");

            messagingTemplate.convertAndSend("/topic/community." + communityId + ".messages", deletionNotification);
            log.info("[Security] Chat message {} auto-flagged/hidden due to report threshold.", messageId);
        } else {
            communityMessageRepo.save(message);
        }
    }
}
