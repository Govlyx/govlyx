import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import {
  useNavigate,
  useParams,
  useLocation,
  useSearchParams,
} from 'react-router-dom';
import {
  Building2,
  Construction,
  GraduationCap,
  Stethoscope,
  Leaf,
  Laptop,
  Trophy,
  Palette,
  Briefcase,
  HardHat,
  ShieldCheck,
  Globe,
  Lock,
  EyeOff,
  Users,
  Mail,
  ClipboardCheck,
  CheckCircle2,
  AlertTriangle,
  Inbox,
  Settings,
  BarChart3,
  X,
  Crown,
  Shield,
  User,
  VolumeX,
  Volume2,
  Ban,
  Trash2,
  Save,
  Archive,
  MessageSquare,
  Calendar,
  Tag,
  Rocket,
  PartyPopper,
  Plus,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  XCircle,
  Home,
  Link,
  Image as ImageIcon,
  RefreshCw,
  Activity,
  Radio,
  FileText,
  Upload,
  Sparkles,
  Flame,
  Check,
  MoreVertical,
  Search,
  ArrowRight,
  Send,
  Copy,
  Instagram,
  ArrowDown,
  Wrench,
  Gamepad2,
  Music,
  Utensils,
  Compass,
  Coins,
  Atom,
  Car,
  Smile,
  Film,
  BookOpen,
  Heart,
  Share2,
  Maximize2,
  Minimize2,
  GripVertical,
} from 'lucide-react';

import CommunityCard from '../components/community/CommunityCard';
import CommunityHeader from '../components/community/CommunityHeader';
import CommunityTabs from '../components/community/CommunityTabs';
import CommunityChat from '../components/community/CommunityChat';
import CommunitySidebar from '../components/community/CommunitySidebar';
import CreatePost from '../components/ui/CreatePost';
import PostCard from '../components/post/PostCard';
import LoadingAnimation from '../components/ui/LoadingAnimation';
import ImageEditorModal from '../components/modals/ImageEditorModal';
import type { CurrentUser as CardUser } from '../components/post/PostCard';
import { useQueryClient, useQuery } from '@tanstack/react-query';
import ConfirmModal from '../components/post/ConfirmModal';
import { getAuthToken } from '../utils/auth';
import { getSessionActorToken } from '../services/vaultService';
import axiosInstance from '../api/axiosConfig';
import { toPostCardPost, decodeHTML } from '../utils/postUtils';
import { jwtDecode } from 'jwt-decode';
import { cacheSuggestion } from '../utils/searchCache';
import { apiUrl } from '../utils/apiUrl';
import { communityService } from '../api/communityService';
import { showToast } from '../utils/toast';
import { postService } from '../api/postService';
import { Helmet } from 'react-helmet-async';
import NewPostsBanner from '../components/NewPostsBanner';
import { useFeedRefresh } from '../hooks/useFeedRefresh';
import { useCurrentUser } from '../hooks/useUser';
import PullToRefresh from '../components/ui/PullToRefresh';

const fetchCache: Record<string, { data: Response; timestamp: number }> = {};
const CACHE_TTL_MS = 30 * 1000; // 30 seconds cache TTL
let lastRefreshCacheTime = 0;
const REFRESH_CACHE_THROTTLE_MS = 60 * 1000; // 1 minute throttle

const cachedFetch = async (
  url: string | URL | Request,
  init?: RequestInit,
): Promise<Response> => {
  const urlStr =
    typeof url === 'string'
      ? url
      : url instanceof URL
        ? url.toString()
        : url.url;
  const method = init?.method || 'GET';

  if (method.toUpperCase() !== 'GET') {
    // Evict all caches on any mutation/write
    Object.keys(fetchCache).forEach((k) => {
      delete fetchCache[k];
    });
    return fetch(url, init);
  }

  const now = Date.now();
  if (fetchCache[urlStr] && now - fetchCache[urlStr].timestamp < CACHE_TTL_MS) {
    return fetchCache[urlStr].data.clone();
  }

  const res = await fetch(url, init);
  if (res.ok) {
    fetchCache[urlStr] = {
      data: res.clone(),
      timestamp: now,
    };
  }
  return res;
};

function resolveIsOwner(c: any, currentUserId: number | null): boolean {
  if (!c) return false;
  const isOwnerVal = c.isOwner === true || c.isOwner === 'true';
  const roleVal =
    (c.role && String(c.role).toUpperCase() === 'OWNER') ||
    (c.currentUserRole &&
      String(c.currentUserRole).toUpperCase() === 'OWNER') ||
    (c.memberRole && String(c.memberRole).toUpperCase() === 'OWNER');
  const ownerObjVal =
    c.owner &&
    currentUserId &&
    (c.owner === true ||
      c.owner === 'true' ||
      Number(c.owner.id || c.owner) === currentUserId);
  return Boolean(isOwnerVal || roleVal || ownerObjVal);
}

/* ────────────────────────────────────────────────────────────────────────────
   useBackNavigation – intercept the browser/hardware back button so it
   closes the overlay instead of navigating away from the page.
   ──────────────────────────────────────────────────────────────────────────── */
function useBackNavigation(onClose: () => void) {
  const closedByUI = useRef(false);

  useEffect(() => {
    // Detail panels are already represented by /communities/:slug, so this
    // listener only closes local overlay state when the URL changes.

    const handlePop = () => {
      // Browser back was pressed — close the overlay
      onClose();
    };

    window.addEventListener('popstate', handlePop);
    return () => {
      window.removeEventListener('popstate', handlePop);
      // Keep browser history intact while closing the overlay.
      if (!closedByUI.current) return;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** Call this instead of onClose() when the user presses the UI close button */
  const closeViaUI = useCallback(() => {
    closedByUI.current = true;
    onClose();
  }, [onClose]);

  return { closeViaUI };
}

/* ────────────────────────────────────────────────────────────────────────────
   useResizableDrawer – allows dragging left edge to resize drawer width,
   plus toggling between standard width and maximized full width.
   ──────────────────────────────────────────────────────────────────────────── */
function useResizableDrawer(
  defaultWidth = 768,
  storageKey = 'govlyx_community_panel_width',
) {
  const [width, setWidth] = useState<number>(() => {
    try {
      const saved = localStorage.getItem(storageKey);
      if (saved) {
        const parsed = parseInt(saved, 10);
        if (!isNaN(parsed) && parsed >= 360) {
          return typeof window !== 'undefined'
            ? Math.min(parsed, window.innerWidth)
            : parsed;
        }
      }
    } catch {}
    return typeof window !== 'undefined'
      ? Math.min(defaultWidth, window.innerWidth)
      : defaultWidth;
  });

  const [isMaximized, setIsMaximized] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const dragStartX = useRef(0);
  const dragStartWidth = useRef(width);

  const handlePointerDown = useCallback(
    (e: React.PointerEvent) => {
      if (e.button !== 0 && e.pointerType === 'mouse') return;
      e.preventDefault();
      setIsDragging(true);
      dragStartX.current = e.clientX;
      dragStartWidth.current = isMaximized ? window.innerWidth : width;

      let rafId: number | null = null;

      const handlePointerMove = (moveEvent: PointerEvent) => {
        if (rafId !== null) return;
        rafId = requestAnimationFrame(() => {
          rafId = null;
          const deltaX = dragStartX.current - moveEvent.clientX; // moving left increases width
          const minW = Math.min(380, window.innerWidth);
          const maxW = window.innerWidth;
          const newWidth = Math.max(
            minW,
            Math.min(maxW, dragStartWidth.current + deltaX),
          );
          setWidth(newWidth);
          if (newWidth >= window.innerWidth - 20) {
            setIsMaximized(true);
          } else if (newWidth < window.innerWidth - 60) {
            setIsMaximized(false);
          }
        });
      };

      const handlePointerUp = () => {
        if (rafId !== null) {
          cancelAnimationFrame(rafId);
          rafId = null;
        }
        setIsDragging(false);
        window.removeEventListener('pointermove', handlePointerMove);
        window.removeEventListener('pointerup', handlePointerUp);
      };

      window.addEventListener('pointermove', handlePointerMove, {
        passive: true,
      });
      window.addEventListener('pointerup', handlePointerUp);
    },
    [width, isMaximized],
  );

  useEffect(() => {
    if (!isDragging && !isMaximized && width) {
      try {
        localStorage.setItem(storageKey, String(Math.round(width)));
      } catch {}
    }
  }, [width, isDragging, isMaximized, storageKey]);

  const toggleMaximize = useCallback(() => {
    setIsMaximized((prev) => !prev);
  }, []);

  const panelWidthStyle = isMaximized
    ? '100vw'
    : `${typeof window !== 'undefined' ? Math.min(width, window.innerWidth) : width}px`;

  return {
    panelWidthStyle,
    rawWidth: width,
    isMaximized,
    isDragging,
    handlePointerDown,
    toggleMaximize,
  };
}

/* ════════════════════════════════════════════════════════════════════════════
   TYPES & CONSTANTS
   ════════════════════════════════════════════════════════════════════════════ */
const Spin = ({ xs }: { xs?: boolean }) => (
  <span
    className={`loading loading-spinner ${xs ? 'loading-xs' : 'loading-sm'}`}
  />
);

export interface CommunityData {
  id: number;
  name: string;
  slug: string;
  description: string;
  privacy: 'PUBLIC' | 'PRIVATE' | 'SECRET';
  avatarUrl: string | null;
  coverImageUrl: string | null;
  category?: string | null;
  tags?: string | null;
  locationName?: string | null;
  memberCount: number;
  postCount: number;
  isMember?: boolean;
  isOwner?: boolean;
  isAdmin?: boolean;
  isModerator?: boolean;
  role?: 'ADMIN' | 'MODERATOR' | 'MEMBER' | 'OWNER' | string | null;
  currentUserRole?: 'ADMIN' | 'MODERATOR' | 'MEMBER' | 'OWNER' | string | null;
  memberRole?: string | null;
  hasPendingRequest?: boolean;
  allowMemberPosts?: boolean;
  requirePostApproval?: boolean;
  feedEligible?: boolean;
  healthScore?: number;
  momentumScore?: number;
  rankLabel?: string;
  cityRank?: number;
  percentile?: number;
  createdAt: string;
  isDeleted?: boolean;
  deletedAt?: string | null;
  scheduledDeletionDate?: string | null;
  deletionDueDate?: string | null;
}

interface CreateForm {
  name: string;
  description: string;
  category: string;
  tags: string;
  privacy: 'PUBLIC' | 'PRIVATE' | 'SECRET';
  avatarUrl: string;
  locationRestricted: boolean;
  allowMemberPosts: boolean;
  requirePostApproval: boolean;
}

/* ─── auth ──────────────────────────────────────────────────────────────── */
function getToken(): string | null {
  return getAuthToken();
}
function hdrs(): Record<string, string> {
  const t = getToken();
  const actorToken = getSessionActorToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (t) {
    headers.Authorization = `Bearer ${t}`;
  }
  if (actorToken) {
    headers['X-Actor-Token'] = actorToken;
  }
  return headers;
}

/* ─── local persistence for join requests (workaround for backend bug) ─── */
const getPendingLocal = (): string[] => {
  try {
    return JSON.parse(localStorage.getItem('pending_joins') || '[]').map(
      String,
    );
  } catch {
    return [];
  }
};
const addPendingLocal = (id: number | string) => {
  const p = getPendingLocal();
  const s = String(id);
  if (!p.includes(s))
    localStorage.setItem('pending_joins', JSON.stringify([...p, s]));
};
const removePendingLocal = (id: number | string) => {
  const p = getPendingLocal();
  const s = String(id);
  localStorage.setItem(
    'pending_joins',
    JSON.stringify(p.filter((x) => x !== s)),
  );
};

/* ─── types ─────────────────────────────────────────────────────────────── */
interface Post {
  id: number;
  content: string;
  authorUsername?: string;
  authorId?: number;
  authorProfileImage?: string;
  authorRole?: string;
  likeCount: number;
  commentCount: number;
  shareCount?: number;
  mediaUrls?: string[];
  imageUrl?: string;
  timeAgo?: string;
  createdAt?: string;
  isLikedByMe?: boolean;
  likedByMe?: boolean;
  isSavedByMe?: boolean;
  savedByMe?: boolean;
  isPoll?: boolean;
  [key: string]: any; // allow extra backend fields (e.g. poll data, feedReach)
}

interface JoinRequest {
  id: number;
  userId: number;
  username: string;
  profileImage: string | null;
  requestedAt: string;
  message?: string;
}

interface Member {
  userId: number;
  username: string;
  profileImage: string | null;
  role: 'ADMIN' | 'MODERATOR' | 'MEMBER';
  joinedAt: string;
  isMuted?: boolean;
  isBanned?: boolean;
}

interface HealthInsight {
  healthScore?: number;
  healthTier?: string;
  memberCount?: number;
  postCount?: number;
  totalCommentCount?: number;
  activeMembers?: number;
  feedReach?: number;
  components?: Record<string, number>;
}

/* ── Invite types (mirrors CommunityInviteDto.java) ─────────────────────── */
interface InviteResponse {
  id: number;
  token: string;
  inviteLink: string;
  inviteeUsername: string | null;
  inviteeProfileImage: string | null;
  inviterUsername: string | null;
  message: string | null;
  status: 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED';
  singleUse: boolean;
  useCount: number;
  createdAt: string;
  expiresAt: string;
  actionedAt: string | null;
}

interface InvitePreviewResponse {
  communityName: string;
  communitySlug: string;
  communityDescription: string | null;
  communityPrivacy: string;
  memberCount: number;
  inviterUsername: string | null;
  message: string | null;
  expiresAt: string;
  valid: boolean;
}

interface AcceptInviteResponse {
  communityId: number;
  communityName: string;
  communitySlug: string;
  joined: boolean;
  message: string;
}

interface UserSearchResult {
  id: number;
  username: string;
  profileImage: string | null;
  displayName?: string | null;
}

/* ─── constants ──────────────────────────────────────────────────────────── */
const CAT_PAGES: string[][] = [
  [
    'LOCAL_GOVERNANCE',
    'CIVIC_ISSUES',
    'EDUCATION',
    'HEALTH',
    'ENVIRONMENT',
    'INFRASTRUCTURE',
    'SAFETY',
  ],
  ['TECHNOLOGY', 'GAMING', 'FUN_ENTERTAINMENT', 'SPORTS', 'MUSIC', 'CULTURE'],
  [
    'EMPLOYMENT',
    'FOOD_DINING',
    'TRAVEL_OUTDOORS',
    'FITNESS_WELLNESS',
    'FINANCE_BUSINESS',
    'SCIENCE_RESEARCH',
  ],
  [
    'AUTOMOTIVE',
    'MEMES_HUMOR',
    'MOVIES_TV',
    'BOOKS_READING',
    'PETS_ANIMALS',
    'ART_DESIGN',
    'OTHER',
  ],
];
export const CATS = CAT_PAGES.flat();
const CAT_ICON: Record<string, React.ReactNode> = {
  LOCAL_GOVERNANCE: <Building2 size={18} />,
  CIVIC_ISSUES: <Construction size={18} />,
  EDUCATION: <GraduationCap size={18} />,
  HEALTH: <Stethoscope size={18} />,
  ENVIRONMENT: <Leaf size={18} />,
  INFRASTRUCTURE: <HardHat size={18} />,
  SAFETY: <ShieldCheck size={18} />,
  TECHNOLOGY: <Laptop size={18} />,
  GAMING: <Gamepad2 size={18} />,
  FUN_ENTERTAINMENT: <PartyPopper size={18} />,
  SPORTS: <Trophy size={18} />,
  MUSIC: <Music size={18} />,
  CULTURE: <Palette size={18} />,
  EMPLOYMENT: <Briefcase size={18} />,
  FOOD_DINING: <Utensils size={18} />,
  TRAVEL_OUTDOORS: <Compass size={18} />,
  FITNESS_WELLNESS: <Activity size={18} />,
  FINANCE_BUSINESS: <Coins size={18} />,
  SCIENCE_RESEARCH: <Atom size={18} />,
  AUTOMOTIVE: <Car size={18} />,
  MEMES_HUMOR: <Smile size={18} />,
  MOVIES_TV: <Film size={18} />,
  BOOKS_READING: <BookOpen size={18} />,
  PETS_ANIMALS: <Heart size={18} />,
  ART_DESIGN: <Palette size={18} />,
  OTHER: <Globe size={18} />,
};
const PRIV_ICON: Record<string, React.ReactNode> = {
  PUBLIC: <Globe size={18} />,
  PRIVATE: <Lock size={18} />,
  SECRET: <EyeOff size={18} />,
};
const PRIV_DESC = {
  PUBLIC: 'Anyone can join instantly',
  PRIVATE: 'Requires moderator approval',
  SECRET: 'Invite only — not discoverable',
};

function resolvePostApprovalFlag(raw: any, fallback = false): boolean {
  return Boolean(
    raw?.requirePostApproval ??
      raw?.requiresPostApproval ??
      raw?.postApprovalRequired ??
      raw?.approvePosts ??
      raw?.approvalRequired ??
      fallback,
  );
}

function withPostApprovalAliases<T extends { requirePostApproval: boolean }>(
  payload: T,
) {
  return {
    ...payload,
    requiresPostApproval: payload.requirePostApproval,
    postApprovalRequired: payload.requirePostApproval,
    approvePosts: payload.requirePostApproval,
    approvalRequired: payload.requirePostApproval,
  };
}

function getCommunityMomentum(c: CommunityData): number {
  return Math.round(c.healthScore ?? c.momentumScore ?? 0);
}

function getCommunityRankLabel(c: CommunityData): string {
  if (c.rankLabel) return c.rankLabel;
  if (c.cityRank && c.cityRank <= 3)
    return `#${c.cityRank} Most Active Community`;
  if (typeof c.percentile === 'number' && c.percentile <= 1)
    return 'Top 1% Overall';
  if (typeof c.percentile === 'number' && c.percentile <= 5)
    return 'Top 5% Overall';
  const score = getCommunityMomentum(c);
  if (score >= 90) return 'Top 1% Overall';
  if (score >= 70) return 'Top 5% Growing';
  return 'Rising Community';
}

function getCurrentUserId(): string | null {
  const t = getToken();
  if (!t) return null;
  try {
    const d: any = jwtDecode(t);
    return d.sub ? String(d.sub) : null;
  } catch {
    return null;
  }
}

let cachedCommunityRoles: Record<number, string> | null = null;
let currentRolesUserId: string | null = null;

function getCachedCommunityRoles(): Record<number, string> {
  const userId = getCurrentUserId() || 'anon';
  if (cachedCommunityRoles === null || currentRolesUserId !== userId) {
    currentRolesUserId = userId;
    try {
      cachedCommunityRoles = JSON.parse(
        localStorage.getItem(`govlyx_community_roles_${userId}`) || '{}',
      );
    } catch {
      cachedCommunityRoles = {};
    }
  }
  return cachedCommunityRoles || {};
}

function setCachedCommunityRole(id: number, role: string) {
  const userId = getCurrentUserId() || 'anon';
  try {
    const roles = getCachedCommunityRoles();
    roles[id] = role;
    cachedCommunityRoles = roles;
    localStorage.setItem(
      `govlyx_community_roles_${userId}`,
      JSON.stringify(roles),
    );
  } catch {}
}

let cachedLocalDeletions: Record<
  number,
  { deletedAt: string; scheduledDeletionDate: string }
> | null = null;
function getLocalDeletions(): Record<
  number,
  { deletedAt: string; scheduledDeletionDate: string }
> {
  if (cachedLocalDeletions === null) {
    try {
      cachedLocalDeletions = JSON.parse(
        localStorage.getItem('govlyx_deleted_communities') || '{}',
      );
    } catch {
      cachedLocalDeletions = {};
    }
  }
  return cachedLocalDeletions || {};
}

function isCommunityDeleted(c: CommunityData): boolean {
  const local = getLocalDeletions();
  const isLocallyDeleted = !!local[c.id];
  return (
    c.isDeleted === true ||
    !!c.deletedAt ||
    !!c.scheduledDeletionDate ||
    !!c.deletionDueDate ||
    isLocallyDeleted
  );
}

function getDeletionDaysLeft(c: CommunityData): number {
  const local = getLocalDeletions();
  const localData = local[c.id];
  const dateStr =
    c.scheduledDeletionDate ||
    c.deletionDueDate ||
    localData?.scheduledDeletionDate ||
    (c.deletedAt
      ? new Date(
          new Date(c.deletedAt).getTime() + 1 * 24 * 60 * 60 * 1000,
        ).toISOString()
      : localData?.deletedAt
        ? new Date(
            new Date(localData.deletedAt).getTime() + 1 * 24 * 60 * 60 * 1000,
          ).toISOString()
        : null);
  if (!dateStr) return 1;
  const diff = new Date(dateStr).getTime() - Date.now();
  const days = Math.ceil(diff / (1000 * 60 * 60 * 24));
  return Math.max(1, Math.min(1, days));
}

function highlight(text: string, query: string): React.ReactNode {
  const decoded = decodeHTML(text);
  if (!query.trim() || !decoded) return decoded;
  const esc = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const parts = decoded.split(new RegExp(`(${esc})`, 'gi'));
  return parts.map((p, i) =>
    p.toLowerCase() === query.toLowerCase() ? (
      <mark key={i} className="bg-red-500/20 text-red-500 rounded px-0.5">
        {p}
      </mark>
    ) : (
      p
    ),
  );
}

function avatar(name: string, image?: string | null) {
  if (image)
    return (
      <img src={image} className="w-8 h-8 rounded-full object-cover" alt="" />
    );
  return (
    <div className="w-8 h-8 rounded-full overflow-hidden border border-base-300 bg-base-200 shrink-0">
      <img
        src={`https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(name || '?')}`}
        alt="Avatar"
        className="w-full h-full object-cover"
      />
    </div>
  );
}

async function copyToClipboard(text: string, setCopied: (v: boolean) => void) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const el = document.createElement('textarea');
    el.value = text;
    document.body.appendChild(el);
    el.select();
    document.execCommand('copy');
    document.body.removeChild(el);
  }
  setCopied(true);
  setTimeout(() => setCopied(false), 2200);
}

function relTime(iso?: string | null): string {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

/* ════════════════════════════════════════════════════════════════════════════
   INVITE TAB — rendered inside AdminPanel for PRIVATE / SECRET communities
════════════════════════════════════════════════════════════════════════════ */
function InviteTab({
  communityId,
  privacy,
  communityName,
}: {
  communityId: number;
  privacy: 'PUBLIC' | 'PRIVATE' | 'SECRET';
  communityName: string;
}) {
  type Mode = 'send' | 'list';
  const [mode, setMode] = useState<Mode>('send');

  const [searchQ, setSearchQ] = useState('');
  const [suggestions, setSuggestions] = useState<UserSearchResult[]>([]);
  const [sugLoading, setSugLoading] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserSearchResult | null>(
    null,
  );
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [sendResult, setSendResult] = useState<InviteResponse | null>(null);
  const [sendError, setSendError] = useState<string | null>(null);
  const [copiedSend, setCopiedSend] = useState(false);
  const debRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [genLoading, setGenLoading] = useState(false);
  const [genResult, setGenResult] = useState<InviteResponse | null>(null);
  const [copiedGen, setCopiedGen] = useState(false);

  const [invites, setInvites] = useState<InviteResponse[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [cursor, setCursor] = useState<number | null>(null);
  const [revoking, setRevoking] = useState<number | null>(null);

  useEffect(() => {
    if (debRef.current) clearTimeout(debRef.current);
    const q = searchQ.trim();
    if (q.length < 2 || selectedUser) {
      setSuggestions([]);
      return;
    }
    debRef.current = setTimeout(async () => {
      setSugLoading(true);
      try {
        const res = await fetch(
          apiUrl(`/api/users/search?query=${encodeURIComponent(q)}&limit=5`),
          { headers: hdrs() },
        );
        if (!res.ok) throw new Error();
        const d = await res.json();
        const list: UserSearchResult[] =
          d?.data?.data ?? d?.data ?? d?.content ?? [];
        setSuggestions(Array.isArray(list) ? list : []);
      } catch {
        setSuggestions([]);
      } finally {
        setSugLoading(false);
      }
    }, 280);
    return () => {
      if (debRef.current) clearTimeout(debRef.current);
    };
  }, [searchQ, selectedUser]);

  async function handleSendInvite() {
    if (!selectedUser) return;
    setSending(true);
    setSendError(null);
    setSendResult(null);
    try {
      const res = await fetch(
        apiUrl(`/api/communities/${communityId}/invites`),
        {
          method: 'POST',
          headers: hdrs(),
          body: JSON.stringify({
            inviteeId: selectedUser.id,
            inviteeUsername: selectedUser.displayName || selectedUser.username,
            message: message.trim() || undefined,
          }),
        },
      );
      const d = await res.json().catch(() => ({}));
      if (!res.ok) {
        setSendError(d?.error || d?.message || `Error ${res.status}`);
        return;
      }
      const result: InviteResponse = d?.data ?? d;
      setSendResult(result);
      setSelectedUser(null);
      setSearchQ('');
      setMessage('');
      setSuggestions([]);
      if (mode === 'list') loadInvites(null, true);
    } catch {
      setSendError('Server unreachable. Please check your connection.');
    } finally {
      setSending(false);
    }
  }

  async function handleGenerateLink() {
    setGenLoading(true);
    setGenResult(null);
    try {
      const res = await fetch(
        apiUrl(`/api/communities/${communityId}/invites`),
        {
          method: 'POST',
          headers: hdrs(),
          body: JSON.stringify({}),
        },
      );
      const d = await res.json().catch(() => ({}));
      if (!res.ok) {
        showToast.error(d?.error || d?.message || 'Could not generate link.');
        return;
      }
      setGenResult(d?.data ?? d);
    } catch {
      showToast.error('Server unreachable. Please check your connection.');
    } finally {
      setGenLoading(false);
    }
  }

  const loadInvites = useCallback(
    async (cur: number | null, replace: boolean) => {
      setListLoading(true);
      try {
        const p = new URLSearchParams({ limit: '20' });
        if (cur) p.set('cursor', String(cur));
        const res = await fetch(
          apiUrl(`/api/communities/${communityId}/invites?${p}`),
          { headers: hdrs() },
        );
        if (!res.ok) throw new Error();
        const d = await res.json();
        const paged = d?.data;
        const rows: InviteResponse[] = paged?.data ?? paged?.content ?? [];
        setInvites((prev) => (replace ? rows : [...prev, ...rows]));
        setHasMore(paged?.hasMore ?? false);
        setCursor(paged?.nextCursor ?? null);
      } catch (err) {
        console.error('Failed to load invites:', err);
        if (replace) setInvites([]);
      } finally {
        setListLoading(false);
      }
    },
    [communityId],
  );

  useEffect(() => {
    if (mode === 'list') loadInvites(null, true);
  }, [mode, loadInvites]);

  const [revokeConfirmId, setRevokeConfirmId] = useState<number | null>(null);

  async function handleRevokeConfirm() {
    if (!revokeConfirmId) return;
    const inviteId = revokeConfirmId;
    setRevokeConfirmId(null);
    setRevoking(inviteId);
    try {
      const res = await fetch(
        apiUrl(`/api/communities/${communityId}/invites/${inviteId}`),
        {
          method: 'DELETE',
          headers: hdrs(),
        },
      );
      if (!res.ok) {
        showToast.error('Could not revoke.');
        return;
      }
      setInvites((prev) => prev.filter((i) => i.id !== inviteId));
    } catch {
      showToast.error('Server unreachable. Please check your connection.');
    } finally {
      setRevoking(null);
    }
  }

  async function handleRevoke(inviteId: number) {
    setRevokeConfirmId(inviteId);
  }

  return (
    <div className="p-5 space-y-6">
      <div className="rounded-xl border border-transparent px-4 py-3 text-sm flex items-start gap-3 bg-[#1D4ED8] text-white">
        <span className="text-xl shrink-0 mt-0.5">
          {privacy === 'SECRET' ? (
            <EyeOff size={20} />
          ) : privacy === 'PRIVATE' ? (
            <Lock size={20} />
          ) : (
            <Users size={20} />
          )}
        </span>
        <div>
          <p className="font-semibold">
            {privacy === 'SECRET'
              ? 'Secret Community'
              : privacy === 'PRIVATE'
                ? 'Private Community'
                : 'Public Community'}
          </p>
          <p className="opacity-70 text-xs mt-0.5">
            {privacy === 'SECRET'
              ? 'Invites are the only way to join this community.'
              : privacy === 'PRIVATE'
                ? 'Invited users skip the approval queue and join instantly.'
                : 'Generate invite links or invite specific users to join your public community.'}
          </p>
        </div>
      </div>

      <div className="flex gap-1 bg-base-200 rounded-xl p-1">
        {(['send', 'list'] as Mode[]).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            className={`flex-1 btn btn-xs rounded-lg transition-all ${mode === m ? 'bg-blue-700 text-white font-semibold border-none hover:bg-blue-800' : 'btn-ghost'}`}
          >
            {m === 'send' ? (
              <>
                <Mail size={12} className="mr-1" /> Send Invite
              </>
            ) : (
              <>
                <ClipboardCheck size={12} className="mr-1" /> Pending Invites
              </>
            )}
          </button>
        ))}
      </div>

      {mode === 'send' && (
        <div className="space-y-4">
          {sendResult && (
            <div className="rounded-xl border border-success/30 bg-success/10 p-4 space-y-3">
              <div className="flex items-center gap-2 text-success font-semibold text-sm">
                <CheckCircle2 size={16} />
                Invite sent to @{sendResult.inviteeUsername ?? 'user'}!
              </div>
              {sendResult.inviteLink && !sendResult.inviteeUsername && (
                <div className="space-y-1.5">
                  <p className="text-xs opacity-60">Share this link:</p>
                  <div className="flex items-center gap-2">
                    <code className="flex-1 text-xs bg-base-300 rounded-lg px-3 py-2 truncate block">
                      {sendResult.inviteLink}
                    </code>
                    <button
                      className={`btn btn-xs shrink-0 gap-1.5 ${copiedSend ? 'btn-success' : 'btn-outline'}`}
                      onClick={() =>
                        copyToClipboard(sendResult.inviteLink, setCopiedSend)
                      }
                    >
                      {copiedSend ? (
                        <>
                          <Check size={12} /> Copied
                        </>
                      ) : (
                        <>
                          <Copy size={12} /> Copy
                        </>
                      )}
                    </button>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 mt-2">
                    <a
                      href={`https://wa.me/?text=${encodeURIComponent(`Join "${communityName}" on Govlyx: ${sendResult.inviteLink}`)}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn btn-success btn-xs gap-2 text-[10px] px-2"
                    >
                      <svg
                        viewBox="0 0 24 24"
                        className="w-3.5 h-3.5 fill-white"
                      >
                        <path d="M.057 24l1.687-6.163c-1.041-1.804-1.588-3.849-1.587-5.946.003-6.556 5.338-11.891 11.893-11.891 3.181 0 6.167 1.24 8.413 3.488 2.245 2.248 3.481 5.236 3.48 8.414-.003 6.557-5.338 11.892-11.893 11.892-1.99 0-3.951-.5-5.688-1.448l-6.305 1.654zm6.597-3.807c1.676.995 3.276 1.591 5.392 1.591 5.52 0 10.002-4.48 10.002-10.002 0-5.522-4.482-10.002-10.002-10.002-5.521 0-10.002 4.48-10.002 10.002 0 2.223.731 4.303 1.99 6.001l-1.34 4.895 5.96-1.565zm10.73-7.502c-.29-.145-1.722-.85-1.988-.947-.266-.097-.46-.145-.654.145-.194.29-.752.947-.922 1.14-.17.194-.34.218-.63.073-.29-.145-1.226-.452-2.336-1.442-.865-.772-1.449-1.725-1.618-2.015-.17-.29-.018-.447.127-.591.132-.132.294-.345.441-.518.147-.173.196-.29.294-.485.098-.195.049-.364-.024-.509-.074-.145-.654-1.577-.897-2.16-.24-.582-.486-.503-.654-.513-.17-.009-.364-.01-.558-.01-.194 0-.51.073-.777.364-.266.291-1.018.995-1.018 2.428s1.042 2.81 1.188 3.004c.145.195 2.05 3.125 4.966 4.383 2.44 1.052 2.44.701 2.88.654.44-.047 1.411-.577 1.606-1.134.195-.557.195-1.034.137-1.131-.059-.096-.217-.145-.508-.29z" />
                      </svg>
                      WhatsApp
                    </a>
                    <a
                      href={`https://t.me/share/url?url=${encodeURIComponent(sendResult.inviteLink)}&text=${encodeURIComponent(`Hey! I invited you to join "${communityName}".`)}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn bg-[#0088cc] hover:bg-[#0077b5] text-white btn-xs gap-2 text-[10px] px-2 border-none"
                    >
                      <Send size={14} />
                      Telegram
                    </a>
                    <button
                      onClick={() =>
                        copyToClipboard(sendResult.inviteLink, setCopiedSend)
                      }
                      className="btn bg-gradient-to-tr from-[#f09433] via-[#dc2743] to-[#bc1888] hover:opacity-90 text-white btn-xs gap-2 text-[10px] px-2 border-none"
                    >
                      <Instagram size={14} />
                      Instagram
                    </button>
                  </div>
                </div>
              )}
              <button
                className="btn btn-ghost btn-xs w-full"
                onClick={() => setSendResult(null)}
              >
                Send another invite
              </button>
            </div>
          )}

          {sendError && (
            <div className="flex items-center gap-2 bg-error/10 border border-error/30 text-error text-sm rounded-xl px-4 py-2">
              <AlertTriangle size={15} className="shrink-0" />
              {sendError}
            </div>
          )}

          {!sendResult && (
            <>
              <div>
                <label className="block text-sm font-semibold mb-1.5">
                  Invite by username <span className="text-error">*</span>
                </label>

                {selectedUser ? (
                  <div className="flex items-center gap-3 rounded-xl border border-blue-700 bg-blue-700/10 p-3">
                    {avatar(selectedUser.username, selectedUser.profileImage)}
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold">
                        {selectedUser.displayName || selectedUser.username}
                      </p>
                    </div>
                    <button
                      className="btn btn-ghost btn-xs btn-circle text-error"
                      onClick={() => {
                        setSelectedUser(null);
                        setSearchQ('');
                      }}
                    >
                      <X size={14} />
                    </button>
                  </div>
                ) : (
                  <div className="relative">
                    <div className="relative">
                      <Search
                        className="absolute left-3 top-1/2 -translate-y-1/2 opacity-40 pointer-events-none"
                        size={14}
                      />
                      <input
                        type="text"
                        placeholder="Search username…"
                        className="input input-bordered w-full pl-8"
                        value={searchQ}
                        onChange={(e) => setSearchQ(e.target.value)}
                        autoComplete="off"
                      />
                      {sugLoading && (
                        <div className="absolute right-3 top-1/2 -translate-y-1/2">
                          <Spin xs />
                        </div>
                      )}
                    </div>

                    {suggestions.length > 0 && (
                      <div className="absolute left-0 right-0 top-full mt-1 z-[100] bg-base-100 border border-base-300 rounded-xl shadow-2xl overflow-hidden ring-1 ring-black/5">
                        {suggestions.map((u) => (
                          <button
                            key={u.id}
                            className="w-full flex items-center gap-3 px-4 py-3 hover:bg-base-200 text-left transition-colors border-b last:border-none border-base-300/50"
                            onMouseDown={(e) => {
                              e.preventDefault();
                              setSelectedUser(u);
                              setSearchQ('');
                              setSuggestions([]);
                            }}
                          >
                            {avatar(u.username, u.profileImage)}
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-semibold text-base-content">
                                {u.displayName || u.username}
                              </p>
                            </div>
                          </button>
                        ))}
                      </div>
                    )}

                    {searchQ.trim().length >= 2 &&
                      !sugLoading &&
                      suggestions.length === 0 && (
                        <div className="absolute left-0 right-0 top-full mt-1 z-[100] bg-base-100 border border-base-300 rounded-xl shadow-2xl p-5 text-center ring-1 ring-black/5">
                          <p className="text-sm font-medium text-base-content/60">
                            No users found for "{searchQ}"
                          </p>
                          <p className="text-xs text-base-content/40 mt-1">
                            Check the spelling or try a different name.
                          </p>
                        </div>
                      )}
                  </div>
                )}
              </div>

              <div>
                <label className="block text-sm font-semibold mb-1.5">
                  Personal message{' '}
                  <span className="opacity-40 font-normal">(optional)</span>
                </label>
                <textarea
                  className="textarea textarea-bordered w-full resize-none text-sm"
                  rows={2}
                  placeholder={`Hey! Join ${communityName}…`}
                  maxLength={300}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                />
                <p className="text-xs opacity-40 mt-1">{message.length}/300</p>
              </div>

              <button
                className="btn bg-blue-700 text-white font-semibold border-none hover:bg-blue-800 w-full gap-2"
                disabled={!selectedUser || sending}
                onClick={handleSendInvite}
              >
                {sending ? (
                  <>
                    <Spin xs /> Sending...
                  </>
                ) : (
                  <>
                    <Send size={16} /> Send Invite
                  </>
                )}
              </button>

              <div className="divider text-xs opacity-40 my-1">OR</div>

              <div className="rounded-xl border border-base-300 bg-base-200 p-4 space-y-3">
                <div className="flex items-start gap-3">
                  <span className="text-xl text-blue-700">
                    <Link size={20} />
                  </span>
                  <div>
                    <p className="text-sm font-semibold">
                      Generate Shareable Link
                    </p>
                    <p className="text-xs opacity-60 mt-0.5">
                      Anyone with this link can join until it expires (7 days).
                    </p>
                  </div>
                </div>

                {genResult?.inviteLink ? (
                  <div className="space-y-2">
                    <div className="flex items-center gap-2">
                      <code className="flex-1 text-xs bg-base-100 rounded-lg px-3 py-2 truncate block border border-base-300">
                        {genResult.inviteLink}
                      </code>
                      <button
                        className={`btn btn-xs shrink-0 ${copiedGen ? 'btn-success' : 'bg-blue-700 text-white font-semibold border-none hover:bg-blue-800'}`}
                        onClick={() =>
                          copyToClipboard(genResult!.inviteLink, setCopiedGen)
                        }
                      >
                        {copiedGen ? (
                          <>
                            <Check size={12} /> Copied
                          </>
                        ) : (
                          <>
                            <Copy size={12} /> Copy
                          </>
                        )}
                      </button>
                    </div>
                    {genResult.expiresAt && (
                      <p className="text-xs opacity-40">
                        Expires{' '}
                        {new Date(genResult.expiresAt).toLocaleDateString(
                          'en-IN',
                          { day: 'numeric', month: 'short', year: 'numeric' },
                        )}
                      </p>
                    )}
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 mt-2">
                      <a
                        href={`https://wa.me/?text=${encodeURIComponent(`Join "${communityName}" on Govlyx: ${genResult.inviteLink}`)}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="btn btn-success btn-sm gap-2 text-[10px] px-2"
                      >
                        <svg
                          viewBox="0 0 24 24"
                          fill="white"
                          className="w-4 h-4"
                        >
                          <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51h-.57c-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z" />
                        </svg>
                        WhatsApp
                      </a>
                      <a
                        href={`https://t.me/share/url?url=${encodeURIComponent(genResult.inviteLink)}&text=${encodeURIComponent(`Join "${communityName}" on Govlyx!`)}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="btn bg-[#0088cc] hover:bg-[#0077b5] text-white btn-sm gap-2 text-[10px] px-2 border-none"
                      >
                        <Send size={16} />
                        Telegram
                      </a>
                      <button
                        onClick={() =>
                          copyToClipboard(genResult.inviteLink, setCopiedGen)
                        }
                        className="btn bg-gradient-to-tr from-[#f09433] via-[#dc2743] to-[#bc1888] hover:opacity-90 text-white btn-sm gap-2 text-[10px] px-2 border-none"
                      >
                        <Instagram size={16} />
                        Instagram
                      </button>
                    </div>
                  </div>
                ) : (
                  <button
                    className="btn btn-outline btn-sm w-full gap-2"
                    disabled={genLoading}
                    onClick={handleGenerateLink}
                  >
                    {genLoading ? (
                      <>
                        <Spin xs /> Generating...
                      </>
                    ) : (
                      <>
                        <Link size={16} /> Generate Invite Link
                      </>
                    )}
                  </button>
                )}
              </div>
            </>
          )}
        </div>
      )}

      {mode === 'list' && (
        <div className="space-y-3">
          {listLoading && invites.length === 0 && (
            <div className="flex justify-center py-10">
              <Spin />
            </div>
          )}
          {!listLoading && invites.length === 0 && (
            <div className="text-center py-12 opacity-50 space-y-2">
              <div className="flex justify-center mb-2">
                <Inbox size={40} />
              </div>
              <p className="font-medium text-sm">No pending invites</p>
              <p className="text-xs">
                Switch to "Send Invite" to invite someone.
              </p>
            </div>
          )}
          {invites.map((inv) => (
            <InviteRow
              key={inv.id}
              invite={inv}
              revoking={revoking === inv.id}
              onRevoke={() => handleRevoke(inv.id)}
            />
          ))}
          {hasMore && !listLoading && (
            <button
              className="w-full py-2 text-sm text-blue-700 hover:opacity-70 inline-flex items-center justify-center gap-1"
              onClick={() => loadInvites(cursor, false)}
            >
              Load more <ArrowDown size={14} />
            </button>
          )}
        </div>
      )}
      <ConfirmModal
        isOpen={revokeConfirmId !== null}
        onClose={() => setRevokeConfirmId(null)}
        onConfirm={handleRevokeConfirm}
        title="Revoke Invite"
        message="Are you sure you want to revoke this invite? The recipient will no longer be able to use it to join."
        confirmLabel="Revoke"
        cancelLabel="Cancel"
        isDanger={true}
        isLoading={revoking !== null}
      />
    </div>
  );
}

function InviteRow({
  invite,
  revoking,
  onRevoke,
}: {
  invite: InviteResponse;
  revoking: boolean;
  onRevoke: () => void;
}) {
  const statusColor: Record<InviteResponse['status'], string> = {
    PENDING: 'badge-warning',
    ACCEPTED: 'badge-success',
    EXPIRED: 'badge-ghost',
    REVOKED: 'badge-error',
  };

  return (
    <div className="rounded-xl border border-base-300 bg-base-200 p-3 space-y-2">
      <div className="flex items-center gap-2.5 sm:gap-3">
        {avatar(invite.inviteeUsername ?? 'Link', invite.inviteeProfileImage)}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 flex-wrap">
            <p className="text-xs sm:text-sm font-semibold truncate">
              {invite.inviteeUsername
                ? `@${invite.inviteeUsername}`
                : 'Link invite'}
            </p>
            <span
              className={`badge badge-[9px] sm:badge-xs py-1.5 sm:py-2.5 ${statusColor[invite.status]}`}
            >
              {invite.status.toLowerCase()}
            </span>
            {!invite.singleUse && (
              <span className="badge badge-[9px] sm:badge-xs py-1.5 sm:py-2.5 badge-ghost">
                multi-use
              </span>
            )}
            {invite.useCount > 0 && (
              <span className="badge badge-[9px] sm:badge-xs py-1.5 sm:py-2.5 badge-ghost">
                {invite.useCount}× used
              </span>
            )}
          </div>
          <div className="flex flex-col sm:flex-row sm:items-center gap-0.5 sm:gap-1.5 text-[10px] sm:text-xs opacity-50 mt-1">
            <span>Sent {relTime(invite.createdAt)}</span>
            {invite.expiresAt && (
              <span className="flex items-center gap-1">
                <span className="hidden sm:inline opacity-60">·</span>
                <span>
                  Expires{' '}
                  {new Date(invite.expiresAt).toLocaleDateString('en-IN', {
                    day: 'numeric',
                    month: 'short',
                  })}
                </span>
              </span>
            )}
          </div>
        </div>
        {invite.status === 'PENDING' && (
          <button
            className="btn btn-ghost btn-xs text-error shrink-0 font-bold"
            disabled={revoking}
            onClick={onRevoke}
          >
            {revoking ? <Spin xs /> : 'Revoke'}
          </button>
        )}
      </div>
    </div>
  );
}

/* ════════════════════════════════════════════════════════════════════════════
   ACCEPT INVITE PAGE
════════════════════════════════════════════════════════════════════════════ */
export function AcceptInvitePage() {
  const { token = '' } = useParams<{ token: string }>();
  const navigate = useNavigate();

  type PageStatus = 'idle' | 'loading' | 'success' | 'already' | 'error';
  const [status, setStatus] = useState<PageStatus>('idle');
  const [preview, setPreview] = useState<InvitePreviewResponse | null>(null);
  const [previewLoading, setPreviewLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [accepted, setAccepted] = useState<AcceptInviteResponse | null>(null);

  useEffect(() => {
    if (!token) {
      setPreviewLoading(false);
      return;
    }
    (async () => {
      try {
        const res = await fetch(
          apiUrl(`/api/communities/invites/preview/${token}`),
        );
        const d = await res.json().catch(() => ({}));
        if (res.status === 404) {
          setErrorMsg(
            d?.error ||
              d?.message ||
              'This invite link is invalid or has expired.',
          );
          return;
        }
        if (!res.ok) {
          setErrorMsg(
            d?.error || d?.message || 'Could not load invite details.',
          );
          return;
        }
        const p: InvitePreviewResponse = d?.data ?? d;
        setPreview(p);
        if (!p || !p.valid) {
          setErrorMsg(
            d?.error ||
              d?.message ||
              'This invite link has expired or been revoked.',
          );
        }
      } catch (err) {
        console.error('Invite Preview Error:', err);
        setErrorMsg('Server unreachable. Please check your connection.');
      } finally {
        setPreviewLoading(false);
      }
    })();
  }, [token]);

  async function handleAccept() {
    if (!getToken()) {
      navigate(`/login?redirect=${encodeURIComponent(`/invite/${token}`)}`);
      return;
    }
    setStatus('loading');
    try {
      const res = await fetch(
        apiUrl(`/api/communities/invites/accept/${token}`),
        {
          method: 'POST',
          headers: hdrs(),
          body: JSON.stringify({}),
        },
      );
      const d = await res.json().catch(() => ({}));
      if (!res.ok) {
        setErrorMsg(d?.message || 'Could not accept invite.');
        setStatus('error');
        return;
      }
      const result: AcceptInviteResponse = d?.data ?? d;
      setAccepted(result);
      setStatus(
        result.message?.toLowerCase().includes('already')
          ? 'already'
          : 'success',
      );
    } catch {
      setErrorMsg('Server unreachable. Please check your connection.');
      setStatus('error');
    }
  }

  return (
    <div className="min-h-screen bg-base-200 flex items-center justify-center p-4">
      <div className="w-full max-w-sm bg-base-100 rounded-3xl border border-base-300 shadow-2xl overflow-hidden">
        <div className="h-24 bg-gradient-to-br from-[#1D4ED8]/30 via-[#1D4ED8]/10 to-base-200 flex items-center justify-center">
          <Home size={48} className="text-blue-700" />
        </div>

        <div className="px-6 pb-6 pt-4 space-y-4">
          {previewLoading && (
            <div className="flex justify-center py-8">
              <Spin />
            </div>
          )}

          {!previewLoading && errorMsg && status === 'idle' && (
            <div className="text-center space-y-3 py-4">
              <div className="flex justify-center text-error">
                <XCircle size={40} />
              </div>
              <p className="font-semibold">{errorMsg}</p>
              <p className="text-xs opacity-50">
                Ask the community admin for a fresh invite.
              </p>
            </div>
          )}

          {!previewLoading && preview && preview.valid && status === 'idle' && (
            <>
              <div className="text-center space-y-1">
                <h1 className="text-xl font-bold notranslate">
                  {preview.communityName}
                </h1>
                {preview.communityDescription && (
                  <p className="text-sm opacity-60 line-clamp-3">
                    {preview.communityDescription}
                  </p>
                )}
                <div className="flex items-center justify-center gap-3 text-xs opacity-50 pt-1">
                  <span className="flex items-center gap-1">
                    <Users size={12} />{' '}
                    {(preview.memberCount ?? 0).toLocaleString()} members
                  </span>
                  <span>·</span>
                  <span className="flex items-center gap-1">
                    {preview.communityPrivacy === 'SECRET' ? (
                      <>
                        <EyeOff size={12} /> Secret
                      </>
                    ) : (
                      <>
                        <Lock size={12} /> Private
                      </>
                    )}
                  </span>
                </div>
              </div>

              {preview.inviterUsername && (
                <div className="rounded-xl bg-base-200 px-4 py-3 text-sm text-center">
                  <span className="opacity-60">Invited by </span>
                  <span className="font-semibold">
                    @{preview.inviterUsername}
                  </span>
                </div>
              )}

              {preview.message && (
                <div className="rounded-xl border border-base-300 bg-base-200 px-4 py-3 text-sm italic opacity-80">
                  "{preview.message}"
                </div>
              )}

              {preview.expiresAt && (
                <p className="text-xs text-center opacity-40">
                  Invite expires:{' '}
                  {new Date(preview.expiresAt).toLocaleDateString('en-IN', {
                    day: 'numeric',
                    month: 'short',
                    year: 'numeric',
                  })}
                </p>
              )}

              {!getToken() && (
                <div className="rounded-xl bg-warning/10 border border-warning/30 px-3 py-2 text-xs text-warning text-center">
                  You need to log in first to accept this invite.
                </div>
              )}

              <button
                className="btn bg-blue-700 text-white font-semibold border-none hover:bg-blue-800 w-full"
                onClick={handleAccept}
              >
                <Rocket size={18} className="mr-2" /> Accept &amp; Join
                Community
              </button>

              <p className="text-xs text-center opacity-40">
                By joining, you agree to follow the community's rules.
              </p>
            </>
          )}

          {status === 'loading' && (
            <div className="text-center space-y-3 py-6">
              <Spin />
              <p className="text-sm opacity-60">Joining community…</p>
            </div>
          )}

          {(status === 'success' || status === 'already') && accepted && (
            <div className="text-center space-y-3 py-4">
              <div className="flex justify-center text-blue-700">
                <PartyPopper size={48} />
              </div>
              <h2 className="font-bold text-lg">
                {status === 'already' ? 'Already a member!' : "You're in!"}
              </h2>
              <p className="text-sm opacity-60">
                Welcome to <strong>{accepted.communityName}</strong>.
              </p>
              <button
                className="btn bg-blue-700 text-white font-semibold border-none hover:bg-blue-800 w-full"
                onClick={() => navigate('/communities')}
              >
                Open Community{' '}
                <ChevronLeft size={16} className="rotate-180 ml-1" />
              </button>
            </div>
          )}

          {status === 'error' && (
            <div className="text-center space-y-3 py-4">
              <div className="flex justify-center text-error">
                <AlertTriangle size={40} />
              </div>
              <p className="font-semibold text-error">{errorMsg}</p>
              <button
                className="btn btn-outline btn-sm"
                onClick={() => {
                  setStatus('idle');
                  setErrorMsg('');
                }}
              >
                Try Again
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

/* ════════════════════════════════════════════════════════════════════════════
   ADMIN PANEL
════════════════════════════════════════════════════════════════════════════ */
type AdminTab =
  | 'requests'
  | 'members'
  | 'settings'
  | 'insights'
  | 'invites'
  | 'post-approvals';

function AdminPanel({
  community,
  onClose,
  onCommunityUpdated,
  onMembershipChange,
  initialTab = 'requests',
  inline = false,
}: {
  community: CommunityData;
  onClose: () => void;
  onCommunityUpdated: (c: CommunityData) => void;
  onMembershipChange?: (
    id: number,
    isMember: boolean,
    delta: number,
    hasPendingRequest?: boolean,
  ) => void;
  initialTab?: AdminTab;
  inline?: boolean;
}) {
  const { closeViaUI } = useBackNavigation(onClose);
  const {
    panelWidthStyle: adminPanelWidthStyle,
    isMaximized: isAdminMaximized,
    isDragging: isAdminDragging,
    handlePointerDown: handleAdminPointerDown,
    toggleMaximize: toggleAdminMaximize,
  } = useResizableDrawer(768, 'govlyx_community_admin_panel_width');
  const [c, setC] = useState(community);

  // Current logged in user decoded from JWT
  const currentLoggedInUser: { id?: number; username?: string } | null =
    (() => {
      const t = getToken();
      if (!t) return null;
      try {
        const d: any = jwtDecode(t);
        return {
          id: Number(d.id || d.userId || d.sub),
          username: d.username || d.sub || '',
        };
      } catch {
        return null;
      }
    })();

  const rawRole = (
    c.currentUserRole ||
    c.role ||
    c.memberRole ||
    (c.isOwner
      ? 'OWNER'
      : c.isModerator
        ? 'MODERATOR'
        : c.isAdmin
          ? 'ADMIN'
          : 'MEMBER')
  )
    .toString()
    .toUpperCase();

  const isOwner =
    rawRole === 'OWNER' ||
    (c.isOwner === true && rawRole !== 'MODERATOR' && rawRole !== 'MEMBER');
  const isAdmin = isOwner || rawRole === 'ADMIN';
  const isModerator =
    !isAdmin && (rawRole === 'MODERATOR' || c.isModerator === true);

  const effectiveInitialTab: AdminTab =
    !isAdmin && initialTab === 'settings' ? 'requests' : initialTab;
  const [tab, setTab] = useState<AdminTab>(effectiveInitialTab);
  const [panelAnimDone, setPanelAnimDone] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [memberConfirm, setMemberConfirm] = useState<{
    isOpen: boolean;
    userId: number;
    action: 'remove' | 'ban';
    username: string;
  } | null>(null);

  const [requests, setRequests] = useState<JoinRequest[]>([]);
  const [reqLoading, setReqLoading] = useState(false);
  const [reqCursor, setReqCursor] = useState<number | null>(null);
  const [reqHasMore, setReqHasMore] = useState(false);
  const [actingReq, setActingReq] = useState<number | null>(null);

  const [pendingPosts, setPendingPosts] = useState<any[]>([]);
  const [pendingLoading, setPendingLoading] = useState(false);
  const [actingPost, setActingPost] = useState<number | null>(null);

  const loadPendingPosts = useCallback(async () => {
    setPendingLoading(true);
    try {
      const d = await communityService.getPendingPosts(c.id);
      const list =
        d?.data?.content ?? d?.data?.data ?? d?.data ?? d?.content ?? d ?? [];
      setPendingPosts(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error('Error loading pending posts:', err);
      // Fallback manual fetches if new service fails
      try {
        let res = await fetch(
          apiUrl(`/api/communities/${c.id}/posts?status=PENDING`),
          { headers: hdrs() },
        );
        if (!res.ok) {
          res = await fetch(
            apiUrl(`/api/communities/${c.id}/posts?pending=true`),
            { headers: hdrs() },
          );
        }
        if (res.ok) {
          const d = await res.json();
          const list =
            d?.data?.content ??
            d?.data?.data ??
            d?.data ??
            d?.content ??
            d ??
            [];
          setPendingPosts(Array.isArray(list) ? list : []);
          return;
        }
      } catch {}
      setPendingPosts([]);
    } finally {
      setPendingLoading(false);
    }
  }, [c.id]);

  async function reviewPost(postId: number, approve: boolean) {
    setActingPost(postId);
    try {
      if (approve) {
        await communityService.approvePost(c.id, postId);
      } else {
        await communityService.rejectPost(c.id, postId);
      }
      setPendingPosts((prev) => prev.filter((p) => p.id !== postId));
      showToast.success(
        approve ? 'Post approved successfully!' : 'Post rejected successfully!',
      );
    } catch (err) {
      // Fallback manual fetches if new service fails
      try {
        let res;
        if (approve) {
          res = await fetch(apiUrl(`/api/posts/${postId}/approve`), {
            method: 'POST',
            headers: hdrs(),
            body: JSON.stringify({}),
          });
        } else {
          res = await fetch(apiUrl(`/api/posts/${postId}/reject`), {
            method: 'POST',
            headers: hdrs(),
            body: JSON.stringify({}),
          });
          if (!res.ok) {
            res = await fetch(apiUrl(`/api/social-posts/${postId}`), {
              method: 'DELETE',
              headers: hdrs(),
            });
          }
        }
        if (res && res.ok) {
          setPendingPosts((prev) => prev.filter((p) => p.id !== postId));
          showToast.success(
            approve
              ? 'Post approved successfully!'
              : 'Post rejected successfully!',
          );
          return;
        }
      } catch {}
      showToast.error('An error occurred while reviewing the post.');
    } finally {
      setActingPost(null);
    }
  }

  const loadRequests = useCallback(
    async (cur: number | null, replace: boolean) => {
      setReqLoading(true);
      try {
        const p = new URLSearchParams({ limit: '20' });
        if (cur) p.set('cursor', String(cur));
        const res = await fetch(
          apiUrl(`/api/communities/${c.id}/join-requests?${p}`),
          { headers: hdrs() },
        );
        if (!res.ok) throw new Error();
        const d = await res.json();
        const paged = d?.data?.data ?? d?.data ?? d;
        const rawRows: any[] = Array.isArray(paged)
          ? paged
          : Array.isArray(paged?.content)
            ? paged.content
            : Array.isArray(paged?.data)
              ? paged.data
              : [];
        const rows: JoinRequest[] = rawRows.map((r: any) => ({
          id: r.id ?? r.requestId ?? r.joinRequestId,
          userId: r.userId ?? r.user?.id ?? r.id,
          username:
            r.username ??
            r.user?.username ??
            r.user?.displayName ??
            r.displayName ??
            'User',
          profileImage:
            r.profileImage ??
            r.user?.profileImage ??
            r.user?.avatarUrl ??
            r.avatarUrl ??
            null,
          requestedAt: r.requestedAt || r.createdAt || new Date().toISOString(),
          message: r.message ?? r.note ?? '',
        }));
        setRequests((prev) => (replace ? rows : [...prev, ...rows]));
        setReqHasMore(
          Boolean(
            paged?.hasMore || (paged?.totalPages && paged.totalPages > 1),
          ),
        );
        setReqCursor(paged?.nextCursor ?? null);
      } catch (err) {
        console.error('[loadRequests] Failed to load join requests:', err);
        if (replace) setRequests([]);
      } finally {
        setReqLoading(false);
      }
    },
    [c.id],
  );

  async function reviewRequest(reqId: number, approve: boolean) {
    setActingReq(reqId);
    try {
      const url = apiUrl(`/api/communities/${c.id}/join-requests/${reqId}`);
      let ok = false;
      try {
        const res = await fetch(url, {
          method: 'PUT',
          headers: hdrs(),
          body: JSON.stringify({
            status: approve ? 'APPROVED' : 'REJECTED',
            approve,
          }),
        });
        if (res.ok) ok = true;
      } catch {}

      if (!ok) {
        try {
          const action = approve ? 'approve' : 'reject';
          const res = await fetch(
            apiUrl(`/api/communities/${c.id}/join-requests/${reqId}/${action}`),
            {
              method: 'POST',
              headers: hdrs(),
              body: JSON.stringify({}),
            },
          );
          if (res.ok) ok = true;
        } catch {}
      }

      if (!ok) {
        showToast.error('Could not update join request.');
        return;
      }

      setRequests((p) => p.filter((r) => r.id !== reqId));
      if (approve) {
        setC((p) => ({ ...p, memberCount: p.memberCount + 1 }));
        onMembershipChange?.(c.id, true, 1, false);
        showToast.success('Join request approved!');
      } else {
        showToast.info('Join request rejected.');
      }
    } catch (err: any) {
      console.error('[reviewRequest] error:', err);
      showToast.error('Server unreachable – please check your connection.');
    } finally {
      setActingReq(null);
    }
  }

  const [members, setMembers] = useState<Member[]>([]);
  const [memLoading, setMemLoading] = useState(false);
  const [memCursor, setMemCursor] = useState<number | null>(null);
  const [memHasMore, setMemHasMore] = useState(false);
  const [actingMem, setActingMem] = useState<number | null>(null);
  const [memSearch, setMemSearch] = useState('');

  const loadMembers = useCallback(
    async (cur: number | null, replace: boolean) => {
      setMemLoading(true);
      try {
        const p = new URLSearchParams({ limit: '30' });
        if (cur) p.set('cursor', String(cur));
        const res = await fetch(
          apiUrl(`/api/communities/${c.id}/members?${p}`),
          { headers: hdrs() },
        );
        if (!res.ok) throw new Error();
        const d = await res.json();
        const paged = d?.data ?? d;
        const rawRows: any[] = Array.isArray(paged?.data)
          ? paged.data
          : (paged?.content ?? (Array.isArray(paged) ? paged : []));
        const rows: Member[] = rawRows.map((m: any) => ({
          ...m,
          userId: m.userId || m.id,
          role: m.memberRole || m.role || 'MEMBER',
          joinedAt: m.joinedAt || m.createdAt || new Date().toISOString(),
        }));
        setMembers((prev) => (replace ? rows : [...prev, ...rows]));
        setMemHasMore(paged.hasMore ?? false);
        setMemCursor(paged.nextCursor ?? null);
      } catch (err) {
        console.error('[loadMembers] Failed to load members:', err);
        if (replace) setMembers([]);
      } finally {
        setMemLoading(false);
      }
    },
    [c.id],
  );

  async function memberAction(
    userId: number,
    action:
      | 'remove'
      | 'mute'
      | 'unmute'
      | 'ban'
      | 'unban'
      | 'makeAdmin'
      | 'makeMod'
      | 'makeMember',
    targetUsername?: string,
  ) {
    if (
      isModerator &&
      (action === 'makeAdmin' ||
        action === 'makeMod' ||
        action === 'makeMember')
    ) {
      showToast.error('Only community admins can change member roles.');
      return;
    }
    setActingMem(userId);
    const userLabel = targetUsername ? `@${targetUsername}` : 'Member';
    try {
      let res: Response;
      if (action === 'remove') {
        res = await fetch(
          apiUrl(`/api/communities/${c.id}/members/${userId}`),
          { method: 'DELETE', headers: hdrs() },
        );
      } else if (action === 'mute') {
        res = await fetch(
          apiUrl(`/api/communities/${c.id}/members/${userId}/mute`),
          { method: 'PUT', headers: hdrs() },
        );
      } else if (action === 'unmute') {
        res = await fetch(
          apiUrl(`/api/communities/${c.id}/members/${userId}/mute`),
          { method: 'DELETE', headers: hdrs() },
        );
      } else if (action === 'ban') {
        res = await fetch(
          apiUrl(`/api/communities/${c.id}/members/${userId}/ban`),
          { method: 'PUT', headers: hdrs(), body: JSON.stringify({}) },
        );
      } else if (action === 'unban') {
        res = await fetch(
          apiUrl(`/api/communities/${c.id}/members/${userId}/ban`),
          { method: 'DELETE', headers: hdrs() },
        );
      } else {
        const roleMap = {
          makeAdmin: 'ADMIN',
          makeMod: 'MODERATOR',
          makeMember: 'MEMBER',
        };
        res = await fetch(
          apiUrl(`/api/communities/${c.id}/members/${userId}/role`),
          {
            method: 'PUT',
            headers: hdrs(),
            body: JSON.stringify({
              newRole: roleMap[action as keyof typeof roleMap],
            }),
          },
        );
      }

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        showToast.error(errData?.message || errData?.error || 'Action failed.');
        return;
      }

      // Notify admin immediately
      if (action === 'makeAdmin') {
        showToast.success(`Promoted ${userLabel} to Admin`);
      } else if (action === 'makeMod') {
        showToast.success(`Promoted ${userLabel} to Moderator`);
      } else if (action === 'makeMember') {
        showToast.success(`Changed ${userLabel}'s role to Member`);
      } else if (action === 'ban') {
        showToast.success(`Banned ${userLabel} from the community`);
      } else if (action === 'unban') {
        showToast.success(`Unbanned ${userLabel}`);
      } else if (action === 'mute') {
        showToast.success(`Muted ${userLabel}`);
      } else if (action === 'unmute') {
        showToast.success(`Unmuted ${userLabel}`);
      } else if (action === 'remove') {
        showToast.success(`Removed ${userLabel} from the community`);
      }

      if (action === 'remove' || action === 'ban') {
        setMembers((p) => p.filter((m) => m.userId !== userId));
        if (action === 'remove') {
          setC((p) => ({ ...p, memberCount: Math.max(0, p.memberCount - 1) }));
          onMembershipChange?.(c.id, true, -1);
        }
      } else {
        setMembers((p) =>
          p.map((m) => {
            if (m.userId !== userId) return m;
            if (action === 'mute') return { ...m, isMuted: true };
            if (action === 'unmute') return { ...m, isMuted: false };
            if (action === 'unban') return { ...m, isBanned: false };
            if (action === 'makeAdmin') return { ...m, role: 'ADMIN' as const };
            if (action === 'makeMod')
              return { ...m, role: 'MODERATOR' as const };
            if (action === 'makeMember')
              return { ...m, role: 'MEMBER' as const };
            return m;
          }),
        );
      }
    } catch {
      showToast.error('Server unreachable. Please check your connection.');
    } finally {
      setActingMem(null);
    }
  }

  const [settingsForm, setSettingsForm] = useState({
    name: c.name,
    description: c.description,
    privacy: c.privacy,
    avatarUrl: c.avatarUrl || '',
    coverImageUrl: c.coverImageUrl || '',
    allowMemberPosts: c.allowMemberPosts ?? true,
    requirePostApproval: resolvePostApprovalFlag(c),
    feedEligible: c.feedEligible ?? false,
  });
  const [settingsBusy, setSettingsBusy] = useState(false);
  const [settingsMsg, setSettingsMsg] = useState<string | null>(null);
  const [archiveBusy, setArchiveBusy] = useState(false);
  const [uploadingImg, setUploadingImg] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploadingCover, setUploadingCover] = useState(false);
  const coverFileInputRef = useRef<HTMLInputElement>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editorTarget, setEditorTarget] = useState<'avatar' | 'cover'>(
    'avatar',
  );
  const [editorImageSrc, setEditorImageSrc] = useState<string | null>(null);

  async function handleCoverUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    // Validation
    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type)) {
      showToast.error('Please upload a JPEG, PNG, or WebP image.');
      return;
    }
    const maxSize = 5 * 1024 * 1024; // 5MB
    if (file.size > maxSize) {
      showToast.error('File size exceeds 5MB limit.');
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      setEditorTarget('cover');
      setEditorImageSrc(reader.result as string);
      setEditorOpen(true);
    };
    reader.readAsDataURL(file);
  }

  async function handleImageUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    // Validation
    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type)) {
      showToast.error('Please upload a JPEG, PNG, or WebP image.');
      return;
    }
    const maxSize = 5 * 1024 * 1024; // 5MB
    if (file.size > maxSize) {
      showToast.error('File size exceeds 5MB limit.');
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      setEditorTarget('avatar');
      setEditorImageSrc(reader.result as string);
      setEditorOpen(true);
    };
    reader.readAsDataURL(file);
  }

  async function handleEditorSave(editedBlob: Blob) {
    const isCover = editorTarget === 'cover';
    if (isCover) {
      setUploadingCover(true);
    } else {
      setUploadingImg(true);
    }
    setEditorOpen(false);
    try {
      const fileName = isCover ? 'community_cover.jpg' : 'community_avatar.jpg';
      const file = new File([editedBlob], fileName, { type: 'image/jpeg' });
      const data = await communityService.uploadCommunityImage(
        c.id,
        file,
        isCover ? 'cover' : 'avatar',
      );
      if (isCover) {
        const updatedUrl =
          data?.coverImageUrl ||
          data?.data?.coverImageUrl ||
          data?.data?.data?.coverImageUrl;
        if (updatedUrl) {
          setSettingsForm((prev) => ({ ...prev, coverImageUrl: updatedUrl }));
          setC((prev) => ({ ...prev, coverImageUrl: updatedUrl }));
          onCommunityUpdated({
            ...c,
            coverImageUrl: updatedUrl,
            isOwner: true,
            isMember: true,
          });
          showToast.success('Community cover image updated successfully!');
        } else {
          showToast.error(
            'Upload succeeded but no cover image URL was returned.',
          );
        }
      } else {
        const updatedUrl =
          data?.avatarUrl ||
          data?.data?.avatarUrl ||
          data?.data?.data?.avatarUrl;
        if (updatedUrl) {
          setSettingsForm((prev) => ({ ...prev, avatarUrl: updatedUrl }));
          setC((prev) => ({ ...prev, avatarUrl: updatedUrl }));
          onCommunityUpdated({
            ...c,
            avatarUrl: updatedUrl,
            isOwner: true,
            isMember: true,
          });
          showToast.success('Community image updated successfully!');
        } else {
          showToast.error('Upload succeeded but no image URL was returned.');
        }
      }
    } catch (err: any) {
      console.error(err);
      showToast.error(
        err.response?.data?.message ||
          err.message ||
          `Failed to upload ${isCover ? 'cover image' : 'image'}.`,
      );
    } finally {
      if (isCover) {
        setUploadingCover(false);
        if (coverFileInputRef.current) coverFileInputRef.current.value = '';
      } else {
        setUploadingImg(false);
        if (fileInputRef.current) fileInputRef.current.value = '';
      }
      setEditorImageSrc(null);
    }
  }

  async function saveSettings() {
    setSettingsBusy(true);
    setSettingsMsg(null);
    try {
      const settingsPayload = withPostApprovalAliases(settingsForm);
      const res = await fetch(apiUrl(`/api/communities/${c.id}`), {
        method: 'PUT',
        headers: hdrs(),
        body: JSON.stringify(settingsPayload),
      });
      if (!res.ok) {
        const d = await res.json().catch(() => ({}));
        const errorMsg = d?.message || 'Save failed.';
        setSettingsMsg('❌ ' + errorMsg);
        showToast.error(errorMsg);
        return;
      }
      const d = await res.json();
      const raw = d?.data ?? d;
      const updated: CommunityData = {
        ...c,
        ...raw,
        requirePostApproval: resolvePostApprovalFlag(
          raw,
          settingsForm.requirePostApproval,
        ),
        isOwner: true,
        isMember: true,
      };
      setC(updated);
      onCommunityUpdated(updated);
      setSettingsMsg('✅ Saved successfully.');
      showToast.success('Changes saved successfully!');
    } catch {
      setSettingsMsg('❌ Server unreachable.');
      showToast.error('Server unreachable.');
    } finally {
      setSettingsBusy(false);
    }
  }

  async function archiveCommunity() {
    if (!confirm(`Archive "${c.name}"? This cannot be undone easily.`)) return;
    setArchiveBusy(true);
    try {
      const res = await fetch(apiUrl(`/api/communities/${c.id}/archive`), {
        method: 'DELETE',
        headers: hdrs(),
      });
      if (!res.ok) {
        showToast.error('Archive failed.');
        return;
      }
      showToast.success('Community archived.');
      onClose();
    } catch {
      showToast.error('Server unreachable. Please check your connection.');
    } finally {
      setArchiveBusy(false);
    }
  }

  async function deleteCommunity() {
    setDeleteBusy(true);
    try {
      const res = await fetch(apiUrl(`/api/communities/${c.id}`), {
        method: 'DELETE',
        headers: hdrs(),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        showToast.error(err?.message || 'Delete failed.');
        return;
      }
      showToast.success('Community scheduled for deletion.');
      const updated: CommunityData = {
        ...c,
        isDeleted: true,
        deletedAt: new Date().toISOString(),
        scheduledDeletionDate: new Date(
          Date.now() + 1 * 24 * 60 * 60 * 1000,
        ).toISOString(),
      };

      try {
        const local = getLocalDeletions();
        local[c.id] = {
          deletedAt: updated.deletedAt || new Date().toISOString(),
          scheduledDeletionDate:
            updated.scheduledDeletionDate ||
            new Date(Date.now() + 1 * 24 * 60 * 60 * 1000).toISOString(),
        };
        localStorage.setItem(
          'govlyx_deleted_communities',
          JSON.stringify(local),
        );
        cachedLocalDeletions = local;
      } catch (err) {
        console.error('Failed to store local deletion status', err);
      }

      setC(updated);
      onCommunityUpdated(updated);
      setShowDeleteConfirm(false);
      onClose();
    } catch {
      showToast.error('Server unreachable. Please check your connection.');
    } finally {
      setDeleteBusy(false);
    }
  }

  const [insights, setInsights] = useState<HealthInsight | null>(null);
  const [insightsLoading, setInsightsLoading] = useState(false);
  const [recalcBusy, setRecalcBusy] = useState(false);

  const loadInsights = useCallback(async () => {
    setInsightsLoading(true);
    try {
      const res = await fetch(apiUrl(`/api/communities/${c.id}/insights`), {
        headers: hdrs(),
      });
      if (!res.ok) throw new Error();
      const d = await res.json();
      setInsights(d?.data ?? d);
    } catch {
      setInsights(null);
    } finally {
      setInsightsLoading(false);
    }
  }, [c.id]);

  async function triggerRecalc() {
    setRecalcBusy(true);
    try {
      await fetch(apiUrl(`/api/communities/${c.id}/health/recalculate`), {
        method: 'POST',
        headers: hdrs(),
      });
      setTimeout(() => loadInsights(), 1500);
    } catch {
    } finally {
      setRecalcBusy(false);
    }
  }

  useEffect(() => {
    loadPendingPosts();
  }, [loadPendingPosts]);

  useEffect(() => {
    if (tab === 'requests') loadRequests(null, true);
    if (tab === 'post-approvals') loadPendingPosts();
    if (tab === 'members') loadMembers(null, true);
    if (tab === 'insights') loadInsights();
  }, [tab, loadRequests, loadMembers, loadInsights, loadPendingPosts]);

  // Pre-load requests & approvals on mount to show badge counts immediately
  // (while avoiding duplicate calls if they match the initial tab)
  useEffect(() => {
    if (tab !== 'requests') {
      loadRequests(null, true);
    }
    if (tab !== 'post-approvals') {
      loadPendingPosts();
    }
  }, [loadRequests, loadPendingPosts, tab]);

  const filteredMembers = memSearch.trim()
    ? members.filter((m) =>
        m.username.toLowerCase().includes(memSearch.toLowerCase()),
      )
    : members;

  const allTabs: {
    key: AdminTab;
    label: string;
    icon: React.ReactNode;
    badge?: number;
    adminOnly?: boolean;
  }[] = [
    {
      key: 'requests',
      label: 'Requests',
      icon: <Inbox size={14} />,
      badge: requests.length > 0 ? requests.length : undefined,
    },
    {
      key: 'post-approvals',
      label: 'Approvals',
      icon: <ClipboardCheck size={14} />,
      badge: pendingPosts.length > 0 ? pendingPosts.length : undefined,
    },
    { key: 'members', label: 'Members', icon: <Users size={14} /> },
    { key: 'invites', label: 'Invites', icon: <Link size={14} /> },
    {
      key: 'settings',
      label: 'Settings',
      icon: <Settings size={14} />,
      adminOnly: true,
    },
  ];

  const TABS = allTabs.filter((t) => !t.adminOnly || isAdmin);

  const roleColor = (role: Member['role'] | string) => {
    if (role === 'OWNER' || role === 'ADMIN') {
      return 'bg-amber-400 text-black font-bold border-none';
    }
    if (role === 'MODERATOR') {
      return 'bg-rose-400 text-white font-bold border-none';
    }
    return 'bg-emerald-600 text-white font-bold border-none';
  };

  const panelContent = (
    <div className="flex-1 flex flex-col h-full bg-base-100 relative">
      {!inline && (
        <div className="shrink-0 px-4 py-3 border-b border-base-300 bg-base-100">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-1">
              <button
                onClick={closeViaUI}
                className="btn btn-ghost btn-sm gap-1 -ml-2 shrink-0"
              >
                <ChevronLeft size={18} /> Back
              </button>
              <button
                type="button"
                onClick={toggleAdminMaximize}
                title={
                  isAdminMaximized
                    ? 'Decrease to standard width'
                    : 'Expand to full width'
                }
                className="btn btn-ghost btn-sm btn-circle text-base-content/75 hover:text-base-content hover:bg-base-200 transition-colors"
              >
                {isAdminMaximized ? (
                  <Minimize2 size={16} />
                ) : (
                  <Maximize2 size={16} />
                )}
              </button>
            </div>
            <div className="text-right">
              <div className="flex items-center justify-end gap-2">
                <span
                  className={`badge border-none badge-xs font-bold px-2.5 py-0.5 inline-flex items-center ${isModerator ? 'bg-rose-400 text-white' : 'bg-amber-400 text-black'}`}
                >
                  {isModerator ? 'Moderator' : 'Admin'}
                </span>
                <h2 className="font-bold text-sm truncate max-w-[160px] notranslate">
                  {c.name}
                </h2>
              </div>
              <p className="text-xs opacity-50 mt-0.5">
                {c.memberCount} members · {c.privacy.toLowerCase()} community
              </p>
            </div>
          </div>
          <div className="flex items-center justify-between mt-2 pt-2 border-t border-base-300/40">
            {/* Mobile View: Simple badge overlay showing current active tab name */}
            <div className="flex items-center justify-between w-full md:hidden">
              <span className="text-[10px] sm:text-xs font-semibold uppercase tracking-wider text-base-content/60">
                Viewing
              </span>
              <span className="text-[10px] sm:text-xs font-bold uppercase bg-[#1D4ED8] text-white border border-transparent px-3 py-1 rounded-full flex items-center gap-1.5 shrink-0 shadow-md">
                {TABS.find((t) => t.key === tab)?.icon}
                {TABS.find((t) => t.key === tab)?.label}
              </span>
            </div>

            {/* PC/Desktop View: Nice horizontal navigation bar to switch between tabs directly */}
            <div className="hidden md:flex items-center gap-2 overflow-x-auto w-full scrollbar-hide py-0.5">
              {TABS.map((t) => {
                const isSelected = tab === t.key;
                return (
                  <button
                    key={t.key}
                    onClick={() => setTab(t.key)}
                    className={`btn btn-xs rounded-xl border-none flex items-center gap-1.5 transition-all py-1.5 px-3 h-auto min-h-0 ${
                      isSelected
                        ? 'bg-[#1D4ED8] text-white shadow-sm font-bold'
                        : 'bg-base-200 hover:bg-base-300 text-base-content/70 hover:text-base-content'
                    }`}
                  >
                    {t.icon}
                    <span className="text-[11px] font-semibold">{t.label}</span>
                    {t.badge && (
                      <span
                        className={`text-[8.5px] font-black rounded-full px-1.5 py-0.5 leading-none shrink-0 ${
                          isSelected
                            ? 'bg-white text-[#1D4ED8]'
                            : 'bg-error text-error-content'
                        }`}
                      >
                        {t.badge}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}

      <div className="flex-1 overflow-y-auto min-h-0 pb-24">
        {tab === 'requests' && (
          <div className="p-4 space-y-3">
            {reqLoading && requests.length === 0 && (
              <div className="flex justify-center py-10">
                <Spin />
              </div>
            )}
            {!reqLoading && requests.length === 0 && (
              <div className="text-center py-14 opacity-50 space-y-2">
                <div className="flex justify-center text-success mb-2">
                  <CheckCircle2 size={40} />
                </div>
                <p className="font-medium">No pending requests</p>
                <p className="text-xs">
                  {c.privacy === 'PUBLIC'
                    ? 'Public community — members join instantly.'
                    : 'All caught up!'}
                </p>
              </div>
            )}
            {requests.map((req) => (
              <div
                key={req.id}
                className="rounded-xl border border-base-300 bg-base-200 p-3 flex items-start gap-3"
              >
                {avatar(req.username, req.profileImage)}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold">@{req.username}</p>
                  {req.message && (
                    <p className="text-xs opacity-60 mt-0.5 line-clamp-2">
                      "{req.message}"
                    </p>
                  )}
                  <p className="text-xs opacity-40 mt-0.5">
                    {new Date(req.requestedAt).toLocaleDateString('en-IN', {
                      day: 'numeric',
                      month: 'short',
                      year: 'numeric',
                    })}
                  </p>
                </div>
                <div className="flex flex-col gap-1.5 shrink-0">
                  <button
                    className="btn btn-success btn-xs gap-1"
                    disabled={actingReq === req.id}
                    onClick={() => reviewRequest(req.id, true)}
                  >
                    {actingReq === req.id ? (
                      <Spin xs />
                    ) : (
                      <>
                        <Check size={12} /> Accept
                      </>
                    )}
                  </button>
                  <button
                    className="btn btn-ghost btn-xs btn-outline gap-1"
                    disabled={actingReq === req.id}
                    onClick={() => reviewRequest(req.id, false)}
                  >
                    <X size={12} /> Reject
                  </button>
                </div>
              </div>
            ))}
            {reqHasMore && !reqLoading && (
              <button
                className="w-full py-2 text-sm text-blue-700 inline-flex items-center justify-center gap-1"
                onClick={() => loadRequests(reqCursor, false)}
              >
                Load more <ArrowDown size={14} />
              </button>
            )}
          </div>
        )}

        {tab === 'post-approvals' && (
          <div className="p-4 space-y-3">
            {pendingLoading && pendingPosts.length === 0 && (
              <div className="flex justify-center py-10">
                <Spin />
              </div>
            )}
            {!pendingLoading && pendingPosts.length === 0 && (
              <div className="text-center py-14 opacity-50 space-y-2">
                <div className="flex justify-center text-success mb-2">
                  <CheckCircle2 size={40} />
                </div>
                <p className="font-medium">No pending posts</p>
                <p className="text-xs">All posts are live!</p>
              </div>
            )}
            {pendingPosts.map((post) => (
              <div
                key={post.id}
                className="rounded-xl border border-base-300 bg-base-200 p-3.5 space-y-2 flex flex-col"
              >
                <div className="flex items-center gap-3">
                  {avatar(post.username, post.userProfileImage)}
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold">@{post.username}</p>
                    <p className="text-[10px] opacity-40 mt-0.5">
                      {post.timeAgo ?? 'pending review'}
                    </p>
                  </div>
                </div>
                {post.content && (
                  <p className="text-xs text-base-content/80 whitespace-pre-wrap">
                    {post.content}
                  </p>
                )}
                {post.mediaUrls && post.mediaUrls.length > 0 && (
                  <div className="rounded-lg overflow-hidden border border-base-300 max-h-32 bg-black/5 flex items-center justify-center">
                    <img
                      src={post.mediaUrls[0]}
                      alt="Post media"
                      className="object-contain max-h-32 w-full"
                    />
                  </div>
                )}
                <div className="flex gap-2 justify-end pt-2 border-t border-base-content/5 mt-2">
                  <button
                    className="btn btn-ghost btn-xs btn-outline gap-1"
                    disabled={actingPost === post.id}
                    onClick={() => reviewPost(post.id, false)}
                  >
                    <X size={12} /> Reject
                  </button>
                  <button
                    className="btn btn-success btn-xs gap-1"
                    disabled={actingPost === post.id}
                    onClick={() => reviewPost(post.id, true)}
                  >
                    {actingPost === post.id ? (
                      <Spin xs />
                    ) : (
                      <>
                        <Check size={12} /> Approve
                      </>
                    )}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {tab === 'members' && (
          <div className="p-4 space-y-3">
            <div className="relative">
              <Search
                className="absolute left-3 top-1/2 -translate-y-1/2 opacity-40 pointer-events-none"
                size={14}
              />
              <input
                type="text"
                placeholder="Filter members…"
                className="input input-bordered input-sm w-full pl-8"
                value={memSearch}
                onChange={(e) => setMemSearch(e.target.value)}
              />
            </div>
            {memLoading && members.length === 0 && (
              <div className="flex justify-center py-10">
                <Spin />
              </div>
            )}
            {!memLoading && members.length === 0 && (
              <div className="text-center py-10 opacity-50">
                <p>No members found.</p>
              </div>
            )}
            {filteredMembers.map((m) => {
              const targetRole = (m.role || (m as any).memberRole || 'MEMBER')
                .toString()
                .toUpperCase();
              const targetIsOwner = targetRole === 'OWNER';
              const targetIsAdmin = targetRole === 'ADMIN' || targetIsOwner;
              const targetIsMod = targetRole === 'MODERATOR';
              const targetIsStaff = targetIsAdmin || targetIsMod;

              const isSelf = Boolean(
                (currentLoggedInUser?.id &&
                  (m.userId === currentLoggedInUser.id ||
                    (m as any).id === currentLoggedInUser.id)) ||
                  (currentLoggedInUser?.username &&
                    m.username?.toLowerCase() ===
                      currentLoggedInUser.username?.toLowerCase()),
              );

              // Permissions logic:
              // 1. Self cannot be modified/moderated
              // 2. Moderators cannot moderate other staff (Admins, Owners, other Moderators)
              // 3. Admins cannot moderate the community Owner
              const canModerateTarget =
                !isSelf &&
                (isAdmin ? !targetIsOwner : !isModerator || !targetIsStaff);

              return (
                <div
                  key={m.userId}
                  className="rounded-xl border border-base-300 bg-base-200 p-3"
                >
                  <div className="flex items-center gap-3">
                    {avatar(m.username, m.profileImage)}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-1.5 flex-wrap">
                        <p className="text-sm font-semibold">@{m.username}</p>
                        <span className={`badge badge-xs ${roleColor(m.role)}`}>
                          {m.role}
                        </span>
                        {m.isMuted && (
                          <span className="badge badge-xs badge-warning flex items-center gap-1">
                            <VolumeX size={10} /> Muted
                          </span>
                        )}
                        {m.isBanned && (
                          <span className="badge badge-xs badge-error flex items-center gap-1">
                            <Ban size={10} /> Banned
                          </span>
                        )}
                        {isSelf && (
                          <span className="badge badge-xs badge-ghost opacity-60">
                            You
                          </span>
                        )}
                      </div>
                      <p className="text-xs opacity-40">
                        Joined{' '}
                        {new Date(m.joinedAt).toLocaleDateString('en-IN', {
                          month: 'short',
                          year: 'numeric',
                        })}
                      </p>
                    </div>
                    {canModerateTarget && (
                      <div className="dropdown dropdown-end">
                        <button
                          tabIndex={0}
                          className="btn btn-ghost btn-xs btn-circle"
                          disabled={actingMem === m.userId}
                        >
                          {actingMem === m.userId ? (
                            <Spin xs />
                          ) : (
                            <MoreVertical size={14} />
                          )}
                        </button>
                        <ul
                          tabIndex={0}
                          className="dropdown-content menu menu-sm bg-base-100 rounded-xl border border-base-300 shadow-lg z-50 w-44 p-1"
                        >
                          {/* Role management is ONLY for Admins, never for Moderators */}
                          {isAdmin && (
                            <>
                              {targetRole !== 'ADMIN' && (
                                <li>
                                  <button
                                    onClick={() =>
                                      memberAction(
                                        m.userId,
                                        'makeAdmin',
                                        m.username,
                                      )
                                    }
                                  >
                                    <Crown size={14} /> Make Admin
                                  </button>
                                </li>
                              )}
                              {targetRole !== 'MODERATOR' && (
                                <li>
                                  <button
                                    onClick={() =>
                                      memberAction(
                                        m.userId,
                                        'makeMod',
                                        m.username,
                                      )
                                    }
                                  >
                                    <Shield size={14} /> Make Moderator
                                  </button>
                                </li>
                              )}
                              {targetRole !== 'MEMBER' && (
                                <li>
                                  <button
                                    onClick={() =>
                                      memberAction(
                                        m.userId,
                                        'makeMember',
                                        m.username,
                                      )
                                    }
                                  >
                                    <User size={14} /> Make Member
                                  </button>
                                </li>
                              )}
                              <li className="menu-title">
                                <span className="text-xs opacity-40">
                                  Actions
                                </span>
                              </li>
                            </>
                          )}
                          {!m.isMuted ? (
                            <li>
                              <button
                                onClick={() =>
                                  memberAction(m.userId, 'mute', m.username)
                                }
                              >
                                <VolumeX size={14} /> Mute
                              </button>
                            </li>
                          ) : (
                            <li>
                              <button
                                onClick={() =>
                                  memberAction(m.userId, 'unmute', m.username)
                                }
                              >
                                <Volume2 size={14} /> Unmute
                              </button>
                            </li>
                          )}
                          {!m.isBanned ? (
                            <li>
                              <button
                                className="text-error"
                                onClick={() =>
                                  setMemberConfirm({
                                    isOpen: true,
                                    userId: m.userId,
                                    action: 'ban',
                                    username: m.username,
                                  })
                                }
                              >
                                <Ban size={14} /> Ban
                              </button>
                            </li>
                          ) : (
                            <li>
                              <button
                                onClick={() =>
                                  memberAction(m.userId, 'unban', m.username)
                                }
                              >
                                <CheckCircle2 size={14} /> Unban
                              </button>
                            </li>
                          )}
                          <li>
                            <button
                              className="text-error"
                              onClick={() =>
                                setMemberConfirm({
                                  isOpen: true,
                                  userId: m.userId,
                                  action: 'remove',
                                  username: m.username,
                                })
                              }
                            >
                              <Trash2 size={14} /> Remove
                            </button>
                          </li>
                        </ul>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
            {memHasMore && !memLoading && (
              <button
                className="w-full py-2 text-sm text-blue-700 inline-flex items-center justify-center gap-1"
                onClick={() => loadMembers(memCursor, false)}
              >
                Load more <ArrowDown size={14} />
              </button>
            )}
          </div>
        )}

        {tab === 'invites' && (
          <InviteTab
            communityId={c.id}
            privacy={c.privacy as 'PUBLIC' | 'PRIVATE' | 'SECRET'}
            communityName={c.name}
          />
        )}

        {tab === 'settings' &&
          (!isAdmin ? (
            <div className="p-8 text-center opacity-70 space-y-2">
              <Shield size={36} className="mx-auto text-base-content/40 mb-2" />
              <p className="font-semibold text-sm">Restricted Access</p>
              <p className="text-xs">
                Only community admins can modify community settings and manage
                the danger zone.
              </p>
            </div>
          ) : (
            <div className="p-4 space-y-4">
              {settingsMsg && (
                <div
                  className={`text-sm rounded-xl px-4 py-2 border flex items-center gap-2 shadow-sm ${settingsMsg.startsWith('✅') ? 'bg-success/10 border-success/30 text-success' : 'bg-error/10 border-error/30 text-error'}`}
                >
                  {settingsMsg.startsWith('✅') ? (
                    <CheckCircle2 size={16} className="shrink-0 text-success" />
                  ) : (
                    <AlertTriangle size={16} className="shrink-0 text-error" />
                  )}
                  <span className="text-base-content font-medium">
                    {settingsMsg.replace(/^[✅❌]\s*/, '')}
                  </span>
                </div>
              )}
              <div>
                <label className="block text-sm font-semibold mb-1">
                  Community Name
                </label>
                <input
                  className="input input-bordered w-full"
                  maxLength={60}
                  value={settingsForm.name}
                  onChange={(e) =>
                    setSettingsForm((f) => ({ ...f, name: e.target.value }))
                  }
                />
              </div>
              <div>
                <label className="block text-sm font-semibold mb-1.5">
                  Community Avatar
                </label>
                <div className="flex items-center gap-4 p-3 bg-base-200/50 rounded-2xl border border-base-300">
                  <div className="shrink-0 w-16 h-16 rounded-full overflow-hidden border border-base-300 shadow-sm relative group bg-base-100">
                    <img
                      src={
                        settingsForm.avatarUrl ||
                        `https://robohash.org/${encodeURIComponent(settingsForm.name || 'avatar')}`
                      }
                      alt="Avatar"
                      className="w-full h-full object-cover"
                    />
                    {uploadingImg && (
                      <div className="absolute inset-0 bg-black/60 flex items-center justify-center">
                        <Spin xs />
                      </div>
                    )}
                  </div>
                  <div className="flex-1 space-y-1.5">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => fileInputRef.current?.click()}
                        disabled={uploadingImg}
                        className="btn btn-sm btn-outline rounded-xl font-bold uppercase tracking-wider text-[10px] gap-1.5"
                      >
                        <Upload size={12} />{' '}
                        {uploadingImg ? 'Uploading...' : 'Upload Pic'}
                      </button>
                      {settingsForm.avatarUrl && (
                        <>
                          <button
                            type="button"
                            onClick={() => {
                              setEditorTarget('avatar');
                              setEditorImageSrc(settingsForm.avatarUrl);
                              setEditorOpen(true);
                            }}
                            disabled={uploadingImg}
                            className="btn btn-sm bg-base-300 hover:bg-base-400 text-base-content rounded-xl font-bold uppercase tracking-wider text-[10px] gap-1.5 cursor-pointer"
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            onClick={() =>
                              setSettingsForm((f) => ({ ...f, avatarUrl: '' }))
                            }
                            className="btn btn-sm btn-ghost text-error rounded-xl font-bold uppercase tracking-wider text-[10px] gap-1.5"
                          >
                            <Trash2 size={12} /> Remove
                          </button>
                        </>
                      )}
                    </div>
                    <p className="text-[10px] opacity-50">
                      JPG, PNG, or WebP. Max 5MB.
                    </p>
                  </div>
                </div>
                <input
                  type="file"
                  ref={fileInputRef}
                  onChange={handleImageUpload}
                  accept="image/jpeg,image/png,image/webp"
                  className="hidden"
                />
              </div>

              <div>
                <label className="block text-sm font-semibold mb-1.5">
                  Community Cover Image
                </label>
                <div className="flex flex-col gap-3 p-3 bg-base-200/50 rounded-2xl border border-base-300">
                  <div className="w-full h-24 rounded-xl overflow-hidden border border-base-300 shadow-sm relative group bg-base-100">
                    {settingsForm.coverImageUrl ? (
                      <img
                        src={settingsForm.coverImageUrl}
                        alt="Cover"
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <div className="w-full h-full bg-gradient-to-r from-blue-700/10 to-blue-500/5" />
                    )}
                    {uploadingCover && (
                      <div className="absolute inset-0 bg-black/60 flex items-center justify-center">
                        <Spin xs />
                      </div>
                    )}
                  </div>
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => coverFileInputRef.current?.click()}
                        disabled={uploadingCover}
                        className="btn btn-sm btn-outline rounded-xl font-bold uppercase tracking-wider text-[10px] gap-1.5"
                      >
                        <Upload size={12} />{' '}
                        {uploadingCover ? 'Uploading...' : 'Upload Cover'}
                      </button>
                      {settingsForm.coverImageUrl && (
                        <>
                          <button
                            type="button"
                            onClick={() => {
                              setEditorTarget('cover');
                              setEditorImageSrc(settingsForm.coverImageUrl);
                              setEditorOpen(true);
                            }}
                            disabled={uploadingCover}
                            className="btn btn-sm bg-base-300 hover:bg-base-400 text-base-content rounded-xl font-bold uppercase tracking-wider text-[10px] gap-1.5 cursor-pointer"
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            onClick={() =>
                              setSettingsForm((f) => ({
                                ...f,
                                coverImageUrl: '',
                              }))
                            }
                            className="btn btn-sm btn-ghost text-error rounded-xl font-bold uppercase tracking-wider text-[10px] gap-1.5"
                          >
                            <Trash2 size={12} /> Remove
                          </button>
                        </>
                      )}
                    </div>
                    <p className="text-[10px] opacity-50">
                      JPG, PNG, or WebP. Max 5MB.
                    </p>
                  </div>
                </div>
                <input
                  type="file"
                  ref={coverFileInputRef}
                  onChange={handleCoverUpload}
                  accept="image/jpeg,image/png,image/webp"
                  className="hidden"
                />
              </div>
              <div>
                <label className="block text-sm font-semibold mb-1">
                  Description
                </label>
                <textarea
                  className="textarea textarea-bordered w-full resize-none"
                  rows={3}
                  maxLength={500}
                  value={settingsForm.description}
                  onChange={(e) =>
                    setSettingsForm((f) => ({
                      ...f,
                      description: e.target.value,
                    }))
                  }
                />
              </div>
              <div>
                <label className="block text-sm font-semibold mb-2">
                  Privacy
                </label>
                <div className="space-y-2">
                  {(['PUBLIC', 'PRIVATE', 'SECRET'] as const).map((p) => (
                    <button
                      key={p}
                      type="button"
                      onClick={() =>
                        setSettingsForm((f) => ({ ...f, privacy: p }))
                      }
                      className={`w-full flex items-center gap-3 rounded-xl border p-3 text-left transition-all ${settingsForm.privacy === p ? 'border-[#1D4ED8] bg-[#1D4ED8] text-white shadow-[0_4px_16px_rgba(29,78,216,0.35)]' : 'border-base-300 hover:border-base-400 text-base-content'}`}
                    >
                      <span
                        className={`text-xl ${settingsForm.privacy === p ? 'text-white' : ''}`}
                      >
                        {PRIV_ICON[p]}
                      </span>
                      <div className="flex-1">
                        <p
                          className={`text-sm font-semibold ${settingsForm.privacy === p ? 'text-white' : 'text-base-content'}`}
                        >
                          {p.charAt(0) + p.slice(1).toLowerCase()}
                        </p>
                        <p
                          className={`text-xs ${settingsForm.privacy === p ? 'text-white/80' : 'text-base-content/70'}`}
                        >
                          {PRIV_DESC[p]}
                        </p>
                      </div>
                      {settingsForm.privacy === p && (
                        <span className="text-white">
                          <Check size={16} />
                        </span>
                      )}
                    </button>
                  ))}
                </div>
              </div>
              <div className="space-y-2">
                <label className="block text-sm font-semibold">
                  Permissions
                </label>
                {[
                  {
                    key: 'allowMemberPosts',
                    label: 'Members can post',
                    desc: 'Any member can create posts',
                  },
                  {
                    key: 'requirePostApproval',
                    label: 'Approve posts',
                    desc: 'Moderator must review posts before they go live',
                  },
                  {
                    key: 'feedEligible',
                    label: 'Show posts in main feed',
                    desc: 'High-engagement posts surface in the main feed',
                  },
                ].map(({ key, label, desc }) => (
                  <label
                    key={key}
                    className="flex items-center justify-between gap-3 rounded-xl border border-base-300 p-3 cursor-pointer hover:border-base-400"
                  >
                    <div>
                      <p className="text-sm font-medium">{label}</p>
                      <p className="text-xs opacity-80">{desc}</p>
                    </div>
                    <input
                      type="checkbox"
                      className="toggle toggle-sm govlyx-red-toggle"
                      checked={
                        settingsForm[
                          key as keyof typeof settingsForm
                        ] as boolean
                      }
                      onChange={(e) =>
                        setSettingsForm((f) => ({
                          ...f,
                          [key]: e.target.checked,
                        }))
                      }
                    />
                  </label>
                ))}
              </div>
              <button
                className="btn bg-blue-700 text-white font-semibold border-none hover:bg-blue-800 w-full"
                disabled={settingsBusy}
                onClick={saveSettings}
              >
                {settingsBusy ? (
                  <>
                    <Spin xs /> Saving…
                  </>
                ) : (
                  <>
                    <Save size={18} className="mr-2" /> Save Changes
                  </>
                )}
              </button>
              <div className="rounded-xl border border-error/30 bg-error/5 p-4 space-y-2 mt-4">
                <p className="text-sm font-semibold text-error flex items-center gap-2">
                  <AlertTriangle size={16} /> Danger Zone
                </p>
                <p className="text-xs opacity-60">
                  Archiving or deleting restricts community interactions.
                  Deletion has a 2-day recovery window.
                </p>
                <div className="grid grid-cols-2 gap-2.5">
                  <button
                    className="btn btn-error btn-outline btn-sm w-full"
                    disabled={archiveBusy}
                    onClick={archiveCommunity}
                  >
                    {archiveBusy ? (
                      <Spin xs />
                    ) : (
                      <>
                        <Archive size={16} className="mr-1" /> Archive
                      </>
                    )}
                  </button>
                  <button
                    className="btn btn-error btn-sm w-full text-white"
                    disabled={deleteBusy}
                    onClick={() => setShowDeleteConfirm(true)}
                  >
                    {deleteBusy ? (
                      <Spin xs />
                    ) : (
                      <>
                        <Trash2 size={16} className="mr-1" /> Delete
                      </>
                    )}
                  </button>
                </div>
              </div>
            </div>
          ))}

        {tab === 'insights' && (
          <div className="p-4 space-y-4">
            {insightsLoading && (
              <div className="flex justify-center py-10">
                <Spin />
              </div>
            )}
            {!insightsLoading && !insights && (
              <div className="text-center py-10 opacity-50 space-y-2">
                <div className="flex justify-center text-base-content/20 mb-2">
                  <BarChart3 size={48} strokeWidth={1.5} />
                </div>
                <p className="text-sm">Could not load insights.</p>
                <button
                  className="btn btn-sm btn-outline !opacity-100"
                  onClick={loadInsights}
                >
                  Retry
                </button>
              </div>
            )}
            {!insightsLoading && insights && (
              <>
                <div className="rounded-2xl border border-base-300 bg-gradient-to-br from-[#1D4ED8]/10 to-base-200 p-5 text-center">
                  <p className="text-xs opacity-50 uppercase tracking-widest mb-1">
                    Health Score
                  </p>
                  <p className="text-5xl font-black text-blue-700">
                    {insights.healthScore != null
                      ? Math.round(insights.healthScore)
                      : '—'}
                  </p>
                  {insights.healthTier && (
                    <p className="text-sm font-semibold mt-1 opacity-70">
                      {insights.healthTier}
                    </p>
                  )}
                </div>
                <div className="grid grid-cols-2 gap-3">
                  {[
                    {
                      icon: <Users size={14} className="text-blue-600" />,
                      label: 'Members',
                      value: insights.memberCount,
                    },
                    {
                      icon: <FileText size={14} className="text-emerald-600" />,
                      label: 'Posts',
                      value: insights.postCount,
                    },
                    {
                      icon: (
                        <MessageSquare size={14} className="text-purple-600" />
                      ),
                      label: 'Comments',
                      value: insights.totalCommentCount,
                    },
                    {
                      icon: <Activity size={14} className="text-orange-600" />,
                      label: 'Active',
                      value: insights.activeMembers,
                    },
                    {
                      icon: <Radio size={14} className="text-rose-600" />,
                      label: 'Feed Reach',
                      value: insights.feedReach,
                    },
                  ]
                    .filter((item) => item.value != null)
                    .map((item) => (
                      <div
                        key={item.label}
                        className="rounded-xl border border-base-300 bg-base-200 p-3 text-center flex flex-col items-center justify-center"
                      >
                        <div className="flex items-center gap-1.5 opacity-60 mb-1">
                          {item.icon}
                          <span className="text-[10px] font-bold uppercase tracking-wider">
                            {item.label}
                          </span>
                        </div>
                        <p className="text-lg font-bold">
                          {Number(item.value).toLocaleString()}
                        </p>
                      </div>
                    ))}
                </div>
                {insights.components &&
                  Object.keys(insights.components).length > 0 && (
                    <div className="rounded-xl border border-base-300 bg-base-200 p-4 space-y-2">
                      <p className="text-xs font-semibold opacity-50 uppercase tracking-widest">
                        Score Breakdown
                      </p>
                      {Object.entries(insights.components).map(([key, val]) => (
                        <div key={key}>
                          <div className="flex justify-between text-xs mb-1">
                            <span className="opacity-70 capitalize">
                              {key.replace(/_/g, ' ')}
                            </span>
                            <span className="font-semibold">
                              {Math.round(val)}
                            </span>
                          </div>
                          <div className="w-full bg-base-300 rounded-full h-1.5">
                            <div
                              className="bg-blue-700 h-1.5 rounded-full transition-all"
                              style={{ width: `${Math.min(100, val)}%` }}
                            />
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                <button
                  className="btn btn-outline btn-sm w-full gap-2"
                  disabled={recalcBusy}
                  onClick={triggerRecalc}
                >
                  {recalcBusy ? (
                    <>
                      <Spin xs /> Recalculating…
                    </>
                  ) : (
                    <>
                      <RefreshCw size={14} /> Recalculate Health Score
                    </>
                  )}
                </button>
              </>
            )}
          </div>
        )}
        {editorImageSrc && (
          <ImageEditorModal
            isOpen={editorOpen}
            cropShape={editorTarget === 'cover' ? 'rect' : 'circle'}
            title={
              editorTarget === 'cover'
                ? 'Edit Cover Photo'
                : 'Edit Avatar Photo'
            }
            onClose={() => {
              setEditorOpen(false);
              setEditorImageSrc(null);
              if (fileInputRef.current) fileInputRef.current.value = '';
              if (coverFileInputRef.current)
                coverFileInputRef.current.value = '';
            }}
            imageSrc={editorImageSrc}
            onSave={handleEditorSave}
          />
        )}
      </div>
      {/* Closes the flex-1 container inside panelContent */}
    </div>
  );

  // FAB rendered separately — injected into the drawer container (fixed inset-0 > relative h-full)
  // so `absolute bottom-6 left-1/2` is relative to the panel, not the scrollable content
  const fabSection = (
    <>
      {/* Backdrop overlay */}
      {menuOpen && (
        <div
          className="fixed inset-0 bg-black/40 backdrop-blur-sm z-[115] transition-all duration-300"
          onClick={() => setMenuOpen(false)}
        />
      )}
      {/* Floating FAB — fixed to bottom-right of screen, always visible */}
      {/* pointer-events-none on wrapper so hidden options never block scrolling */}
      <div className="fixed bottom-6 right-6 z-[120] select-none flex flex-col items-end pointer-events-none">
        {/* Options stacked above */}
        <div className="flex flex-col-reverse items-end gap-3 mb-3">
          {TABS.map((t, i) => {
            const isSelected = tab === t.key;
            return (
              <div
                key={t.key}
                className="flex items-center justify-end gap-2.5 transition-all duration-300"
                style={{
                  transform: menuOpen
                    ? 'translateY(0) scale(1)'
                    : 'translateY(16px) scale(0.7)',
                  opacity: menuOpen ? 1 : 0,
                  pointerEvents: menuOpen ? 'auto' : 'none',
                  transitionDelay: menuOpen ? `${i * 40}ms` : '0ms',
                }}
              >
                <span
                  onClick={() => {
                    setTab(t.key);
                    setMenuOpen(false);
                  }}
                  className={`text-xs font-bold px-3 py-1.5 rounded-xl shadow-lg border backdrop-blur-md cursor-pointer whitespace-nowrap transition-all duration-200 active:scale-95 ${
                    isSelected
                      ? 'bg-[#1D4ED8] text-white border-[#1D4ED8] shadow-[0_0_12px_rgba(29,78,216,0.3)]'
                      : 'bg-base-100/95 dark:bg-base-200/95 text-base-content border-base-300 hover:bg-[#1D4ED8] hover:text-white hover:border-[#1D4ED8]'
                  }`}
                >
                  {t.label}
                </span>
                <button
                  type="button"
                  onClick={() => {
                    setTab(t.key);
                    setMenuOpen(false);
                  }}
                  className={`relative flex items-center justify-center w-11 h-11 rounded-full border shadow-xl transition-all duration-200 cursor-pointer active:scale-95 shrink-0 ${
                    isSelected
                      ? 'bg-[#1D4ED8] border-[#1D4ED8] text-white shadow-[0_0_15px_rgba(29,78,216,0.35)]'
                      : 'bg-base-100 dark:bg-base-200 border-base-300 text-base-content hover:bg-[#1D4ED8] hover:border-[#1D4ED8] hover:text-white hover:shadow-[0_0_12px_rgba(29,78,216,0.3)]'
                  }`}
                  title={t.label}
                >
                  {t.icon}
                  {t.badge && (
                    <span className="absolute -top-1 -right-1 bg-error text-error-content text-[9px] font-black rounded-full min-w-4.5 h-4.5 px-1 flex items-center justify-center shadow-md animate-pulse">
                      {t.badge > 9 ? '9+' : t.badge}
                    </span>
                  )}
                </button>
              </div>
            );
          })}
        </div>
        {/* FAB trigger pill — always receives pointer events */}
        <button
          type="button"
          onClick={() => setMenuOpen(!menuOpen)}
          className={`pointer-events-auto flex items-center justify-center gap-2 px-5 h-12 sm:h-13 rounded-full text-white shadow-2xl transition-all duration-300 cursor-pointer z-50 border active:scale-95 shrink-0 ${
            menuOpen
              ? 'bg-slate-800 dark:bg-base-300 border-white/20 shadow-xl'
              : 'bg-[#1D4ED8] hover:bg-[#2563EB] border-blue-600/30 hover:shadow-[0_0_18px_rgba(29,78,216,0.5)]'
          }`}
          title={isModerator ? 'Moderator Menu' : 'Admin Menu'}
        >
          {menuOpen ? (
            <X size={20} className="shrink-0" />
          ) : (
            <Wrench size={20} className="shrink-0" />
          )}
          <span className="text-xs font-bold whitespace-nowrap">
            {menuOpen ? 'Close' : isModerator ? 'Moderator' : 'Manage'}
          </span>
        </button>
      </div>
    </>
  );

  const modalSection = (
    <>
      <ConfirmModal
        isOpen={showDeleteConfirm}
        onClose={() => setShowDeleteConfirm(false)}
        onConfirm={deleteCommunity}
        title="Delete Community"
        message={`Are you sure you want to delete "${c.name}"? Your community will be temporarily disabled and permanently deleted in 1 day. You can restore it anytime before then.`}
        confirmLabel="Delete"
        cancelLabel="Cancel"
        isDanger={true}
        isLoading={deleteBusy}
      />
      {memberConfirm && (
        <ConfirmModal
          isOpen={memberConfirm.isOpen}
          onClose={() => setMemberConfirm(null)}
          onConfirm={async () => {
            const { userId, action, username } = memberConfirm;
            setMemberConfirm(null);
            await memberAction(userId, action, username);
          }}
          title={
            memberConfirm.action === 'ban' ? 'Ban Member' : 'Remove Member'
          }
          message={
            memberConfirm.action === 'ban'
              ? `Are you sure you want to ban @${memberConfirm.username} from "${c.name}"? They will not be able to rejoin.`
              : `Are you sure you want to remove @${memberConfirm.username} from "${c.name}"?`
          }
          confirmLabel={memberConfirm.action === 'ban' ? 'Ban' : 'Remove'}
          cancelLabel="Cancel"
          isDanger={true}
          isLoading={actingMem === memberConfirm.userId}
        />
      )}
    </>
  );

  if (inline) {
    return (
      <div className="relative flex flex-col h-full bg-base-100 min-h-[500px]">
        {panelContent}
        {fabSection}
        {modalSection}
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-[110] flex" onClick={closeViaUI}>
      <div className="absolute inset-0 bg-black/60 backdrop-blur-xs transition-opacity" />
      <style>{`
          @keyframes slideRPanel{from{transform:translateX(100%)}to{transform:translateX(0)}}
          ${isAdminDragging ? '* { user-select: none !important; cursor: col-resize !important; }' : ''}
        `}</style>
      <div
        className="relative ml-auto h-full bg-base-100 flex flex-col shadow-2xl border-l border-base-300 dark:border-white/10"
        style={{
          width: adminPanelWidthStyle,
          maxWidth: '100vw',
          transition: isAdminDragging
            ? 'none'
            : 'width 0.22s cubic-bezier(0.16, 1, 0.3, 1)',
          animation: panelAnimDone
            ? undefined
            : 'slideRPanel .22s ease-out forwards',
        }}
        onAnimationEnd={() => setPanelAnimDone(true)}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Left Drag Resize Handle */}
        <div
          onPointerDown={handleAdminPointerDown}
          onDoubleClick={toggleAdminMaximize}
          title="Drag left/right to resize • Double-click to expand/restore"
          className="hidden sm:flex absolute -left-1 top-0 bottom-0 w-2.5 z-30 cursor-col-resize items-center justify-center group/resizer select-none"
        >
          <div className="w-0.5 h-16 rounded-full bg-base-content/20 group-hover/resizer:bg-[#1D4ED8] group-hover/resizer:w-1 transition-all shadow-xs" />
        </div>

        {panelContent}
        {fabSection}
      </div>
      {modalSection}
    </div>
  );
}

/* ════════════════════════════════════════════════════════════════════════════
   CREATE COMMUNITY MODAL
════════════════════════════════════════════════════════════════════════════ */
function CreateModal({
  onClose,
  onDone,
}: {
  onClose: () => void;
  onDone: (c: CommunityData) => void;
}) {
  const { closeViaUI } = useBackNavigation(onClose);
  const [step, setStep] = useState<1 | 2>(1);
  const [form, setForm] = useState<CreateForm>({
    name: '',
    description: '',
    category: 'LOCAL_GOVERNANCE',
    tags: '',
    privacy: 'PUBLIC',
    locationRestricted: false,
    avatarUrl: '',
    allowMemberPosts: true,
    requirePostApproval: false,
  });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [catPage, setCatPage] = useState(0);

  // Image Upload & Editor States (Avatar & Cover)
  const [editorOpen, setEditorOpen] = useState(false);
  const [editorTarget, setEditorTarget] = useState<'avatar' | 'cover'>(
    'avatar',
  );
  const [editorImageSrc, setEditorImageSrc] = useState<string | null>(null);
  const [selectedBlob, setSelectedBlob] = useState<Blob | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Cover Image Upload States
  const [selectedCoverBlob, setSelectedCoverBlob] = useState<Blob | null>(null);
  const [coverPreviewUrl, setCoverPreviewUrl] = useState<string | null>(null);
  const coverFileInputRef = useRef<HTMLInputElement>(null);

  const canNext =
    form.name.trim().length >= 3 && form.description.trim().length >= 10;

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type)) {
      showToast.error('Please upload a JPEG, PNG, or WebP image.');
      return;
    }
    const maxSize = 5 * 1024 * 1024; // 5MB
    if (file.size > maxSize) {
      showToast.error('File size exceeds 5MB limit.');
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      setEditorTarget('avatar');
      setEditorImageSrc(reader.result as string);
      setEditorOpen(true);
    };
    reader.readAsDataURL(file);
  };

  const handleCoverUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type)) {
      showToast.error('Please upload a JPEG, PNG, or WebP image.');
      return;
    }
    const maxSize = 5 * 1024 * 1024; // 5MB
    if (file.size > maxSize) {
      showToast.error('File size exceeds 5MB limit.');
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      setEditorTarget('cover');
      setEditorImageSrc(reader.result as string);
      setEditorOpen(true);
    };
    reader.readAsDataURL(file);
  };

  const handleEditorSave = async (editedBlob: Blob) => {
    if (editorTarget === 'cover') {
      setSelectedCoverBlob(editedBlob);
      setCoverPreviewUrl(URL.createObjectURL(editedBlob));
    } else {
      setSelectedBlob(editedBlob);
      setPreviewUrl(URL.createObjectURL(editedBlob));
    }
    setEditorOpen(false);
    setEditorImageSrc(null);
  };

  async function submit() {
    setBusy(true);
    setErr(null);
    try {
      const createPayload = withPostApprovalAliases({
        name: form.name.trim(),
        description: form.description.trim(),
        category: form.category,
        tags: form.tags.trim(),
        privacy: form.privacy,
        avatarUrl: null,
        locationRestricted: form.locationRestricted,
        allowMemberPosts: form.allowMemberPosts,
        requirePostApproval: form.requirePostApproval,
      });
      const res = await fetch(apiUrl('/api/communities'), {
        method: 'POST',
        headers: hdrs(),
        body: JSON.stringify(createPayload),
      });
      if (res.status === 401 || res.status === 403) {
        setErr('Please log in.');
        return;
      }
      if (!res.ok) {
        const d = await res.json().catch(() => ({}));
        setErr(d?.message || `Error ${res.status}`);
        return;
      }
      const d = await res.json();
      let raw = d?.data ?? d;

      // Ensure creation settings (like requirePostApproval) are correctly persisted via a follow-up PUT request
      try {
        await fetch(apiUrl(`/api/communities/${raw.id}`), {
          method: 'PUT',
          headers: hdrs(),
          body: JSON.stringify(
            withPostApprovalAliases({
              name: form.name.trim(),
              description: form.description.trim(),
              category: form.category,
              privacy: form.privacy,
              allowMemberPosts: form.allowMemberPosts,
              requirePostApproval: form.requirePostApproval,
              feedEligible: true,
            }),
          ),
        });
        raw.requirePostApproval = form.requirePostApproval;
        raw.requiresPostApproval = form.requirePostApproval;
        raw.postApprovalRequired = form.requirePostApproval;
        raw.approvePosts = form.requirePostApproval;
        raw.approvalRequired = form.requirePostApproval;
        raw.allowMemberPosts = form.allowMemberPosts;
      } catch (putErr) {
        console.error(
          'Failed to sync community settings via PUT fallback:',
          putErr,
        );
      }

      // Upload avatar image if a blob was selected
      if (selectedBlob) {
        try {
          const file = new File([selectedBlob], 'community_avatar.jpg', {
            type: 'image/jpeg',
          });
          const uploadData = await communityService.uploadCommunityImage(
            raw.id,
            file,
            'avatar',
          );
          const updatedUrl =
            uploadData?.avatarUrl ||
            uploadData?.data?.avatarUrl ||
            uploadData?.data?.data?.avatarUrl;
          if (updatedUrl) {
            raw = { ...raw, avatarUrl: updatedUrl };
          }
        } catch (uploadErr: any) {
          console.error(
            'Failed to upload community avatar image during creation:',
            uploadErr,
          );
          showToast.error('Community created, but avatar image upload failed.');
        }
      }

      // Upload cover image if selected
      if (selectedCoverBlob) {
        try {
          const file = new File([selectedCoverBlob], 'community_cover.jpg', {
            type: selectedCoverBlob.type || 'image/jpeg',
          });
          const uploadData = await communityService.uploadCommunityImage(
            raw.id,
            file,
            'cover',
          );
          const updatedUrl =
            uploadData?.coverImageUrl ||
            uploadData?.data?.coverImageUrl ||
            uploadData?.data?.data?.coverImageUrl;
          if (updatedUrl) {
            raw = { ...raw, coverImageUrl: updatedUrl };
          }
        } catch (uploadErr: any) {
          console.error(
            'Failed to upload community cover image during creation:',
            uploadErr,
          );
          showToast.error('Community created, but cover image upload failed.');
        }
      }

      showToast.success('Community created successfully!');
      onDone({
        ...raw,
        isMember: true,
        isOwner: true,
        postCount: raw.postCount ?? 0,
        memberCount: raw.memberCount ?? 1,
        createdAt: raw.createdAt ?? new Date().toISOString(),
      });
    } catch {
      setErr('Server unreachable.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm p-2 sm:p-4"
      onClick={closeViaUI}
    >
      <div
        className="w-full max-w-sm sm:max-w-xl md:max-w-3xl bg-base-100/95 rounded-2xl sm:rounded-3xl border border-black/10 dark:border-white/15 shadow-[0_24px_48px_-12px_rgba(0,0,0,0.4)] h-[540px] max-h-[82dvh] sm:h-[580px] sm:max-h-[85vh] flex flex-col overflow-hidden backdrop-blur-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-3.5 sm:px-5 py-2.5 sm:py-3.5 border-b border-base-content/5 shrink-0">
          <div>
            <h2 className="font-extrabold text-sm sm:text-base tracking-tight flex items-center gap-1.5 sm:gap-2 text-base-content">
              <Plus size={14} className="text-blue-700 sm:size-[15px]" /> Create
              Community
            </h2>
            <div className="flex gap-1 mt-1">
              {[1, 2].map((s) => (
                <div
                  key={s}
                  className={`h-1 w-4 sm:w-5 rounded-full transition-all duration-500 ${step >= s ? 'bg-blue-700 w-6 sm:w-8' : 'bg-base-300'}`}
                />
              ))}
            </div>
          </div>
          <button
            onClick={closeViaUI}
            className="btn btn-ghost btn-circle btn-xs sm:btn-sm text-base-content/60 hover:text-base-content hover:bg-base-300/50"
          >
            <X size={16} className="sm:size-[18px]" />
          </button>
        </div>
        <div className="flex-1 min-h-0 overflow-y-auto p-3 sm:p-4 space-y-2.5 sm:space-y-3">
          {err && (
            <div className="bg-red-500/10 border border-red-500/30 text-red-400 text-xs sm:text-sm rounded-xl px-3 sm:px-4 py-1.5 sm:py-2 flex items-center gap-2 shadow-[0_0_15px_rgba(239,68,68,0.4)] animate-pulse">
              <AlertTriangle size={14} className="text-red-500 shrink-0" />
              <span>{err}</span>
            </div>
          )}

          {step === 1 && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 sm:gap-5">
              <div className="space-y-2 sm:space-y-3">
                <div>
                  <label className="block text-[10px] sm:text-xs font-bold text-base-content/80 mb-0.5 sm:mb-1">
                    Name <span className="text-error">*</span>
                  </label>
                  <input
                    autoFocus
                    className="input input-bordered input-xs sm:input-sm w-full rounded-xl font-medium text-xs sm:text-sm text-base-content h-8 sm:h-9"
                    placeholder="e.g. Pune Cyclists Club"
                    maxLength={60}
                    value={form.name}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, name: e.target.value }))
                    }
                  />
                  <p className="text-[8.5px] sm:text-[9px] font-bold opacity-65 mt-0.5 tracking-tighter text-base-content">
                    {form.name.length}/60 · min 3
                  </p>
                </div>

                <div>
                  <label className="block text-[10px] sm:text-xs font-bold text-base-content/80 mb-0.5 sm:mb-1">
                    Description <span className="text-error">*</span>
                  </label>
                  <textarea
                    className="textarea textarea-bordered transition-all focus:textarea-primary w-full resize-none text-xs sm:text-sm rounded-xl min-h-[60px] sm:min-h-[75px] text-base-content p-2 sm:p-2.5"
                    rows={2}
                    maxLength={500}
                    placeholder="What's this community about?"
                    value={form.description}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, description: e.target.value }))
                    }
                  />
                  <p className="text-[8.5px] sm:text-[9px] font-bold opacity-65 mt-0.5 tracking-tighter text-base-content">
                    {form.description.length}/500
                  </p>
                </div>

                <div>
                  <div className="flex items-center justify-between mb-1.5 gap-2 h-6">
                    <div className="flex items-center gap-1.5 min-w-0 flex-1">
                      <label className="text-[10px] sm:text-xs font-bold text-base-content/80 shrink-0">
                        Category
                      </label>
                      {form.category && (
                        <span className="text-[8.5px] sm:text-[9px] font-bold text-white bg-[#1D4ED8] px-1.5 sm:px-2 py-0.5 rounded-md shadow-sm shadow-[#1D4ED8]/30 truncate max-w-[150px] sm:max-w-[180px]">
                          {form.category
                            .replace(/_/g, ' ')
                            .toLowerCase()
                            .replace(/\b\w/g, (c) => c.toUpperCase())}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-1 sm:gap-1.5">
                    {CAT_PAGES[catPage].map((cat) => (
                      <button
                        key={cat}
                        type="button"
                        onClick={() =>
                          setForm((f) => ({ ...f, category: cat }))
                        }
                        className={`rounded-xl border min-h-[36px] sm:min-h-[40px] h-auto py-1.5 px-2 sm:px-2.5 text-left transition-colors duration-200 cursor-pointer flex items-center gap-2 ${
                          form.category === cat
                            ? 'border-[#1D4ED8] bg-[#1D4ED8] text-white shadow-[0_0_16px_rgba(29,78,216,0.65)] font-bold'
                            : 'border-base-300 hover:border-base-400 opacity-90 text-base-content'
                        }`}
                      >
                        <div className="text-sm shrink-0 flex items-center justify-center">
                          {CAT_ICON[cat] || <Globe size={15} />}
                        </div>
                        <div className="text-[10px] sm:text-[10.5px] font-bold tracking-tight leading-normal truncate flex-1 py-0.5">
                          {cat
                            .replace(/_/g, ' ')
                            .toLowerCase()
                            .replace(/\b\w/g, (c) => c.toUpperCase())}
                        </div>
                      </button>
                    ))}

                    {/* Pagination card occupying the 8th / final grid slot */}
                    <div
                      className={`rounded-xl border border-base-300 bg-base-200/60 min-h-[36px] sm:min-h-[40px] h-auto py-1 px-2 sm:px-2.5 flex items-center justify-between gap-1 shadow-xs ${
                        CAT_PAGES[catPage].length % 2 === 0
                          ? 'col-span-2'
                          : 'col-span-1'
                      }`}
                    >
                      <button
                        type="button"
                        onClick={() => setCatPage((p) => Math.max(0, p - 1))}
                        disabled={catPage === 0}
                        className="w-6 h-6 rounded-lg bg-base-100 hover:bg-[#1D4ED8] hover:text-white border border-base-content/10 flex items-center justify-center text-base-content transition-all duration-150 disabled:opacity-25 disabled:hover:bg-base-100 disabled:hover:text-base-content cursor-pointer disabled:cursor-not-allowed shadow-xs"
                        title="Previous categories"
                      >
                        <ChevronLeft size={13} />
                      </button>
                      <span className="text-[9px] sm:text-[9.5px] font-black opacity-75 tracking-tight text-base-content whitespace-nowrap">
                        {catPage + 1} / {CAT_PAGES.length}
                      </span>
                      <button
                        type="button"
                        onClick={() =>
                          setCatPage((p) =>
                            Math.min(CAT_PAGES.length - 1, p + 1),
                          )
                        }
                        disabled={catPage === CAT_PAGES.length - 1}
                        className="w-6 h-6 rounded-lg bg-[#1D4ED8] text-white hover:bg-blue-800 border border-[#1D4ED8] flex items-center justify-center transition-all duration-150 disabled:opacity-25 disabled:bg-base-100 disabled:text-base-content disabled:border-base-content/10 cursor-pointer disabled:cursor-not-allowed shadow-xs"
                        title="Next categories"
                      >
                        <ChevronRight size={13} />
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <div className="space-y-2.5 sm:space-y-3">
                <div>
                  <label className="block text-[10px] sm:text-xs font-bold text-base-content/80 mb-1">
                    Community Avatar
                  </label>
                  <div className="flex items-center gap-3 p-2 sm:p-2.5 bg-base-200/50 rounded-xl sm:rounded-2xl border border-base-300">
                    <div className="avatar shrink-0">
                      <div className="h-12 w-12 sm:h-14 sm:w-14 rounded-xl sm:rounded-2xl overflow-hidden border-2 border-primary/20 shadow-md bg-base-300 relative flex items-center justify-center font-bold text-lg sm:text-xl text-blue-700 uppercase">
                        {previewUrl ? (
                          <img
                            src={previewUrl}
                            alt="Preview"
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          (form.name?.[0] || '?').toUpperCase()
                        )}
                      </div>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex gap-1.5 flex-wrap">
                        <button
                          type="button"
                          onClick={() => fileInputRef.current?.click()}
                          className="btn btn-xs bg-blue-700 text-white border-none hover:bg-blue-800 rounded-lg font-bold uppercase tracking-wider text-[8.5px] sm:text-[9px] px-2.5 py-1 h-auto cursor-pointer"
                        >
                          {previewUrl ? 'Change Pic' : 'Upload Pic'}
                        </button>
                        {previewUrl && (
                          <>
                            <button
                              type="button"
                              onClick={() => {
                                setEditorTarget('avatar');
                                setEditorImageSrc(previewUrl);
                                setEditorOpen(true);
                              }}
                              className="btn btn-xs bg-base-300 hover:bg-base-400 text-base-content rounded-lg font-bold uppercase tracking-wider text-[8.5px] sm:text-[9px] px-2 py-1 h-auto cursor-pointer"
                            >
                              Edit
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setSelectedBlob(null);
                                setPreviewUrl(null);
                                if (fileInputRef.current)
                                  fileInputRef.current.value = '';
                              }}
                              className="btn btn-xs btn-ghost text-error rounded-lg font-bold uppercase tracking-wider text-[8.5px] sm:text-[9px] px-2 py-1 h-auto cursor-pointer"
                            >
                              Remove
                            </button>
                          </>
                        )}
                      </div>
                      <p className="text-[8.5px] sm:text-[9px] opacity-65 mt-1 text-base-content">
                        JPG, PNG, WebP (max 5MB)
                      </p>
                    </div>
                  </div>
                  <input
                    type="file"
                    ref={fileInputRef}
                    onChange={handleImageUpload}
                    accept="image/jpeg,image/png,image/webp"
                    className="hidden"
                  />
                </div>

                <div>
                  <label className="block text-[10px] sm:text-xs font-bold text-base-content/80 mb-1">
                    Community Cover Image
                  </label>
                  <div className="flex flex-col gap-2 p-2 sm:p-2.5 bg-base-200/50 rounded-xl sm:rounded-2xl border border-base-300">
                    <div className="w-full h-16 sm:h-20 rounded-xl overflow-hidden border border-base-300 shadow-sm relative bg-base-100 flex items-center justify-center">
                      {coverPreviewUrl ? (
                        <img
                          src={coverPreviewUrl}
                          alt="Cover Preview"
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <div className="w-full h-full bg-gradient-to-r from-blue-700/10 to-blue-500/5" />
                      )}
                    </div>
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1.5">
                      <div className="flex flex-wrap gap-1.5">
                        <button
                          type="button"
                          onClick={() => coverFileInputRef.current?.click()}
                          className="btn btn-xs bg-blue-700 text-white border-none hover:bg-blue-800 rounded-lg font-bold uppercase tracking-wider text-[8.5px] sm:text-[9px] px-2.5 py-1 h-auto cursor-pointer"
                        >
                          {coverPreviewUrl ? 'Change Cover' : 'Upload Cover'}
                        </button>
                        {coverPreviewUrl && (
                          <>
                            <button
                              type="button"
                              onClick={() => {
                                setEditorTarget('cover');
                                setEditorImageSrc(coverPreviewUrl);
                                setEditorOpen(true);
                              }}
                              className="btn btn-xs bg-base-300 hover:bg-base-400 text-base-content rounded-lg font-bold uppercase tracking-wider text-[8.5px] sm:text-[9px] px-2 py-1 h-auto cursor-pointer"
                            >
                              Edit
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setSelectedCoverBlob(null);
                                setCoverPreviewUrl(null);
                                if (coverFileInputRef.current)
                                  coverFileInputRef.current.value = '';
                              }}
                              className="btn btn-xs btn-ghost text-error rounded-lg font-bold uppercase tracking-wider text-[8.5px] sm:text-[9px] px-2 py-1 h-auto cursor-pointer"
                            >
                              Remove
                            </button>
                          </>
                        )}
                      </div>
                      <p className="text-[8.5px] sm:text-[9px] opacity-65 text-base-content">
                        JPG, PNG, WebP (max 5MB)
                      </p>
                    </div>
                  </div>
                  <input
                    type="file"
                    ref={coverFileInputRef}
                    onChange={handleCoverUpload}
                    accept="image/jpeg,image/png,image/webp"
                    className="hidden"
                  />
                </div>
              </div>
            </div>
          )}
          {step === 2 && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 sm:gap-5">
              <div className="space-y-2 sm:space-y-3">
                <div>
                  <label className="block text-xs sm:text-sm font-semibold mb-1.5 text-base-content">
                    Privacy
                  </label>
                  <div className="space-y-1.5 sm:space-y-2">
                    {(['PUBLIC', 'PRIVATE', 'SECRET'] as const).map((p) => {
                      const isLocked = false; // MONETIZATION DISABLED

                      return (
                        <button
                          key={p}
                          type="button"
                          onClick={() => {
                            setForm((f) => ({ ...f, privacy: p }));
                          }}
                          className={`w-full flex items-center gap-2.5 rounded-xl border p-2 sm:p-2.5 text-left transition-all cursor-pointer ${form.privacy === p ? 'border-[#1D4ED8] bg-[#1D4ED8] text-white shadow-[0_4px_16px_rgba(29,78,216,0.35)] scale-[1.01]' : 'border-base-content/5 hover:border-base-content/10 text-base-content'}`}
                        >
                          <span
                            className={`text-lg sm:text-xl ${form.privacy === p ? 'text-white' : 'opacity-80'}`}
                          >
                            {PRIV_ICON[p]}
                          </span>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-1.5">
                              <p
                                className={`text-[11px] sm:text-xs font-bold ${form.privacy === p ? 'text-white' : 'text-base-content/95'}`}
                              >
                                {p.charAt(0) + p.slice(1).toLowerCase()}
                              </p>
                              {isLocked && (
                                <span className="badge badge-warning text-[7px] sm:text-[8px] font-black py-0.5 px-1 uppercase tracking-wide">
                                  Locked
                                </span>
                              )}
                            </div>
                            <p
                              className={`text-[9px] sm:text-[10px] mt-0.5 truncate ${form.privacy === p ? 'text-white/80' : 'opacity-65 text-base-content'}`}
                            >
                              {PRIV_DESC[p]}
                            </p>
                          </div>
                          {form.privacy === p && (
                            <span className="text-white text-xs shadow-sm">
                              <Check size={13} />
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>

              <div className="space-y-2 sm:space-y-3">
                <div>
                  <label className="block text-[10px] sm:text-xs font-bold text-base-content/80 mb-0.5 sm:mb-1">
                    Tags{' '}
                    <span className="opacity-65 font-normal">(optional)</span>
                  </label>
                  <input
                    className="input input-bordered input-xs sm:input-sm w-full rounded-xl text-xs sm:text-sm text-base-content h-8 sm:h-9"
                    placeholder="civic, roads, water"
                    value={form.tags}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, tags: e.target.value }))
                    }
                  />
                </div>
                <div className="space-y-1 sm:space-y-1.5">
                  <label className="block text-[10px] sm:text-xs font-bold text-base-content/80 mb-0.5 sm:mb-1">
                    Settings
                  </label>
                  {[
                    {
                      key: 'allowMemberPosts',
                      label: 'Members can post',
                      desc: 'Allow anyone to create posts',
                    },
                    {
                      key: 'requirePostApproval',
                      label: 'Approve posts',
                      desc: 'Moderator must review',
                    },
                    {
                      key: 'locationRestricted',
                      label: 'Local Only',
                      desc: 'Limit to your pincode',
                    },
                  ].map(({ key, label, desc }) => (
                    <label
                      key={key}
                      className="flex items-center justify-between gap-2.5 rounded-xl border border-base-content/5 p-1.5 sm:p-2 px-2.5 sm:px-3 cursor-pointer hover:bg-base-200/50 transition-colors"
                    >
                      <div className="min-w-0">
                        <p className="text-[10px] sm:text-[11px] font-bold text-base-content/90 truncate">
                          {label}
                        </p>
                        <p className="text-[8.5px] sm:text-[9px] opacity-65 text-base-content mt-0.5 truncate">
                          {desc}
                        </p>
                      </div>
                      <input
                        type="checkbox"
                        className="toggle toggle-xs sm:toggle-sm govlyx-red-toggle scale-80 sm:scale-90"
                        checked={form[key as keyof CreateForm] as boolean}
                        onChange={(e) =>
                          setForm((f) => ({ ...f, [key]: e.target.checked }))
                        }
                      />
                    </label>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
        <div
          className={`shrink-0 px-3.5 sm:px-5 py-2.5 sm:py-3 border-t border-base-content/10 bg-base-200/30 flex items-center gap-2.5 ${step === 2 ? 'justify-between' : 'justify-end'}`}
        >
          {step === 2 && (
            <button
              type="button"
              className="btn btn-xs sm:btn-sm btn-ghost rounded-lg sm:rounded-xl text-xs font-semibold px-3 sm:px-3.5 h-8 sm:h-9 min-h-0 text-base-content cursor-pointer inline-flex items-center gap-1"
              onClick={() => setStep(1)}
              disabled={busy}
            >
              <ChevronLeft size={14} /> Back
            </button>
          )}
          {step === 1 ? (
            <button
              type="button"
              className={`btn btn-xs sm:btn-sm rounded-lg sm:rounded-xl text-white text-xs font-bold px-4 sm:px-5 h-8 sm:h-9 min-h-0 inline-flex items-center gap-1.5 border-none transition-all duration-200 cursor-pointer ${
                !canNext
                  ? 'bg-[#1D4ED8]/60 opacity-60 cursor-not-allowed'
                  : 'bg-[#1D4ED8] hover:bg-blue-800 shadow-sm shadow-[#1D4ED8]/40'
              }`}
              disabled={!canNext}
              onClick={() => setStep(2)}
              title={
                !canNext
                  ? 'Please enter a community name (min 3 chars) and description (min 10 chars)'
                  : 'Proceed to next step'
              }
            >
              <span>Next</span> <ArrowRight size={13} />
            </button>
          ) : (
            <button
              type="button"
              className={`btn btn-xs sm:btn-sm rounded-lg sm:rounded-xl text-white text-xs font-semibold px-4 sm:px-5 h-8 sm:h-9 min-h-0 inline-flex items-center gap-1 border-none transition-all duration-200 cursor-pointer ${
                busy
                  ? 'bg-[#1D4ED8] opacity-70'
                  : 'bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 shadow-sm shadow-[#1D4ED8]/30'
              }`}
              disabled={busy}
              onClick={submit}
            >
              {busy ? (
                'Creating…'
              ) : (
                <>
                  <Rocket size={13} className="shrink-0" /> Create Community
                </>
              )}
            </button>
          )}
        </div>
      </div>
      {editorImageSrc && (
        <div onClick={(e) => e.stopPropagation()}>
          <ImageEditorModal
            isOpen={editorOpen}
            cropShape={editorTarget === 'cover' ? 'rect' : 'circle'}
            title={
              editorTarget === 'cover'
                ? 'Edit Cover Photo'
                : 'Edit Avatar Photo'
            }
            onClose={() => {
              setEditorOpen(false);
              setEditorImageSrc(null);
              if (fileInputRef.current) fileInputRef.current.value = '';
              if (coverFileInputRef.current)
                coverFileInputRef.current.value = '';
            }}
            imageSrc={editorImageSrc}
            onSave={handleEditorSave}
          />
        </div>
      )}
    </div>
  );
}

interface ShareCommunityModalProps {
  isOpen: boolean;
  onClose: () => void;
  community: CommunityData;
}

function ShareCommunityModal({
  isOpen,
  onClose,
  community,
}: ShareCommunityModalProps) {
  const [copied, setCopied] = useState(false);
  const shareUrl = `${window.location.origin}/communities/${community.slug || community.id}`;
  const shareText = `Join the community "${community.name}" on Govlyx!`;

  useEffect(() => {
    if (isOpen) {
      setCopied(false);
    }
  }, [isOpen]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopied(true);
      showToast.success('Community link copied to clipboard!');
      setTimeout(() => setCopied(false), 2000);
    } catch {
      showToast.error('Could not copy link.');
    }
  };

  const handleWhatsApp = () => {
    window.open(
      `https://wa.me/?text=${encodeURIComponent(shareText + '\n' + shareUrl)}`,
      '_blank',
    );
    onClose();
  };

  const handleTelegram = () => {
    window.open(
      `https://t.me/share/url?url=${encodeURIComponent(shareUrl)}&text=${encodeURIComponent(shareText)}`,
      '_blank',
    );
    onClose();
  };

  const handleTwitter = () => {
    window.open(
      `https://twitter.com/intent/tweet?text=${encodeURIComponent(shareText)}&url=${encodeURIComponent(shareUrl)}`,
      '_blank',
    );
    onClose();
  };

  const handleInstagram = () => {
    navigator.clipboard.writeText(shareUrl).catch(() => {});
    showToast.success('Link copied! Opening Instagram...');
    window.open('https://instagram.com', '_blank');
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-[200] flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
      onClick={onClose}
    >
      <div
        className="w-full max-w-xs rounded-3xl border border-black/10 dark:border-white/15 bg-base-100 p-6 shadow-2xl space-y-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-center text-lg font-bold text-base-content w-full">
            Share Community via
          </h3>
        </div>
        <div className="space-y-4">
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
                {copied ? 'Copied!' : 'Copy'}
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

          {/* Cancel Button */}
          <button
            onClick={onClose}
            className="w-full py-2.5 rounded-2xl text-xs font-bold text-base-content/70 hover:bg-base-200 transition-colors"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}

/* ════════════════════════════════════════════════════════════════════════════
   DETAIL PANEL
════════════════════════════════════════════════════════════════════════════ */
function DetailPanel({
  community,
  onClose,
  onMembershipChange,
}: {
  community: CommunityData;
  onClose: () => void;
  onMembershipChange: (
    id: number,
    isMember: boolean,
    delta: number,
    hasPendingRequest?: boolean,
    communityData?: CommunityData,
  ) => void;
}) {
  const navigate = useNavigate();
  const { closeViaUI } = useBackNavigation(onClose);
  const {
    panelWidthStyle,
    isMaximized,
    isDragging,
    handlePointerDown,
    toggleMaximize,
  } = useResizableDrawer(768, 'govlyx_community_detail_panel_width');
  const queryClient = useQueryClient();
  const normalise = (raw: CommunityData): CommunityData =>
    raw.isOwner ? { ...raw, isMember: true } : raw;

  const handleShareCommunity = () => {
    setShareModalOpen(true);
  };

  const [c, setC] = useState(() => normalise(community));

  useEffect(() => {
    setC(normalise(community));
  }, [community]);

  // Lock background body scroll while community drawer is open to prevent scroll jank
  useEffect(() => {
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = originalOverflow;
    };
  }, []);

  const [panelAnimDone, setPanelAnimDone] = useState(false);
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const [showJoinConfirm, setShowJoinConfirm] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const postId = searchParams.get('postId');
  const tabParam = searchParams.get('tab');
  const sharedPostIdParam = searchParams.get('sharedPostId');

  const [tab, setTab] = useState<'posts' | 'chat' | 'about' | 'manage'>(() => {
    return tabParam === 'chat' || tabParam === 'about' || !!sharedPostIdParam
      ? 'chat'
      : tabParam === 'posts'
        ? 'posts'
        : tabParam === 'manage'
          ? 'manage'
          : 'posts';
  });
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [hasMore, setHasMore] = useState(false);
  const [cursor, setCursor] = useState<number | null>(null);
  const [acting, setActing] = useState(false);
  const [openCreatePost, setOpenCreatePost] = useState(false);
  const [shareModalOpen, setShareModalOpen] = useState(false);
  const [feedFilter, setFeedFilter] = useState<'ALL' | 'MY_POSTS'>('ALL');
  const [postSort, setPostSort] = useState<'NEW' | 'TOP'>('NEW');
  const [cursorScore, setCursorScore] = useState<number | null>(null);
  const [pendingSharedPost, setPendingSharedPost] = useState<{
    postId: number;
    content: string;
    authorUsername?: string;
  } | null>(null);

  // Sync tab with URL search parameter
  useEffect(() => {
    if (
      tabParam === 'chat' ||
      tabParam === 'about' ||
      tabParam === 'posts' ||
      tabParam === 'manage'
    ) {
      setTab(tabParam);
    }
  }, [tabParam]);

  // If sharedPostId is passed in URL, load post and attach to community chat
  useEffect(() => {
    if (sharedPostIdParam) {
      setTab('chat');
      const loadSharedPost = async () => {
        try {
          const res: any = await postService.getPostById(
            Number(sharedPostIdParam),
            'social-posts',
          );
          const postData = res?.data || res;
          if (postData) {
            setPendingSharedPost({
              postId: Number(sharedPostIdParam),
              content: postData.content || '',
              authorUsername: postData.authorUsername || postData.username,
            });
            setTab('chat');
          }
        } catch {
          try {
            const res2: any = await postService.getPostById(
              Number(sharedPostIdParam),
              'posts',
            );
            const postData2 = res2?.data || res2;
            if (postData2) {
              setPendingSharedPost({
                postId: Number(sharedPostIdParam),
                content: postData2.content || '',
                authorUsername: postData2.authorUsername || postData2.username,
              });
              setTab('chat');
            }
          } catch (err) {
            console.error('Failed to load shared post for chat', err);
          }
        }
      };
      loadSharedPost();
    }
  }, [sharedPostIdParam]);

  const {
    data: singlePost,
    isLoading: loadingSinglePost,
    error: singlePostError,
  } = useQuery({
    queryKey: ['social-post', postId],
    queryFn: async () => {
      if (!postId) return null;
      const raw: any = await postService.getPostById(
        Number(postId),
        'social-posts',
      );
      const postData = raw?.data || raw;
      return toPostCardPost({
        ...postData,
        variant: postData.isPoll ? 'poll' : 'social',
        username: postData.authorUsername || postData.username,
        userProfileImage:
          postData.authorProfileImage || postData.userProfileImage,
        isLikedByCurrentUser:
          postData.isLikedByMe ??
          postData.likedByMe ??
          postData.isLikedByCurrentUser ??
          false,
        isSavedByCurrentUser:
          postData.isSavedByMe ??
          postData.savedByMe ??
          postData.isSavedByCurrentUser ??
          postData.isSaved ??
          false,
        communityId: c.id,
        communityName: c.name,
        communityAvatar: c.avatarUrl || undefined,
        communityMemberCount: String(c.memberCount || 0),
        isMember: c.isMember,
      });
    },
    enabled:
      !!postId && !!c.id && (c.isMember || c.isOwner || c.privacy === 'PUBLIC'),
  });

  const { data: currentUserProfile } = useCurrentUser();

  // ── CurrentUser for PostCard ──
  const currentUser: CardUser | null = useMemo(() => {
    if (currentUserProfile) {
      return {
        id: currentUserProfile.id,
        role: currentUserProfile.role,
        username:
          currentUserProfile.username ||
          currentUserProfile.actualUsername ||
          'User',
      };
    }
    const t = getToken();
    if (!t) return null;
    try {
      const d: any = jwtDecode(t);
      return {
        id: Number(d.sub),
        role: d.role,
        username: d.username || d.sub || 'User',
      };
    } catch {
      return null;
    }
  }, [currentUserProfile]);

  const sharePostToChat = (
    postId: number,
    postContent: string,
    authorUsername?: string,
  ) => {
    if (!c.isMember && !c.isOwner) {
      showToast.error(
        'You must be a member to share or reply in community chat',
      );
      return;
    }
    setPendingSharedPost({
      postId,
      content: postContent,
      authorUsername,
    });
    setTab('chat');
  };

  useEffect(() => {
    const isOwner = Boolean(
      community.isOwner === true ||
        (community.role && String(community.role).toUpperCase() === 'OWNER'),
    );
    const isMember = Boolean(community.isMember === true || isOwner);
    const localPending = getPendingLocal().includes(String(community.id));
    const isPending =
      !isMember &&
      !isOwner &&
      Boolean(community.hasPendingRequest === true || localPending);
    const role = isOwner
      ? 'OWNER'
      : isMember
        ? community.role || community.currentUserRole || 'MEMBER'
        : null;
    const initial: CommunityData = {
      ...community,
      isOwner,
      isMember,
      hasPendingRequest: isPending,
      role,
      currentUserRole: role,
      memberRole: role,
      isAdmin: isOwner || role === 'ADMIN',
      isModerator: role === 'MODERATOR',
    };
    setC(initial);
  }, [community]);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const res = await fetch(
          apiUrl(`/api/communities/${community.slug || community.id}`),
          { headers: hdrs() },
        );
        if (!res.ok) return;
        const d = await res.json();
        const detail = d?.data?.data ?? d?.data ?? d;
        if (active && detail) {
          const communityId = detail.id ?? community.id;
          const token = getToken();
          let currentUserId: number | null = null;
          if (token) {
            try {
              const decoded: any = jwtDecode(token);
              currentUserId = Number(
                decoded.id || decoded.userId || decoded.sub,
              );
            } catch {}
          }
          const fetchedOwner = resolveIsOwner(detail, currentUserId);
          const fetchedMember = Boolean(
            detail.isMember === true || detail.member === true || fetchedOwner,
          );
          const backendPending = Boolean(
            detail.hasPendingRequest === true || detail.pendingRequest === true,
          );
          const localPending = getPendingLocal().includes(String(communityId));
          const finalPending =
            !fetchedMember && !fetchedOwner && backendPending;

          // Clear local pending if member, owner, or backend says not pending
          if (
            fetchedMember ||
            fetchedOwner ||
            (!backendPending && localPending)
          )
            removePendingLocal(communityId);
          else if (backendPending) addPendingLocal(communityId);

          const role = fetchedOwner
            ? 'OWNER'
            : fetchedMember
              ? detail.role ||
                detail.currentUserRole ||
                detail.memberRole ||
                'MEMBER'
              : null;

          const updatedCommunity: CommunityData = {
            ...community,
            ...detail,
            id: communityId,
            requirePostApproval: resolvePostApprovalFlag(
              detail,
              community.requirePostApproval ?? false,
            ),
            isMember: fetchedMember,
            isOwner: fetchedOwner,
            isAdmin: fetchedOwner || role === 'ADMIN' || detail.isAdmin,
            isModerator: role === 'MODERATOR' || detail.isModerator,
            role: role,
            currentUserRole: role,
            memberRole: role,
            hasPendingRequest: finalPending,
          };

          setC(updatedCommunity);
          if (role) {
            setCachedCommunityRole(communityId, role);
          }
          onMembershipChange(
            communityId,
            fetchedMember,
            0,
            finalPending,
            updatedCommunity,
          );
        }
      } catch {}
    })();
    return () => {
      active = false;
    };
  }, [community.slug, community.id]);

  const loadPosts = useCallback(
    async (cur: number | null, score: number | null, replace: boolean) => {
      const canView = c.isMember || c.isOwner || c.privacy === 'PUBLIC';
      if (!canView) return;
      setLoading(true);
      try {
        const p = new URLSearchParams({ limit: '15' });
        if (cur !== null) p.set('cursor', String(cur));
        if (postSort === 'TOP' && score !== null)
          p.set('cursorScore', String(score));

        let endpoint = '';
        if (feedFilter === 'MY_POSTS' && currentUser?.id) {
          endpoint = `/api/communities/${c.id}/posts/user/${currentUser.id}?${p}`;
        } else {
          endpoint =
            postSort === 'TOP'
              ? `/api/communities/${c.id}/posts/top?${p}`
              : `/api/communities/${c.id}/posts?${p}`;
        }

        const res = await fetch(apiUrl(endpoint), { headers: hdrs() });
        if (!res.ok) throw new Error();
        const data = await res.json();
        const paged: any = data?.data ?? data;
        const arr = paged?.content ?? paged?.data ?? [];
        setPosts((prev) => (replace ? arr : [...prev, ...arr]));
        setHasMore(paged?.hasMore ?? false);
        setCursor(paged?.nextCursor ?? null);
        if (postSort === 'TOP') {
          setCursorScore(paged?.nextCursorScore ?? null);
        }
      } catch {
      } finally {
        setLoading(false);
      }
    },
    [
      c.id,
      c.isMember,
      c.isOwner,
      c.privacy,
      postSort,
      feedFilter,
      currentUser?.id,
    ],
  );

  useEffect(() => {
    const canView = c.isMember || c.isOwner || c.privacy === 'PUBLIC';
    if (canView) {
      loadPosts(null, null, true);
    } else {
      setPosts([]);
    }
  }, [loadPosts, c.isMember, c.isOwner, c.privacy, postSort, feedFilter]);

  const topPostId = posts[0]?.id;
  const { newPostCount, clearAndRefresh } = useFeedRefresh({
    communityId: c.id,
    topPostId,
    enabled:
      tab === 'posts' && (c.isMember || c.isOwner || c.privacy === 'PUBLIC'),
    onRefresh: () => {
      if (postSort !== 'NEW') {
        setPostSort('NEW');
      } else {
        loadPosts(null, null, true);
      }
    },
  });

  useEffect(() => {
    const handleCustomPostCreated = (e: Event) => {
      const customEvent = e as CustomEvent;
      const { post: rawNewPost, communityId: targetCommId } =
        customEvent.detail || {};
      if (targetCommId && targetCommId !== c.id) return;
      const newPost = rawNewPost?.data ?? rawNewPost;
      if (!newPost) return;
      if (newPost.status === 'PENDING_APPROVAL' || newPost.isPendingApproval)
        return;

      const authorName =
        newPost.authorUsername ||
        newPost.authorActualUsername ||
        newPost.author?.actualUsername ||
        newPost.author?.username ||
        currentUser?.actualUsername ||
        currentUser?.username ||
        'You';

      const authorImg =
        newPost.authorProfileImage ||
        newPost.author?.profileImage ||
        currentUser?.profileImage ||
        null;

      const normalizedPost: Post = {
        ...newPost,
        id: Number(newPost.id),
        content: newPost.content,
        authorUsername: authorName,
        authorActualUsername: authorName,
        username: authorName,
        authorProfileImage: authorImg,
        userProfileImage: authorImg,
        authorRole:
          newPost.authorRole ||
          (c.isOwner ? 'OWNER' : c.isModerator ? 'MODERATOR' : 'MEMBER'),
        isMyPost: true,
        canDelete: true,
        likeCount: newPost.likeCount ?? 0,
        commentCount: newPost.commentCount ?? 0,
        shareCount: newPost.shareCount ?? 0,
        timeAgo: 'Just now',
        createdAt: newPost.createdAt || new Date().toISOString(),
        isLikedByMe: false,
        isSavedByMe: false,
        communityId: c.id,
        communityName: c.name,
        status: 'ACTIVE',
      };

      setPosts((prev) => [
        normalizedPost,
        ...prev.filter((p) => p.id !== normalizedPost.id),
      ]);
      setC((prev) => ({ ...prev, postCount: (prev.postCount || 0) + 1 }));
    };

    window.addEventListener('postCreated', handleCustomPostCreated);
    return () =>
      window.removeEventListener('postCreated', handleCustomPostCreated);
  }, [c.id, c.name, c.isOwner, c.isModerator, currentUser]);

  const updatePostState = useCallback(
    (postId: number, updater: (post: any) => any) => {
      setPosts((prev) =>
        prev.map((p) => {
          if (p.id === postId) {
            return updater(p);
          }
          return p;
        }),
      );
    },
    [],
  );

  const handleLike = useCallback(
    (postId: number, liked: boolean) => {
      updatePostState(postId, (p: any) => {
        if (!!p.isLikedByMe === liked) return p;
        return {
          ...p,
          isLikedByMe: liked,
          likeCount: (p.likeCount ?? 0) + (liked ? 1 : -1),
        };
      });

      queryClient.setQueryData(['social-post', String(postId)], (prev: any) => {
        if (!prev) return prev;
        if (!!prev.isLikedByCurrentUser === liked) return prev;
        return {
          ...prev,
          isLikedByCurrentUser: liked,
          likeCount: (prev.likeCount ?? 0) + (liked ? 1 : -1),
        };
      });
    },
    [queryClient, updatePostState],
  );

  const handleSave = useCallback(
    (postId: number, saved: boolean) => {
      updatePostState(postId, (p: any) => {
        const isSaved = !!(p.isSavedByMe ?? p.isSaved ?? false);
        if (isSaved === saved) return p;
        return {
          ...p,
          isSavedByMe: saved,
          isSaved: saved,
          saveCount: Math.max(0, (p.saveCount ?? 0) + (saved ? 1 : -1)),
        };
      });

      queryClient.setQueryData(['social-post', String(postId)], (prev: any) => {
        if (!prev) return prev;
        const isSaved = !!(prev.isSavedByCurrentUser ?? prev.isSaved ?? false);
        if (isSaved === saved) return prev;
        return {
          ...prev,
          isSavedByCurrentUser: saved,
          isSaved: saved,
          saveCount: Math.max(0, (prev.saveCount ?? 0) + (saved ? 1 : -1)),
        };
      });
    },
    [queryClient, updatePostState],
  );

  const handleCommentCountChange = useCallback(
    (postId: number, newCount: number) => {
      updatePostState(postId, (p: any) => ({
        ...p,
        commentCount: newCount,
      }));

      queryClient.setQueryData(['social-post', String(postId)], (prev: any) => {
        if (!prev) return prev;
        return {
          ...prev,
          commentCount: newCount,
        };
      });
    },
    [queryClient, updatePostState],
  );

  const handleShare = useCallback(
    (postId: number) => {
      updatePostState(postId, (p: any) => ({
        ...p,
        shareCount: (p.shareCount ?? 0) + 1,
      }));

      queryClient.setQueryData(['social-post', String(postId)], (prev: any) => {
        if (!prev) return prev;
        return {
          ...prev,
          shareCount: (prev.shareCount ?? 0) + 1,
        };
      });
    },
    [queryClient, updatePostState],
  );

  const [restoreBusy, setRestoreBusy] = useState(false);

  async function handleRestoreCommunity() {
    setRestoreBusy(true);
    try {
      const res = await fetch(
        apiUrl(`/api/communities/${c.id}/revoke-delete`),
        {
          method: 'PUT',
          headers: hdrs(),
          body: JSON.stringify({}),
        },
      );
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        showToast.error(err?.message || 'Restore failed.');
        return;
      }
      showToast.success('Community restored successfully!');

      try {
        const local = getLocalDeletions();
        delete local[c.id];
        localStorage.setItem(
          'govlyx_deleted_communities',
          JSON.stringify(local),
        );
        cachedLocalDeletions = local;
      } catch (err) {
        console.error('Failed to update local deletions on restore', err);
      }

      const restored: CommunityData = {
        ...c,
        isDeleted: false,
        deletedAt: null,
        scheduledDeletionDate: null,
        deletionDueDate: null,
      };
      setC(restored);
      onMembershipChange(
        c.id,
        c.isMember ?? false,
        0,
        c.hasPendingRequest,
        restored,
      );
    } catch {
      showToast.error('Server unreachable.');
    } finally {
      setRestoreBusy(false);
    }
  }

  async function handleLeaveConfirm() {
    setActing(true);
    setShowLeaveConfirm(false);
    try {
      const res = await fetch(apiUrl(`/api/communities/${c.id}/leave`), {
        method: 'DELETE',
        headers: hdrs(),
      });
      if (!res.ok) {
        showToast.error(
          (await res.json().catch(() => ({}))).message || 'Could not leave.',
        );
        return;
      }
      setC((p) => ({ ...p, isMember: false, memberCount: p.memberCount - 1 }));
      onMembershipChange(c.id, false, -1, false, {
        ...c,
        isMember: false,
        memberCount: c.memberCount - 1,
      });
      showToast.info("💔 You left us... we'll remember you.");
    } catch {
      showToast.error('Could not leave.');
    } finally {
      setActing(false);
    }
  }

  async function performJoinCommunity() {
    setShowJoinConfirm(false);
    setActing(true);
    try {
      const res = await fetch(apiUrl(`/api/communities/${c.id}/join`), {
        method: 'POST',
        headers: hdrs(),
        body: JSON.stringify({}),
      });
      if (res.status === 401) {
        showToast.error('Please log in.');
        return;
      }
      const d = await res.json();
      const joined = d?.data?.joined ?? false;
      const isPrivate = String(c.privacy).toUpperCase() === 'PRIVATE';
      const newHasPending =
        !joined &&
        (isPrivate ||
          d?.data?.hasPendingRequest === true ||
          d?.data?.status === 'PENDING' ||
          d?.message?.toLowerCase().includes('request'));

      if (newHasPending) addPendingLocal(c.id);
      else if (joined) removePendingLocal(c.id);

      setC((p) => ({
        ...p,
        isMember: joined,
        hasPendingRequest: newHasPending,
        memberCount: joined ? p.memberCount + 1 : p.memberCount,
      }));
      onMembershipChange(c.id, joined, joined ? 1 : 0, newHasPending, {
        ...c,
        isMember: joined,
        hasPendingRequest: newHasPending,
        memberCount: joined ? c.memberCount + 1 : c.memberCount,
      });
      if (joined) {
        showToast.success('👋 Hey there, newcomer! Make yourself at home.');
      } else if (newHasPending) {
        showToast.info(
          'Request sent. We saved you a seat while the admins review it.',
        );
      }
      // Cache the community if we successfully joined it
      if (joined) {
        cacheSuggestion({
          kind: 'COMMUNITY',
          id: c.id,
          displayText: c.name,
          subText: c.description,
          avatarUrl: c.avatarUrl || undefined,
          slug: c.slug,
        });
      }
    } catch {
      showToast.error('Action failed.');
    } finally {
      setActing(false);
    }
  }

  async function toggleMembership() {
    if (!getAuthToken()) {
      navigate('/login');
      return;
    }
    if (acting) return;

    if (c.isMember) {
      setShowLeaveConfirm(true);
      return;
    } else if (c.hasPendingRequest) {
      setActing(true);
      try {
        await fetch(apiUrl(`/api/communities/${c.id}/join-requests/me`), {
          method: 'DELETE',
          headers: hdrs(),
        });
        removePendingLocal(c.id);
        setC((p) => ({ ...p, hasPendingRequest: false }));
        onMembershipChange(c.id, false, 0, false, {
          ...c,
          hasPendingRequest: false,
        });
        showToast.info('Join request cancelled.');
      } catch {
        showToast.error('Action failed.');
      } finally {
        setActing(false);
      }
    } else {
      setShowJoinConfirm(true);
    }
  }

  const canPost =
    !isCommunityDeleted(c) &&
    (c.isOwner || (c.isMember && c.allowMemberPosts !== false));

  const canViewPosts = c.isMember || c.isOwner || c.privacy === 'PUBLIC';

  return (
    <div className="fixed inset-0 z-[110] flex" onClick={closeViaUI}>
      <div className="absolute inset-0 bg-black/60 backdrop-blur-xs transition-opacity" />
      <style>{`
        @keyframes slideRPanel{from{transform:translateX(100%)}to{transform:translateX(0)}}
        ${isDragging ? '* { user-select: none !important; cursor: col-resize !important; }' : ''}
      `}</style>
      <div
        className="relative ml-auto h-full bg-base-100 flex flex-col shadow-2xl border-l border-base-300 dark:border-white/10"
        style={{
          width: panelWidthStyle,
          maxWidth: '100vw',
          transition: isDragging
            ? 'none'
            : 'width 0.22s cubic-bezier(0.16, 1, 0.3, 1)',
          animation: panelAnimDone
            ? undefined
            : 'slideRPanel .22s ease-out forwards',
        }}
        onAnimationEnd={() => setPanelAnimDone(true)}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Left Drag Resize Handle */}
        <div
          onPointerDown={handlePointerDown}
          onDoubleClick={toggleMaximize}
          title="Drag left/right to resize • Double-click to expand/restore"
          className="hidden sm:flex absolute -left-1 top-0 bottom-0 w-2.5 z-30 cursor-col-resize items-center justify-center group/resizer select-none"
        >
          <div className="w-0.5 h-16 rounded-full bg-base-content/20 group-hover/resizer:bg-[#1D4ED8] group-hover/resizer:w-1 transition-all shadow-xs" />
        </div>

        <div className="shrink-0 flex items-center justify-between px-4 py-3 border-b border-base-300 bg-base-100/90 backdrop-blur-md z-20">
          <button
            className="btn btn-ghost btn-sm gap-1 font-semibold"
            onClick={closeViaUI}
          >
            <ChevronLeft size={18} /> Back
          </button>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={toggleMaximize}
              title={
                isMaximized
                  ? 'Decrease to standard width'
                  : 'Expand to full width'
              }
              className="btn btn-ghost btn-sm btn-circle text-base-content/75 hover:text-base-content hover:bg-base-200 transition-colors"
            >
              {isMaximized ? <Minimize2 size={16} /> : <Maximize2 size={16} />}
            </button>
          </div>
        </div>

        {isCommunityDeleted(c) && (
          <div className="shrink-0 bg-red-50 dark:bg-red-950/40 border-b border-red-200 dark:border-red-500/25 p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-red-700 dark:text-red-400 z-55 shadow-[0_0_15px_rgba(239,68,68,0.05)] dark:shadow-[0_0_15px_rgba(239,68,68,0.15)]">
            <div className="flex items-start gap-2.5">
              <AlertTriangle
                size={20}
                className="shrink-0 text-red-600 dark:text-red-550 mt-0.5"
              />
              <div>
                <p className="text-xs font-bold uppercase tracking-wider">
                  Scheduled for Deletion ({getDeletionDaysLeft(c)} days left)
                </p>
                <p className="text-xs opacity-90 mt-0.5">
                  {c.isOwner
                    ? 'This community is scheduled for permanent deletion. All features are currently disabled.'
                    : 'This community is archived and scheduled for deletion. You can read past posts, but new interactions are disabled.'}
                </p>
              </div>
            </div>
            {c.isOwner && (
              <button
                onClick={handleRestoreCommunity}
                disabled={restoreBusy}
                className="btn btn-error btn-sm self-start sm:self-auto text-white shadow-lg shadow-error/20 rounded-xl"
              >
                {restoreBusy ? <Spin xs /> : 'Restore Community'}
              </button>
            )}
          </div>
        )}

        <div
          className="flex-1 overflow-y-auto overscroll-contain"
          style={{
            WebkitOverflowScrolling: 'touch',
            overscrollBehavior: 'contain',
          }}
        >
          <div className="p-2 sm:p-4 space-y-3 sm:space-y-4">
            <CommunityHeader
              community={c}
              acting={acting}
              onJoinClick={toggleMembership}
              onImageUploaded={(type, url) => {
                setC((prev) => ({
                  ...prev,
                  [type === 'avatar' ? 'avatarUrl' : 'coverImageUrl']: url,
                }));
                onMembershipChange(
                  c.id,
                  c.isMember ?? false,
                  0,
                  c.hasPendingRequest,
                  {
                    ...c,
                    [type === 'avatar' ? 'avatarUrl' : 'coverImageUrl']: url,
                  },
                );
              }}
            />
            <CommunityTabs
              active={tab}
              onChange={(nextTab) => {
                setTab(nextTab);
                setSearchParams(
                  (prev) => {
                    const next = new URLSearchParams(prev);
                    next.set('tab', nextTab);
                    return next;
                  },
                  { replace: true },
                );
              }}
              onActiveClick={(activeTab) => {
                const scrollContainers = document.querySelectorAll(
                  'main.overflow-y-auto, .overflow-y-auto',
                );
                scrollContainers.forEach((el) =>
                  el.scrollTo({ top: 0, behavior: 'smooth' }),
                );
                if (activeTab === 'posts') {
                  loadPosts(null, null, true);
                }
              }}
              showManage={Boolean(
                c.isOwner ||
                  c.isAdmin ||
                  c.isModerator ||
                  c.role === 'OWNER' ||
                  c.role === 'ADMIN' ||
                  c.role === 'MODERATOR',
              )}
            />

            {tab === 'posts' && (
              <PullToRefresh
                onRefresh={async () => {
                  await loadPosts(null, null, true);
                }}
              >
                <div className="space-y-3">
                  {!canViewPosts ? (
                    <div className="text-center py-12 opacity-50 space-y-2">
                      <div className="flex justify-center mb-2">
                        <Lock size={40} />
                      </div>
                      <p className="text-sm">
                        Join this community to view posts.
                      </p>
                    </div>
                  ) : postId ? (
                    <div className="space-y-4 pt-2">
                      <button
                        className="btn btn-ghost btn-sm gap-1 hover:bg-base-200/50"
                        onClick={() => {
                          setSearchParams((prev) => {
                            const next = new URLSearchParams(prev);
                            next.delete('postId');
                            return next;
                          });
                          setTab('posts');
                        }}
                      >
                        ← Back to Community
                      </button>

                      {loadingSinglePost && (
                        <div className="relative min-h-[360px] rounded-3xl">
                          <LoadingAnimation overlay label="Loading post" />
                        </div>
                      )}

                      {singlePostError && (
                        <div className="text-center py-12 opacity-50 space-y-2">
                          <p className="text-sm text-error font-medium">
                            Post not found or unavailable.
                          </p>
                        </div>
                      )}

                      {!loadingSinglePost && !singlePostError && singlePost && (
                        <PostCard
                          post={singlePost}
                          currentUser={currentUser || undefined}
                          hideCommunityStrip={true}
                          onLike={handleLike}
                          onSave={handleSave}
                          onShare={handleShare}
                          onCommentCountChange={handleCommentCountChange}
                          onShareToCommunity={
                            c.isMember || c.isOwner
                              ? (postId, content) =>
                                  sharePostToChat(
                                    postId,
                                    content,
                                    singlePost.username,
                                  )
                              : undefined
                          }
                          readOnly={isCommunityDeleted(c)}
                          isCommunityOwner={c.isOwner}
                        />
                      )}
                    </div>
                  ) : (
                    <>
                      {canPost && (
                        <button
                          className="btn w-full bg-[#1D4ED8] hover:bg-blue-800 text-white border-none rounded-xl py-2.5 flex items-center justify-center gap-2 text-sm font-semibold shadow-sm hover:shadow transition-all duration-200 transform hover:scale-[1.01] active:scale-[0.99]"
                          onClick={() => setOpenCreatePost(true)}
                        >
                          <Plus size={16} /> Create a community post
                        </button>
                      )}

                      <button
                        type="button"
                        onClick={handleShareCommunity}
                        className="btn w-full bg-base-100 hover:bg-base-200 dark:bg-base-200 dark:hover:bg-base-300 border border-base-300 dark:border-white/10 text-base-content rounded-xl py-2 flex items-center justify-center gap-2 text-xs sm:text-sm font-semibold shadow-xs hover:shadow transition-all duration-200 transform hover:scale-[1.01] active:scale-[0.99] cursor-pointer"
                      >
                        <Share2 size={15} /> Share Community
                      </button>

                      <CreatePost
                        open={openCreatePost}
                        onClose={() => setOpenCreatePost(false)}
                        communityId={c.id}
                        communityName={c.name}
                        onPostCreated={(rawNewPost: any) => {
                          const newPost = rawNewPost?.data ?? rawNewPost;
                          if (!newPost) return;
                          if (
                            newPost.status === 'PENDING_APPROVAL' ||
                            newPost.isPendingApproval
                          ) {
                            return;
                          }

                          const authorName =
                            newPost.authorUsername ||
                            newPost.authorActualUsername ||
                            newPost.author?.actualUsername ||
                            newPost.author?.username ||
                            currentUser?.actualUsername ||
                            currentUser?.username ||
                            'You';

                          const authorImg =
                            newPost.authorProfileImage ||
                            newPost.author?.profileImage ||
                            currentUser?.profileImage ||
                            null;

                          const normalizedPost: Post = {
                            ...newPost,
                            id: Number(newPost.id),
                            content: newPost.content,
                            authorUsername: authorName,
                            authorActualUsername: authorName,
                            username: authorName,
                            authorProfileImage: authorImg,
                            userProfileImage: authorImg,
                            authorRole:
                              newPost.authorRole ||
                              (c.isOwner
                                ? 'OWNER'
                                : c.isModerator
                                  ? 'MODERATOR'
                                  : 'MEMBER'),
                            isMyPost: true,
                            canDelete: true,
                            likeCount: newPost.likeCount ?? 0,
                            commentCount: newPost.commentCount ?? 0,
                            shareCount: newPost.shareCount ?? 0,
                            timeAgo: 'Just now',
                            createdAt:
                              newPost.createdAt || new Date().toISOString(),
                            isLikedByMe: false,
                            isSavedByMe: false,
                            communityId: c.id,
                            communityName: c.name,
                            status: 'ACTIVE',
                          };

                          setPosts((prev) => [
                            normalizedPost,
                            ...prev.filter((p) => p.id !== normalizedPost.id),
                          ]);
                          setC((prev) => ({
                            ...prev,
                            postCount: (prev.postCount || 0) + 1,
                          }));
                        }}
                      />

                      <div className="sticky top-4 z-30 bg-base-200/95 backdrop-blur-md py-2 px-3 rounded-xl border border-base-content/5 flex items-center justify-between flex-wrap gap-2 mb-2">
                        <div className="flex items-center gap-3">
                          <span className="text-sm font-semibold opacity-80">
                            Feed
                          </span>

                          {/* Feed Filter: All vs My Posts */}
                          <div className="flex bg-base-300/40 p-1 rounded-xl border border-base-300/50">
                            <button
                              className={`btn btn-sm rounded-lg border-none px-3.5 font-bold transition-all text-[11px] sm:text-xs h-7 min-h-7 ${
                                feedFilter === 'ALL'
                                  ? 'bg-base-100 shadow-sm text-base-content'
                                  : 'btn-ghost text-base-content/60 hover:text-base-content'
                              }`}
                              onClick={() => setFeedFilter('ALL')}
                            >
                              All Posts
                            </button>
                            <button
                              className={`btn btn-sm rounded-lg border-none px-3.5 font-bold transition-all text-[11px] sm:text-xs h-7 min-h-7 ${
                                feedFilter === 'MY_POSTS'
                                  ? 'bg-base-100 shadow-sm text-base-content'
                                  : 'btn-ghost text-base-content/60 hover:text-base-content'
                              }`}
                              onClick={() => setFeedFilter('MY_POSTS')}
                            >
                              My Posts
                            </button>
                          </div>
                        </div>

                        <div className="flex bg-base-200 rounded-lg p-0.5 border border-base-300">
                          <button
                            className={`btn btn-xs rounded-md border-none flex items-center gap-1 ${postSort === 'NEW' ? 'bg-base-100 shadow-sm text-base-content' : 'btn-ghost text-base-content/60'}`}
                            onClick={() => setPostSort('NEW')}
                            disabled={loading}
                          >
                            <Sparkles size={12} className="text-amber-500" />
                            <span>New</span>
                          </button>
                          <button
                            className={`btn btn-xs rounded-md border-none flex items-center gap-1 ${postSort === 'TOP' ? 'bg-base-100 shadow-sm text-base-content' : 'btn-ghost text-base-content/60'}`}
                            onClick={() => setPostSort('TOP')}
                            disabled={loading}
                          >
                            <Flame size={12} className="text-orange-500" />
                            <span>Trending</span>
                          </button>
                        </div>
                      </div>

                      <NewPostsBanner
                        count={newPostCount}
                        onTap={clearAndRefresh}
                        topOffset="4.25rem"
                      />

                      {loading && posts.length === 0 && (
                        <div className="relative min-h-[360px] rounded-3xl">
                          <LoadingAnimation overlay label="Loading posts" />
                        </div>
                      )}
                      {(() => {
                        const displayedPosts = posts;

                        if (!loading && displayedPosts.length === 0) {
                          return (
                            <div className="text-center py-12 opacity-50 space-y-2">
                              <div className="flex justify-center mb-2">
                                <Inbox size={40} />
                              </div>
                              <p className="text-sm">
                                {feedFilter === 'MY_POSTS'
                                  ? 'No post done by you yet'
                                  : canPost
                                    ? 'No posts yet — be the first!'
                                    : 'No posts yet.'}
                              </p>
                            </div>
                          );
                        }

                        return displayedPosts.map((post) => {
                          const authorUsername =
                            post.authorUsername ||
                            post.authorActualUsername ||
                            post.author?.actualUsername ||
                            post.author?.username ||
                            post.username;

                          const authorImg =
                            post.authorProfileImage ||
                            post.author?.profileImage ||
                            post.userProfileImage;

                          const isMine =
                            post.isMyPost ??
                            (currentUser &&
                              (authorUsername?.toLowerCase() ===
                                currentUser.username?.toLowerCase() ||
                                authorUsername?.toLowerCase() ===
                                  currentUser.actualUsername?.toLowerCase()));

                          const cardPost = toPostCardPost({
                            ...post,
                            variant: post.isPoll ? 'poll' : 'social', // allow toPostCardPost to re-detect if it's a poll
                            username: authorUsername,
                            authorUsername,
                            userProfileImage: authorImg,
                            isMyPost: isMine,
                            canDelete: isMine,
                            isLikedByCurrentUser:
                              post.isLikedByMe ??
                              post.likedByMe ??
                              post.isLikedByCurrentUser,
                            isSavedByCurrentUser:
                              post.isSavedByMe ??
                              post.savedByMe ??
                              post.isSavedByCurrentUser,
                            communityId: c.id,
                            communityName: c.name,
                            communityAvatar: c.avatarUrl || undefined,
                            communityMemberCount: String(c.memberCount || 0),
                            isMember: c.isMember,
                          });

                          return (
                            <div key={post.id} className="flex flex-col">
                              <PostCard
                                post={cardPost}
                                currentUser={currentUser || undefined}
                                hideCommunityStrip={true}
                                onLike={handleLike}
                                onSave={handleSave}
                                onShare={handleShare}
                                onCommentCountChange={handleCommentCountChange}
                                onShareToCommunity={
                                  c.isMember || c.isOwner
                                    ? (postId, content) =>
                                        sharePostToChat(
                                          postId,
                                          content,
                                          post.authorUsername,
                                        )
                                    : undefined
                                }
                                readOnly={isCommunityDeleted(c)}
                                isCommunityOwner={c.isOwner}
                              />
                            </div>
                          );
                        });
                      })()}

                      {hasMore && !loading && (
                        <button
                          className="w-full py-2 text-sm text-blue-700 hover:opacity-70 inline-flex items-center justify-center gap-1"
                          onClick={() => loadPosts(cursor, cursorScore, false)}
                        >
                          Load more <ArrowDown size={14} />
                        </button>
                      )}
                    </>
                  )}
                </div>
              </PullToRefresh>
            )}

            {tab === 'chat' &&
              (c.isMember || c.isOwner ? (
                <CommunityChat
                  communityId={c.id}
                  isAdmin={c.isOwner || c.isModerator || c.isAdmin || false}
                  initialSharedPost={pendingSharedPost}
                  onClearSharedPost={() => setPendingSharedPost(null)}
                />
              ) : (
                <div className="text-center py-12 bg-base-200 rounded-2xl border border-base-300 opacity-50 space-y-2">
                  <div className="flex justify-center mb-2">
                    <Lock size={40} />
                  </div>
                  <p className="text-sm text-base-content">
                    Join this community to view and participate in the group
                    chat.
                  </p>
                </div>
              ))}

            {tab === 'about' && (
              <div className="space-y-6">
                <div className="rounded-xl border border-base-300 bg-base-200 p-5">
                  <h3 className="font-semibold mb-3 text-lg">
                    About this community
                  </h3>
                  <p className="text-sm opacity-80 whitespace-pre-line leading-relaxed">
                    {decodeHTML(
                      c.description || 'No detailed description provided.',
                    )}
                  </p>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
                  {(
                    [
                      [
                        'Members',
                        c.memberCount.toLocaleString(),
                        <Users size={14} />,
                      ],
                      [
                        'Posts',
                        c.postCount.toLocaleString(),
                        <MessageSquare size={14} />,
                      ],
                      ['Privacy', c.privacy, <Lock size={14} />],
                      [
                        'Since',
                        new Date(c.createdAt).toLocaleDateString('en-IN', {
                          month: 'short',
                          year: 'numeric',
                        }),
                        <Calendar size={14} />,
                      ],
                      c.category
                        ? [
                            'Category',
                            c.category.replace(/_/g, ' '),
                            <Tag size={14} />,
                          ]
                        : null,
                    ] as Array<[string, string, React.ReactNode] | null>
                  )
                    .filter(
                      (item): item is [string, string, React.ReactNode] =>
                        !!item,
                    )
                    .map(([l, v, ico]) => (
                      <div
                        key={l}
                        className="rounded-xl border border-base-300 bg-base-200 p-3"
                      >
                        <p className="text-xs opacity-50 mb-1 flex items-center gap-1">
                          {ico} {l}
                        </p>
                        <p className="text-sm font-semibold">{v}</p>
                      </div>
                    ))}
                </div>

                <div className="rounded-xl border border-base-300 bg-base-200 p-5">
                  <h3 className="font-semibold mb-3 text-lg">
                    Media &amp; Data
                  </h3>
                  {(() => {
                    const mediaList: Array<{
                      url: string;
                      type: 'image' | 'video';
                      postId: number;
                    }> = [];
                    posts.forEach((p) => {
                      const urls = new Set<string>();
                      if (p.mediaUrls && Array.isArray(p.mediaUrls)) {
                        p.mediaUrls.forEach((u) => {
                          if (u) urls.add(u);
                        });
                      }
                      if (p.imageUrl) {
                        urls.add(p.imageUrl);
                      }
                      urls.forEach((url) => {
                        const isVideo =
                          /\.(mp4|webm|ogg|mov|m4v)$/i.test(url) ||
                          url.includes('/video');
                        mediaList.push({
                          url,
                          type: isVideo ? 'video' : 'image',
                          postId: p.id,
                        });
                      });
                    });

                    if (mediaList.length > 0) {
                      return (
                        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
                          {mediaList.slice(0, 6).map((item, idx) => (
                            <div
                              key={idx}
                              onClick={() => navigate(`/post/${item.postId}`)}
                              className="aspect-square rounded-xl bg-base-300/50 flex items-center justify-center overflow-hidden border border-base-300 text-base-content hover:scale-[1.02] transition-all cursor-pointer relative group/media"
                            >
                              {item.type === 'video' ? (
                                <>
                                  <video
                                    src={item.url}
                                    className="w-full h-full object-cover"
                                    muted
                                    playsInline
                                    loop
                                    onMouseOver={(e) =>
                                      e.currentTarget.play().catch(() => {})
                                    }
                                    onMouseOut={(e) => e.currentTarget.pause()}
                                  />
                                  <div className="absolute inset-0 bg-black/20 flex items-center justify-center group-hover/media:bg-black/40 transition-colors">
                                    <span className="text-white text-[9px] bg-black/60 px-1.5 py-0.5 rounded font-black uppercase tracking-wider">
                                      Video
                                    </span>
                                  </div>
                                </>
                              ) : (
                                <img
                                  src={item.url}
                                  alt="Uploaded content"
                                  className="w-full h-full object-cover transition-transform duration-300 group-hover/media:scale-105"
                                  onError={(e) => {
                                    (
                                      e.target as HTMLImageElement
                                    ).style.display = 'none';
                                  }}
                                />
                              )}
                            </div>
                          ))}
                        </div>
                      );
                    }

                    return (
                      <div className="text-center py-8 opacity-55 border-2 border-dashed border-base-300 rounded-xl space-y-1.5 bg-base-100/50">
                        <ImageIcon size={32} className="mx-auto opacity-30" />
                        <p className="text-xs font-bold uppercase tracking-wider">
                          No media uploaded yet
                        </p>
                        <p className="text-[10px] opacity-70">
                          Images and videos shared in community posts will
                          appear here.
                        </p>
                      </div>
                    );
                  })()}
                </div>

                <CommunitySidebar />
              </div>
            )}
            {tab === 'manage' && (
              <AdminPanel
                community={c}
                onClose={() => {
                  setTab('posts');
                  setSearchParams(
                    (prev) => {
                      const next = new URLSearchParams(prev);
                      next.set('tab', 'posts');
                      return next;
                    },
                    { replace: true },
                  );
                }}
                onCommunityUpdated={(updated) => {
                  setC(updated);
                  onMembershipChange(
                    c.id,
                    c.isMember ?? false,
                    0,
                    c.hasPendingRequest,
                    updated,
                  );
                }}
                onMembershipChange={(
                  id,
                  isMember,
                  delta,
                  hasPendingRequest,
                ) => {
                  onMembershipChange(id, isMember, delta, hasPendingRequest);
                }}
                inline={true}
              />
            )}
          </div>
        </div>
      </div>
      <ConfirmModal
        isOpen={showLeaveConfirm}
        onClose={() => setShowLeaveConfirm(false)}
        onConfirm={handleLeaveConfirm}
        title="Leave Community"
        message={`Are you sure you want to leave "${c.name}"? You will no longer receive updates or see posts from this community.`}
        confirmLabel="Leave"
        cancelLabel="Cancel"
        isDanger={true}
        isLoading={acting}
      />
      <ConfirmModal
        isOpen={showJoinConfirm}
        onClose={() => setShowJoinConfirm(false)}
        onConfirm={performJoinCommunity}
        title={
          String(c.privacy).toUpperCase() === 'PRIVATE'
            ? 'Request to Join Community'
            : 'Join Community'
        }
        message={
          String(c.privacy).toUpperCase() === 'PRIVATE'
            ? `Are you sure you want to request to join "${c.name}"? Community moderators will review your request.`
            : `Are you sure you want to join "${c.name}"? You will be able to participate, see posts, and connect with other members.`
        }
        confirmLabel={
          String(c.privacy).toUpperCase() === 'PRIVATE'
            ? 'Send Request'
            : 'Join'
        }
        cancelLabel="Cancel"
        isDanger={false}
        isLoading={acting}
      />
      <ShareCommunityModal
        isOpen={shareModalOpen}
        onClose={() => setShareModalOpen(false)}
        community={c}
      />
    </div>
  );
}

/* ════════════════════════════════════════════════════════════════════════════
   MAIN PAGE
════════════════════════════════════════════════════════════════════════════ */

function RecommendedCarousel({
  recommended,
  loading,
  onSelect,
  onJoin,
  joiningId,
}: {
  recommended: CommunityData[];
  loading: boolean;
  onSelect: (c: CommunityData) => void;
  onJoin: (e: React.MouseEvent, c: CommunityData) => void;
  joiningId: number | null;
}) {
  const [isPaused, setIsPaused] = useState(false);
  const [visibleCards, setVisibleCards] = useState(2);
  const [transitionEnabled, setTransitionEnabled] = useState(true);
  const touchStartX = useRef<number | null>(null);

  // Dynamically update the number of visible cards based on screen size
  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth < 768) {
        setVisibleCards(1);
      } else {
        setVisibleCards(2);
      }
    };
    handleResize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // Filter out joined communities
  const availableRecommended = useMemo(() => {
    return recommended.filter((c) => !c.isMember);
  }, [recommended]);

  // Base list of items repeated to ensure smooth circular buffer
  const baseItems = useMemo(() => {
    if (availableRecommended.length === 0) return [];
    if (availableRecommended.length === 1) return availableRecommended;
    if (availableRecommended.length < 4) {
      let repeated = [...availableRecommended];
      while (repeated.length < 6) {
        repeated = [...repeated, ...availableRecommended];
      }
      return repeated;
    }
    return availableRecommended;
  }, [availableRecommended]);

  // Extended circular list with clones before and after
  const carouselItems = useMemo(() => {
    if (baseItems.length <= 1) return baseItems;
    const clonesBefore = baseItems.slice(-visibleCards);
    const clonesAfter = baseItems.slice(0, visibleCards);
    return [...clonesBefore, ...baseItems, ...clonesAfter];
  }, [baseItems, visibleCards]);

  const [currentIndex, setCurrentIndex] = useState(visibleCards);

  // Sync index when visibleCards or baseItems change
  useEffect(() => {
    setCurrentIndex(visibleCards);
    setTransitionEnabled(true);
  }, [visibleCards, baseItems.length]);

  const nextSlide = useCallback(() => {
    if (baseItems.length <= 1) return;
    setTransitionEnabled(true);
    setCurrentIndex((prev) => prev + 1);
  }, [baseItems.length]);

  const prevSlide = useCallback(() => {
    if (baseItems.length <= 1) return;
    setTransitionEnabled(true);
    setCurrentIndex((prev) => prev - 1);
  }, [baseItems.length]);

  // Seamless wrap-around after transition completes
  const handleTransitionEnd = () => {
    if (baseItems.length <= 1) return;

    if (currentIndex >= baseItems.length + visibleCards) {
      setTransitionEnabled(false);
      setCurrentIndex(visibleCards);
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          setTransitionEnabled(true);
        });
      });
    } else if (currentIndex <= 0) {
      setTransitionEnabled(false);
      setCurrentIndex(baseItems.length);
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          setTransitionEnabled(true);
        });
      });
    }
  };

  // Autoplay loop
  useEffect(() => {
    if (isPaused || baseItems.length <= 1) return;
    const interval = setInterval(nextSlide, 5000);
    return () => clearInterval(interval);
  }, [isPaused, nextSlide, baseItems.length]);

  if (loading) {
    return (
      <div className="relative min-h-[260px] rounded-2xl border border-base-300 bg-base-200/20">
        <LoadingAnimation overlay label="Loading communities" />
      </div>
    );
  }

  if (availableRecommended.length === 0) return null;

  const handleTouchStart = (e: React.TouchEvent) => {
    setIsPaused(true);
    touchStartX.current = e.touches[0].clientX;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartX.current === null) return;
    const diffX = e.changedTouches[0].clientX - touchStartX.current;
    if (Math.abs(diffX) > 40) {
      if (diffX > 0) prevSlide();
      else nextSlide();
    }
    touchStartX.current = null;
    setTimeout(() => setIsPaused(false), 2000);
  };

  const getCardImage = (c: CommunityData) => {
    let url = c.coverImageUrl || c.avatarUrl || '';
    if (!url) {
      return `https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?auto=format&fit=crop&w=500&q=80`;
    }
    if (url.includes('unsplash.com')) {
      url = url.replace(/w=\d+/, 'w=500').replace(/q=\d+/, 'q=90');
    }
    return url;
  };

  const dotCount = availableRecommended.length;
  const activeDotIndex =
    dotCount > 0
      ? (((currentIndex - visibleCards) % dotCount) + dotCount) % dotCount
      : 0;

  return (
    <div
      className="relative flex flex-col w-full select-none overflow-hidden space-y-3.5"
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
    >
      <div className="w-full text-left">
        <div className="flex items-center gap-2">
          <Sparkles
            size={15}
            className="text-[#1D4ED8] dark:text-blue-400 stroke-[2.5]"
          />
          <h2 className="font-black text-sm sm:text-base tracking-tight text-slate-900 dark:text-white">
            Recommended Communities
          </h2>
        </div>
      </div>

      {/* Carousel Container */}
      <div className="relative w-full px-6 sm:px-10">
        {/* Left Arrow Button */}
        {baseItems.length > 1 && (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              prevSlide();
            }}
            className="absolute left-0 top-1/2 -translate-y-1/2 z-20 flex items-center justify-center w-7 h-7 sm:w-8 sm:h-8 rounded-full bg-base-200 border border-base-300/80 shadow-[0_4px_12px_rgba(0,0,0,0.08)] hover:shadow-[0_6px_16px_rgba(0,0,0,0.12)] text-base-content hover:text-[#1D4ED8] hover:scale-105 active:scale-95 transition-all cursor-pointer"
            aria-label="Previous slide"
          >
            <ChevronLeft size={15} className="sm:w-4 sm:h-4" />
          </button>
        )}

        {/* Right Arrow Button */}
        {baseItems.length > 1 && (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              nextSlide();
            }}
            className="absolute right-0 top-1/2 -translate-y-1/2 z-20 flex items-center justify-center w-7 h-7 sm:w-8 sm:h-8 rounded-full bg-base-200 border border-base-300/80 shadow-[0_4px_12px_rgba(0,0,0,0.08)] hover:shadow-[0_6px_16px_rgba(0,0,0,0.12)] text-base-content hover:text-[#1D4ED8] hover:scale-105 active:scale-95 transition-all cursor-pointer"
            aria-label="Next slide"
          >
            <ChevronRight size={15} className="sm:w-4 sm:h-4" />
          </button>
        )}

        {/* Viewport/Track */}
        <div
          className="overflow-hidden w-full"
          onTouchStart={handleTouchStart}
          onTouchEnd={handleTouchEnd}
        >
          <div
            className={`flex -mx-2.5 ${transitionEnabled ? 'transition-transform duration-500 ease-out' : ''}`}
            style={{
              transform: `translateX(-${currentIndex * (100 / visibleCards)}%)`,
            }}
            onTransitionEnd={handleTransitionEnd}
          >
            {carouselItems.map((c, index) => {
              return (
                <div
                  key={`${c.id}-${index}`}
                  className="flex flex-col"
                  style={{
                    width: `${100 / visibleCards}%`,
                    flexShrink: 0,
                    padding: '0 10px',
                  }}
                  onClick={(e) => {
                    if ((e.target as HTMLElement).closest('button, a')) return;
                    onSelect(c);
                  }}
                >
                  {/* Card Container */}
                  <div
                    className={`w-full bg-base-200 rounded-3xl border border-base-300 dark:border-white/10 p-4 sm:p-4.5 flex flex-col justify-between shadow-xs hover:shadow-md transition-all duration-300 min-h-[340px] sm:min-h-[360px] h-full cursor-pointer hover:border-base-content/20 dark:hover:border-white/25 relative ${isCommunityDeleted(c) ? 'opacity-50' : ''}`}
                  >
                    {isCommunityDeleted(c) && (
                      <div className="absolute top-2.5 right-2.5 bg-red-100 dark:bg-red-950/80 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-500/35 text-[10px] font-black uppercase tracking-wider px-2 py-0.5 rounded-full flex items-center gap-1 z-10 animate-pulse">
                        <AlertTriangle size={10} className="shrink-0" />
                        <span>Deleting ({getDeletionDaysLeft(c)}d left)</span>
                      </div>
                    )}
                    <div className="flex flex-col h-full justify-between">
                      <div>
                        {/* Cover Image at Top */}
                        <div className="w-full aspect-[16/10] max-h-[140px] sm:max-h-[160px] rounded-2xl overflow-hidden mb-3 bg-base-300">
                          <img
                            src={getCardImage(c)}
                            alt={c.name}
                            className="w-full h-full object-cover transition-transform duration-500 hover:scale-105 pointer-events-none"
                            onError={(e) => {
                              (e.target as HTMLImageElement).src =
                                `https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?auto=format&fit=crop&w=500&q=80`;
                            }}
                          />
                        </div>

                        {/* Tags Row */}
                        <div className="mb-2 flex flex-wrap items-center gap-1.5 min-h-5">
                          {String(c.privacy).toUpperCase() === 'PRIVATE' ? (
                            <span className="inline-flex items-center gap-1 rounded-full border border-base-content/10 bg-base-300/80 px-2 py-0.5 text-[10px] font-semibold text-base-content/70">
                              <Lock size={10} /> Private
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 rounded-full border border-base-content/10 bg-base-300/80 px-2 py-0.5 text-[10px] font-semibold text-base-content/70">
                              <Globe size={10} /> Public
                            </span>
                          )}
                          {getCommunityRankLabel(c) && (
                            <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/25 dark:border-amber-400/30 bg-amber-500/10 dark:bg-amber-400/10 px-2 py-0.5 text-[10px] font-black text-amber-700 dark:text-amber-400">
                              <Trophy size={10} /> {getCommunityRankLabel(c)}
                            </span>
                          )}
                          <span className="inline-flex items-center gap-1 rounded-full border border-emerald-500/25 dark:border-emerald-400/30 bg-emerald-500/10 dark:bg-emerald-400/10 px-2 py-0.5 text-[10px] font-bold text-emerald-700 dark:text-emerald-400">
                            <Activity size={10} /> {getCommunityMomentum(c)}{' '}
                            momentum
                          </span>
                          {c.category && (
                            <span className="text-[10px] font-medium text-base-content/50 uppercase tracking-wider px-2 py-0.5 rounded-md bg-base-300/60 dark:bg-white/5 truncate max-w-[90px]">
                              {c.category}
                            </span>
                          )}
                        </div>

                        {/* Title */}
                        <h3 className="font-extrabold text-sm sm:text-base text-base-content leading-tight mb-1 truncate notranslate">
                          {decodeHTML(c.name)}
                        </h3>

                        {/* Description */}
                        <p className="text-[11px] sm:text-xs text-base-content/60 line-clamp-2 leading-relaxed">
                          {decodeHTML(c.description) ||
                            'Civic community for local issues and accountability.'}
                        </p>
                      </div>

                      {/* Footer Details */}
                      <div className="flex items-center justify-between mt-3 pt-3 border-t border-base-content/5">
                        <div className="flex items-center gap-1.5 text-base-content/60 min-w-0 shrink-0">
                          <Users
                            size={13}
                            className="text-base-content/40 shrink-0"
                          />
                          <span className="text-xs font-semibold whitespace-nowrap">
                            {c.memberCount.toLocaleString()}{' '}
                            {c.memberCount === 1 ? 'member' : 'members'}
                          </span>
                        </div>

                        <button
                          type="button"
                          onPointerDown={(e) => e.stopPropagation()}
                          onMouseDown={(e) => e.stopPropagation()}
                          onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            onJoin(e, c);
                          }}
                          disabled={
                            joiningId === c.id ||
                            c.isMember ||
                            c.hasPendingRequest
                          }
                          className={`relative z-20 btn btn-xs rounded-full px-4 h-7 text-[10px] font-bold uppercase tracking-wider border-none shrink-0 transition-all active:scale-95 cursor-pointer ${
                            c.isMember
                              ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                              : c.hasPendingRequest
                                ? 'bg-amber-400 text-black font-bold opacity-90 cursor-not-allowed'
                                : 'bg-[#1D4ED8] hover:bg-blue-800 text-white shadow-xs'
                          }`}
                        >
                          {joiningId === c.id ? (
                            <span className="loading loading-spinner loading-xs" />
                          ) : c.isMember ? (
                            'Joined'
                          ) : c.hasPendingRequest ? (
                            'Pending'
                          ) : String(c.privacy).toUpperCase() === 'PRIVATE' ? (
                            'Request'
                          ) : (
                            'Join'
                          )}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Control Dots */}
      {dotCount > 1 && (
        <div className="flex justify-center gap-1.5 mt-3">
          {Array.from({ length: dotCount }).map((_, i) => (
            <button
              key={i}
              onClick={() => {
                setTransitionEnabled(true);
                setCurrentIndex(visibleCards + i);
              }}
              className={`h-1.5 rounded-full transition-all duration-300 cursor-pointer ${
                activeDotIndex === i
                  ? 'bg-[#1D4ED8] w-5'
                  : 'bg-base-content/20 w-1.5'
              }`}
              aria-label={`Go to slide ${i + 1}`}
            />
          ))}
        </div>
      )}
    </div>
  );
}

const Community = () => {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [committed, setCommitted] = useState('');
  const [searchResults, setSearchResults] = useState<CommunityData[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchHasMore, setSearchHasMore] = useState(false);
  const [searchCursor, setSearchCursor] = useState<number | null>(null);
  const [searchLoadingMore, setSearchLoadingMore] = useState(false);
  const [suggestions, setSuggestions] = useState<CommunityData[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [myCommunities, setMyCommunities] = useState<CommunityData[]>([]);
  const [myCommunitiesLoading, setMyCommunitiesLoading] = useState(true);
  const [selected, setSelected] = useState<CommunityData | null>(null);
  const [adminTarget, setAdminTarget] = useState<CommunityData | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const [searchParams, setSearchParams] = useSearchParams();
  const view =
    (searchParams.get('tab') as 'default' | 'joined' | 'owned') || 'default';

  const setView = (
    newTabOrFn:
      | 'default'
      | 'joined'
      | 'owned'
      | ((
          prev: 'default' | 'joined' | 'owned',
        ) => 'default' | 'joined' | 'owned'),
  ) => {
    const nextTab =
      typeof newTabOrFn === 'function' ? newTabOrFn(view) : newTabOrFn;
    setSearchParams(
      (prev) => {
        if (nextTab === 'default') {
          prev.delete('tab');
        } else {
          prev.set('tab', nextTab);
        }
        return prev;
      },
      { replace: true },
    );
  };

  const [recommended, setRecommended] = useState<CommunityData[]>([]);
  const [recommendedLoading, setRecommendedLoading] = useState(true);
  const [joiningId, setJoiningId] = useState<number | null>(null);

  const [viewDropdownOpen, setViewDropdownOpen] = useState(false);
  const viewDropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        viewDropdownRef.current &&
        !viewDropdownRef.current.contains(e.target as Node)
      ) {
        setViewDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const { id: slugParam } = useParams<{ id?: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const communityQueryParam = searchParams.get('community');
  const activeSlug =
    slugParam || communityQueryParam || searchParams.get('slug') || null;

  const openCommunity = (
    c: CommunityData,
    initialTab?: 'posts' | 'chat' | 'about' | 'manage',
  ) => {
    const cachedRoles = getCachedCommunityRoles();
    const rawRole =
      c.role ||
      c.currentUserRole ||
      c.memberRole ||
      cachedRoles[c.id] ||
      (c.isOwner
        ? 'OWNER'
        : c.isAdmin
          ? 'ADMIN'
          : c.isModerator
            ? 'MODERATOR'
            : c.isMember
              ? 'MEMBER'
              : null);
    const resolvedRole =
      typeof rawRole === 'string' ? rawRole.toUpperCase() : rawRole;
    const enriched: CommunityData = {
      ...c,
      role: resolvedRole,
      currentUserRole: resolvedRole,
      memberRole: resolvedRole,
      isModerator: resolvedRole === 'MODERATOR' || c.isModerator,
      isAdmin:
        resolvedRole === 'ADMIN' || resolvedRole === 'OWNER' || c.isAdmin,
      isOwner: resolvedRole === 'OWNER' || c.isOwner,
    };
    setSelected(enriched);
    const search = new URLSearchParams(location.search);
    if (initialTab) {
      search.set('tab', initialTab);
    }
    const searchStr = search.toString() ? `?${search.toString()}` : '';
    if (slugParam) {
      navigate(`/communities/${c.slug || c.id}${searchStr}`, { replace: true });
    } else {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          next.set('community', c.slug || String(c.id));
          if (initialTab) {
            next.set('tab', initialTab);
          }
          return next;
        },
        { replace: true },
      );
    }
  };

  const closeCommunity = () => {
    setSelected(null);

    // Clean up community detail parameters from search params
    const nextParams = new URLSearchParams(location.search);
    nextParams.delete('community');
    nextParams.delete('slug');
    nextParams.delete('sharedPostId');

    // If the tab is a community detail tab, remove it so it defaults on the listing page
    const currentTab = nextParams.get('tab');
    if (
      currentTab === 'chat' ||
      currentTab === 'posts' ||
      currentTab === 'about' ||
      currentTab === 'manage'
    ) {
      nextParams.delete('tab');
    }

    const searchStr = nextParams.toString();
    const targetUrl = '/communities' + (searchStr ? `?${searchStr}` : '');

    if (slugParam) {
      navigate(targetUrl, { replace: true });
    } else {
      setSearchParams(nextParams, { replace: true });
    }
  };

  // Load recommended
  const fetchRecommended = useCallback(async () => {
    setRecommendedLoading(true);
    try {
      const res = await cachedFetch(apiUrl('/api/communities?size=30'), {
        headers: hdrs(),
      });
      if (!res.ok) throw new Error();
      const d = await res.json();
      const list =
        d?.data?.data ?? d?.data?.content ?? d?.data ?? d?.content ?? d ?? [];
      if (Array.isArray(list)) {
        const pendingIds = getPendingLocal();
        const mapped = list.map(
          (c: any): CommunityData => ({
            id: c.id,
            name: c.name || c.communityName || '',
            slug: c.slug || c.communitySlug || String(c.id),
            description: c.description || c.communityDescription || '',
            category: c.category || c.communityCategory || 'General',
            privacy: c.privacy || c.communityPrivacy || 'PUBLIC',
            avatarUrl: c.avatarUrl || null,
            coverImageUrl: c.coverImageUrl || null,
            memberCount: c.memberCount ?? c.communityMemberCount ?? 0,
            postCount: c.postCount ?? 0,
            isMember: c.isMember ?? c.member ?? false,
            isOwner: c.isOwner ?? c.owner ?? false,
            createdAt: c.createdAt ?? new Date().toISOString(),
            hasPendingRequest:
              (c.isMember ?? c.member ?? false)
                ? false
                : c.hasPendingRequest === true ||
                  c.pendingRequest === true ||
                  pendingIds.includes(String(c.id)),
          }),
        );
        const joinedIds = new Set(myCommunities.map((x) => x.id));
        const filtered = mapped.filter(
          (x) =>
            !joinedIds.has(x.id) &&
            String(x.privacy).toUpperCase() !== 'SECRET',
        );
        setRecommended(filtered);
      }
    } catch {
      setRecommended([]);
    } finally {
      setRecommendedLoading(false);
    }
  }, [myCommunities]);

  useEffect(() => {
    fetchRecommended();
  }, [fetchRecommended]);

  const handleCarouselJoin = async (e: React.MouseEvent, c: CommunityData) => {
    e.stopPropagation();
    if (!getAuthToken()) {
      navigate('/login');
      return;
    }
    setJoiningId(c.id);
    try {
      const res = await fetch(apiUrl(`/api/communities/${c.id}/join`), {
        method: 'POST',
        headers: hdrs(),
        body: JSON.stringify({}),
      });
      if (res.status === 401) {
        showToast.error('Please log in.');
        return;
      }
      const d = await res.json();
      const joined = d?.data?.joined ?? false;
      const isPrivate = String(c.privacy).toUpperCase() === 'PRIVATE';
      const newHasPending =
        !joined &&
        (isPrivate ||
          d?.data?.hasPendingRequest === true ||
          d?.data?.status === 'PENDING');

      if (newHasPending) {
        addPendingLocal(c.id);
      } else if (joined) {
        removePendingLocal(c.id);
      }

      setRecommended((prev) =>
        prev.map((item) =>
          item.id === c.id
            ? {
                ...item,
                isMember: joined,
                hasPendingRequest: newHasPending,
                memberCount: joined ? item.memberCount + 1 : item.memberCount,
              }
            : item,
        ),
      );

      syncMembership(c.id, joined, joined ? 1 : 0, newHasPending);
      if (joined) {
        showToast.success('👋 Hey there, newcomer! Make yourself at home.');
      } else if (newHasPending) {
        showToast.info(
          'Request sent. We saved you a seat while the admins review it.',
        );
      }
    } catch {
      showToast.error('Action failed.');
    } finally {
      setJoiningId(null);
    }
  };

  const inputRef = useRef<HTMLInputElement>(null);
  const quickDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const fetchMyCommunities = useCallback(async () => {
    const token = getAuthToken();
    if (!token) {
      setMyCommunities([]);
      setMyCommunitiesLoading(false);
      return;
    }
    let currentUserId: number | null = null;
    try {
      const decoded: any = jwtDecode(token);
      currentUserId = Number(decoded.id || decoded.userId || decoded.sub);
    } catch {}

    setMyCommunitiesLoading(true);
    // Silently evict the server-side Caffeine cache for this user before fetching.
    // This ensures we always get fresh role data (isModerator, isAdmin) from the DB,
    // not a stale cached response from before a role change was applied.
    const now = Date.now();
    if (now - lastRefreshCacheTime > REFRESH_CACHE_THROTTLE_MS) {
      try {
        await fetch(apiUrl('/api/communities/me/refresh-cache'), {
          method: 'POST',
          headers: hdrs(),
        });
        lastRefreshCacheTime = now;
      } catch {
        /* non-critical — continue even if endpoint is not yet deployed */
      }
    }
    try {
      const [joinedRes, ownedRes] = await Promise.allSettled([
        axiosInstance.get('/api/communities/me?limit=100'),
        axiosInstance.get('/api/communities/owned'),
      ]);
      let joined: CommunityData[] = [];
      const cachedRoles = getCachedCommunityRoles();
      if (joinedRes.status === 'fulfilled' && joinedRes.value.status === 200) {
        const j = joinedRes.value.data;
        const rawJoined =
          j?.data?.content ?? j?.data?.data ?? j?.data ?? j?.content ?? j ?? [];
        const joinedList = Array.isArray(rawJoined) ? rawJoined : [];
        joined = joinedList.map((c: any) => {
          const isOwner = resolveIsOwner(c, currentUserId);
          const isMem = Boolean(
            c.isMember ??
              c.member ??
              (isOwner ||
                c.role ||
                c.memberRole ||
                c.currentUserRole ||
                cachedRoles[c.id]),
          );
          const computedRole = isOwner
            ? 'OWNER'
            : c.isAdmin || c.admin
              ? 'ADMIN'
              : c.isModerator || c.moderator
                ? 'MODERATOR'
                : isMem
                  ? 'MEMBER'
                  : null;
          const rawRole =
            c.role ||
            c.currentUserRole ||
            c.memberRole ||
            c.membershipRole ||
            c.userRole ||
            c.membership?.role ||
            c.member?.role ||
            computedRole ||
            cachedRoles[c.id];
          const role =
            typeof rawRole === 'string' ? rawRole.toUpperCase() : rawRole;
          const backendPending =
            c.hasPendingRequest === true ||
            c.pendingRequest === true ||
            c.hasPendingRequest === 'true' ||
            c.pendingRequest === 'true';
          const localPending = getPendingLocal().includes(String(c.id));
          // Clear local pending if: user is now a member, OR backend explicitly says not pending
          if (isMem || (!backendPending && localPending))
            removePendingLocal(c.id);
          if (role) {
            setCachedCommunityRole(c.id, role);
          }
          return {
            ...c,
            requirePostApproval: resolvePostApprovalFlag(c),
            isMember: isMem,
            isOwner: isOwner,
            isAdmin:
              role === 'ADMIN' || role === 'OWNER' || c.isAdmin || c.admin,
            isModerator: role === 'MODERATOR' || c.isModerator || c.moderator,
            role: role,
            currentUserRole: role,
            memberRole: role,
            hasPendingRequest: isMem ? false : backendPending,
          };
        });
      }
      let owned: CommunityData[] = [];
      if (ownedRes.status === 'fulfilled' && ownedRes.value.status === 200) {
        const o = ownedRes.value.data;
        const rawOwned =
          o?.data?.content ?? o?.data?.data ?? o?.data ?? o?.content ?? o ?? [];
        const ownedList = Array.isArray(rawOwned) ? rawOwned : [];
        owned = ownedList.map((c: any) => {
          removePendingLocal(c.id);
          const isOwner = resolveIsOwner(c, currentUserId);
          const rawRole =
            c.role ||
            c.currentUserRole ||
            c.memberRole ||
            (isOwner
              ? 'OWNER'
              : c.isAdmin
                ? 'ADMIN'
                : c.isModerator
                  ? 'MODERATOR'
                  : 'MEMBER');
          const role =
            typeof rawRole === 'string' ? rawRole.toUpperCase() : 'OWNER';
          return {
            ...c,
            requirePostApproval: resolvePostApprovalFlag(c),
            isMember: true,
            isOwner: isOwner,
            isAdmin: role === 'ADMIN' || role === 'OWNER' || c.isAdmin,
            isModerator: role === 'MODERATOR' || c.isModerator,
            role: role,
            currentUserRole: role,
            memberRole: role,
            hasPendingRequest: false,
          };
        });
      }
      const seen = new Set<number>();
      const merged: CommunityData[] = [];
      for (const c of [...owned, ...joined]) {
        if (!seen.has(c.id)) {
          seen.add(c.id);
          merged.push(c);
        } else {
          const idx = merged.findIndex((x) => x.id === c.id);
          if (idx !== -1) {
            const isOwner = merged[idx].isOwner || c.isOwner;
            const role = isOwner ? 'OWNER' : merged[idx].role || c.role;
            merged[idx] = {
              ...c,
              ...merged[idx],
              isOwner: isOwner,
              isMember: merged[idx].isMember || c.isMember,
              role: role,
              currentUserRole: role,
              memberRole: role,
            };
          }
        }
      }
      setMyCommunities(merged);

      // Auto-sync joined communities to offline search cache
      merged.forEach((item) => {
        cacheSuggestion({
          kind: 'COMMUNITY',
          id: item.id,
          displayText: item.name,
          subText: item.description,
          avatarUrl: item.avatarUrl || undefined,
          slug: item.slug,
        });
      });
    } catch {
      setMyCommunities([]);
    } finally {
      setMyCommunitiesLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMyCommunities();
  }, [fetchMyCommunities]);

  // Listen for sidebar or global communities refresh events
  useEffect(() => {
    const handleRefreshActiveFeed = (e: Event) => {
      const customEvent = e as CustomEvent;
      const target = customEvent.detail?.target;
      if (target === 'communities') {
        const scrollContainers = document.querySelectorAll(
          'main.overflow-y-auto, .overflow-y-auto',
        );
        scrollContainers.forEach((el) =>
          el.scrollTo({ top: 0, behavior: 'smooth' }),
        );
        fetchRecommended();
        fetchMyCommunities();
      }
    };
    window.addEventListener('refreshActiveFeed', handleRefreshActiveFeed);
    return () =>
      window.removeEventListener('refreshActiveFeed', handleRefreshActiveFeed);
  }, [fetchRecommended, fetchMyCommunities]);

  // ── Auto-select community from URL /:slug or search navigation state ──────────
  useEffect(() => {
    let active = true;
    const navState = (location.state ?? {}) as {
      selectedCommunity?: any;
      searchQuery?: string;
    };

    // If navigated with a pre-built community object (from search overlay), select immediately
    if (navState.selectedCommunity) {
      setSelected(navState.selectedCommunity);
      window.history.replaceState({}, ''); // clear state so back nav doesn't re-trigger
      return;
    }

    // If routed to /communities/:slug or ?community=slug, fetch and open that community
    if (activeSlug) {
      axiosInstance
        .get(`/api/communities/${activeSlug}`)
        .then((res) => {
          if (!active) return;
          const raw = res.data?.data?.data ?? res.data?.data ?? res.data;
          if (raw?.id) {
            const cachedRoles = getCachedCommunityRoles();
            const token = getAuthToken();
            let currentUserId: number | null = null;
            if (token) {
              try {
                const decoded: any = jwtDecode(token);
                currentUserId = Number(
                  decoded.id || decoded.userId || decoded.sub,
                );
              } catch {}
            }
            const isOwner = resolveIsOwner(raw, currentUserId);
            const rawRole =
              raw.role ||
              raw.currentUserRole ||
              raw.memberRole ||
              cachedRoles[raw.id] ||
              (isOwner
                ? 'OWNER'
                : raw.isAdmin
                  ? 'ADMIN'
                  : raw.isModerator
                    ? 'MODERATOR'
                    : raw.isMember
                      ? 'MEMBER'
                      : null);
            const resolvedRole =
              typeof rawRole === 'string' ? rawRole.toUpperCase() : rawRole;
            const mapped: CommunityData = {
              id: raw.id,
              name: raw.name || raw.communityName || '',
              slug: raw.slug || raw.communitySlug || String(raw.id),
              description: raw.description || raw.communityDescription || '',
              category: raw.category || raw.communityCategory || 'General',
              privacy: raw.privacy || raw.communityPrivacy || 'PUBLIC',
              avatarUrl: raw.avatarUrl || null,
              coverImageUrl: raw.coverImageUrl || null,
              memberCount: raw.memberCount ?? raw.communityMemberCount ?? 0,
              postCount: raw.postCount ?? 0,
              isMember: raw.isMember ?? raw.member ?? false,
              isOwner: isOwner,
              createdAt: raw.createdAt ?? new Date().toISOString(),
              hasPendingRequest: raw.hasPendingRequest ?? false,
              role: resolvedRole,
              currentUserRole: resolvedRole,
              memberRole: resolvedRole,
              isModerator: resolvedRole === 'MODERATOR' || raw.isModerator,
              isAdmin:
                resolvedRole === 'ADMIN' ||
                resolvedRole === 'OWNER' ||
                raw.isAdmin,
            };
            setSelected(mapped);
          }
        })
        .catch(() => {
          /* slug not found — stay on list view */
        });
    } else {
      setSelected(null);
      setAdminTarget(null);
    }

    // If navigated with a hashtag search query (from HashtagCard)
    if (navState.searchQuery) {
      setQuery(navState.searchQuery);
      setCommitted(navState.searchQuery);
      doSearch(navState.searchQuery, null, true);
      window.history.replaceState({}, '');
    }

    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeSlug]);

  // Fallback for numeric community ID in activeSlug when direct single fetch fails
  useEffect(() => {
    const isNumeric = activeSlug && /^\d+$/.test(activeSlug);
    if (
      isNumeric &&
      !selected &&
      !myCommunitiesLoading &&
      myCommunities.length > 0
    ) {
      const matched = myCommunities.find(
        (c) => String(c.id) === String(activeSlug),
      );
      if (matched) {
        openCommunity(matched);
      }
    }
  }, [activeSlug, selected, myCommunities, myCommunitiesLoading]);

  useEffect(() => {
    if (quickDebounceRef.current) clearTimeout(quickDebounceRef.current);
    const q = query.trim();
    if (q.length < 2) {
      setSuggestions([]);
      return;
    }
    quickDebounceRef.current = setTimeout(async () => {
      try {
        const res = await fetch(
          apiUrl(`/api/search/quick?q=${encodeURIComponent(q)}`),
          { headers: hdrs() },
        );
        if (!res.ok) return;
        const d = await res.json();
        const allHits: any[] = Array.isArray(d?.data)
          ? d.data
          : Array.isArray(d?.data?.data)
            ? d.data.data
            : (d?.content ?? []);
        const communityHits = allHits.filter(
          (r: any) => r.type === 'COMMUNITY' || r.resultType === 'COMMUNITY',
        );
        const mapped = communityHits.map(
          (r: any): CommunityData => ({
            id: r.communityId ?? r.id,
            name: r.communityName ?? r.name ?? '',
            slug: r.communitySlug ?? r.slug ?? String(r.communityId ?? r.id),
            description: r.communityDescription ?? r.description ?? '',
            category: r.communityCategory ?? r.category ?? null,
            tags: null,
            avatarUrl:
              r.communityAvatar ??
              r.communityAvatarUrl ??
              r.avatarUrl ??
              r.avatar ??
              r.communityLogo ??
              null,
            coverImageUrl: null,
            privacy: (r.communityPrivacy ??
              r.privacy ??
              'PUBLIC') as CommunityData['privacy'],
            locationName: r.locationName ?? null,
            memberCount: r.communityMemberCount ?? r.memberCount ?? 0,
            postCount: 0,
            isMember: r.communityIsMember ?? r.isMember ?? r.member ?? false,
            isOwner: r.communityIsOwner ?? r.isOwner ?? r.owner ?? false,
            createdAt: r.createdAt ?? new Date().toISOString(),
            hasPendingRequest:
              (r.communityIsMember ?? r.isMember ?? r.member ?? false)
                ? false
                : r.hasPendingRequest === true ||
                  r.pendingRequest === true ||
                  r.hasPendingRequest === 'true' ||
                  r.pendingRequest === 'true' ||
                  getPendingLocal().includes(String(r.communityId ?? r.id)),
          }),
        );
        setSuggestions(mapped);
      } catch {
        setSuggestions([]);
      }
    }, 300);
    return () => {
      if (quickDebounceRef.current) clearTimeout(quickDebounceRef.current);
    };
  }, [query]);

  const doSearch = useCallback(
    async (q: string, cur: number | null, replace: boolean) => {
      if (!q.trim()) return;
      if (replace) setSearchLoading(true);
      else setSearchLoadingMore(true);
      try {
        const p = new URLSearchParams({ q, type: 'COMMUNITY', limit: '20' });
        if (cur !== null) p.set('cursor', String(cur));
        const res = await fetch(apiUrl(`/api/search/type?${p}`), {
          headers: hdrs(),
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const d = await res.json();
        const paged = d?.hasMore !== undefined ? d : (d?.data ?? d);
        const rawItems: any[] = Array.isArray(paged?.data)
          ? paged.data
          : (paged?.content ?? []);
        const mapped = rawItems.map((r: any): CommunityData => {
          const cId = r.communityId ?? r.id;
          const isMem = r.communityIsMember ?? r.isMember ?? r.member ?? false;
          const backendPending =
            r.hasPendingRequest === true ||
            r.pendingRequest === true ||
            r.hasPendingRequest === 'true' ||
            r.pendingRequest === 'true';
          const localPending = getPendingLocal().includes(String(cId));
          if (isMem || (!backendPending && localPending))
            removePendingLocal(cId);
          return {
            id: cId,
            name: r.communityName ?? r.name ?? '',
            slug: r.communitySlug ?? r.slug ?? String(cId),
            description: r.communityDescription ?? r.description ?? '',
            category: r.communityCategory ?? r.category ?? null,
            tags: null,
            avatarUrl:
              r.communityAvatar ??
              r.communityAvatarUrl ??
              r.avatarUrl ??
              r.avatar ??
              r.communityLogo ??
              null,
            coverImageUrl: null,
            privacy: (r.communityPrivacy ??
              r.privacy ??
              'PUBLIC') as CommunityData['privacy'],
            locationName: r.locationName ?? null,
            memberCount: r.communityMemberCount ?? r.memberCount ?? 0,
            postCount: r.postCount ?? 0,
            isMember: isMem,
            isOwner: r.communityIsOwner ?? r.isOwner ?? r.owner ?? false,
            createdAt: r.createdAt ?? new Date().toISOString(),
            allowMemberPosts: r.allowMemberPosts,
            requirePostApproval: resolvePostApprovalFlag(r),
            feedEligible: r.feedEligible,
            hasPendingRequest: isMem ? false : backendPending,
          };
        });
        setSearchResults((prev) => (replace ? mapped : [...prev, ...mapped]));
        setSearchHasMore(paged?.hasMore ?? false);
        setSearchCursor(paged?.nextCursor ?? null);
      } catch {
        if (replace) setSearchResults([]);
      } finally {
        if (replace) setSearchLoading(false);
        else setSearchLoadingMore(false);
      }
    },
    [],
  );

  function commitSearch(q: string) {
    const t = q.trim();
    setQuery(t);
    setCommitted(t);
    setShowSuggestions(false);
    setSuggestions([]);
    setSearchResults([]);
    setSearchCursor(null);
    setView('default');
    if (t) doSearch(t, null, true);
  }

  function syncMembership(
    id: number,
    isMember: boolean,
    delta: number,
    hasPendingRequest?: boolean,
    communityData?: CommunityData,
  ) {
    queryClient.invalidateQueries({ queryKey: ['my-communities'] });
    if (communityData?.role) {
      setCachedCommunityRole(id, communityData.role);
    }
    const upd = (c: CommunityData): CommunityData =>
      c.id === id
        ? {
            ...c,
            ...(communityData || {}),
            isMember,
            memberCount: c.memberCount + delta,
            hasPendingRequest: hasPendingRequest ?? c.hasPendingRequest,
          }
        : c;
    setSearchResults((p) => p.map(upd));
    setRecommended((p) => p.map(upd));
    setSelected((prev) =>
      prev && prev.id === id
        ? {
            ...prev,
            ...(communityData || {}),
            isMember,
            memberCount: prev.memberCount + delta,
            hasPendingRequest: hasPendingRequest ?? prev.hasPendingRequest,
          }
        : prev,
    );
    setMyCommunities((p) => {
      const e = p.some((c) => c.id === id);
      if (e) {
        if (!isMember && !hasPendingRequest) {
          const item = p.find((c) => c.id === id);
          if (item && !item.isOwner) {
            return p.filter((c) => c.id !== id);
          }
        }
        return p.map(upd);
      }
      const f =
        communityData ||
        searchResults.find((c) => c.id === id) ||
        recommended.find((c) => c.id === id) ||
        (selected && selected.id === id ? selected : null);
      if (f) {
        const normalized: CommunityData = {
          id: f.id,
          name: f.name || '',
          slug: f.slug || String(f.id),
          description: f.description || '',
          category: f.category || 'General',
          privacy: f.privacy || 'PUBLIC',
          avatarUrl: f.avatarUrl || null,
          coverImageUrl: f.coverImageUrl || null,
          memberCount: f.memberCount + delta,
          postCount: f.postCount ?? 0,
          isMember: isMember,
          isOwner: f.isOwner ?? false,
          createdAt: f.createdAt ?? new Date().toISOString(),
          hasPendingRequest: hasPendingRequest ?? false,
        };
        return [...p, normalized];
      }
      return p;
    });
  }

  function handleCreated(c: CommunityData) {
    queryClient.invalidateQueries({ queryKey: ['my-communities'] });
    setShowCreate(false);
    const entry: CommunityData = { ...c, isMember: true, isOwner: true };
    setMyCommunities((prev) => [
      entry,
      ...prev.filter((x) => x.id !== entry.id),
    ]);
    setSelected(entry);
    navigate('/communities/' + entry.slug + location.search);
  }

  function handleCommunityUpdated(updated: CommunityData) {
    queryClient.invalidateQueries({ queryKey: ['my-communities'] });
    if (updated.role) {
      setCachedCommunityRole(updated.id, updated.role);
    }
    setMyCommunities((p) => p.map((c) => (c.id === updated.id ? updated : c)));
    setAdminTarget(updated);
  }

  const isSearching = committed.length > 0;
  const ownedList = myCommunities.filter(
    (c) =>
      c.isOwner ||
      c.isAdmin ||
      c.isModerator ||
      c.role === 'OWNER' ||
      c.role === 'ADMIN' ||
      c.role === 'MODERATOR' ||
      c.currentUserRole === 'OWNER' ||
      c.currentUserRole === 'ADMIN' ||
      c.currentUserRole === 'MODERATOR',
  );
  const joinedOnly = myCommunities.filter(
    (c) =>
      !isCommunityDeleted(c) &&
      !(
        c.isOwner ||
        c.isAdmin ||
        c.isModerator ||
        c.role === 'OWNER' ||
        c.role === 'ADMIN' ||
        c.role === 'MODERATOR' ||
        c.currentUserRole === 'OWNER' ||
        c.currentUserRole === 'ADMIN' ||
        c.currentUserRole === 'MODERATOR'
      ),
  );
  const isCommunityPageLoading =
    !isSearching &&
    (myCommunitiesLoading || (view === 'default' && recommendedLoading));

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12 overflow-hidden">
      <Helmet>
        <title>Local Neighborhood Forums & Online Communities | Govlyx</title>
        <meta
          name="description"
          content="Join ungated neighborhood forums and local community groups near you on Govlyx. Discuss interests, security, and local events."
        />
        <meta
          name="keywords"
          content="local community forum india, neighborhood Q&A, connect with neighbors online, local forums"
        />
      </Helmet>
      <div className="text-left space-y-1">
        <h1 className="text-xl sm:text-2xl font-black text-slate-900 dark:text-white tracking-tight">
          Communities
        </h1>
        <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 font-medium">
          Discover and join communities based on your interests.
        </p>
      </div>

      {/* Search & Create Action Bar */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2.5">
        <div className="relative flex-1">
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search
                className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none"
                size={15}
              />
              <input
                ref={inputRef}
                type="text"
                placeholder="Search communities..."
                className="input input-sm w-full bg-base-200 border border-black/10 dark:border-base-300 rounded-xl pl-10 pr-8 text-xs sm:text-sm h-10 font-medium focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/30"
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setShowSuggestions(true);
                }}
                onFocus={() => setShowSuggestions(true)}
                onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    commitSearch(query);
                  }
                  if (e.key === 'Escape') setShowSuggestions(false);
                }}
              />
              {query && (
                <button
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer"
                  onClick={() => {
                    setQuery('');
                    setCommitted('');
                    setSearchResults([]);
                    setSuggestions([]);
                    inputRef.current?.focus();
                  }}
                >
                  <X size={14} />
                </button>
              )}
              {showSuggestions &&
                query.trim().length >= 2 &&
                suggestions.length > 0 && (
                  <div className="absolute left-0 right-0 top-full mt-1.5 z-50 bg-base-100 dark:bg-base-200 border border-black/10 dark:border-base-300 rounded-2xl shadow-xl overflow-hidden p-1">
                    {suggestions.map((c) => (
                      <button
                        key={c.id}
                        className="w-full flex items-center gap-3 px-3.5 py-2.5 hover:bg-base-300/60 rounded-xl text-left transition-colors cursor-pointer"
                        onMouseDown={(e) => {
                          e.preventDefault();
                          commitSearch(c.name);
                        }}
                      >
                        {c.avatarUrl ? (
                          <img
                            src={c.avatarUrl}
                            alt={c.name}
                            className="w-8 h-8 rounded-full object-cover shrink-0 border border-black/10 dark:border-white/10"
                          />
                        ) : (
                          <span className="w-8 h-8 rounded-full bg-base-300 border border-black/10 dark:border-white/10 flex items-center justify-center shrink-0 text-[#1D4ED8] dark:text-blue-400">
                            <Users size={16} />
                          </span>
                        )}
                        <div className="flex-1 min-w-0">
                          <p className="text-xs sm:text-sm font-bold truncate notranslate">
                            {highlight(c.name, query)}
                          </p>
                          {c.description && (
                            <p className="text-xs text-slate-500 dark:text-slate-400 truncate">
                              {decodeHTML(c.description)}
                            </p>
                          )}
                        </div>
                        <span className="text-xs text-slate-400 shrink-0 flex items-center gap-1 font-medium">
                          <Users size={12} /> {c.memberCount.toLocaleString()}
                        </span>
                      </button>
                    ))}
                  </div>
                )}
            </div>
            <button
              className="btn btn-sm bg-[#1D4ED8] text-white border-none hover:bg-blue-800 h-10 w-10 p-0 rounded-xl flex items-center justify-center shrink-0 shadow-xs cursor-pointer"
              disabled={!query.trim()}
              onClick={() => commitSearch(query)}
              aria-label="Search"
            >
              <Search size={16} />
            </button>
          </div>
        </div>

        <button
          className="bg-[#1D4ED8] hover:bg-blue-800 text-white font-bold text-sm h-10 w-full sm:w-auto px-4 rounded-xl flex items-center justify-center gap-2 transition-all shadow-xs active:scale-95 shrink-0 cursor-pointer"
          onClick={() => setShowCreate(true)}
        >
          <Plus size={16} className="stroke-[2.5] shrink-0" />
          <span>+ Create Community</span>
        </button>
      </div>

      {!isSearching && (
        <>
          {/* Mobile Filter Dropdown (< sm) */}
          <div
            ref={viewDropdownRef}
            className="sm:hidden notranslate text-left relative z-20"
          >
            <button
              type="button"
              onClick={() => setViewDropdownOpen((prev) => !prev)}
              className="w-full bg-base-200 border border-black/10 dark:border-base-300 rounded-2xl px-4 py-2.5 text-xs font-bold text-slate-800 dark:text-slate-200 flex items-center justify-between shadow-xs outline-none focus:outline-none cursor-pointer"
            >
              <div className="flex items-center gap-2">
                {view === 'default' && (
                  <Sparkles size={14} className="text-[#1D4ED8]" />
                )}
                {view === 'owned' && (
                  <Settings size={14} className="text-[#1D4ED8]" />
                )}
                {view === 'joined' && (
                  <Users size={14} className="text-[#1D4ED8]" />
                )}
                <span>
                  {view === 'default' && 'Recommended Communities'}
                  {view === 'owned' &&
                    `Managed Communities (${ownedList.length})`}
                  {view === 'joined' &&
                    `Joined Communities (${joinedOnly.length})`}
                </span>
              </div>
              <ChevronDown
                size={15}
                className={`text-slate-400 transition-transform duration-200 ${
                  viewDropdownOpen ? 'rotate-180 text-[#1D4ED8]' : ''
                }`}
              />
            </button>

            {viewDropdownOpen && (
              <div className="absolute top-full mt-1.5 left-0 right-0 z-50 rounded-2xl border border-black/10 dark:border-base-300 bg-base-100 dark:bg-base-200 backdrop-blur-md shadow-2xl p-1.5 space-y-1 animate-in fade-in zoom-in-95 duration-150">
                {[
                  {
                    id: 'default' as const,
                    label: 'Recommended Communities',
                    icon: Sparkles,
                    count: null,
                  },
                  {
                    id: 'owned' as const,
                    label: 'Managed Communities',
                    icon: Settings,
                    count: ownedList.length,
                  },
                  {
                    id: 'joined' as const,
                    label: 'Joined Communities',
                    icon: Users,
                    count: joinedOnly.length,
                  },
                ].map((opt) => {
                  const isSelected = view === opt.id;
                  const Icon = opt.icon;
                  return (
                    <button
                      key={opt.id}
                      type="button"
                      onClick={() => {
                        setView(opt.id);
                        setViewDropdownOpen(false);
                      }}
                      className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer text-left outline-none focus:outline-none ${
                        isSelected
                          ? 'bg-[#1D4ED8] text-white shadow-xs font-bold'
                          : 'text-slate-700 dark:text-slate-200 hover:bg-base-300/60'
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <Icon
                          size={14}
                          className={
                            isSelected ? 'text-white' : 'text-slate-400'
                          }
                        />
                        <span>{opt.label}</span>
                        {opt.count !== null && opt.count > 0 && (
                          <span
                            className={`text-[10px] px-1.5 py-0.2 rounded-full font-black ${
                              isSelected
                                ? 'bg-white/20 text-white'
                                : 'bg-base-300 text-slate-600 dark:text-slate-300'
                            }`}
                          >
                            {opt.count}
                          </span>
                        )}
                      </div>
                      {isSelected && (
                        <Check size={14} className="stroke-[2.5]" />
                      )}
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* Desktop Filter Tabs (>= sm) */}
          <div className="hidden sm:flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none notranslate select-none text-left">
            <button
              onClick={() => {
                if (view === 'default') {
                  const mainEl = document.querySelector('main.overflow-y-auto');
                  if (mainEl) mainEl.scrollTo({ top: 0, behavior: 'smooth' });
                  fetchRecommended();
                } else {
                  setView('default');
                }
              }}
              className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all shrink-0 cursor-pointer outline-none focus:outline-none flex items-center gap-1.5 ${
                view === 'default'
                  ? 'bg-[#1D4ED8] text-white shadow-xs'
                  : 'bg-base-200 hover:bg-base-300/80 text-slate-600 dark:text-slate-300 border border-black/5 dark:border-white/5'
              }`}
            >
              <Sparkles size={14} className="shrink-0" />
              <span>Recommended</span>
            </button>
            <button
              onClick={() => {
                if (view === 'owned') {
                  const mainEl = document.querySelector('main.overflow-y-auto');
                  if (mainEl) mainEl.scrollTo({ top: 0, behavior: 'smooth' });
                  fetchMyCommunities();
                } else {
                  setView((v) => (v === 'owned' ? 'default' : 'owned'));
                }
              }}
              className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all shrink-0 cursor-pointer outline-none focus:outline-none flex items-center gap-1.5 ${
                view === 'owned'
                  ? 'bg-[#1D4ED8] text-white shadow-xs'
                  : 'bg-base-200 hover:bg-base-300/80 text-slate-600 dark:text-slate-300 border border-black/5 dark:border-white/5'
              }`}
            >
              <Settings size={14} className="shrink-0" />
              <span>Managed</span>
              {ownedList.length > 0 && (
                <span
                  className={`text-[10px] font-black px-1.5 py-0.2 rounded-full ${
                    view === 'owned'
                      ? 'bg-white/20 text-white'
                      : 'bg-base-300 text-slate-600 dark:text-slate-300'
                  }`}
                >
                  {ownedList.length}
                </span>
              )}
            </button>
            <button
              onClick={() => {
                if (view === 'joined') {
                  const mainEl = document.querySelector('main.overflow-y-auto');
                  if (mainEl) mainEl.scrollTo({ top: 0, behavior: 'smooth' });
                  fetchMyCommunities();
                } else {
                  setView((v) => (v === 'joined' ? 'default' : 'joined'));
                }
              }}
              className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all shrink-0 cursor-pointer outline-none focus:outline-none flex items-center gap-1.5 ${
                view === 'joined'
                  ? 'bg-[#1D4ED8] text-white shadow-xs'
                  : 'bg-base-200 hover:bg-base-300/80 text-slate-600 dark:text-slate-300 border border-black/5 dark:border-white/5'
              }`}
            >
              <Users size={14} className="shrink-0" />
              <span>Joined</span>
              {joinedOnly.length > 0 && (
                <span
                  className={`text-[10px] font-black px-1.5 py-0.2 rounded-full ${
                    view === 'joined'
                      ? 'bg-white/20 text-white'
                      : 'bg-base-300 text-slate-600 dark:text-slate-300'
                  }`}
                >
                  {joinedOnly.length}
                </span>
              )}
            </button>
          </div>
        </>
      )}

      {isSearching && (
        <>
          <div className="flex items-center justify-between text-left">
            <p className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
              Results for "{committed}"
              {!searchLoading &&
                searchResults.length > 0 &&
                ` · ${searchResults.length}${searchHasMore ? '+' : ''}`}
            </p>
            <button
              className="text-xs font-bold text-slate-500 hover:text-slate-700 dark:hover:text-slate-200 cursor-pointer"
              onClick={() => {
                setQuery('');
                setCommitted('');
                setSearchResults([]);
              }}
            >
              Clear
            </button>
          </div>
          {searchLoading && (
            <div className="relative min-h-[320px] rounded-3xl bg-base-200/50">
              <LoadingAnimation overlay label="Loading communities" />
            </div>
          )}
          {!searchLoading && searchResults.length === 0 && committed && (
            <div className="flex flex-col items-center justify-center py-16 opacity-60 space-y-3 text-center">
              <p className="text-sm font-bold">
                No communities found for "{committed}"
              </p>
              <button
                className="bg-[#1D4ED8] hover:bg-blue-800 text-white font-bold text-xs px-4 py-2 rounded-xl shadow-xs cursor-pointer"
                onClick={() => setShowCreate(true)}
              >
                + Create "{committed}"
              </button>
            </div>
          )}
          {!searchLoading && searchResults.length > 0 && (
            <div className="grid gap-4 grid-cols-1 sm:grid-cols-2">
              {searchResults.map((c: any) => {
                const local = myCommunities.find((x) => x.id === c.id);
                const isOwner = Boolean(
                  local?.isOwner ||
                    local?.role === 'OWNER' ||
                    c.role === 'OWNER' ||
                    (c.isOwner === true && !local),
                );
                const isMember = Boolean(
                  local?.isMember || c.isMember || isOwner,
                );
                const localPending = getPendingLocal().includes(String(c.id));
                const hasPending =
                  !isMember &&
                  !isOwner &&
                  Boolean(
                    local?.hasPendingRequest ||
                      c.hasPendingRequest ||
                      localPending,
                  );
                const userRole = isOwner
                  ? 'OWNER'
                  : isMember
                    ? local?.role ||
                      local?.currentUserRole ||
                      c.role ||
                      c.currentUserRole ||
                      'MEMBER'
                    : null;
                return (
                  <CommunityCard
                    key={c.id}
                    id={c.id}
                    slug={c.slug}
                    name={c.name}
                    description={c.description}
                    members={c.memberCount}
                    avatarUrl={c.avatarUrl}
                    privacy={c.privacy}
                    rankLabel={getCommunityRankLabel(c)}
                    momentumScore={getCommunityMomentum(c)}
                    isMember={isMember}
                    isOwner={isOwner}
                    role={userRole}
                    currentUserRole={userRole}
                    hasPendingRequest={hasPending}
                    isDeleted={c.isDeleted ?? local?.isDeleted}
                    deletedAt={c.deletedAt ?? local?.deletedAt}
                    scheduledDeletionDate={
                      c.scheduledDeletionDate ?? local?.scheduledDeletionDate
                    }
                    deletionDueDate={
                      c.deletionDueDate ?? local?.deletionDueDate
                    }
                    onManage={
                      isOwner ||
                      userRole === 'ADMIN' ||
                      userRole === 'MODERATOR'
                        ? () => {
                            openCommunity(
                              local
                                ? {
                                    ...c,
                                    isMember: local.isMember,
                                    isOwner: local.isOwner,
                                    role: userRole,
                                    currentUserRole: userRole,
                                    hasPendingRequest: hasPending,
                                    isDeleted: local.isDeleted,
                                    deletedAt: local.deletedAt,
                                    scheduledDeletionDate:
                                      local.scheduledDeletionDate,
                                    deletionDueDate: local.deletionDueDate,
                                  }
                                : {
                                    ...c,
                                    isMember,
                                    isOwner,
                                    role: userRole,
                                    currentUserRole: userRole,
                                    hasPendingRequest: hasPending,
                                  },
                              'manage',
                            );
                          }
                        : undefined
                    }
                    onClick={() => {
                      openCommunity(
                        local
                          ? {
                              ...c,
                              isMember: local.isMember,
                              isOwner: local.isOwner,
                              role: userRole,
                              currentUserRole: userRole,
                              hasPendingRequest: hasPending,
                              isDeleted: local.isDeleted,
                              deletedAt: local.deletedAt,
                              scheduledDeletionDate:
                                local.scheduledDeletionDate,
                              deletionDueDate: local.deletionDueDate,
                            }
                          : {
                              ...c,
                              isMember,
                              isOwner,
                              role: userRole,
                              currentUserRole: userRole,
                              hasPendingRequest: hasPending,
                            },
                      );
                    }}
                  />
                );
              })}
            </div>
          )}
          {searchHasMore && !searchLoading && (
            <button
              className="w-full py-2 text-xs font-bold text-[#1D4ED8] hover:underline inline-flex items-center justify-center gap-1 cursor-pointer"
              disabled={searchLoadingMore}
              onClick={() => doSearch(committed, searchCursor, false)}
            >
              {searchLoadingMore ? (
                <Spin xs />
              ) : (
                <>
                  Load more <ArrowDown size={14} />
                </>
              )}
            </button>
          )}
        </>
      )}

      {!isSearching && (
        <PullToRefresh
          onRefresh={async () => {
            await Promise.all([fetchRecommended(), fetchMyCommunities()]);
          }}
        >
          {isCommunityPageLoading && (
            <div className="relative min-h-[420px] rounded-3xl bg-base-200/50">
              <LoadingAnimation overlay label="Loading communities" />
            </div>
          )}
          {!isCommunityPageLoading && (
            <div className="space-y-6">
              {view === 'default' && (
                <RecommendedCarousel
                  recommended={recommended}
                  loading={false}
                  onSelect={openCommunity}
                  onJoin={handleCarouselJoin}
                  joiningId={joiningId}
                />
              )}

              {view === 'default' &&
                recommended.length === 0 &&
                myCommunities.length === 0 && (
                  <div className="text-center py-16 opacity-70 space-y-3">
                    <p className="font-bold text-base text-slate-900 dark:text-white">
                      No communities found
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400 max-w-xs mx-auto">
                      Create a community to get started and invite others!
                    </p>
                    <button
                      className="bg-[#1D4ED8] hover:bg-blue-800 text-white font-bold text-xs px-4 py-2 rounded-xl shadow-xs cursor-pointer"
                      onClick={() => setShowCreate(true)}
                    >
                      + Create a community
                    </button>
                  </div>
                )}
              {((view === 'default' && ownedList.length > 0) ||
                view === 'owned') &&
                (ownedList.length > 0 ? (
                  <div className="space-y-3.5 text-left">
                    <div className="flex items-center gap-2">
                      <Settings
                        size={15}
                        className="text-[#1D4ED8] dark:text-blue-400 stroke-[2.5]"
                      />
                      <h2 className="font-black text-sm sm:text-base tracking-tight text-slate-900 dark:text-white">
                        Managed by you
                      </h2>
                      <span className="bg-[#1D4ED8]/10 text-[#1D4ED8] dark:text-blue-400 border border-[#1D4ED8]/25 text-[11px] font-black rounded-full px-2 py-0.5">
                        {ownedList.length}
                      </span>
                    </div>
                    <div className="grid gap-4 grid-cols-1 sm:grid-cols-2">
                      {ownedList.map((c) => {
                        const userRole =
                          c.role ||
                          c.currentUserRole ||
                          c.memberRole ||
                          (c.isOwner
                            ? 'OWNER'
                            : c.isAdmin
                              ? 'ADMIN'
                              : c.isModerator
                                ? 'MODERATOR'
                                : 'MEMBER');
                        return (
                          <CommunityCard
                            key={c.id}
                            id={c.id}
                            slug={c.slug}
                            name={c.name}
                            description={c.description}
                            members={c.memberCount}
                            avatarUrl={c.avatarUrl}
                            privacy={c.privacy}
                            rankLabel={getCommunityRankLabel(c)}
                            momentumScore={getCommunityMomentum(c)}
                            isMember={c.isMember ?? true}
                            isOwner={c.isOwner}
                            role={userRole}
                            currentUserRole={userRole}
                            isDeleted={c.isDeleted}
                            deletedAt={c.deletedAt}
                            scheduledDeletionDate={c.scheduledDeletionDate}
                            deletionDueDate={c.deletionDueDate}
                            onManage={() => {
                              openCommunity(
                                {
                                  ...c,
                                  role: userRole,
                                  currentUserRole: userRole,
                                  isModerator:
                                    userRole === 'MODERATOR' || c.isModerator,
                                  isAdmin:
                                    userRole === 'ADMIN' ||
                                    userRole === 'OWNER' ||
                                    c.isOwner ||
                                    c.isAdmin,
                                },
                                'manage',
                              );
                            }}
                            onClick={() => {
                              openCommunity(c);
                            }}
                          />
                        );
                      })}
                    </div>
                  </div>
                ) : (
                  <div className="text-center py-16 opacity-70 space-y-3">
                    <div className="flex justify-center text-slate-400">
                      <Settings size={40} />
                    </div>
                    <p className="font-bold text-slate-900 dark:text-white">
                      You aren't managing any communities yet
                    </p>
                    <button
                      className="bg-[#1D4ED8] hover:bg-blue-800 text-white font-bold text-xs px-4 py-2 rounded-xl shadow-xs cursor-pointer"
                      onClick={() => setShowCreate(true)}
                    >
                      + Create your first community
                    </button>
                  </div>
                ))}

              {((view === 'default' && joinedOnly.length > 0) ||
                view === 'joined') &&
                (joinedOnly.length > 0 ? (
                  <div className="space-y-3.5 text-left">
                    <div className="flex items-center gap-2">
                      <Users
                        size={15}
                        className="text-[#1D4ED8] dark:text-blue-400 stroke-[2.5]"
                      />
                      <h2 className="font-black text-sm sm:text-base tracking-tight text-slate-900 dark:text-white">
                        Joined Communities
                      </h2>
                      <span className="bg-[#1D4ED8]/10 text-[#1D4ED8] dark:text-blue-400 border border-[#1D4ED8]/25 text-[11px] font-black rounded-full px-2 py-0.5">
                        {joinedOnly.length}
                      </span>
                    </div>
                    <div className="grid gap-4 grid-cols-1 sm:grid-cols-2">
                      {joinedOnly
                        .filter((c) => !isCommunityDeleted(c))
                        .map((c) => {
                          const userRole =
                            c.role ||
                            c.currentUserRole ||
                            c.memberRole ||
                            (c.isAdmin
                              ? 'ADMIN'
                              : c.isModerator
                                ? 'MODERATOR'
                                : c.isOwner
                                  ? 'OWNER'
                                  : 'MEMBER');
                          return (
                            <CommunityCard
                              key={c.id}
                              id={c.id}
                              slug={c.slug}
                              name={c.name}
                              description={c.description}
                              members={c.memberCount}
                              avatarUrl={c.avatarUrl}
                              privacy={c.privacy}
                              rankLabel={getCommunityRankLabel(c)}
                              momentumScore={getCommunityMomentum(c)}
                              isMember={c.isMember ?? true}
                              isOwner={c.isOwner || userRole === 'OWNER'}
                              role={userRole}
                              currentUserRole={userRole}
                              isDeleted={c.isDeleted}
                              deletedAt={c.deletedAt}
                              scheduledDeletionDate={c.scheduledDeletionDate}
                              deletionDueDate={c.deletionDueDate}
                              onManage={() => {
                                openCommunity(
                                  {
                                    ...c,
                                    role: userRole,
                                    currentUserRole: userRole,
                                    isModerator: userRole === 'MODERATOR',
                                    isAdmin:
                                      userRole === 'ADMIN' ||
                                      userRole === 'OWNER',
                                  },
                                  'manage',
                                );
                              }}
                              onClick={() => {
                                openCommunity(c);
                              }}
                            />
                          );
                        })}
                    </div>
                  </div>
                ) : (
                  view === 'joined' && (
                    <div className="text-center py-16 opacity-70 space-y-3">
                      <p className="font-bold text-slate-900 dark:text-white">
                        You haven't joined any communities yet
                      </p>
                      <p className="text-xs text-slate-500 dark:text-slate-400 max-w-xs mx-auto">
                        Search above to discover communities.
                      </p>
                    </div>
                  )
                ))}
            </div>
          )}
        </PullToRefresh>
      )}

      {selected && !adminTarget && (
        <DetailPanel
          key={selected.id}
          community={{
            ...selected,
            isMember:
              selected.isMember ||
              myCommunities.some((x) => x.id === selected.id && x.isMember),
            isOwner:
              selected.isOwner ||
              myCommunities.some((x) => x.id === selected.id && x.isOwner),
            role:
              selected.role ||
              myCommunities.find((x) => x.id === selected.id)?.role,
            currentUserRole:
              selected.currentUserRole ||
              myCommunities.find((x) => x.id === selected.id)?.currentUserRole,
            hasPendingRequest:
              selected.hasPendingRequest &&
              !myCommunities.some((x) => x.id === selected.id && x.isMember),
          }}
          onClose={closeCommunity}
          onMembershipChange={syncMembership}
        />
      )}

      {adminTarget && (
        <AdminPanel
          key={adminTarget.id}
          community={adminTarget}
          initialTab="requests"
          onClose={() => {
            setAdminTarget(null);
            if (slugParam)
              navigate('/communities' + location.search, { replace: true });
          }}
          onCommunityUpdated={handleCommunityUpdated}
          onMembershipChange={syncMembership}
        />
      )}

      {showCreate && (
        <CreateModal
          onClose={() => setShowCreate(false)}
          onDone={handleCreated}
        />
      )}
    </div>
  );
};

export default Community;
