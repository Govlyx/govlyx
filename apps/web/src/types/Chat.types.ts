// src/types/chat.types.ts

/** Wrapper returned by every REST endpoint  (ApiResponse<T>) */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
  error?: string;
}

// ── Message ──────────────────────────────────────────────────────────────────

export type MessageType = "TEXT" | "SYSTEM" | "USER_JOINED" | "USER_LEFT" | "CHAT_ENDED" | "IMAGE" | "VIDEO" | "STICKER" | "MEDIA_WIPED" | "MESSAGE_DELIVERED" | "MESSAGE_SEEN";

export interface ChatMessageDto {
  messageId:   string;
  senderId:    string;   // anonymous id, or "SYSTEM"
  content?:    string;   // optional for media messages
  messageType: MessageType;
  timestamp:   string;   // ISO-8601
  replyToId?:  string;   // optional: messageId of the message being replied to

  // Delivery / read receipts
  delivered?:  boolean;  // true once the partner's device acknowledged receipt
  seen?:       boolean;  // true once the partner has read the message

  // Media fields
  mediaPayload?: string; // base64 data-URI or file UUID from server
  mimeType?:     string; // image/jpeg, etc.
  mediaName?:    string; // sunset.jpg, etc.
  viewTimer?:    number; // 0 = no timer, >0 = seconds
  viewOnce?:     boolean;
  isWiped?:      boolean; // true after server has wiped the media from RAM
}

// ── Session ───────────────────────────────────────────────────────────────────

export type SessionStatus = "ACTIVE" | "ENDED" | "EXPIRED" | "DISCONNECTED";

export interface ChatSessionDto {
  sessionId:          string;
  yourAnonymousId:    string;
  partnerAnonymousId: string;
  status:             SessionStatus;
  createdAt:          string;
  lastActivityAt:     string;
}

// ── Search ────────────────────────────────────────────────────────────────────

export interface SearchResponseData {
  matched:   boolean;
  searching?: boolean;
  queueSize?: number;
  sessionId?: string;
  session?:   ChatSessionDto;
}

// ── STOMP payloads ────────────────────────────────────────────────────────────

export interface SendMessagePayload {
  content: string;
}

export interface MatchNotification {
  matched:            boolean;
  sessionId:          string;
  yourAnonymousId:    string;
  partnerAnonymousId: string;
}

export interface TypingNotification {
  typing:   boolean;
  senderId: string;
}

// ── UI state ──────────────────────────────────────────────────────────────────

export type ChatStatus =
  | "IDLE"
  | "SEARCHING"
  | "CONNECTED"
  | "PARTNER_LEFT"
  | "ERROR";