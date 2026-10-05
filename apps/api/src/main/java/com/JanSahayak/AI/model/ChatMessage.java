package com.JanSahayak.AI.model;

import lombok.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a single message in an anonymous chat session.
 * Not persisted to database — only exists during the active session.
 *
 * ── Rich-media support (Telegram-style) ──────────────────────────────────────
 *
 * Images and videos are relayed in-memory only — they are NEVER written to
 * disk, database, or any persistent store.  The flow is:
 *
 *   Sender  ──POST /api/chat/{id}/media──►  ChatMediaController
 *                                                  │
 *                                         addMediaMessage()  ← touches lastActivityAt only
 *                                                  │
 *                                         sendMediaToSession() ──WebSocket──► Receiver
 *                                                  │
 *                                              (dropped)   ← object GC'd, nothing retained
 *
 * ── Timed / view-once media (Telegram-style) ─────────────────────────────────
 *
 *   viewTimer  > 0  → recipient may view the media for this many seconds after
 *                      first opening it; client destroys the payload when timer
 *                      hits zero.  Server never held it persistently.
 *
 *   viewTimer == 0  → no timer; media persists for the lifetime of the chat
 *                      (which is itself ephemeral / in-memory only).
 *
 *   viewOnce == true → media may be opened exactly once; client removes payload
 *                       immediately on first open (implies no server timer).
 *
 * Valid viewTimer range: 0–60 s (enforced in ChatMediaController).
 * All enforcement is client-side — server only relays the fields.
 *
 * ── WebRTC signaling (voice & video calls) ────────────────────────────────────
 * CALL_OFFER / CALL_ANSWER / CALL_ICE / CALL_ENDED carry SDP / ICE JSON in
 * {@code mediaPayload} as a relay.  Actual A/V streams are peer-to-peer.
 *
 * ── Storage policy ────────────────────────────────────────────────────────────
 *  TEXT / SYSTEM          → added to session.recentMessages (capped, in-memory)
 *  IMAGE / VIDEO /
 *  STICKER / VOICE_NOTE   → NOT added to recentMessages; relayed once and dropped
 *  CALL_*                 → NOT stored; relayed to partner only
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Unique message ID */
    private String messageId;

    /** Session this message belongs to */
    private String sessionId;

    /**
     * Anonymous sender identifier (e.g., "Riya", "Arjun").
     * "SYSTEM" for system-generated messages.
     */
    private String senderId;

    /** Text content. Null for pure-media messages. */
    private String content;

    /** 
     * Optional: messageId of the message being replied to (Telegram-style).
     * Relayed in-memory only — never persisted.
     */
    private String replyToId;

    /** Message type */
    private MessageType messageType;

    /** When the message was sent */
    private Instant timestamp;

    /** Whether this message has been delivered over WebSocket */
    private boolean delivered;

    /** Whether this message has been seen by the receiver */
    private boolean seen;

    // ── Media fields (null for TEXT / SYSTEM) ─────────────────────────────────

    /**
     * File ID for IMAGE / VIDEO / STICKER / VOICE_NOTE.
     * For CALL_* types this carries SDP or ICE candidate JSON.
     * For MEDIA_WIPED this carries the fileId that was wiped.
     *
     * Examples:
     *   "f47ac10b-58cc-4372-a567-0e02b2c3d479"
     *   "data:video/mp4;base64,AAAAFGZ0..." (legacy/fallback if needed)
     *
     * NEVER persisted — relayed in-flight only.
     */
    private String mediaPayload;

    /**
     * MIME type hint for the receiver.
     * e.g. "image/jpeg", "image/gif", "video/mp4", "video/webm",
     *      "audio/webm", "image/webp" (sticker).
     */
    private String mimeType;

    /**
     * Optional display name / sticker label.
     * e.g. "sunset.jpg", "clip.mp4", "voice.webm"
     * Purely informational — never used server-side.
     */
    private String mediaName;

    // ── Timed / view-once fields ───────────────────────────────────────────────

    /**
     * View timer in seconds (Telegram-style "⏱" media).
     *
     *  > 0  → client starts countdown from first open; destroys payload at zero.
     * == 0  → no timer (default).
     *
     * Valid range: 0–60.  Values outside this range are clamped to 0 in
     * ChatMediaController before the message is created.
     */
    @Builder.Default
    private int viewTimer = 0;

    /**
     * View-once flag (Telegram "⊕ 1" media).
     *
     * When true, client removes payload immediately after first open.
     * Takes precedence over {@code viewTimer} on the client side.
     * Server only relays; enforcement is entirely client-side.
     */
    @Builder.Default
    private boolean viewOnce = false;

    // ── Message types ─────────────────────────────────────────────────────────

    public enum MessageType {
        // ── Text ──────────────────────────────────────────────────────────────
        TEXT,

        // ── System events ─────────────────────────────────────────────────────
        SYSTEM,
        USER_JOINED,
        USER_LEFT,
        CHAT_ENDED,
        MESSAGE_DELIVERED,
        MESSAGE_SEEN,

        // ── Rich media (relayed once, never stored) ────────────────────────────
        IMAGE,          // Photo / GIF       — mediaPayload = fileId
        VIDEO,          // Video clip        — mediaPayload = fileId
        STICKER,        // Sticker           — mediaPayload = fileId, mimeType = "image/webp"
        VOICE_NOTE,     // Voice memo        — mediaPayload = fileId, mimeType = "audio/webm"
        MEDIA_WIPED,    // Media destroyed   — mediaPayload = fileId

        // ── WebRTC call signaling (relayed to partner only, never stored) ──────
        CALL_OFFER,     // SDP offer         — mediaPayload = { "type":"offer",  "sdp":"..." }
        CALL_ANSWER,    // SDP answer        — mediaPayload = { "type":"answer", "sdp":"..." }
        CALL_ICE,       // ICE candidate     — mediaPayload = RTCIceCandidate JSON
        CALL_ENDED      // Call terminated
    }

    // ── Static factories ──────────────────────────────────────────────────────

    /** System notification (e.g. "You are now connected. Say hello!"). */
    public static ChatMessage systemMessage(String sessionId, String content) {
        return ChatMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .senderId("SYSTEM")
                .content(content)
                .messageType(MessageType.SYSTEM)
                .timestamp(Instant.now())
                .delivered(false)
                .seen(false)
                .build();
    }

    /** Plain-text user message — no timer, no media. */
    public static ChatMessage userMessage(String sessionId, String senderId, String content) {
        return ChatMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .senderId(senderId)
                .content(content)
                .messageType(MessageType.TEXT)
                .timestamp(Instant.now())
                .delivered(false)
                .seen(false)
                .build();
    }

    /**
     * Rich-media message (IMAGE / VIDEO / STICKER / VOICE_NOTE) with optional
     * Telegram-style timer or view-once flag.
     *
     * @param type          IMAGE, VIDEO, STICKER, or VOICE_NOTE
     * @param mediaPayload  fileId generated by the server, e.g. UUID
     * @param mimeType      MIME type, e.g. "image/jpeg", "video/mp4"
     * @param mediaName     optional display name, e.g. "clip.mp4" (may be null)
     * @param viewTimer     0 = no timer; 1–60 = seconds visible after first open
     * @param viewOnce      true = destroy on first open (ignores viewTimer client-side)
     */
    public static ChatMessage mediaMessage(
            String sessionId,
            String senderId,
            MessageType type,
            String mediaPayload,
            String mimeType,
            String mediaName,
            int viewTimer,
            boolean viewOnce) {

        if (type != MessageType.IMAGE
                && type != MessageType.VIDEO
                && type != MessageType.STICKER
                && type != MessageType.VOICE_NOTE) {
            throw new IllegalArgumentException(
                    "mediaMessage() only accepts IMAGE, VIDEO, STICKER, or VOICE_NOTE; got: " + type);
        }
        return ChatMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .senderId(senderId)
                .messageType(type)
                .mediaPayload(mediaPayload)
                .mimeType(mimeType)
                .mediaName(mediaName)
                .viewTimer(Math.max(0, Math.min(viewTimer, 60)))  // clamp 0–60
                .viewOnce(viewOnce)
                .timestamp(Instant.now())
                .delivered(false)
                .seen(false)
                .build();
    }

    /**
     * WebRTC signaling relay (CALL_OFFER / CALL_ANSWER / CALL_ICE / CALL_ENDED).
     *
     * @param sdpOrIceJson  SDP or ICE JSON string; null for CALL_ENDED
     */
    public static ChatMessage signalingMessage(
            String sessionId,
            String senderId,
            MessageType type,
            String sdpOrIceJson) {

        return ChatMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .senderId(senderId)
                .messageType(type)
                .mediaPayload(sdpOrIceJson)
                .timestamp(Instant.now())
                .delivered(false)
                .seen(false)
                .build();
    }
}
