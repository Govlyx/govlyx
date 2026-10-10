

import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { API_BASE_URL } from "./axiosConfig";
import { getAuthToken } from "../utils/auth";
import type {
  ChatMessageDto,
  MatchNotification,
  TypingNotification,
} from "../types/Chat.types";

// ── Auth helpers ──────────────────────────────────────────────────────────────
function getToken(): string | null {
  return getAuthToken();
}

function buildAuthHeaders(): Record<string, string> {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

const API_BASE = API_BASE_URL;
const WS_URL   = `${API_BASE}/ws`;

// ── Handler types ─────────────────────────────────────────────────────────────
type OnMessage = (msg: ChatMessageDto)   => void;
type OnTyping  = (n: TypingNotification) => void;
type OnMatch   = (n: MatchNotification)  => void;
type OnError   = (msg: string)           => void;

export interface ChatSocketHandlers {
  onMessage:       OnMessage;
  onTyping:        OnTyping;
  onMatchEvent:    OnMatch;
  onError:         OnError;
  onConnected?:    () => void;
  onDisconnected?: () => void;
}

// ── Service ───────────────────────────────────────────────────────────────────

class ChatSocketService {
  private client:        Client | null = null;
  private subscriptions: StompSubscription[] = [];
  private handlers:      ChatSocketHandlers | null = null;

  /**
   * Activate STOMP client and register all subscription handlers.
   * Safe to call multiple times — disconnects the old client first if active.
   */
  connect(handlers: ChatSocketHandlers): void {
    const token = getToken();
    if (!token) return; // Never open STOMP socket without valid JWT

    // If already connected with same handlers, no-op
    if (this.client?.active) {
      return;
    }

    // Cleanly tear down socket when page transitions / navigates to avoid BFCache forced termination
    if (typeof window !== "undefined") {
      window.addEventListener("pagehide", () => this.disconnect(), { once: true });
    }

    this.handlers = handlers;

    this.client = new Client({
      // SockJS factory: evaluated fresh on every (re)connect attempt
      // so the URL is always up-to-date.
      webSocketFactory: () => {
        console.debug("[ChatSocket] Connecting to:", WS_URL);
        return new SockJS(WS_URL);
      },

      // JWT in STOMP CONNECT frame headers — Spring Security reads this
      connectHeaders: buildAuthHeaders(),

      // Re-read token before every connect/reconnect so a refreshed JWT
      // is picked up automatically without needing to reconnect manually.
      beforeConnect: async () => {
        if (this.client) {
          this.client.connectHeaders = buildAuthHeaders();
        }
      },

      reconnectDelay: 5_000,

      onConnect: () => {
        console.debug("[ChatSocket] Connected to", WS_URL);
        this.handlers?.onConnected?.();
        this._clearSubscriptions();
        this.subscriptions = [
          this._sub<ChatMessageDto>("/user/queue/messages", (msg) => this.handlers?.onMessage(msg)),
          this._sub<TypingNotification>("/user/queue/typing", (n) => this.handlers?.onTyping(n)),
          this._sub<MatchNotification>("/user/queue/match", (n) => this.handlers?.onMatchEvent(n)),
        ];
      },

      onStompError: (frame) => {
        const msg = frame.headers?.message ?? "WebSocket protocol error";
        console.error("[ChatSocket] STOMP error:", msg, frame);
        this.handlers?.onError(msg);
      },

      onWebSocketError: (event) => {
        console.error("[ChatSocket] WebSocket error:", event);
        this.handlers?.onError("Connection lost — reconnecting…");
      },

      onWebSocketClose: (event) => {
        console.debug("[ChatSocket] WebSocket closed:", event.code, event.reason);
      },

      onDisconnect: () => {
        this.handlers?.onDisconnected?.();
      },

      debug: import.meta.env.DEV
        ? (str) => console.debug("[STOMP]", str)
        : () => {},
    });

    this.client.activate();
  }

  /** Unsubscribe everything and close the connection cleanly. */
  disconnect(): void {
    this._clearSubscriptions();
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    this.handlers = null;
  }

  /** Send a text message → @MessageMapping("/chat.send") */
  sendMessage(content: string, replyToId?: string): void {
    this._publish("/app/chat.send", { content, replyToId });
  }

  /** Notify partner of typing → @MessageMapping("/chat.typing") */
  sendTyping(): void {
    this._publish("/app/chat.typing", {});
  }

  /** Notify sender that their message was delivered → @MessageMapping("/chat.delivered") */
  sendDelivered(messageId: string): void {
    this._publish("/app/chat.delivered", { messageId });
  }

  /** Notify sender that their message was seen → @MessageMapping("/chat.seen") */
  sendSeen(messageId: string): void {
    this._publish("/app/chat.seen", { messageId });
  }

  get isConnected(): boolean {
    return this.client?.connected ?? false;
  }

  /** Dynamically subscribe to any topic (e.g. /topic/feed.new-post) */
  subscribeTopic<T>(dest: string, handler: (p: T) => void): StompSubscription | null {
    if (!this.client?.connected) return null;
    try {
      return this._sub<T>(dest, handler);
    } catch (err) {
      console.error(`[ChatSocket] Failed to subscribe to ${dest}:`, err);
      return null;
    }
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private _sub<T>(dest: string, handler: (p: T) => void): StompSubscription {
    if (!this.client) throw new Error("Client not initialised");
    return this.client.subscribe(dest, (frame: IMessage) => {
      try {
        handler(JSON.parse(frame.body) as T);
      } catch (e) {
        console.error(`[ChatSocket] Parse error on ${dest}:`, e, frame.body);
      }
    });
  }

  private _publish(dest: string, body: object): void {
    if (!this.client?.connected) {
      console.warn("[ChatSocket] Cannot publish — not connected. Dest:", dest);
      return;
    }
    this.client.publish({ destination: dest, body: JSON.stringify(body) });
  }

  private _clearSubscriptions(): void {
    for (const sub of this.subscriptions) {
      try { sub.unsubscribe(); } catch (_) { /* already gone */ }
    }
    this.subscriptions = [];
  }
}

// One socket per browser tab
export const chatSocket = new ChatSocketService();