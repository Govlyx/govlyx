package com.Govlyx.AI.service;

import com.Govlyx.AI.config.Constant;
import com.Govlyx.AI.model.ChatMessage;
import com.Govlyx.AI.model.ChatSession;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Manages chat session lifecycle and message delivery.
 *
 * ── Reconnect flow (page-refresh fix) ────────────────────────────────────────
 *
 * Problem:
 *   When a user refreshes the browser the WebSocket drops, but endSession()
 *   is never called.  On the next findMatch() call isUserInSession() returns
 *   true and throws "already in an active chat", leaving the user locked out.
 *
 * Fix:
 *   1. WebSocket disconnect  → handleUserDisconnect()
 *      • Keeps the session alive in DISCONNECTED state for a short grace period
 *        (Constant.CHAT_RECONNECT_GRACE_PERIOD_SECONDS, e.g. 30 s).
 *      • Notifies the partner with a "partner disconnected" system message so
 *        their UI can show a reconnecting indicator instead of a hard leave.
 *
 *   2. User reconnects within grace period → reconnectToSession()
 *      • Restores status to ACTIVE, re-registers userSessionMap entry.
 *      • Notifies the partner that the user is back.
 *      • Returns the full session so the frontend can restore chat history.
 *
 *   3. Grace period expires → cleanupDisconnectedSessions() (scheduled)
 *      • If the user never came back, the session is ended and the partner
 *        gets the usual "partner has left" notification.
 *
 * ── WebSocket routing ─────────────────────────────────────────────────────────
 *   convertAndSendToUser(name, dest, payload) routes to /user/{name}/queue/...
 *   The STOMP principal name is the user's EMAIL, not the numeric userId.
 *   All routing therefore goes through getUserEmail(Long userId).
 *
 * ── Rich media (new) ──────────────────────────────────────────────────────────
 *   addMediaMessage() creates a transient ChatMessage for IMAGE / VIDEO /
 *   STICKER / VOICE_NOTE.  It does NOT add the message to session.recentMessages
 *   — media is relayed once and then dropped, consistent with the "never store"
 *   policy.  The caller (ChatMediaController) must immediately pass the returned
 *   object to ChatMessagingService.sendMediaToSession().
 */
@Service
@Slf4j
public class ChatSessionService {

    private final UserRepo userRepository;
    private final com.Govlyx.AI.repository.ChatSessionAuditRepo chatSessionAuditRepo;

    // @Lazy breaks the circular dependency:
    // ChatSessionService → ChatMessagingService → ChatSessionService
    private final ChatMessagingService chatMessagingService;

    private final Pattern excludedAuditEmailPattern;

    @Autowired
    public ChatSessionService(
            UserRepo userRepository,
            com.Govlyx.AI.repository.ChatSessionAuditRepo chatSessionAuditRepo,
            @Lazy ChatMessagingService chatMessagingService,
            @Value("${chat.audit.excluded-email-regex:^(user\\d+|usersomenumer)@gmail\\.com$}") String excludedEmailRegex) {
        this.userRepository      = userRepository;
        this.chatSessionAuditRepo = chatSessionAuditRepo;
        this.chatMessagingService = chatMessagingService;
        String regex = (excludedEmailRegex != null && !excludedEmailRegex.trim().isEmpty())
                ? excludedEmailRegex
                : "^(user\\d+|usersomenumer)@gmail\\.com$";
        this.excludedAuditEmailPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    public ChatSessionService(
            UserRepo userRepository,
            com.Govlyx.AI.repository.ChatSessionAuditRepo chatSessionAuditRepo,
            ChatMessagingService chatMessagingService) {
        this(userRepository, chatSessionAuditRepo, chatMessagingService, "^(user\\d+|usersomenumer)@gmail\\.com$");
    }

    // sessionId → session  (includes DISCONNECTED sessions during grace period)
    private final Map<String, ChatSession> activeSessions = new ConcurrentHashMap<>();

    // userId → sessionId   (maintained for both ACTIVE and DISCONNECTED sessions)
    private final Map<Long, String> userSessionMap = new ConcurrentHashMap<>();

    // userId → ipAddress (tracked from WebSocket CONNECT)
    private final Map<Long, String> userIpMap = new ConcurrentHashMap<>();

    public void trackUserIp(Long userId, String ipAddress) {
        if (ipAddress != null) {
            userIpMap.put(userId, ipAddress);
        }
    }

    public String getTrackedIp(Long userId) {
        return userIpMap.get(userId);
    }

    /**
     * FIX MEMORY LEAK #1 — recentMessages cap.
     * Without this cap the in-memory message list grows without bound for long-lived
     * sessions.  We keep the last MAX_RECENT_MESSAGES messages only.
     * Constant.CHAT_MAX_RECENT_MESSAGES should be set to ~200.
     */
    private static final int MAX_RECENT_MESSAGES =
            Constant.CHAT_MAX_RECENT_MESSAGES > 0 ? Constant.CHAT_MAX_RECENT_MESSAGES : 200;

    // FIX MEMORY LEAK #2 — per-session email lookup cache.
    // getUserEmail() is called on every WebSocket send; hitting the DB every time
    // creates GC pressure from short-lived User objects.  We cache email by userId
    // for the lifetime of the session and evict when the session is removed.
    // Bounded by the number of concurrent sessions * 2 users = negligible.
    private final Map<Long, String> emailCache = new ConcurrentHashMap<>();

    // ── Ephemeral Media Caching (Telegram-style) ──────────────────────────────
    // fileId -> raw bytes
    private final Map<String, byte[]> mediaCache = new ConcurrentHashMap<>();
    // sessionId -> list of fileIds
    private final Map<String, List<String>> sessionMediaMap = new ConcurrentHashMap<>();

    // ── Session lifecycle ─────────────────────────────────────────────────────

    public ChatSession createSession(Long user1Id, Long user2Id) {
        log.info("Creating chat session between users {} and {}", user1Id, user2Id);

        if (isUserInSession(user1Id) || isUserInSession(user2Id)) {
            throw new RuntimeException("One or both users are already in an active chat");
        }

        String sessionId        = UUID.randomUUID().toString();
        String user1DisplayName = getActualDisplayName(user1Id);
        String user2DisplayName = getActualDisplayName(user2Id);

        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .user1Id(user1Id)
                .user2Id(user2Id)
                .user1AnonymousId(user1DisplayName)
                .user2AnonymousId(user2DisplayName)
                .status(ChatSession.SessionStatus.ACTIVE)
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .recentMessages(new ArrayList<>())
                .build();

        session.addMessage(ChatMessage.systemMessage(sessionId, "You are now connected. Say hello!"));

        activeSessions.put(sessionId, session);
        userSessionMap.put(user1Id, sessionId);
        userSessionMap.put(user2Id, sessionId);

        // FIX MEMORY LEAK #2 — pre-populate email cache so WebSocket sends are DB-free
        try { emailCache.put(user1Id, fetchEmail(user1Id)); } catch (Exception e) { log.warn("Failed to pre-fetch email for user {}", user1Id, e); }
        try { emailCache.put(user2Id, fetchEmail(user2Id)); } catch (Exception e) { log.warn("Failed to pre-fetch email for user {}", user2Id, e); }

        // Legal Audit: Create immutable session record with IPs (skipped for test/excluded accounts)
        if (shouldAuditSession(user1Id, user2Id)) {
            try {
                com.Govlyx.AI.model.ChatSessionAudit audit = com.Govlyx.AI.model.ChatSessionAudit.builder()
                        .sessionId(sessionId)
                        .user1Id(user1Id)
                        .user2Id(user2Id)
                        .user1Ip(userIpMap.get(user1Id))
                        .user2Ip(userIpMap.get(user2Id))
                        .startedAt(new java.util.Date())
                        .build();
                chatSessionAuditRepo.save(audit);
            } catch (Exception e) {
                log.error("Failed to create ChatSessionAudit for session {}", sessionId, e);
            }
        } else {
            log.info("Skipping ChatSessionAudit for session {}: participant email matches exclusion pattern", sessionId);
        }

        log.info("Chat session {} created successfully", sessionId);
        return session;
    }

    public ChatSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    public ChatSession getUserSession(Long userId) {
        String sessionId = userSessionMap.get(userId);
        return sessionId != null ? getSession(sessionId) : null;
    }

    // ── Text message ──────────────────────────────────────────────────────────

    public ChatMessage addMessage(String sessionId, Long senderId, String content, String replyToId) {
        ChatSession session = getSession(sessionId);
        if (session == null)            throw new RuntimeException("Session not found: " + sessionId);
        if (!session.hasUser(senderId)) throw new RuntimeException("User not part of this session");
        if (!session.isActive())        throw new RuntimeException("Session is not active");

        String senderAnonymousId = session.getUserAnonymousId(senderId);
        ChatMessage message = ChatMessage.userMessage(sessionId, senderAnonymousId, content);
        message.setReplyToId(replyToId);
        session.addMessage(message);

        // FIX MEMORY LEAK #1 — trim message list to cap so it never grows unbounded
        List<ChatMessage> msgs = session.getRecentMessages();
        if (msgs != null && msgs.size() > MAX_RECENT_MESSAGES) {
            msgs.subList(0, msgs.size() - MAX_RECENT_MESSAGES).clear();
        }

        log.debug("Message added to session {} by user {}", sessionId, senderId);
        return message;
    }

    public boolean markMessageAsSeen(String sessionId, String messageId) {
        ChatSession session = getSession(sessionId);
        if (session == null || session.getRecentMessages() == null) return false;

        for (ChatMessage msg : session.getRecentMessages()) {
            if (msg.getMessageId().equals(messageId)) {
                if (!msg.isSeen()) {
                    msg.setSeen(true);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public boolean markMessageAsDelivered(String sessionId, String messageId) {
        ChatSession session = getSession(sessionId);
        if (session == null || session.getRecentMessages() == null) return false;

        for (ChatMessage msg : session.getRecentMessages()) {
            if (msg.getMessageId().equals(messageId)) {
                if (!msg.isDelivered()) {
                    msg.setDelivered(true);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    // ── Rich media message (IMAGE / VIDEO / STICKER / VOICE_NOTE) ─────────────

    /**
     * Create a transient rich-media message and return it for immediate relay.
     *
     * ── Storage policy ────────────────────────────────────────────────────────
     * Media messages are intentionally NOT added to session.recentMessages.
     * They exist only as an in-flight WebSocket frame — once delivered, dropped.
     * This means they are never replayed in chat history and never accumulate in
     * the in-memory session object, keeping heap pressure predictable.
     *
     * ── Payload size guard ────────────────────────────────────────────────────
     * Rejects payloads over 50 MB (base64 string length) to prevent a single
     * large video from exhausting heap while it is buffered in the WebSocket
     * send queue.  50 MB base64 ≈ 37.5 MB raw binary.
     *
     * ── Timer / view-once ─────────────────────────────────────────────────────
     * viewTimer is clamped to 0–60 s in ChatMessage.mediaMessage().
     * viewOnce takes precedence over viewTimer on the client side.
     * The server only relays these values; enforcement is client-side.
     *
     * @param sessionId    active session
     * @param senderId     userId of the sender
     * @param type         IMAGE | VIDEO | STICKER | VOICE_NOTE
     * @param mediaBytes   raw binary bytes from MultipartFile
     * @param mimeType     MIME type hint, e.g. "image/jpeg", "video/mp4"
     * @param mediaName    optional display name (may be null)
     * @param viewTimer    0 = no timer; 1–60 = seconds visible after first open
     * @param viewOnce     true = destroy on first open
     * @return transient ChatMessage — caller MUST immediately pass to
     *         ChatMessagingService.sendMediaToSession()
     */
    public ChatMessage addMediaMessage(
            String sessionId,
            Long senderId,
            ChatMessage.MessageType type,
            byte[] mediaBytes,
            String mimeType,
            String mediaName,
            int viewTimer,
            boolean viewOnce,
            String replyToId) {

        ChatSession session = getSession(sessionId);
        if (session == null)            throw new RuntimeException("Session not found: " + sessionId);
        if (!session.hasUser(senderId)) throw new RuntimeException("User not part of this session");
        if (!session.isActive())        throw new RuntimeException("Session is not active");

        // Guard: reject over-large payloads before they hit the RAM cache
        // 50 MB limit
        if (mediaBytes != null && mediaBytes.length > 50L * 1024 * 1024) {
            throw new RuntimeException("Media payload exceeds 50 MB limit");
        }

        String fileId = UUID.randomUUID().toString();
        
        if (mediaBytes != null) {
            mediaCache.put(fileId, mediaBytes);
            // FIX OOM DoS + Race Condition: Use synchronizedList and enforce a strict cap 
            // of 20 media files per session so an attacker cannot exhaust server RAM by 
            // spamming 50MB files indefinitely.
            List<String> sessionMedia = sessionMediaMap.computeIfAbsent(sessionId, k -> java.util.Collections.synchronizedList(new ArrayList<>()));
            synchronized (sessionMedia) {
                if (sessionMedia.size() >= 20) {
                    String oldestFileId = sessionMedia.remove(0);
                    mediaCache.remove(oldestFileId);
                }
                sessionMedia.add(fileId);
            }
        }

        String senderAnonymousId = session.getUserAnonymousId(senderId);
        ChatMessage message = ChatMessage.mediaMessage(
                sessionId, senderAnonymousId, type,
                fileId, mimeType, mediaName,
                viewTimer, viewOnce);
        message.setReplyToId(replyToId);

        // Touch lastActivityAt so the session doesn't time out during media exchange
        session.setLastActivityAt(Instant.now());

        // NOTE: message is NOT added to session.recentMessages — media is ephemeral
        log.debug("Media message {} ({}) created in session {} by user {} | timer={}s viewOnce={}",
                message.getMessageId(), type, sessionId, senderId, viewTimer, viewOnce);
        return message;
    }

    public byte[] getMedia(String fileId) {
        return mediaCache.get(fileId);
    }

    public boolean deleteMedia(String fileId) {
        if (fileId == null) return false;
        return mediaCache.remove(fileId) != null;
    }

    public void markMediaUsedInAudit(String sessionId, Long userId) {
        try {
            chatSessionAuditRepo.findBySessionId(sessionId).ifPresent(audit -> {
                boolean changed = false;
                if (userId.equals(audit.getUser1Id()) && !Boolean.TRUE.equals(audit.getUser1UsedMedia())) {
                    audit.setUser1UsedMedia(true);
                    changed = true;
                } else if (userId.equals(audit.getUser2Id()) && !Boolean.TRUE.equals(audit.getUser2UsedMedia())) {
                    audit.setUser2UsedMedia(true);
                    changed = true;
                }
                if (changed) {
                    chatSessionAuditRepo.save(audit);
                }
            });
        } catch (Exception e) {
            log.error("Failed to mark media used in audit for session {}", sessionId, e);
        }
    }

    private void cleanupSessionMedia(String sessionId) {
        List<String> fileIds = sessionMediaMap.remove(sessionId);
        if (fileIds != null) {
            for (String fileId : fileIds) {
                mediaCache.remove(fileId);
            }
            log.info("Cleaned up {} media files for session {}", fileIds.size(), sessionId);
        }
    }

    // ── Session end ───────────────────────────────────────────────────────────

    /**
     * Normal, intentional leave (user clicks "Leave chat").
     *
     * Order matters:
     *  1. notifyUserLeft()  — partner notified while session still ACTIVE
     *  2. session.end()     — mark ENDED
     *  3. remove mappings   — clean up
     */
    public void endSession(String sessionId, Long userId) {
        ChatSession session = getSession(sessionId);
        if (session == null) {
            log.warn("Attempted to end non-existent session: {}", sessionId);
            return;
        }
        if (!session.hasUser(userId)) {
            throw new RuntimeException("User not authorized to end this session");
        }

        log.info("Ending chat session {} by user {}", sessionId, userId);

        try {
            chatMessagingService.notifyUserLeft(sessionId, userId);
        } catch (Exception e) {
            log.error("Failed to notify partner in session {} that user {} left", sessionId, userId, e);
        }

        String userAnonymousId = session.getUserAnonymousId(userId);
        session.addMessage(ChatMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .senderId("SYSTEM")
                .content(userAnonymousId + " has left the chat")
                .messageType(ChatMessage.MessageType.USER_LEFT)
                .timestamp(Instant.now())
                .build());
        session.end();

        userSessionMap.remove(session.getUser1Id());
        userSessionMap.remove(session.getUser2Id());
        activeSessions.remove(sessionId);
        // FIX MEMORY LEAK #2 — evict cached emails for users whose session ended
        emailCache.remove(session.getUser1Id());
        emailCache.remove(session.getUser2Id());
        
        // TELEGRAM-STYLE CLEANUP: Wipe all standard media from RAM
        cleanupSessionMedia(sessionId);

        // Legal Audit: Close session record
        try {
            chatSessionAuditRepo.findBySessionId(sessionId).ifPresent(audit -> {
                audit.setEndedAt(new java.util.Date());
                chatSessionAuditRepo.save(audit);
            });
        } catch (Exception e) {
            log.error("Failed to update ChatSessionAudit endedAt for session {}", sessionId, e);
        }
    }

    // ── Reconnect flow ────────────────────────────────────────────────────────

    /**
     * Called when a WebSocket disconnect event is detected for this user.
     *
     * Behaviour:
     *  • If the user has an ACTIVE session  → mark DISCONNECTED (grace period starts).
     *  • If already DISCONNECTED / no session → no-op.
     *
     * The partner is sent a soft "partner disconnected" system message so their
     * UI can show a reconnecting spinner rather than treating it as a hard leave.
     */
    public void handleUserDisconnect(Long userId) {
        ChatSession session = getUserSession(userId);
        if (session == null || !session.isActive()) {
            return;
        }

        String sessionId = session.getSessionId();
        log.info("User {} disconnected from session {} — starting grace period of {}s",
                userId, sessionId, Constant.CHAT_RECONNECT_GRACE_PERIOD_SECONDS);

        session.markDisconnected(userId);

        Long partnerId = session.getPartnerId(userId);
        if (partnerId != null) {
            try {
                chatMessagingService.sendSystemMessage(sessionId,
                        "Your partner lost connection. Waiting for them to reconnect...");
            } catch (Exception e) {
                log.warn("Could not send disconnect notification to partner in session {}", sessionId, e);
            }
        }
    }

    /**
     * Called when a user reconnects (e.g. after a page refresh).
     *
     * Returns the existing ChatSession if the user is within the grace period,
     * or null if there is nothing to reconnect to.
     */
    public ChatSession reconnectToSession(Long userId) {
        String sessionId = userSessionMap.get(userId);
        if (sessionId == null) {
            log.debug("reconnectToSession: no session mapping for user {}", userId);
            return null;
        }

        ChatSession session = activeSessions.get(sessionId);
        if (session == null) {
            userSessionMap.remove(userId);
            log.debug("reconnectToSession: stale mapping for user {}, removed", userId);
            return null;
        }

        if (!session.isInReconnectWindow()) {
            log.info("reconnectToSession: grace period expired for user {} session {}", userId, sessionId);
            forceEndSessionForDisconnectedUser(session, userId);
            return null;
        }

        log.info("User {} reconnected to session {} within grace period", userId, sessionId);
        session.markReconnected();
        userSessionMap.put(userId, sessionId);

        String anonId = session.getUserAnonymousId(userId);
        session.addMessage(ChatMessage.systemMessage(sessionId, anonId + " reconnected."));

        try {
            chatMessagingService.sendSystemMessage(sessionId, "Your partner has reconnected!");
        } catch (Exception e) {
            log.warn("Could not send reconnect notification in session {}", sessionId, e);
        }

        return session;
    }

    /**
     * Check if a user has a disconnected-but-reconnectable session.
     * Used by MatchmakingService to skip the "already in session" error.
     */
    public boolean isUserDisconnectedWithActiveSession(Long userId) {
        ChatSession session = getUserSession(userId);
        if (session == null) return false;
        return ChatSession.SessionStatus.DISCONNECTED.equals(session.getStatus())
                && session.isInReconnectWindow();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<ChatMessage> getRecentMessages(String sessionId, Long userId, int limit) {
        ChatSession session = getSession(sessionId);
        if (session == null) return Collections.emptyList();
        if (!session.hasUser(userId)) throw new RuntimeException("User not part of this session");

        List<ChatMessage> messages = session.getRecentMessages();
        if (messages == null || messages.isEmpty()) return Collections.emptyList();

        int startIndex = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(startIndex, messages.size()));
    }

    public boolean isUserInSession(Long userId) {
        ChatSession session = getUserSession(userId);
        return session != null && session.isActive();
    }

    public int getActiveSessionCount() {
        return (int) activeSessions.values().stream()
                .filter(ChatSession::isActive)
                .count();
    }

    public int getOnlineUserCount() {
        return getActiveSessionCount() * 2;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void cleanupExpiredSessions() {
        List<String> toExpire = new ArrayList<>();
        for (Map.Entry<String, ChatSession> entry : activeSessions.entrySet()) {
            if (entry.getValue().hasExpired(Constant.CHAT_MAX_INACTIVE_MINUTES)) {
                toExpire.add(entry.getKey());
            }
        }
        for (String sid : toExpire) {
            ChatSession s = activeSessions.get(sid);
            if (s != null) {
                log.info("Expiring inactive session {}", sid);
                s.setStatus(ChatSession.SessionStatus.EXPIRED);
                userSessionMap.remove(s.getUser1Id());
                userSessionMap.remove(s.getUser2Id());
                activeSessions.remove(sid);
                cleanupSessionMedia(sid);
            }
        }
        if (!toExpire.isEmpty()) log.info("Expired {} inactive sessions", toExpire.size());
    }

    /**
     * Admin emergency kill switch — force-ends ALL active and disconnected sessions.
     * Called by AdminController.forceEndAllChatSessions().
     *
     * @return the number of sessions that were terminated
     */
    public int forceEndAllSessions() {
        List<String> toEnd = new ArrayList<>(activeSessions.keySet());
        int count = 0;
        for (String sid : toEnd) {
            ChatSession session = activeSessions.get(sid);
            if (session != null) {
                try {
                    session.end();
                } catch (Exception e) {
                    log.warn("Error ending session {} during force-end-all", sid, e);
                }
                userSessionMap.remove(session.getUser1Id());
                userSessionMap.remove(session.getUser2Id());
                emailCache.remove(session.getUser1Id());
                emailCache.remove(session.getUser2Id());
                activeSessions.remove(sid);
                cleanupSessionMedia(sid);
                count++;
            }
        }
        log.warn("Force-ended ALL chat sessions — {} sessions terminated", count);
        return count;
    }

    public void cleanupEndedSessions() {
        List<String> toRemove = new ArrayList<>();
        Instant cutoff = Instant.now().minusSeconds(60);
        for (Map.Entry<String, ChatSession> entry : activeSessions.entrySet()) {
            ChatSession s = entry.getValue();
            if (!s.isActive() && s.getEndedAt() != null && s.getEndedAt().isBefore(cutoff)) {
                toRemove.add(entry.getKey());
            }
        }
        for (String sid : toRemove) {
            ChatSession s = activeSessions.remove(sid);
            if (s != null) {
                userSessionMap.remove(s.getUser1Id());
                userSessionMap.remove(s.getUser2Id());
                emailCache.remove(s.getUser1Id());
                emailCache.remove(s.getUser2Id());
                cleanupSessionMedia(sid);
            }
        }
        if (!toRemove.isEmpty()) log.info("Removed {} ended sessions from memory", toRemove.size());
    }

    /**
     * Called by the scheduler every 15 seconds.
     * Ends sessions whose disconnected user's grace period has expired.
     */
    public void cleanupDisconnectedSessions() {
        List<ChatSession> expired = new ArrayList<>();
        for (ChatSession session : activeSessions.values()) {
            if (ChatSession.SessionStatus.DISCONNECTED.equals(session.getStatus())
                    && !session.isInReconnectWindow()) {
                expired.add(session);
            }
        }

        for (ChatSession session : expired) {
            Long disconnectedUser = session.getDisconnectedUserId();
            log.info("Grace period expired for user {} in session {} — ending session",
                    disconnectedUser, session.getSessionId());
            forceEndSessionForDisconnectedUser(session, disconnectedUser);
        }

        if (!expired.isEmpty()) {
            log.info("Cleaned up {} grace-period-expired sessions", expired.size());
        }
    }

    // ── Principal / routing helpers ───────────────────────────────────────────

    /**
     * Returns the WebSocket principal name (= email) for a user.
     * convertAndSendToUser() routes to /user/{email}/queue/...
     *
     * FIX MEMORY LEAK #2 — served from emailCache when available.
     */
    public String getUserEmail(Long userId) {
        String cached = emailCache.get(userId);
        if (cached != null) return cached;
        String email = fetchEmail(userId);
        emailCache.put(userId, email);
        return email;
    }

    /**
     * Resolve a userId from an email address.
     * Used by ChatMediaController to identify the authenticated caller.
     */
    public Long resolveUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("User not found for email: " + email));
    }

    /**
     * Called by WebSocketDisconnectListener when a WebSocket session drops.
     *
     * @param email the principal name from the disconnect event (always an email
     *              in this project — User.getUsername() returns email)
     */
    public void handleUserDisconnectByEmail(String email) {
        userRepository.findByEmail(email).ifPresentOrElse(
                user -> handleUserDisconnect(user.getId()),
                () -> log.debug("handleUserDisconnectByEmail: no user found for email {}", email)
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * End a session because a disconnected user never reconnected.
     * Notifies the remaining partner and cleans up all mappings.
     */
    private void forceEndSessionForDisconnectedUser(ChatSession session, Long disconnectedUserId) {
        String sessionId = session.getSessionId();
        try {
            // Temporarily restore ACTIVE so notifyUserLeft() can resolve the partner
            session.setStatus(ChatSession.SessionStatus.ACTIVE);
            chatMessagingService.notifyUserLeft(sessionId, disconnectedUserId);
        } catch (Exception e) {
            log.error("Error notifying partner about disconnected user {} in session {}",
                    disconnectedUserId, sessionId, e);
        } finally {
            session.end();
            userSessionMap.remove(session.getUser1Id());
            userSessionMap.remove(session.getUser2Id());
            activeSessions.remove(sessionId);
            emailCache.remove(session.getUser1Id());
            emailCache.remove(session.getUser2Id());
            cleanupSessionMedia(sessionId);
        }
    }

    /** Raw DB lookup — only called on cache miss. */
    private String fetchEmail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return user.getUsername(); // getUsername() returns EMAIL in this project
    }

    private String getActualDisplayName(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        String username = user.getActualUsername();
        if (username == null || username.trim().isEmpty()) {
            String email = user.getEmail();
            username = (email != null && !email.trim().isEmpty())
                    ? email.split("@")[0]
                    : "User" + userId;
        }
        return username;
    }

    /**
     * Checks whether the chat session should be audited in the database.
     * Returns false if either participant has an email matching excludedAuditEmailPattern.
     */
    private boolean shouldAuditSession(Long user1Id, Long user2Id) {
        try {
            String email1 = emailCache.get(user1Id);
            if (email1 == null) {
                email1 = getUserEmail(user1Id);
            }
            String email2 = emailCache.get(user2Id);
            if (email2 == null) {
                email2 = getUserEmail(user2Id);
            }
            if (isExcludedFromAudit(email1) || isExcludedFromAudit(email2)) {
                return false;
            }
        } catch (Exception e) {
            log.warn("Error resolving participant emails for audit exclusion check (users {} and {}): {}",
                    user1Id, user2Id, e.getMessage());
        }
        return true;
    }

    public boolean isExcludedFromAudit(String email) {
        if (email == null || email.trim().isEmpty() || excludedAuditEmailPattern == null) {
            return false;
        }
        return excludedAuditEmailPattern.matcher(email.trim()).matches();
    }
}
