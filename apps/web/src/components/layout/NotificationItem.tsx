import React from "react";
import {
  Trash2, UserPlus, X as XIcon, Check, Shield, CheckCircle2,
  AlertTriangle, Heart, MessageSquare, Sparkles, Megaphone, Bell
} from "lucide-react";
import type { Notification, NotificationType } from "../../types/notification";

const Spin = ({ xs }: { xs?: boolean }) => (
  <span className={`loading loading-spinner ${xs ? "loading-xs" : "loading-sm"}`} />
);

function decodeHtmlEntities(text: string): string {
  if (!text) return "";
  if (typeof document === "undefined") return text;
  const textarea = document.createElement("textarea");
  textarea.innerHTML = text;
  return textarea.value;
}

function cleanNotificationTitle(title: string): string {
  if (!title) return "";
  return title.replace(/^[\p{Extended_Pictographic}\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\s]+/gu, "").trim() || title;
}

const getNotificationBadgeConfig = (type: NotificationType) => {
  switch (type) {
    case "COMMUNITY_ROLE_CHANGED":
      return { icon: Shield, bg: "bg-amber-500", text: "text-white" };
    case "COMMUNITY_INVITE":
      return { icon: UserPlus, bg: "bg-[#1D4ED8]", text: "text-white" };
    case "COMMUNITY_JOIN_REQUEST":
      return { icon: UserPlus, bg: "bg-amber-500", text: "text-white" };
    case "COMMUNITY_JOIN_ACCEPT":
    case "COMMUNITY_INVITE_ACCEPT":
    case "COMMUNITY_POST_APPROVED":
      return { icon: CheckCircle2, bg: "bg-emerald-500", text: "text-white" };
    case "COMMUNITY_JOIN_REJECT":
    case "COMMUNITY_INVITE_DECLINE":
    case "COMMUNITY_POST_REJECTED":
    case "COMMUNITY_DELETED":
      return { icon: AlertTriangle, bg: "bg-rose-500", text: "text-white" };
    case "POST_LIKE":
      return { icon: Heart, bg: "bg-rose-500", text: "text-white" };
    case "POST_COMMENT":
    case "COMMENT_REPLY":
      return { icon: MessageSquare, bg: "bg-blue-500", text: "text-white" };
    case "FOLLOW":
      return { icon: UserPlus, bg: "bg-emerald-500", text: "text-white" };
    case "MENTION":
      return { icon: Sparkles, bg: "bg-indigo-500", text: "text-white" };
    case "SYSTEM_ANNOUNCEMENT":
    case "BROADCAST":
      return { icon: Megaphone, bg: "bg-[#1D4ED8]", text: "text-white" };
    default:
      return { icon: Bell, bg: "bg-slate-500", text: "text-white" };
  }
};

interface NotificationItemProps {
  notification: Notification;
  onRead: (id: number) => void;
  onDelete: (id: number, e: React.MouseEvent) => void;
  onAcceptInvite: (n: Notification, e: React.MouseEvent) => void;
  onDeclineInvite: (n: Notification, e: React.MouseEvent) => void;
  onReviewJoinRequest?: (n: Notification, approve: boolean, e: React.MouseEvent) => void;
  onClick: (n: Notification) => void;
  isAccepting: boolean;
  isAccepted: boolean;
  isDeclined: boolean;
  isReviewingJoinRequest?: boolean;
  joinRequestStatus?: "approved" | "rejected" | "APPROVED" | "REJECTED" | null;
}

const NotificationItem: React.FC<NotificationItemProps> = ({
  notification,
  onRead,
  onDelete,
  onAcceptInvite,
  onDeclineInvite,
  onReviewJoinRequest,
  onClick,
  isAccepting,
  isAccepted,
  isDeclined,
  isReviewingJoinRequest = false,
  joinRequestStatus = null,
}) => {
  const isInvite = notification.notificationType === "COMMUNITY_INVITE";
  const isJoinRequest = notification.notificationType === "COMMUNITY_JOIN_REQUEST";
  const { id, title, message, timeAgo, isRead, triggeredByProfileImage, triggeredByUsername } = notification;

  const rawStatus = joinRequestStatus || notification.joinRequestStatus;
  const status = typeof rawStatus === "string"
    ? (rawStatus.toLowerCase() as "approved" | "rejected")
    : null;

  const handleContainerClick = () => {
    if (!isRead && !isInvite && !isJoinRequest) {
      onRead(id);
    }
    onClick(notification);
  };

  const badgeConfig = getNotificationBadgeConfig(notification.notificationType);
  const BadgeIcon = badgeConfig.icon;

  const isUnread = !isRead && !isAccepted && !isDeclined && !status;

  return (
    <div
      onClick={handleContainerClick}
      className={`p-4 sm:p-4.5 flex gap-3.5 transition-all duration-200 relative group text-left ${
        (isInvite && !isAccepted && !isDeclined) || (isJoinRequest && !status)
          ? "bg-transparent"
          : "cursor-pointer hover:bg-base-300/40 dark:hover:bg-white/[0.03]"
      } ${
        isUnread
          ? "bg-[#1D4ED8]/[0.03] dark:bg-[#1D4ED8]/[0.06]"
          : "bg-transparent"
      }`}
    >
      {/* Unread indicator accent bar */}
      {isUnread && (
        <div className="absolute left-0 top-3 bottom-3 w-1 bg-[#1D4ED8] rounded-r-full" />
      )}

      {/* Avatar & Type Badge */}
      <div className="shrink-0 relative">
        <div className="w-10 h-10 sm:w-11 sm:h-11 rounded-full overflow-hidden border border-black/10 dark:border-white/10 bg-base-300 shadow-xs">
          <img
            src={
              triggeredByProfileImage ||
              `https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(
                triggeredByUsername || "sys"
              )}`
            }
            alt=""
            className="w-full h-full object-cover"
          />
        </div>
        {/* Floating Type Icon Badge */}
        <div
          className={`absolute -bottom-1 -right-1 w-5 h-5 rounded-full ${badgeConfig.bg} ${badgeConfig.text} flex items-center justify-center ring-2 ring-base-200 shadow-xs`}
        >
          <BadgeIcon size={10} className="stroke-[2.5]" />
        </div>
      </div>

      {/* Content Column */}
      <div className="flex-1 min-w-0">
        <div className="flex items-baseline justify-between gap-2">
          <h4 className="text-xs sm:text-sm font-bold text-slate-900 dark:text-white leading-tight truncate notranslate">
            {cleanNotificationTitle(decodeHtmlEntities(title))}
          </h4>
          <span className="text-[10px] sm:text-[11px] font-medium text-slate-400 dark:text-slate-500 whitespace-nowrap shrink-0 notranslate">
            {timeAgo}
          </span>
        </div>

        <p className="text-xs sm:text-[13px] text-slate-600 dark:text-slate-300 mt-1 line-clamp-2 leading-relaxed notranslate">
          {decodeHtmlEntities(message)}
        </p>

        {/* ── Invite action buttons ── */}
        {isInvite && !isAccepted && !isDeclined && (
          <div className="flex items-center gap-2 mt-3">
            <button
              onClick={(e) => onAcceptInvite(notification, e)}
              disabled={isAccepting}
              className="btn btn-xs bg-[#1D4ED8] hover:bg-[#1e40af] text-white border-none gap-1.5 font-bold rounded-xl h-8 px-4 shadow-xs active:scale-95 transition-all cursor-pointer"
            >
              {isAccepting ? <Spin xs /> : <><Check size={13} className="stroke-[2.5]" /> Accept</>}
            </button>
            <button
              onClick={(e) => onDeclineInvite(notification, e)}
              className="btn btn-xs bg-base-300/80 hover:bg-rose-500/10 hover:text-rose-600 dark:hover:bg-rose-500/20 dark:hover:text-rose-400 text-slate-700 dark:text-slate-200 border border-transparent gap-1.5 font-bold rounded-xl h-8 px-4 transition-all cursor-pointer"
            >
              <XIcon size={13} className="stroke-[2.5]" /> Decline
            </button>
          </div>
        )}

        {/* ── Join Request action buttons ── */}
        {isJoinRequest && !status && onReviewJoinRequest && (
          <div className="flex items-center gap-2 mt-3">
            <button
              onClick={(e) => onReviewJoinRequest(notification, true, e)}
              disabled={isReviewingJoinRequest}
              className="btn btn-xs bg-emerald-600 hover:bg-emerald-700 text-white border-none gap-1.5 font-bold rounded-xl h-8 px-4 shadow-xs active:scale-95 transition-all cursor-pointer"
            >
              {isReviewingJoinRequest ? <Spin xs /> : <><Check size={13} className="stroke-[2.5]" /> Approve</>}
            </button>
            <button
              onClick={(e) => onReviewJoinRequest(notification, false, e)}
              disabled={isReviewingJoinRequest}
              className="btn btn-xs bg-base-300/80 hover:bg-rose-500/10 hover:text-rose-600 dark:hover:bg-rose-500/20 dark:hover:text-rose-400 text-slate-700 dark:text-slate-200 border border-transparent gap-1.5 font-bold rounded-xl h-8 px-4 transition-all cursor-pointer"
            >
              <XIcon size={13} className="stroke-[2.5]" /> Reject
            </button>
          </div>
        )}

        {/* ── Completed status badges ── */}
        {isJoinRequest && status === "approved" && (
          <div className="mt-2.5 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-[11px] font-bold">
            <Check size={13} className="stroke-[2.5]" /> Request approved
          </div>
        )}
        {isJoinRequest && status === "rejected" && (
          <div className="mt-2.5 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-500/10 text-slate-500 dark:text-slate-400 text-[11px] font-bold">
            <XIcon size={13} className="stroke-[2.5]" /> Request rejected
          </div>
        )}
        {isInvite && isAccepted && (
          <div className="mt-2.5 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-[11px] font-bold">
            <Check size={13} className="stroke-[2.5]" /> Joined community
          </div>
        )}
        {isInvite && isDeclined && (
          <div className="mt-2.5 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-500/10 text-slate-500 dark:text-slate-400 text-[11px] font-bold">
            <XIcon size={13} className="stroke-[2.5]" /> Invite declined
          </div>
        )}
      </div>

      {/* Delete / Actions */}
      {!((isInvite && !isAccepted && !isDeclined) || (isJoinRequest && !status)) && (
        <div className="shrink-0 flex items-center self-center opacity-100 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
          <button
            onClick={(e) => onDelete(id, e)}
            className="w-8 h-8 rounded-xl bg-transparent hover:bg-rose-500/10 text-slate-400 hover:text-rose-500 flex items-center justify-center transition-all cursor-pointer"
            title="Delete notification"
          >
            <Trash2 size={15} />
          </button>
        </div>
      )}
    </div>
  );
};

export default NotificationItem;
