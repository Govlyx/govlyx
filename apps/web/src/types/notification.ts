/**
 * src/types/notification.ts
 *
 * Frontend interfaces for the Notification system,
 * matching the backend NotificationDto and NotificationType.
 */

export type NotificationType =
  | "POST_LIKE"
  | "POST_COMMENT"
  | "COMMENT_REPLY"
  | "FOLLOW"
  | "MENTION"
  | "COMMUNITY_INVITE"
  | "COMMUNITY_JOIN_REQUEST"
  | "COMMUNITY_JOIN_ACCEPT"
  | "COMMUNITY_JOIN_REJECT"
  | "COMMUNITY_INVITE_ACCEPT"
  | "COMMUNITY_INVITE_DECLINE"
  | "SYSTEM_ANNOUNCEMENT"
  | "BROADCAST"
  | "COMMUNITY_POST_APPROVED"
  | "COMMUNITY_POST_REJECTED"
  | "COMMUNITY_DELETED"
  | "COMMUNITY_ROLE_CHANGED";

export interface Notification {
  id: number;
  notificationType: NotificationType;
  title: string;
  message: string;
  referenceId?: number;
  referenceType?: string;
  isRead: boolean;
  readAt?: string;
  createdAt: string;
  actionUrl?: string;
  timeAgo: string;
  category?: string;
  typeDescription?: string;
  joinRequestStatus?: "approved" | "rejected" | "APPROVED" | "REJECTED" | null;

  // Triggered by user information
  triggeredByUserId?: number;
  triggeredByUsername?: string;
  triggeredByDisplayName?: string;
  triggeredByProfileImage?: string;
}

export interface UnreadCountResponse {
  count: number;
  hasUnread: boolean;
}

export interface NotificationSummaryCounts {
  all: number;
  unread: number;
  invites: number;
  interactions: number;
}
