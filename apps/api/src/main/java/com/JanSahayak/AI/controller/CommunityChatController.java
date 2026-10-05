package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.CommunityMessageDto;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.service.CommunityChatService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/communities")
@RequiredArgsConstructor
@Slf4j
public class CommunityChatController {

    private final CommunityChatService communityChatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.JanSahayak.AI.repository.CommunityRepo communityRepo;

    // ── REST Endpoints ────────────────────────────────────────────────────────

    /**
     * GET /api/communities/{id}/chat/messages
     * Fetches message history using cursor-based pagination.
     */
    @GetMapping("/{id}/chat/messages")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<CommunityMessageDto>>> getChatHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            List<CommunityMessageDto> history = communityChatService.getChatHistory(id, user.getId(), cursor, limit);
            return ResponseEntity.ok(ApiResponse.success("Chat history retrieved", history));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access Denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving chat history for community {}", id, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error", "Failed to retrieve history"));
        }
    }

    /**
     * GET /api/communities/{id}/chat/settings
     * Retrieves chat settings (permission toggle and message retention).
     */
    @GetMapping("/{id}/chat/settings")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChatSettings(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        try {
            com.JanSahayak.AI.model.Community community = communityRepo.findById(id)
                    .orElseThrow(() -> new java.util.NoSuchElementException("Community not found: " + id));
            
            Map<String, Object> settings = new HashMap<>();
            settings.put("isGroupChatEnabled", community.getIsGroupChatEnabled() != null ? community.getIsGroupChatEnabled() : true);
            settings.put("chatRetentionDays", community.getChatRetentionDays() != null ? community.getChatRetentionDays() : 0);
            return ResponseEntity.ok(ApiResponse.success("Chat settings retrieved", settings));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access Denied", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve settings", e.getMessage()));
        }
    }

    /**
     * PUT /api/communities/{id}/chat/settings
     * Updates group chat configurations (disappearing timer and active toggle state).
     */
    @PutMapping("/{id}/chat/settings")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateChatSettings(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @RequestBody SettingsRequest req) {
        try {
            communityChatService.updateChatSettings(id, user.getId(), req.getIsGroupChatEnabled(), req.getChatRetentionDays());
            return ResponseEntity.ok(ApiResponse.success("Settings updated successfully", null));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access Denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating chat settings for community {}", id, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Update failed", e.getMessage()));
        }
    }

    /**
     * POST /api/communities/{id}/chat/messages/{messageId}/pin
     * Toggles the pinned status of a message.
     */
    @PostMapping("/{id}/chat/messages/{messageId}/pin")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CommunityMessageDto>> toggleMessagePin(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @AuthenticationPrincipal User user) {
        try {
            CommunityMessageDto updated = communityChatService.toggleMessagePin(id, messageId, user.getId());
            return ResponseEntity.ok(ApiResponse.success(
                    updated.isPinned() ? "Message pinned successfully" : "Message unpinned successfully",
                    updated
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access Denied", e.getMessage()));
        } catch (com.JanSahayak.AI.exception.PlanLimitExceededException e) {
            return ResponseEntity.status(403).body(ApiResponse.error("VIP Feature Required", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to toggle pin for message {} in community {}", messageId, id, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Error", "Failed to pin/unpin message"));
        }
    }

    /**
     * GET /api/communities/{id}/chat/pinned
     * Retrieves currently pinned messages for the community chat.
     */
    @GetMapping("/{id}/chat/pinned")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<CommunityMessageDto>>> getPinnedMessages(
            @PathVariable Long id) {
        try {
            List<CommunityMessageDto> pinned = communityChatService.getPinnedMessages(id);
            return ResponseEntity.ok(ApiResponse.success("Pinned messages retrieved", pinned));
        } catch (Exception e) {
            log.error("Failed to retrieve pinned messages for community {}", id, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error", "Failed to retrieve pinned messages"));
        }
    }

    /**
     * DELETE /api/communities/{id}/chat/messages/{messageId}
     * Soft-deletes a message from the community chat.
     */
    @DeleteMapping("/{id}/chat/messages/{messageId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @AuthenticationPrincipal User user) {
        try {
            communityChatService.deleteMessage(id, messageId, user.getId());
            return ResponseEntity.ok(ApiResponse.success("Message deleted successfully", null));
        } catch (SecurityException e) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Access Denied", e.getMessage()));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("Not Found", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete message {} in community {}", messageId, id, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Server Error", "Failed to delete message"));
        }
    }

    // ── WebSocket Endpoint Handlers ───────────────────────────────────────────

    /**
     * Handles new incoming message events.
     * Receives messages over STOMP from `/app/community.{communityId}.send`.
     */
    @MessageMapping("/community.{communityId}.send")
    public void sendMessage(
            @DestinationVariable Long communityId,
            @Payload SendMessageRequest payload,
            Principal principal) {
        
        User user = getUserFromPrincipal(principal);
        if (user == null) {
            log.warn("WS send rejected: Could not extract user from principal");
            return;
        }

        try {
            communityChatService.processNewMessage(
                    communityId, 
                    user.getId(), 
                    payload.getContent(), 
                    payload.getSharedPostId(), 
                    payload.getReplyToId()
            );
        } catch (Exception e) {
            log.error("Error processing WS community message from user {} in community {}", user.getId(), communityId, e);
            // Send error notification directly back to the specific user via their personal queue
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage());
            messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/errors", err);
        }
    }

    /**
     * Broadcasts typing events to `/topic/community.{communityId}.typing`.
     */
    @MessageMapping("/community.{communityId}.typing")
    public void sendTyping(
            @DestinationVariable Long communityId,
            Principal principal) {
        
        User user = getUserFromPrincipal(principal);
        if (user == null) return;

        Map<String, Object> typingPayload = new HashMap<>();
        typingPayload.put("userId", user.getId());
        typingPayload.put("username", user.getActualUsername());
        typingPayload.put("typing", true);

        messagingTemplate.convertAndSend("/topic/community." + communityId + ".typing", typingPayload);
    }

    /**
     * POST /api/communities/{id}/chat/messages/{messageId}/report
     * Reports a message for content violations.
     */
    @PostMapping("/{id}/chat/messages/{messageId}/report")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> reportMessage(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @AuthenticationPrincipal User user,
            @RequestBody ReportRequest req) {
        try {
            if (req.getCategory() == null || req.getCategory().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Report failed", "Category is required"));
            }
            
            com.JanSahayak.AI.enums.ReportCategory categoryEnum;
            try {
                categoryEnum = com.JanSahayak.AI.enums.ReportCategory.valueOf(req.getCategory().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Report failed", "Invalid category: " + req.getCategory()));
            }

            communityChatService.reportMessage(id, messageId, user.getId(), categoryEnum, req.getDescription());
            return ResponseEntity.ok(ApiResponse.success("Message reported successfully", null));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access Denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Error reporting chat message {} in community {}", messageId, id, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Report failed", e.getMessage()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User getUserFromPrincipal(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            Object p = auth.getPrincipal();
            if (p instanceof User u) return u;
        }
        return null;
    }

    // ── Request Payloads ──────────────────────────────────────────────────────

    @Data
    public static class SettingsRequest {
        private Boolean isGroupChatEnabled;
        private Integer chatRetentionDays;
    }

    @Data
    public static class SendMessageRequest {
        private String content;
        private Long sharedPostId;
        private Long replyToId;
    }

    @Data
    public static class ReportRequest {
        private String category;
        private String description;
    }
}
