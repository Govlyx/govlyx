import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_BASE_URL } from './axiosConfig';
import { getAuthToken } from '../utils/auth';

export interface PostStatsUpdate {
  postId: number;
  postType?: 'POST' | 'SOCIAL_POST' | 'ISSUE' | 'BROADCAST' | 'POLL' | string;
  type?: string;
  commentCount?: number;
  commentsCount?: number;
  shareCount?: number;
  sharesCount?: number;
  likeCount?: number;
  likesCount?: number;
  dislikeCount?: number;
  dislikesCount?: number;
  saveCount?: number;
  [key: string]: any;
}

const WS_URL = `${API_BASE_URL}/ws`;

class FeedSocketService {
  private client: Client | null = null;
  private feedSub: StompSubscription | null = null;
  private userFeedSub: StompSubscription | null = null;
  private postSubs: Map<number, { count: number; sub: StompSubscription }> =
    new Map();
  private isConnecting: boolean = false;

  public connect(): void {
    const token = getAuthToken();
    if (!token) return; // Never open STOMP socket without valid JWT

    if (this.client?.active || this.isConnecting) {
      return;
    }

    if (typeof window !== 'undefined') {
      window.addEventListener('pagehide', () => this.disconnect(), {
        once: true,
      });
    }

    this.isConnecting = true;

    this.client = new Client({
      webSocketFactory: () => {
        if (import.meta.env.DEV) {
          console.debug('[FeedSocket] Connecting to:', WS_URL);
        }
        return new SockJS(WS_URL);
      },
      connectHeaders: this.getAuthHeaders(),
      beforeConnect: async () => {
        if (this.client) {
          this.client.connectHeaders = this.getAuthHeaders();
        }
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.isConnecting = false;
        if (import.meta.env.DEV) {
          console.debug('[FeedSocket] Connected to', WS_URL);
        }
        this.subscribeGlobalFeed();
      },
      onStompError: (frame) => {
        this.isConnecting = false;
        console.warn(
          '[FeedSocket] STOMP error:',
          frame.headers?.message,
          frame,
        );
      },
      onWebSocketError: (event) => {
        this.isConnecting = false;
        if (import.meta.env.DEV) {
          console.debug('[FeedSocket] WebSocket error:', event);
        }
      },
      onWebSocketClose: () => {
        this.isConnecting = false;
      },
      debug: import.meta.env.DEV
        ? (str) => console.debug('[FeedSTOMP]', str)
        : () => {},
    });

    this.client.activate();
  }

  public disconnect(): void {
    this.feedSub?.unsubscribe();
    this.feedSub = null;

    this.userFeedSub?.unsubscribe();
    this.userFeedSub = null;

    this.postSubs.forEach(({ sub }) => sub.unsubscribe());
    this.postSubs.clear();

    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    this.isConnecting = false;
  }

  public get isConnected(): boolean {
    return this.client?.connected ?? false;
  }

  private getAuthHeaders(): Record<string, string> {
    const token = getAuthToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  private subscribeGlobalFeed(): void {
    if (!this.client?.connected) return;

    // 1. Global feed interaction topic
    try {
      this.feedSub = this.client.subscribe(
        '/topic/feed.updates',
        (frame: IMessage) => {
          this.handleIncomingStats(frame.body);
        },
      );
    } catch (err) {
      console.warn(
        '[FeedSocket] Failed to subscribe to /topic/feed.updates',
        err,
      );
    }

    // 2. User specific queue for interactions if authenticated
    try {
      this.userFeedSub = this.client.subscribe(
        '/user/queue/feed.updates',
        (frame: IMessage) => {
          this.handleIncomingStats(frame.body);
        },
      );
    } catch {
      // Ignored for unauthenticated users
    }

    // 3. Re-subscribe any active individual post subscriptions
    this.postSubs.forEach((val, postId) => {
      try {
        val.sub = this.client!.subscribe(
          `/topic/post.${postId}.updates`,
          (frame: IMessage) => {
            this.handleIncomingStats(frame.body);
          },
        );
      } catch (err) {
        console.warn(`[FeedSocket] Failed to resubscribe post ${postId}`, err);
      }
    });
  }

  private handleIncomingStats(rawBody: string): void {
    try {
      const data = JSON.parse(rawBody) as PostStatsUpdate;
      if (!data || typeof data.postId !== 'number') return;

      const commentCount =
        typeof data.commentCount === 'number'
          ? data.commentCount
          : typeof data.commentsCount === 'number'
            ? data.commentsCount
            : undefined;

      const shareCount =
        typeof data.shareCount === 'number'
          ? data.shareCount
          : typeof data.sharesCount === 'number'
            ? data.sharesCount
            : undefined;

      const likeCount =
        typeof data.likeCount === 'number'
          ? data.likeCount
          : typeof data.likesCount === 'number'
            ? data.likesCount
            : undefined;

      const dislikeCount =
        typeof data.dislikeCount === 'number'
          ? data.dislikeCount
          : typeof data.dislikesCount === 'number'
            ? data.dislikesCount
            : undefined;

      const saveCount =
        typeof data.saveCount === 'number' ? data.saveCount : undefined;

      // Dispatch local POST_SYNC event so all PostCards sync in real-time
      window.dispatchEvent(
        new CustomEvent('POST_SYNC', {
          detail: {
            postId: data.postId,
            source: 'websocket',
            commentCount,
            shareCount,
            likeCount,
            dislikeCount,
            saveCount,
            raw: data,
          },
        }),
      );
    } catch (err) {
      console.error(
        '[FeedSocket] Error parsing feed stats update:',
        err,
        rawBody,
      );
    }
  }

  /**
   * Subscribe to real-time updates for a single post (e.g. On post detail view)
   */
  public subscribePost(
    postId: number,
    callback?: (data: PostStatsUpdate) => void,
  ): () => void {
    if (!postId) return () => {};

    const existing = this.postSubs.get(postId);
    if (existing) {
      existing.count += 1;
    } else if (this.client?.connected) {
      try {
        const sub = this.client.subscribe(
          `/topic/post.${postId}.updates`,
          (frame: IMessage) => {
            this.handleIncomingStats(frame.body);
            if (callback) {
              try {
                callback(JSON.parse(frame.body));
              } catch {}
            }
          },
        );
        this.postSubs.set(postId, { count: 1, sub });
      } catch (err) {
        console.warn(
          `[FeedSocket] Failed to subscribe to /topic/post.${postId}.updates`,
          err,
        );
      }
    }

    return () => {
      const current = this.postSubs.get(postId);
      if (current) {
        current.count -= 1;
        if (current.count <= 0) {
          current.sub.unsubscribe();
          this.postSubs.delete(postId);
        }
      }
    };
  }
}

export const feedSocket = new FeedSocketService();
