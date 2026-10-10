package com.JanSahayak.AI.model;

import com.JanSahayak.AI.config.Constant;

import lombok.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an active 1-vs-1 anonymous chat session.
 * Stored in memory, not persisted to database.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;
    private Long user1Id;
    private Long user2Id;
    private String user1AnonymousId;
    private String user2AnonymousId;
    private SessionStatus status;
    private Instant createdAt;
    private Instant lastActivityAt;
    private Instant endedAt;

    /**
     * When one user disconnected (e.g. page refresh).
     * Used to enforce the reconnect grace-period window.
     */
    private Instant disconnectedAt;

    /**
     * Which user is currently disconnected (null = both connected).
     */
    private Long disconnectedUserId;

    @Builder.Default
    private List<ChatMessage> recentMessages = new ArrayList<>();

    public enum SessionStatus {
        ACTIVE,         // Both users connected
        WAITING,        // One user connected, waiting for partner
        DISCONNECTED,   // One user lost connection — grace period active
        ENDED,          // Session ended normally
        EXPIRED         // Session timed out
    }

    // ── User helpers ──────────────────────────────────────────────────────────

    public boolean hasUser(Long userId) {
        return userId.equals(user1Id) || userId.equals(user2Id);
    }

    public String getPartnerAnonymousId(Long userId) {
        if (userId.equals(user1Id)) return user2AnonymousId;
        if (userId.equals(user2Id)) return user1AnonymousId;
        return null;
    }

    public String getUserAnonymousId(Long userId) {
        if (userId.equals(user1Id)) return user1AnonymousId;
        if (userId.equals(user2Id)) return user2AnonymousId;
        return null;
    }

    public Long getPartnerId(Long userId) {
        if (userId.equals(user1Id)) return user2Id;
        if (userId.equals(user2Id)) return user1Id;
        return null;
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    public boolean isActive() {
        return SessionStatus.ACTIVE.equals(status);
    }

    /**
     * A session is "joinable" when it is ACTIVE or in a DISCONNECTED grace period.
     * Used by the reconnect flow to decide if the user can resume.
     */
    public boolean isJoinable() {
        return isActive() || isInReconnectWindow();
    }

    /**
     * Returns true if a disconnected user's grace period has NOT yet expired.
     * Grace period length is controlled by Constant.CHAT_RECONNECT_GRACE_PERIOD_SECONDS.
     */
    public boolean isInReconnectWindow() {
        if (!SessionStatus.DISCONNECTED.equals(status) || disconnectedAt == null) {
            return false;
        }
        Instant deadline = disconnectedAt.plusSeconds(Constant.CHAT_RECONNECT_GRACE_PERIOD_SECONDS);
        return Instant.now().isBefore(deadline);
    }

    public boolean hasExpired(int maxInactiveMinutes) {
        if (!isActive()) return false;
        Instant expiryTime = lastActivityAt.plusSeconds(maxInactiveMinutes * 60L);
        return Instant.now().isAfter(expiryTime);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void addMessage(ChatMessage message) {
        if (recentMessages == null) recentMessages = new ArrayList<>();
        recentMessages.add(message);

        if (recentMessages.size() > Constant.CHAT_MAX_RECENT_MESSAGES) {
            recentMessages = new ArrayList<>(
                    recentMessages.subList(
                            recentMessages.size() - Constant.CHAT_MAX_RECENT_MESSAGES,
                            recentMessages.size()));
        }
        lastActivityAt = Instant.now();
    }

    /**
     * Mark one participant as disconnected and start the grace-period clock.
     */
    public void markDisconnected(Long userId) {
        this.status            = SessionStatus.DISCONNECTED;
        this.disconnectedUserId = userId;
        this.disconnectedAt    = Instant.now();
    }

    /**
     * Restore the session to ACTIVE when the disconnected user reconnects.
     */
    public void markReconnected() {
        this.status             = SessionStatus.ACTIVE;
        this.disconnectedUserId = null;
        this.disconnectedAt     = null;
        this.lastActivityAt     = Instant.now();
    }

    public void end() {
        this.status  = SessionStatus.ENDED;
        this.endedAt = Instant.now();
    }
}
