// src/components/layout/StrangerChat.tsx
import { useEffect, useRef, useState, useCallback, useMemo, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { Dices, Zap, Search, AlertTriangle, Plus, Image as ImageIcon, X, Eye, EyeOff, Send, Trash2, LogOut, ChevronLeft, ChevronRight, Copy, Check, CheckCheck, MoreVertical, Smile } from "lucide-react";
import { showToast } from "../../utils/toast";
import { motion, AnimatePresence, useMotionValue, useTransform } from "framer-motion";
import { useChat } from "../../hooks/useChat";
import { sendMedia } from "../../api/chatApi.service";
import { API_BASE_URL } from "../../api/axiosConfig";
import type { ChatMessageDto, ChatStatus, MessageType } from "../../types/Chat.types";
import { useCurrentUser } from "../../hooks/useUser";
import { useTheme } from "../../hooks/useTheme";
import { getAuthToken } from "../../utils/auth";
import LimitReachedModal from "../modals/LimitReachedModal";
import { ChatMediaPicker } from "../chat/ChatMediaPicker";


// ── Icons & Config ──────────────────────────────────────────────────────────

const IconShield = () => (
  <svg width="11" height="11" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
  </svg>
);

const IconReply = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="9 17 4 12 9 7" />
    <path d="M20 18v-2a4 4 0 0 0-4-4H4" />
  </svg>
);

const DOT_CLASS: Record<ChatStatus, string> = {
  IDLE: "bg-base-content/20",
  SEARCHING: "bg-warning animate-pulse",
  CONNECTED: "bg-success shadow-[0_0_8px_rgba(34,197,94,0.5)]",
  PARTNER_LEFT: "bg-error",
  ERROR: "bg-error",
};

const QUICK_CHAT_LIGHT_ICON = "/icons/quick_chat_light_theme.gif";
const QUICK_CHAT_DARK_ICON = "/icons/quick_chat_dark_theme.gif";

const STATUS_LABEL: Record<ChatStatus, string> = {
  IDLE: "Ready",
  SEARCHING: "Finding Match",
  CONNECTED: "Connected",
  PARTNER_LEFT: "Stranger Left",
  ERROR: "Error",
};

const LIMIT_ERROR_KEYWORDS = [
  "limit",
  "upgrade",
  "quota",
  "cap",
  "daily",
  "premium",
  "plan",
  "tier",
  "pass",
  "matchmaking",
];

const isLimitReachedError = (error?: string | null) => {
  if (!error) return false;
  const normalized = error.toLowerCase();
  return LIMIT_ERROR_KEYWORDS.some((keyword) => normalized.includes(keyword));
};

interface ReplyTo {
  messageId: string;
  senderId: string;
  content?: string;
  messageType: MessageType;
}

interface MediaPreview {
  file: File;
  url: string;
  type: "IMAGE" | "VIDEO";
  viewOnce: boolean;
}

// ── Image Compression Utility ────────────────────────────────────────────────
async function compressImage(file: File, maxWidth = 1200, maxHeight = 1200, quality = 0.8): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = (event) => {
      const img = new Image();
      img.src = event.target?.result as string;
      img.onload = () => {
        const canvas = document.createElement("canvas");
        let width = img.width;
        let height = img.height;

        if (width > height) {
          if (width > maxWidth) {
            height *= maxWidth / width;
            width = maxWidth;
          }
        } else {
          if (height > maxHeight) {
            width *= maxHeight / height;
            height = maxHeight;
          }
        }

        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d");
        ctx?.drawImage(img, 0, 0, width, height);
        // Returns base64
        resolve(canvas.toDataURL("image/jpeg", quality));
      };
      img.onerror = (err) => reject(err);
    };
    reader.onerror = (err) => reject(err);
  });
}

// ── Data URL → Blob Utility ──────────────────────────────────────────────────
function dataURLtoBlob(dataUrl: string): Blob {
  const [header, base64] = dataUrl.split(",");
  const mime = header.match(/:(.*?);/)?.[1] ?? "application/octet-stream";
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return new Blob([bytes], { type: mime });
}

// ── Global Media Cache Helper ────────────────────────────────────────────────
export function getCachedMediaUrl(uuid: string): string {
  if (!uuid) return "";
  if (uuid.startsWith("data:") || uuid.startsWith("blob:") || uuid.startsWith("http")) {
    return uuid;
  }
  const globalCache = (window as any).__mediaCache;
  return globalCache?.get(uuid) || "";
}

// ── Safe Image Component with Authenticated Blob Fetching ────────────────────
export function SafeImage({
  mediaPayload,
  sessionId,
  className,
  onClick,
  isSticker,
  ...props
}: {
  mediaPayload?: string;
  sessionId: string;
  className?: string;
  onClick?: (e: React.MouseEvent<HTMLImageElement>) => void;
  isSticker?: boolean;
  [key: string]: any;
}) {
  const [url, setUrl] = useState<string>("");
  const [error, setError] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    if (!mediaPayload) return;
    if (mediaPayload.startsWith("data:") || mediaPayload.startsWith("blob:") || mediaPayload.startsWith("http") || mediaPayload.startsWith("/")) {
      setUrl(mediaPayload);
      return;
    }

    const globalCache = (window as any).__mediaCache || new Map();
    (window as any).__mediaCache = globalCache;

    if (globalCache.has(mediaPayload)) {
      setUrl(globalCache.get(mediaPayload));
      return;
    }

    let active = true;
    setLoading(true);
    setError(false);

    const token = getAuthToken();
    const headers: HeadersInit = token ? { Authorization: `Bearer ${token}` } : {};

    fetch(`${API_BASE_URL}/api/chat/${sessionId}/media/${mediaPayload}`, { headers })
      .then(async (res) => {
        if (!res.ok) throw new Error("Failed to fetch");
        let blob = await res.blob();
        if (isSticker) {
          blob = new Blob([blob], { type: "image/svg+xml" });
        }
        if (!active) return;
        const objectUrl = URL.createObjectURL(blob);
        globalCache.set(mediaPayload, objectUrl);
        setUrl(objectUrl);
      })
      .catch((err) => {
        console.error("Failed to load image", err);
        if (active) setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [mediaPayload, sessionId, isSticker]);

  if (loading) {
    return (
      <div className={`${className} flex items-center justify-center bg-base-300 animate-pulse min-h-[150px]`}>
        <span className="loading loading-spinner loading-sm opacity-50" />
      </div>
    );
  }

  if (error || !url) {
    return (
      <div className={`${className} flex flex-col items-center justify-center bg-base-300 text-error min-h-[150px] p-4 text-center`}>
        <AlertTriangle size={20} className="mb-1" />
        <span className="text-[10px] font-bold">Failed to load</span>
      </div>
    );
  }

  return <img src={url} className={className} onClick={onClick} {...props} />;
}

// ── Safe Video Component with Authenticated Blob Fetching ────────────────────
export function SafeVideo({
  mediaPayload,
  sessionId,
  className,
  ...props
}: {
  mediaPayload?: string;
  sessionId: string;
  className?: string;
  [key: string]: any;
}) {
  const [url, setUrl] = useState<string>("");
  const [error, setError] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    if (!mediaPayload) return;
    if (mediaPayload.startsWith("data:") || mediaPayload.startsWith("blob:") || mediaPayload.startsWith("http")) {
      setUrl(mediaPayload);
      return;
    }

    const globalCache = (window as any).__mediaCache || new Map();
    (window as any).__mediaCache = globalCache;

    if (globalCache.has(mediaPayload)) {
      setUrl(globalCache.get(mediaPayload));
      return;
    }

    let active = true;
    setLoading(true);
    setError(false);

    const token = getAuthToken();
    const headers: HeadersInit = token ? { Authorization: `Bearer ${token}` } : {};

    fetch(`${API_BASE_URL}/api/chat/${sessionId}/media/${mediaPayload}`, { headers })
      .then(async (res) => {
        if (!res.ok) throw new Error("Failed to fetch");
        const blob = await res.blob();
        if (!active) return;
        const objectUrl = URL.createObjectURL(blob);
        globalCache.set(mediaPayload, objectUrl);
        setUrl(objectUrl);
      })
      .catch((err) => {
        console.error("Failed to load video", err);
        if (active) setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [mediaPayload, sessionId]);

  if (loading) {
    return (
      <div className={`${className} flex items-center justify-center bg-base-300 animate-pulse min-h-[150px]`}>
        <span className="loading loading-spinner loading-sm opacity-50" />
      </div>
    );
  }

  if (error || !url) {
    return (
      <div className={`${className} flex flex-col items-center justify-center bg-base-300 text-error min-h-[150px] p-4 text-center`}>
        <AlertTriangle size={20} className="mb-1" />
        <span className="text-[10px] font-bold">Failed to load</span>
      </div>
    );
  }

  return <video src={url} className={className} {...props} />;
}

// ── Private Media self-destruct overlay ───────────────────────────────────────
function PrivateMediaViewer({
  msg,
  sessionId,
  username,
  onClose,
  deleteMedia
}: {
  msg: ChatMessageDto;
  sessionId: string;
  username: string;
  onClose: () => void;
  deleteMedia: (id: string) => Promise<void>;
}) {
  const [url, setUrl] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  
  const totalSeconds = msg.viewTimer ?? 10;
  const [timeLeft, setTimeLeft] = useState<number>(totalSeconds);
  const timerStartedRef = useRef<boolean>(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const deletedRef = useRef<boolean>(false);

  const triggerWipe = useCallback(async () => {
    if (deletedRef.current) return;
    deletedRef.current = true;
    try {
      await deleteMedia(msg.messageId);
    } catch (e) {
      console.error("Failed to wipe view-once media", e);
    }
  }, [deleteMedia, msg.messageId]);

  // Fetch media on mount
  useEffect(() => {
    let active = true;
    const uuid = msg.mediaPayload;
    if (!uuid) {
      setError("No media content found");
      setLoading(false);
      return;
    }

    if (uuid.startsWith("data:") || uuid.startsWith("blob:") || uuid.startsWith("http")) {
      setUrl(uuid);
      setLoading(false);
      return;
    }

    const token = getAuthToken();
    const headers: HeadersInit = token ? { Authorization: `Bearer ${token}` } : {};

    fetch(`${API_BASE_URL}/api/chat/${sessionId}/media/${uuid}`, { headers })
      .then(async (res) => {
        if (!res.ok) throw new Error("Failed to load secure media");
        const blob = await res.blob();
        if (!active) return;
        const objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch((err) => {
        console.error("Private media fetch failed", err);
        if (active) setError("Could not load private media");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [msg.mediaPayload, sessionId]);

  // Start the countdown timer when URL is loaded
  useEffect(() => {
    if (!url || loading || error || timerStartedRef.current) return;
    timerStartedRef.current = true;

    intervalRef.current = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          triggerWipe().then(() => onClose());
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [url, loading, error, triggerWipe, onClose]);

  const handleClose = () => {
    triggerWipe().then(() => onClose());
  };

  const preventDefault = (e: React.SyntheticEvent) => {
    e.preventDefault();
  };

  const content = (
    <div 
      className="fixed inset-0 z-[9999] bg-black/95 backdrop-blur-2xl flex flex-col items-center justify-center p-4 md:p-8 select-none"
      onContextMenu={preventDefault}
    >
      <WatermarkOverlay username={username} className="text-white opacity-[0.05] z-[10005]" />
      {/* Top Header: Timer and Close Button */}
      <div className="absolute top-[max(env(safe-area-inset-top,0px),1rem)] inset-x-4 md:inset-x-6 z-[10010] flex items-center justify-between pointer-events-none">
        <div className="flex items-center gap-3 bg-white/10 px-4 py-2 rounded-2xl backdrop-blur-md border border-white/10 shadow-2xl pointer-events-auto">
          {/* Circular Countdown Progress */}
          <div className="relative w-6 h-6 flex items-center justify-center font-black text-xs text-white">
            <svg className="absolute inset-0 w-full h-full -rotate-90">
              <circle 
                cx="12" cy="12" r="10" 
                className="stroke-white/20 fill-none" 
                strokeWidth="2" 
              />
              <circle 
                cx="12" cy="12" r="10" 
                className="stroke-[#1D4ED8] fill-none transition-all duration-1000 ease-linear" 
                strokeWidth="2" 
                strokeDasharray="62.8"
                strokeDashoffset={62.8 - (62.8 * timeLeft) / totalSeconds}
              />
            </svg>
            <span className="relative z-10">{timeLeft}</span>
          </div>
          <span className="text-[10px] font-black text-white/80 uppercase tracking-widest">Viewing Private Media</span>
        </div>
        
        <button 
          onClick={handleClose}
          className="text-white p-3 bg-white/15 hover:bg-white/25 rounded-full backdrop-blur-md shadow-2xl border border-white/20 transition-all pointer-events-auto cursor-pointer"
        >
          <X size={20} />
        </button>
      </div>

      {/* Main Container */}
      <div className="w-full h-full flex items-center justify-center relative touch-pan-x p-4 py-20 pb-24 md:p-12">
        {loading && (
          <div className="flex flex-col items-center gap-4 text-white/50">
            <span className="loading loading-spinner loading-lg text-[#1D4ED8]" />
            <p className="text-[10px] font-black uppercase tracking-[0.3em]">Decrypting Media...</p>
          </div>
        )}

        {error && (
          <div className="flex flex-col items-center gap-4 text-error max-w-sm text-center">
            <div className="w-16 h-16 rounded-full bg-error/10 flex items-center justify-center shadow-inner border border-error/10"><AlertTriangle size={32} /></div>
            <div>
              <p className="text-lg font-black text-white">Wiped or Missing</p>
              <p className="text-xs text-white/40 mt-1">{error}</p>
            </div>
            <button onClick={handleClose} className="btn btn-sm btn-error px-6 rounded-xl mt-2 font-bold">Close</button>
          </div>
        )}

        {!loading && !error && url && (
          <div className="relative w-full h-full flex items-center justify-center pointer-events-none">
            {msg.messageType === "IMAGE" ? (
              <img 
                src={url} 
                className="max-w-full max-h-full object-contain rounded-3xl shadow-2xl border border-white/5 select-none" 
                onDragStart={preventDefault}
                alt="Secure media"
              />
            ) : (
              <video 
                src={url} 
                autoPlay 
                playsInline
                className="max-w-full max-h-full object-contain rounded-3xl shadow-2xl bg-black/50 border border-white/5" 
              />
            )}
          </div>
        )}
      </div>
      
      {/* Anti-screenshot hint */}
      <div className="absolute bottom-8 text-center pointer-events-none">
        <p className="text-[10px] font-black text-white/20 uppercase tracking-[0.4em]">Self-Destruct Active</p>
      </div>
    </div>
  );

  return typeof document !== "undefined" ? createPortal(content, document.body) : content;
}

function WatermarkOverlay({ 
  username, 
  className = "opacity-[0.03] dark:opacity-[0.02]" 
}: { 
  username: string; 
  className?: string; 
}) {
  return (
    <div className={`absolute inset-0 pointer-events-none select-none overflow-hidden z-20 ${className}`}>
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

// ── Main Component ───────────────────────────────────────────────────────────

export default function StrangerChat({ onClose, standalone }: { onClose?: () => void; standalone?: boolean }) {
  const { data: currentUser } = useCurrentUser();
  const { theme } = useTheme();
  const usernameWatermark = currentUser?.actualUsername || currentUser?.username || "Govlyx User";
  const chat = useChat();

  const [draft, setDraft] = useState("");
  const [showLimitModal, setShowLimitModal] = useState(false);
  const [limitModalMessage, setLimitModalMessage] = useState("");
  const [hasReachedChatLimit, setHasReachedChatLimit] = useState(false);
  const [replyTo, setReplyTo] = useState<ReplyTo | null>(null);
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const [showStickerMenu, setShowStickerMenu] = useState(false);
  const [mediaPreviews, setMediaPreviews] = useState<MediaPreview[]>([]);
  const [fullscreenMedia, setFullscreenMedia] = useState<{ items: { url: string, type: MessageType }[], index: number } | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isSendingGif, setIsSendingGif] = useState(false);
  const [confirmAction, setConfirmAction] = useState<"next" | "clear" | "leave" | null>(null);
  
  // Custom states for view once media options and security overlay
  const [selectedTimer, setSelectedTimer] = useState<3 | 10 | 30>(10);
  const [privateMedia, setPrivateMedia] = useState<ChatMessageDto | null>(null);

  // Media zoom lightbox states
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);
  const dragStart = useRef<{ mx: number; my: number; px: number; py: number } | null>(null);
  const touchStart = useRef<{ tx: number; ty: number; px: number; py: number } | null>(null);

  const resetZoom = () => { setZoom(1); setPan({ x: 0, y: 0 }); };

  // ── Mobile keyboard awareness ────────────────────────────────────────────────
  // When the soft keyboard opens on mobile, visualViewport.height shrinks.
  // We track it so the container never sits behind the keyboard.
  const [viewportHeight, setViewportHeight] = useState<number | null>(null);
  useEffect(() => {
    const vv = window.visualViewport;
    if (!vv) return;
    const onResize = () => setViewportHeight(vv.height);
    vv.addEventListener("resize", onResize);
    return () => vv.removeEventListener("resize", onResize);
  }, []);

  const zoomIn  = () => setZoom(z => Math.min(z + 0.5, 5));
  const zoomOut = () => setZoom(z => { const next = Math.max(z - 0.5, 1); if (next === 1) setPan({ x: 0, y: 0 }); return next; });

  // Mouse-wheel zoom
  const onWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.deltaY < 0) zoomIn();
    else zoomOut();
  };

  // Drag / pan support
  const onMouseDown = (e: React.MouseEvent) => {
    if (zoom <= 1) return;
    e.preventDefault();
    setDragging(true);
    dragStart.current = { mx: e.clientX, my: e.clientY, px: pan.x, py: pan.y };
  };
  const onMouseMove = (e: React.MouseEvent) => {
    if (!dragging || !dragStart.current) return;
    const dx = e.clientX - dragStart.current.mx;
    const dy = e.clientY - dragStart.current.my;
    setPan({ x: dragStart.current.px + dx, y: dragStart.current.py + dy });
  };
  const onMouseUp = () => { setDragging(false); dragStart.current = null; };

  // Touch pan
  const onTouchStart = (e: React.TouchEvent) => {
    if (zoom <= 1 || e.touches.length !== 1) return;
    touchStart.current = { tx: e.touches[0].clientX, ty: e.touches[0].clientY, px: pan.x, py: pan.y };
  };
  const onTouchMove = (e: React.TouchEvent) => {
    if (!touchStart.current || e.touches.length !== 1) return;
    setPan({
      x: touchStart.current.px + (e.touches[0].clientX - touchStart.current.tx),
      y: touchStart.current.py + (e.touches[0].clientY - touchStart.current.ty),
    });
  };
  const onTouchEnd = () => { touchStart.current = null; };

  useEffect(() => {
    resetZoom();
  }, [fullscreenMedia?.index, fullscreenMedia === null]);

  const isLimitError = isLimitReachedError(chat.error);

  useEffect(() => {
    if (chat.status === "ERROR" && isLimitError) {
      setLimitModalMessage(chat.error ?? "You have reached your daily matchmaking limit. Upgrade to a premium pass for unlimited chats.");
      setHasReachedChatLimit(true);
      setShowLimitModal(true);
    }
  }, [chat.status, chat.error, isLimitError]);



  const inputRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (chat.status === "CONNECTED") {
      inputRef.current?.focus({ preventScroll: true });
    }
  }, [chat.status]);

  useEffect(() => {
    if (chat.status !== "CONNECTED") {
      setReplyTo(null);
      setMediaPreviews([]);
      setShowAttachMenu(false);
    }
  }, [chat.status]);

  // Auto-resize textarea height as user types
  useEffect(() => {
    const textarea = inputRef.current;
    if (!textarea) return;
    textarea.style.height = "auto";
    textarea.style.height = `${Math.min(textarea.scrollHeight, 128)}px`;
  }, [draft]);

  const handleSend = () => {
    if (!draft.trim()) return;
    chat.sendMessage(draft, replyTo?.messageId);
    setDraft("");
    setReplyTo(null);
  };

  const openChatLimitModal = useCallback(() => {
    setLimitModalMessage(chat.error ?? "You have reached your daily matchmaking limit. Buy a plan to continue chatting.");
    setHasReachedChatLimit(true);
    setShowLimitModal(true);
  }, [chat.error]);

  const handleStartSearch = useCallback(() => {
    if (hasReachedChatLimit || isLimitError) {
      openChatLimitModal();
      return;
    }
    chat.startSearch();
  }, [chat, hasReachedChatLimit, isLimitError, openChatLimitModal]);

  const handleFileSelect = () => {
    if (fileInputRef.current) {
      fileInputRef.current.accept = "image/*,video/*";
      fileInputRef.current.multiple = true;
      fileInputRef.current.click();
    }
    setShowAttachMenu(false);
  };

  const handleAttachmentClick = () => {
    handleFileSelect();
  };

  const onFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;

    setIsUploading(true);
    try {
      const newPreviews: MediaPreview[] = [];
      for (const file of files) {
        let url: string;
        let type: "IMAGE" | "VIDEO" = file.type.startsWith("video/") ? "VIDEO" : "IMAGE";

        if (type === "IMAGE") {
          // Compress and get base64 immediately for preview + faster send
          const compressedBase64 = await compressImage(file);
          url = compressedBase64;
        } else {
          url = URL.createObjectURL(file);
        }

        newPreviews.push({
          file,
          url,
          type,
          viewOnce: false
        });
      }
      setMediaPreviews(prev => [...prev, ...newPreviews]);
    } catch (err) {
      console.error("File processing failed", err);
      showToast.error("File processing failed. Please try a different file.");
    } finally {
      setIsUploading(false);
      e.target.value = "";
    }
  };

  const handleSendMedia = async () => {
    if (mediaPreviews.length === 0 || !chat.session) return;
    setIsUploading(true);
    try {
      for (const preview of mediaPreviews) {
        let blob: Blob;
        if (preview.type === "IMAGE") {
          // preview.url is a compressed base64 data-URI — convert to Blob
          blob = dataURLtoBlob(preview.url);
        } else {
          // VIDEO: use the original File directly as a Blob
          blob = preview.file;
        }

        const res = await sendMedia(chat.session.sessionId, {
          type: preview.type,
          file: blob,                     // ← CORRECT: sendMedia expects a Blob
          mimeType: preview.type === "IMAGE" ? "image/jpeg" : preview.file.type,
          mediaName: preview.file.name,
          viewOnce: preview.viewOnce,
          viewTimer: preview.viewOnce ? selectedTimer : undefined,
          replyToId: replyTo?.messageId
        });

        // Pre-seed sender's local URL in the global media cache for instant rendering
        if (res?.data?.messageId) {
          const fileId = res.data.messageId;
          const globalCache = (window as any).__mediaCache || new Map();
          (window as any).__mediaCache = globalCache;
          globalCache.set(fileId, preview.url);
        }
      }
      setMediaPreviews([]);
      setReplyTo(null);
    } catch (err) {
      console.error("Failed to send media", err);
      showToast.error("Failed to send media. Please try again.");
    } finally {
      setIsUploading(false);
    }
  };

  const handleSendSticker = async (stickerUrl: string) => {
    if (!chat.session?.sessionId) {
      showToast.error("Chat session not connected.");
      return;
    }
    try {
      const response = await fetch(stickerUrl);
      if (!response.ok) {
        throw new Error(`Failed to load sticker file: ${response.statusText}`);
      }
      const blob = await response.blob();
      const filename = stickerUrl.split("/").pop() || "sticker.svg";
      const file = new File([blob], filename, { type: blob.type || "image/svg+xml" });

      const res = await sendMedia(chat.session.sessionId, {
        type: "STICKER",
        file,
        mimeType: "image/svg+xml",
        mediaName: filename,
        viewOnce: false,
        replyToId: replyTo?.messageId
      });

      // Pre-seed local sticker URL in cache for instant rendering
      const fileId = (res as any)?.data?.messageId || (res as any)?.messageId;
      if (fileId) {
        const globalCache = (window as any).__mediaCache || new Map();
        (window as any).__mediaCache = globalCache;
        globalCache.set(fileId, stickerUrl);
      }

      setShowStickerMenu(false);
      setReplyTo(null);
    } catch (err: any) {
      console.error("Failed to send sticker:", err);
      const errMsg = err?.message || "Failed to send sticker.";
      showToast.error(errMsg);
    }
  };

  const handleInsertEmoji = (char: string) => {
    if (inputRef.current) {
      const start = inputRef.current.selectionStart || draft.length;
      const end = inputRef.current.selectionEnd || draft.length;
      const newDraft = draft.substring(0, start) + char + draft.substring(end);
      setDraft(newDraft);
      setTimeout(() => {
        if (inputRef.current) {
          inputRef.current.focus();
          inputRef.current.setSelectionRange(start + char.length, start + char.length);
        }
      }, 0);
    } else {
      setDraft((prev) => prev + char);
    }
    chat.notifyTyping();
  };

  const handleSendGif = async (gifUrl: string) => {
    if (!chat.session?.sessionId) {
      showToast.error("Chat session not connected.");
      return;
    }
    try {
      setIsSendingGif(true);
      setShowStickerMenu(false);
      const response = await fetch(gifUrl);
      if (!response.ok) throw new Error(`Failed to load GIF: ${response.statusText}`);
      const blob = await response.blob();
      const filename = "klipy_" + Date.now() + ".gif";
      const file = new File([blob], filename, { type: "image/gif" });

      const res = await sendMedia(chat.session.sessionId, {
        type: "IMAGE",
        file,
        mimeType: "image/gif",
        mediaName: filename,
        viewOnce: false,
        replyToId: replyTo?.messageId,
      });

      const fileId = (res as any)?.data?.messageId || (res as any)?.messageId;
      if (fileId) {
        const globalCache = (window as any).__mediaCache || new Map();
        (window as any).__mediaCache = globalCache;
        globalCache.set(fileId, gifUrl);
      }

      setReplyTo(null);
    } catch (err: any) {
      console.error("Failed to send GIF:", err);
      showToast.error(err?.message || "Failed to send GIF.");
    } finally {
      setIsSendingGif(false);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
    if (e.key === "Escape") {
      e.preventDefault();
      setReplyTo(null);
      setMediaPreviews([]);
      setShowAttachMenu(false);
      setShowStickerMenu(false);
    }
  };

  const handleNext = useCallback(async () => {
    await chat.leaveSession();
    handleStartSearch();
  }, [chat, handleStartSearch]);

  const handleBackdrop = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget && chat.status !== "CONNECTED" && onClose) onClose();
  };




  const renderContent = () => (
    <div
      className={standalone
        ? "w-full flex flex-col bg-base-100 relative overflow-hidden"
        : "flex flex-col w-full md:max-w-2xl h-full md:h-[90vh] md:rounded-3xl overflow-hidden bg-base-200 shadow-2xl backdrop-blur-xl relative border border-base-300"}
      style={standalone ? { height: "100%", minHeight: 0 } : undefined}
    >
      {/* ── Background Glows (Subtle and Theme-adaptive) ── */}
      <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-[#1D4ED8]/5 blur-[120px] pointer-events-none" />
      <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-secondary/5 blur-[120px] pointer-events-none" />

      {chat.status === "CONNECTED" && <WatermarkOverlay username={usernameWatermark} />}


      {/* ── Header Area (Persistent & Sticky for modal mode) ── */}
      {!standalone && (
        <div className="shrink-0 z-20 pt-[env(safe-area-inset-top,0px)]">
          <header className="flex items-center justify-between gap-3 px-4 md:px-6 pb-2 bg-base-300/90 backdrop-blur-xl border-b border-base-300 pt-3 md:pt-4">
            <div className="flex items-center gap-2.5">
              <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-[#1D4ED8] text-white shadow-lg shadow-[#1D4ED8]/20">
                <Dices size={16} />
              </div>
              <div className="flex flex-col">
                <h1 className="text-[13px] font-black text-base-content uppercase tracking-tight leading-none">Stranger Chat</h1>
                <div className="flex items-center gap-1 mt-0.5">
                  <span className={`w-1.5 h-1.5 rounded-full ${DOT_CLASS[chat.status]}`} />
                  <span className="text-[8px] text-base-content/40 uppercase tracking-widest font-black">
                    {STATUS_LABEL[chat.status]}
                  </span>
                </div>
              </div>
            </div>
            <div className="flex items-center gap-2">
              {onClose && (
                <button onClick={onClose} className="btn btn-ghost btn-sm btn-square opacity-60 hover:opacity-100">
                  <X size={20} />
                </button>
              )}
            </div>
          </header>

          {/* ── Persistent Info Banner ── */}
          {(chat.status === "CONNECTED" || chat.status === "PARTNER_LEFT") && (
            <div className="px-4 py-2 bg-base-200/50 backdrop-blur-md border-b border-base-content/5 flex items-center justify-center">
              <div className="flex items-center gap-2 text-base-content/30 font-black uppercase tracking-[0.2em] text-[8px]">
                <IconShield />
                <span>Identity Masked & Encrypted</span>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── Middle: Message / Response Area — fills ALL remaining space ── */}
      <div className="flex-1 w-full flex flex-col overflow-hidden" style={{ minHeight: 0 }}>
        {chat.status === "IDLE" && (
          <IdleScreen onStart={handleStartSearch} theme={theme} />
        )}
        {chat.status === "SEARCHING" && (
          <SearchingScreen queueSize={chat.queueSize} onCancel={chat.cancelSearch} />
        )}
        {(chat.status === "CONNECTED" || chat.status === "PARTNER_LEFT") && (
          <MessageArea
            messages={chat.messages ?? []}
            myId={chat.session?.yourAnonymousId ?? ""}
            partnerTyping={chat.partnerTyping}
            onReply={(msg) => setReplyTo({ messageId: msg.messageId, senderId: msg.senderId, content: msg.content, messageType: msg.messageType })}
            onMediaClick={(items, index) => setFullscreenMedia({ items, index })}
            onUnlockPrivateMedia={(msg) => setPrivateMedia(msg)}
            sessionId={chat.session?.sessionId ?? ""}
            status={chat.status}
          />
        )}
        {chat.status === "ERROR" && (
          <ErrorScreen
            error={chat.error}
            onRetry={handleStartSearch}
          />
        )}
      </div>

      {/* ── Footer: strictly pinned at bottom, never moves ── */}
      {(chat.status === "CONNECTED" || chat.status === "PARTNER_LEFT") && (
        <footer className="w-full shrink-0 z-30 p-2 sm:p-2.5 pb-[max(env(safe-area-inset-bottom,0px),16px)] bg-base-200 backdrop-blur-xl border-t border-base-300">
          <div className="mx-auto w-full">
            <AnimatePresence>
              {replyTo && (
                <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 10 }} className="mb-2 px-3 py-1.5 rounded-xl bg-base-300/50 backdrop-blur-xl border border-base-content/10 flex items-center gap-3">
                  <div className="w-1 rounded-full bg-[#1D4ED8] h-6 shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-[9px] text-[#1D4ED8] font-black uppercase tracking-wider mb-0.5">
                      Replying to {replyTo.senderId === chat.session?.yourAnonymousId ? "yourself" : "stranger"}
                    </p>
                    <div className="text-xs text-base-content/60 truncate font-medium mt-0.5">
                      {replyTo.messageType === "TEXT" ? (
                        replyTo.content?.startsWith("/govlyx-emoji/") ? (
                          <span className="inline-flex items-center gap-1.5 align-middle">
                            <img src={replyTo.content} alt="Emoji" className="w-4 h-4 object-contain inline-block shrink-0" />
                            <span className="text-[11px] font-medium opacity-80">Govlyx Emoji</span>
                          </span>
                        ) : (
                          replyTo.content
                        )
                      ) : (
                        `[${replyTo.messageType}]`
                      )}
                    </div>
                  </div>
                  <button onClick={() => setReplyTo(null)} className="btn btn-ghost btn-xs btn-square">
                    <X size={14} />
                  </button>
                </motion.div>
              )}
            </AnimatePresence>

            {chat.status === "CONNECTED" ? (
              <div className="flex flex-col gap-1.5 sm:gap-2 relative z-50">
                <div className="flex items-center gap-1.5 sm:gap-2 w-full max-w-full relative min-w-0">
                  
                  {/* 3-Option Media Picker: Emojis with Hex Codes & Subcategories, KLIPY GIFs, and Govlyx Stickers */}
                  <ChatMediaPicker
                    isOpen={showStickerMenu}
                    onClose={() => setShowStickerMenu(false)}
                    onSelectEmoji={handleInsertEmoji}
                    onSelectGif={handleSendGif}
                    onSelectSticker={handleSendSticker}
                  />

                  {/* Sticker Toggle Button */}
                  <button
                    type="button"
                    onClick={() => {
                      setShowStickerMenu(!showStickerMenu);
                      setShowAttachMenu(false);
                    }}
                    className={`btn btn-ghost btn-circle btn-sm h-9 w-9 sm:h-10 sm:w-10 shrink-0 shadow-xs transition-colors rounded-xl bg-base-100 border ${showStickerMenu ? "text-[#1D4ED8] bg-[#1D4ED8]/10 border-[#1D4ED8]/25" : "text-base-content/65 hover:text-base-content border-base-300"}`}
                    title="Stickers"
                  >
                    <Smile size={18} />
                  </button>

                  {/* Text Input — with inline GIF sending loader */}
                  <div className="relative flex-1 min-w-0">
                    <textarea
                      ref={inputRef}
                      rows={1}
                      value={draft}
                      onChange={(e) => { setDraft(e.target.value); chat.notifyTyping(); }}
                      onKeyDown={handleKeyDown}
                      onFocus={() => setShowStickerMenu(false)}
                      placeholder="Write a message..."
                      disabled={isSendingGif}
                      className="w-full bg-base-100 border border-base-300 rounded-xl text-[16px] sm:text-sm focus:outline-none focus:ring-1 focus:ring-[#1D4ED8] py-2 px-2.5 sm:px-4 max-h-28 min-h-[38px] sm:min-h-[40px] resize-none leading-tight shadow-none scrollbar-hide text-base-content disabled:opacity-50"
                    />
                    {isSendingGif && (
                      <div className="absolute inset-0 flex items-center gap-2 px-3 bg-base-100/90 backdrop-blur-sm rounded-xl border border-[#1D4ED8]/30 pointer-events-none">
                        <svg className="animate-spin h-4 w-4 text-[#1D4ED8] shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
                        </svg>
                        <span className="text-xs text-[#1D4ED8] font-semibold">Sending GIF...</span>
                      </div>
                    )}
                  </div>


                  {/* Attachments Trigger Button */}
                  <div className="relative shrink-0">
                    <button
                      type="button"
                      onClick={() => { setShowAttachMenu(!showAttachMenu); setShowStickerMenu(false); }}
                      className={`btn btn-ghost btn-circle btn-sm h-9 w-9 sm:h-10 sm:w-10 shrink-0 shadow-xs transition-colors rounded-xl bg-base-100 border ${showAttachMenu ? "text-[#1D4ED8] bg-[#1D4ED8]/10 border-[#1D4ED8]/25" : "text-base-content/65 hover:text-base-content border-base-300"}`}
                      title="Attachments"
                    >
                      <Plus size={18} />
                    </button>
                    <AnimatePresence>
                       {showAttachMenu && (
                         <motion.div initial={{ opacity: 0, scale: 0.9, y: 10 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.9, y: 10 }} className="absolute bottom-[48px] right-0 flex flex-col gap-1.5 min-w-[160px] p-2 rounded-2xl bg-base-200/95 backdrop-blur-xl border border-base-content/10 shadow-2xl z-50 origin-bottom-right">
                           <button onClick={handleAttachmentClick} className="group flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-base-300 text-base-content transition-all cursor-pointer w-full text-left bg-transparent border-none">
                             <div className="p-2 rounded-xl bg-[#1D4ED8]/15 dark:bg-[#1D4ED8]/25 text-[#1D4ED8] dark:text-[#60a5fa] transition-all duration-200 shadow-[0_0_14px_rgba(29,78,216,0.5)] border border-[#1D4ED8]/30">
                               <ImageIcon size={16} className="stroke-[2.5]" />
                             </div>
                             <span className="text-xs font-bold tracking-wide">Media</span>
                           </button>
                         </motion.div>
                       )}
                    </AnimatePresence>
                  </div>

                  {/* Send Button */}
                  <button
                    type="button"
                    onClick={handleSend}
                    disabled={!draft.trim()}
                    className="btn btn-primary bg-[#1D4ED8] hover:bg-[#1e40af] border-none text-white btn-circle btn-sm h-9 w-9 sm:h-10 sm:w-10 shrink-0 shadow-xs transition-transform active:scale-95 flex items-center justify-center cursor-pointer disabled:opacity-40 disabled:bg-slate-700"
                  >
                    <Send size={15} />
                  </button>
                </div>
                <div className="flex items-center justify-between px-1 md:px-2 pt-0.5">
                  <div className="flex items-center gap-3 flex-wrap">
                    <button type="button" onClick={() => setConfirmAction("next")} className="flex items-center gap-1 text-base-content/50 hover:text-[#1D4ED8] transition-colors text-[9px] font-black uppercase tracking-widest leading-none group cursor-pointer border-none bg-transparent">
                      <Zap size={12} className="group-hover:fill-current" /> Next
                    </button>
                    <button type="button" onClick={() => setConfirmAction("clear")} className="flex items-center gap-1 text-base-content/50 hover:text-warning transition-colors text-[9px] font-black uppercase tracking-widest leading-none cursor-pointer border-none bg-transparent">
                      <Trash2 size={12} /> Clear
                    </button>
                    <button type="button" onClick={() => setConfirmAction("leave")} className="flex items-center gap-1 text-base-content/50 hover:text-red-400 transition-colors text-[9px] font-black uppercase tracking-widest leading-none cursor-pointer border-none bg-transparent">
                      <LogOut size={12} /> Leave
                    </button>
                  </div>
                  <div className="flex items-center gap-1.5 select-none pt-0.5 opacity-0 pointer-events-none">
                    {/* Hidden spacer to keep left alignment intact */}
                    <IconShield />
                  </div>
                </div>
              </div>
            ) : (
              <div className="flex flex-col items-center gap-3 bg-base-300/30 p-5 rounded-2xl border border-base-content/5 backdrop-blur-md max-w-sm mx-auto">
                <p className="text-[10px] text-base-content/50 font-black uppercase tracking-[0.2em]">Stranger disconnected</p>
                <div className="flex gap-2 w-full">
                  <button onClick={handleStartSearch} className="btn btn-sm bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 text-white flex-1 h-10 rounded-xl font-black text-[10px] uppercase tracking-widest shadow-lg shadow-[#1D4ED8]/20 border-none">Find New</button>
                  <button onClick={onClose} className="btn btn-sm bg-base-content/5 border-none flex-1 h-10 rounded-xl font-black text-[10px] uppercase tracking-widest">Exit</button>
                </div>
              </div>
            )}
          </div>
        </footer>
      )}

      {/* ── Media Preview Sheet ── */}
      <AnimatePresence>
        {mediaPreviews.length > 0 && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-[120] bg-black/40 backdrop-blur-sm" onClick={() => setMediaPreviews([])}>
            <motion.div initial={{ y: "100%" }} animate={{ y: 0 }} exit={{ y: "100%" }} transition={{ type: "spring", damping: 25, stiffness: 300 }} className="absolute bottom-0 left-0 right-0 z-[130] bg-base-200 shadow-[0_-20px_60px_rgba(0,0,0,0.3)] rounded-t-[32px] flex flex-col h-[85vh] max-h-[85vh] overflow-hidden" onClick={e => e.stopPropagation()}>
              <div className="flex items-center justify-between p-4 px-6 relative shrink-0">
                <div className="absolute top-2 left-1/2 -translate-x-1/2 w-12 h-1.5 rounded-full bg-base-content/10" />
                <button onClick={() => setMediaPreviews([])} className="btn btn-ghost btn-sm btn-circle text-base-content/50 hover:text-base-content"><X size={20} /></button>
                <h2 className="text-base-content font-bold text-sm tracking-wide">Preview Media ({mediaPreviews.length})</h2>
                <div className="w-8" />
              </div>
              <div className="flex-1 min-h-0 w-full bg-base-300/30 flex items-center justify-start py-6 px-4 md:px-8 relative overflow-x-auto gap-4 md:gap-5 snap-x custom-scrollbar">
                <div className="absolute inset-0 pattern-dots pattern-base-content pattern-bg-transparent pattern-opacity-5 pattern-size-4 pointer-events-none" />
                {mediaPreviews.map((preview, i) => (
                  <div key={i} className="relative z-10 w-[82vw] max-w-[340px] shrink-0 h-full max-h-[100%] flex items-center justify-center p-2 md:p-3 snap-center bg-base-100 rounded-[24px] shadow-sm border border-base-content/5">
                    {preview.type === "IMAGE" ? (
                      <img src={preview.url} alt="preview" className="max-w-full max-h-full object-contain drop-shadow-md rounded-[16px]" />
                    ) : (
                      <video src={preview.url} controls className="max-w-full max-h-full object-contain drop-shadow-md rounded-[16px] bg-black/5" />
                    )}
                    <button onClick={() => setMediaPreviews(prev => prev.filter((_, idx) => idx !== i))} className="absolute top-3 right-3 md:-top-3 md:-right-2 btn btn-circle btn-sm shadow-xl bg-base-100 hover:bg-error hover:text-white border-base-content/10 text-base-content z-20"><X size={16} /></button>
                  </div>
                ))}
                <div className="w-[8vw] shrink-0" />
              </div>
              <div className="p-4 md:p-6 shrink-0 bg-base-100 flex flex-col gap-3 rounded-t-[32px] relative z-20 shadow-[0_-10px_20px_rgba(0,0,0,0.05)] border-t border-base-content/5">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3 rounded-2xl bg-base-200 border border-base-content/5">
                  <div className="flex items-center justify-between flex-1 cursor-pointer" onClick={() => setMediaPreviews(prev => { const val = !prev.some(p => p.viewOnce); return prev.map(p => ({ ...p, viewOnce: val })); })}>
                    <div className="flex items-center gap-3">
                      <div className={`p-2 rounded-xl transition-colors ${mediaPreviews.some(p => p.viewOnce) ? "bg-warning/20 text-warning" : "bg-base-content/5 text-base-content/40"}`}>
                        {mediaPreviews.some(p => p.viewOnce) ? <EyeOff size={16} /> : <Eye size={16} />}
                      </div>
                      <div>
                        <p className="text-xs font-bold text-base-content mb-0.5">View Once</p>
                        <p className="text-[10px] text-base-content/50 font-medium">{mediaPreviews.some(p => p.viewOnce) ? "Media vanishes after opening" : "Standard permanent view"}</p>
                      </div>
                    </div>
                    <input type="checkbox" className="toggle toggle-primary toggle-sm scale-90 ml-3" checked={mediaPreviews.some(p => p.viewOnce)} readOnly />
                  </div>

                  {mediaPreviews.some(p => p.viewOnce) && (
                    <div className="flex items-center justify-between sm:justify-start gap-2 border-t sm:border-t-0 sm:border-l border-base-content/10 pt-2.5 sm:pt-0 sm:pl-3 shrink-0">
                      <span className="text-[10px] font-black uppercase tracking-wider text-base-content/55 mr-1">Duration</span>
                      <div className="flex gap-1">
                        {([3, 10, 30] as const).map((t) => (
                          <button
                            key={t}
                            onClick={() => setSelectedTimer(t)}
                            className={`btn btn-xs rounded-lg font-black text-[10px] transition-all px-2.5 h-7 min-w-[36px] ${
                              selectedTimer === t
                                ? "bg-[#1D4ED8] text-white hover:bg-[#1e40af] border-none shadow-sm"
                                : "bg-base-300 text-base-content/60 hover:bg-base-300/80 border-none"
                            }`}
                          >
                            {t}s
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                </div>

                <button onClick={handleSendMedia} disabled={isUploading} className="btn bg-[#1D4ED8] hover:bg-[#1e40af] disabled:bg-[#1D4ED8]/50 disabled:text-white/50 text-white w-full h-12 rounded-2xl font-bold text-[14px] shadow-lg shadow-[#1D4ED8]/20 border-none flex items-center justify-center gap-2">
                  {isUploading ? <span className="loading loading-spinner loading-sm" /> : <>Send to Stranger <Send size={16} /></>}
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── Fullscreen Media Lightbox (Portaled to document.body with z-[9999] to sit above navbar) ── */}
      {typeof document !== "undefined" && createPortal(
        <AnimatePresence>
          {fullscreenMedia && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="fixed inset-0 z-[9999] bg-black/85 backdrop-blur-2xl flex flex-col items-center justify-center p-0 md:p-8"
              onClick={() => setFullscreenMedia(null)}
            >
              <WatermarkOverlay username={usernameWatermark} className="text-white opacity-[0.05] z-[10005]" />
              
              {/* Header controls with safe area padding */}
              <div className="absolute top-[max(env(safe-area-inset-top,0px),1rem)] inset-x-4 md:inset-x-8 z-[10010] flex items-center justify-between pointer-events-none">
                <div className="pointer-events-auto flex items-center gap-3">
                  {fullscreenMedia.items.length > 1 && (
                    <span className="text-white text-sm font-bold bg-white/10 px-4 py-2 rounded-2xl backdrop-blur-md shadow-2xl border border-white/20">
                      {fullscreenMedia.index + 1} / {fullscreenMedia.items.length}
                    </span>
                  )}
                </div>

                {/* Zoom controls inside Quick Chat fullscreen media lightbox */}
                <div className="pointer-events-auto flex items-center gap-2">
                  <motion.button
                    whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}
                    onClick={(e) => { e.stopPropagation(); zoomOut(); }}
                    disabled={zoom <= 1}
                    title="Zoom out (−)"
                    className="w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 disabled:opacity-30 flex items-center justify-center text-white text-lg font-bold transition-all border border-white/15 cursor-pointer"
                  >−</motion.button>

                  <button
                    onClick={(e) => { e.stopPropagation(); resetZoom(); }}
                    title="Reset zoom"
                    className="px-3 py-1 rounded-full bg-white/10 hover:bg-white/20 text-white text-xs font-mono transition-all min-w-[52px] text-center border border-white/15 cursor-pointer"
                  >
                    {Math.round(zoom * 100)}%
                  </button>

                  <motion.button
                    whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}
                    onClick={(e) => { e.stopPropagation(); zoomIn(); }}
                    disabled={zoom >= 5}
                    title="Zoom in (+)"
                    className="w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 disabled:opacity-30 flex items-center justify-center text-white text-lg font-bold transition-all border border-white/15 cursor-pointer"
                  >+</motion.button>
                </div>

                <button
                  className="text-white p-3 bg-white/15 hover:bg-white/25 rounded-full backdrop-blur-md shadow-2xl border border-white/20 transition-all pointer-events-auto cursor-pointer"
                  onClick={(e) => { e.stopPropagation(); setFullscreenMedia(null); }}
                  title="Close media viewer"
                >
                  <X size={24} />
                </button>
              </div>

              {/* Main Media */}
              <div 
                className="w-full h-full flex items-center justify-center overflow-hidden relative select-none touch-none p-4 py-20 pb-24 md:p-12"
                onWheel={onWheel}
                onMouseDown={onMouseDown}
                onMouseMove={onMouseMove}
                onMouseUp={onMouseUp}
                onMouseLeave={onMouseUp}
                onTouchStart={onTouchStart}
                onTouchMove={onTouchMove}
                onTouchEnd={onTouchEnd}
                style={{ cursor: zoom > 1 ? (dragging ? "grabbing" : "grab") : "default" }}
              >
                <div
                  style={{
                    transform: `scale(${zoom}) translate(${pan.x / zoom}px, ${pan.y / zoom}px)`,
                    transformOrigin: "center center",
                    transition: dragging ? "none" : "transform 0.18s ease",
                  }}
                  className="flex items-center justify-center w-full h-full"
                >
                  {fullscreenMedia.items[fullscreenMedia.index].type === "IMAGE" || fullscreenMedia.items[fullscreenMedia.index].type === "STICKER" ? (
                    <img src={fullscreenMedia.items[fullscreenMedia.index].url} className="max-w-full max-h-full object-contain rounded-[24px] shadow-2xl" onClick={e => e.stopPropagation()} />
                  ) : (
                    <video src={fullscreenMedia.items[fullscreenMedia.index].url} controls className="max-w-full max-h-full object-contain rounded-[24px] shadow-2xl bg-black/50" onClick={e => e.stopPropagation()} style={{ pointerEvents: zoom > 1 ? "none" : "auto" }} />
                  )}
                </div>
              </div>

              {/* Carousel Buttons */}
              {fullscreenMedia.index > 0 && (
                <button onClick={(e) => { e.stopPropagation(); setFullscreenMedia(prev => ({ ...prev!, index: prev!.index - 1 })) }} className="absolute left-2 md:left-8 top-1/2 -translate-y-1/2 p-3 md:p-4 bg-white/10 hover:bg-white/20 rounded-full text-white backdrop-blur-md shadow-2xl border border-white/20 transition-all z-[10010] cursor-pointer">
                  <ChevronLeft size={32} />
                </button>
              )}

              {fullscreenMedia.index < fullscreenMedia.items.length - 1 && (
                <button onClick={(e) => { e.stopPropagation(); setFullscreenMedia(prev => ({ ...prev!, index: prev!.index + 1 })) }} className="absolute right-2 md:right-8 top-1/2 -translate-y-1/2 p-3 md:p-4 bg-white/10 hover:bg-white/20 rounded-full text-white backdrop-blur-md shadow-2xl border border-white/20 transition-all z-[10010] cursor-pointer">
                  <ChevronRight size={32} />
                </button>
              )}
            </motion.div>
          )}
        </AnimatePresence>,
        document.body
      )}


      <input type="file" ref={fileInputRef} className="hidden" onChange={onFileChange} />

      {/* View once self-destruct security overlay */}
      <AnimatePresence>
        {privateMedia && chat.session && (
          <PrivateMediaViewer 
            msg={privateMedia} 
            sessionId={chat.session.sessionId} 
            username={usernameWatermark}
            onClose={() => setPrivateMedia(null)} 
            deleteMedia={chat.deleteMedia} 
          />
        )}
      </AnimatePresence>

      <LimitReachedModal
        isOpen={showLimitModal}
        onClose={() => setShowLimitModal(false)}
        onUpgrade={() => {}}
        message={limitModalMessage}
      />

      {/* ── Confirmation Modal for Next / Clear / Leave (Portaled to document.body to avoid nested blur compounding) ── */}
      {typeof document !== "undefined" && createPortal(
        <AnimatePresence>
          {confirmAction && (
            <div className="fixed inset-0 z-[9999] flex items-center justify-center p-4">
              {/* Backdrop: clean dark overlay without blur compounding */}
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={() => setConfirmAction(null)}
                className="absolute inset-0 bg-black/60"
              />

              {/* Modal Card */}
              <motion.div
                initial={{ opacity: 0, scale: 0.96, y: 8 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.96, y: 8 }}
                transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
                className="relative w-full max-w-md overflow-hidden bg-base-100 border border-black/10 dark:border-white/10 rounded-2xl shadow-2xl z-10 select-none text-left"
              >
                <div className="p-6 sm:p-7">
                  <h3 className="text-base sm:text-lg font-bold text-base-content tracking-tight">
                    {confirmAction === "next" && "Find Next Match?"}
                    {confirmAction === "clear" && "Clear Messages?"}
                    {confirmAction === "leave" && "Leave This Chat?"}
                  </h3>
                  <p className="text-xs sm:text-sm font-normal text-base-content/65 mt-2 leading-relaxed">
                    {confirmAction === "next" && "Leave this conversation and search for a new stranger."}
                    {confirmAction === "clear" && "Clear all messages from your current screen."}
                    {confirmAction === "leave" && "Disconnect and end this conversation."}
                  </p>
                </div>

                <div className="flex items-center justify-end gap-2.5 px-6 py-4 border-t border-black/5 dark:border-white/5 bg-base-200/40 dark:bg-base-200/20">
                  <button
                    type="button"
                    onClick={() => setConfirmAction(null)}
                    className="px-4 py-2 rounded-xl text-xs font-medium text-base-content/70 hover:text-base-content hover:bg-base-200 dark:hover:bg-white/10 transition-colors cursor-pointer border border-transparent dark:border-white/5"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={async () => {
                      const action = confirmAction;
                      setConfirmAction(null);
                      if (action === "next") {
                        await handleNext();
                      } else if (action === "clear") {
                        chat.clearMessages();
                      } else if (action === "leave") {
                        chat.leaveSession();
                      }
                    }}
                    className={`px-4 py-2 rounded-xl text-xs font-semibold text-white transition-all shadow-xs flex items-center justify-center gap-1.5 cursor-pointer active:scale-98 ${
                      confirmAction === "next"
                        ? "bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 active:bg-[#1e40af]"
                        : confirmAction === "clear"
                        ? "bg-amber-500 hover:bg-amber-600 active:bg-amber-700"
                        : "bg-red-600 hover:bg-red-700 active:bg-red-800"
                    }`}
                  >
                    {confirmAction === "next" && "Next"}
                    {confirmAction === "clear" && "Clear"}
                    {confirmAction === "leave" && "Leave"}
                  </button>
                </div>
              </motion.div>
            </div>
          )}
        </AnimatePresence>,
        document.body
      )}
    </div>
  );


  if (chat.restoring) {
    return (
      <div className="w-full h-full flex flex-col items-center justify-center bg-base-100 gap-4">
        <span className="loading loading-spinner loading-lg text-[#1D4ED8]" />
        <p className="text-[10px] font-black uppercase tracking-[0.4em] text-base-content/30">Syncing Session</p>
      </div>
    );
  }

  return (
    <div
      className={standalone
        ? "w-full flex flex-col bg-base-100"
        : "fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm md:p-4"}
      style={
        standalone
          ? { height: "100%", minHeight: 0 }
          : viewportHeight
          ? { height: `${viewportHeight}px` }
          : undefined
      }
      onClick={handleBackdrop}
    >
      {renderContent()}
    </div>
  );
}

// ── Sub-components ───────────────────────────────────────────────────────────

function IdleScreen({ onStart, theme }: { onStart: () => void; theme: string }) {
  return (
    <div className="flex-1 w-full flex flex-col items-center justify-center p-6 md:p-8 text-center bg-transparent">
      <motion.div initial={{ y: 20, opacity: 0 }} animate={{ y: 0, opacity: 1 }} className="flex items-center justify-center text-[#1D4ED8] dark:text-white mb-5 sm:mb-6 md:mb-8 relative shrink-0">
        <img
          src={theme === "dark" ? QUICK_CHAT_DARK_ICON : QUICK_CHAT_LIGHT_ICON}
          alt=""
          aria-hidden="true"
          draggable={false}
          onContextMenu={(event) => event.preventDefault()}
          className="h-20 w-20 md:h-24 md:w-24 rounded-xl object-contain"
        />
      </motion.div>
      <motion.div initial={{ y: 20, opacity: 0 }} animate={{ y: 0, opacity: 1 }} transition={{ delay: 0.1 }}>
        <h3 className="text-2xl md:text-4xl font-black text-base-content tracking-tighter mb-3 sm:mb-4">Connect with Neighbors.</h3>
        <p className="text-xs sm:text-sm text-base-content/50 max-w-[280px] mx-auto leading-relaxed font-semibold">
          Secure, anonymous, and ephemeral connections with local people in your area.
        </p>
      </motion.div>
      <motion.button initial={{ y: 20, opacity: 0 }} animate={{ y: 0, opacity: 1 }} transition={{ delay: 0.2 }} whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }} onClick={onStart} className="btn bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 text-white w-full max-w-[280px] h-13 sm:h-14 md:h-16 rounded-2xl md:rounded-[24px] font-black text-xs sm:text-sm uppercase tracking-[0.2em] mt-7 sm:mt-8 md:mt-12 shadow-2xl shadow-[#1D4ED8]/20 border-none cursor-pointer">
        Start Chatting
      </motion.button>
      <div className="flex items-center justify-center gap-2 mt-8 sm:mt-12 md:mt-16 text-base-content/30 text-[10px] uppercase font-black tracking-[0.3em]"><IconShield /> <span>Private Relay Active</span></div>
    </div>
  );
}

function SearchingScreen({ queueSize, onCancel }: { queueSize: number | null; onCancel: () => void }) {
  return (
    <div className="flex-1 flex flex-col items-center justify-center p-12 text-center relative overflow-hidden">
      <div className="relative">
        <motion.div animate={{ scale: [1, 1.5, 1], opacity: [0.3, 0.1, 0.3] }} transition={{ duration: 3, repeat: Infinity }} className="absolute inset-[-60px] rounded-full bg-[#1D4ED8]/20 blur-3xl" />
        <div className="w-24 h-24 rounded-full bg-base-300 border border-base-content/5 flex items-center justify-center relative z-10 shadow-2xl">
          <motion.div animate={{ rotate: 360 }} transition={{ duration: 4, repeat: Infinity, ease: "linear" }} className="text-[#1D4ED8]"><Search size={40} /></motion.div>
        </div>
      </div>
      <div className="mt-12 space-y-3">
        <h3 className="text-2xl font-black text-base-content tracking-tight">Finding a match...</h3>
        <p key={queueSize} className="text-sm text-base-content/40 font-bold uppercase tracking-widest">{queueSize != null ? `${queueSize} people discoverying` : "Scanning network..."}</p>
      </div>
      <button onClick={onCancel} className="btn btn-ghost mt-16 px-10 h-12 rounded-xl text-base-content/40 hover:text-base-content font-black text-[10px] uppercase tracking-[0.3em]">Stop Search</button>
    </div>
  );
}

function ErrorScreen({ 
  error, 
  onRetry 
}: { 
  error: string | null; 
  onRetry: () => void; 
}) {
  const isLimitError = isLimitReachedError(error);
  if (isLimitError) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-8 p-8 text-center bg-transparent">
        <div className="w-20 h-20 rounded-[28px] bg-amber-500/10 flex items-center justify-center text-amber-500 shadow-inner border border-amber-500/10 font-sans">
          <AlertTriangle size={40} />
        </div>
        <div>
          <h3 className="text-2xl font-black text-base-content mb-3 tracking-tight">Daily Limit Reached</h3>
          <p className="text-sm text-base-content/60 max-w-[320px] mx-auto leading-relaxed">
            {error ?? "You have reached your daily matchmaking limit."}
          </p>
        </div>
        <button 
          onClick={onRetry} 
          className="btn bg-base-300 hover:bg-base-400 text-base-content h-14 px-12 rounded-2xl font-black text-xs uppercase tracking-[0.2em] border-none transition-all cursor-pointer"
        >
          Retry Connection
        </button>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-8 p-8 text-center bg-transparent">
      <div className="w-20 h-20 rounded-[28px] bg-error/10 flex items-center justify-center text-error shadow-inner border border-error/10"><AlertTriangle size={40} /></div>
      <div>
        <h3 className="text-2xl font-black text-base-content mb-3 tracking-tight">Connection Lost</h3>
        <p className="text-sm text-base-content/40 max-w-[280px] mx-auto leading-relaxed">{error ?? "There was a problem with the chat relay."}</p>
      </div>
      <button onClick={onRetry} className="btn btn-error btn-outline h-14 px-12 rounded-2xl font-black text-xs uppercase tracking-[0.2em] hover:shadow-xl hover:shadow-error/20 transition-all">Retry Link</button>
    </div>
  );
}

function MessageArea({ 
  messages, 
  myId, 
  partnerTyping, 
  onReply, 
  onMediaClick,
  onUnlockPrivateMedia,
  sessionId,
  status
}: { 
  messages: ChatMessageDto[]; 
  myId: string; 
  partnerTyping: boolean; 
  onReply: (r: { messageId: string; senderId: string; content?: string; messageType: MessageType }) => void; 
  onMediaClick: (items: { url: string, type: MessageType }[], index: number) => void;
  onUnlockPrivateMedia: (msg: ChatMessageDto) => void;
  sessionId: string;
  status: ChatStatus;
}) {
  const [contextMenu, setContextMenu] = useState<{
    messageId: string;
    x: number;
    y: number;
    content?: string;
    senderId: string;
    messageType: MessageType;
  } | null>(null);
  
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const messageAreaRef = useRef<HTMLDivElement>(null);
  const isFirstScrollRef = useRef(true);

  // Scroll to bottom when messages or typing status updates
  useEffect(() => {
    if (messageAreaRef.current) {
      messageAreaRef.current.scrollTo({
        top: messageAreaRef.current.scrollHeight,
        behavior: isFirstScrollRef.current ? "auto" : "smooth"
      });
      isFirstScrollRef.current = false;
    }
  }, [messages, partnerTyping]);

  // Scroll to bottom on resize (e.g. when mobile keyboard opens/closes)
  useEffect(() => {
    const container = messageAreaRef.current;
    if (!container) return;

    const observer = new ResizeObserver(() => {
      container.scrollTo({
        top: container.scrollHeight,
        behavior: "smooth"
      });
    });

    observer.observe(container);
    return () => observer.disconnect();
  }, []);

  // Close context menu on click outside or scroll
  useEffect(() => {
    const handleClose = () => setContextMenu(null);
    window.addEventListener("click", handleClose);
    
    const container = messageAreaRef.current;
    container?.addEventListener("scroll", handleClose);
    
    return () => {
      window.removeEventListener("click", handleClose);
      container?.removeEventListener("scroll", handleClose);
    };
  }, []);

  const handleCopyText = async (text: string, id: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 2000);
      showToast.success("Text copied to clipboard!");
    } catch (e) {
      console.error("Failed to copy", e);
      showToast.error("Failed to copy text.");
    }
  };

  const handleOpenMenu = (pos: { clientX: number; clientY: number }, msg: ChatMessageDto) => {
    const rect = messageAreaRef.current?.getBoundingClientRect();
    if (rect) {
      const x = pos.clientX - rect.left;
      const y = pos.clientY - rect.top;
      setContextMenu({
        messageId: msg.messageId,
        x,
        y,
        content: msg.content,
        senderId: msg.senderId,
        messageType: msg.messageType
      });
    }
  };

  const hasUserMessages = useMemo(() => {
    return messages.some(
      (m) =>
        m.senderId !== "SYSTEM" &&
        m.messageType !== "USER_LEFT" &&
        m.messageType !== "SYSTEM"
    );
  }, [messages]);

  // Chunk consecutive media messages sent within 60s
  const groupedMessages = useMemo(() => {
    const groups: ChatMessageDto[][] = [];
    let current: ChatMessageDto[] = [];

    for (const msg of messages) {
      if (current.length === 0) {
        current.push(msg);
        continue;
      }
      const prev = current[current.length - 1];
      const isMedia = (m: ChatMessageDto) => m.messageType === "IMAGE" || m.messageType === "VIDEO";
      const timeDiff = new Date(msg.timestamp).getTime() - new Date(prev.timestamp).getTime();

      if (prev.senderId === msg.senderId && isMedia(prev) && isMedia(msg) && !msg.viewOnce && !prev.viewOnce && !msg.replyToId && timeDiff < 60000) {
        current.push(msg);
      } else {
        groups.push(current);
        current = [msg];
      }
    }
    if (current.length > 0) groups.push(current);
    return groups;
  }, [messages]);

  return (
    <div 
      ref={messageAreaRef} 
      className={`flex-1 min-h-0 h-full w-full overflow-y-auto overflow-x-hidden px-1.5 py-2 md:p-4 scrollbar-hide flex flex-col gap-2 relative ${
        !hasUserMessages ? "justify-center" : ""
      }`}
    >
      {messages.length === 0 && status === "CONNECTED" && (
        <motion.div 
          initial={{ opacity: 0, y: 5 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex justify-center w-full my-4 px-4 select-none"
        >
          <div className="bg-base-300/40 backdrop-blur-md border border-base-content/10 px-5 py-3 rounded-2xl text-center max-w-[85%] shadow-sm">
            <p className="text-xs font-semibold text-base-content/70 leading-relaxed">
              You are now connected. Say hello!
            </p>
          </div>
        </motion.div>
      )}

      {hasUserMessages && <div className="mt-auto" />}

      {groupedMessages.map((group, i) => (
        <Bubble 
          key={group[0].messageId ?? i} 
          msgGroup={group} 
          isMine={group[0].senderId === myId} 
          allMessages={messages} 
          onReply={onReply} 
          onMediaClick={onMediaClick} 
          onUnlockPrivateMedia={onUnlockPrivateMedia}
          sessionId={sessionId}
          onOpenMenu={handleOpenMenu}
        />
      ))}
      {partnerTyping && (
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="flex justify-start px-2 py-2">
          <div className="flex items-center gap-3 px-4 py-3 rounded-[24px] bg-base-300/80 backdrop-blur-md border border-base-content/5 shadow-sm">
            <div className="flex gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#1D4ED8] animate-bounce [animation-duration:0.8s]" />
              <span className="w-1.5 h-1.5 rounded-full bg-[#1D4ED8] animate-bounce [animation-duration:0.8s] [animation-delay:0.2s]" />
              <span className="w-1.5 h-1.5 rounded-full bg-[#1D4ED8] animate-bounce [animation-duration:0.8s] [animation-delay:0.4s]" />
            </div>
            <span className="text-[10px] font-black uppercase tracking-[0.2em] text-base-content/40">Stranger is typing</span>
          </div>
        </motion.div>
      )}
      <div className="h-4 shrink-0" />

      <AnimatePresence>
        {contextMenu && (
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 5 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 5 }}
            transition={{ duration: 0.12 }}
            style={{ 
              position: "absolute",
              left: Math.min(
                Math.max(4, contextMenu.x),
                (messageAreaRef.current?.clientWidth ?? 300) - 176
              ),
              top: (() => {
                const areaH = messageAreaRef.current?.clientHeight ?? 400;
                const menuH = contextMenu.messageType === "TEXT" && contextMenu.content ? 90 : 50;
                // If click is in the bottom 30% of the area, show menu above click point
                return contextMenu.y + menuH > areaH - 20
                  ? Math.max(4, contextMenu.y - menuH - 8)
                  : contextMenu.y + 8;
              })(),
              zIndex: 100
            }}
            onClick={(e) => e.stopPropagation()}
            className="w-40 bg-base-300/90 backdrop-blur-md border border-base-content/10 shadow-2xl rounded-2xl p-1.5 flex flex-col gap-0.5"
          >
            <button
              onClick={() => {
                onReply({
                  messageId: contextMenu.messageId,
                  senderId: contextMenu.senderId,
                  content: contextMenu.content,
                  messageType: contextMenu.messageType
                });
                setContextMenu(null);
              }}
              className="flex items-center gap-2.5 px-3 py-2 rounded-xl text-left text-xs font-bold text-base-content hover:bg-base-content/5 transition-colors cursor-pointer"
            >
              <IconReply />
              <span>Reply</span>
            </button>
            {contextMenu.messageType === "TEXT" && contextMenu.content && (
              <button
                onClick={() => {
                  handleCopyText(contextMenu.content || "", contextMenu.messageId);
                }}
                className="flex items-center gap-2.5 px-3 py-2 rounded-xl text-left text-xs font-bold text-base-content hover:bg-base-content/5 transition-colors cursor-pointer"
              >
                {copiedId === contextMenu.messageId ? (
                  <>
                    <Check size={14} className="text-success" />
                    <span className="text-success">Copied!</span>
                  </>
                ) : (
                  <>
                    <Copy size={14} />
                    <span>Copy Text</span>
                  </>
                )}
              </button>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
// ── Message tick indicator (WhatsApp-style) ───────────────────────────────────
function MessageTicks({
  isMine,
  delivered,
  seen,
}: {
  isMine: boolean;
  delivered?: boolean;
  seen?: boolean;
}) {
  if (!isMine) return null;

  // Two blue ticks — seen by partner
  if (seen) {
    return (
      <span className="shrink-0 flex items-center translate-y-[-1px]" title="Seen">
        <CheckCheck size={12} className="text-sky-300" strokeWidth={2.5} />
      </span>
    );
  }

  // Two gray ticks — delivered to partner's device
  if (delivered) {
    return (
      <span className="shrink-0 flex items-center translate-y-[-1px]" title="Delivered">
        <CheckCheck size={12} className="text-white/50" strokeWidth={2.5} />
      </span>
    );
  }

  // Single gray tick — sent to server (partner not yet received)
  return (
    <span className="shrink-0 flex items-center translate-y-[-1px]" title="Sent">
      <Check size={12} className="text-white/40" strokeWidth={2.5} />
    </span>
  );
}

function Bubble({ 
  msgGroup, 
  isMine, 
  allMessages, 
  onReply, 
  onMediaClick,
  onUnlockPrivateMedia,
  sessionId,
  onOpenMenu
}: { 
  msgGroup: ChatMessageDto[]; 
  isMine: boolean; 
  allMessages: ChatMessageDto[]; 
  onReply: (r: { messageId: string; senderId: string; content?: string; messageType: MessageType }) => void; 
  onMediaClick: (items: { url: string, type: MessageType }[], index: number) => void;
  onUnlockPrivateMedia: (msg: ChatMessageDto) => void;
  sessionId: string;
  onOpenMenu: (pos: { clientX: number; clientY: number }, msg: ChatMessageDto) => void;
}) {
  const msg = msgGroup[msgGroup.length - 1];

  const isSystem = msg.senderId === "SYSTEM" || msg.messageType === "USER_LEFT" || msg.messageType === "SYSTEM";

  if (isSystem) {
    return (
      <div className="flex justify-center w-full my-4 px-4 select-none">
        <div className="bg-base-300/40 backdrop-blur-md border border-base-content/10 px-5 py-3 rounded-2xl text-center max-w-[85%] shadow-sm">
          <p className="text-xs font-semibold text-base-content/70 leading-relaxed">
            {msg.content}
          </p>
        </div>
      </div>
    );
  }

  const time = msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "";
  const repliedMsg = msg.replyToId ? allMessages.find(m => m.messageId === msg.replyToId || (m.messageId.startsWith('local-') && m.messageId === msg.replyToId)) : null;
  const truncate = (s: string, max = 50) => s.length > max ? s.slice(0, max) + "..." : s;

  const isSingleMedia = msgGroup.length === 1 && (msg.messageType === "IMAGE" || msg.messageType === "VIDEO" || msg.messageType === "STICKER");
  const isMultiMedia = msgGroup.length > 1;

  const dragX = useMotionValue(0);
  const [reachedThreshold, setReachedThreshold] = useState(false);

  const iconScale = useTransform(dragX, isMine ? [-60, 0] : [0, 60], isMine ? [1.2, 0.5] : [0.5, 1.2]);
  const iconOpacity = useTransform(dragX, isMine ? [-60, 0] : [0, 60], isMine ? [1, 0] : [0, 1]);

  const handleDrag = (_event: any, info: any) => {
    const x = info.offset.x;
    if (isMine) {
      setReachedThreshold(x < -50);
    } else {
      setReachedThreshold(x > 50);
    }
  };

  const handleDragEnd = (_event: any, info: any) => {
    const x = info.offset.x;
    const triggered = isMine ? x < -50 : x > 50;
    if (triggered) {
      onReply({ messageId: msg.messageId, senderId: msg.senderId, content: msg.content, messageType: msg.messageType });
    }
    setReachedThreshold(false);
    dragX.set(0);
  };

  const handleThreeDotsClick = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    // Anchor menu to the bottom-center of the button, not the raw click point
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    onOpenMenu({ clientX: rect.left + rect.width / 2, clientY: rect.bottom }, msg);
  };

  const handleScrollToMessage = (messageId: string) => {
    const element = document.getElementById(`msg-${messageId}`);
    if (element) {
      element.scrollIntoView({ behavior: "smooth", block: "center" });
      
      // Flash highlight ring effect
      element.classList.add("scale-[1.02]", "shadow-lg", "ring-2", "ring-[#1D4ED8]", "transition-all", "duration-300");
      setTimeout(() => {
        element.classList.remove("scale-[1.02]", "shadow-lg", "ring-2", "ring-[#1D4ED8]");
      }, 1000);
    }
  };

  const renderSingleMedia = () => {
    if (msg.messageType === "STICKER") {
      return (
        <div className="relative">
          <SafeImage 
            mediaPayload={msg.mediaPayload} 
            sessionId={sessionId}
            className="w-24 h-24 sm:w-28 sm:h-28 object-contain drop-shadow-md" 
            alt="Sticker" 
            isSticker={true}
          />
        </div>
      );
    }
    
    if (msg.isWiped) {
      return (
        <div className="relative w-48 sm:w-56 h-14 rounded-xl bg-base-300/40 border border-base-content/5 flex items-center gap-2.5 px-3 select-none pointer-events-none opacity-60">
          <div className="w-7 h-7 rounded-full bg-base-content/5 flex items-center justify-center text-base-content/40 shrink-0">
            <EyeOff size={12} />
          </div>
          <div className="flex flex-col min-w-0">
            <span className="text-[11px] font-bold text-base-content/85 leading-tight truncate">Private Media</span>
            <span className="text-[8px] text-base-content/40 font-bold uppercase tracking-wider leading-none mt-0.5">Opened and vanished</span>
          </div>
        </div>
      );
    }

    if (msg.viewOnce) {
      return (
        <div 
          onClick={(e) => { e.stopPropagation(); onUnlockPrivateMedia(msg); }} 
          className="relative w-48 sm:w-56 py-4 px-3 rounded-[16px] bg-red-500/10 dark:bg-red-400/10 flex flex-col items-center justify-center gap-2 cursor-pointer group/vo border border-red-400/40 dark:border-red-400/30 hover:bg-red-500/15 dark:hover:bg-red-400/15 transition-all duration-300"
        >
          <div className="w-10 h-10 rounded-full bg-red-400/10 dark:bg-red-400/20 flex items-center justify-center text-red-400 dark:text-red-400 shadow-sm transition-transform group-hover/vo:scale-110">
            <EyeOff size={20} className="stroke-[2.5]" />
          </div>
          <div className="text-center">
            <p className="text-[9px] sm:text-[10px] font-black text-red-400 dark:text-red-400 uppercase tracking-[0.2em] leading-tight">Unlock Private Media</p>
            {msg.viewTimer && (
              <p className="text-[7px] sm:text-[8px] text-red-400/70 dark:text-red-400/70 font-bold uppercase tracking-widest mt-0.5">{msg.viewTimer}s self-destruct</p>
            )}
          </div>
        </div>
      );
    }

    return (
      <div className={`relative overflow-hidden rounded-[14px] bg-base-100 flex items-center justify-center ${isMine ? "bg-transparent ring-0 shadow-none border-0" : "shadow-sm ring-1 ring-base-content/5 bg-black/5"}`}>
        {msg.messageType === "IMAGE" ? (
          <SafeImage 
            mediaPayload={msg.mediaPayload} 
            sessionId={sessionId}
            className={`max-w-full max-h-[150px] md:max-h-[200px] w-auto h-auto object-cover cursor-pointer ${isMine ? "rounded-[14px]" : ""}`} 
            onClick={(e: React.MouseEvent<HTMLImageElement>) => { e.stopPropagation(); onMediaClick([{ url: getCachedMediaUrl(msg.mediaPayload!), type: msg.messageType }], 0); }} 
            alt="chat media" 
          />
        ) : (
          <SafeVideo 
            mediaPayload={msg.mediaPayload} 
            sessionId={sessionId}
            controls 
            className="max-w-full max-h-[150px] md:max-h-[200px] w-auto h-auto" 
          />
        )}
      </div>
    );
  };

  const renderMultiMedia = () => {
    return (
      <div className="flex gap-1 overflow-x-auto snap-x max-w-[260px] md:max-w-[320px] custom-scrollbar pb-1 rounded-[14px]">
        {msgGroup.map((m, idx) => (
          <div key={m.messageId} className="shrink-0 w-[140px] md:w-[160px] snap-center rounded-[10px] overflow-hidden bg-black/5 relative aspect-square">
            {m.messageType === "IMAGE" ? (
              <SafeImage 
                mediaPayload={m.mediaPayload} 
                sessionId={sessionId}
                className="w-full h-full object-cover cursor-pointer hover:opacity-90 transition-opacity" 
                onClick={(e: React.MouseEvent<HTMLImageElement>) => { e.stopPropagation(); onMediaClick(msgGroup.map(g => ({ url: getCachedMediaUrl(g.mediaPayload!), type: g.messageType })), idx); }} 
                alt="chat media list" 
              />
            ) : (
              <SafeVideo 
                mediaPayload={m.mediaPayload} 
                sessionId={sessionId}
                controls 
                className="w-full h-full object-cover" 
              />
            )}
          </div>
        ))}
      </div>
    );
  };

  return (
    <div className={`relative flex flex-col ${isMine ? "items-end pr-9 pl-1" : "items-start pl-1 pr-1"} group px-1 mb-1`}>

      <div className="relative max-w-[80%] md:max-w-[65%]">
        {/* Swipe to reply background indicator */}
        {(msg.messageType === "TEXT" || msg.messageType === "STICKER" || msg.messageType === "IMAGE" || msg.messageType === "VIDEO") && (
          <div className={`absolute inset-y-0 flex items-center ${isMine ? "right-2 justify-end" : "left-2 justify-start"} pointer-events-none z-0`}>
            <motion.div
              style={{ scale: iconScale, opacity: iconOpacity }}
              className={`p-1.5 rounded-full transition-colors duration-200 ${
                reachedThreshold
                  ? "bg-[#1D4ED8] text-white"
                  : isMine
                    ? "bg-base-content/10 text-base-content/40"
                    : "bg-[#1D4ED8]/10 text-[#1D4ED8]/40"
              }`}
            >
              <IconReply />
            </motion.div>
          </div>
        )}

        <motion.div
          id={`msg-${msg.messageId}`}
          drag="x"
          dragDirectionLock
          dragConstraints={{ left: 0, right: 0 }}
          dragElastic={{ left: isMine ? 0.5 : 0, right: isMine ? 0 : 0.5 }}
          style={{ x: dragX }}
          onDrag={handleDrag}
          onDragEnd={handleDragEnd}
          className={`overflow-hidden relative z-10 transition-shadow 
            ${msg.messageType === "STICKER"
              ? "bg-transparent shadow-none"
              : isMine
                ? "rounded-2xl rounded-tr-xs bg-[#1D4ED8] text-white shadow-xs"
                : "rounded-2xl rounded-tl-xs bg-white dark:bg-base-200 text-slate-900 dark:text-white border border-slate-200/80 dark:border-base-300 shadow-xs"
            } ${isSingleMedia || isMultiMedia ? "p-0 bg-transparent border-none shadow-none" : "px-3.5 py-1.5"}`}
        >
          {repliedMsg && (
            <div 
              onClick={(e) => { e.stopPropagation(); handleScrollToMessage(repliedMsg.messageId); }}
              className={`mb-2 pl-2 border-l-2 transition-colors cursor-pointer hover:bg-black/5 ${
                isMine ? "border-white/40 bg-white/5 hover:bg-white/10" : "border-[#1D4ED8]/60 bg-[#1D4ED8]/5 hover:bg-[#1D4ED8]/10"
              } py-1.5 pr-2 rounded-r-lg`}
            >
              <p className={`text-[8px] font-black uppercase tracking-[0.1em] mb-0.5 ${isMine ? "text-white/60" : "text-[#1D4ED8]/70"}`}>
                {repliedMsg.senderId === (isMine ? msg.senderId : "STRANGER") ? "You" : "Stranger"}
              </p>
              <div className={`text-[11px] ${isMine ? "text-white/80" : "text-base-content/50"} truncate leading-none notranslate mt-0.5`}>
                {repliedMsg.messageType === "TEXT" ? (
                  repliedMsg.content?.startsWith("/govlyx-emoji/") ? (
                    <span className="inline-flex items-center gap-1">
                      <img src={repliedMsg.content} alt="Emoji" className="w-3.5 h-3.5 object-contain inline-block shrink-0" />
                      <span className="text-[10px]">Govlyx Emoji</span>
                    </span>
                  ) : (
                    truncate(repliedMsg.content || "")
                  )
                ) : (
                  `[${repliedMsg.messageType}]`
                )}
              </div>
            </div>
          )}

          {isMultiMedia ? (
            <>
              {renderMultiMedia()}
              {isMine && (
                <div className={`flex items-center gap-1 justify-end mt-1 pr-0.5`}>
                  <span className="text-[8px] font-bold uppercase tracking-widest text-base-content/40">{time}</span>
                  <MessageTicks isMine={isMine} delivered={msg.delivered} seen={msg.seen} />
                </div>
              )}
            </>
          ) : isSingleMedia ? (
            <>
              {renderSingleMedia()}
              {isMine && (
                <div className={`flex items-center gap-1 justify-end mt-1 pr-0.5`}>
                  <span className="text-[8px] font-bold uppercase tracking-widest text-base-content/40">{time}</span>
                  <MessageTicks isMine={isMine} delivered={msg.delivered} seen={msg.seen} />
                </div>
              )}
            </>
          ) : (
            <div className="flex items-end justify-between gap-3 min-w-[50px] relative">
              <p className="text-[13px] whitespace-pre-wrap break-words leading-[1.4] font-bold tracking-tight flex-1 text-left py-0.5 notranslate">{msg.content}</p>
              <div className="flex items-center gap-1 shrink-0 translate-y-[-2px]">
                <span className={`text-[8px] font-bold uppercase tracking-widest ${isMine ? "text-white/60" : "text-base-content/40"}`}>{time}</span>
                <MessageTicks isMine={isMine} delivered={msg.delivered} seen={msg.seen} />
              </div>
            </div>
          )}
        </motion.div>

        {/* Three dots menu button — sits on top corner of the bubble */}
        {(msg.messageType === "TEXT" || msg.messageType === "STICKER" || msg.messageType === "IMAGE" || msg.messageType === "VIDEO") && (
          <button
            onClick={handleThreeDotsClick}
            className={`absolute top-1 ${isMine ? "left-1" : "right-1"} btn btn-circle btn-xs w-6 h-6 min-h-0 bg-base-300/80 backdrop-blur-md border border-base-content/10 text-base-content/50 hover:text-[#1D4ED8] hover:bg-base-200 transition-all shadow-md z-20 opacity-0 group-hover:opacity-100 cursor-pointer`}
          >
            <MoreVertical size={12} />
          </button>
        )}
      </div>
    </div>
  );
}
