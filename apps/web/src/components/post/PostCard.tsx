import { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { createPortal } from 'react-dom';
import {
  BadgeCheck,
  CheckCircle2,
  Clock,
  MapPin,
  Globe,
  Building2,
  AlertCircle,
  Pencil,
  Trash2,
  ChevronLeft,
  ChevronRight,
  X,
  ImageIcon,
  UserPlus,
  LogOut,
  Play,
  Volume2,
  VolumeX,
  Link,
  Instagram,
  Flag,
  MoreVertical,
  Maximize2,
  MessageCircle,
  EyeOff,
  AlertTriangle,
  Send,
  ThumbsDown,
  Share2,
  Bookmark,
  Crown,
  MessageSquare,
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import CommentSection, { prefetchComments } from './CommentSection';
import type { PostType } from './CommentSection';
import {
  resolveMediaUrl,
  toPostCardPost,
  decodeHTML,
} from '../../utils/postUtils';
import ConfirmModal from './ConfirmModal';
import { useNavigate } from 'react-router-dom';
import { useQueryClient, useQuery } from '@tanstack/react-query';
import { useCurrentUser } from '../../hooks/useUser';
import ReportModal from '../modals/ReportModal';
import UserProfileModal from '../modals/UserProfileModal';
import { checkProfanity } from '../../utils/profanity';
import { showToast } from '../../utils/toast';
import { parseError } from '../../utils/error-handler';
import axiosInstance from '../../api/axiosConfig';
import { translateText } from '../../context/LanguageContext';
import KarmaBadge from '../ui/KarmaBadge';
import MarkdownRenderer from '../ui/MarkdownRenderer';
import LoveBurst from '../ui/LoveBurst';

const POST_ACTION_ACTIVE_CLASS =
  'text-[#1d4ed8] dark:text-white bg-[#1d4ed8]/10 border-transparent';
const POST_ACTION_HOVER_GLOW = 'rgba(29,78,216,0.65)';

const getResolvedSlugsCache = (): Record<number, string> => {
  try {
    return JSON.parse(
      localStorage.getItem('govlyx_resolved_community_slugs') || '{}',
    );
  } catch {
    return {};
  }
};

const saveResolvedSlugToCache = (id: number, slug: string) => {
  try {
    const cache = getResolvedSlugsCache();
    cache[id] = slug;
    localStorage.setItem(
      'govlyx_resolved_community_slugs',
      JSON.stringify(cache),
    );
  } catch {}
};

function PostActionIcon({
  name,
  active = false,
  vertical = false,
}: {
  name: 'like' | 'dislike' | 'comment' | 'share' | 'bookmark' | 'communityChat';
  active?: boolean;
  vertical?: boolean;
}) {
  const size = name === 'bookmark' && vertical ? 24 : vertical ? 16 : 20;

  if (name === 'like') {
    return <LoveBurst active={active} size={size} />;
  }

  if (name === 'communityChat') {
    return (
      <MessageSquare
        size={size}
        fill={active ? 'currentColor' : 'none'}
        className="shrink-0"
      />
    );
  }

  if (name === 'bookmark') {
    if (active) {
      return (
        <svg
          width={size}
          height={size}
          viewBox="0 0 24 24"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          className="shrink-0"
        >
          <path
            d="M19 21l-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"
            fill="#1D4ED8"
            stroke="#1D4ED8"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="m9 10 2 2 4-4"
            stroke="white"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      );
    }
    return <Bookmark size={size} className="shrink-0" />;
  }

  const IconComponent = {
    dislike: ThumbsDown,
    comment: MessageCircle,
    share: Share2,
  }[name as Exclude<typeof name, 'like' | 'bookmark'>];

  return (
    <IconComponent
      size={size}
      fill={active ? 'currentColor' : 'none'}
      className="shrink-0"
    />
  );
}

// ─── API helpers ──────────────────────────────────────────────────────────────
async function apiFetch(
  url: string,
  method: string,
  body?: unknown,
): Promise<unknown> {
  try {
    const res = await axiosInstance({
      url,
      method: method as any,
      data: body,
    });
    return res.data?.data ?? res.data;
  } catch (err: any) {
    const errorMsg =
      err.response?.data?.message || err.response?.data?.error || err.message;
    throw new Error(`${err.response?.status || 500} - ${errorMsg}`);
  }
}

const apiPost = (url: string, body: unknown) => apiFetch(url, 'POST', body);
const apiPut = (url: string, body?: unknown) => apiFetch(url, 'PUT', body);
const apiDelete = (url: string) => apiFetch(url, 'DELETE');

async function recordShare(
  postType: 'posts' | 'social-posts',
  id: number,
  skipApi?: boolean,
  method: string = 'copy',
) {
  if (method === 'copy') {
    const url = `${window.location.origin}/post/${id}?type=${postType}`;
    try {
      await navigator.clipboard.writeText(url);
    } catch {
      window.prompt('Copy link:', url);
    }
  }
  if (!skipApi) {
    const typeStr = method === 'copy' ? 'LINK_COPY' : 'EXTERNAL_SHARE';
    apiPost(
      `/api/interactions/${postType}/${id}/share?shareType=${typeStr}`,
      {},
    ).catch(() => {});
  }
}

function useCopied() {
  const [copied, setCopied] = useState(false);
  function flash() {
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  }
  return { copied, flash };
}

// ─── Types ────────────────────────────────────────────────────────────────────
export type PostVariant =
  | 'issue'
  | 'social'
  | 'community'
  | 'government'
  | 'poll';
export type PostStatus = 'ACTIVE' | 'RESOLVED' | 'DELETED' | 'FLAGGED';
export type BroadcastScope = 'AREA' | 'DISTRICT' | 'STATE' | 'COUNTRY';

export type CurrentUser = {
  id: number;
  role: 'ROLE_USER' | 'ROLE_DEPARTMENT' | 'ROLE_ADMIN';
  taggedUsernames?: string[];
  username: string;
};

type BasePost = {
  id: number;
  content: string;
  // Auto-translate fields returned by the backend
  translatedContent?: string;
  isTranslated?: boolean;
  timeAgo?: string;
  username: string;
  userDisplayName?: string;
  userProfileImage?: string;
  likeCount: number;
  commentCount: number;
  shareCount: number;
  contentHidden?: boolean;
  hiddenReason?: string;
  isViewedByCurrentUser?: boolean;
  karmaScore?: number;
  authorKarmaScore?: number;
  communitySewaScore?: number;
  communityFlair?: string;
  isPendingSync?: boolean;
  category?: string;
  authorRole?: string;
  communityId?: number | null;
  communityName?: string;
  communitySlug?: string;
  communityAvatar?: string;
  isMember?: boolean;
};

export type IssuePost = BasePost & {
  variant: 'issue';
  status: PostStatus;
  broadcastScope?: BroadcastScope;
  broadcastScopeDescription?: string;
  targetPincodes?: string[];
  isResolved: boolean;
  resolvedAt?: string;
  canBeResolved?: boolean;
  dislikeCount: number;
  viewCount: number;
  taggedUsernames: string[];
  imageName?: string;
  hasImage?: boolean;
  isLikedByCurrentUser?: boolean;
  isDislikedByCurrentUser?: boolean;
  isSaved?: boolean;
  isReopened?: boolean;
  reopened?: boolean;
  reopenReason?: string | null;
  reopenedReason?: string | null;
};

export type SocialPost = BasePost & {
  variant: 'social';
  isSaved?: boolean;
  isSavedByCurrentUser?: boolean;
  hashtags?: string[];
  mediaUrls?: string[];
  communityId?: number | null;
  isLikedByCurrentUser?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
  isPoll?: boolean;
  pollId?: number;
};

export type CommunityPost = BasePost & {
  variant: 'community';
  communityId: number;
  communityName: string;
  communityAvatar?: string;
  communityMemberCount?: string;
  isMember?: boolean;
  authorRole?: string;
  isSaved?: boolean;
  isSavedByCurrentUser?: boolean;
  hashtags?: string[];
  mediaUrls?: string[];
  isLikedByCurrentUser?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
};

export type GovernmentPost = BasePost & {
  variant: 'government';
  department: string;
  isSaved?: boolean;
  isSavedByCurrentUser?: boolean;
  broadcastScope?: BroadcastScope;
  broadcastScopeDescription?: string;
  isGovernmentBroadcast: true;
  isLikedByCurrentUser?: boolean;
  isDislikedByCurrentUser?: boolean;
  dislikeCount: number;
};

export type PollOption = {
  id: number;
  optionText: string;
  voteCount: number;
  percentage: number;
};

export type PollPost = BasePost & {
  variant: 'poll';
  pollId: number;
  question: string;
  options: PollOption[];
  totalVotes: number;
  allowMultipleVotes: boolean;
  isExpired: boolean;
  expiresAt?: string;
  timeLeft?: string;
  userHasVoted: boolean;
  votedOptionIds: number[];
  showResults: boolean;
  isSaved?: boolean;
  communityId?: number;
  communityName?: string;
  communityAvatar?: string;
  communityMemberCount?: string;
  isMember?: boolean;
  authorRole?: string;
  isLikedByCurrentUser?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
};

export type AnyPost =
  | IssuePost
  | SocialPost
  | CommunityPost
  | GovernmentPost
  | PollPost;

type PostCardProps = {
  post: AnyPost;
  currentUser?: CurrentUser;
  onLike?: (postId: number, liked: boolean) => void;
  onDislike?: (postId: number, disliked: boolean) => void;
  onSave?: (postId: number, saved: boolean) => void;
  onShare?: (postId: number) => void;
  onComment?: (postId: number) => void;
  onCommentCountChange?: (postId: number, count: number) => void;
  onResolve?: (postId: number, isResolved: boolean, message: string) => void;
  onVote?: (pollId: number, optionIds: number[]) => void;
  onDelete?: (postId: number) => void;
  hideCommunityStrip?: boolean;
  hideDelete?: boolean;
  onShareToCommunity?: (postId: number, content: string) => void;
  onNotInterested?: (postId: number) => void;
  readOnly?: boolean;
  isCommunityOwner?: boolean;
  hideAuthorRoleBadge?: boolean;
};

const postTranslationCache = new Map<string, string>();

interface GlobalInteractionState {
  liked?: boolean;
  likeCount?: number;
  disliked?: boolean;
  dislikeCount?: number;
  saved?: boolean;
  commentCount?: number;
  shareCount?: number;
}
const globalInteractionCache = new Map<string, GlobalInteractionState>();

function getGlobalCacheKey(post: AnyPost): string {
  const isIssue = post.variant === 'issue';
  const isGovt = post.variant === 'government';
  const type = isIssue || isGovt ? 'posts' : 'social-posts';
  return `${type}-${post.id}`;
}

function updateGlobalCache(post: AnyPost, updates: GlobalInteractionState) {
  const key = getGlobalCacheKey(post);
  const current = globalInteractionCache.get(key) || {};
  globalInteractionCache.set(key, { ...current, ...updates });
}

function canUpdateResolution(
  post: IssuePost,
  currentUser?: CurrentUser,
): boolean {
  if (!currentUser) return false;
  if (currentUser.role === 'ROLE_ADMIN') return true;
  if (currentUser.role === 'ROLE_DEPARTMENT')
    return post.taggedUsernames?.includes(currentUser.username) ?? false;
  return false;
}

function commentPostType(variant: PostVariant): PostType {
  return variant === 'issue' || variant === 'government'
    ? 'posts'
    : 'social-posts';
}

function isCommunityPost(post: AnyPost): boolean {
  if (post.variant === 'community') return true;
  if (post.variant === 'poll' && !!(post as PollPost).communityId) return true;
  if (post.variant === 'social' && !!(post as SocialPost).communityId)
    return true;
  return false;
}

function getCommunityId(post: AnyPost): number | null {
  if (post.variant === 'community') return (post as CommunityPost).communityId;
  if (post.variant === 'poll') return (post as PollPost).communityId ?? null;
  if (post.variant === 'social')
    return (post as SocialPost).communityId ?? null;
  return null;
}

// ─── Zoom Viewer (Fullscreen Lightbox with Zoom) ─────────────────────────────
function ZoomViewer({
  mediaUrls,
  startIndex,
  onClose,
}: {
  mediaUrls: string[];
  startIndex: number;
  onClose: () => void;
}) {
  const [index, setIndex] = useState(startIndex);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);
  const dragStart = useRef<{
    mx: number;
    my: number;
    px: number;
    py: number;
  } | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const modalVideoRef = useRef<HTMLVideoElement>(null);

  const isVideo = (url: string) => /\.(mp4|webm|ogg|mov|avi|mkv)$/i.test(url);

  const resetZoom = () => {
    setZoom(1);
    setPan({ x: 0, y: 0 });
  };
  const zoomIn = () => setZoom((z) => Math.min(z + 0.5, 5));
  const zoomOut = () =>
    setZoom((z) => {
      const next = Math.max(z - 0.5, 1);
      if (next === 1) setPan({ x: 0, y: 0 });
      return next;
    });

  const goNext = () => {
    setIndex((i) => (i + 1) % mediaUrls.length);
    resetZoom();
  };
  const goPrev = () => {
    setIndex((i) => (i - 1 + mediaUrls.length) % mediaUrls.length);
    resetZoom();
  };

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
  const onMouseUp = () => {
    setDragging(false);
    dragStart.current = null;
  };

  // Touch pan
  const touchStart = useRef<{
    tx: number;
    ty: number;
    px: number;
    py: number;
  } | null>(null);
  const onTouchStart = (e: React.TouchEvent) => {
    if (zoom <= 1 || e.touches.length !== 1) return;
    touchStart.current = {
      tx: e.touches[0].clientX,
      ty: e.touches[0].clientY,
      px: pan.x,
      py: pan.y,
    };
  };
  const onTouchMove = (e: React.TouchEvent) => {
    if (!touchStart.current || e.touches.length !== 1) return;
    setPan({
      x: touchStart.current.px + (e.touches[0].clientX - touchStart.current.tx),
      y: touchStart.current.py + (e.touches[0].clientY - touchStart.current.ty),
    });
  };
  const onTouchEnd = () => {
    touchStart.current = null;
  };

  // Keyboard support
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      if (e.key === 'ArrowRight') goNext();
      if (e.key === 'ArrowLeft') goPrev();
      if (e.key === '+' || e.key === '=') zoomIn();
      if (e.key === '-') zoomOut();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [index, zoom]);

  useEffect(() => {
    const video = modalVideoRef.current;
    if (!video) return;

    const handleVideoClick = (e: MouseEvent) => {
      const isFullscreen =
        document.fullscreenElement === video ||
        (document as any).webkitFullscreenElement === video ||
        (document as any).mozFullScreenElement === video ||
        (document as any).msFullscreenElement === video;

      if (isFullscreen) {
        const rect = video.getBoundingClientRect();
        const y = e.clientY - rect.top;
        const controlsHeight = 60;
        if (y < rect.height - controlsHeight) {
          e.preventDefault();
          e.stopPropagation();
          if (video.paused) {
            video.play();
          } else {
            video.pause();
          }
        }
      }
    };

    video.addEventListener('click', handleVideoClick);
    return () => {
      video.removeEventListener('click', handleVideoClick);
    };
  }, [index]);

  const current = mediaUrls[index];

  if (typeof document === 'undefined') return null;

  return createPortal(
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-[99999] flex flex-col bg-black/95 backdrop-blur-md"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      {/* Top Bar */}
      <div className="flex items-center justify-between px-5 py-3 shrink-0">
        <span className="text-white/50 text-xs font-mono">
          {mediaUrls.length > 1 ? `${index + 1} / ${mediaUrls.length}` : ''}
        </span>

        {/* Zoom controls */}
        <div className="flex items-center gap-2">
          <motion.button
            whileHover={{ scale: 1.1 }}
            whileTap={{ scale: 0.9 }}
            onClick={zoomOut}
            disabled={zoom <= 1}
            title="Zoom out (−)"
            className="w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 disabled:opacity-30 flex items-center justify-center text-white text-lg font-bold transition-all"
          >
            −
          </motion.button>

          <button
            onClick={resetZoom}
            title="Reset zoom"
            className="px-3 py-1 rounded-full bg-white/10 hover:bg-white/20 text-white text-xs font-mono transition-all min-w-[52px] text-center"
          >
            {Math.round(zoom * 100)}%
          </button>

          <motion.button
            whileHover={{ scale: 1.1 }}
            whileTap={{ scale: 0.9 }}
            onClick={zoomIn}
            disabled={zoom >= 5}
            title="Zoom in (+)"
            className="w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 disabled:opacity-30 flex items-center justify-center text-white text-lg font-bold transition-all"
          >
            +
          </motion.button>
        </div>

        <motion.button
          whileHover={{ scale: 1.1 }}
          whileTap={{ scale: 0.9 }}
          onClick={onClose}
          className="w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center text-white transition-all cursor-pointer"
        >
          <X size={18} />
        </motion.button>
      </div>

      {/* Media area */}
      <div
        ref={containerRef}
        className="flex-1 flex items-center justify-center overflow-hidden relative select-none"
        onWheel={onWheel}
        onMouseDown={onMouseDown}
        onMouseMove={onMouseMove}
        onMouseUp={onMouseUp}
        onMouseLeave={onMouseUp}
        onTouchStart={onTouchStart}
        onTouchMove={onTouchMove}
        onTouchEnd={onTouchEnd}
        style={{
          cursor: zoom > 1 ? (dragging ? 'grabbing' : 'grab') : 'default',
        }}
      >
        <AnimatePresence mode="wait">
          <motion.div
            key={index}
            initial={{ opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.96 }}
            transition={{ duration: 0.25 }}
            className="max-h-[85vh] max-w-[95vw] flex items-center justify-center"
          >
            <div
              style={{
                transform: `scale(${zoom}) translate(${pan.x / zoom}px, ${pan.y / zoom}px)`,
                transformOrigin: 'center center',
                transition: dragging ? 'none' : 'transform 0.18s ease',
              }}
              className="flex items-center justify-center w-full h-full"
            >
              {isVideo(current) ? (
                <div className="relative max-h-[85vh] max-w-[95vw] rounded-xl overflow-hidden shadow-2xl flex items-center justify-center">
                  <video
                    ref={modalVideoRef}
                    src={current}
                    controls
                    autoPlay
                    className="max-h-[85vh] max-w-[95vw]"
                    style={{ pointerEvents: zoom > 1 ? 'none' : 'auto' }}
                  />
                  {zoom <= 1 && (
                    <div
                      onClick={(e) => {
                        e.stopPropagation();
                        const video = modalVideoRef.current;
                        if (video) {
                          if (video.paused) {
                            video.play();
                          } else {
                            video.pause();
                          }
                        }
                      }}
                      className="absolute inset-x-0 top-0 bottom-[55px] cursor-pointer z-10"
                    />
                  )}
                </div>
              ) : (
                <img
                  src={current}
                  alt={`Media ${index + 1}`}
                  draggable={false}
                  className="max-h-[85vh] max-w-[95vw] object-contain rounded-xl shadow-2xl"
                />
              )}
            </div>
          </motion.div>
        </AnimatePresence>

        {/* Prev / Next navigation */}
        {mediaUrls.length > 1 && (
          <>
            <motion.button
              whileHover={{ scale: 1.1, x: -3 }}
              whileTap={{ scale: 0.9 }}
              onClick={(e) => {
                e.stopPropagation();
                goPrev();
              }}
              className="absolute left-4 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-white/15 backdrop-blur-md hover:bg-white/25 flex items-center justify-center text-white shadow-lg z-10 cursor-pointer"
            >
              <ChevronLeft size={22} />
            </motion.button>
            <motion.button
              whileHover={{ scale: 1.1, x: 3 }}
              whileTap={{ scale: 0.9 }}
              onClick={(e) => {
                e.stopPropagation();
                goNext();
              }}
              className="absolute right-4 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-white/15 backdrop-blur-md hover:bg-white/25 flex items-center justify-center text-white shadow-lg z-10 cursor-pointer"
            >
              <ChevronRight size={22} />
            </motion.button>
          </>
        )}
      </div>

      {/* Dot indicators */}
      {mediaUrls.length > 1 && (
        <div className="flex justify-center gap-2 pb-4 shrink-0">
          {mediaUrls.map((_, i) => (
            <button
              key={i}
              onClick={() => {
                setIndex(i);
                resetZoom();
              }}
              className={`rounded-full transition-all duration-300 ${i === index ? 'w-6 h-2 bg-white' : 'w-2 h-2 bg-white/30 hover:bg-white/60'}`}
            />
          ))}
        </div>
      )}
    </motion.div>,
    document.body,
  );
}

// ─── Modern Carousel Component ───────────────────────────────────────────────
function ModernMediaCarousel({
  mediaUrls,
  onExpand,
}: {
  mediaUrls: string[];
  onExpand: (index: number) => void;
}) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [imgError, setImgError] = useState<Record<number, boolean>>({});
  const [isPlaying, setIsPlaying] = useState(false);
  const [isMuted, setIsMuted] = useState(true);
  const videoRef = useRef<HTMLVideoElement>(null);

  const isVideoUrl = (url: string) =>
    /\.(mp4|webm|ogg|mov|avi|mkv)$/i.test(url);
  const currentMedia = mediaUrls[activeIndex];
  const isCurrentVideo = isVideoUrl(currentMedia);

  const handlePrev = (e?: React.MouseEvent) => {
    e?.stopPropagation();
    setActiveIndex((i) => (i === 0 ? mediaUrls.length - 1 : i - 1));
  };

  const handleNext = (e?: React.MouseEvent) => {
    e?.stopPropagation();
    setActiveIndex((i) => (i === mediaUrls.length - 1 ? 0 : i + 1));
  };

  const togglePlay = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (videoRef.current) {
      if (videoRef.current.paused) {
        videoRef.current.play();
      } else {
        videoRef.current.pause();
      }
    }
  };

  useEffect(() => {
    if (!isCurrentVideo) setIsPlaying(false);
  }, [activeIndex, isCurrentVideo]);

  useEffect(() => {
    if (videoRef.current) {
      videoRef.current.muted = isMuted;
    }
  }, [isMuted, activeIndex]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const handleVideoClick = (e: MouseEvent) => {
      const isFullscreen =
        document.fullscreenElement === video ||
        (document as any).webkitFullscreenElement === video ||
        (document as any).mozFullScreenElement === video ||
        (document as any).msFullscreenElement === video;

      if (isFullscreen) {
        const rect = video.getBoundingClientRect();
        const y = e.clientY - rect.top;
        const controlsHeight = 60;
        if (y < rect.height - controlsHeight) {
          e.preventDefault();
          e.stopPropagation();
          if (video.paused) {
            video.play();
          } else {
            video.pause();
          }
        }
      }
    };

    video.addEventListener('click', handleVideoClick);
    return () => {
      video.removeEventListener('click', handleVideoClick);
    };
  }, [activeIndex]);

  return (
    <div className="relative w-full overflow-hidden rounded-2xl bg-base-300 shadow-inner group">
      <motion.div
        className="relative h-[290px] sm:h-80 w-full bg-black/50 flex items-center justify-center cursor-pointer"
        onClick={isCurrentVideo ? togglePlay : () => onExpand(activeIndex)}
        transition={{ duration: 0.3 }}
      >
        <AnimatePresence mode="wait">
          {!imgError[activeIndex] ? (
            isCurrentVideo ? (
              <motion.div
                key={`video-container-${activeIndex}`}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.3 }}
                className="relative h-full w-full flex items-center justify-center"
              >
                <video
                  ref={videoRef}
                  src={currentMedia}
                  controls={isPlaying}
                  muted={isMuted}
                  loop
                  playsInline
                  className="h-full w-full object-contain"
                  onClick={togglePlay}
                  onError={() =>
                    setImgError((prev) => ({ ...prev, [activeIndex]: true }))
                  }
                  onPlay={() => setIsPlaying(true)}
                  onPause={() => setIsPlaying(false)}
                />
                {isPlaying && (
                  <div
                    onClick={togglePlay}
                    className="absolute inset-x-0 top-0 bottom-[55px] cursor-pointer z-10"
                  />
                )}
              </motion.div>
            ) : (
              <motion.img
                key={`img-${activeIndex}`}
                initial={{ opacity: 0, scale: 0.98 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.98 }}
                transition={{ duration: 0.3 }}
                src={currentMedia}
                alt={`Media ${activeIndex + 1}`}
                className="h-full w-full object-cover"
                onError={() =>
                  setImgError((prev) => ({ ...prev, [activeIndex]: true }))
                }
              />
            )
          ) : (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="flex flex-col items-center justify-center h-full w-full bg-slate-800/50"
            >
              <div className="w-12 h-12 rounded-full bg-slate-700/50 flex items-center justify-center mb-2">
                <ImageIcon size={24} className="stroke-slate-400" />
              </div>
              <p className="text-xs font-medium text-slate-400 px-6 text-center">
                Legacy media unavailable
              </p>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Play Icon Overlay for Videos */}
        <AnimatePresence>
          {isCurrentVideo && !isPlaying && (
            <motion.div
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.8 }}
              className="absolute inset-0 flex items-center justify-center z-10 pointer-events-none"
            >
              <div className="w-16 h-16 rounded-full bg-white/20 backdrop-blur-md flex items-center justify-center border border-white/30 shadow-2xl">
                <Play size={32} className="text-white fill-white ml-1" />
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Gradient overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none" />
      </motion.div>

      {/* Navigation arrows */}
      {mediaUrls.length > 1 && (
        <>
          <motion.button
            onClick={handlePrev}
            whileHover={{ scale: 1.1, x: -4 }}
            whileTap={{ scale: 0.95 }}
            className="absolute left-3 top-1/2 -translate-y-1/2 w-10 h-10 flex items-center justify-center rounded-full bg-white/20 backdrop-blur-md hover:bg-white/30 transition-all z-20 text-white shadow-lg"
          >
            <ChevronLeft size={18} />
          </motion.button>
          <motion.button
            onClick={handleNext}
            whileHover={{ scale: 1.1, x: 4 }}
            whileTap={{ scale: 0.95 }}
            className="absolute right-3 top-1/2 -translate-y-1/2 w-10 h-10 flex items-center justify-center rounded-full bg-white/20 backdrop-blur-md hover:bg-white/30 transition-all z-20 text-white shadow-lg"
          >
            <ChevronRight size={18} />
          </motion.button>
        </>
      )}

      {/* Indicator dots */}
      {mediaUrls.length > 1 && (
        <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-2 z-20">
          {mediaUrls.map((_, i) => (
            <motion.button
              key={i}
              onClick={(e) => {
                e.stopPropagation();
                setActiveIndex(i);
              }}
              whileTap={{ scale: 0.8 }}
              className={`rounded-full transition-all duration-300 ${
                i === activeIndex
                  ? 'w-6 h-2 bg-white shadow-lg'
                  : 'w-2 h-2 bg-white/50 hover:bg-white/80'
              }`}
            />
          ))}
        </div>
      )}

      {/* Counter, Video controls & Zoom button */}
      <div className="absolute top-3 right-3 flex items-center gap-2 z-20">
        {isCurrentVideo && (
          <motion.button
            onClick={(e) => {
              e.stopPropagation();
              setIsMuted(!isMuted);
            }}
            whileHover={{ scale: 1.1 }}
            whileTap={{ scale: 0.95 }}
            className="w-8 h-8 rounded-full bg-white/20 backdrop-blur-md hover:bg-white/30 flex items-center justify-center text-white transition-all"
          >
            {isMuted ? <VolumeX size={16} /> : <Volume2 size={16} />}
          </motion.button>
        )}
        {mediaUrls.length > 1 && (
          <div className="rounded-full bg-black/40 backdrop-blur-sm px-3 py-1.5 text-xs text-white font-semibold font-mono">
            {activeIndex + 1}/{mediaUrls.length}
          </div>
        )}
        {/* Zoom / fullscreen button */}
        <motion.button
          onClick={(e) => {
            e.stopPropagation();
            onExpand(activeIndex);
          }}
          whileHover={{ scale: 1.15 }}
          whileTap={{ scale: 0.9 }}
          title="View fullscreen"
          className="w-8 h-8 rounded-full bg-white/20 backdrop-blur-md hover:bg-white/35 flex items-center justify-center text-white transition-all opacity-0 group-hover:opacity-100"
        >
          <Maximize2 size={15} />
        </motion.button>
      </div>
    </div>
  );
}

// ─── Community Strip (Minimalist & Clean) ───────────────────────────────────
function CommunityStrip({
  post,
  isJoined,
  onJoin,
  isAdminOrOwner,
}: {
  post: AnyPost;
  isJoined: boolean;
  onJoin: (cid: number) => void;
  isAdminOrOwner?: boolean;
}) {
  const navigate = useNavigate();
  const communityId = getCommunityId(post);
  const communityName =
    (post as CommunityPost).communityName ||
    (post as PollPost).communityName ||
    'Community';
  const memberCount =
    (post as CommunityPost).communityMemberCount ||
    (post as PollPost).communityMemberCount;

  return (
    <div className="flex items-center justify-between gap-2 mb-1 -mt-1 pb-1">
      <button
        type="button"
        onClick={async (e) => {
          e.stopPropagation();
          if (communityId) {
            let targetSlug = (post as any).communitySlug || (post as any).slug;
            if (!targetSlug) {
              try {
                const resData = (await apiFetch(
                  `/api/communities/search?q=${encodeURIComponent(communityName)}`,
                  'GET',
                )) as any;
                const list =
                  resData?.data?.content ??
                  resData?.data?.data ??
                  resData?.data ??
                  resData?.content ??
                  [];
                if (Array.isArray(list)) {
                  const match = list.find((c: any) => c.id === communityId);
                  if (match && match.slug) {
                    targetSlug = match.slug;
                  }
                }
              } catch (err) {
                console.error('Failed to fetch community slug:', err);
              }
            }
            navigate(`/communities?community=${targetSlug || communityId}`);
          }
        }}
        className="inline-flex items-center gap-1.5 px-3.5 py-1.5 sm:py-2 rounded-full bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 text-white shadow-xs transition-all duration-200 group cursor-pointer text-left min-w-0"
      >
        <span className="text-xs text-white truncate max-w-[220px] sm:max-w-[320px]">
          <span className="text-white/80 font-medium">Post from </span>
          <span className="font-bold">{communityName}</span>
        </span>
        {memberCount && (
          <span className="text-[11px] text-white/80 font-medium shrink-0">
            • {memberCount} {Number(memberCount) === 1 ? 'member' : 'members'}
          </span>
        )}
      </button>

      {communityId &&
        !isAdminOrOwner &&
        (isJoined ? (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onJoin(communityId);
            }}
            className="btn btn-xs rounded-full px-3 h-7 text-[10px] font-bold bg-base-200/80 hover:bg-red-500/10 hover:text-red-600 text-base-content border border-slate-300 dark:border-slate-600/70 hover:border-red-400 dark:hover:border-red-500/60 shrink-0 transition-all active:scale-95 shadow-xs cursor-pointer"
          >
            <LogOut size={11} className="mr-0.5" />
            Leave
          </button>
        ) : (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onJoin(communityId);
            }}
            className="btn btn-xs rounded-full px-3 h-7 text-[10px] font-bold bg-base-200/80 hover:bg-base-300 text-base-content border border-slate-300 dark:border-slate-600/70 hover:border-slate-400 dark:hover:border-slate-500 shrink-0 transition-all active:scale-95 shadow-xs cursor-pointer"
          >
            <UserPlus size={11} className="mr-0.5" />
            Join
          </button>
        ))}
    </div>
  );
}

/** Renders a role badge (Admin / Mod) next to the author's name for community posts */
function AuthorRoleBadge({ role }: { role?: string | null }) {
  if (!role) return null;

  const normalized = role.toUpperCase();
  const isAdmin = normalized === 'ADMIN' || normalized === 'OWNER';

  if (!isAdmin) return null;

  return (
    <span
      className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md text-[8px] font-black uppercase tracking-tighter leading-none shrink-0 border shadow-xs bg-amber-500/15 text-amber-700 dark:text-amber-400 border-amber-500/30"
      title="Community Admin"
    >
      <Crown size={10} className="shrink-0 text-amber-500" />
      <span>Admin</span>
    </span>
  );
}

function AuthorRow({
  post,
  badge,
  onDelete,
  isDeleting,
  showDelete,
  hideDelete,
  rightAction,
  onProfileClick,
  hideAuthorRoleBadge,
}: {
  post: AnyPost;
  badge?: string;
  onDelete?: () => void;
  isDeleting?: boolean;
  showDelete?: boolean;
  hideDelete?: boolean;
  rightAction?: React.ReactNode;
  onProfileClick?: (username: string) => void;
  hideAuthorRoleBadge?: boolean;
}) {
  const navigate = useNavigate();

  const karmaScore = post.karmaScore ?? post.authorKarmaScore;
  const sewaScore = post.communitySewaScore ?? 0;
  const communityFlair =
    post.communityFlair ||
    (sewaScore >= 1000
      ? 'Pramukh'
      : sewaScore >= 500
        ? 'Margdarshak'
        : sewaScore >= 150
          ? 'Rakshak'
          : '');

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="flex items-center gap-3 w-full min-w-0"
    >
      <motion.div
        className="relative shrink-0 cursor-pointer avatar"
        onClick={() => {
          if (post.username && onProfileClick) {
            onProfileClick(post.username);
          } else if (post.username) {
            navigate(`/profile?username=${encodeURIComponent(post.username)}`);
          }
        }}
      >
        <div className="w-10 h-10 rounded-full overflow-hidden transition-all ring-2 ring-primary/10 ring-offset-2 ring-offset-base-100">
          {post.userProfileImage ? (
            <img
              src={post.userProfileImage}
              className="w-full h-full object-cover"
              alt=""
            />
          ) : (
            <img
              src={`https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(
                post.username || '?',
              )}`}
              className="w-full h-full object-cover bg-base-200"
              alt="Avatar"
            />
          )}
        </div>
      </motion.div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 min-w-0 flex-wrap">
          <span
            className="min-w-0 max-w-full text-xs font-black text-base-content uppercase tracking-tight notranslate truncate cursor-pointer hover:underline"
            onClick={() => {
              if (post.username && onProfileClick) {
                onProfileClick(post.username);
              } else if (post.username) {
                navigate(
                  `/profile?username=${encodeURIComponent(post.username)}`,
                );
              }
            }}
          >
            {post.userDisplayName || post.username}
          </span>
          {!hideAuthorRoleBadge && post.authorRole && (
            <AuthorRoleBadge role={post.authorRole} />
          )}
          {!hideAuthorRoleBadge &&
            badge &&
            (!post.authorRole ||
              badge.toUpperCase() !== post.authorRole.toUpperCase()) && (
              <motion.span
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                className="text-[8px] font-medium uppercase tracking-tighter px-2 py-0.5 rounded-full bg-[#1D4ED8] text-white border border-transparent shrink-0"
              >
                {badge}
              </motion.span>
            )}
          {typeof karmaScore === 'number' && (
            <KarmaBadge score={karmaScore} compact />
          )}
          {communityFlair && (
            <span className="shrink-0 rounded-full border border-amber-500/25 dark:border-amber-400/30 bg-amber-500/10 dark:bg-amber-400/10 px-2 py-0.5 text-[8px] font-black uppercase tracking-tighter text-amber-700 dark:text-amber-400">
              {communityFlair}
            </span>
          )}
        </div>
        <p className="text-[10px] text-base-content/50 mt-0.5 font-bold uppercase tracking-tighter flex items-center gap-1.5 flex-wrap">
          <span>{post.timeAgo ?? 'just now'}</span>
          {post.isPendingSync && (
            <span className="inline-flex items-center gap-1 text-[9px] text-[#1D4ED8] dark:text-blue-400 font-bold uppercase tracking-wider bg-blue-500/10 px-2 py-0.5 rounded-full border border-blue-500/20 animate-pulse">
              <span className="loading loading-spinner loading-xs text-[#1D4ED8]" />
              Wait, uploading...
            </span>
          )}
        </p>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        {rightAction}
        {showDelete && !hideDelete && onDelete && (
          <motion.button
            onClick={(e) => {
              e.stopPropagation();
              onDelete();
            }}
            disabled={isDeleting}
            whileHover={{ scale: 1.12, y: -1 }}
            whileTap={{ scale: 0.94 }}
            className="group/del relative flex h-9 w-9 items-center justify-center rounded-xl border border-transparent bg-base-300/40 text-base-content/40 transition-all duration-300 hover:border-red-500/30 hover:bg-red-500/5 hover:text-red-600 hover:shadow-lg hover:shadow-red-500/10 backdrop-blur-md disabled:opacity-30"
            title="Delete post"
          >
            <div className="absolute inset-0 rounded-xl bg-red-500/0 transition-all duration-300 group-hover/del:bg-red-500/5" />
            {isDeleting ? (
              <span className="loading loading-spinner loading-xs" />
            ) : (
              <Trash2
                size={16}
                className="relative z-10 transition-transform duration-300 group-hover/del:rotate-6"
              />
            )}
          </motion.button>
        )}
      </div>
    </motion.div>
  );
}

// ─── Status Badge ───────────────────────────────────────────────────────────
function StatusBadge({
  status,
  reopened,
}: {
  status: PostStatus;
  reopened?: boolean;
}) {
  if (status === 'RESOLVED')
    return (
      <span className="inline-flex items-center gap-1 rounded-lg bg-blue-500/10 px-3 py-1.5 text-xs font-semibold text-blue-600 dark:text-blue-400 border border-blue-500/20 shadow-[0_0_8px_rgba(59,130,246,0.1)]">
        <CheckCircle2 size={13} /> Resolved
      </span>
    );
  if (reopened)
    return (
      <span className="inline-flex items-center gap-1 rounded-lg bg-red-500/10 px-3 py-1.5 text-xs font-semibold text-red-600 dark:text-red-400 border border-red-500/20 shadow-[0_0_8px_rgba(239,68,68,0.1)] animate-pulse">
        <AlertCircle size={13} className="text-red-500" /> Reopened
      </span>
    );
  if (status === 'ACTIVE')
    return (
      <span className="inline-flex items-center gap-1 rounded-lg bg-emerald-500/10 px-3 py-1.5 text-xs font-semibold text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 shadow-[0_0_8px_rgba(16,185,129,0.1)]">
        <Clock size={13} /> Active
      </span>
    );
  return null;
}

// ─── Resolve Modal ───────────────────────────────────────────────────────────
function ResolveModal({
  isOpen,
  onClose,
  onConfirm,
}: {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (message: string) => void;
}) {
  const [msg, setMsg] = useState('');

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{ scale: 0.95, y: 10, opacity: 0 }}
            animate={{ scale: 1, y: 0, opacity: 1 }}
            exit={{ scale: 0.95, y: 10, opacity: 0 }}
            className="w-full max-w-sm rounded-2xl border border-black/10 dark:border-white/15 bg-base-100 p-6 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <motion.h3
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              className="mb-2 flex items-center gap-2 text-lg font-bold text-base-content"
            >
              <CheckCircle2 size={20} className="text-emerald-600" />
              Mark Issue Resolved
            </motion.h3>
            <motion.p
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.1 }}
              className="mb-4 text-sm text-base-content/70"
            >
              Provide an update message for the citizen who raised this issue.
            </motion.p>
            <motion.textarea
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.15 }}
              className="w-full p-3 rounded-lg border border-base-300 bg-base-100 focus:border-blue-500 focus:ring-2 focus:ring-blue-200 resize-none text-sm font-medium placeholder-base-content/40 transition-all"
              rows={4}
              placeholder="e.g. Road repair completed on 15 Jan 2025…"
              value={msg}
              onChange={(e) => setMsg(e.target.value)}
            />
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.2 }}
              className="mt-5 flex justify-end gap-3"
            >
              <motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={onClose}
                className="px-4 py-2 rounded-lg font-semibold text-slate-700 hover:bg-slate-100 transition-colors"
              >
                Cancel
              </motion.button>
              <motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                disabled={!msg.trim()}
                onClick={() => onConfirm(msg.trim())}
                className="px-4 py-2 rounded-lg font-semibold text-white bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-lg shadow-emerald-600/30"
              >
                Confirm
              </motion.button>
            </motion.div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  );
}

// ─── Reopen Modal ───────────────────────────────────────────────────────────
function ReopenModal({
  isOpen,
  onClose,
  onConfirm,
}: {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState('');

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{ scale: 0.95, y: 10, opacity: 0 }}
            animate={{ scale: 1, y: 0, opacity: 1 }}
            exit={{ scale: 0.95, y: 10, opacity: 0 }}
            className="w-full max-w-sm rounded-2xl border border-black/10 dark:border-white/15 bg-base-100 p-6 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <motion.h3
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              className="mb-2 flex items-center gap-2 text-lg font-bold text-base-content"
            >
              <AlertCircle size={20} className="text-red-600 animate-pulse" />
              Reopen Resolved Issue
            </motion.h3>
            <motion.p
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.1 }}
              className="mb-4 text-sm text-base-content/70 font-medium"
            >
              Are you sure you want to reopen this issue? Please provide a
              reason.
            </motion.p>
            <motion.textarea
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.15 }}
              className="w-full p-3 rounded-lg border border-base-300 bg-base-100 focus:border-red-500 focus:ring-2 focus:ring-red-200 resize-none text-sm font-medium placeholder-base-content/40 transition-all text-base-content"
              rows={4}
              placeholder="Provide a reason why the resolution is insufficient…"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.2 }}
              className="mt-5 flex justify-end gap-3"
            >
              <motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={onClose}
                className="px-4 py-2 rounded-lg font-semibold text-slate-700 hover:bg-slate-100 transition-colors"
              >
                Cancel
              </motion.button>
              <motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                disabled={!reason.trim()}
                onClick={() => onConfirm(reason.trim())}
                className="px-4 py-2 rounded-lg font-semibold text-white bg-red-600 hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-lg shadow-red-600/30"
              >
                Reopen
              </motion.button>
            </motion.div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  );
}

// ─── Share Modal ─────────────────────────────────────────────────────────────
function ShareModal({
  isOpen,
  onClose,
  post,
  onShareAction,
  hasCommunityShare,
  onShareToCommunity,
}: {
  isOpen: boolean;
  onClose: () => void;
  post: AnyPost;
  onShareAction: (method: string) => void;
  hasCommunityShare?: boolean;
  onShareToCommunity?: () => void;
}) {
  const isIssue = post.variant === 'issue';
  const isGovt = post.variant === 'government';
  const postType = isIssue || isGovt ? 'posts' : 'social-posts';
  const url = `${window.location.origin}/post/${post.id}?type=${postType}`;
  const rawText = post.content || '';
  const chars = Array.from(rawText);
  const shortened =
    chars.length > 50 ? chars.slice(0, 50).join('') + '...' : rawText;
  let text = '';
  try {
    text = encodeURIComponent(
      `Check out this post on Govlyx:\n"${shortened}"\n\n`,
    );
  } catch {
    text = encodeURIComponent(`Check out this post on Govlyx!\n\n`);
  }

  const handleCopy = () => {
    onShareAction('copy');
    onClose();
  };

  const handleWhatsApp = () => {
    window.open(
      `https://wa.me/?text=${text}${encodeURIComponent(url)}`,
      '_blank',
    );
    onShareAction('whatsapp');
    onClose();
  };

  const handleTwitter = () => {
    window.open(
      `https://twitter.com/intent/tweet?text=${text}&url=${encodeURIComponent(url)}`,
      '_blank',
    );
    onShareAction('twitter');
    onClose();
  };

  const handleInstagram = () => {
    navigator.clipboard.writeText(url).catch(() => {});
    showToast.success('Link copied! Opening Instagram...');
    window.open('https://instagram.com', '_blank');
    onShareAction('instagram');
    onClose();
  };

  const handleTelegram = () => {
    window.open(
      `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${text}`,
      '_blank',
    );
    onShareAction('telegram');
    onClose();
  };

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-[200] flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{ scale: 0.95, y: 10, opacity: 0 }}
            animate={{ scale: 1, y: 0, opacity: 1 }}
            exit={{ scale: 0.95, y: 10, opacity: 0 }}
            className="w-full max-w-xs rounded-3xl border border-black/10 dark:border-white/15 bg-base-100 p-6 shadow-2xl space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-center text-lg font-bold text-base-content w-full">
                Share Post via
              </h3>
            </div>
            <div className="flex flex-wrap justify-center gap-5 pt-2">
              {/* Copy */}
              <button
                onClick={handleCopy}
                className="flex flex-col items-center w-14 gap-2 group"
              >
                <div className="w-14 h-14 rounded-full bg-slate-100 dark:bg-white/10 flex items-center justify-center group-hover:scale-105 transition-all shadow-sm shrink-0 aspect-square">
                  <Link size={24} className="text-slate-600 dark:text-white" />
                </div>
                <span className="text-[11px] font-semibold text-center text-base-content/80">
                  Copy
                </span>
              </button>

              {/* WhatsApp */}
              <button
                onClick={handleWhatsApp}
                className="flex flex-col items-center w-14 gap-2 group"
              >
                <div className="w-14 h-14 rounded-full bg-[#25D366] flex items-center justify-center group-hover:scale-105 transition-all shadow-sm shrink-0 aspect-square">
                  <svg viewBox="0 0 24 24" className="w-6 h-6 fill-white">
                    <path d="M.057 24l1.687-6.163c-1.041-1.804-1.588-3.849-1.587-5.946.003-6.556 5.338-11.891 11.893-11.891 3.181 0 6.167 1.24 8.413 3.488 2.245 2.248 3.481 5.236 3.48 8.414-.003 6.557-5.338 11.892-11.893 11.892-1.99 0-3.951-.5-5.688-1.448l-6.305 1.654zm6.597-3.807c1.676.995 3.276 1.591 5.392 1.591 5.52 0 10.002-4.48 10.002-10.002 0-5.522-4.482-10.002-10.002-10.002-5.521 0-10.002 4.48-10.002 10.002 0 2.223.731 4.303 1.99 6.001l-1.34 4.895 5.96-1.565zm10.73-7.502c-.29-.145-1.722-.85-1.988-.947-.266-.097-.46-.145-.654.145-.194.29-.752.947-.922 1.14-.17.194-.34.218-.63.073-.29-.145-1.226-.452-2.336-1.442-.865-.772-1.449-1.725-1.618-2.015-.17-.29-.018-.447.127-.591.132-.132.294-.345.441-.518.147-.173.196-.29.294-.485.098-.195.049-.364-.024-.509-.074-.145-.654-1.577-.897-2.16-.24-.582-.486-.503-.654-.513-.17-.009-.364-.01-.558-.01-.194 0-.51.073-.777.364-.266.291-1.018.995-1.018 2.428s1.042 2.81 1.188 3.004c.145.195 2.05 3.125 4.966 4.383 2.44 1.052 2.44.701 2.88.654.44-.047 1.411-.577 1.606-1.134.195-.557.195-1.034.137-1.131-.059-.096-.217-.145-.508-.29z" />
                  </svg>
                </div>
                <span className="text-[11px] font-semibold text-center text-base-content/80">
                  WhatsApp
                </span>
              </button>

              {/* Telegram */}
              <button
                onClick={handleTelegram}
                className="flex flex-col items-center w-14 gap-2 group"
              >
                <div className="w-14 h-14 rounded-full bg-[#0088cc] flex items-center justify-center group-hover:scale-105 transition-all shadow-sm shrink-0 aspect-square">
                  <Send size={22} className="text-white ml-0.5" />
                </div>
                <span className="text-[11px] font-semibold text-center text-base-content/80">
                  Telegram
                </span>
              </button>

              {/* Twitter / X */}
              <button
                onClick={handleTwitter}
                className="flex flex-col items-center w-14 gap-2 group"
              >
                <div className="w-14 h-14 rounded-full bg-black flex items-center justify-center group-hover:scale-105 transition-all shadow-sm shrink-0 aspect-square">
                  <svg viewBox="0 0 24 24" className="w-5 h-5 fill-white">
                    <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
                  </svg>
                </div>
                <span className="text-[11px] font-semibold text-center text-base-content/80">
                  X
                </span>
              </button>

              {/* Instagram */}
              <button
                onClick={handleInstagram}
                className="flex flex-col items-center w-14 gap-2 group"
              >
                <div className="w-14 h-14 rounded-full bg-gradient-to-tr from-[#f09433] via-[#dc2743] to-[#bc1888] flex items-center justify-center group-hover:scale-105 transition-all shadow-sm shrink-0 aspect-square">
                  <Instagram size={24} className="text-white" />
                </div>
                <span className="text-[11px] font-semibold text-center text-base-content/80">
                  Insta
                </span>
              </button>
            </div>

            {hasCommunityShare && onShareToCommunity && (
              <button
                type="button"
                onClick={() => {
                  onShareToCommunity();
                  onClose();
                }}
                className="flex items-center gap-3 w-full p-4 rounded-2xl bg-[#1D4ED8] hover:bg-blue-800 text-white font-extrabold text-xs uppercase tracking-wider transition-all duration-200 shadow-lg shadow-blue-600/20 active:scale-98 cursor-pointer justify-center"
              >
                <MessageSquare size={16} />
                <span>Share to Community</span>
              </button>
            )}

            <button
              onClick={onClose}
              className={`${hasCommunityShare ? 'mt-2' : 'mt-6'} w-full py-2.5 rounded-2xl bg-base-200/80 text-xs font-bold opacity-80 hover:bg-base-300 transition-colors`}
            >
              Cancel
            </button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  );
}

function ActionPill({
  onClick,
  onHover,
  active = false,
  disabled = false,
  children,
  vertical = false,
  activeClass = 'bg-primary/10 border-primary/20 text-primary dark:text-white',
  hoverGlow = 'rgba(99,102,241,0.5)',
  className = '',
}: {
  onClick: () => void;
  onHover?: () => void;
  active?: boolean;
  disabled?: boolean;
  children: React.ReactNode;
  vertical?: boolean;
  activeClass?: string;
  hoverGlow?: string;
  className?: string;
}) {
  const handleClick = () => {
    if (disabled) return;
    onClick();
  };

  return (
    <button
      onClick={handleClick}
      disabled={disabled}
      style={{
        position: 'relative',
        overflow: 'visible',
      }}
      className={`flex items-center gap-2 sm:gap-2.5 rounded-2xl transition-colors duration-200 disabled:opacity-30 select-none border border-transparent ${
        vertical
          ? 'p-2.5 sm:p-3 flex-col min-w-[50px] sm:min-w-[58px]'
          : 'px-3 sm:px-4 py-1.5 sm:py-2.5'
      } text-[9px] sm:text-[10px] font-black uppercase tracking-tighter group/pill ${
        active
          ? `${activeClass} shadow-sm`
          : 'text-base-content/70 dark:text-white bg-transparent hover:bg-base-200/50'
      } ${className}`}
      onMouseEnter={(e) => {
        onHover?.();
        if (!disabled) {
          (e.currentTarget as HTMLElement).style.boxShadow =
            `0 0 0 1.5px ${hoverGlow}, 0 0 12px 2px ${hoverGlow}55`;
          (e.currentTarget as HTMLElement).style.borderColor = hoverGlow;
        }
      }}
      onTouchStart={() => {
        onHover?.();
      }}
      onMouseLeave={(e) => {
        (e.currentTarget as HTMLElement).style.boxShadow = '';
        (e.currentTarget as HTMLElement).style.borderColor = '';
      }}
    >
      {children}
    </button>
  );
}

// ─── Poll Body ───────────────────────────────────────────────────────────────
function PollBody({
  post,
  onVote,
}: {
  post: PollPost;
  onVote?: (pollId: number, ids: number[]) => void;
  isProcessing?: boolean;
}) {
  const [votedIds, setVotedIds] = useState<number[]>(
    post?.votedOptionIds || [],
  );
  const debounceTimerRef = useRef<any>(null);
  const inFlightCountRef = useRef<number>(0);
  const activeRequestPromiseRef = useRef<Promise<any> | null>(null);
  const nextPendingVoteRef = useRef<number[] | null>(null);
  const prevPollIdRef = useRef<number>(post?.pollId);

  useEffect(() => {
    // If pollId changed, reset completely
    if (prevPollIdRef.current !== post?.pollId) {
      prevPollIdRef.current = post?.pollId;
      setVotedIds(post?.votedOptionIds || []);
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
        debounceTimerRef.current = null;
      }
      activeRequestPromiseRef.current = null;
      nextPendingVoteRef.current = null;
      return;
    }

    // Otherwise, only sync from props if we are not actively debouncing and there are no in-flight or pending requests
    if (
      !debounceTimerRef.current &&
      inFlightCountRef.current === 0 &&
      !activeRequestPromiseRef.current &&
      !nextPendingVoteRef.current
    ) {
      setVotedIds(post?.votedOptionIds || []);
    }
  }, [post?.votedOptionIds, post?.pollId]);

  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, []);

  if (
    !post ||
    !post.options ||
    !Array.isArray(post.options) ||
    post.options.length === 0
  ) {
    return null;
  }

  const showResults =
    post.showResults ||
    post.userHasVoted ||
    post.isExpired ||
    votedIds.length > 0;

  const sendVoteSequentially = async (ids: number[]) => {
    if (activeRequestPromiseRef.current) {
      nextPendingVoteRef.current = ids;
      return;
    }

    inFlightCountRef.current += 1;
    const currentPromise = onVote?.(post.pollId, ids);
    activeRequestPromiseRef.current = Promise.resolve(currentPromise);

    try {
      await activeRequestPromiseRef.current;
    } finally {
      activeRequestPromiseRef.current = null;
      inFlightCountRef.current = Math.max(0, inFlightCountRef.current - 1);

      if (nextPendingVoteRef.current) {
        const nextIds = nextPendingVoteRef.current;
        nextPendingVoteRef.current = null;
        sendVoteSequentially(nextIds);
      } else {
        if (inFlightCountRef.current === 0 && !debounceTimerRef.current) {
          setVotedIds(post?.votedOptionIds || []);
        }
      }
    }
  };

  const handleVote = (optionId: number) => {
    if (post.isExpired) return;

    let next: number[];
    if (votedIds.includes(optionId)) {
      // Un-vote: remove the selected option
      next = votedIds.filter((id) => id !== optionId);
    } else if (post.allowMultipleVotes) {
      // Multi-choice: add the new option
      next = [...votedIds, optionId];
    } else {
      // Single-choice: replace with the new option (change vote)
      next = [optionId];
    }

    setVotedIds(next);

    // Debounce the call to onVote
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    debounceTimerRef.current = setTimeout(() => {
      debounceTimerRef.current = null;
      sendVoteSequentially(next);
    }, 500);
  };

  // ─── Optimistic Updates ──────────────────────────────────────────
  const { displayedOptions, displayedTotalVotes } = useMemo(() => {
    const adjustedOptions = post.options.map((opt) => {
      const wasServerVoted = (post.votedOptionIds || []).includes(opt.id);
      const isLocallyVoted = votedIds.includes(opt.id);

      let count = opt.voteCount || 0;
      if (isLocallyVoted && !wasServerVoted) {
        count += 1;
      } else if (!isLocallyVoted && wasServerVoted) {
        count = Math.max(0, count - 1);
      }
      return { ...opt, voteCount: count };
    });

    const total = adjustedOptions.reduce((sum, opt) => sum + opt.voteCount, 0);

    const mappedOptions = adjustedOptions.map((opt) => {
      const pct = total > 0 ? (opt.voteCount / total) * 100 : 0;
      return { ...opt, percentage: pct };
    });

    return { displayedOptions: mappedOptions, displayedTotalVotes: total };
  }, [post.options, post.votedOptionIds, votedIds]);
  // ────────────────────────────────────────────────────────────────

  return (
    <div className="space-y-2 w-full mt-2">
      <div className="space-y-2">
        {displayedOptions.map((opt) => {
          const isSelected = votedIds.includes(opt.id);
          const canClick = !post.isExpired;
          return (
            <motion.div
              key={opt.id}
              onClick={() => canClick && handleVote(opt.id)}
              whileHover={canClick ? { y: -1 } : undefined}
              whileTap={canClick ? { scale: 0.98 } : undefined}
              className={`relative overflow-hidden rounded-lg border transition-all ${
                canClick ? 'cursor-pointer' : 'cursor-default'
              } ${isSelected ? 'border-blue-500/50 shadow-sm shadow-blue-500/10' : 'border-base-content/10'}`}
            >
              {/* Progress */}
              <motion.div
                initial={false}
                animate={{ width: showResults ? `${opt.percentage}%` : '0%' }}
                transition={{ type: 'spring', stiffness: 80, damping: 20 }}
                className={`absolute left-0 top-0 h-full transition-colors duration-300 ${
                  isSelected ? 'bg-blue-500/10' : 'bg-base-content/5'
                }`}
              />

              {/* Content */}
              <div className="relative z-10 flex items-center justify-between px-3.5 py-2.5 text-sm">
                <div className="flex items-center gap-3">
                  {/* Selection Indicator */}
                  <div
                    className={`w-4 h-4 rounded-full border flex items-center justify-center transition-all ${
                      isSelected
                        ? 'border-blue-500 bg-blue-500'
                        : 'border-base-content/20'
                    }`}
                  >
                    {isSelected && (
                      <div className="w-1.5 h-1.5 rounded-full bg-white" />
                    )}
                  </div>

                  <span
                    className={`font-semibold ${isSelected ? 'text-blue-400' : 'text-base-content/80 text-[13px]'}`}
                  >
                    {opt.optionText}
                  </span>
                </div>
                {showResults && (
                  <span className="font-bold opacity-60 text-xs text-base-content">
                    {Math.round(opt.percentage)}%
                  </span>
                )}
              </div>
            </motion.div>
          );
        })}
      </div>

      {/* Meta */}
      <div className="mt-3 flex items-center gap-4 text-[10px] font-bold uppercase tracking-widest opacity-60 px-1 text-base-content">
        <span>
          {displayedTotalVotes.toLocaleString()}{' '}
          {displayedTotalVotes === 1 ? 'vote' : 'votes'}
        </span>
        <span className="flex items-center gap-1.5">
          <Clock size={12} />
          {post.isExpired
            ? 'Ended'
            : post.timeLeft || (!post.expiresAt ? 'Always' : 'Active')}
        </span>
      </div>
    </div>
  );
}

// Module-level set to prevent concurrent duplicate view requests in the same browser session
const sessionTrackedViews = new Set<string>();

export default function PostCard({
  post,
  currentUser,
  onLike,
  onDislike,
  onSave,
  onShare,
  onCommentCountChange,
  onResolve,
  onVote,
  onDelete,
  hideCommunityStrip,
  hideDelete,
  onShareToCommunity,
  onNotInterested,
  readOnly,
  isCommunityOwner,
  hideAuthorRoleBadge,
}: PostCardProps) {
  const isTakedown =
    (post as any)?.isCopyrightRemoved || (post as any)?.status === 'TAKEDOWN';

  if (isTakedown) {
    return (
      <div className="rounded-[2rem] border-2 border-slate-200 dark:border-slate-800 p-6 flex flex-col items-center justify-center text-center gap-3 bg-slate-900/10 dark:bg-slate-900/40 backdrop-blur-md w-full">
        <AlertTriangle className="w-8 h-8 text-slate-450" />
        <h3 className="text-sm font-bold text-slate-700 dark:text-slate-300">
          Content Unavailable
        </h3>
        <p className="text-xs text-slate-450 max-w-sm leading-relaxed">
          This content is unavailable in your region due to a legal complaint or
          copyright infringement claim.
        </p>
      </div>
    );
  }

  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: currentUserProfile } = useCurrentUser();

  const { data: myCommunities } = useQuery<any[]>({
    queryKey: ['my-communities'],
    queryFn: async () => {
      const [joinedRes, ownedRes] = await Promise.allSettled([
        axiosInstance.get('/api/communities/me?limit=100'),
        axiosInstance.get('/api/communities/owned'),
      ]);

      let joined: any[] = [];
      if (joinedRes.status === 'fulfilled' && joinedRes.value.status === 200) {
        const j = joinedRes.value.data;
        const rawJoined =
          j?.data?.content ?? j?.data?.data ?? j?.content ?? j?.data ?? [];
        joined = Array.isArray(rawJoined) ? rawJoined : [];
      }

      let owned: any[] = [];
      if (ownedRes.status === 'fulfilled' && ownedRes.value.status === 200) {
        const o = ownedRes.value.data;
        const rawOwned =
          o?.data?.content ?? o?.data?.data ?? o?.content ?? o?.data ?? [];
        const rawOwnedList = Array.isArray(rawOwned) ? rawOwned : [];
        owned = rawOwnedList.map((c: any) => ({
          ...c,
          isOwner: true,
          role: 'OWNER',
          currentUserRole: 'OWNER',
        }));
      }

      let localDeletions: Record<string, any> = {};
      try {
        localDeletions = JSON.parse(
          localStorage.getItem('govlyx_deleted_communities') || '{}',
        );
      } catch {}

      const seen = new Set<number>();
      const merged: any[] = [];
      for (const c of [...owned, ...joined]) {
        const isDeleted =
          c?.isDeleted === true ||
          !!c?.deletedAt ||
          !!c?.scheduledDeletionDate ||
          !!c?.deletionDueDate ||
          !!localDeletions[c?.id];
        if (c?.id && !seen.has(c.id) && !isDeleted) {
          seen.add(c.id);
          merged.push(c);
        }
      }
      return merged;
    },
    enabled: !!currentUserProfile,
  });

  useEffect(() => {
    if (myCommunities && myCommunities.length > 0) {
      const cache = getResolvedSlugsCache();
      let changed = false;
      for (const c of myCommunities) {
        if (c.id && c.slug && cache[c.id] !== c.slug) {
          cache[c.id] = c.slug;
          changed = true;
        }
      }
      if (changed) {
        try {
          localStorage.setItem(
            'govlyx_resolved_community_slugs',
            JSON.stringify(cache),
          );
        } catch {}
      }
    }
  }, [myCommunities]);

  const cachedState = post
    ? globalInteractionCache.get(getGlobalCacheKey(post))
    : undefined;

  const [liked, setLiked] = useState(
    cachedState?.liked !== undefined
      ? cachedState.liked
      : !!(post as AnyPost)?.isLikedByCurrentUser,
  );
  const [disliked, setDisliked] = useState(
    cachedState?.disliked !== undefined
      ? cachedState.disliked
      : !!(post as any)?.isDislikedByCurrentUser,
  );
  const [saved, setSaved] = useState(
    cachedState?.saved !== undefined
      ? cachedState.saved
      : !!(
          (post as any).isSavedByCurrentUser ??
          (post as any).isSaved ??
          false
        ),
  );
  const [likeCount, setLikeCount] = useState<number>(
    cachedState?.likeCount !== undefined
      ? cachedState.likeCount
      : (post?.likeCount ?? 0),
  );
  const [dislikeCount, setDislikeCount] = useState<number>(
    cachedState?.dislikeCount !== undefined
      ? cachedState.dislikeCount
      : ((post as any)?.dislikeCount ?? 0),
  );
  const [shareCount, setShareCount] = useState(post?.shareCount ?? 0);
  const [commentCount, setCommentCount] = useState(
    cachedState?.commentCount !== undefined
      ? cachedState.commentCount
      : (post?.commentCount ?? 0),
  );
  const [contentOverride, setContentOverride] = useState(post?.content ?? '');
  const [hasSharedLocally, setHasSharedLocally] = useState(() => {
    if (!post) return false;
    const isIssue = post.variant === 'issue';
    const isGovt = post.variant === 'government';
    const type = isIssue || isGovt ? 'posts' : 'social-posts';
    const userId =
      currentUserProfile?.id ||
      currentUser?.id ||
      currentUserProfile?.username ||
      currentUser?.username ||
      'guest';
    const key = `govlyx_shared_${userId}_${type}_${post.id}`;
    try {
      return localStorage.getItem(key) === 'true';
    } catch {
      return false;
    }
  });
  const [resolveOpen, setResolveOpen] = useState(false);
  const [reopenOpen, setReopenOpen] = useState(false);
  const [shareMenuOpen, setShareMenuOpen] = useState(false);
  const [moreMenuOpen, setMoreMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!moreMenuOpen) return;
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMoreMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [moreMenuOpen]);

  const [resolving, setResolving] = useState(false);
  const [reopening, setReopening] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editContent, setEditContent] = useState(post?.content ?? '');
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const [confirmNotInterestedOpen, setConfirmNotInterestedOpen] =
    useState(false);
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const [showJoinConfirm, setShowJoinConfirm] = useState(false);
  const [pendingJoinCommunityId, setPendingJoinCommunityId] = useState<
    number | null
  >(null);
  const [showJoinToShareModal, setShowJoinToShareModal] = useState(false);
  const [isJoiningCommunity, setIsJoiningCommunity] = useState(false);
  const [isJoined, setIsJoined] = useState(() => {
    if (!currentUserProfile) return false;
    return (post as any).isMember ?? (post as any).isJoined ?? false;
  });
  const [commentsOpen, setCommentsOpen] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [canExpand, setCanExpand] = useState(false);
  const textRef = useRef<HTMLParagraphElement>(null);
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [lightboxIndex, setLightboxIndex] = useState(0);
  const [isProcessing, setIsProcessing] = useState(false);
  const [isContentRevealed, setIsContentRevealed] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [locallyHidden, setLocallyHidden] = useState(false);
  // Translation toggle — when true shows the original untranslated text
  const [showOriginal, setShowOriginal] = useState(false);
  const [dynamicTranslation, setDynamicTranslation] = useState<string | null>(
    null,
  );
  const [isTranslating, setIsTranslating] = useState(false);
  const [profileModalOpen, setProfileModalOpen] = useState(false);
  const [profileModalUsername, setProfileModalUsername] = useState('');
  const [profileModalDisplayName, setProfileModalDisplayName] = useState('');
  const [profileModalAvatar, setProfileModalAvatar] = useState<string | null>(
    null,
  );
  const { copied, flash } = useCopied();
  const instanceId = useRef(Math.random().toString()).current;
  const cleanEmailHelper = (val: string) =>
    val && val.includes('@') ? val.split('@')[0] : val || '';
  const userUsername = currentUserProfile?.username || currentUser?.username;
  const userActualUsername =
    currentUserProfile?.actualUsername || (currentUser as any)?.actualUsername;
  const postUsername =
    post.username || (post as any)?.authorUsername || (post as any)?.author?.username;
  const currentUserId = currentUserProfile?.id || currentUser?.id;

  const isAuthor = !!(
    (post as any)?.isMyPost ||
    (post as any)?.isPostOwner ||
    (post as any)?.canDelete ||
    (post as any)?.canEdit ||
    (postUsername &&
      userUsername &&
      (postUsername.toLowerCase() === userUsername.toLowerCase() ||
        postUsername.toLowerCase() ===
          cleanEmailHelper(userUsername).toLowerCase())) ||
    (postUsername &&
      userActualUsername &&
      (postUsername.toLowerCase() === userActualUsername.toLowerCase() ||
        postUsername.toLowerCase() ===
          cleanEmailHelper(userActualUsername).toLowerCase())) ||
    (currentUserId &&
      ((post as any)?.userId === currentUserId ||
        (post as any)?.author?.id === currentUserId ||
        (post as any)?.user?.id === currentUserId))
  );

  useEffect(() => {
    setContentOverride(post?.content ?? '');
    setEditContent(post?.content ?? '');
  }, [post?.id, post?.content]);

  const handleTranslateDynamic = async () => {
    const preferredLang = currentUserProfile?.preferredLanguage || 'en';
    const cacheKey = `${post.variant}_${post.id}_${preferredLang}`;

    if (dynamicTranslation) {
      setShowOriginal((v) => !v);
      return;
    }

    if (postTranslationCache.has(cacheKey)) {
      setDynamicTranslation(postTranslationCache.get(cacheKey)!);
      setShowOriginal(false);
      return;
    }

    setIsTranslating(true);
    try {
      const translatedText = await translateText(
        contentOverride || '',
        preferredLang,
      );
      if (translatedText) {
        postTranslationCache.set(cacheKey, translatedText);
        setDynamicTranslation(translatedText);
        setShowOriginal(false);
      } else {
        throw new Error('Translation returned empty result');
      }
    } catch (err: any) {
      console.error('Translation failed', err);
      showToast.error('Failed to translate post. Please try again.');
    } finally {
      setIsTranslating(false);
    }
  };

  // Synced backend states to resolve optimistic updates on network errors / debouncing
  const syncedLikedRef = useRef(
    cachedState?.liked !== undefined
      ? cachedState.liked
      : !!(post as AnyPost)?.isLikedByCurrentUser,
  );
  const syncedLikeCountRef = useRef(
    cachedState?.likeCount !== undefined
      ? cachedState.likeCount
      : (post?.likeCount ?? 0),
  );
  const syncedDislikedRef = useRef(
    cachedState?.disliked !== undefined
      ? cachedState.disliked
      : !!(post as any)?.isDislikedByCurrentUser,
  );
  const syncedDislikeCountRef = useRef(
    cachedState?.dislikeCount !== undefined
      ? cachedState.dislikeCount
      : ((post as any)?.dislikeCount ?? 0),
  );
  const syncedSavedRef = useRef(
    cachedState?.saved !== undefined
      ? cachedState.saved
      : !!(
          (post as any).isSavedByCurrentUser ??
          (post as any).isSaved ??
          false
        ),
  );

  // Store callbacks in ref to avoid event listener churn when callbacks are unstable
  const callbacksRef = useRef({
    onLike,
    onDislike,
    onSave,
    onShare,
    onCommentCountChange,
  });
  useEffect(() => {
    callbacksRef.current = {
      onLike,
      onDislike,
      onSave,
      onShare,
      onCommentCountChange,
    };
  }, [onLike, onDislike, onSave, onShare, onCommentCountChange]);

  // Debounce timers
  const pendingLikeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(
    null,
  );
  const pendingDislikeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(
    null,
  );
  const pendingSaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(
    null,
  );

  // Ref to prevent duplicate simultaneous share clicks
  const isSharingRef = useRef(false);

  // Active sync flags to handle consecutive clicks when request is in flight
  const isLikeDislikeSyncingRef = useRef(false);
  const hasPendingLikeDislikeSyncRef = useRef(false);
  const isSaveSyncingRef = useRef(false);
  const hasPendingSaveSyncRef = useRef(false);

  // Ref tracking visual state for debounce closure safety
  const currentLikedValueRef = useRef(liked);
  const currentDislikedValueRef = useRef(disliked);
  const currentSavedValueRef = useRef(saved);
  const currentLikeCountValueRef = useRef(likeCount);
  const currentDislikeCountValueRef = useRef(dislikeCount);

  useEffect(() => {
    currentLikedValueRef.current = liked;
  }, [liked]);

  useEffect(() => {
    currentDislikedValueRef.current = disliked;
  }, [disliked]);

  useEffect(() => {
    currentSavedValueRef.current = saved;
  }, [saved]);

  useEffect(() => {
    return () => {
      if (pendingLikeTimerRef.current)
        clearTimeout(pendingLikeTimerRef.current);
      if (pendingDislikeTimerRef.current)
        clearTimeout(pendingDislikeTimerRef.current);
      if (pendingSaveTimerRef.current)
        clearTimeout(pendingSaveTimerRef.current);
    };
  }, []);

  useEffect(() => {
    if (post) {
      const cached = globalInteractionCache.get(getGlobalCacheKey(post));
      const isLiked =
        cached?.liked !== undefined
          ? cached.liked
          : !!(post as AnyPost)?.isLikedByCurrentUser;
      const isSaved =
        cached?.saved !== undefined
          ? cached.saved
          : !!(
              (post as any).isSavedByCurrentUser ??
              (post as any).isSaved ??
              false
            );
      const isDisliked =
        cached?.disliked !== undefined
          ? cached.disliked
          : post.variant === 'issue'
            ? !!(post as any).isDislikedByCurrentUser
            : false;
      const pLikeCount =
        cached?.likeCount !== undefined
          ? cached.likeCount
          : (post.likeCount ?? 0);
      const pDislikeCount =
        cached?.dislikeCount !== undefined
          ? cached.dislikeCount
          : post.variant === 'issue'
            ? ((post as any).dislikeCount ?? 0)
            : 0;

      // Only sync if there is no pending optimistic interaction and no active API call syncing
      if (!pendingLikeTimerRef.current && !isLikeDislikeSyncingRef.current) {
        setLiked(isLiked);
        syncedLikedRef.current = isLiked;
        currentLikedValueRef.current = isLiked;
        setLikeCount(pLikeCount);
        syncedLikeCountRef.current = pLikeCount;
        currentLikeCountValueRef.current = pLikeCount;
      }
      if (!pendingDislikeTimerRef.current && !isLikeDislikeSyncingRef.current) {
        if (post.variant === 'issue') {
          setDislikeCount(pDislikeCount);
          syncedDislikeCountRef.current = pDislikeCount;
          currentDislikeCountValueRef.current = pDislikeCount;
        }
        if (post.variant === 'issue') {
          setDisliked(isDisliked);
          syncedDislikedRef.current = isDisliked;
          currentDislikedValueRef.current = isDisliked;
        }
      }
      if (!pendingSaveTimerRef.current && !isSaveSyncingRef.current) {
        setSaved(isSaved);
        syncedSavedRef.current = isSaved;
        currentSavedValueRef.current = isSaved;
      }

      setShareCount(post.shareCount ?? 0);
      setIsJoined(
        currentUserProfile
          ? ((post as any).isMember ?? (post as any).isJoined ?? false)
          : false,
      );
      // Prefer cached (freshly-fetched) comment count so we never regress to stale feed data
      const freshCache = globalInteractionCache.get(getGlobalCacheKey(post));
      setCommentCount(
        freshCache?.commentCount !== undefined
          ? freshCache.commentCount
          : (post.commentCount ?? 0),
      );
      setIsContentRevealed(false);
      setDynamicTranslation(null);
      setShowOriginal(false);

      const isIssue = post.variant === 'issue';
      const isGovt = post.variant === 'government';
      const type = isIssue || isGovt ? 'posts' : 'social-posts';
      const userId =
        currentUserProfile?.id ||
        currentUser?.id ||
        currentUserProfile?.username ||
        currentUser?.username ||
        'guest';
      const key = `govlyx_shared_${userId}_${type}_${post.id}`;
      try {
        setHasSharedLocally(localStorage.getItem(key) === 'true');
      } catch {
        setHasSharedLocally(false);
      }
    }
  }, [post, currentUserProfile, currentUser]);

  useEffect(() => {
    if (post?.id) {
      const viewKey = `${post.variant}-${post.id}`;
      // Skip view recording if post was already viewed by the current user or already tracked in this session
      if (post.isViewedByCurrentUser || sessionTrackedViews.has(viewKey)) {
        return;
      }

      // Mark as tracked immediately to block concurrent mounts from repeating the request
      sessionTrackedViews.add(viewKey);

      const isIssue = post.variant === 'issue';
      const isGovt = post.variant === 'government';
      const type: 'posts' | 'social-posts' =
        isIssue || isGovt ? 'posts' : 'social-posts';
      apiPost(`/api/interactions/${type}/${post.id}/view`, {}).catch(() => {});
    }
  }, [post?.id, post?.variant, post?.isViewedByCurrentUser]);

  useEffect(() => {
    const handlePostSync = (e: any) => {
      if (e.detail.postId !== post?.id) return;
      if (e.detail.emitterId === instanceId) return;
      if (e.detail.source === 'like') {
        setLiked(e.detail.liked);
        syncedLikedRef.current = e.detail.liked;
        currentLikedValueRef.current = e.detail.liked;
        if (e.detail.likeCount !== undefined) {
          setLikeCount(e.detail.likeCount);
          syncedLikeCountRef.current = e.detail.likeCount;
          currentLikeCountValueRef.current = e.detail.likeCount;
        }
        if (e.detail.liked) {
          setDisliked(false);
          syncedDislikedRef.current = false;
          currentDislikedValueRef.current = false;
          if (e.detail.dislikeCount !== undefined) {
            setDislikeCount(e.detail.dislikeCount);
            syncedDislikeCountRef.current = e.detail.dislikeCount;
            currentDislikeCountValueRef.current = e.detail.dislikeCount;
          }
        }

        // Update global cache
        updateGlobalCache(post, {
          liked: e.detail.liked,
          likeCount: e.detail.likeCount,
          ...(e.detail.liked
            ? { disliked: false, dislikeCount: e.detail.dislikeCount }
            : {}),
        });

        // Notify parent to sync cache and avoid state reversion on re-renders
        callbacksRef.current.onLike?.(post.id, e.detail.liked);
        if (e.detail.liked && callbacksRef.current.onDislike) {
          callbacksRef.current.onDislike(post.id, false);
        }
      } else if (e.detail.source === 'dislike') {
        setDisliked(e.detail.disliked);
        syncedDislikedRef.current = e.detail.disliked;
        currentDislikedValueRef.current = e.detail.disliked;
        if (e.detail.dislikeCount !== undefined) {
          setDislikeCount(e.detail.dislikeCount);
          syncedDislikeCountRef.current = e.detail.dislikeCount;
          currentDislikeCountValueRef.current = e.detail.dislikeCount;
        }
        if (e.detail.disliked) {
          setLiked(false);
          syncedLikedRef.current = false;
          currentLikedValueRef.current = false;
          if (e.detail.likeCount !== undefined) {
            setLikeCount(e.detail.likeCount);
            syncedLikeCountRef.current = e.detail.likeCount;
            currentLikeCountValueRef.current = e.detail.likeCount;
          }
        }

        // Update global cache
        updateGlobalCache(post, {
          disliked: e.detail.disliked,
          dislikeCount: e.detail.dislikeCount,
          ...(e.detail.disliked
            ? { liked: false, likeCount: e.detail.likeCount }
            : {}),
        });

        // Notify parent to sync cache
        callbacksRef.current.onDislike?.(post.id, e.detail.disliked);
        if (e.detail.disliked && callbacksRef.current.onLike) {
          callbacksRef.current.onLike(post.id, false);
        }
      } else if (e.detail.source === 'save') {
        setSaved(e.detail.saved);
        syncedSavedRef.current = e.detail.saved;
        currentSavedValueRef.current = e.detail.saved;

        // Update global cache
        updateGlobalCache(post, { saved: e.detail.saved });

        // Notify parent to sync cache
        callbacksRef.current.onSave?.(post.id, e.detail.saved);
      } else if (e.detail.source === 'share') {
        if (e.detail.shareCount !== undefined) {
          setShareCount(e.detail.shareCount);
          setHasSharedLocally(true);
          // Notify parent to sync cache
          callbacksRef.current.onShare?.(post.id);
        }
      } else if (e.detail.source === 'comment') {
        if (e.detail.commentCount !== undefined) {
          setCommentCount(e.detail.commentCount);
          callbacksRef.current.onCommentCountChange?.(
            post.id,
            e.detail.commentCount,
          );
          updateGlobalCache(post, { commentCount: e.detail.commentCount });
        }
      } else if (e.detail.source === 'websocket') {
        if (e.detail.commentCount !== undefined) {
          setCommentCount(e.detail.commentCount);
          callbacksRef.current.onCommentCountChange?.(
            post.id,
            e.detail.commentCount,
          );
          updateGlobalCache(post, { commentCount: e.detail.commentCount });
        }
        if (e.detail.shareCount !== undefined) {
          setShareCount(e.detail.shareCount);
          callbacksRef.current.onShare?.(post.id);
          updateGlobalCache(post, { shareCount: e.detail.shareCount });
        }
        if (e.detail.likeCount !== undefined) {
          setLikeCount(e.detail.likeCount);
          syncedLikeCountRef.current = e.detail.likeCount;
          currentLikeCountValueRef.current = e.detail.likeCount;
          updateGlobalCache(post, { likeCount: e.detail.likeCount });
        }
        if (e.detail.dislikeCount !== undefined) {
          setDislikeCount(e.detail.dislikeCount);
          syncedDislikeCountRef.current = e.detail.dislikeCount;
          currentDislikeCountValueRef.current = e.detail.dislikeCount;
          updateGlobalCache(post, { dislikeCount: e.detail.dislikeCount });
        }
      }
    };
    window.addEventListener('POST_SYNC', handlePostSync);
    return () => window.removeEventListener('POST_SYNC', handlePostSync);
  }, [post?.id]);

  const handleCommentCountChange = useCallback(
    (newCount: number) => {
      setCommentCount(newCount);
      callbacksRef.current.onCommentCountChange?.(post.id, newCount);
      updateGlobalCache(post, { commentCount: newCount });
      window.dispatchEvent(
        new CustomEvent('POST_SYNC', {
          detail: {
            postId: post.id,
            source: 'comment',
            commentCount: newCount,
            emitterId: instanceId,
          },
        }),
      );
    },
    [post.id, instanceId, post],
  );

  if (!post) return null;
  if (locallyHidden) return null;
  if ((post as any).status === 'DELETED') {
    return null;
  }

  const isIssue = post.variant === 'issue';
  const isGovt = post.variant === 'government';
  const interactionType: 'posts' | 'social-posts' =
    isIssue || isGovt ? 'posts' : 'social-posts';
  const isResolved = isIssue && (post as IssuePost).status === 'RESOLVED';

  const govCanResolve =
    isIssue &&
    (post as IssuePost).status === 'ACTIVE' &&
    canUpdateResolution(post as IssuePost, currentUser);

  const showStatusBadge = isIssue;

  const postHasCommunity = isCommunityPost(post);

  // Media handling
  const allMediaUrls: string[] = (() => {
    const urls: string[] = [];
    if ('mediaUrls' in post && Array.isArray(post.mediaUrls)) {
      (post.mediaUrls as string[]).filter(Boolean).forEach((u) => {
        urls.push(resolveMediaUrl(u, 'social-posts'));
      });
    }
    if (
      urls.length === 0 &&
      'imageName' in post &&
      typeof post.imageName === 'string' &&
      post.imageName.length > 0
    ) {
      urls.push(resolveMediaUrl(post.imageName, 'posts'));
    }
    return urls;
  })();
  const hasMedia = allMediaUrls.length > 0;

  // Handlers
  async function syncLikeDislike() {
    if (isLikeDislikeSyncingRef.current) {
      hasPendingLikeDislikeSyncRef.current = true;
      return;
    }

    const targetLiked = currentLikedValueRef.current;
    const targetDisliked = currentDislikedValueRef.current;
    const syncedLiked = syncedLikedRef.current;
    const syncedDisliked = syncedDislikedRef.current;

    // Check if we actually need to sync anything
    if (targetLiked === syncedLiked && targetDisliked === syncedDisliked) {
      return;
    }

    isLikeDislikeSyncingRef.current = true;

    let endpoint: string | null = null;
    let actionType: 'like' | 'dislike' | null = null;

    if (targetLiked && !syncedLiked) {
      endpoint = `/api/interactions/${interactionType}/${post.id}/like`;
      actionType = 'like';
    } else if (targetDisliked && !syncedDisliked) {
      endpoint = `/api/interactions/${interactionType}/${post.id}/dislike`;
      actionType = 'dislike';
    } else if (!targetLiked && !targetDisliked) {
      if (syncedLiked) {
        endpoint = `/api/interactions/${interactionType}/${post.id}/like`;
        actionType = 'like';
      } else if (syncedDisliked) {
        endpoint = `/api/interactions/${interactionType}/${post.id}/dislike`;
        actionType = 'dislike';
      }
    }

    if (!endpoint || !actionType) {
      isLikeDislikeSyncingRef.current = false;
      return;
    }

    try {
      const res = await apiPost(endpoint, {});
      const data = (res as any)?.data ?? res;

      let serverLiked = targetLiked;
      let serverLikeCount = currentLikeCountValueRef.current;
      let serverDisliked = targetDisliked;
      let serverDislikeCount = currentDislikeCountValueRef.current;

      if (data) {
        if (typeof data.liked === 'boolean') {
          serverLiked = data.liked;
        } else if (actionType === 'like') {
          serverLiked = targetLiked;
        }
        // Do NOT overwrite serverLikeCount and serverDislikeCount with data from the API response.
        // The backend processes database increments/decrements asynchronously via @Async,
        // which means the counts retrieved immediately here are stale/pre-transaction values.
        // We preserve our correct optimistic counts instead.

        if (typeof data.disliked === 'boolean') {
          serverDisliked = data.disliked;
        } else if (actionType === 'dislike') {
          serverDisliked = targetDisliked;
        }
      }

      if (actionType === 'like' && serverLiked) {
        serverDisliked = false;
      }
      if (actionType === 'dislike' && serverDisliked) {
        serverLiked = false;
      }

      syncedLikedRef.current = serverLiked;
      syncedLikeCountRef.current = serverLikeCount;
      currentLikeCountValueRef.current = serverLikeCount;

      syncedDislikedRef.current = serverDisliked;
      syncedDislikeCountRef.current = serverDislikeCount;
      currentDislikeCountValueRef.current = serverDislikeCount;

      // Update global cache
      updateGlobalCache(post, {
        liked: serverLiked,
        likeCount: serverLikeCount,
        disliked: serverDisliked,
        dislikeCount: serverDislikeCount,
      });

      if (
        currentLikedValueRef.current === targetLiked &&
        currentDislikedValueRef.current === targetDisliked
      ) {
        setLiked(serverLiked);
        setLikeCount(serverLikeCount);
        setDisliked(serverDisliked);
        setDislikeCount(serverDislikeCount);

        window.dispatchEvent(
          new CustomEvent('POST_SYNC', {
            detail: {
              postId: post.id,
              source: actionType,
              liked: serverLiked,
              likeCount: serverLikeCount,
              disliked: serverDisliked,
              dislikeCount: serverDislikeCount,
              emitterId: instanceId,
            },
          }),
        );
      }
    } catch (err) {
      console.error(`Failed to sync ${actionType} interaction`, err);
      if (
        currentLikedValueRef.current === targetLiked &&
        currentDislikedValueRef.current === targetDisliked
      ) {
        setLiked(syncedLiked);
        setLikeCount(syncedLikeCountRef.current);
        currentLikeCountValueRef.current = syncedLikeCountRef.current;

        setDisliked(syncedDisliked);
        setDislikeCount(syncedDislikeCountRef.current);
        currentDislikeCountValueRef.current = syncedDislikeCountRef.current;

        // Revert global cache
        updateGlobalCache(post, {
          liked: syncedLiked,
          likeCount: syncedLikeCountRef.current,
          disliked: syncedDisliked,
          dislikeCount: syncedDislikeCountRef.current,
        });

        if (actionType === 'like') {
          onLike?.(post.id, syncedLiked);
        } else {
          onDislike?.(post.id, syncedDisliked);
        }

        window.dispatchEvent(
          new CustomEvent('POST_SYNC', {
            detail: {
              postId: post.id,
              source: actionType,
              liked: syncedLiked,
              likeCount: syncedLikeCountRef.current,
              disliked: syncedDisliked,
              dislikeCount: syncedDislikeCountRef.current,
              emitterId: instanceId,
            },
          }),
        );
      }
    } finally {
      isLikeDislikeSyncingRef.current = false;
      if (hasPendingLikeDislikeSyncRef.current) {
        hasPendingLikeDislikeSyncRef.current = false;
        syncLikeDislike();
      }
    }
  }

  async function handleLike() {
    if (!currentUserProfile) {
      navigate('/login');
      return;
    }
    if (isResolved) return;

    const nextLiked = !currentLikedValueRef.current;
    const nextLikeCount = nextLiked
      ? currentLikeCountValueRef.current + 1
      : Math.max(0, currentLikeCountValueRef.current - 1);

    let nextDislikeCount = currentDislikeCountValueRef.current;
    const hasDislike = isIssue;
    if (hasDislike && nextLiked && currentDislikedValueRef.current) {
      nextDislikeCount = Math.max(0, currentDislikeCountValueRef.current - 1);
    }

    // 1. Synchronously update Refs for instant response to successive clicks
    currentLikedValueRef.current = nextLiked;
    currentLikeCountValueRef.current = nextLikeCount;
    if (hasDislike && nextLiked) {
      currentDislikedValueRef.current = false;
      currentDislikeCountValueRef.current = nextDislikeCount;
    }

    // 2. Optimistic state updates
    setLiked(nextLiked);
    setLikeCount(nextLikeCount);
    if (hasDislike && nextLiked) {
      setDisliked(false);
      setDislikeCount(nextDislikeCount);
    }

    // Update global cache immediately on optimistic update
    updateGlobalCache(post, {
      liked: nextLiked,
      likeCount: nextLikeCount,
      ...(hasDislike && nextLiked
        ? { disliked: false, dislikeCount: nextDislikeCount }
        : {}),
    });

    // 3. Notify parent and sync instances
    onLike?.(post.id, nextLiked);
    window.dispatchEvent(
      new CustomEvent('POST_SYNC', {
        detail: {
          postId: post.id,
          source: 'like',
          liked: nextLiked,
          likeCount: nextLikeCount,
          disliked: nextLiked ? false : currentDislikedValueRef.current,
          dislikeCount: nextDislikeCount,
          emitterId: instanceId,
        },
      }),
    );

    // 4. Debounce API call
    if (pendingLikeTimerRef.current) {
      clearTimeout(pendingLikeTimerRef.current);
    }
    if (pendingDislikeTimerRef.current) {
      clearTimeout(pendingDislikeTimerRef.current);
      pendingDislikeTimerRef.current = null;
    }

    pendingLikeTimerRef.current = setTimeout(async () => {
      pendingLikeTimerRef.current = null;
      syncLikeDislike();
    }, 400);
  }

  async function handleDislike() {
    if (!currentUserProfile) {
      navigate('/login');
      return;
    }
    if (!isIssue || isResolved) return;

    const nextDisliked = !currentDislikedValueRef.current;
    const nextDislikeCount = nextDisliked
      ? currentDislikeCountValueRef.current + 1
      : Math.max(0, currentDislikeCountValueRef.current - 1);

    let nextLikeCount = currentLikeCountValueRef.current;
    if (nextDisliked && currentLikedValueRef.current) {
      nextLikeCount = Math.max(0, currentLikeCountValueRef.current - 1);
      currentLikedValueRef.current = false;
      currentLikeCountValueRef.current = nextLikeCount;
    }

    // 1. Synchronously update Refs
    currentDislikedValueRef.current = nextDisliked;
    currentDislikeCountValueRef.current = nextDislikeCount;

    // 2. Optimistic state updates
    setDisliked(nextDisliked);
    setDislikeCount(nextDislikeCount);
    if (nextDisliked) {
      setLiked(false);
      setLikeCount(nextLikeCount);
    }

    // Update global cache immediately on optimistic update
    updateGlobalCache(post, {
      disliked: nextDisliked,
      dislikeCount: nextDislikeCount,
      ...(nextDisliked ? { liked: false, likeCount: nextLikeCount } : {}),
    });

    // 3. Notify parent and sync instances
    onDislike?.(post.id, nextDisliked);
    window.dispatchEvent(
      new CustomEvent('POST_SYNC', {
        detail: {
          postId: post.id,
          source: 'dislike',
          disliked: nextDisliked,
          dislikeCount: nextDislikeCount,
          liked: nextDisliked ? false : currentLikedValueRef.current,
          likeCount: nextLikeCount,
          emitterId: instanceId,
        },
      }),
    );

    // 4. Debounce API call
    if (pendingDislikeTimerRef.current) {
      clearTimeout(pendingDislikeTimerRef.current);
    }
    if (pendingLikeTimerRef.current) {
      clearTimeout(pendingLikeTimerRef.current);
      pendingLikeTimerRef.current = null;
    }

    pendingDislikeTimerRef.current = setTimeout(async () => {
      pendingDislikeTimerRef.current = null;
      syncLikeDislike();
    }, 400);
  }

  async function syncSave() {
    if (isSaveSyncingRef.current) {
      hasPendingSaveSyncRef.current = true;
      return;
    }

    const finalSavedState = currentSavedValueRef.current;
    const originalSyncedState = syncedSavedRef.current;

    if (finalSavedState === originalSyncedState) {
      return;
    }

    isSaveSyncingRef.current = true;
    try {
      const res = await apiPost(
        `/api/interactions/${interactionType}/${post.id}/save`,
        {},
      );
      const data = (res as any)?.data ?? res;

      let serverSaved = finalSavedState;
      if (data && typeof data.saved === 'boolean') serverSaved = data.saved;
      else if (data && typeof data.isSaved === 'boolean')
        serverSaved = data.isSaved;

      syncedSavedRef.current = serverSaved;
      currentSavedValueRef.current = serverSaved;

      updateGlobalCache(post, { saved: serverSaved });

      if (currentSavedValueRef.current === finalSavedState) {
        setSaved(serverSaved);
        window.dispatchEvent(
          new CustomEvent('POST_SYNC', {
            detail: {
              postId: post.id,
              source: 'save',
              saved: serverSaved,
              emitterId: instanceId,
            },
          }),
        );
      }
    } catch (err) {
      console.error('Failed to sync save interaction', err);
      if (currentSavedValueRef.current === finalSavedState) {
        setSaved(originalSyncedState);
        currentSavedValueRef.current = originalSyncedState;

        // Revert global cache
        updateGlobalCache(post, { saved: originalSyncedState });

        onSave?.(post.id, originalSyncedState);
        window.dispatchEvent(
          new CustomEvent('POST_SYNC', {
            detail: {
              postId: post.id,
              source: 'save',
              saved: originalSyncedState,
              emitterId: instanceId,
            },
          }),
        );
      }
    } finally {
      isSaveSyncingRef.current = false;
      if (hasPendingSaveSyncRef.current) {
        hasPendingSaveSyncRef.current = false;
        syncSave();
      }
    }
  }

  async function handleSave() {
    if (!currentUserProfile) {
      navigate('/login');
      return;
    }
    const nextSaved = !currentSavedValueRef.current;

    // 1. Synchronously update Ref
    currentSavedValueRef.current = nextSaved;

    // 2. Optimistic state updates
    setSaved(nextSaved);

    if (nextSaved) {
      showToast.success('Bookmarked');
    } else {
      showToast.success('Bookmark removed');
    }

    // Update global cache immediately on optimistic update
    updateGlobalCache(post, { saved: nextSaved });

    // 3. Notify parent and sync instances
    onSave?.(post.id, nextSaved);
    window.dispatchEvent(
      new CustomEvent('POST_SYNC', {
        detail: {
          postId: post.id,
          source: 'save',
          saved: nextSaved,
          emitterId: instanceId,
        },
      }),
    );

    // 4. Debounce API call
    if (pendingSaveTimerRef.current) {
      clearTimeout(pendingSaveTimerRef.current);
    }

    pendingSaveTimerRef.current = setTimeout(async () => {
      pendingSaveTimerRef.current = null;
      syncSave();
    }, 400);
  }

  async function actuallyTriggerShare(method: string) {
    if (isSharingRef.current) return;
    isSharingRef.current = true;
    if (method === 'copy') flash();
    try {
      if (!hasSharedLocally) {
        const nextShareCount = shareCount + 1;
        setShareCount(nextShareCount);
        setHasSharedLocally(true);
        if (post) {
          const isIssue = post.variant === 'issue';
          const isGovt = post.variant === 'government';
          const type = isIssue || isGovt ? 'posts' : 'social-posts';
          const userId =
            currentUserProfile?.id ||
            currentUser?.id ||
            currentUserProfile?.username ||
            currentUser?.username ||
            'guest';
          const key = `govlyx_shared_${userId}_${type}_${post.id}`;
          try {
            localStorage.setItem(key, 'true');
          } catch (e) {
            console.error('Failed to save share to localStorage', e);
          }
        }
        onShare?.(post.id);

        window.dispatchEvent(
          new CustomEvent('POST_SYNC', {
            detail: {
              postId: post.id,
              source: 'share',
              shareCount: nextShareCount,
              emitterId: instanceId,
            },
          }),
        );
      }

      await recordShare(interactionType, post.id, hasSharedLocally, method);
    } catch (err) {
      console.error('Failed to log share', err);
    } finally {
      isSharingRef.current = false;
    }
  }

  const commId =
    getCommunityId(post) ||
    post.communityId ||
    (post as any).community?.id ||
    (post as any).communityId;
  const commSlug =
    post.communitySlug ||
    (post as any).community?.slug ||
    (post as any).communitySlug;
  const commName =
    post.communityName ||
    (post as any).community?.name ||
    (post as any).communityName;

  const myCommunityData = myCommunities?.find(
    (c: any) => String(c.id) === String(commId),
  );
  const isOwner = myCommunityData
    ? myCommunityData.isOwner === true ||
      myCommunityData.isOwner === 'true' ||
      (myCommunityData.role &&
        String(myCommunityData.role).toUpperCase() === 'OWNER') ||
      (myCommunityData.currentUserRole &&
        String(myCommunityData.currentUserRole).toUpperCase() === 'OWNER') ||
      (myCommunityData.memberRole &&
        String(myCommunityData.memberRole).toUpperCase() === 'OWNER') ||
      (myCommunityData.owner &&
        currentUserId &&
        (myCommunityData.owner === true ||
          myCommunityData.owner === 'true' ||
          Number(myCommunityData.owner.id || myCommunityData.owner) ===
            currentUserId))
    : false;

  const role =
    myCommunityData?.role ||
    myCommunityData?.currentUserRole ||
    myCommunityData?.memberRole ||
    myCommunityData?.membershipRole ||
    myCommunityData?.userRole ||
    myCommunityData?.membership?.role ||
    myCommunityData?.member?.role;
  const isAdmin = myCommunityData
    ? myCommunityData.isAdmin === true ||
      myCommunityData.isAdmin === 'true' ||
      (role && ['ADMIN', 'OWNER'].includes(String(role).toUpperCase()))
    : false;

  const isAdminOrOwner = isCommunityOwner === true || isOwner || isAdmin;

  const effectiveJoined = Boolean(
    isJoined || (myCommunityData && !myCommunityData.isDeleted),
  );
  const commIsMember = Boolean(effectiveJoined || isAdminOrOwner);

  const hasCommunityShare = !!(
    commId ||
    commSlug ||
    commName ||
    onShareToCommunity
  );

  const handleCommunityShareClick = async (e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    if (onShareToCommunity) {
      onShareToCommunity(post.id, post.content || '');
      return;
    }

    let resolvedSlug = commSlug;
    if (!resolvedSlug && commId) {
      const cache = getResolvedSlugsCache();
      if (cache[commId]) {
        resolvedSlug = cache[commId];
      } else {
        try {
          const queryName = commName || 'Community';
          const resData = (await apiFetch(
            `/api/communities/search?q=${encodeURIComponent(queryName)}`,
            'GET',
          )) as any;
          const list =
            resData?.data?.content ??
            resData?.data?.data ??
            resData?.data ??
            resData?.content ??
            [];
          if (Array.isArray(list)) {
            const match = list.find((c: any) => c.id === commId);
            if (match && match.slug) {
              resolvedSlug = match.slug;
              saveResolvedSlugToCache(commId, match.slug);
            }
          }
        } catch (err) {
          console.error('Failed to fetch community slug for share link:', err);
        }
      }
    }

    const targetSlugOrId = resolvedSlug || commId;
    if (targetSlugOrId) {
      if (commIsMember || isCommunityOwner) {
        navigate(
          `/communities/${targetSlugOrId}?tab=chat&sharedPostId=${post.id}`,
        );
      } else {
        setShowJoinToShareModal(true);
      }
    } else {
      showToast.error('Community not found for this post.');
    }
  };

  async function handleResolveConfirm(message: string) {
    if (checkProfanity(message)) {
      showToast.error(
        'Content contains prohibited language/profanity. Please check your words.',
      );
      return;
    }
    setResolving(true);
    try {
      await apiPut(
        `/api/posts/${post.id}/resolution?isResolved=true&updateMessage=${encodeURIComponent(
          message,
        )}`,
      );
      setResolveOpen(false);
      onResolve?.(post.id, true, message);
      showToast.success('Issue resolved successfully!');
    } catch (err) {
      console.error('Resolve error:', err);
      showToast.error(parseError(err));
    } finally {
      setResolving(false);
    }
  }

  async function handleReopenConfirm(reason: string) {
    if (checkProfanity(reason)) {
      showToast.error(
        'Content contains prohibited language/profanity. Please check your words.',
      );
      return;
    }
    setReopening(true);
    try {
      await apiPut(`/api/posts/${post.id}/reopen`, { reason });
      setReopenOpen(false);
      onResolve?.(post.id, false, reason);
      showToast.success('Issue reopened successfully!');
    } catch (err) {
      console.error('Reopen error:', err);
      showToast.error(parseError(err));
    } finally {
      setReopening(false);
    }
  }

  async function handleDelete() {
    setIsDeleting(true);
    try {
      const isSocial =
        post.variant === 'social' ||
        post.variant === 'community' ||
        post.variant === 'poll';
      let ep = '';
      if ((post as any).communityId) {
        ep = `/api/communities/${(post as any).communityId}/posts/${post.id}`;
      } else {
        ep = isSocial
          ? `/api/social-posts/${post.id}`
          : `/api/posts/${post.id}`;
      }

      await apiDelete(ep);
      setConfirmDeleteOpen(false);
      showToast.success('Post deleted successfully!');

      // Notify parent to remove it from the list without a full page reload if possible
      if (onDelete) {
        onDelete(post.id);
      } else {
        // Fallback for cases where onDelete isn't provided (unlikely in modern feeds)
        window.location.reload();
      }
    } catch (err: any) {
      console.error('Delete error:', err);
      showToast.error('Failed to delete post. Please try again later.');
    } finally {
      setIsDeleting(false);
    }
  }

  async function handleEditSave() {
    const nextContent = editContent.trim();
    const isSocial = post.variant === 'social' || post.variant === 'community';
    const maxLength = isSocial ? 3000 : 2000;

    if (!nextContent) {
      showToast.error('Post content is required.');
      return;
    }
    if (nextContent.length > maxLength) {
      showToast.error(
        `Post content must be ${maxLength.toLocaleString()} characters or less.`,
      );
      return;
    }
    if (checkProfanity(nextContent)) {
      showToast.error(
        'Content contains prohibited language/profanity. Please check your words.',
      );
      return;
    }

    setIsEditing(true);
    try {
      const endpoint = isSocial
        ? `/api/social-posts/${post.id}`
        : `/api/posts/${post.id}/content/dto`;
      const payload = isSocial
        ? {
            content: nextContent,
            hashtags: (post as SocialPost | CommunityPost).hashtags,
            allowComments: (post as any).allowComments,
          }
        : { content: nextContent };

      const updated = (await apiPut(endpoint, payload)) as any;
      const returnedContent =
        updated?.content ?? updated?.data?.content ?? nextContent;
      post.content = returnedContent;
      (post as BasePost).translatedContent = undefined;
      (post as BasePost).isTranslated = false;
      setContentOverride(returnedContent);
      setDynamicTranslation(null);
      setShowOriginal(false);
      setEditOpen(false);
      showToast.success('Post updated successfully.');
    } catch (err: any) {
      console.error('Edit post error:', err);
      showToast.error(parseError(err));
    } finally {
      setIsEditing(false);
    }
  }

  async function handleJoinCommunity(cid: number) {
    if (!currentUserProfile) {
      navigate('/login');
      return;
    }
    if (isProcessing) return;
    if (effectiveJoined) {
      setShowLeaveConfirm(true);
      return;
    }
    setPendingJoinCommunityId(cid);
    setShowJoinConfirm(true);
  }

  async function handleJoinConfirm() {
    if (!currentUserProfile) {
      navigate('/login');
      return;
    }
    const cid = pendingJoinCommunityId || getCommunityId(post);
    if (!cid || isProcessing) return;
    setIsProcessing(true);
    setIsJoined(true);
    try {
      await apiPost(`/api/communities/${cid}/join`, {});
      queryClient.invalidateQueries({ queryKey: ['my-communities'] });
      showToast.success('Joined community successfully!');
    } catch {
      setIsJoined(false);
      showToast.error('Could not join community.');
    } finally {
      setIsProcessing(false);
      setShowJoinConfirm(false);
      setPendingJoinCommunityId(null);
    }
  }

  async function handleLeaveConfirm() {
    if (!currentUserProfile) {
      navigate('/login');
      return;
    }
    const cid = getCommunityId(post);
    if (!cid || isProcessing) return;
    setIsProcessing(true);
    try {
      await apiDelete(`/api/communities/${cid}/leave`);
      setIsJoined(false);
      queryClient.invalidateQueries({ queryKey: ['my-communities'] });
      showToast.success('Left community successfully!');
    } catch (err) {
      console.error('Failed to leave community:', err);
      showToast.error('Could not leave community.');
    } finally {
      setIsProcessing(false);
      setShowLeaveConfirm(false);
    }
  }

  async function handlePollVote(pollId: number, optionIds: number[]) {
    if (!currentUserProfile) {
      navigate('/login');
      return;
    }
    setIsProcessing(true);
    try {
      // 1. Notify parent if needed
      onVote?.(pollId, optionIds);

      // 2. Perform API call
      const res = (await apiPost(
        `/api/polls/${pollId}/vote`,
        optionIds,
      )) as any;

      // 3. Update the post data locally if we're in a poll variant
      if (post.variant === 'poll' && res) {
        res.isPoll = true;
        const updatedPoll = toPostCardPost(res) as PollPost;
        // Selectively merge poll-specific and response fields, keeping original author/metadata
        post.options = updatedPoll.options;
        post.totalVotes = updatedPoll.totalVotes;
        post.userHasVoted = updatedPoll.userHasVoted;
        post.votedOptionIds = updatedPoll.votedOptionIds;
        post.showResults = updatedPoll.showResults;
        post.timeLeft = updatedPoll.timeLeft || post.timeLeft;
        post.expiresAt = updatedPoll.expiresAt;
        post.isExpired = updatedPoll.isExpired;
      }
    } catch (err: any) {
      console.error('Poll vote failed:', err);
      showToast.error(
        err.message === '403' || err.message === '401'
          ? 'Please login to vote.'
          : 'Failed to submit vote. Please try again.',
      );
    } finally {
      setIsProcessing(false);
    }
  }

  const borderClass = isGovt
    ? 'border-[#1D4ED8]/25 bg-base-200'
    : isResolved
      ? 'border-emerald-500/20 bg-base-200'
      : 'border-base-300/80 dark:border-white/10 bg-base-200';

  const handleCommentClick = () => {
    // Comments are public (GET /api/comments/** is permitAll).
    // Allow guests to view existing comments — CommentSection shows a
    // "sign in to comment" prompt if they try to write one.
    setCommentsOpen(!commentsOpen);
  };

  const handleShareClick = () => {
    if (!currentUserProfile) {
      navigate('/login');
      return;
    }
    setShareMenuOpen(true);
  };

  const hasBackendTranslation =
    !!(post as BasePost).isTranslated && !!(post as BasePost).translatedContent;
  const hasTranslation = hasBackendTranslation || !!dynamicTranslation;
  const displayText =
    hasTranslation && !showOriginal
      ? dynamicTranslation || (post as BasePost).translatedContent!
      : contentOverride;

  useEffect(() => {
    const element = textRef.current;
    if (!element || typeof ResizeObserver === 'undefined') return;

    const observer = new ResizeObserver(() => {
      if (!expanded) {
        const isTruncated = element.scrollHeight > element.clientHeight;
        setCanExpand(isTruncated);
      }
    });

    observer.observe(element);
    return () => {
      observer.disconnect();
    };
  }, [displayText, expanded]);

  const canShowDelete =
    !hideDelete &&
    (isCommunityOwner ||
      ((post as any).canDelete !== undefined
        ? !!(post as any).canDelete
        : isAuthor));
  const canShowEdit =
    post.variant !== 'poll' &&
    ((post as any).canEdit !== undefined ? !!(post as any).canEdit : isAuthor);
  const translateLabel = hasTranslation
    ? showOriginal
      ? 'See Translation'
      : 'Show Original'
    : 'Translate';

  const handleMenuTranslate = () => {
    if (isTranslating) return;
    setMoreMenuOpen(false);
    if (hasTranslation) {
      setShowOriginal((v) => !v);
      return;
    }
    handleTranslateDynamic();
  };

  const headerMoreMenu = (
    <div ref={menuRef} className="relative shrink-0">
      <motion.button
        onClick={(e) => {
          e.stopPropagation();
          setMoreMenuOpen((open) => !open);
        }}
        whileHover={{ scale: 1.08, y: -1 }}
        whileTap={{ scale: 0.94 }}
        className="relative flex h-8 w-8 sm:h-9 sm:w-9 items-center justify-center rounded-xl border border-base-300/60 dark:border-white/10 bg-transparent text-base-content/70 hover:text-base-content transition-all duration-200 hover:border-base-content/30 dark:hover:border-white/20 hover:bg-base-content/5 dark:hover:bg-white/5 cursor-pointer"
        title="More options"
        aria-haspopup="menu"
        aria-expanded={moreMenuOpen}
      >
        <MoreVertical size={16} />
      </motion.button>

      <AnimatePresence>
        {moreMenuOpen && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.96 }}
            transition={{ duration: 0.16 }}
            className="absolute right-0 top-11 z-50 w-48 overflow-hidden rounded-2xl border border-base-300 bg-base-100 p-1.5 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
            role="menu"
          >
            {canShowEdit && (
              <button
                onClick={() => {
                  setEditContent(contentOverride);
                  setMoreMenuOpen(false);
                  setEditOpen(true);
                }}
                disabled={isEditing}
                className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-xs font-bold text-base-content/75 hover:bg-[#1D4ED8] hover:text-white transition-colors disabled:opacity-40"
                role="menuitem"
              >
                <Pencil size={14} />
                Edit
              </button>
            )}
            {canShowDelete && (
              <button
                onClick={() => {
                  setMoreMenuOpen(false);
                  setConfirmDeleteOpen(true);
                }}
                disabled={isDeleting}
                className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-xs font-bold text-red-600 hover:bg-red-500/10 transition-colors disabled:opacity-40"
                role="menuitem"
              >
                <Trash2 size={14} />
                Delete
              </button>
            )}
            {onShareToCommunity && (
              <button
                onClick={() => {
                  setMoreMenuOpen(false);
                  onShareToCommunity(post.id, post.content || '');
                }}
                className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-xs font-bold text-base-content/75 hover:bg-[#1D4ED8] hover:text-white transition-colors"
                role="menuitem"
              >
                <MessageSquare size={14} />
                Reply in Community
              </button>
            )}
            <button
              onClick={handleMenuTranslate}
              disabled={isTranslating}
              className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-xs font-bold text-base-content/75 hover:bg-[#1D4ED8] hover:text-white transition-colors disabled:opacity-40"
              role="menuitem"
            >
              {isTranslating ? (
                <span className="loading loading-spinner w-3 h-3" />
              ) : (
                <Globe size={14} />
              )}
              {isTranslating ? 'Translating...' : translateLabel}
            </button>
            {!isAuthor && (
              <>
                <button
                  onClick={() => {
                    setMoreMenuOpen(false);
                    showToast.success('Marked as interested.');
                  }}
                  className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-xs font-bold text-base-content/75 hover:bg-[#1D4ED8] hover:text-white transition-colors"
                  role="menuitem"
                >
                  <CheckCircle2 size={14} />
                  Interested
                </button>
                <button
                  onClick={() => {
                    setMoreMenuOpen(false);
                    setConfirmNotInterestedOpen(true);
                  }}
                  className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-xs font-bold text-base-content/75 hover:bg-[#1D4ED8] hover:text-white transition-colors"
                  role="menuitem"
                >
                  <EyeOff size={14} />
                  Not Interested
                </button>
                {currentUser && post.username !== currentUser.username && (
                  <button
                    onClick={() => {
                      setMoreMenuOpen(false);
                      setReportOpen(true);
                    }}
                    className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-xs font-bold text-red-650 hover:bg-red-500/10 hover:text-red-600 transition-colors"
                    role="menuitem"
                  >
                    <Flag size={14} />
                    Report
                  </button>
                )}
              </>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );

  return (
    <>
      <motion.div
        initial={{ opacity: 0, scale: 0.98 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.4, ease: [0.23, 1, 0.32, 1] }}
        className={`rounded-[2rem] border ${borderClass} shadow-sm overflow-visible flex flex-col relative group/card transition-all duration-500 notranslate`}
      >
        <div className="p-5 sm:p-6 flex flex-col gap-4 flex-1 relative">
          {/* Community Strip at top */}
          {postHasCommunity && !hideCommunityStrip && (
            <CommunityStrip
              post={post}
              isJoined={effectiveJoined}
              onJoin={handleJoinCommunity}
              isAdminOrOwner={isAdminOrOwner}
            />
          )}

          {/* Header Row: Author + Join */}
          <div className="flex items-start justify-between gap-3">
            {isGovt ? (
              <>
                <motion.div
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  className="flex items-center gap-3 min-w-0"
                >
                  <div
                    className="relative shrink-0 cursor-pointer"
                    onClick={(e) => {
                      e.stopPropagation();
                      if (post.username) {
                        setProfileModalUsername(post.username);
                        setProfileModalDisplayName(
                          (post as GovernmentPost).department || post.username,
                        );
                        setProfileModalAvatar(post.userProfileImage || null);
                        setProfileModalOpen(true);
                      }
                    }}
                  >
                    {post.userProfileImage ? (
                      <img
                        src={post.userProfileImage}
                        className="w-10 h-10 rounded-full object-cover ring-2 ring-red-500/20 ring-offset-2 ring-offset-base-100"
                        alt=""
                      />
                    ) : (
                      <img
                        src={`https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(
                          post.username || '?',
                        )}`}
                        className="w-10 h-10 rounded-full object-cover bg-red-500/5 ring-2 ring-red-500/20 ring-offset-2 ring-offset-base-100"
                        alt="Avatar"
                      />
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5">
                      <span
                        className="font-bold text-[#EF4444] dark:text-[#F87171] text-sm truncate tracking-tight notranslate cursor-pointer hover:underline"
                        onClick={(e) => {
                          e.stopPropagation();
                          if (post.username) {
                            setProfileModalUsername(post.username);
                            setProfileModalDisplayName(
                              (post as GovernmentPost).department ||
                                post.username,
                            );
                            setProfileModalAvatar(
                              post.userProfileImage || null,
                            );
                            setProfileModalOpen(true);
                          }
                        }}
                      >
                        {(post as GovernmentPost).department || post.username}
                      </span>
                      <BadgeCheck
                        size={16}
                        className="text-red-500 fill-red-500/10 shrink-0"
                      />
                    </div>
                    <p className="text-[10px] text-base-content/50 mt-0.5 flex items-center gap-1.5 flex-wrap">
                      <span>{post.timeAgo ?? 'just now'}</span>
                      {post.isPendingSync && (
                        <span className="inline-flex items-center gap-1 text-[9px] text-[#1D4ED8] dark:text-blue-400 font-bold uppercase tracking-wider bg-blue-500/10 px-2 py-0.5 rounded-full border border-blue-500/20 animate-pulse">
                          <span className="loading loading-spinner loading-xs text-[#1D4ED8]" />
                          Wait, uploading...
                        </span>
                      )}
                    </p>
                  </div>
                </motion.div>
                <div className="flex items-center gap-1.5 shrink-0">
                  {headerMoreMenu}
                </div>
              </>
            ) : (
              <AuthorRow
                post={post}
                onDelete={undefined}
                isDeleting={isDeleting}
                showDelete={false}
                hideDelete={true}
                hideAuthorRoleBadge={hideAuthorRoleBadge}
                onProfileClick={(uname) => {
                  setProfileModalUsername(uname);
                  setProfileModalDisplayName(post.userDisplayName || uname);
                  setProfileModalAvatar(post.userProfileImage || null);
                  setProfileModalOpen(true);
                }}
                rightAction={
                  <div className="flex items-center gap-1.5">
                    {headerMoreMenu}
                  </div>
                }
              />
            )}
          </div>

          {/* Meta row */}
          {(isIssue || isGovt) && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="flex flex-wrap items-center gap-2"
            >
              {isIssue && showStatusBadge && (
                <StatusBadge
                  status={(post as IssuePost).status}
                  reopened={
                    (post as IssuePost).reopened ||
                    (post as IssuePost).isReopened
                  }
                />
              )}
              {(post as any).targetPincodes &&
                (post as any).targetPincodes.length > 0 && (
                  <div className="flex items-center gap-1 bg-[#1D4ED8]/5 text-[#1D4ED8] dark:text-[#60A5FA] dark:bg-[#1D4ED8]/20 text-[10px] font-bold px-2 py-0.5 rounded-full border border-[#1D4ED8]/10">
                    <MapPin size={10} />
                    <span>
                      Pincode: {(post as any).targetPincodes.join(', ')}
                    </span>
                  </div>
                )}
            </motion.div>
          )}

          {/* Mark Resolved banner */}
          {govCanResolve && !resolving && (
            <motion.button
              whileHover={{ scale: 1.01 }}
              onClick={() => setResolveOpen(true)}
              className="flex items-center justify-between gap-3 rounded-xl border border-amber-500/20 bg-amber-500/5 px-4 py-2.5 text-left"
            >
              <span className="flex items-center gap-2 text-xs font-bold text-amber-500">
                <AlertCircle size={14} /> Assigned to your department
              </span>
              <span className="text-[10px] font-black uppercase text-amber-600">
                Resolve Now →
              </span>
            </motion.button>
          )}

          {/* Content: Text Always Above */}
          <motion.div className="space-y-1.5 relative overflow-hidden">
            {post.contentHidden && !isContentRevealed && (
              <div
                onClick={(e) => {
                  e.stopPropagation();
                  setIsContentRevealed(true);
                }}
                className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-base-300/30 backdrop-blur-sm cursor-pointer rounded-xl border border-warning/10 hover:bg-base-300/40 transition-colors p-3 text-center select-none"
              >
                <AlertCircle className="text-warning mb-1 shrink-0" size={16} />
                <p className="text-[11px] font-black uppercase tracking-wider text-warning">
                  Content Moderated
                </p>
                <p className="text-[10px] text-base-content/80 font-bold mt-0.5 line-clamp-1">
                  Reason: {post.hiddenReason || 'Violates community guidelines'}
                </p>
                <p className="text-[9px] text-base-content/50 uppercase tracking-widest font-black mt-1">
                  Click to reveal
                </p>
              </div>
            )}

            <motion.div
              ref={textRef}
              className={`text-[13px] leading-relaxed font-medium text-base-content/90 notranslate ${!expanded ? 'line-clamp-3' : ''} ${post.contentHidden && !isContentRevealed ? 'blur-sm opacity-50 select-none' : ''}`}
            >
              <MarkdownRenderer content={displayText} />
            </motion.div>
            {(!post.contentHidden || isContentRevealed) &&
              (canExpand || expanded) && (
                <button
                  onClick={() => setExpanded(!expanded)}
                  className="text-[9px] font-black uppercase tracking-widest text-red-600 dark:text-red-500 hover:underline [text-shadow:0_0_8px_rgba(239,68,68,0.4)] transition-all"
                >
                  {expanded ? 'Less ↑' : 'More ↓'}
                </button>
              )}
          </motion.div>

          {/* Tagged depts */}
          {isIssue &&
            ((post as IssuePost).taggedUsernames?.length ?? 0) > 0 && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.1 }}
                className="flex flex-wrap gap-2"
              >
                {(post as IssuePost).taggedUsernames?.map((name) => (
                  <motion.span
                    key={name}
                    initial={{ opacity: 0, scale: 0.8 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-blue-200 bg-blue-50 px-3 py-1.5 text-xs font-semibold text-blue-700 notranslate"
                  >
                    <Building2 size={12} /> @{name}
                  </motion.span>
                ))}
              </motion.div>
            )}

          {/* Conditional Body Layout */}
          <div
            className={
              hasMedia
                ? 'flex flex-col lg:flex-row gap-4 items-start'
                : 'flex flex-col gap-4'
            }
          >
            <div className="flex-1 min-w-0 flex flex-col gap-4 w-full lg:order-1">
              {hasMedia && (
                <div className="-mx-1 relative rounded-2xl overflow-hidden">
                  <ModernMediaCarousel
                    mediaUrls={allMediaUrls}
                    onExpand={(idx) => {
                      setLightboxIndex(idx);
                      setLightboxOpen(true);
                    }}
                  />
                  {post.isPendingSync && (
                    <div className="absolute inset-0 bg-base-100/50 dark:bg-black/60 backdrop-blur-[2px] flex flex-col items-center justify-center gap-2 z-20 pointer-events-none">
                      <span className="loading loading-spinner loading-md text-[#1D4ED8]" />
                      <span className="text-xs font-bold text-base-content bg-base-100/90 dark:bg-base-200/90 px-3 py-1 rounded-full border border-base-300 shadow-md animate-pulse flex items-center gap-1.5">
                        Wait, uploading media...
                      </span>
                    </div>
                  )}
                </div>
              )}

              {/* Poll Variant Rendering */}
              {post.variant === 'poll' && (post as any).options && (
                <div className="relative">
                  <PollBody
                    post={post as PollPost}
                    onVote={handlePollVote}
                    isProcessing={isProcessing}
                  />
                  {post.isPendingSync && (
                    <div className="absolute inset-0 bg-base-100/50 dark:bg-black/60 backdrop-blur-[2px] flex flex-col items-center justify-center gap-2 z-20 pointer-events-none rounded-xl">
                      <span className="loading loading-spinner loading-md text-[#1D4ED8]" />
                      <span className="text-xs font-bold text-base-content bg-base-100/90 dark:bg-base-200/90 px-3 py-1 rounded-full border border-base-300 shadow-md animate-pulse">
                        Wait, uploading poll...
                      </span>
                    </div>
                  )}
                </div>
              )}

              {isResolved && (
                <div className="p-3 rounded-xl bg-blue-500/10 border border-blue-500/20 flex items-center justify-between gap-2 text-xs font-bold text-blue-600 dark:text-blue-400 shadow-[0_0_8px_rgba(59,130,246,0.05)]">
                  <div className="flex items-center gap-2">
                    <CheckCircle2 size={14} className="text-blue-500" /> Issue
                    resolved
                  </div>
                  {currentUser && post.username === currentUser.username && (
                    <motion.button
                      whileHover={{ scale: 1.02 }}
                      whileTap={{ scale: 0.98 }}
                      disabled={reopening}
                      onClick={(e) => {
                        e.stopPropagation();
                        setReopenOpen(true);
                      }}
                      className="px-2.5 py-1 rounded-lg bg-red-600 text-white font-semibold text-[11px] hover:bg-red-700 transition-colors shadow-sm cursor-pointer disabled:opacity-50 flex items-center gap-1"
                    >
                      {reopening && (
                        <span className="loading loading-spinner loading-xs" />
                      )}
                      Reopen Issue
                    </motion.button>
                  )}
                </div>
              )}

              {((post as IssuePost).reopened ||
                (post as IssuePost).isReopened) &&
                !isResolved && (
                  <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/20 flex items-center gap-2 text-xs font-bold text-red-600 dark:text-red-400">
                    <AlertCircle size={14} className="text-red-500" /> Issue
                    Reopened:{' '}
                    {((post as IssuePost).reopenedReason ||
                      (post as IssuePost).reopenReason) ??
                      'Reason not specified'}
                  </div>
                )}

              {/* Horizontal Action Bar */}
              <div
                className={`flex items-center gap-1 sm:gap-2 border-t border-base-300 pt-3 ${hasMedia ? 'lg:hidden' : 'flex'}`}
              >
                <ActionPill
                  onClick={handleLike}
                  active={liked}
                  disabled={isResolved}
                  activeClass={POST_ACTION_ACTIVE_CLASS}
                  hoverGlow={POST_ACTION_HOVER_GLOW}
                >
                  <PostActionIcon name="like" active={liked} />
                  <span>{likeCount || '0'}</span>
                </ActionPill>
                <ActionPill
                  onClick={handleCommentClick}
                  onHover={() =>
                    prefetchComments(post.id, commentPostType(post.variant))
                  }
                  active={commentsOpen}
                  activeClass={POST_ACTION_ACTIVE_CLASS}
                  hoverGlow={POST_ACTION_HOVER_GLOW}
                >
                  <PostActionIcon name="comment" active={commentsOpen} />
                  <span>{commentCount ?? 0}</span>
                </ActionPill>
                <ActionPill
                  onClick={handleShareClick}
                  active={copied}
                  activeClass={POST_ACTION_ACTIVE_CLASS}
                  hoverGlow={POST_ACTION_HOVER_GLOW}
                >
                  <PostActionIcon name="share" active={copied} />
                  <span>{copied ? 'Copied!' : shareCount || '0'}</span>
                </ActionPill>
                <div className="flex-1" />
                {isIssue ? (
                  <ActionPill
                    onClick={handleDislike}
                    active={disliked}
                    disabled={isResolved}
                    activeClass={POST_ACTION_ACTIVE_CLASS}
                    hoverGlow={POST_ACTION_HOVER_GLOW}
                  >
                    <PostActionIcon name="dislike" active={disliked} />
                    <span>{dislikeCount || '0'}</span>
                  </ActionPill>
                ) : (
                  <ActionPill
                    onClick={handleSave}
                    active={saved}
                    activeClass="bg-transparent text-[#1D4ED8] dark:text-white border-transparent"
                    hoverGlow={POST_ACTION_HOVER_GLOW}
                  >
                    <PostActionIcon name="bookmark" active={saved} />
                  </ActionPill>
                )}
              </div>
            </div>

            {/* Desktop Sidebar: Visible only when media exists and on lg: screens */}
            {hasMedia && (
              <motion.div
                initial={{ opacity: 0, x: 10 }}
                animate={{ opacity: 1, x: 0 }}
                className="hidden lg:flex flex-col gap-2 p-1 rounded-2xl bg-base-200/50 border border-base-300 lg:order-2 shrink-0 sticky top-0"
              >
                <ActionPill
                  onClick={handleLike}
                  active={liked}
                  disabled={isResolved}
                  vertical
                  activeClass={POST_ACTION_ACTIVE_CLASS}
                  hoverGlow={POST_ACTION_HOVER_GLOW}
                >
                  <PostActionIcon name="like" active={liked} vertical />
                  <span>{likeCount || '0'}</span>
                </ActionPill>
                <ActionPill
                  onClick={handleCommentClick}
                  onHover={() =>
                    prefetchComments(post.id, commentPostType(post.variant))
                  }
                  active={commentsOpen}
                  vertical
                  activeClass={POST_ACTION_ACTIVE_CLASS}
                  hoverGlow={POST_ACTION_HOVER_GLOW}
                >
                  <PostActionIcon
                    name="comment"
                    active={commentsOpen}
                    vertical
                  />
                  <span>{commentCount ?? 0}</span>
                </ActionPill>
                <ActionPill
                  onClick={handleShareClick}
                  active={copied}
                  vertical
                  activeClass={POST_ACTION_ACTIVE_CLASS}
                  hoverGlow={POST_ACTION_HOVER_GLOW}
                >
                  <PostActionIcon name="share" active={copied} vertical />
                  <span className="text-[9px] leading-tight mt-0.5">
                    {copied ? 'Copied' : shareCount || '0'}
                  </span>
                </ActionPill>
                {isIssue ? (
                  <ActionPill
                    onClick={handleDislike}
                    active={disliked}
                    disabled={isResolved}
                    vertical
                    activeClass={POST_ACTION_ACTIVE_CLASS}
                    hoverGlow={POST_ACTION_HOVER_GLOW}
                  >
                    <PostActionIcon name="dislike" active={disliked} vertical />
                    <span>{dislikeCount || '0'}</span>
                  </ActionPill>
                ) : (
                  <ActionPill
                    onClick={handleSave}
                    active={saved}
                    vertical
                    activeClass="bg-transparent text-[#1D4ED8] dark:text-white border-transparent"
                    hoverGlow={POST_ACTION_HOVER_GLOW}
                    className="h-[58px] sm:h-[66px] justify-center"
                  >
                    <PostActionIcon name="bookmark" active={saved} vertical />
                  </ActionPill>
                )}
              </motion.div>
            )}
          </div>

          {/* Comments section */}
          <AnimatePresence>
            {commentsOpen && (
              <div className="mt-2 border-t border-base-300 pt-4">
                <CommentSection
                  postId={post.id}
                  postType={commentPostType(post.variant)}
                  commentCount={commentCount}
                  currentUsername={
                    currentUserProfile?.username ||
                    currentUserProfile?.actualUsername ||
                    currentUser?.username
                  }
                  currentRole={currentUserProfile?.role || currentUser?.role}
                  defaultOpen={true}
                  onCommentCountChange={handleCommentCountChange}
                  readOnly={readOnly}
                  isCommunityOwner={Boolean(isCommunityOwner || isAdminOrOwner)}
                  isPostAuthor={isAuthor}
                />
              </div>
            )}
          </AnimatePresence>
        </div>
      </motion.div>

      <ResolveModal
        isOpen={resolveOpen}
        onClose={() => setResolveOpen(false)}
        onConfirm={handleResolveConfirm}
      />

      <ReopenModal
        isOpen={reopenOpen}
        onClose={() => setReopenOpen(false)}
        onConfirm={handleReopenConfirm}
      />

      <ShareModal
        isOpen={shareMenuOpen}
        onClose={() => setShareMenuOpen(false)}
        post={post}
        onShareAction={actuallyTriggerShare}
        hasCommunityShare={hasCommunityShare}
        onShareToCommunity={handleCommunityShareClick}
      />

      <ConfirmModal
        isOpen={confirmDeleteOpen}
        onClose={() => setConfirmDeleteOpen(false)}
        onConfirm={handleDelete}
        title="Delete Post"
        message="Are you sure you want to delete this post? This action cannot be undone."
        isLoading={isDeleting}
      />

      <ConfirmModal
        isOpen={showLeaveConfirm}
        onClose={() => setShowLeaveConfirm(false)}
        onConfirm={handleLeaveConfirm}
        title="Leave Community"
        message={
          commName
            ? `Are you sure you want to leave "${commName}"? You will no longer receive updates or see posts from this community.`
            : 'Are you sure you want to leave this community? You will no longer receive updates or see posts from this community.'
        }
        confirmLabel="Leave"
        isLoading={isProcessing}
      />

      <ConfirmModal
        isOpen={showJoinConfirm}
        onClose={() => {
          setShowJoinConfirm(false);
          setPendingJoinCommunityId(null);
        }}
        onConfirm={handleJoinConfirm}
        title="Join Community"
        message={
          commName
            ? `Are you sure you want to join "${commName}"? You will be able to participate, see posts, and connect with other members.`
            : 'Are you sure you want to join this community? You will be able to participate, see posts, and connect with other members.'
        }
        confirmLabel="Join"
        isDanger={false}
        isLoading={isProcessing}
      />

      <ConfirmModal
        isOpen={showJoinToShareModal}
        onClose={() => setShowJoinToShareModal(false)}
        onConfirm={async () => {
          setIsJoiningCommunity(true);
          const targetSlugOrId = commSlug || commId;
          if (commId) {
            try {
              await apiPost(`/api/communities/${commId}/join`, {});
              setIsJoined(true);
              showToast.success(`Joined ${commName || 'Community'}!`);
            } catch (err) {
              console.error('Failed to join community', err);
            }
          }
          setIsJoiningCommunity(false);
          setShowJoinToShareModal(false);
          navigate(
            `/communities/${targetSlugOrId}?tab=chat&sharedPostId=${post.id}`,
          );
        }}
        title="Join Community to Share"
        message={`Join "${decodeHTML(commName || 'Community')}" to reply and share this post directly into the community chat.`}
        confirmLabel="Join & Open Chat"
        cancelLabel="Cancel"
        isLoading={isJoiningCommunity}
        isDanger={false}
      />

      <AnimatePresence>
        {editOpen && (
          <motion.div
            className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => !isEditing && setEditOpen(false)}
          >
            <motion.div
              initial={{ opacity: 0, y: 12, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 12, scale: 0.98 }}
              transition={{ duration: 0.18 }}
              className="w-full max-w-lg overflow-hidden rounded-2xl border border-black/10 dark:border-white/15 bg-base-100 shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between border-b border-base-300 px-4 py-3">
                <div className="flex items-center gap-2">
                  <Pencil size={16} className="text-blue-700" />
                  <h3 className="text-sm font-black uppercase tracking-wide">
                    Edit Post
                  </h3>
                </div>
                <button
                  type="button"
                  className="btn btn-ghost btn-circle btn-sm"
                  onClick={() => setEditOpen(false)}
                  disabled={isEditing}
                  aria-label="Close edit post"
                >
                  <X size={16} />
                </button>
              </div>
              <div className="space-y-2 p-4">
                <textarea
                  className="textarea textarea-bordered min-h-40 w-full resize-none rounded-xl text-sm leading-relaxed"
                  value={editContent}
                  onChange={(e) => setEditContent(e.target.value)}
                  maxLength={
                    post.variant === 'social' || post.variant === 'community'
                      ? 3000
                      : 2000
                  }
                  disabled={isEditing}
                  autoFocus
                />
                <div className="flex items-center justify-between gap-3 text-[11px] font-semibold text-base-content/50">
                  <span>Text only</span>
                  <span>
                    {editContent.length.toLocaleString()} /{' '}
                    {(post.variant === 'social' || post.variant === 'community'
                      ? 3000
                      : 2000
                    ).toLocaleString()}
                  </span>
                </div>
              </div>
              <div className="flex justify-end gap-2 border-t border-base-300 px-4 py-3">
                <button
                  type="button"
                  className="btn btn-ghost btn-sm rounded-xl"
                  onClick={() => setEditOpen(false)}
                  disabled={isEditing}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="btn btn-sm rounded-xl border-none bg-blue-700 px-5 font-bold text-white hover:bg-blue-800"
                  onClick={handleEditSave}
                  disabled={
                    isEditing ||
                    !editContent.trim() ||
                    editContent.trim() === contentOverride.trim()
                  }
                >
                  {isEditing ? (
                    <>
                      <span className="loading loading-spinner loading-xs" />{' '}
                      Saving...
                    </>
                  ) : (
                    'Save'
                  )}
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <ConfirmModal
        isOpen={confirmNotInterestedOpen}
        onClose={() => setConfirmNotInterestedOpen(false)}
        onConfirm={async () => {
          setConfirmNotInterestedOpen(false);
          if (onNotInterested) {
            onNotInterested(post.id);
          } else {
            setLocallyHidden(true);
            try {
              await axiosInstance.post('/api/v1/feed/signal/not-interested', {
                postId: post.id,
              });
            } catch (err) {
              console.error(
                'Failed to submit not interested signal from card:',
                err,
              );
            }
          }
        }}
        title="Not Interested"
        message="Are you sure you want to mark this post as 'Not Interested'? This post will be hidden from your feed."
        confirmLabel="Not Interested"
        cancelLabel="Interested"
        isCancelSuccess={true}
        isDanger={true}
      />

      <ReportModal
        isOpen={reportOpen}
        onClose={() => setReportOpen(false)}
        targetType={isIssue ? 'POST' : 'SOCIAL_POST'}
        targetId={post.id}
      />

      {/* Lightbox / Zoom Viewer */}
      <AnimatePresence>
        {lightboxOpen && hasMedia && (
          <ZoomViewer
            mediaUrls={allMediaUrls}
            startIndex={lightboxIndex}
            onClose={() => setLightboxOpen(false)}
          />
        )}
      </AnimatePresence>

      <UserProfileModal
        isOpen={profileModalOpen}
        onClose={() => setProfileModalOpen(false)}
        username={profileModalUsername}
        fallbackDisplayName={profileModalDisplayName}
        fallbackProfileImage={profileModalAvatar}
      />
    </>
  );
}
