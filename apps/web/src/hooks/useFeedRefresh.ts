import { useState, useEffect, useCallback, useRef } from 'react';
import { chatSocket } from '../api/chatSocket.service';
import axiosInstance from '../api/axiosConfig';
import type { StompSubscription } from '@stomp/stompjs';

export interface UseFeedRefreshOptions {
  scope?: 'FOR_YOU' | 'LOCATION' | 'FOLLOWING' | 'OFFICIAL' | 'NEIGHBORHOOD_QA';
  communityId?: number;
  topPostId?: number;
  onRefresh?: () => Promise<void> | void;
  enabled?: boolean;
}

export function useFeedRefresh({
  scope = 'FOR_YOU',
  communityId,
  topPostId,
  onRefresh,
  enabled = true,
}: UseFeedRefreshOptions) {
  const [newPostCount, setNewPostCount] = useState<number>(0);
  const topPostIdRef = useRef<number | undefined>(topPostId);
  const onRefreshRef = useRef<(() => Promise<void> | void) | undefined>(
    onRefresh,
  );
  const isRefreshingRef = useRef<boolean>(false);

  useEffect(() => {
    topPostIdRef.current = topPostId;
  }, [topPostId]);

  useEffect(() => {
    onRefreshRef.current = onRefresh;
  }, [onRefresh]);

  // Clear count & trigger refresh callback with reentrancy guard
  const clearAndRefresh = useCallback(async () => {
    if (isRefreshingRef.current) return;
    isRefreshingRef.current = true;
    setNewPostCount(0);
    try {
      if (onRefreshRef.current) {
        await onRefreshRef.current();
      }
    } catch (err) {
      console.error('Failed to refresh feed:', err);
    } finally {
      // Debounce unlock slightly to prevent duplicate triggers during scroll transitions
      setTimeout(() => {
        isRefreshingRef.current = false;
      }, 400);
    }
  }, []);

  // Poll fallback function
  const checkPeek = useCallback(async () => {
    if (!enabled || !topPostIdRef.current) return;
    try {
      const endpoint = communityId
        ? `/api/v1/feed/community/${communityId}/peek`
        : '/api/v1/feed/peek';
      const params: Record<string, any> = {
        afterId: topPostIdRef.current,
      };

      if (!communityId) {
        params.scope = scope;
      }

      const res = await axiosInstance.get(endpoint, { params });
      const json = res.data;
      const count =
        json?.count ??
        (typeof json?.data?.count === 'number' ? json.data.count : 0);

      if (count > 0) {
        setNewPostCount((prev) => Math.max(prev, count));
      }
    } catch {
      // Endpoint may not be ready or network transient — ignore safely
    }
  }, [enabled, scope, communityId]);

  // Setup WebSocket subscription + Polling fallback with Tab Visibility Guard
  useEffect(() => {
    if (!enabled) {
      setNewPostCount(0);
      return;
    }

    let wsSub: StompSubscription | null = null;

    // Only subscribe to WebSocket topic for community feeds (global topic is disabled on backend)
    if (communityId && chatSocket.isConnected) {
      const topic = `/topic/community.${communityId}.new-post`;
      wsSub = chatSocket.subscribeTopic<{ postId?: number; scope?: string }>(
        topic,
        (data) => {
          // Increment count if post is newer than what we currently see
          if (
            !topPostIdRef.current ||
            (data?.postId && data.postId > topPostIdRef.current)
          ) {
            setNewPostCount((prev) => prev + 1);
          } else if (!data?.postId) {
            setNewPostCount((prev) => prev + 1);
          }
        },
      );
    }

    // Interval polling fallback (60s), active only when tab is visible
    let pollInterval: ReturnType<typeof setInterval> | null = null;

    const startPolling = () => {
      if (!pollInterval && document.visibilityState === 'visible') {
        pollInterval = setInterval(() => {
          // If WS is not connected or sub couldn't be registered, fallback to poll peek
          if (!chatSocket.isConnected || !wsSub) {
            checkPeek();
          }
        }, 60000);
      }
    };

    const stopPolling = () => {
      if (pollInterval) {
        clearInterval(pollInterval);
        pollInterval = null;
      }
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        // Run an initial check when coming back to visible tab
        checkPeek();
        startPolling();
      } else {
        stopPolling();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    startPolling();

    // Auto dismiss when scrolled to very top (scrollTop <= 10)
    const mainEl = document.querySelector('main.overflow-y-auto');
    const handleScroll = () => {
      if (isRefreshingRef.current) return;
      const scrollY = mainEl
        ? mainEl.scrollTop
        : window.scrollY || document.documentElement.scrollTop;
      if (scrollY <= 10) {
        setNewPostCount(0);
      }
    };
    if (mainEl) {
      mainEl.addEventListener('scroll', handleScroll, { passive: true });
    }
    window.addEventListener('scroll', handleScroll, { passive: true });

    return () => {
      if (wsSub) {
        try {
          wsSub.unsubscribe();
        } catch (_) {}
      }
      stopPolling();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('scroll', handleScroll);
      if (mainEl) {
        mainEl.removeEventListener('scroll', handleScroll);
      }
    };
  }, [enabled, scope, communityId, checkPeek]);

  return {
    newPostCount,
    clearAndRefresh,
  };
}
