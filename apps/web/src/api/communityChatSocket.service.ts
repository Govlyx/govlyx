import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_BASE_URL } from './axiosConfig';
import { getAuthToken } from '../utils/auth';
import type {
  CommunityMessage,
  TypingIndicator,
} from '../types/CommunityChat.types';

function getToken(): string | null {
  return getAuthToken();
}

function buildAuthHeaders(): Record<string, string> {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

const WS_URL = `${API_BASE_URL}/ws`;

export interface CommunitySocketHandlers {
  onMessage: (msg: CommunityMessage) => void;
  onTyping: (n: TypingIndicator) => void;
  onError: (msg: string) => void;
  onConnected?: () => void;
  onDisconnected?: () => void;
}

class CommunityChatSocketService {
  private client: Client | null = null;
  private messageSub: StompSubscription | null = null;
  private typingSub: StompSubscription | null = null;
  private errorSub: StompSubscription | null = null;
  private activeCommunityId: number | null = null;
  private handlers: CommunitySocketHandlers | null = null;

  connect(handlers: CommunitySocketHandlers): void {
    const token = getToken();
    if (!token) return; // Never open STOMP socket without valid JWT

    // Cleanly tear down socket when page transitions / navigates to avoid BFCache forced termination
    if (typeof window !== 'undefined') {
      window.addEventListener('pagehide', () => this.disconnect(), {
        once: true,
      });
    }

    if (this.client?.active) {
      this.handlers = handlers;
      // If already active but not connected, wait for it. If connected, trigger onConnected.
      if (this.client.connected) {
        handlers.onConnected?.();
        this.subscribeToActiveCommunity();
      }
      return;
    }

    this.handlers = handlers;
    this.client = new Client({
      webSocketFactory: () => {
        console.debug('[CommunitySocket] Connecting to:', WS_URL);
        return new SockJS(WS_URL);
      },
      connectHeaders: buildAuthHeaders(),
      beforeConnect: async () => {
        if (this.client) {
          this.client.connectHeaders = buildAuthHeaders();
        }
      },
      reconnectDelay: 5000,
      onConnect: () => {
        console.debug('[CommunitySocket] Connected successfully');
        this.handlers?.onConnected?.();
        this.subscribeToActiveCommunity();

        // Subscribe to user-specific errors/safety quarantine filters
        if (this.client) {
          this.errorSub = this.client.subscribe(
            '/user/queue/errors',
            (frame) => {
              try {
                const body = JSON.parse(frame.body);
                this.handlers?.onError(
                  body.message || body.error || 'Server validation error',
                );
              } catch {
                this.handlers?.onError(frame.body || 'Server error');
              }
            },
          );
        }
      },
      onStompError: (frame) => {
        const msg = frame.headers?.message ?? 'WebSocket protocol error';
        console.error('[CommunitySocket] STOMP error:', msg, frame);
        this.handlers?.onError(msg);
      },
      onWebSocketError: (event) => {
        console.error('[CommunitySocket] WebSocket error:', event);
        this.handlers?.onError('Connection lost — reconnecting…');
      },
      onWebSocketClose: (event) => {
        console.debug('[CommunitySocket] Closed:', event.code, event.reason);
      },
      onDisconnect: () => {
        this.handlers?.onDisconnected?.();
      },
      debug: import.meta.env.DEV
        ? (str) => console.debug('[CommunitySTOMP]', str)
        : () => {},
    });

    this.client.activate();
  }

  disconnect(): void {
    this.unsubscribeCurrent();
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    this.handlers = null;
  }

  joinCommunity(communityId: number): void {
    const isNew = this.activeCommunityId !== communityId;
    this.activeCommunityId = communityId;
    if (isNew && this.client?.connected) {
      this.subscribeToActiveCommunity();
    }
  }

  private subscribeToActiveCommunity(): void {
    if (
      !this.client ||
      !this.client.connected ||
      this.activeCommunityId === null
    )
      return;

    // Unsubscribe from previous community subs (but not errorSub)
    if (this.messageSub) {
      try {
        this.messageSub.unsubscribe();
      } catch {}
      this.messageSub = null;
    }
    if (this.typingSub) {
      try {
        this.typingSub.unsubscribe();
      } catch {}
      this.typingSub = null;
    }

    const cid = this.activeCommunityId;
    console.debug(`[CommunitySocket] Subscribing to community ${cid}`);

    this.messageSub = this.client.subscribe(
      `/topic/community.${cid}.messages`,
      (frame: IMessage) => {
        try {
          const msg = JSON.parse(frame.body) as CommunityMessage;
          this.handlers?.onMessage(msg);
        } catch (e) {
          console.error('[CommunitySocket] Failed to parse message body:', e);
        }
      },
    );

    this.typingSub = this.client.subscribe(
      `/topic/community.${cid}.typing`,
      (frame: IMessage) => {
        try {
          const typingInfo = JSON.parse(frame.body) as TypingIndicator;
          this.handlers?.onTyping(typingInfo);
        } catch (e) {
          console.error(
            '[CommunitySocket] Failed to parse typing info body:',
            e,
          );
        }
      },
    );
  }

  private unsubscribeCurrent(): void {
    if (this.messageSub) {
      try {
        this.messageSub.unsubscribe();
      } catch {}
      this.messageSub = null;
    }
    if (this.typingSub) {
      try {
        this.typingSub.unsubscribe();
      } catch {}
      this.typingSub = null;
    }
    if (this.errorSub) {
      try {
        this.errorSub.unsubscribe();
      } catch {}
      this.errorSub = null;
    }
    this.activeCommunityId = null;
  }

  sendMessage(
    communityId: number,
    payload: {
      content?: string;
      replyToId?: number;
      sharedPostId?: number;
      clientSideId?: string;
    },
  ): void {
    this._publish(`/app/community.${communityId}.send`, payload);
  }

  sendTyping(communityId: number, typing: boolean): void {
    this._publish(`/app/community.${communityId}.typing`, { typing });
  }

  private _publish(dest: string, body: object): void {
    if (!this.client?.connected) {
      console.warn(
        '[CommunitySocket] Cannot publish — not connected. Dest:',
        dest,
      );
      return;
    }
    this.client.publish({ destination: dest, body: JSON.stringify(body) });
  }

  get isConnected(): boolean {
    return this.client?.connected ?? false;
  }
}

export const communityChatSocket = new CommunityChatSocketService();
