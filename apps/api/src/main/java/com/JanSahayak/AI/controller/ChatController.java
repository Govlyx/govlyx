package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.ChatMessageDto;
import com.JanSahayak.AI.dto.ChatSessionDto;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.ChatMessage;
import com.JanSahayak.AI.model.ChatSession;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.service.ChatMessagingService;
import com.JanSahayak.AI.service.ChatSessionService;
import com.JanSahayak.AI.service.MatchmakingService;
import com.JanSahayak.AI.service.WebRtcSignalingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.validation.Valid;

/**
 * Handles all chat operations:
 *
 *  ── Original WebSocket handlers (unchanged) ───────────────────────────────
 *  /app/chat.send      — send a text message
 *  /app/chat.typing    — send typing indicator
 *
 *  ── Original REST endpoints (unchanged) ───────────────────────────────────
 *  POST   /api/chat/search          — find / join a match
 *  POST   /api/chat/search/cancel   — cancel matchmaking
 *  GET    /api/chat/session         — get current session
 *  GET    /api/chat/online-count    — active users + queue size
 *  POST   /api/chat/session/leave   — leave current session
 *  GET    /api/chat/messages        — get recent text messages
 *  GET    /api/chat/admin/statistics— admin stats
 *
 *  ── New media REST endpoints ─────────────────────────────────────────────
 *  POST   /api/chat/{sessionId}/media          — send image / video / sticker /
 *                                                voice note (with optional timer)
 *
 *  ── New WebRTC signaling REST endpoints ──────────────────────────────────
 *  POST   /api/chat/{sessionId}/call/offer     — initiate voice / video call
 *  POST   /api/chat/{sessionId}/call/answer    — accept incoming call
 *  POST   /api/chat/{sessionId}/call/ice       — relay ICE candidate
 *  POST   /api/chat/{sessionId}/call/end       — end / reject call
 *
 * ── Why media goes via REST and not STOMP @MessageMapping ─────────────────
 * Images and video clips can be several MB.  STOMP frames have a practical
 * size ceiling and buffering them in the broker wastes heap.  REST POST gives
 * us chunked transfer, a clean 413 / 400 path for oversized payloads, and
 * keeps the WebSocket channel free for low-latency control messages.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final MatchmakingService     matchmakingService;
    private final ChatSessionService     chatSessionService;
    private final ChatMessagingService   chatMessagingService;
    private final WebRtcSignalingService webRtcSignalingService;
    private final com.JanSahayak.AI.service.PlanEnforcementService planEnforcementService;

    // ── WebSocket handlers ────────────────────────────────────────────────────
    // @AuthenticationPrincipal does NOT work in @MessageMapping handlers.
    // Use Principal — Spring always injects Authentication there for STOMP.

    @MessageMapping("/chat.send")
    public void sendMessage(
            @Payload ChatMessageDto messageDto,
            Principal principal) {

        User user = getUserFromPrincipal(principal);
        if (user == null) {
            log.warn("chat.send: could not resolve user from principal");
            return;
        }
        if (messageDto.getContent() == null || messageDto.getContent().isBlank()) return;

        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session == null || !session.isActive()) {
                log.warn("chat.send from user {} but no active session", user.getId());
                return;
            }
            ChatMessage stored = chatSessionService.addMessage(
                    session.getSessionId(), user.getId(), messageDto.getContent(), messageDto.getReplyToId());
            chatMessagingService.sendMessageToSession(session.getSessionId(), stored);
        } catch (Exception e) {
            log.error("Error processing message from user {}", user.getId(), e);
        }
    }

    @MessageMapping("/chat.typing")
    public void sendTypingIndicator(Principal principal) {
        User user = getUserFromPrincipal(principal);
        if (user == null) return;

        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session != null && session.isActive()) {
                chatMessagingService.sendTypingIndicator(session.getSessionId(), user.getId());
            }
        } catch (Exception e) {
            log.error("Error processing typing indicator from user {}", user.getId(), e);
        }
    }

    @MessageMapping("/chat.seen")
    public void sendSeenReceipt(@Payload Map<String, String> payload, Principal principal) {
        User user = getUserFromPrincipal(principal);
        if (user == null) return;

        String messageId = payload.get("messageId");
        if (messageId == null || messageId.isBlank()) return;

        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session != null && session.isActive()) {
                boolean updated = chatSessionService.markMessageAsSeen(session.getSessionId(), messageId);
                if (updated) {
                    chatMessagingService.sendSeenReceipt(session.getSessionId(), user.getId(), messageId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing seen receipt from user {}", user.getId(), e);
        }
    }

    @MessageMapping("/chat.delivered")
    public void sendDeliveredReceipt(@Payload Map<String, String> payload, Principal principal) {
        User user = getUserFromPrincipal(principal);
        if (user == null) return;

        String messageId = payload.get("messageId");
        if (messageId == null || messageId.isBlank()) return;

        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session != null && session.isActive()) {
                boolean updated = chatSessionService.markMessageAsDelivered(session.getSessionId(), messageId);
                if (updated) {
                    chatMessagingService.sendDeliveredReceipt(session.getSessionId(), user.getId(), messageId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing delivered receipt from user {}", user.getId(), e);
        }
    }

    // ── Original REST endpoints ───────────────────────────────────────────────

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startSearch(
            @AuthenticationPrincipal User user) {
        try {
            log.info("User {} starting chat search", user.getId());
            if (chatSessionService.isUserInSession(user.getId())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<Map<String, Object>>error(
                                "Already in session", "You are already in an active chat session"));
            }
            String sessionId = matchmakingService.findMatch(user);
            Map<String, Object> response = new HashMap<>();
            if (sessionId != null) {
                ChatSession session = chatSessionService.getSession(sessionId);
                chatMessagingService.notifyMatchFound(sessionId, session.getUser1Id(), session.getUser2Id());
                response.put("matched", true);
                response.put("sessionId", sessionId);
                response.put("session", convertToDto(session, user.getId()));
                return ResponseEntity.ok(ApiResponse.success("Match found!", response));
            }
            response.put("matched", false);
            response.put("searching", true);
            response.put("queueSize", matchmakingService.getQueueSize());
            return ResponseEntity.ok(ApiResponse.success("Searching for a chat partner...", response));
        } catch (Exception e) {
            log.error("Error starting chat search for user {}", user.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>error(
                            "Search failed", "Failed to start search: " + e.getMessage()));
        }
    }

    @PostMapping("/search/cancel")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelSearch(
            @AuthenticationPrincipal User user) {
        try {
            matchmakingService.cancelSearch(user.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("cancelled", true);
            return ResponseEntity.ok(ApiResponse.success("Search cancelled", response));
        } catch (Exception e) {
            log.error("Error cancelling search for user {}", user.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>error("Cancel failed", "Failed to cancel search"));
        }
    }

    @GetMapping("/session")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ChatSessionDto>> getCurrentSession(
            @AuthenticationPrincipal User user) {
        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session == null) {
                return ResponseEntity.ok(ApiResponse.success("No active session", null));
            }
            return ResponseEntity.ok(ApiResponse.success("Active session", convertToDto(session, user.getId())));
        } catch (Exception e) {
            log.error("Error getting session for user {}", user.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<ChatSessionDto>error("Get session failed", "Failed to get session"));
        }
    }

    @GetMapping("/online-count")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOnlineCount() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("onlineCount", chatSessionService.getOnlineUserCount());
            response.put("queueSize", matchmakingService.getQueueSize());
            return ResponseEntity.ok(ApiResponse.success("Online count", response));
        } catch (Exception e) {
            log.error("Error getting online count", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>error("Count failed", "Failed to get count"));
        }
    }

    @PostMapping("/session/leave")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> leaveSession(
            @AuthenticationPrincipal User user) {
        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<Map<String, Object>>error(
                                "No active session", "No active session to leave"));
            }
            chatSessionService.endSession(session.getSessionId(), user.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("left", true);
            return ResponseEntity.ok(ApiResponse.success("Left chat session", response));
        } catch (Exception e) {
            log.error("Error leaving session for user {}", user.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>error("Leave failed", "Failed to leave session"));
        }
    }

    @GetMapping("/messages")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<ChatMessageDto>>> getMessages(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<List<ChatMessageDto>>error("No active session", "No active session"));
            }
            List<ChatMessage> messages = chatSessionService.getRecentMessages(
                    session.getSessionId(), user.getId(), limit);
            List<ChatMessageDto> dtos = messages.stream()
                    .map(this::convertMessageToDto)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Messages retrieved", dtos));
        } catch (Exception e) {
            log.error("Error getting messages for user {}", user.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<List<ChatMessageDto>>error("Get messages failed", "Failed to get messages"));
        }
    }

    @GetMapping("/admin/statistics")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChatStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("activeSessions", chatSessionService.getActiveSessionCount());
            stats.put("queueSize", matchmakingService.getQueueSize());
            stats.put("timestamp", java.time.Instant.now());
            return ResponseEntity.ok(ApiResponse.success("Chat statistics", stats));
        } catch (Exception e) {
            log.error("Error getting chat statistics", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>error("Statistics failed", "Failed to get statistics"));
        }
    }

    // ── NEW: Rich media endpoints ─────────────────────────────────────────────

    /**
     * POST /api/chat/{sessionId}/media
     *
     * Send a rich-media message: image, video, sticker, or voice note.
     * Optionally include a Telegram-style self-destruct timer or view-once flag.
     *
     * Request body:
     * {
     *   "type":         "IMAGE" | "VIDEO" | "STICKER" | "VOICE_NOTE",
     *   "mediaPayload": "data:image/jpeg;base64,...",   // base64 data-URI
     *   "mimeType":     "image/jpeg",                   // MIME hint
     *   "mediaName":    "sunset.jpg",                   // optional
     *   "viewTimer":    10,                             // optional: 0–60 seconds (0 = no timer)
     *   "viewOnce":     false                           // optional: true = view once like Telegram
     * }
     *
     * Returns: the generated messageId and fileId.
     *
     * ── Timer behaviour ──────────────────────────────────────────────────────
     *  viewTimer > 0  → receiver sees a countdown starting from first open;
     *                   client destroys the media data when it hits zero.
     *  viewOnce == true → media is destroyed immediately on first open;
     *                     takes precedence over viewTimer on the client side.
     *  Both false/0   → media shows normally for the life of the chat session
     *                   (which is itself ephemeral — never stored on server).
     *
     * ── Storage guarantee ────────────────────────────────────────────────────
     * Media is NEVER written to disk, database, or session.recentMessages.
     * It exists only as an in-flight WebSocket frame.
     */
    @PostMapping(value = "/{sessionId}/media", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendMedia(
            @PathVariable String sessionId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("type") String typeRaw,
            @RequestParam(value = "viewTimer", defaultValue = "0") int viewTimer,
            @RequestParam(value = "viewOnce", defaultValue = "false") boolean viewOnce,
            @RequestParam(value = "replyToId", required = false) String replyToId,
            @AuthenticationPrincipal User user) {
        try {
            ChatMessage.MessageType type = parseMediaType(typeRaw);

            if (type != ChatMessage.MessageType.STICKER) {
                if (!planEnforcementService.canSendChatMedia(user.getId(), sessionId)) {
                    throw new RuntimeException("Upgrade to Govlyx Pro or VIP to send rich media.");
                }
            }

            // Clamp timer to valid range (0–60 s)
            viewTimer = Math.max(0, Math.min(viewTimer, 60));

            // Guard against large files BEFORE loading them into memory (50 MB limit)
            if (file.getSize() > 50L * 1024 * 1024) {
                throw new RuntimeException("Media payload exceeds 50 MB limit");
            }

            ChatMessage msg = chatSessionService.addMediaMessage(
                    sessionId,
                    user.getId(),
                    type,
                    file.getBytes(),
                    file.getContentType(),
                    file.getOriginalFilename(),
                    viewTimer,
                    viewOnce,
                    replyToId);

            chatMessagingService.sendMediaToSession(sessionId, msg);
            
            if (type != ChatMessage.MessageType.STICKER) {
                chatSessionService.markMediaUsedInAudit(sessionId, user.getId());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("messageId", msg.getMessageId());
            response.put("fileId", msg.getMediaPayload());
            response.put("viewTimer", viewTimer);
            response.put("viewOnce", viewOnce);
            return ResponseEntity.ok(ApiResponse.success("Media sent", response));

        } catch (RuntimeException e) {
            log.error("Error sending media in session {} for user {}", sessionId, user.getId(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Map<String, Object>>error("Media send failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error sending media in session {}", sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>error("Media send failed", "Internal error"));
        }
    }

    @GetMapping("/{sessionId}/media/{fileId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<byte[]> getMedia(
            @PathVariable String sessionId,
            @PathVariable String fileId,
            @AuthenticationPrincipal User user) {
        
        byte[] data = chatSessionService.getMedia(fileId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Use general octet stream or try to guess MIME type. Browsers generally handle octet stream.
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @DeleteMapping("/{sessionId}/media/{fileId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable String sessionId,
            @PathVariable String fileId,
            @AuthenticationPrincipal User user) {
        
        boolean deleted = chatSessionService.deleteMedia(fileId);
        if (deleted) {
            try {
                // Broadcast to both users that the media has been wiped from RAM
                chatMessagingService.notifyMediaWiped(sessionId, fileId);
            } catch (Exception e) {
                log.error("Failed to notify users that media {} was wiped", fileId, e);
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Media wiped", null));
    }

    // ── NEW: WebRTC signaling endpoints ───────────────────────────────────────

    /**
     * POST /api/chat/{sessionId}/call/offer
     *
     * Initiate a voice or video call.
     *
     * Body: { "callType": "VOICE" | "VIDEO", "sdp": "<SDP offer JSON>" }
     */
    @PostMapping("/{sessionId}/call/offer")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> callOffer(
            @PathVariable String sessionId,
            @Valid @RequestBody CallOfferRequest req,
            @AuthenticationPrincipal User user) {
        try {
            WebRtcSignalingService.CallType callType =
                    WebRtcSignalingService.CallType.valueOf(req.getCallType().toUpperCase());
            webRtcSignalingService.initiateCall(sessionId, user.getId(), callType, req.getSdp());
            return ResponseEntity.ok(ApiResponse.success("Call offer sent", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Void>error("Invalid call type", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Error sending call offer in session {} for user {}", sessionId, user.getId(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Void>error("Call offer failed", e.getMessage()));
        }
    }

    /**
     * POST /api/chat/{sessionId}/call/answer
     *
     * Accept an incoming call.
     *
     * Body: { "sdp": "<SDP answer JSON>" }
     */
    @PostMapping("/{sessionId}/call/answer")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> callAnswer(
            @PathVariable String sessionId,
            @Valid @RequestBody SdpRequest req,
            @AuthenticationPrincipal User user) {
        try {
            webRtcSignalingService.acceptCall(sessionId, user.getId(), req.getSdp());
            return ResponseEntity.ok(ApiResponse.success("Call answer sent", null));
        } catch (RuntimeException e) {
            log.error("Error answering call in session {} for user {}", sessionId, user.getId(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Void>error("Call answer failed", e.getMessage()));
        }
    }

    /**
     * POST /api/chat/{sessionId}/call/ice
     *
     * Relay an ICE candidate to the partner.
     *
     * Body: { "candidate": "<RTCIceCandidate JSON>" }
     */
    @PostMapping("/{sessionId}/call/ice")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> iceCandidate(
            @PathVariable String sessionId,
            @Valid @RequestBody IceRequest req,
            @AuthenticationPrincipal User user) {
        try {
            webRtcSignalingService.relayIceCandidate(sessionId, user.getId(), req.getCandidate());
            return ResponseEntity.ok(ApiResponse.success("ICE candidate relayed", null));
        } catch (RuntimeException e) {
            log.error("Error relaying ICE in session {} for user {}", sessionId, user.getId(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Void>error("ICE relay failed", e.getMessage()));
        }
    }

    /**
     * POST /api/chat/{sessionId}/call/end
     *
     * End or reject the active call.  No request body needed.
     */
    @PostMapping("/{sessionId}/call/end")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> endCall(
            @PathVariable String sessionId,
            @AuthenticationPrincipal User user) {
        try {
            webRtcSignalingService.endCall(sessionId, user.getId());
            return ResponseEntity.ok(ApiResponse.success("Call ended", null));
        } catch (RuntimeException e) {
            log.error("Error ending call in session {} for user {}", sessionId, user.getId(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Void>error("End call failed", e.getMessage()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts User from the STOMP Principal.
     * WebSocketConfig stores: new UsernamePasswordAuthenticationToken(user, null, authorities)
     * So: Principal → UsernamePasswordAuthenticationToken → getPrincipal() → User
     */
    private User getUserFromPrincipal(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            Object p = auth.getPrincipal();
            if (p instanceof User u) return u;
        }
        log.warn("Could not extract User from principal: {}",
                principal != null ? principal.getClass().getName() : "null");
        return null;
    }

    private ChatSessionDto convertToDto(ChatSession session, Long userId) {
        return ChatSessionDto.builder()
                .sessionId(session.getSessionId())
                .yourAnonymousId(session.getUserAnonymousId(userId))
                .partnerAnonymousId(session.getPartnerAnonymousId(userId))
                .status(session.getStatus().name())
                .createdAt(session.getCreatedAt())
                .lastActivityAt(session.getLastActivityAt())
                .build();
    }

    private ChatMessageDto convertMessageToDto(ChatMessage message) {
        return ChatMessageDto.builder()
                .messageId(message.getMessageId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .timestamp(message.getTimestamp())
                .delivered(message.isDelivered())
                .seen(message.isSeen())
                // Media messages are never in recentMessages, so these will always be null/0/false
                // for history responses — but included for completeness in case that changes.
                .mediaPayload(message.getMediaPayload())
                .mimeType(message.getMimeType())
                .mediaName(message.getMediaName())
                .viewTimer(message.getViewTimer())
                .viewOnce(message.isViewOnce())
                .replyToId(message.getReplyToId())
                .build();
    }

    private ChatMessage.MessageType parseMediaType(String raw) {
        if (raw == null) throw new RuntimeException("Media type is required");
        try {
            ChatMessage.MessageType type = ChatMessage.MessageType.valueOf(raw.toUpperCase());
            if (type != ChatMessage.MessageType.IMAGE
                    && type != ChatMessage.MessageType.VIDEO
                    && type != ChatMessage.MessageType.STICKER
                    && type != ChatMessage.MessageType.VOICE_NOTE) {
                throw new RuntimeException("Not a media type: " + raw + ". Allowed: IMAGE, VIDEO, STICKER, VOICE_NOTE");
            }
            return type;
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown media type: " + raw + ". Allowed: IMAGE, VIDEO, STICKER, VOICE_NOTE");
        }
    }

    // ── Request body DTOs ─────────────────────────────────────────────────────

    /**
     * Request body for POST /{sessionId}/media
     *
     * viewTimer: 0–60 (seconds). 0 = no timer (default).
     * viewOnce:  true = destroy on first open (Telegram "⊕ 1" mode).
     */
    @lombok.Data
    public static class MediaRequest {
        /** "IMAGE", "VIDEO", "STICKER", or "VOICE_NOTE" */
        private String  type;
        /** base64 data-URI, e.g. "data:image/jpeg;base64,..." */
        private String  mediaPayload;
        /** MIME type, e.g. "image/jpeg", "video/mp4" */
        private String  mimeType;
        /** Optional display name, e.g. "clip.mp4" */
        private String  mediaName;
        /** 0 = no timer; 1–60 = seconds visible after first open */
        private int     viewTimer  = 0;
        /** true = view-once mode (destroy on first open) */
        private boolean viewOnce   = false;
        /** Optional: reference to another messageId */
        private String  replyToId;
    }

    @lombok.Data
    public static class CallOfferRequest {
        /** "VOICE" or "VIDEO" */
        @jakarta.validation.constraints.NotBlank(message = "Call type is required")
        private String callType;
        /** SDP offer JSON from RTCPeerConnection */
        @jakarta.validation.constraints.NotBlank(message = "SDP is required")
        private String sdp;
    }

    @lombok.Data
    public static class SdpRequest {
        /** SDP answer JSON from RTCPeerConnection */
        @jakarta.validation.constraints.NotBlank(message = "SDP is required")
        private String sdp;
    }

    @lombok.Data
    public static class IceRequest {
        /** RTCIceCandidate JSON string */
        @jakarta.validation.constraints.NotBlank(message = "ICE candidate is required")
        private String candidate;
    }
}
