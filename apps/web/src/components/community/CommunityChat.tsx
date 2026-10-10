import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useCurrentUser } from "../../hooks/useUser";
import { useCommunityChat } from "../../hooks/useCommunityChat";
import { communityService } from "../../api/communityService";
import { showToast } from "../../utils/toast";
import { resolveMediaUrl, decodeHTML } from "../../utils/postUtils";
import { useTheme } from "../../hooks/useTheme";
import {
  Send,
  MoreVertical,
  Reply,
  Pin,
  Trash2,
  AlertTriangle,
  Lock,
  Settings,
  Clock,
  X,
  ChevronDown,
  ExternalLink,
  MessageSquare,
  Smile,
  CheckCheck,
  Paperclip,
  Moon,
  Sun,
  RotateCw,
  Image as ImageIcon,
} from "lucide-react";
import { GOVLYX_EMOJIS } from "../../utils/stickers";
import type { CommunityMessage, ChatAttachmentDto, SharedPostDto, CommunityMessageType } from "../../types/CommunityChat.types";

interface SharedPostDraft {
  postId: number;
  content: string;
  authorUsername?: string;
}

interface CommunityChatProps {
  communityId: number;
  isAdmin: boolean;
  initialSharedPost?: SharedPostDraft | null;
  onClearSharedPost?: () => void;
}

// Render friendly reply/attachment/emoji snippet instead of raw svg/path or metadata
const renderMessagePreviewSnippet = (msg: {
  content?: string;
  attachments?: ChatAttachmentDto[];
  messageType?: CommunityMessageType;
  sharedPost?: SharedPostDto;
  isDeleted?: boolean;
  deletedByType?: "USER" | "ADMINISTRATOR";
}) => {
  if (msg.isDeleted) {
    return (
      <span className="italic opacity-60">
        {msg.deletedByType === "ADMINISTRATOR"
          ? "Message deleted by administrator"
          : "Message deleted by user"}
      </span>
    );
  }

  if (msg.content && msg.content.startsWith("/govlyx-emoji/")) {
    return (
      <span className="inline-flex items-center gap-1.5 align-middle">
        <img
          src={msg.content}
          alt="Emoji"
          className="w-4 h-4 sm:w-5 sm:h-5 object-contain inline-block shrink-0 drop-shadow-xs"
        />
        <span className="text-[11px] font-medium opacity-75">Govlyx Emoji</span>
      </span>
    );
  }

  if (msg.messageType === "SHARE_POST" || msg.sharedPost) {
    return (
      <span className="inline-flex items-center gap-1 opacity-80">
        <ExternalLink size={12} className="shrink-0 text-primary" />
        <span className="truncate">
          Shared post{msg.sharedPost?.authorUsername ? ` by @${msg.sharedPost.authorUsername}` : ""}
        </span>
      </span>
    );
  }

  if (msg.attachments && msg.attachments.length > 0) {
    const isImg = msg.attachments.some((a) => a.attachmentType === "IMAGE");
    return (
      <span className="inline-flex items-center gap-1.5 opacity-80">
        {isImg ? (
          <>
            <ImageIcon size={12} className="shrink-0 text-primary" />
            <span className="truncate">Photo{msg.content ? ` • ${decodeHTML(msg.content)}` : ""}</span>
          </>
        ) : (
          <>
            <Paperclip size={12} className="shrink-0 text-primary" />
            <span className="truncate">{msg.attachments[0].fileName || "Attachment"}</span>
          </>
        )}
      </span>
    );
  }

  return (
    <span className="truncate opacity-75">
      {decodeHTML(msg.content || "")}
    </span>
  );
};

// Message expiry countdown utility (moved outside to prevent unmount/flicker)
const ExpiryTimer = React.memo(({ expiresAt }: { expiresAt: string }) => {
  const getInitialTimeLeft = () => {
    const diff = new Date(expiresAt).getTime() - Date.now();
    if (diff <= 0) return "Expired";
    const totalSecs = Math.floor(diff / 1000);
    if (totalSecs > 86400) {
      return `${Math.floor(totalSecs / 86400)}d left`;
    }
    const secs = totalSecs % 60;
    const mins = Math.floor(totalSecs / 60) % 60;
    const hrs = Math.floor(totalSecs / 3600);
    const parts = [];
    if (hrs > 0) parts.push(`${hrs}h`);
    if (mins > 0 || hrs > 0) parts.push(`${mins}m`);
    parts.push(`${secs}s`);
    return parts.join(" ");
  };

  const [timeLeft, setTimeLeft] = useState(getInitialTimeLeft);

  useEffect(() => {
    const update = () => {
      const diff = new Date(expiresAt).getTime() - Date.now();
      if (diff <= 0) {
        setTimeLeft("Expired");
        return;
      }

      const totalSecs = Math.floor(diff / 1000);
      if (totalSecs > 86400) {
        setTimeLeft(`${Math.floor(totalSecs / 86400)}d left`);
        return;
      }

      const secs = totalSecs % 60;
      const mins = Math.floor(totalSecs / 60) % 60;
      const hrs = Math.floor(totalSecs / 3600);

      const parts = [];
      if (hrs > 0) parts.push(`${hrs}h`);
      if (mins > 0 || hrs > 0) parts.push(`${mins}m`);
      parts.push(`${secs}s`);
      setTimeLeft(parts.join(" "));
    };

    update();
    const interval = setInterval(update, 1000);
    return () => clearInterval(interval);
  }, [expiresAt]);

  return (
    <span className="flex items-center gap-1 text-[10px] opacity-60 text-warning font-mono">
      <Clock size={10} />
      {timeLeft}
    </span>
  );
});

function renderFormattedText(text: string) {
  if (!text) return null;
  const parts = text.split(/(@[a-zA-Z0-9_]+)/g);
  return parts.map((part, i) => {
    if (part.startsWith("@")) {
      return (
        <span key={i} className="text-[#38BDF8] font-bold">
          {part}
        </span>
      );
    }
    return part;
  });
}

function formatTime(dateStr: string | Date | number): string {
  if (!dateStr) return "";
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return "";
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  } catch {
    return "";
  }
}

export default function CommunityChat({
  communityId,
  isAdmin,
  initialSharedPost,
  onClearSharedPost,
}: CommunityChatProps) {
  const navigate = useNavigate();
  const [showStickerMenu, setShowStickerMenu] = useState(false);
  const { data: userProfile } = useCurrentUser();
  const { theme, toggleTheme } = useTheme();
  const usernameWatermark = userProfile?.actualUsername || userProfile?.username || "Govlyx User";
  const {
    messages,
    typingUsers,
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
  } = useCommunityChat(communityId, userProfile || null);

  const [inputText, setInputText] = useState("");
  const [replyMessage, setReplyMessage] = useState<CommunityMessage | null>(null);
  const [attachedPost, setAttachedPost] = useState<SharedPostDraft | null>(initialSharedPost || null);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [chatSettings, setChatSettings] = useState({
    isGroupChatEnabled: true,
    chatRetentionDays: 30,
  });
  const [tempSettings, setTempSettings] = useState({
    isGroupChatEnabled: true,
    chatRetentionDays: 30,
  });
  const [activeDropdown, setActiveDropdown] = useState<string | null>(null);
  const [reportModalMessage, setReportModalMessage] = useState<CommunityMessage | null>(null);
  const [reportCategory, setReportCategory] = useState("SPAM");
  const [reportDescription, setReportDescription] = useState("");
  const [deleteConfirmMessageId, setDeleteConfirmMessageId] = useState<string | number | null>(null);

  const handleAddReaction = (_msg: CommunityMessage, _emoji: string) => {
    // Community reaction handler placeholder
  };

  const chatEndRef = useRef<HTMLDivElement>(null);
  const chatContainerRef = useRef<HTMLDivElement>(null);
  const [showScrollBottom, setShowScrollBottom] = useState(false);

  // Sync initial attached post
  useEffect(() => {
    if (initialSharedPost) {
      setAttachedPost(initialSharedPost);
    }
  }, [initialSharedPost]);

  // ── 1. Fetch Chat Settings on Load ──
  useEffect(() => {
    const loadSettings = async () => {
      try {
        if (communityService.getChatSettings) {
          const res = await communityService.getChatSettings(communityId);
          if (res?.data) {
            setChatSettings(res.data);
          }
        }
      } catch (err) {
        console.debug("Chat settings GET endpoint fallback:", err);
      }
    };
    loadSettings();
  }, [communityId]);

  // ── 2. Handle Scroll behaviors ──
  const scrollToBottom = (behavior: ScrollBehavior = "smooth") => {
    chatEndRef.current?.scrollIntoView({ behavior });
  };

  useEffect(() => {
    if (!isLoading && messages.length > 0) {
      scrollToBottom("auto");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoading]);

  // Scroll to bottom on new message if user is close to bottom
  const previousMessageCount = useRef(messages.length);
  useEffect(() => {
    if (messages.length > previousMessageCount.current) {
      const container = chatContainerRef.current;
      if (container) {
        const isNearBottom =
          container.scrollHeight - container.scrollTop - container.clientHeight < 300;
        if (isNearBottom) {
          setTimeout(() => scrollToBottom("smooth"), 100);
        } else {
          setShowScrollBottom(true);
        }
      }
    }
    previousMessageCount.current = messages.length;
  }, [messages]);

  const handleScroll = () => {
    const container = chatContainerRef.current;
    if (!container) return;

    // Trigger fetch older messages when hitting top
    if (container.scrollTop === 0 && hasMore && !isFetchingMore) {
      const prevScrollHeight = container.scrollHeight;
      loadMoreMessages().then(() => {
        // Retain scroll position relative to content loaded
        setTimeout(() => {
          if (chatContainerRef.current) {
            chatContainerRef.current.scrollTop =
              chatContainerRef.current.scrollHeight - prevScrollHeight;
          }
        }, 50);
      });
    }

    // Show/hide scroll to bottom button
    const isNearBottom =
      container.scrollHeight - container.scrollTop - container.clientHeight < 200;
    setShowScrollBottom(!isNearBottom);
  };

  // ── 3. Find Pinned Message ──
  // Pinned message is loaded and kept up to date by the useCommunityChat hook

  const scrollToPinned = () => {
    if (!pinnedMessage) return;
    const element = document.getElementById(`msg-${pinnedMessage.id || pinnedMessage.messageId}`);
    if (element) {
      element.scrollIntoView({ behavior: "smooth", block: "center" });
      element.classList.add("bg-yellow-500/20");
      setTimeout(() => {
        element.classList.remove("bg-yellow-500/20");
      }, 2000);
    } else {
      showToast.error("Message is further up, scroll to load more history");
    }
  };

  // ── 4. Message send action ──
  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim() && !attachedPost) return;

    if (attachedPost) {
      const typedText = inputText.trim();
      const messageContent =
        typedText ||
        `Shared a post: "${attachedPost.content.substring(0, 60)}${attachedPost.content.length > 60 ? "..." : ""}"`;
      sendMessage(
        messageContent,
        replyMessage?.id || undefined,
        attachedPost.postId,
        {
          id: attachedPost.postId,
          content: attachedPost.content,
          authorUsername: attachedPost.authorUsername,
        }
      );
      setAttachedPost(null);
      onClearSharedPost?.();
    } else {
      sendMessage(inputText.trim(), replyMessage?.id || undefined);
    }

    setInputText("");
    setReplyMessage(null);
  };

  // ── 5. Admin Settings Actions ──
  const handleOpenSettings = () => {
    setTempSettings(chatSettings);
    setIsSettingsOpen(true);
  };

  const handleSaveSettings = async () => {
    const payload = {
      isGroupChatEnabled: tempSettings.isGroupChatEnabled,
      chatRetentionDays: tempSettings.chatRetentionDays,
    };

    try {
      await communityService.updateChatSettings(communityId, payload);
      setChatSettings(tempSettings);
      showToast.success("Chat settings updated successfully");
      setIsSettingsOpen(false);
      setError(null);
      fetchInitialMessages();
    } catch (err) {
      console.error(err);
      showToast.error("Failed to update chat settings");
    }
  };

  // ── 6. Message actions (Pin/Delete/Report) ──
  const handlePinToggle = async (msg: CommunityMessage) => {
    setActiveDropdown(null);
    const msgId = msg.id ?? msg.messageId;
    if (!msgId) return;
    try {
      await communityService.pinChatMessage(communityId, msgId);
    } catch (err: any) {
      showToast.error(err.response?.data?.message || "Failed to pin/unpin message");
    }
  };

  const handleReportSubmit = async () => {
    if (!reportModalMessage) return;
    try {
      await communityService.reportChatMessage(communityId, String(reportModalMessage.id || reportModalMessage.messageId), {
        category: reportCategory,
        description: reportDescription,
      });
      showToast.success("Message reported successfully");
      setReportModalMessage(null);
      setReportDescription("");
    } catch {
      showToast.error("Failed to report message");
    }
  };

  // ── 7. Helpers to check bubble attributes ──
  const getSenderColor = (userId: number) => {
    const colors = [
      "text-emerald-500",
      "text-sky-500",
      "text-pink-500",
      "text-amber-500",
      "text-violet-500",
      "text-indigo-500",
      "text-teal-500",
      "text-rose-500",
    ];
    return colors[userId % colors.length];
  };

  const isComposerLocked = !chatSettings.isGroupChatEnabled && !isAdmin;

  return (
    <div className="flex flex-col h-[calc(100vh-200px)] min-h-[500px] sm:min-h-[560px] rounded-2xl bg-base-200 border border-base-300 overflow-hidden relative">
      {/* ── Chat Header ── */}
      <div className="shrink-0 flex items-center justify-between px-4 py-3 bg-base-100 border-b border-base-300">
        <div className="flex flex-col">
          <span className="font-semibold text-sm">Community Chat</span>
          {typingUsers.length > 0 ? (
            <span className="text-xs text-primary animate-pulse">
              {typingUsers.map((u) => u.username).join(", ")}{" "}
              {typingUsers.length === 1 ? "is" : "are"} typing...
            </span>
          ) : (
            <span className="text-xs opacity-60">Real-time group messaging</span>
          )}
        </div>

        <div className="flex items-center gap-1">
          <button
            onClick={() => {
              fetchInitialMessages();
            }}
            title="Refresh chat"
            className="btn btn-ghost btn-circle btn-sm text-base-content/70 hover:text-base-content transition-colors"
            disabled={isLoading}
          >
            <RotateCw size={16} className={isLoading ? "animate-spin" : ""} />
          </button>
          <button
            onClick={toggleTheme}
            title={theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
            className="btn btn-ghost btn-circle btn-sm text-base-content/70 hover:text-base-content transition-colors"
          >
            {theme === "dark" ? <Sun size={16} /> : <Moon size={16} />}
          </button>
          {isAdmin && (
            <button
              onClick={handleOpenSettings}
              className="btn btn-ghost btn-circle btn-sm text-base-content/85 hover:text-primary transition-colors"
            >
              <Settings size={18} />
            </button>
          )}
        </div>
      </div>

      {/* ── Frozen Community Banner ── */}
      {error?.toLowerCase().includes("frozen") && (
        <div className="bg-amber-500/10 border-b border-amber-500/20 px-4 py-2 flex items-center gap-2 text-xs text-amber-500 font-semibold animate-pulse shrink-0">
          <AlertTriangle size={14} className="shrink-0" />
          <span>This Secret community is frozen because the owner's pass expired. Group chat is read-only.</span>
        </div>
      )}

      {/* ── Pinned Message Banner ── */}
      {pinnedMessage && (
        <div
          onClick={scrollToPinned}
          className="shrink-0 flex items-center justify-between px-4 py-2 bg-primary/10 border-b border-primary/20 cursor-pointer hover:bg-primary/15 transition-all text-xs"
        >
          <div className="flex items-center gap-2 overflow-hidden mr-4">
            <Pin size={12} className="text-primary shrink-0" />
            <div className="truncate text-base-content/80 flex items-center gap-1.5">
              <span className="font-semibold text-primary shrink-0">Pinned Message:</span>
              <div className="truncate">{renderMessagePreviewSnippet(pinnedMessage)}</div>
            </div>
          </div>
          <span className="text-[10px] opacity-60 shrink-0">Click to view</span>
        </div>
      )}

      {/* ── Chat Messages Container ── */}
      <div
        ref={chatContainerRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto px-1.5 py-3 space-y-3 scrollbar-hide scroll-smooth relative"
      >
        {userProfile && <WatermarkOverlay username={usernameWatermark} />}
        {isLoading ? (
          <div className="flex flex-col items-center justify-center h-full space-y-2">
            <span className="loading loading-spinner loading-md text-primary"></span>
            <p className="text-xs opacity-60">Loading conversations...</p>
          </div>
        ) : error && !error.toLowerCase().includes("frozen") ? (
          <div className="flex flex-col items-center justify-center h-full text-center space-y-2 p-6">
            <AlertTriangle className="text-error" size={32} />
            <p className="text-sm font-semibold">{error}</p>
            <button onClick={fetchInitialMessages} className="btn btn-xs btn-primary btn-outline">
              Retry
            </button>
          </div>
        ) : messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-center opacity-40 p-6 space-y-2">
            <MessageSquare size={40} />
            <p className="text-sm">No messages yet. Send a message to start the conversation!</p>
          </div>
        ) : (
          <>
            {isFetchingMore && (
              <div className="flex justify-center py-2">
                <span className="loading loading-spinner loading-xs text-primary"></span>
              </div>
            )}

            {messages.map((msg, index) => {
              if (msg.messageType === "SYSTEM") {
                return (
                  <div key={msg.id || msg.messageId || index} className="flex justify-center my-2 w-full">
                    <div className="bg-base-300/80 text-base-content/70 px-4 py-1.5 rounded-full text-xs font-semibold max-w-[85%] text-center shadow-sm border border-base-300">
                      {msg.content}
                    </div>
                  </div>
                );
              }

              const isMe = userProfile && msg.sender.id === userProfile.id;
              const isSenderAdmin = msg.sender.roleName === "ADMIN" || msg.sender.roleName === "OWNER";
              const showAvatar =
                index === 0 || messages[index - 1].sender.id !== msg.sender.id;

              return (
                <div
                  key={msg.id || msg.messageId || index}
                  id={`msg-${msg.id || msg.messageId}`}
                  className={`flex items-start gap-1.5 group transition-colors rounded-lg relative ${
                    isMe ? "justify-end" : "justify-start"
                  }`}
                >
                  {/* Avatar (for incoming messages) */}
                  {!isMe && (
                    <div className="w-8 shrink-0 self-end mb-0.5">
                      {showAvatar ? (
                        <img
                          src={
                            resolveMediaUrl(msg.sender?.profileImage, "social-posts") ||
                            `https://api.dicebear.com/7.x/bottts/svg?seed=${msg.sender?.username || "user"}`
                          }
                          alt={msg.sender?.username || "User"}
                          className="w-7 h-7 sm:w-8 sm:h-8 rounded-full border border-base-300 dark:border-white/10 object-cover shadow-xs"
                        />
                      ) : (
                        <div className="w-7 sm:w-8" />
                      )}
                    </div>
                  )}

                  {/* Message Bubble Block */}
                  <div className={`flex flex-col max-w-[80%] sm:max-w-[85%] ${isMe ? "items-end" : "items-start"}`}>
                    {/* Sender Display Name */}
                    {!isMe && (
                      <span className={`text-xs font-semibold mb-1 ml-1 ${getSenderColor(msg.sender?.id || 0)}`}>
                        {msg.sender?.actualUsername || msg.sender?.username}
                        {isSenderAdmin && (
                          <span className="badge badge-warning badge-xs ml-1 font-bold text-[8px]">
                            Admin
                          </span>
                        )}
                      </span>
                    )}

                    {/* The Bubble */}
                    <div
                      className={`relative px-3.5 py-2 rounded-2xl shadow-xs transition-all duration-200 border ${
                        msg.content && msg.content.startsWith("/govlyx-emoji/") && !msg.isDeleted
                          ? "bg-transparent border-transparent shadow-none"
                          : msg.isDeleted
                          ? "bg-base-200/40 text-base-content/40 border-base-300/60 rounded-xl"
                          : isMe
                          ? "bg-[#1D4ED8] text-white border-transparent rounded-tr-xs shadow-xs"
                          : "bg-slate-100/80 dark:bg-slate-800/85 text-base-content border-slate-200/60 dark:border-slate-800/40 rounded-tl-xs shadow-xs"
                      }`}
                    >
                      {/* Reply preview inside bubble */}
                      {!msg.isDeleted && msg.replyToId && (() => {
                        const target = messages.find((m) => {
                          const mId = m.id || m.messageId;
                          return mId && String(mId) === String(msg.replyToId);
                        });
                        if (!target) return null;
                        return (
                          <div
                            onClick={() => {
                              const el = document.getElementById(`msg-${msg.replyToId}`);
                              if (el) {
                                el.scrollIntoView({ behavior: "smooth", block: "center" });
                                el.classList.add("bg-blue-500/20");
                                setTimeout(() => {
                                  el.classList.remove("bg-blue-500/20");
                                }, 2000);
                              }
                            }}
                            className={`mb-2 py-1 px-2.5 border-l-2 text-xs rounded cursor-pointer hover:opacity-80 transition-opacity flex flex-col ${
                              isMe
                                ? "bg-white/10 border-white/50 text-white"
                                : "bg-black/5 dark:bg-white/5 border-primary/50 text-base-content"
                            }`}
                          >
                            <span className="font-semibold opacity-90 text-[11px]">
                              {target.sender.actualUsername || target.sender.username}
                            </span>
                            <div className="truncate max-w-xs mt-0.5 text-[11px]">
                              {renderMessagePreviewSnippet(target)}
                            </div>
                          </div>
                        );
                      })()}

                      {/* Message Content */}
                      {msg.isDeleted ? (
                        <p className="text-xs sm:text-[13px] italic opacity-60 select-none flex items-center gap-1.5 py-0.5">
                          <Trash2 size={12} className="opacity-50" />
                          <span>{msg.deletedByType === "ADMINISTRATOR" ? "This message was deleted by the administrator" : "This message was deleted by the user"}</span>
                        </p>
                      ) : msg.messageType === "SHARE_POST" && msg.sharedPost ? (
                        <div className="space-y-1.5">
                          {msg.content && !msg.content.startsWith("Shared a post:") && (
                            <p className="text-xs sm:text-[13px] whitespace-pre-wrap break-words leading-relaxed select-text font-normal mb-1">
                              {decodeHTML(msg.content)}
                            </p>
                          )}
                          <div
                            onClick={() => navigate(`/post/${msg.sharedPost?.id}`)}
                            className={`border rounded-xl p-3 cursor-pointer transition-all space-y-1.5 min-w-[220px] ${
                              isMe
                                ? "bg-white/10 hover:bg-white/15 border-white/15 text-white"
                                : "bg-base-200/80 hover:bg-base-200 border-base-300 text-base-content"
                            }`}
                          >
                            <div className={`flex items-center gap-1.5 text-xs font-bold ${isMe ? "text-white" : "text-base-content"}`}>
                              <ExternalLink size={12} className={isMe ? "text-white/80" : "text-primary"} />
                              <span>
                                Shared Post{msg.sharedPost.authorUsername ? ` by @${msg.sharedPost.authorUsername}` : ""}
                              </span>
                            </div>
                            <p className={`text-xs line-clamp-3 leading-relaxed font-normal ${isMe ? "text-white/90" : "opacity-80"}`}>
                              {decodeHTML(msg.sharedPost.content || "")}
                            </p>
                          </div>
                        </div>
                      ) : msg.content && msg.content.startsWith("/govlyx-emoji/") ? (
                        <div className="w-16 h-16 sm:w-20 sm:h-20 flex items-center justify-center p-0.5">
                          <img
                            src={msg.content}
                            alt="Govlyx Emoji"
                            className="w-full h-full object-contain drop-shadow-sm select-none"
                          />
                        </div>
                      ) : (
                        <p className="text-xs sm:text-[13px] whitespace-pre-wrap break-words leading-relaxed select-text font-bold">
                          {renderFormattedText(decodeHTML(msg.content || ""))}
                        </p>
                      )}

                      {/* Attachments rendering */}
                      {!msg.isDeleted && msg.attachments && msg.attachments.length > 0 && (
                        <div className="mt-2 space-y-2">
                          {msg.attachments.map((att, attIdx) => {
                            if (att.attachmentType === "IMAGE") {
                              return (
                                <img
                                  key={att.id || attIdx}
                                  src={resolveMediaUrl(att.url, "community-chat")}
                                  alt="Attachment"
                                  className="max-h-60 rounded-xl object-cover cursor-pointer hover:opacity-95"
                                  onClick={() => window.open(resolveMediaUrl(att.url, "community-chat"), "_blank")}
                                />
                              );
                            }
                            return (
                              <a
                                key={att.id || attIdx}
                                href={resolveMediaUrl(att.url, "community-chat")}
                                target="_blank"
                                rel="noreferrer"
                                className="flex items-center gap-2 p-2 bg-base-300/40 rounded-lg text-xs hover:underline"
                              >
                                <Paperclip size={14} />
                                <span>{att.fileName || "Download file"}</span>
                              </a>
                            );
                          })}
                        </div>
                      )}

                      {/* Bottom row: Time + Reactions + Status */}
                      <div className="flex items-center justify-end gap-1.5 mt-1 select-none">
                        {msg.expiresAt && <ExpiryTimer expiresAt={msg.expiresAt} />}
                        <span className={`text-[9px] font-medium leading-none ${isMe ? "text-white/70" : "opacity-60"}`}>
                          {formatTime(msg.createdAt)}
                        </span>
                        {isMe && (
                          <span className="text-[10px] leading-none text-white/80">
                            {msg.status === "FAILED" ? (
                              <span className="text-red-400 font-bold" title="Failed to send">!</span>
                            ) : msg.status === "SENDING" ? (
                              <Clock size={10} className="animate-spin opacity-70" />
                            ) : (
                              <CheckCheck size={12} className="text-white/90" />
                            )}
                          </span>
                        )}
                      </div>

                      {/* Reaction bar */}
                      {!msg.isDeleted && msg.reactions && msg.reactions.length > 0 && (
                        <div className="flex flex-wrap gap-1 mt-1.5">
                          {msg.reactions.map((react, idx) => (
                            <button
                              key={idx}
                              onClick={() => handleAddReaction(msg, react.emoji)}
                              className={`badge badge-sm gap-1 cursor-pointer transition-all border ${
                                react.userReacted
                                  ? "badge-primary text-white border-primary"
                                  : "bg-base-200/90 text-base-content border-base-300"
                              }`}
                            >
                              <span>{react.emoji}</span>
                              <span className="text-[10px]">{react.count}</span>
                            </button>
                          ))}
                        </div>
                      )}

                      {/* Hover/Touch action menu trigger */}
                      {!msg.isDeleted && (
                        <div
                          className={`absolute ${
                            isMe 
                              ? "-top-3.5 left-2" 
                              : "-top-3.5 right-2"
                          } ${
                            activeDropdown === String(msg.id || msg.messageId) ? "flex" : "hidden group-hover:flex"
                          } items-center bg-base-100 dark:bg-base-200 backdrop-blur-md border border-base-300 dark:border-white/15 rounded-full shadow-lg px-1.5 py-0.5 z-[50] transition-all`}
                        >
                          <button
                            onClick={() => setReplyMessage(msg)}
                            className="btn btn-ghost btn-circle btn-xs text-base-content/70 hover:text-primary cursor-pointer"
                            title="Reply"
                          >
                            <Reply size={12} />
                          </button>

                          <div className="relative">
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                const mId = String(msg.id || msg.messageId);
                                setActiveDropdown(activeDropdown === mId ? null : mId);
                              }}
                              className="btn btn-ghost btn-circle btn-xs text-base-content/70 hover:text-primary cursor-pointer"
                              title="More options"
                            >
                              <MoreVertical size={12} />
                            </button>

                            {activeDropdown === String(msg.id || msg.messageId) && (
                              <>
                                <div
                                  className="fixed inset-0 z-[55] cursor-default"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    setActiveDropdown(null);
                                  }}
                                />
                                <div
                                  className={`absolute ${
                                    index >= messages.length - 3 ? "bottom-full mb-2" : "top-full mt-2"
                                  } ${
                                    isMe ? "left-0" : "right-0"
                                  } w-32 bg-base-100 dark:bg-slate-800 rounded-xl shadow-2xl border border-base-300 dark:border-white/15 py-1.5 z-[60] text-xs`}
                                >
                                  {isAdmin && (
                                    <button
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        handlePinToggle(msg);
                                      }}
                                      className="w-full text-left px-3 py-2 text-xs text-base-content hover:bg-base-200 dark:hover:bg-slate-700 flex items-center justify-between font-medium cursor-pointer"
                                    >
                                      <div className="flex items-center gap-2">
                                        <Pin size={13} className="text-primary shrink-0" />
                                        <span>{msg.isPinned ? "Unpin" : "Pin"}</span>
                                      </div>
                                    </button>
                                  )}
                                  {!isMe && (
                                    <button
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        setActiveDropdown(null);
                                        setReportModalMessage(msg);
                                      }}
                                      className="w-full text-left px-3 py-2 text-xs text-warning hover:bg-base-200 dark:hover:bg-slate-700 flex items-center gap-2 font-medium cursor-pointer"
                                    >
                                      <AlertTriangle size={13} className="shrink-0" />
                                      <span>Report</span>
                                    </button>
                                  )}
                                  {(isAdmin || isMe) && (
                                    <button
                                      onClick={async (e) => {
                                        e.stopPropagation();
                                        setActiveDropdown(null);
                                        const mId = msg.id || msg.messageId;
                                        if (mId) setDeleteConfirmMessageId(mId);
                                      }}
                                      className="w-full text-left px-3 py-2 text-xs text-red-500 hover:bg-base-200 dark:hover:bg-slate-700 flex items-center gap-2 font-medium border-t border-base-300 dark:border-white/10 cursor-pointer"
                                    >
                                      <Trash2 size={13} className="shrink-0" />
                                      <span>Delete</span>
                                    </button>
                                  )}
                                </div>
                              </>
                            )}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Avatar (for outgoing messages on the right) */}
                  {isMe && (
                    <div className="w-8 shrink-0 self-end mb-0.5">
                      <img
                        src={
                          resolveMediaUrl(userProfile?.profileImage || msg.sender?.profileImage, "social-posts") ||
                          `https://api.dicebear.com/7.x/bottts/svg?seed=${userProfile?.username || msg.sender?.username || "me"}`
                        }
                        alt={userProfile?.username || msg.sender?.username || "Me"}
                        className="w-7 h-7 sm:w-8 sm:h-8 rounded-full border border-base-300 dark:border-white/10 object-cover shadow-xs"
                      />
                    </div>
                  )}
                </div>
              );
            })}
            <div ref={chatEndRef} />
          </>
        )}
      </div>

      {/* Floating scroll to bottom button */}
      {showScrollBottom && (
        <button
          onClick={() => scrollToBottom("smooth")}
          className="absolute bottom-20 right-4 btn btn-circle btn-primary btn-sm shadow-lg border border-primary/20 z-10"
        >
          <ChevronDown size={18} />
        </button>
      )}

      {/* ── Reply Bar Indicator ── */}
      {replyMessage && (() => {
        const latestMsg = messages.find((m) => {
          const mId = m.id || m.messageId;
          const rId = replyMessage.id || replyMessage.messageId;
          return mId && rId && String(mId) === String(rId);
        });
        const msgToPreview = latestMsg || replyMessage;

        return (
          <div className="shrink-0 flex items-center justify-between px-4 py-2 bg-base-100 border-t border-base-300 text-xs animate-in slide-in-from-bottom duration-200">
            <div className="flex items-center gap-2 border-l-2 border-primary pl-2 overflow-hidden mr-4">
              <Reply size={13} className="text-primary shrink-0" />
              <div className="truncate text-base-content/85">
                <span className="font-semibold text-xs">
                  Replying to @{replyMessage.sender.actualUsername || replyMessage.sender.username}
                </span>
                <div className="truncate max-w-xs mt-0.5">
                  {renderMessagePreviewSnippet(msgToPreview)}
                </div>
              </div>
            </div>
            <button
              onClick={() => setReplyMessage(null)}
              className="btn btn-ghost btn-circle btn-xs text-base-content/65 hover:text-error"
              title="Cancel reply"
            >
              <X size={14} />
            </button>
          </div>
        );
      })()}

      {/* ── Attached Community Post Indicator ── */}
      {attachedPost && (
        <div className="shrink-0 flex items-center justify-between px-4 py-2.5 bg-blue-500/10 dark:bg-blue-900/20 border-t border-blue-500/20 text-xs">
          <div className="flex items-center gap-2.5 overflow-hidden mr-3">
            <div className="w-8 h-8 rounded-xl bg-[#1D4ED8] text-white flex items-center justify-center shrink-0 shadow-xs">
              <MessageSquare size={16} />
            </div>
            <div className="truncate text-base-content">
              <div className="flex items-center gap-1.5 font-bold text-[11px] text-[#1D4ED8] dark:text-blue-400">
                <span>Replying to post</span>
                {attachedPost.authorUsername && (
                  <span className="font-extrabold text-base-content">@{attachedPost.authorUsername}</span>
                )}
              </div>
              <p className="truncate opacity-75 text-[11px] max-w-sm mt-0.5 font-medium">
                "{decodeHTML(attachedPost.content)}"
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              setAttachedPost(null);
              onClearSharedPost?.();
            }}
            className="btn btn-ghost btn-circle btn-xs text-base-content/65 hover:text-error"
            title="Cancel sharing post"
          >
            <X size={14} />
          </button>
        </div>
      )}

      {/* ── Input Composer ── */}
      <div className="shrink-0 p-3 bg-base-100 border-t border-base-300 backdrop-blur-md bg-base-100/90">
        {isComposerLocked ? (
          <div className="flex items-center justify-center gap-2 p-2 bg-base-200 text-base-content/60 rounded-xl text-xs font-semibold">
            <Lock size={14} />
            <span>Administrator has disabled group chat in this community</span>
          </div>
        ) : (
          <div className="relative w-full">
            {/* Sticker / Emoji Menu Overlay */}
            {showStickerMenu && (
              <div className="absolute bottom-[52px] left-0 right-0 bg-base-100 border border-base-300 rounded-2xl shadow-2xl p-2 z-30 max-h-[260px] overflow-y-auto scrollbar-hide">
                <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1.5 px-1">Govlyx Emojis</p>
                <div className="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-7 gap-1.5 mb-1">
                  {GOVLYX_EMOJIS.map((url, i) => (
                    <button
                      key={`gov-${i}`}
                      type="button"
                      onClick={() => {
                        sendMessage(url, replyMessage?.id);
                        setReplyMessage(null);
                        setShowStickerMenu(false);
                      }}
                      className="p-1 hover:bg-base-200/80 rounded-xl transition-all aspect-square flex items-center justify-center cursor-pointer border border-base-300/30 hover:border-primary/40 hover:scale-110 active:scale-95"
                    >
                      <img
                        src={url}
                        alt="Govlyx Emoji"
                        loading="lazy"
                        className="w-12 h-12 max-w-[46px] max-h-[46px] object-contain drop-shadow-sm"
                      />
                    </button>
                  ))}
                </div>
              </div>
            )}

            <form onSubmit={handleSend} className="flex gap-2 items-center">
              <button
                type="button"
                onClick={() => setShowStickerMenu(!showStickerMenu)}
                className={`btn btn-ghost btn-circle btn-sm h-10 w-10 shrink-0 shadow-sm transition-colors rounded-xl bg-base-200 border-none ${showStickerMenu ? "text-primary bg-primary/10" : "text-base-content/65 hover:text-base-content"}`}
                title="Stickers"
              >
                <Smile size={18} />
              </button>
              <input
                type="text"
                value={inputText}
                onChange={(e) => {
                  setInputText(e.target.value);
                  sendTyping();
                }}
                placeholder={
                  error?.toLowerCase().includes("frozen")
                    ? "This Secret community is frozen (owner pass expired)..."
                    : attachedPost
                    ? "Add a comment, tag or press send to share post..."
                    : "Write a message..."
                }
                className="input input-sm flex-1 bg-base-200 border-none rounded-xl text-sm focus:outline-none focus:ring-1 focus:ring-primary h-10 px-4"
                disabled={isLoading || !!error}
              />
              <button
                type="submit"
                disabled={isLoading || !!error || (!inputText.trim() && !attachedPost)}
                className="btn btn-primary btn-circle btn-sm h-10 w-10 shrink-0 shadow-sm transition-transform active:scale-95 cursor-pointer"
                title={attachedPost ? "Send post to community chat" : "Send message"}
              >
                <Send size={14} />
              </button>
            </form>
          </div>
        )}
      </div>

      {/* ── Admin Chat Settings Modal ── */}
      {isSettingsOpen && (
        <div className="fixed inset-0 z-[150] flex items-center justify-center p-4 bg-black/60">
          <div className="bg-base-100 rounded-2xl max-w-md w-full p-6 space-y-4 border border-black/10 dark:border-white/15 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-lg flex items-center gap-2">
                <Settings className="text-primary" size={20} />
                <span>Chat Admin Settings</span>
              </h3>
              <button
                onClick={() => setIsSettingsOpen(false)}
                className="btn btn-ghost btn-circle btn-sm"
              >
                <X size={18} />
              </button>
            </div>

            <div className="space-y-4 py-2">
              <div className="space-y-2">
                <span className="text-xs font-semibold opacity-75">Chat Permission</span>
                
                {/* Option 1: Allow members to chat */}
                <label className="flex items-center justify-between bg-base-200 p-3 rounded-xl cursor-pointer hover:bg-base-300/40 transition-colors">
                  <div className="flex flex-col">
                    <span className="text-sm font-semibold">Allow members to chat</span>
                    <span className="text-[10px] opacity-60">Everyone in the community can send messages</span>
                  </div>
                  <input
                    type="radio"
                    name="chatPermission"
                    checked={tempSettings.isGroupChatEnabled === true}
                    onChange={() => setTempSettings((prev) => ({ ...prev, isGroupChatEnabled: true }))}
                    className="radio radio-primary"
                  />
                </label>

                {/* Option 2: Only admin can send messages */}
                <label className="flex items-center justify-between bg-base-200 p-3 rounded-xl cursor-pointer hover:bg-base-300/40 transition-colors">
                  <div className="flex flex-col">
                    <span className="text-sm font-semibold">Only admin can send messages</span>
                    <span className="text-[10px] opacity-60">Members can only read announcements and updates</span>
                  </div>
                  <input
                    type="radio"
                    name="chatPermission"
                    checked={tempSettings.isGroupChatEnabled === false}
                    onChange={() => setTempSettings((prev) => ({ ...prev, isGroupChatEnabled: false }))}
                    className="radio radio-primary"
                  />
                </label>
              </div>

              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-semibold opacity-75">Message Retention Period</label>
                </div>
                <select
                  value={tempSettings.chatRetentionDays}
                  onChange={(e) => {
                    setTempSettings((prev) => ({ ...prev, chatRetentionDays: Number(e.target.value) }));
                  }}
                  className="select select-bordered select-sm w-full rounded-xl"
                >
                  <option value={7}>7 Days (1 Week)</option>
                  <option value={14}>14 Days (2 Weeks)</option>
                  <option value={30}>30 Days (1 Month)</option>
                  <option value={90}>90 Days (3 Months)</option>
                  <option value={0}>Forever (No Auto-Delete)</option>
                </select>
              </div>
            </div>

            <div className="flex gap-2 justify-end pt-3">
              <button onClick={() => setIsSettingsOpen(false)} className="btn btn-sm btn-ghost">
                Cancel
              </button>
              <button onClick={handleSaveSettings} className="btn btn-sm btn-primary">
                Save Changes
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Report Message Modal ── */}
      {reportModalMessage && (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center p-4 bg-black/60">
          <div className="bg-base-100 rounded-2xl max-w-md w-full p-6 space-y-4 border border-black/10 dark:border-white/15 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-lg text-warning flex items-center gap-2">
                <AlertTriangle size={20} />
                <span>Report Message</span>
              </h3>
              <button
                onClick={() => setReportModalMessage(null)}
                className="btn btn-ghost btn-circle btn-sm"
              >
                <X size={18} />
              </button>
            </div>

            <div className="space-y-4">
              <div className="bg-base-200 p-3 rounded-xl text-xs space-y-1 opacity-70">
                <span className="font-semibold block">
                  Reported User: @
                  {reportModalMessage.sender.actualUsername || reportModalMessage.sender.username}
                </span>
                <p className="truncate">{reportModalMessage.content}</p>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold opacity-75">Violation Category</label>
                <select
                  value={reportCategory}
                  onChange={(e) => setReportCategory(e.target.value)}
                  className="select select-bordered select-sm w-full rounded-xl"
                >
                  <option value="SPAM">Spam</option>
                  <option value="HARASSMENT">Harassment or Abuse</option>
                  <option value="HATE_SPEECH">Hate Speech</option>
                  <option value="VIOLENCE">Violence or Harm</option>
                  <option value="OTHER">Other guidelines violation</option>
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold opacity-75">Description (optional)</label>
                <textarea
                  value={reportDescription}
                  onChange={(e) => setReportDescription(e.target.value)}
                  placeholder="Provide additional details..."
                  className="textarea textarea-bordered textarea-sm w-full rounded-xl h-20"
                />
              </div>
            </div>

            <div className="flex gap-2 justify-end pt-3">
              <button onClick={() => setReportModalMessage(null)} className="btn btn-sm btn-ghost">
                Cancel
              </button>
              <button onClick={handleReportSubmit} className="btn btn-sm btn-primary">
                Submit Report
              </button>
            </div>
          </div>
        </div>
      )}
      {/* ── Delete Message Confirm Modal ── */}
      {deleteConfirmMessageId && (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-base-100 rounded-2xl max-w-sm w-full p-6 space-y-4 border border-black/10 dark:border-white/15 shadow-2xl">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-red-500/10 flex items-center justify-center shrink-0">
                <Trash2 size={18} className="text-red-500" />
              </div>
              <div>
                <h3 className="font-bold text-base">Delete Message</h3>
                <p className="text-xs text-base-content/60 mt-0.5">This action cannot be undone.</p>
              </div>
            </div>
            <p className="text-sm text-base-content/75">
              Are you sure you want to delete this message?
            </p>
            <div className="flex gap-2 justify-end pt-1">
              <button
                onClick={() => setDeleteConfirmMessageId(null)}
                className="btn btn-sm btn-ghost rounded-xl"
              >
                Cancel
              </button>
              <button
                onClick={async () => {
                  const mId = deleteConfirmMessageId;
                  setDeleteConfirmMessageId(null);
                  try {
                    await deleteMessage(mId);
                    showToast.success("Message deleted");
                  } catch {
                    showToast.error("Failed to delete message");
                  }
                }}
                className="btn btn-sm bg-red-500 hover:bg-red-600 text-white border-none rounded-xl"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}

function WatermarkOverlay({ 
  username, 
  className = "opacity-[0.03] dark:opacity-[0.02] text-base-content" 
}: { 
  username: string; 
  className?: string; 
}) {
  return (
    <div className={`absolute inset-0 pointer-events-none select-none overflow-hidden z-[5] ${className}`}>
      <div 
        className="w-[150%] h-[150%] -left-[25%] -top-[25%] absolute flex flex-col justify-around rotate-[-25deg]"
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: '40px',
        }}
      >
        {Array.from({ length: 15 }).map((_, i) => {
          const isEven = i % 2 === 0;
          return (
            <div 
              key={i} 
              className="whitespace-nowrap font-black uppercase text-[14px] tracking-[0.2em] flex gap-20"
              style={{
                animation: `watermark-scroll-${isEven ? 'left' : 'right'} ${30 + (i % 5) * 5}s linear infinite`,
                transform: `translateX(${isEven ? '0' : '-30'}%)`,
              }}
            >
              {Array.from({ length: 10 }).map((_, j) => (
                <span key={j}>{username}</span>
              ))}
            </div>
          );
        })}
      </div>
      <style>{`
        @keyframes watermark-scroll-left {
          0% { transform: translateX(0); }
          100% { transform: translateX(-50%); }
        }
        @keyframes watermark-scroll-right {
          0% { transform: translateX(-50%); }
          100% { transform: translateX(0); }
        }
      `}</style>
    </div>
  );
}
