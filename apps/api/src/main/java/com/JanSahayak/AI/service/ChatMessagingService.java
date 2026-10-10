package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.ChatMessageDto;
import com.JanSahayak.AI.model.ChatMessage;
import com.JanSahayak.AI.model.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends real-time events via WebSocket (STOMP).
 *
 * ── Routing rule ──────────────────────────────────────────────────────────────
 * WebSocketConfig sets the STOMP principal as:
 *   new UsernamePasswordAuthenticationToken(user, ...)
 * so Authentication.getName() == user.getUsername() == EMAIL.
 *
 * convertAndSendToUser(email, dest, payload) routes to /user/{email}/queue/...
 * We MUST pass getUserEmail(userId), NOT userId.toString().
 *
 * ── Message-ID consistency ────────────────────────────────────────────────────
 * sendMessageToSession() receives the ChatMessage already persisted by
 * ChatSessionService.addMessage() and reuses its messageId in the DTO.
 * This guarantees the WebSocket echo and the REST history endpoint
 * (/api/chat/messages) always carry the SAME messageId.
 *
 * ── Rich media relay ─────────────────────────────────────────────────────────
 * sendMediaToSession() forwards the transient ChatMessage produced by
 * ChatSessionService.addMediaMessage() to both participants.
 * The mediaPayload (base64 data-URI) travels inside ChatMessageDto and is
 * NEVER written to any store — it lives only as a WebSocket frame.
 * Timed / view-once metadata (viewTimer, viewOnce) is relayed as-is;
 * enforcement is entirely client-side (matching Telegram's design).
 *
 * ── WebRTC signaling ──────────────────────────────────────────────────────────
 * relaySignal() forwards CALL_OFFER / CALL_ANSWER / CALL_ICE / CALL_ENDED to
 * the partner only (sender does NOT receive an echo).  Audio/video streams
 * are peer-to-peer and never touch this server.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessagingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatSessionService    chatSessionService;

    private static final String DEST_MESSAGES = "/queue/messages";
    private static final String DEST_MATCH    = "/queue/match";
    private static final String DEST_TYPING   = "/queue/typing";
    private static final String DEST_CALL     = "/queue/call";

    // ── Text messages ─────────────────────────────────────────────────────────

    /**
     * Send a TEXT message to both users in the session.
     *
     * @param storedMessage ChatMessage already added via ChatSessionService.addMessage().
     *                      Reusing its messageId keeps echo and history in sync.
     */
    public void sendMessageToSession(String sessionId, ChatMessage storedMessage) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) {
            log.warn("sendMessageToSession: session {} not found", sessionId);
            return;
        }

        ChatMessageDto dto = ChatMessageDto.builder()
                .messageId(storedMessage.getMessageId())
                .senderId(storedMessage.getSenderId())
                .content(storedMessage.getContent())
                .messageType(storedMessage.getMessageType().name())
                .timestamp(storedMessage.getTimestamp())
                .replyToId(storedMessage.getReplyToId())
                .build();

        sendToUser(session.getUser1Id(), dto);
        sendToUser(session.getUser2Id(), dto);

        log.debug("Text message {} delivered in session {}", storedMessage.getMessageId(), sessionId);
    }

    // ── Rich media relay (IMAGE / VIDEO / STICKER / VOICE_NOTE) ──────────────

    /**
     * Relay a rich-media message (IMAGE, VIDEO, STICKER, VOICE_NOTE) to both
     * session participants.
     *
     * The {@code mediaPayload} field (base64 data-URI), {@code viewTimer}, and
     * {@code viewOnce} are forwarded as-is inside the DTO.  Nothing is stored.
     * This method is the only stop between sender and recipient.
     *
     * Caller: invoke this immediately after ChatSessionService.addMediaMessage()
     * returns — pass the returned ChatMessage object directly.
     *
     * @param sessionId    active session ID
     * @param mediaMessage transient ChatMessage from ChatSessionService.addMediaMessage()
     */
    public void sendMediaToSession(String sessionId, ChatMessage mediaMessage) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) {
            log.warn("sendMediaToSession: session {} not found", sessionId);
            return;
        }
        if (!session.isActive()) {
            log.warn("sendMediaToSession: session {} is not active", sessionId);
            return;
        }

        ChatMessageDto dto = ChatMessageDto.builder()
                .messageId(mediaMessage.getMessageId())
                .senderId(mediaMessage.getSenderId())
                .messageType(mediaMessage.getMessageType().name())
                .timestamp(mediaMessage.getTimestamp())
                // ── media fields ──────────────────────────────────────────────
                .mediaPayload(mediaMessage.getMediaPayload())
                .mimeType(mediaMessage.getMimeType())
                .mediaName(mediaMessage.getMediaName())
                // ── Telegram-style timer/view-once ────────────────────────────
                .viewTimer(mediaMessage.getViewTimer())
                .viewOnce(mediaMessage.isViewOnce())
                .replyToId(mediaMessage.getReplyToId())
                .build();

        sendToUser(session.getUser1Id(), dto);
        sendToUser(session.getUser2Id(), dto);

        log.info("Media {} ({}) relayed in session {} | viewTimer={}s viewOnce={}",
                mediaMessage.getMessageId(),
                mediaMessage.getMessageType(),
                sessionId,
                mediaMessage.getViewTimer(),
                mediaMessage.isViewOnce());
    }

    /**
     * Notify both participants that a media file has been wiped from RAM.
     * This triggers the frontend to replace the media bubble with a "Flame/Destroyed" icon.
     */
    public void notifyMediaWiped(String sessionId, String fileId) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) {
            log.warn("notifyMediaWiped: session {} not found", sessionId);
            return;
        }

        ChatMessageDto dto = ChatMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .senderId("SYSTEM")
                .messageType(ChatMessage.MessageType.MEDIA_WIPED.name())
                .mediaPayload(fileId) // The fileId that was destroyed
                .timestamp(Instant.now())
                .build();

        sendToUser(session.getUser1Id(), dto);
        sendToUser(session.getUser2Id(), dto);

        log.info("Broadcast MEDIA_WIPED for fileId {} in session {}", fileId, sessionId);
    }

    // ── WebRTC signaling relay ────────────────────────────────────────────────

    /**
     * Relay a WebRTC signaling message (CALL_OFFER / CALL_ANSWER / CALL_ICE /
     * CALL_ENDED) from the initiating user to their partner only.
     *
     * Sender does NOT receive an echo.  Nothing is stored anywhere.
     * The server acts purely as a STOMP signaling channel.
     *
     * @param sessionId active session ID
     * @param signal    ChatMessage carrying SDP / ICE JSON in mediaPayload
     */
    public void relaySignal(String sessionId, ChatMessage signal) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) {
            log.warn("relaySignal: session {} not found", sessionId);
            return;
        }

        // Resolve sender's real userId from their anonymousId so we can find partner
        Long senderId = resolveUserId(session, signal.getSenderId());
        if (senderId == null) {
            log.warn("relaySignal: cannot resolve userId for anonymousId={} session={}",
                    signal.getSenderId(), sessionId);
            return;
        }

        Long partnerId = session.getPartnerId(senderId);
        if (partnerId == null) {
            log.warn("relaySignal: no partner in session {}", sessionId);
            return;
        }

        ChatMessageDto dto = ChatMessageDto.builder()
                .messageId(signal.getMessageId())
                .senderId(signal.getSenderId())
                .messageType(signal.getMessageType().name())
                .mediaPayload(signal.getMediaPayload())   // SDP / ICE JSON
                .timestamp(signal.getTimestamp())
                .build();

        String partnerEmail = chatSessionService.getUserEmail(partnerId);
        if (partnerEmail != null) {
            messagingTemplate.convertAndSendToUser(partnerEmail, DEST_CALL, dto);
            log.debug("Signal {} ({}) relayed to partner userId={} session={}",
                    signal.getMessageId(), signal.getMessageType(), partnerId, sessionId);
        }
    }

    // ── Session-level notifications ───────────────────────────────────────────

    /**
     * Notify the partner when the other user leaves.
     * Must be called BEFORE endSession() marks the session ENDED.
     */
    public void notifyUserLeft(String sessionId, Long leavingUserId) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) {
            log.warn("notifyUserLeft: session {} not found", sessionId);
            return;
        }

        String leavingAnonymousId = session.getUserAnonymousId(leavingUserId);
        Long   partnerId          = session.getPartnerId(leavingUserId);
        if (partnerId == null) {
            log.warn("notifyUserLeft: no partner in session {}", sessionId);
            return;
        }

        String partnerEmail = chatSessionService.getUserEmail(partnerId);
        if (partnerEmail == null) {
            log.warn("notifyUserLeft: cannot resolve email for partner {} session {}", partnerId, sessionId);
            return;
        }

        // 1. USER_LEFT chat bubble
        ChatMessageDto leaveMsg = ChatMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .senderId("SYSTEM")
                .content("Your chat partner has left. Find someone new or exit.")
                .messageType(ChatMessage.MessageType.USER_LEFT.name())
                .timestamp(Instant.now())
                .build();
        sendToUser(partnerId, leaveMsg);

        // 2. Match-state event → switches partner UI to PARTNER_LEFT
        Map<String, Object> matchEvent = new HashMap<>();
        matchEvent.put("matched", false);
        matchEvent.put("sessionId", sessionId);
        matchEvent.put("yourAnonymousId", session.getUserAnonymousId(partnerId));
        matchEvent.put("partnerAnonymousId", leavingAnonymousId);
        messagingTemplate.convertAndSendToUser(partnerEmail, DEST_MATCH, matchEvent);

        log.info("Partner-left event sent to userId={} email={} session={}",
                partnerId, partnerEmail, sessionId);
    }

    /**
     * Notify both users that a match was found.
     */
    public void notifyMatchFound(String sessionId, Long user1Id, Long user2Id) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) {
            log.warn("notifyMatchFound: session {} not found", sessionId);
            return;
        }
        sendMatchEvent(session, user1Id, user2Id);
        sendMatchEvent(session, user2Id, user1Id);
        log.info("Match notifications sent to users {} / {} session {}", user1Id, user2Id, sessionId);
    }

    /**
     * Send typing indicator to the partner only.
     */
    public void sendTypingIndicator(String sessionId, Long typingUserId) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null || !session.isActive()) return;

        Long partnerId = session.getPartnerId(typingUserId);
        if (partnerId == null) return;

        String partnerEmail = chatSessionService.getUserEmail(partnerId);
        if (partnerEmail == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("typing", true);
        payload.put("senderId", session.getUserAnonymousId(typingUserId));
        messagingTemplate.convertAndSendToUser(partnerEmail, DEST_TYPING, payload);
    }

    /**
     * Notify the partner that their message was seen.
     */
    public void sendSeenReceipt(String sessionId, Long seenByUserId, String messageId) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null || !session.isActive()) return;

        Long partnerId = session.getPartnerId(seenByUserId);
        if (partnerId == null) return;

        String partnerEmail = chatSessionService.getUserEmail(partnerId);
        if (partnerEmail == null) return;

        ChatMessageDto dto = ChatMessageDto.builder()
                .messageId(messageId)
                .senderId(session.getUserAnonymousId(seenByUserId))
                .messageType(ChatMessage.MessageType.MESSAGE_SEEN.name())
                .timestamp(Instant.now())
                .seen(true)
                .build();

        messagingTemplate.convertAndSendToUser(partnerEmail, DEST_MESSAGES, dto);
        log.debug("Sent MESSAGE_SEEN receipt for message {} to partner {}", messageId, partnerId);
    }

    /**
     * Notify the partner that their message was delivered.
     */
    public void sendDeliveredReceipt(String sessionId, Long deliveredByUserId, String messageId) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null || !session.isActive()) return;

        Long partnerId = session.getPartnerId(deliveredByUserId);
        if (partnerId == null) return;

        String partnerEmail = chatSessionService.getUserEmail(partnerId);
        if (partnerEmail == null) return;

        ChatMessageDto dto = ChatMessageDto.builder()
                .messageId(messageId)
                .senderId(session.getUserAnonymousId(deliveredByUserId))
                .messageType(ChatMessage.MessageType.MESSAGE_DELIVERED.name())
                .timestamp(Instant.now())
                .delivered(true)
                .build();

        messagingTemplate.convertAndSendToUser(partnerEmail, DEST_MESSAGES, dto);
        log.debug("Sent MESSAGE_DELIVERED receipt for message {} to partner {}", messageId, partnerId);
    }

    /**
     * Send a SYSTEM message to both users (used for reconnect / disconnect notices).
     */
    public void sendSystemMessage(String sessionId, String content) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) return;

        ChatMessageDto msg = ChatMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .senderId("SYSTEM")
                .content(content)
                .messageType(ChatMessage.MessageType.SYSTEM.name())
                .timestamp(Instant.now())
                .build();

        sendToUser(session.getUser1Id(), msg);
        sendToUser(session.getUser2Id(), msg);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void sendToUser(Long userId, ChatMessageDto message) {
        if (userId == null) return;
        try {
            String email = chatSessionService.getUserEmail(userId);
            messagingTemplate.convertAndSendToUser(email, DEST_MESSAGES, message);
            log.debug("Sent type={} id={} to userId={}", message.getMessageType(), message.getMessageId(), userId);
        } catch (Exception e) {
            log.error("Failed to deliver message to userId={}", userId, e);
        }
    }

    private void sendMatchEvent(ChatSession session, Long recipientId, Long otherId) {
        String email = chatSessionService.getUserEmail(recipientId);
        if (email == null) return;

        Map<String, Object> event = new HashMap<>();
        event.put("matched", true);
        event.put("sessionId", session.getSessionId());
        event.put("yourAnonymousId", session.getUserAnonymousId(recipientId));
        event.put("partnerAnonymousId", session.getUserAnonymousId(otherId));
        messagingTemplate.convertAndSendToUser(email, DEST_MATCH, event);
    }

    /**
     * Reverse-lookup: given an anonymousId, return the real userId from the session.
     * Used by relaySignal() to determine who the partner is.
     */
    private Long resolveUserId(ChatSession session, String anonymousId) {
        if (anonymousId == null) return null;
        if (anonymousId.equals(session.getUser1AnonymousId())) return session.getUser1Id();
        if (anonymousId.equals(session.getUser2AnonymousId())) return session.getUser2Id();
        return null;
    }
}
