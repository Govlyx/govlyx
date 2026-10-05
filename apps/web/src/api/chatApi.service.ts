// src/api/chatApi.service.ts
// ─────────────────────────────────────────────────────────────────────────────
// All REST calls to /api/chat/*
//
// ⚠️  IMPORTANT — uses YOUR existing axiosConfig instance so the JWT
//     Authorization header is injected automatically by your interceptor.
//     Just change the import path below if your export name differs.
//
// If axiosConfig.ts exports:   export default axiosInstance  → no change needed
// If it exports:               export const api = axios...  → change to { api }
// ─────────────────────────────────────────────────────────────────────────────

import axiosInstance from './axiosConfig'; // ← your existing configured axios
import type {
  ApiResponse,
  ChatMessageDto,
  ChatSessionDto,
  SearchResponseData,
} from '../types/Chat.types';

// ── Custom error classes ──────────────────────────────────────────────────────

/** Thrown when any /api/chat call returns HTTP 401 */
export class ChatAuthError extends Error {
  constructor() {
    super('Session expired — please log in again.');
    this.name = 'ChatAuthError';
  }
}

/** Thrown for other HTTP 4xx/5xx errors */
export class ChatApiError extends Error {
  public readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = 'ChatApiError';
  }
}

// ── Internal helpers ──────────────────────────────────────────────────────────

async function post<T>(url: string, body?: unknown): Promise<ApiResponse<T>> {
  try {
    const { data } = await axiosInstance.post<ApiResponse<T>>(url, body ?? {});
    return data;
  } catch (err: unknown) {
    throw _normalise(err);
  }
}

async function postForm<T>(
  url: string,
  form: FormData,
): Promise<ApiResponse<T>> {
  try {
    const { data } = await axiosInstance.post<ApiResponse<T>>(url, form, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return data;
  } catch (err: unknown) {
    throw _normalise(err);
  }
}

async function get<T>(
  url: string,
  params?: Record<string, unknown>,
): Promise<ApiResponse<T>> {
  try {
    const { data } = await axiosInstance.get<ApiResponse<T>>(url, { params });
    return data;
  } catch (err: unknown) {
    throw _normalise(err);
  }
}

async function del<T>(url: string): Promise<ApiResponse<T>> {
  try {
    const { data } = await axiosInstance.delete<ApiResponse<T>>(url);
    return data;
  } catch (err: unknown) {
    throw _normalise(err);
  }
}

function _normalise(err: unknown): Error {
  // axios error with a response
  if (
    err != null &&
    typeof err === 'object' &&
    'response' in err &&
    err.response != null &&
    typeof err.response === 'object' &&
    'status' in err.response
  ) {
    const res = err.response as {
      status: number;
      data?: ApiResponse<unknown> | any;
    };
    if (res.status === 401) return new ChatAuthError();
    const msg =
      (typeof res.data === 'string' ? res.data : undefined) ||
      res.data?.error ||
      res.data?.message ||
      res.data?.details ||
      (err as any)?.message ||
      'Request failed';
    return new ChatApiError(res.status, msg);
  }
  if (err instanceof Error) return err;
  return new Error('An unexpected error occurred.');
}

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * POST /api/chat/search
 * Instant match  → { matched: true,  session: ChatSessionDto }
 * Added to queue → { matched: false, queueSize: number }
 */
export const startSearch = (): Promise<ApiResponse<SearchResponseData>> =>
  post<SearchResponseData>('/api/chat/search');

/**
 * POST /api/chat/search/cancel
 * Removes user from matchmaking queue.
 */
export const cancelSearch = (): Promise<ApiResponse<{ cancelled: boolean }>> =>
  post<{ cancelled: boolean }>('/api/chat/search/cancel');

/**
 * GET /api/chat/session
 * Returns active session or data: null.
 * Polled every 2.5s while user is in the matchmaking queue.
 */
export const getCurrentSession = (): Promise<ApiResponse<ChatSessionDto>> =>
  get<ChatSessionDto>('/api/chat/session');

/**
 * POST /api/chat/session/leave
 * Ends the session server-side + notifies partner via WebSocket.
 */
export const leaveSession = (): Promise<ApiResponse<{ left: boolean }>> =>
  post<{ left: boolean }>('/api/chat/session/leave');

/**
 * GET /api/chat/messages?limit=N
 * Returns last N decrypted messages for the current session.
 */
export const getMessages = (
  limit = 50,
): Promise<ApiResponse<ChatMessageDto[]>> =>
  get<ChatMessageDto[]>('/api/chat/messages', { limit });

/**
 * POST /api/chat/{sessionId}/media  (multipart/form-data)
 * Sends rich media (image, video, sticker) to the session.
 * Constructs FormData with: file (Blob), type, viewTimer, viewOnce, replyToId.
 */
export const sendMedia = (
  sessionId: string,
  media: {
    type: string;
    file: Blob;
    mimeType: string;
    mediaName?: string;
    viewTimer?: number;
    viewOnce?: boolean;
    replyToId?: string;
  },
): Promise<ApiResponse<{ messageId: string }>> => {
  const form = new FormData();
  form.append('file', media.file, media.mediaName ?? 'media');
  form.append('type', media.type);
  if (media.viewTimer !== undefined)
    form.append('viewTimer', String(media.viewTimer));
  if (media.viewOnce !== undefined)
    form.append('viewOnce', String(media.viewOnce));
  if (media.replyToId) form.append('replyToId', media.replyToId);
  return postForm<{ messageId: string }>(`/api/chat/${sessionId}/media`, form);
};

/**
 * DELETE /api/chat/{sessionId}/media/{messageId}
 * Wipes the server-side in-RAM media for a view-once message.
 * Triggers a MEDIA_WIPED socket event to both participants.
 */
export const deleteMedia = (
  sessionId: string,
  messageId: string,
): Promise<ApiResponse<{ wiped: boolean }>> =>
  del<{ wiped: boolean }>(`/api/chat/${sessionId}/media/${messageId}`);

export const sendCommunityMessage = async (
  communityId: number,
  payload: { content?: string; replyToId?: number; sharedPostId?: number },
  idempotencyKey?: string,
) => {
  const response = await axiosInstance.post(
    `/api/communities/${communityId}/chat/messages`,
    payload,
    {
      headers: idempotencyKey
        ? { 'Idempotency-Key': idempotencyKey }
        : undefined,
    },
  );
  return response.data;
};
