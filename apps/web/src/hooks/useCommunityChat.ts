import { useState, useEffect, useRef, useCallback } from "react";
import { communityChatSocket } from "../api/communityChatSocket.service";
import { communityService } from "../api/communityService";
import type { CommunityMessage, TypingIndicator } from "../types/CommunityChat.types";
import { showToast } from "../utils/toast";

interface UserProfile {
  id: number;
  username: string;
  actualUsername?: string;
  profileImage: string | null;
}

function normalizeMessage(msg: any): CommunityMessage {
  if (!msg) return msg;
  const normalized = { ...msg };
  if (normalized.pinned !== undefined && normalized.isPinned === undefined) {
    normalized.isPinned = normalized.pinned;
  }
  if (normalized.deleted !== undefined && normalized.isDeleted === undefined) {
    normalized.isDeleted = normalized.deleted;
  }
  if (normalized.edited !== undefined && normalized.isEdited === undefined) {
    normalized.isEdited = normalized.edited;
  }
  return normalized;
}

export function useCommunityChat(communityId: number, currentUser: UserProfile | null) {
  const [messages, setMessages] = useState<CommunityMessage[]>([]);
  const [typingUsers, setTypingUsers] = useState<{ [userId: number]: { username: string; timestamp: number } }>({});
  const [isLoading, setIsLoading] = useState(true);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pinnedMessage, setPinnedMessage] = useState<CommunityMessage | null>(null);

  const lastTypingSentRef = useRef<number>(0);
  const pendingMessagesRef = useRef<Set<string>>(new Set());

  // ── 1. Fetch Initial Messages ──
  const fetchInitialMessages = useCallback(async () => {
    if (!communityId) return;
    setIsLoading(true);
    setError(null);
    setPinnedMessage(null); // Clear pinned message on room switch
    try {
      const response = await communityService.getChatMessages(communityId, null, 25);
      // Expected structure: ApiResponse<CommunityMessage[]> or direct list or paginated payload
      const paged = response?.data ?? response;
      const list: CommunityMessage[] = Array.isArray(paged)
        ? paged
        : (Array.isArray(paged?.data)
          ? paged.data
          : (Array.isArray(paged?.content)
            ? paged.content
            : []));
      
      const hasMoreFlag = typeof paged?.hasMore === "boolean"
        ? paged.hasMore
        : (typeof response?.hasMore === "boolean"
          ? response.hasMore
          : list.length >= 25);

      // If descending (newest first), reverse it so older is at the top, newer is at bottom
      const sorted = [...list].reverse().map(normalizeMessage);
      setMessages(sorted);
      setHasMore(hasMoreFlag);

      // Fetch pinned messages
      try {
        if (communityService.getPinnedMessages) {
          const pinRes = await communityService.getPinnedMessages(communityId);
          const pinList = pinRes?.data ?? pinRes;
          if (Array.isArray(pinList) && pinList.length > 0) {
            const firstPin = normalizeMessage(pinList[0]);
            if (firstPin && !firstPin.isDeleted) {
              setPinnedMessage(firstPin);
            } else {
              setPinnedMessage(null);
            }
          }
        }
      } catch (pinErr) {
        console.error("Failed to load pinned messages:", pinErr);
      }
    } catch (err) {
      console.error("Failed to load initial chat history:", err);
      setError("Failed to load chat history.");
    } finally {
      setIsLoading(false);
    }
  }, [communityId]);

  // ── 2. Fetch Older Messages (Pagination) ──
  const loadMoreMessages = useCallback(async () => {
    if (isFetchingMore || !hasMore || !messages.length || !communityId) return;
    setIsFetchingMore(true);
    try {
      // The cursor is the oldest message ID we have (first item in messages array)
      const cursorId = messages[0].id ? String(messages[0].id) : null;
      const response = await communityService.getChatMessages(communityId, cursorId, 25);
      const paged = response?.data ?? response;
      const list: CommunityMessage[] = Array.isArray(paged)
        ? paged
        : (Array.isArray(paged?.data)
          ? paged.data
          : (Array.isArray(paged?.content)
            ? paged.content
            : []));

      const hasMoreFlag = typeof paged?.hasMore === "boolean"
        ? paged.hasMore
        : (typeof response?.hasMore === "boolean"
          ? response.hasMore
          : list.length >= 25);

      if (list.length === 0) {
        if (hasMore) {
          setHasMore(false);
        }
      } else {
        const sorted = [...list].reverse().map(normalizeMessage);
        setMessages((prev) => [...sorted, ...prev]);
        setHasMore(hasMoreFlag);
      }
    } catch (err) {
      console.error("Failed to load older messages:", err);
    } finally {
      setIsFetchingMore(false);
    }
  }, [communityId, messages, isFetchingMore, hasMore]);

  // ── 3. WebSocket Handlers ──
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handleIncomingMessage = useCallback((rawMsg: any) => {
    const msg = normalizeMessage(rawMsg);
    if (msg.isDeleted === true) {
      setPinnedMessage((currentPin) => {
        const pinId = currentPin?.id || currentPin?.messageId;
        const msgId = msg.id || msg.messageId;
        if (pinId && msgId && String(pinId) === String(msgId)) {
          return null;
        }
        return currentPin;
      });
      setMessages((prev) =>
        prev.map((m) => {
          const id = m.id || m.messageId;
          if (id && String(id) === String(msg.messageId || msg.id)) {
            return {
              ...m,
              isDeleted: true,
              deletedByType: msg.deletedByType || "USER",
              content: msg.deletedByType === "ADMINISTRATOR" ? "This message was deleted by the administrator" : "This message was deleted by the user",
            };
          }
          return m;
        })
      );
      return;
    }

    // Handle pin updates via websocket
    if (typeof msg.isPinned === "boolean") {
      setPinnedMessage((currentPin) => {
        if (msg.isPinned) {
          return msg;
        } else {
          const pinId = currentPin?.id || currentPin?.messageId;
          const msgId = msg.id || msg.messageId;
          if (pinId && msgId && String(pinId) === String(msgId)) {
            return null;
          }
        }
        return currentPin;
      });
    }

    setMessages((prev) => {
      // 1. Check if this resolves an optimistic/pending message
      // Since backend doesn't return clientSideId, we match by sender and content:
      const pendingIndex = prev.findIndex(
        (m) => !m.id && m.sender.id === msg.sender.id && m.content === msg.content
      );

      if (pendingIndex !== -1) {
        const updated = [...prev];
        updated[pendingIndex] = msg;
        return updated;
      }

      // 2. If message is already in the list, update it in place (e.g. pin toggles, edits)
      if (prev.some((m) => m.id === msg.id)) {
        return prev.map((m) => (m.id === msg.id ? { ...m, ...msg } : m));
      }

      // 3. Prevent appending older messages (like pin-updates for history messages) to the bottom
      if (prev.length > 0) {
        const lastMsg = prev[prev.length - 1];
        if (msg.id && lastMsg.id && msg.id < lastMsg.id) {
          return prev;
        }
      }

      // 4. Otherwise append new message to the bottom
      return [...prev, msg];
    });
  }, []);

  const handleIncomingTyping = useCallback((n: TypingIndicator) => {
    if (currentUser && n.userId === currentUser.id) return; // ignore our own typing echoes
    setTypingUsers((prev) => ({
      ...prev,
      [n.userId]: {
        username: n.username,
        timestamp: Date.now(),
      },
    }));
  }, [currentUser]);

  // ── 4. Setup Socket Connection ──
  useEffect(() => {
    if (!communityId) return;

    // Trigger initial REST fetch
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchInitialMessages();

    // Connect & subscribe
    communityChatSocket.connect({
      onMessage: handleIncomingMessage,
      onTyping: handleIncomingTyping,
      onError: (errMsg) => {
        console.error("[CommunityChatHook] Socket error:", errMsg);
        setError(errMsg);
        showToast.error(errMsg || "Failed to send message. Please try again.");
      },
    });

    communityChatSocket.joinCommunity(communityId);

    return () => {
      // On unmount/switch, we tell socket service to clear subscriptions for this community
      // and disconnect if this was the last consumer.
      communityChatSocket.disconnect();
    };
  }, [communityId, fetchInitialMessages, handleIncomingMessage, handleIncomingTyping]);

  // ── 5. Auto-clearing Typing Indicators (3-second timeout) ──
  useEffect(() => {
    const timer = setInterval(() => {
      const now = Date.now();
      setTypingUsers((prev) => {
        const updated = { ...prev };
        let changed = false;
        for (const [userIdStr, data] of Object.entries(updated)) {
          if (now - data.timestamp > 3000) {
            delete updated[Number(userIdStr)];
            changed = true;
          }
        }
        return changed ? updated : prev;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  // ── 6. Local Expiry / Pruning of Disappearing Messages ──
  useEffect(() => {
    const timer = setInterval(() => {
      const now = Date.now();
      setMessages((prev) => {
        const filtered = prev.filter((m) => {
          if (!m.expiresAt) return true;
          return new Date(m.expiresAt).getTime() > now;
        });
        return filtered.length === prev.length ? prev : filtered;
      });

      setPinnedMessage((prevPin) => {
        if (!prevPin || !prevPin.expiresAt) return prevPin;
        if (new Date(prevPin.expiresAt).getTime() <= now) {
          return null;
        }
        return prevPin;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  // ── 7. Send Actions ──
  const sendMessage = useCallback((
    content?: string,
    replyToId?: number,
    sharedPostId?: number,
    sharedPostData?: { id: number; content: string; authorUsername?: string }
  ) => {
    if (!communityId || !currentUser) return;

    // Generate correlation ID
    const clientSideId = `opt-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    pendingMessagesRef.current.add(clientSideId);

    // Optimistic message DTO
    const optimisticMessage: CommunityMessage = {
      messageId: clientSideId,
      content,
      sender: {
        id: currentUser.id,
        username: currentUser.username,
        actualUsername: currentUser.actualUsername,
        profileImage: currentUser.profileImage,
      },
      messageType: sharedPostId ? "SHARE_POST" : "TEXT",
      sharedPost: sharedPostData
        ? {
            id: sharedPostData.id,
            content: sharedPostData.content,
            authorUsername: sharedPostData.authorUsername || "author",
          }
        : undefined,
      replyToId,
      createdAt: new Date().toISOString(),
      clientSideId,
    };

    // Append optimistically
    setMessages((prev) => [...prev, optimisticMessage]);

    // Send payload
    communityChatSocket.sendMessage(communityId, {
      content,
      replyToId: replyToId || undefined,
      sharedPostId,
      clientSideId,
    });
  }, [communityId, currentUser]);

  const sendTyping = useCallback(() => {
    if (!communityId) return;
    const now = Date.now();
    // Throttle outgoing typing signals to once per 2 seconds
    if (now - lastTypingSentRef.current > 2000) {
      lastTypingSentRef.current = now;
      communityChatSocket.sendTyping(communityId, true);
    }
  }, [communityId]);

  const deleteMessage = useCallback(async (messageId: number | string) => {
    try {
      await communityService.deleteChatMessage(communityId, messageId);
    } catch (err) {
      console.error("Failed to delete community message:", err);
      throw err;
    }
  }, [communityId]);

  return {
    messages,
    typingUsers: Object.values(typingUsers),
    isLoading,
    isFetchingMore,
    hasMore,
    error,
    setError,
    sendMessage,
    sendTyping,
    loadMoreMessages,
    fetchInitialMessages,
    deleteMessage,
    pinnedMessage,
  };
}
