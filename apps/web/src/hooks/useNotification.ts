import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import axiosInstance from '../api/axiosConfig';
import type { Notification } from '../types/notification';

/**
 * Hook to fetch and cache the unread notification count.
 * Refetches every 60 seconds to maintain real-time-like updates.
 */
export const useUnreadNotificationsCount = (options?: {
  enabled?: boolean;
}) => {
  const hasToken =
    typeof window !== 'undefined' && !!localStorage.getItem('token');
  return useQuery({
    queryKey: ['unreadNotificationsCount', hasToken],
    queryFn: async () => {
      if (!hasToken) return 0;
      try {
        const response = await axiosInstance.get<any>(
          '/api/notifications/unread/count',
        );
        const raw = response.data;
        const count =
          raw?.count ??
          raw?.data?.count ??
          raw?.data ??
          (typeof raw === 'number' ? raw : 0);
        return typeof count === 'number' ? count : 0;
      } catch {
        return 0;
      }
    },
    refetchInterval: 60 * 1000, // Refresh every minute
    staleTime: 1000 * 30, // 30 seconds
    enabled: options?.enabled !== false && hasToken,
  });
};

/**
 * Hook to fetch the full list of notifications.
 */
export const useNotifications = (limit = 20) => {
  const hasToken =
    typeof window !== 'undefined' && !!localStorage.getItem('token');
  return useQuery({
    queryKey: ['notifications', limit, hasToken],
    queryFn: async () => {
      if (!hasToken) return [];
      const response = await axiosInstance.get<any>(
        `/api/notifications?limit=${limit}`,
      );
      const raw = response.data;
      const list =
        raw?.data?.content ??
        raw?.data?.data ??
        raw?.data ??
        raw?.content ??
        (Array.isArray(raw) ? raw : []);
      return Array.isArray(list) ? list : [];
    },
    staleTime: 1000 * 30, // 30 seconds
    enabled: hasToken,
  });
};

/**
 * Hook for notification-related actions.
 */
export const useNotificationActions = () => {
  const queryClient = useQueryClient();

  // Mark a single notification as read
  const markAsRead = useMutation({
    mutationFn: async (id: number) => {
      try {
        await axiosInstance.put(`/api/notifications/${id}/read`);
      } catch (err: any) {
        if (err?.response?.status === 404 || err?.response?.status === 405) {
          await axiosInstance.patch(`/api/notifications/${id}/read`);
        } else {
          throw err;
        }
      }
    },
    onMutate: async (id: number) => {
      await queryClient.cancelQueries({ queryKey: ['notifications'] });
      await queryClient.cancelQueries({
        queryKey: ['unreadNotificationsCount'],
      });

      queryClient.setQueriesData(
        { queryKey: ['notifications'] },
        (old: any) => {
          if (!Array.isArray(old)) return old;
          return old.map((n: Notification) =>
            n.id === id ? { ...n, isRead: true } : n,
          );
        },
      );
      queryClient.setQueryData(
        ['unreadNotificationsCount'],
        (prev: number | undefined) => Math.max(0, (prev ?? 1) - 1),
      );
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['unreadNotificationsCount'] });
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  // Mark all notifications as read
  const markAllAsRead = useMutation({
    mutationFn: async () => {
      try {
        await axiosInstance.put('/api/notifications/read-all');
      } catch (err: any) {
        if (err?.response?.status === 404 || err?.response?.status === 405) {
          try {
            await axiosInstance.post('/api/notifications/mark-all-read');
          } catch {
            await axiosInstance.put('/api/notifications/read');
          }
        } else {
          throw err;
        }
      }
    },
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: ['notifications'] });
      await queryClient.cancelQueries({
        queryKey: ['unreadNotificationsCount'],
      });

      const prevNotifications = queryClient.getQueriesData({
        queryKey: ['notifications'],
      });
      const prevCount = queryClient.getQueryData<number>([
        'unreadNotificationsCount',
      ]);

      queryClient.setQueriesData(
        { queryKey: ['notifications'] },
        (old: any) => {
          if (!Array.isArray(old)) return old;
          return old.map((n: Notification) => ({ ...n, isRead: true }));
        },
      );
      queryClient.setQueryData(['unreadNotificationsCount'], 0);

      return { prevNotifications, prevCount };
    },
    onError: (_err, _vars, context) => {
      if (context?.prevNotifications) {
        context.prevNotifications.forEach(([queryKey, data]) => {
          queryClient.setQueryData(queryKey, data);
        });
      }
      if (context?.prevCount !== undefined) {
        queryClient.setQueryData(
          ['unreadNotificationsCount'],
          context.prevCount,
        );
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['unreadNotificationsCount'] });
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  // Delete a single notification
  const deleteNotification = useMutation({
    mutationFn: async (id: number) => {
      await axiosInstance.delete(`/api/notifications/${id}`);
    },
    onMutate: async (id: number) => {
      await queryClient.cancelQueries({ queryKey: ['notifications'] });
      await queryClient.cancelQueries({
        queryKey: ['unreadNotificationsCount'],
      });

      queryClient.setQueriesData(
        { queryKey: ['notifications'] },
        (old: any) => {
          if (!Array.isArray(old)) return old;
          return old.filter((n: Notification) => n.id !== id);
        },
      );
      queryClient.setQueryData(
        ['unreadNotificationsCount'],
        (prev: number | undefined) => Math.max(0, (prev ?? 1) - 1),
      );
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['unreadNotificationsCount'] });
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  // Delete all notifications (Clear all)
  const deleteAllNotifications = useMutation({
    mutationFn: async () => {
      try {
        await axiosInstance.delete('/api/notifications/all');
      } catch (err: any) {
        if (err?.response?.status === 404 || err?.response?.status === 405) {
          try {
            await axiosInstance.delete('/api/notifications/clear');
          } catch {
            await axiosInstance.delete('/api/notifications');
          }
        } else {
          throw err;
        }
      }
    },
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: ['notifications'] });
      await queryClient.cancelQueries({
        queryKey: ['unreadNotificationsCount'],
      });

      const prevNotifications = queryClient.getQueriesData({
        queryKey: ['notifications'],
      });
      const prevCount = queryClient.getQueryData<number>([
        'unreadNotificationsCount',
      ]);

      queryClient.setQueriesData({ queryKey: ['notifications'] }, () => []);
      queryClient.setQueryData(['unreadNotificationsCount'], 0);

      return { prevNotifications, prevCount };
    },
    onError: (_err, _vars, context) => {
      if (context?.prevNotifications) {
        context.prevNotifications.forEach(([queryKey, data]) => {
          queryClient.setQueryData(queryKey, data);
        });
      }
      if (context?.prevCount !== undefined) {
        queryClient.setQueryData(
          ['unreadNotificationsCount'],
          context.prevCount,
        );
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['unreadNotificationsCount'] });
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  return {
    markAsRead,
    markAllAsRead,
    deleteNotification,
    deleteAllNotifications,
  };
};
