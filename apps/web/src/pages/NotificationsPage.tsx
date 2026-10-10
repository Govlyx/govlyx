import React, { useState, useEffect, useMemo, useRef } from "react";
import { Inbox, Trash2, Bell, UserPlus, Heart, ChevronDown, Check, RefreshCw } from "lucide-react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useNotifications, useNotificationActions } from "../hooks/useNotification";
import NotificationItem from "../components/layout/NotificationItem";
import type { Notification } from "../types/notification";
import axiosInstance from "../api/axiosConfig";
import { communityService } from "../api/communityService";
import { showToast } from "../utils/toast";
import { parseError } from "../utils/error-handler";
import ConfirmModal from "../components/post/ConfirmModal";

const ACCEPTED_KEY = "govlyx_accepted_invites";
const DECLINED_KEY = "govlyx_declined_invites";
const REVIEWED_KEY = "govlyx_reviewed_requests";

type FilterTab = "all" | "unread" | "invites" | "interactions";

const getStoredIds = (key: string): Set<number> => {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return new Set();
    return new Set(JSON.parse(raw));
  } catch {
    return new Set();
  }
};

const saveStoredIds = (key: string, ids: Set<number>) => {
  try {
    localStorage.setItem(key, JSON.stringify(Array.from(ids)));
  } catch {}
};

const getStoredMap = (key: string): Map<number, "approved" | "rejected"> => {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return new Map();
    return new Map(JSON.parse(raw));
  } catch {
    return new Map();
  }
};

const saveStoredMap = (key: string, map: Map<number, "approved" | "rejected">) => {
  try {
    localStorage.setItem(key, JSON.stringify(Array.from(map.entries())));
  } catch {}
};

const NotificationsPage: React.FC = () => {
  const { data: notifications = [], isLoading, refetch, isRefetching } = useNotifications(50);
  const { markAsRead, markAllAsRead, deleteNotification, deleteAllNotifications } = useNotificationActions();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const tabParam = searchParams.get("tab") as FilterTab | null;
  const activeTab: FilterTab =
    tabParam === "unread" || tabParam === "invites" || tabParam === "interactions"
      ? tabParam
      : "all";

  const setActiveTab = (tab: FilterTab) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (tab === "all") {
          next.delete("tab");
        } else {
          next.set("tab", tab);
        }
        return next;
      },
      { replace: true }
    );
  };

  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);
  const [showClearConfirm, setShowClearConfirm] = useState(false);
  const mobileDropdownRef = useRef<HTMLDivElement>(null);

  const [acceptingId, setAcceptingId] = useState<number | null>(null);
  const [acceptedIds, setAcceptedIds] = useState<Set<number>>(() => getStoredIds(ACCEPTED_KEY));
  const [declinedIds, setDeclinedIds] = useState<Set<number>>(() => getStoredIds(DECLINED_KEY));
  const [visibleCount, setVisibleCount] = useState<number>(10);
  const [actingReqId, setActingReqId] = useState<number | null>(null);
  const [reviewedRequests, setReviewedRequests] = useState<Map<number, "approved" | "rejected">>(() => getStoredMap(REVIEWED_KEY));

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (mobileDropdownRef.current && !mobileDropdownRef.current.contains(e.target as Node)) {
        setMobileFilterOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // When a join-accept or join-reject notification arrives, clear the pending_joins
  // localStorage entry for that community so the button reverts immediately.
  useEffect(() => {
    if (!notifications.length) return;
    let didClear = false;
    notifications.forEach((n: Notification) => {
      if (
        n.notificationType === "COMMUNITY_JOIN_ACCEPT" ||
        n.notificationType === "COMMUNITY_JOIN_REJECT"
      ) {
        const communityId = n.referenceId ? String(n.referenceId) : null;
        if (communityId) {
          const pending: string[] = (() => {
            try { return JSON.parse(localStorage.getItem("pending_joins") || "[]").map(String); }
            catch { return []; }
          })();
          if (pending.includes(communityId)) {
            localStorage.setItem(
              "pending_joins",
              JSON.stringify(pending.filter((x: string) => x !== communityId))
            );
            didClear = true;
          }
        }
      }
    });
    if (didClear) {
      queryClient.invalidateQueries({ queryKey: ["myCommunities"] });
      queryClient.invalidateQueries({ queryKey: ["sidebarCommunities"] });
      queryClient.invalidateQueries({ queryKey: ["my-communities"] });
    }
  }, [notifications, queryClient]);

  const extractInviteToken = (url?: string | null): string | null => {
    if (!url) return null;
    const match = url.match(/\/(?:communities\/)?invites?\/(?:accept\/)?([a-zA-Z0-9_-]+)/i);
    if (match) return match[1];
    const parts = url.split("/").filter(Boolean);
    return parts[parts.length - 1] || null;
  };

  const extractCommunityId = (url?: string | null): number | null => {
    if (!url) return null;
    const match = url.match(/\/communities\/(\d+)/);
    return match ? Number(match[1]) : null;
  };

  const markAccepted = (id: number) => {
    setAcceptedIds(prev => {
      const next = new Set(prev).add(id);
      saveStoredIds(ACCEPTED_KEY, next);
      return next;
    });
  };

  const markDeclined = (id: number) => {
    setDeclinedIds(prev => {
      const next = new Set(prev).add(id);
      saveStoredIds(DECLINED_KEY, next);
      return next;
    });
  };

  const markReviewed = (id: number, status: "approved" | "rejected") => {
    setReviewedRequests(prev => {
      const next = new Map(prev).set(id, status);
      saveStoredMap(REVIEWED_KEY, next);
      return next;
    });
  };

  const handleAcceptInvite = async (n: Notification, e: React.MouseEvent) => {
    e.stopPropagation();
    setAcceptingId(n.id);
    try {
      let success = false;
      const inviteToken = extractInviteToken(n.actionUrl);
      
      // 1. Try accepting via invite token if available
      if (inviteToken && inviteToken.length >= 6) {
        try {
          const res = await axiosInstance.post(`/api/communities/invites/accept/${inviteToken}`, {});
          if (res.status === 200 || res.status === 201) {
            success = true;
          }
        } catch (tokenErr: any) {
          const tokenMsg = tokenErr.response?.data?.message || "";
          if (tokenMsg.toLowerCase().includes("already")) {
            markAccepted(n.id);
            if (!n.isRead) markAsRead.mutate(n.id);
            showToast.info("You are already a member of this community.");
            return;
          }
        }
      }

      // 2. If token acceptance didn't succeed, fallback to join via referenceId (communityId)
      if (!success && n.referenceId) {
        try {
          const res = await axiosInstance.post(`/api/communities/${n.referenceId}/join`, {});
          if (res.status === 200 || res.status === 201) {
            success = true;
          }
        } catch (refErr: any) {
          const refMsg = refErr.response?.data?.message || "";
          if (refMsg.toLowerCase().includes("already")) {
            markAccepted(n.id);
            if (!n.isRead) markAsRead.mutate(n.id);
            showToast.info("You are already a member of this community.");
            return;
          }
        }
      }

      if (success) {
        markAccepted(n.id);
        if (!n.isRead) markAsRead.mutate(n.id);
        queryClient.invalidateQueries({ queryKey: ["myCommunities"] });
        queryClient.invalidateQueries({ queryKey: ["sidebarCommunities"] });
        showToast.success("Invite accepted! You joined the community.");
      } else {
        showToast.error("Unable to accept invite. It may have expired or already been accepted.");
      }
    } catch (err: any) {
      showToast.error(parseError(err));
    } finally {
      setAcceptingId(null);
    }
  };

  const handleDeclineInvite = async (n: Notification, e: React.MouseEvent) => {
    e.stopPropagation();
    markDeclined(n.id);
    if (!n.isRead) markAsRead.mutate(n.id);
    const inviteToken = extractInviteToken(n.actionUrl);
    if (inviteToken && inviteToken.length >= 6) {
      try {
        await axiosInstance.post(`/api/communities/invites/decline/${inviteToken}`, {}).catch(() => {});
      } catch {}
    }
    showToast.info("Invite declined.");
  };

  const handleReviewJoinRequest = async (n: Notification, approve: boolean, e: React.MouseEvent) => {
    e.stopPropagation();
    setActingReqId(n.id);
    try {
      const communityId = extractCommunityId(n.actionUrl) || n.referenceId;
      const reqId = n.referenceId;
      if (communityId && reqId) {
        await communityService.handleJoinRequest(communityId, reqId, approve);
        markReviewed(n.id, approve ? "approved" : "rejected");
        if (!n.isRead) markAsRead.mutate(n.id);
        queryClient.invalidateQueries({ queryKey: ["myCommunities"] });
        showToast.success(approve ? "Join request approved!" : "Join request rejected.");
      } else {
        showToast.error("Community or request not found.");
      }
    } catch (err: any) {
      showToast.error(parseError(err));
    } finally {
      setActingReqId(null);
    }
  };

  const handleNotificationClick = (n: Notification) => {
    if (n.notificationType === "COMMUNITY_INVITE") {
      if (declinedIds.has(n.id)) {
        showToast.info("This invite has been declined.");
        return;
      }
      if (acceptedIds.has(n.id)) {
        const communityId = extractCommunityId(n.actionUrl) || n.referenceId;
        if (communityId) {
          navigate(`/communities/${communityId}`);
        }
        return;
      }
      if (n.actionUrl) {
        navigate(n.actionUrl);
      }
      return;
    }
    if (n.notificationType === "COMMUNITY_JOIN_REQUEST" && !reviewedRequests.has(n.id)) {
      const communityId = extractCommunityId(n.actionUrl) || n.referenceId;
      if (communityId) {
        navigate(`/communities/${communityId}?tab=requests`);
      }
      return;
    }
    if (!n.isRead) markAsRead.mutate(n.id);
    if (n.actionUrl) {
      let targetUrl = n.actionUrl;
      if (targetUrl.startsWith("/community/")) {
        targetUrl = targetUrl.replace("/community/", "/communities/");
      }
      navigate(targetUrl);
    } else if (n.referenceId && (n.notificationType === "COMMUNITY_JOIN_REQUEST" || n.notificationType === "COMMUNITY_JOIN_ACCEPT" || n.notificationType === "COMMUNITY_JOIN_REJECT" || n.notificationType === "COMMUNITY_ROLE_CHANGED")) {
      const communityId = extractCommunityId(n.actionUrl) || n.referenceId;
      if (communityId) {
        navigate(`/communities/${communityId}`);
      }
    }
  };

  // ── Tab Filters & Counters ──
  const unreadCount = useMemo(() => {
    return notifications.filter(n => !n.isRead && !acceptedIds.has(n.id) && !declinedIds.has(n.id) && !reviewedRequests.has(n.id)).length;
  }, [notifications, acceptedIds, declinedIds, reviewedRequests]);

  const invitesCount = useMemo(() => {
    return notifications.filter(n => n.notificationType === "COMMUNITY_INVITE" || n.notificationType === "COMMUNITY_JOIN_REQUEST").length;
  }, [notifications]);

  const interactionsCount = useMemo(() => {
    return notifications.filter(
      (n) =>
        n.notificationType === "POST_LIKE" ||
        n.notificationType === "POST_COMMENT" ||
        n.notificationType === "COMMENT_REPLY" ||
        n.notificationType === "MENTION" ||
        n.notificationType === "FOLLOW"
    ).length;
  }, [notifications]);

  const filterOptions = [
    { id: "all" as FilterTab, label: "All", count: notifications.length, icon: Bell },
    { id: "unread" as FilterTab, label: "Unread", count: unreadCount },
    { id: "invites" as FilterTab, label: "Invites & Requests", count: invitesCount, icon: UserPlus },
    { id: "interactions" as FilterTab, label: "Interactions", count: interactionsCount, icon: Heart },
  ];

  const filteredNotifications = useMemo(() => {
    if (activeTab === "unread") {
      return notifications.filter(n => !n.isRead && !acceptedIds.has(n.id) && !declinedIds.has(n.id) && !reviewedRequests.has(n.id));
    }
    if (activeTab === "invites") {
      return notifications.filter(n => 
        n.notificationType === "COMMUNITY_INVITE" || 
        n.notificationType === "COMMUNITY_JOIN_REQUEST" ||
        n.notificationType === "COMMUNITY_ROLE_CHANGED" ||
        n.notificationType === "COMMUNITY_JOIN_ACCEPT" ||
        n.notificationType === "COMMUNITY_JOIN_REJECT"
      );
    }
    if (activeTab === "interactions") {
      return notifications.filter(n => 
        n.notificationType === "POST_LIKE" || 
        n.notificationType === "POST_COMMENT" || 
        n.notificationType === "COMMENT_REPLY" ||
        n.notificationType === "MENTION" ||
        n.notificationType === "FOLLOW"
      );
    }
    return notifications;
  }, [notifications, activeTab, acceptedIds, declinedIds, reviewedRequests]);

  const handleRefresh = async () => {
    try {
      await refetch();
      await queryClient.invalidateQueries({ queryKey: ["unreadNotificationsCount"] });
      showToast.info("Notifications refreshed");
    } catch {
      showToast.error("Failed to refresh notifications");
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-5 sm:space-y-6 pb-12">
      {/* ═══════════════ PAGE HEADER ═══════════════ */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:gap-4 text-left">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-slate-900 dark:text-white tracking-tight">
            Notifications
          </h1>
          <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-0.5 sm:mt-1 font-medium">
            Stay updated on community invites, discussions, and interactions
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2 self-start sm:self-auto flex-wrap">
          {/* Manual Refresh Button */}
          <button
            type="button"
            onClick={handleRefresh}
            disabled={isLoading || isRefetching}
            title="Refresh notifications"
            className="bg-white dark:bg-base-200 hover:bg-slate-50 dark:hover:bg-base-300 text-slate-700 dark:text-slate-200 border border-slate-200 dark:border-base-300 font-semibold text-xs px-3 py-2 sm:px-3.5 sm:py-2.5 rounded-xl flex items-center gap-1.5 transition-all active:scale-95 shadow-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed outline-none focus:outline-none"
          >
            <RefreshCw
              size={14}
              className={`shrink-0 transition-transform ${isRefetching ? "animate-spin text-[#1D4ED8]" : ""}`}
            />
            <span className="hidden xs:inline">Refresh</span>
          </button>

          {/* Mark All As Read Button (Solid royal blue pill, single checkmark) */}
          <button
            type="button"
            onClick={() => {
              markAllAsRead.mutate(undefined, {
                onSuccess: () => showToast.success("All notifications marked as read"),
              });
            }}
            disabled={markAllAsRead.isPending || unreadCount === 0}
            className="bg-[#1D4ED8] hover:bg-[#1e40af] text-white border border-[#1D4ED8] font-bold text-xs px-3.5 py-2 sm:px-4 sm:py-2.5 rounded-xl flex items-center gap-1.5 transition-all active:scale-95 shadow-xs cursor-pointer outline-none focus:outline-none disabled:opacity-40 disabled:cursor-not-allowed disabled:active:scale-100"
          >
            {markAllAsRead.isPending ? (
              <span className="loading loading-spinner loading-xs" />
            ) : (
              <Check size={14} className="stroke-[2.5]" />
            )}
            <span>Mark all as read</span>
          </button>

          {/* Clear All Button (Soft pastel red pill, red trash icon) */}
          <button
            type="button"
            onClick={() => setShowClearConfirm(true)}
            disabled={deleteAllNotifications.isPending || notifications.length === 0}
            className="bg-red-50 hover:bg-red-100/80 text-red-600 dark:bg-red-950/40 dark:hover:bg-red-900/40 dark:text-red-400 border border-red-200/80 dark:border-red-900/40 font-bold text-xs px-3.5 py-2 sm:px-4 sm:py-2.5 rounded-xl flex items-center gap-1.5 transition-all active:scale-95 shadow-xs cursor-pointer outline-none focus:outline-none disabled:opacity-40 disabled:cursor-not-allowed disabled:active:scale-100"
          >
            {deleteAllNotifications.isPending ? (
              <span className="loading loading-spinner loading-xs" />
            ) : (
              <Trash2 size={14} />
            )}
            <span>Clear all</span>
          </button>
        </div>
      </div>

      {/* ═══════════════ MOBILE FILTER DROPDOWN (< sm) ═══════════════ */}
      <div ref={mobileDropdownRef} className="sm:hidden notranslate text-left relative z-20">
        <button
          type="button"
          onClick={() => setMobileFilterOpen((prev) => !prev)}
          className="w-full bg-white dark:bg-base-200 border border-slate-200 dark:border-base-300 rounded-2xl px-4 py-2.5 text-xs font-bold text-slate-800 dark:text-slate-200 flex items-center justify-between shadow-xs outline-none focus:outline-none cursor-pointer"
        >
          <div className="flex items-center gap-2">
            {activeTab === "all" && <Bell size={14} className="text-[#1D4ED8]" />}
            {activeTab === "unread" && (
              <span className="w-2 h-2 rounded-full bg-[#1D4ED8]" />
            )}
            {activeTab === "invites" && <UserPlus size={14} className="text-[#1D4ED8]" />}
            {activeTab === "interactions" && <Heart size={14} className="text-[#1D4ED8]" />}
            <span>
              {activeTab === "all" && `All (${notifications.length})`}
              {activeTab === "unread" && `Unread (${unreadCount})`}
              {activeTab === "invites" && `Invites & Requests ${invitesCount > 0 ? `(${invitesCount})` : ""}`}
              {activeTab === "interactions" && `Interactions ${interactionsCount > 0 ? `(${interactionsCount})` : ""}`}
            </span>
          </div>
          <ChevronDown
            size={15}
            className={`text-slate-400 transition-transform duration-200 ${
              mobileFilterOpen ? "rotate-180 text-[#1D4ED8]" : ""
            }`}
          />
        </button>

        {mobileFilterOpen && (
          <div className="absolute top-full mt-1.5 left-0 right-0 z-50 rounded-2xl border border-slate-200 dark:border-base-300 bg-white dark:bg-base-200 backdrop-blur-md shadow-2xl p-1.5 space-y-1 animate-in fade-in zoom-in-95 duration-150">
            {filterOptions.map((opt) => {
              const isSelected = activeTab === opt.id;
              const Icon = opt.icon;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => {
                    setActiveTab(opt.id);
                    setMobileFilterOpen(false);
                  }}
                  className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer text-left outline-none focus:outline-none ${
                    isSelected
                      ? "bg-[#1D4ED8] text-white shadow-xs font-bold"
                      : "text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-base-300/60"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    {Icon ? (
                      <Icon size={14} className={isSelected ? "text-white" : "text-slate-400"} />
                    ) : (
                      <span className={`w-1.5 h-1.5 rounded-full ${isSelected ? "bg-white" : "bg-[#1D4ED8]"}`} />
                    )}
                    <span>{opt.label}</span>
                    {opt.count !== undefined && (opt.id === "all" || opt.id === "unread" || opt.count > 0) && (
                      <span
                        className={`text-[10px] px-1.5 py-0.2 rounded-full font-black ${
                          isSelected
                            ? "bg-white/20 text-white"
                            : opt.id === "unread"
                            ? "bg-[#1D4ED8] text-white"
                            : "bg-slate-200 dark:bg-base-300 text-slate-700 dark:text-slate-300"
                        }`}
                      >
                        {opt.count}
                      </span>
                    )}
                  </div>
                  {isSelected && <Check size={14} className="stroke-[2.5]" />}
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* ═══════════════ DESKTOP FILTER TABS (>= sm) ═══════════════ */}
      <div
        role="tablist"
        aria-label="Notification filters"
        className="hidden sm:flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none notranslate text-left select-none"
      >
        {/* Tab 1: All */}
        <button
          role="tab"
          id="tab-all"
          aria-selected={activeTab === "all"}
          onClick={() => setActiveTab("all")}
          className={`px-4 py-2 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer outline-none focus:outline-none focus-visible:outline-none focus:ring-0 flex items-center gap-1.5 ${
            activeTab === "all"
              ? "bg-[#1D4ED8] text-white shadow-xs"
              : "bg-white dark:bg-base-200 hover:bg-slate-50 dark:hover:bg-base-300 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-base-300 shadow-xs"
          }`}
        >
          <Bell size={13} className="shrink-0" />
          <span>All ({notifications.length})</span>
        </button>

        {/* Tab 2: Unread (NO Bell icon, solid blue count badge when inactive) */}
        <button
          role="tab"
          id="tab-unread"
          aria-selected={activeTab === "unread"}
          onClick={() => setActiveTab("unread")}
          className={`px-4 py-2 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer outline-none focus:outline-none focus-visible:outline-none focus:ring-0 flex items-center gap-1.5 ${
            activeTab === "unread"
              ? "bg-[#1D4ED8] text-white shadow-xs"
              : "bg-white dark:bg-base-200 hover:bg-slate-50 dark:hover:bg-base-300 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-base-300 shadow-xs"
          }`}
        >
          <span>Unread</span>
          <span
            className={`text-[10px] font-black px-1.5 py-0.2 rounded-full transition-colors ${
              activeTab === "unread" ? "bg-white/20 text-white" : "bg-[#1D4ED8] text-white"
            }`}
          >
            {unreadCount}
          </span>
        </button>

        {/* Tab 3: Invites & Requests (Icon + Badge only when invitesCount > 0) */}
        <button
          role="tab"
          id="tab-invites"
          aria-selected={activeTab === "invites"}
          onClick={() => setActiveTab("invites")}
          className={`px-4 py-2 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer outline-none focus:outline-none focus-visible:outline-none focus:ring-0 flex items-center gap-1.5 ${
            activeTab === "invites"
              ? "bg-[#1D4ED8] text-white shadow-xs"
              : "bg-white dark:bg-base-200 hover:bg-slate-50 dark:hover:bg-base-300 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-base-300 shadow-xs"
          }`}
        >
          <UserPlus size={13} className="shrink-0" />
          <span>Invites & Requests</span>
          {invitesCount > 0 && (
            <span
              className={`text-[10px] font-black px-1.5 py-0.2 rounded-full ${
                activeTab === "invites"
                  ? "bg-white/20 text-white"
                  : "bg-slate-200 dark:bg-base-300 text-slate-700 dark:text-slate-200"
              }`}
            >
              {invitesCount}
            </span>
          )}
        </button>

        {/* Tab 4: Interactions (Icon + Badge only when interactionsCount > 0) */}
        <button
          role="tab"
          id="tab-interactions"
          aria-selected={activeTab === "interactions"}
          onClick={() => setActiveTab("interactions")}
          className={`px-4 py-2 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer outline-none focus:outline-none focus-visible:outline-none focus:ring-0 flex items-center gap-1.5 ${
            activeTab === "interactions"
              ? "bg-[#1D4ED8] text-white shadow-xs"
              : "bg-white dark:bg-base-200 hover:bg-slate-50 dark:hover:bg-base-300 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-base-300 shadow-xs"
          }`}
        >
          <Heart size={13} className="shrink-0" />
          <span>Interactions</span>
          {interactionsCount > 0 && (
            <span
              className={`text-[10px] font-black px-1.5 py-0.2 rounded-full ${
                activeTab === "interactions"
                  ? "bg-white/20 text-white"
                  : "bg-slate-200 dark:bg-base-300 text-slate-700 dark:text-slate-200"
              }`}
            >
              {interactionsCount}
            </span>
          )}
        </button>
      </div>

      {/* ═══════════════ NOTIFICATIONS CARD ═══════════════ */}
      <div className="rounded-2xl sm:rounded-3xl border border-black/10 dark:border-base-300 bg-base-200 shadow-sm overflow-hidden text-left">
        {isLoading ? (
          <div className="flex flex-col items-center justify-center py-20 gap-3">
            <span className="loading loading-spinner loading-md text-[#1D4ED8]" />
            <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Loading notifications...</p>
          </div>
        ) : filteredNotifications.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 px-6 text-center">
            <div className="w-16 h-16 bg-base-300/60 rounded-2xl flex items-center justify-center mb-3 text-slate-400">
              <Inbox size={32} />
            </div>
            <h3 className="text-base font-bold text-slate-900 dark:text-white">
              {activeTab === "unread" ? "All caught up!" : "No notifications yet"}
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 max-w-xs leading-relaxed">
              {activeTab === "unread"
                ? "You have read all your notifications."
                : activeTab === "invites"
                ? "No pending community invites or requests."
                : activeTab === "interactions"
                ? "Likes, comments, and mentions will show up here."
                : "When you receive invites, replies, or mentions, they will appear here."}
            </p>
          </div>
        ) : (
          <div className="divide-y divide-black/5 dark:divide-white/5">
            {filteredNotifications.slice(0, visibleCount).map((n) => (
              <NotificationItem
                key={n.id}
                notification={n}
                onRead={(id) => markAsRead.mutate(id)}
                onDelete={(id, e) => {
                  e.stopPropagation();
                  deleteNotification.mutate(id);
                }}
                onAcceptInvite={handleAcceptInvite}
                onDeclineInvite={handleDeclineInvite}
                onReviewJoinRequest={handleReviewJoinRequest}
                onClick={handleNotificationClick}
                isAccepting={acceptingId === n.id}
                isAccepted={acceptedIds.has(n.id)}
                isDeclined={declinedIds.has(n.id)}
                isReviewingJoinRequest={actingReqId === n.id}
                joinRequestStatus={reviewedRequests.get(n.id) || null}
              />
            ))}

            {filteredNotifications.length > visibleCount && (
              <div className="p-3 sm:p-4 text-center bg-base-200/50">
                <button
                  type="button"
                  onClick={() => setVisibleCount((prev) => prev + 10)}
                  className="px-5 py-2.5 rounded-xl bg-base-100 hover:bg-base-300/80 text-[#1D4ED8] dark:text-blue-400 font-bold text-xs sm:text-sm border border-black/5 dark:border-white/5 shadow-xs transition-all active:scale-98 cursor-pointer inline-flex items-center gap-2"
                >
                  <span>View More</span>
                  <span className="text-[11px] opacity-70">
                    ({filteredNotifications.length - visibleCount} remaining)
                  </span>
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Confirm Clear All Modal ── */}
      <ConfirmModal
        isOpen={showClearConfirm}
        onClose={() => setShowClearConfirm(false)}
        onConfirm={() => {
          setShowClearConfirm(false);
          deleteAllNotifications.mutate(undefined, {
            onSuccess: () => showToast.success("All notifications cleared"),
          });
        }}
        title="Clear All Notifications"
        message="Are you sure you want to remove all notifications? This action cannot be undone."
        confirmLabel="Clear All"
        cancelLabel="Cancel"
        isDanger={true}
      />
    </div>
  );
};

export default NotificationsPage;
