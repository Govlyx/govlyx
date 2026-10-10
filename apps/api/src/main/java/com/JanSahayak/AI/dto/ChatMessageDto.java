package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDto {

    // ── Core fields (always present) ──────────────────────────────────────────
    private String  messageId;
    private String  senderId;       // anonymous display name
    private String  content;        // null for media messages
    private String  messageType;    // ChatMessage.MessageType.name()
    private Instant timestamp;
    private String  replyToId;      // optional: reference to another messageId

    // ── Media fields (null / 0 / false for text / system messages) ────────────

    private boolean delivered;
    private boolean seen;

    /**
     * Base64 data-URI for IMAGE / VIDEO / STICKER / VOICE_NOTE.
     * SDP or ICE JSON for CALL_* types.
     * null for TEXT / SYSTEM messages.
     *
     * NEVER stored server-side — present only while in transit.
     */
    private String mediaPayload;

    /**
     * MIME type hint, e.g. "image/jpeg", "video/mp4", "audio/webm", "image/webp".
     * Null for non-media messages.
     */
    private String mimeType;

    /**
     * Optional display label, e.g. "sunset.jpg", "clip.mp4".
     * Null when not provided by the sender.
     */
    private String mediaName;

    // ── Timed / view-once (Telegram-style) ────────────────────────────────────

    /**
     * Seconds the recipient may view the media after first opening it.
     *  0 = no timer (default).
     *  1–60 = self-destruct countdown starts on first open.
     */
    @Builder.Default
    private int viewTimer = 0;

    /**
     * When true, the client destroys the media payload immediately after
     * the first open (view-once / "⊕ 1" mode, like Telegram).
     */
    @Builder.Default
    private boolean viewOnce = false;
}
