import {
  useState,
  useEffect,
  useRef,
  useCallback,
  useLayoutEffect,
} from 'react';
import {
  Send,
  Trash2,
  Pencil,
  ChevronDown,
  Loader2,
  ThumbsUp,
  ThumbsDown,
  MoreHorizontal,
  Sparkles,
  Flag,
  Clock,
  ArrowUpDown,
  Pin,
} from 'lucide-react';
import { motion, AnimatePresence, type Variants } from 'framer-motion';
import axiosInstance from '../../api/axiosConfig';
import { getAuthToken } from '../../utils/auth';
import ConfirmModal from './ConfirmModal';
import ReportModal from '../modals/ReportModal';
import { useCreateComment } from '../../hooks/usePostInteractions';
import { useCurrentUser } from '../../hooks/useUser';
import { checkProfanity } from '../../utils/profanity';
import { showToast } from '../../utils/toast';
import { parseError } from '../../utils/error-handler';
import { decodeHTML, resolveMediaUrl } from '../../utils/postUtils';

const generateUUID = (): string => {
  if (
    typeof crypto !== 'undefined' &&
    typeof crypto.randomUUID === 'function'
  ) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

async function apiFetch(url: string) {
  const res = await axiosInstance.get(url);
  return res.data?.data ?? res.data;
}

async function apiPut(url: string, body: unknown) {
  const res = await axiosInstance.put(url, body);
  return res.data?.data ?? res.data;
}

async function apiDelete(url: string) {
  const res = await axiosInstance.delete(url);
  return res.data?.data ?? res.data;
}

// ─── types ────────────────────────────────────────────────────────────────────
export type PostType = 'posts' | 'social-posts';

export type AuthorDto = {
  username?: string;
  actualUsername?: string;
  displayName?: string;
  profileImage?: string;
  profileImageUrl?: string;
  userProfileImage?: string;
  avatarUrl?: string;
  pincode?: string;
};

export type CommentDto = {
  id: number;
  text: string;
  createdAt: string;
  updatedAt?: string;
  lastActivityAt?: string;
  author?: AuthorDto;
  authorUsername?: string;
  authorDisplayName?: string;
  actorToken?: string;
  parentCommentId?: number | null;
  replyCount?: number;
  replies?: CommentDto[];
  isPendingSync?: boolean;
  likeCount?: number;
  dislikeCount?: number;
  userVote?: 'LIKE' | 'DISLIKE' | null;
  interactionType?: 'LIKE' | 'DISLIKE' | null;
  likes?: string[];
  dislikes?: string[];
  userProfileImage?: string;
  authorProfileImage?: string;
  profileImage?: string;
  isPinned?: boolean;
  isDeleted?: boolean;
  deletedByType?: 'USER' | 'ADMINISTRATOR' | string;
  rankingScore?: number;
};

type CommentSectionProps = {
  postId: number;
  postType: PostType;
  commentCount?: number;
  currentUsername?: string;
  currentRole?: 'ROLE_USER' | 'ROLE_DEPARTMENT' | 'ROLE_ADMIN';
  defaultOpen?: boolean;
  onCommentCountChange?: (count: number) => void;
  readOnly?: boolean;
  isCommunityOwner?: boolean;
  isPostAuthor?: boolean;
};

// ─── helpers ──────────────────────────────────────────────────────────────────
function getParentCommentId(c: CommentDto): number | null {
  const pId =
    c.parentCommentId ??
    (c as any).parentComment?.id ??
    (typeof (c as any).parentComment === 'number'
      ? (c as any).parentComment
      : null);
  return pId ? Number(pId) : null;
}

function isRootComment(c: CommentDto): boolean {
  return !getParentCommentId(c);
}

function flattenAllReplies(list: CommentDto[]): CommentDto[] {
  const result: CommentDto[] = [];
  const seen = new Set<number>();
  function traverse(items: CommentDto[]) {
    for (const item of items) {
      if (!seen.has(item.id)) {
        seen.add(item.id);
        const { replies: nestedReplies, ...rest } = item;
        result.push({
          ...rest,
          replies: [],
        });
        if (nestedReplies && nestedReplies.length > 0) {
          traverse(nestedReplies);
        }
      }
    }
  }
  traverse(list);
  return result;
}

function organizeCommentsIntoTree(rawList: CommentDto[]): CommentDto[] {
  const map = new Map<number, CommentDto>();
  const roots: CommentDto[] = [];

  rawList.forEach((item) => {
    map.set(item.id, {
      ...item,
      replies: item.replies ? [...item.replies] : [],
    });
  });

  function findRootAncestorId(id: number): number {
    let curr = map.get(id);
    const visited = new Set<number>([id]);
    while (curr) {
      const parentId = getParentCommentId(curr);
      if (!parentId || !map.has(parentId) || visited.has(parentId)) {
        return curr.id;
      }
      visited.add(parentId);
      curr = map.get(parentId);
    }
    return id;
  }

  rawList.forEach((item) => {
    const parentId = getParentCommentId(item);
    const mapped = map.get(item.id)!;
    if (parentId) {
      const rootAncestorId = findRootAncestorId(item.id);
      if (rootAncestorId !== item.id && map.has(rootAncestorId)) {
        const rootComment = map.get(rootAncestorId)!;
        if (!rootComment.replies) rootComment.replies = [];
        if (!rootComment.replies.some((r) => r.id === item.id)) {
          rootComment.replies.push(mapped);
        }
      } else {
        roots.push(mapped);
      }
    } else {
      roots.push(mapped);
    }
  });

  roots.forEach((root) => {
    if (root.replies && root.replies.length > 0) {
      root.replies = flattenAllReplies(root.replies);
      root.replyCount = Math.max(
        root.replyCount ?? 0,
        (root as any).repliesCount ?? 0,
        (root as any).reply_count ?? 0,
        root.replies.length,
      );
    }
  });

  return roots;
}

function timeAgo(raw: string | undefined): string {
  if (!raw) return '';
  const d = new Date(raw);
  if (isNaN(d.getTime())) return '';
  const diff = Date.now() - d.getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 7) return `${days}d ago`;
  return `${Math.floor(days / 7)}w ago`;
}

function getCommentUsername(c: CommentDto): string {
  return (
    c.author?.username ??
    c.author?.actualUsername ??
    c.authorUsername ??
    'anonymous'
  );
}

function getDisplayName(c: CommentDto): string {
  return (
    c.author?.displayName ??
    c.authorDisplayName ??
    c.author?.actualUsername ??
    c.author?.username ??
    c.authorUsername ??
    'Citizen'
  );
}

function getAvatarSrc(username?: string, rawImage?: string | null): string {
  if (
    rawImage &&
    rawImage !== 'null' &&
    rawImage !== 'undefined' &&
    rawImage !== 'false'
  ) {
    const resolved = resolveMediaUrl(rawImage, 'social-posts');
    if (resolved) return resolved;
  }
  return `https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(
    username || 'anonymous',
  )}`;
}

function formatCommentText(text: string) {
  if (!text) return '';
  const parts = text.split(/(@[a-zA-Z0-9_]+)/g);
  return parts.map((part, index) => {
    if (part.startsWith('@') && part.length > 1) {
      return (
        <span
          key={index}
          className="text-[#1D4ED8] dark:text-blue-400 font-bold hover:underline cursor-pointer inline mr-1 notranslate break-all"
        >
          {part}
        </span>
      );
    }
    return part;
  });
}

const LIMIT = 10;

export interface CachedComments {
  comments: CommentDto[];
  totalElements: number;
  cursor?: number;
  hasMore: boolean;
  timestamp: number;
}

export const commentsCache = new Map<string, CachedComments>();

export function getCommentsCacheKey(
  postId: number,
  postType: PostType,
  sortBy: 'NEW' | 'TOP' = 'NEW',
): string {
  return `${postId}-${postType}-${sortBy}`;
}

export async function prefetchComments(
  postId: number,
  postType: PostType,
  sortBy: 'NEW' | 'TOP' = 'NEW',
): Promise<CachedComments | undefined> {
  const cacheKey = getCommentsCacheKey(postId, postType, sortBy);
  const cached = commentsCache.get(cacheKey);
  if (cached && Date.now() - cached.timestamp < 30000) return cached;

  try {
    const params = new URLSearchParams({ limit: String(LIMIT), sort: sortBy });
    const url = `/api/comments/${postType === 'posts' ? 'post' : 'social-posts'}/${postId}/top-level?${params}`;
    const res = await apiFetch(url);
    const container = res?.data ?? res;
    const rows: CommentDto[] = Array.isArray(container)
      ? container
      : (container?.data ?? container?.content ?? []);
    const organized = organizeCommentsIntoTree(rows);
    const newCursor =
      res?.nextCursor ??
      (rows.length === LIMIT ? rows[rows.length - 1]?.id : undefined);
    const more = res?.hasMore ?? rows.length === LIMIT;
    const totalElements =
      typeof res?.totalElements === 'number' ? res.totalElements : rows.length;

    const data: CachedComments = {
      comments: organized,
      totalElements,
      cursor: newCursor,
      hasMore: more,
      timestamp: Date.now(),
    };
    commentsCache.set(cacheKey, data);
    return data;
  } catch {
    // silent catch on prefetch
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Variants for animations
// ═══════════════════════════════════════════════════════════════════════════════
const containerVariants: Variants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.05,
    },
  },
};

const itemVariants: Variants = {
  hidden: { opacity: 0, y: 8 },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      duration: 0.2,
      ease: 'easeOut',
    },
  },
  exit: { opacity: 0, transition: { duration: 0.15 } },
};

// ═══════════════════════════════════════════════════════════════════════════════
// Auto-grow textarea
// ═══════════════════════════════════════════════════════════════════════════════
function AutoTextarea({
  value,
  onChange,
  onKeyDown,
  placeholder,
  disabled,
  autoFocus,
  onFocus,
}: {
  value: string;
  onChange: (v: string) => void;
  onKeyDown?: React.KeyboardEventHandler<HTMLTextAreaElement>;
  placeholder: string;
  disabled?: boolean;
  autoFocus?: boolean;
  onFocus?: () => void;
}) {
  const ref = useRef<HTMLTextAreaElement>(null);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }, [value]);

  useEffect(() => {
    if (autoFocus && ref.current) {
      const el = ref.current;
      el.focus();
      const len = el.value.length;
      el.setSelectionRange(len, len);
    }
  }, [autoFocus]);

  return (
    <textarea
      ref={ref}
      rows={1}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onKeyDown={onKeyDown}
      placeholder={placeholder}
      disabled={disabled}
      onFocus={onFocus}
      className="w-full resize-none overflow-hidden bg-transparent text-sm leading-relaxed outline-none placeholder:text-base-content/40 disabled:opacity-50"
      style={{ minHeight: '24px', maxHeight: '200px' }}
    />
  );
}

// ═══════════════════════════════════════════════════════════════════════════════
// CommentInput — modern composer
// ═══════════════════════════════════════════════════════════════════════════════
function CommentInput({
  placeholder,
  initialValue = '',
  onSubmit,
  onCancel,
  submitLabel = 'Comment',
  autoFocus = false,
  avatarSeed,
  avatarImage,
  hideAvatar = false,
}: {
  placeholder: string;
  initialValue?: string;
  onSubmit: (text: string) => Promise<void>;
  onCancel?: () => void;
  submitLabel?: string;
  autoFocus?: boolean;
  avatarSeed?: string;
  avatarImage?: string | null;
  hideAvatar?: boolean;
}) {
  const { data: currentUser } = useCurrentUser();
  const [text, setText] = useState(initialValue);
  const [busy, setBusy] = useState(false);
  const [focused, setFocused] = useState(autoFocus);

  const resolvedUsername =
    avatarSeed || currentUser?.username || currentUser?.actualUsername || 'You';
  const resolvedImage =
    avatarImage !== undefined ? avatarImage : currentUser?.profileImage;
  const avatarUrl = getAvatarSrc(resolvedUsername, resolvedImage);

  const handleTextChange = (val: string) => {
    const isReplyMention =
      initialValue &&
      initialValue.startsWith('@') &&
      initialValue.endsWith(' ');
    if (isReplyMention && !val.startsWith(initialValue)) {
      setText(initialValue);
      return;
    }
    setText(val);
  };

  useEffect(() => {
    setText(initialValue);
  }, [initialValue]);

  async function submit() {
    const trimmed = text.trim();
    if (!trimmed) return;

    if (checkProfanity(trimmed)) {
      showToast.error(
        'Content contains prohibited language/profanity. Please check your words.',
      );
      return;
    }

    setBusy(true);
    try {
      await onSubmit(trimmed);
      setText('');
      setFocused(false);
    } catch (e: unknown) {
      showToast.error(parseError(e));
    } finally {
      setBusy(false);
    }
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
      e.preventDefault();
      submit();
    }
    if (e.key === 'Escape') onCancel?.();
  }

  return (
    <div className="flex items-start gap-2.5 sm:gap-3.5">
      {/* Current user avatar */}
      {!hideAvatar && (
        <img
          src={avatarUrl}
          alt={resolvedUsername}
          onError={(e) => {
            (e.target as HTMLImageElement).src =
              `https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(
                resolvedUsername || 'anonymous',
              )}`;
          }}
          className="w-7 h-7 sm:w-8.5 sm:h-8.5 rounded-full shrink-0 object-cover border border-base-content/10 dark:border-white/10 shadow-xs mt-0.5"
        />
      )}

      <div className="flex-1 min-w-0">
        <div
          className={`relative rounded-2xl border transition-all duration-200 ${
            focused
              ? 'border-[#1D4ED8] dark:border-blue-500 bg-base-100 dark:bg-base-300/40 shadow-sm ring-2 ring-[#1D4ED8]/15'
              : 'border-base-300/80 dark:border-white/10 bg-base-200/40 dark:bg-white/[0.03] hover:border-base-300 dark:hover:border-white/20'
          }`}
        >
          <div className="px-3 sm:px-4 pt-2.5 sm:pt-3 pb-2 sm:pb-2.5">
            <AutoTextarea
              value={text}
              onChange={handleTextChange}
              onKeyDown={onKeyDown}
              placeholder={placeholder}
              disabled={busy}
              autoFocus={autoFocus}
              onFocus={() => setFocused(true)}
            />
          </div>

          <AnimatePresence>
            {(focused || text.length > 0) && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="overflow-hidden"
              >
                <div className="flex items-center justify-between gap-2 border-t border-base-300/40 dark:border-white/10 px-3 sm:px-4 py-2 sm:py-2.5 bg-base-200/20 dark:bg-white/[0.01]">
                  <div className="hidden lg:flex flex-col shrink-0 min-w-0">
                    <p className="text-[10px] font-medium text-base-content/40 flex items-center gap-1.5 whitespace-nowrap">
                      <Sparkles size={11} className="text-[#1D4ED8]" />
                      Press{' '}
                      <kbd className="kbd kbd-xs font-mono font-black border rounded-md px-1.5 py-0.5 shadow-xs bg-black text-white border-black/20 dark:bg-white dark:text-black dark:border-white">
                        Ctrl + Enter
                      </kbd>{' '}
                      to send
                    </p>
                  </div>
                  <div className="flex items-center gap-1.5 sm:gap-2 ml-auto shrink-0">
                    {onCancel && (
                      <button
                        onClick={onCancel}
                        disabled={busy}
                        className="btn btn-ghost btn-xs rounded-xl h-7 sm:h-8 px-2.5 sm:px-3.5 text-xs font-semibold text-base-content/60 hover:bg-base-200 cursor-pointer shrink-0"
                      >
                        Cancel
                      </button>
                    )}
                    <button
                      onClick={submit}
                      disabled={busy || !text.trim()}
                      className="flex items-center gap-1.5 rounded-xl bg-[#1D4ED8] h-7 sm:h-8 px-3 sm:px-4 text-xs font-bold text-white shadow-md shadow-blue-700/25 transition-all hover:bg-blue-800 hover:scale-[1.02] active:scale-[0.98] disabled:opacity-30 disabled:grayscale disabled:scale-100 cursor-pointer shrink-0 whitespace-nowrap"
                    >
                      {busy ? (
                        <Loader2 size={12} className="animate-spin" />
                      ) : (
                        <Send size={12} />
                      )}
                      {submitLabel}
                    </button>
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════════
// Helper functions for comment counting
// ═══════════════════════════════════════════════════════════════════════════════
function countCommentsInSubtree(comment: CommentDto): number {
  const loadedReplies = comment.replies ?? [];
  let subCount = 0;
  let loadedDirectSum = 0;
  if (loadedReplies.length > 0) {
    for (const reply of loadedReplies) {
      subCount += countCommentsInSubtree(reply);
      loadedDirectSum += 1 + (reply.replyCount ?? 0);
    }
  }
  const unloadedDescendants = Math.max(
    0,
    (comment.replyCount ?? 0) - loadedDirectSum,
  );
  return 1 + subCount + unloadedDescendants;
}

function getCommentReplyCount(c: CommentDto): number {
  return (
    c.replyCount ??
    (c as any).repliesCount ??
    (c as any).reply_count ??
    (c as any).replies_count ??
    (c as any).childrenCount ??
    (c as any).childCommentCount ??
    (c as any).totalReplies ??
    (c.replies ? c.replies.length : 0)
  );
}

// ═══════════════════════════════════════════════════════════════════════════════
// SingleComment
// ═══════════════════════════════════════════════════════════════════════════════
type SingleCommentProps = {
  postId: number;
  postType: PostType;
  comment: CommentDto;
  currentUsername?: string;
  currentRole?: 'ROLE_USER' | 'ROLE_DEPARTMENT' | 'ROLE_ADMIN';
  depth?: number;
  isLastReply?: boolean;
  onDeleted: (id: number, countToRemove: number, parentId?: number) => void;
  onUpdated: (updated: CommentDto) => void;
  onReplyAdded: (parentId?: number) => void;
  onAddReplyToThread?: (
    text: string,
    targetComment: CommentDto,
  ) => Promise<void>;
  readOnly?: boolean;
  isCommunityOwner?: boolean;
  isPostAuthor?: boolean;
};

function CommentItem({
  postId,
  postType,
  comment,
  currentUsername,
  currentRole,
  depth = 0,
  isLastReply = false,
  onDeleted,
  onUpdated,
  onReplyAdded,
  onAddReplyToThread,
  readOnly = false,
  isCommunityOwner = false,
  isPostAuthor = false,
}: SingleCommentProps) {
  const { data: currentUser } = useCurrentUser();
  const createCommentMutation = useCreateComment();
  const [showReplyBox, setShowReplyBox] = useState(false);
  const [editing, setEditing] = useState(false);
  const decodedText = decodeHTML(comment.text);
  const [repliesOpen, setRepliesOpen] = useState(false);
  const [replies, setReplies] = useState<CommentDto[]>(() => {
    if (depth === 0 && comment.replies && comment.replies.length > 0) {
      return flattenAllReplies(comment.replies);
    }
    return [];
  });
  const [loadingReplies, setLoadingReplies] = useState(false);
  const [repliesCursor, setRepliesCursor] = useState<number | undefined>();
  const initialReplyCount = getCommentReplyCount(comment);
  const [hasMoreReplies, setHasMoreReplies] = useState(
    depth === 0 &&
      initialReplyCount > 0 &&
      (comment.replies ?? []).length === 0,
  );
  const [deleting, setDeleting] = useState(false);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [moreMenuOpen, setMoreMenuOpen] = useState(false);
  const moreMenuRef = useRef<HTMLDivElement>(null);

  const [isPinned, setIsPinned] = useState(!!comment.isPinned);

  // ── Like / Dislike state initialized from comment data & localStorage ──
  const [voteState, setVoteState] = useState<{
    userVote: 'LIKE' | 'DISLIKE' | null;
    likes: number;
    dislikes: number;
  }>(() => {
    let initialVote: 'LIKE' | 'DISLIKE' | null =
      comment.interactionType || comment.userVote || null;
    try {
      const stored = localStorage.getItem(`comment_vote_${comment.id}`);
      if (stored === 'LIKE' || stored === 'DISLIKE') {
        initialVote = stored;
      }
    } catch {}

    const initialLikes =
      typeof comment.likeCount === 'number'
        ? comment.likeCount
        : (comment.likes?.length ?? 0);
    const initialDislikes =
      typeof comment.dislikeCount === 'number'
        ? comment.dislikeCount
        : (comment.dislikes?.length ?? 0);

    return {
      userVote: initialVote,
      likes: Math.max(0, initialLikes),
      dislikes: Math.max(0, initialDislikes),
    };
  });

  // Sync initial comment.replies if provided from parent tree without recursive re-render loop
  const lastCommentRepliesRef = useRef(comment.replies);
  useEffect(() => {
    if (
      depth === 0 &&
      comment.replies &&
      comment.replies !== lastCommentRepliesRef.current
    ) {
      lastCommentRepliesRef.current = comment.replies;
      if (comment.replies.length > 0) {
        setReplies(flattenAllReplies(comment.replies));
      }
    }
  }, [depth, comment.replies]);

  // Close 3-dot dropdown on outside click
  useEffect(() => {
    if (!moreMenuOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (
        moreMenuRef.current &&
        !moreMenuRef.current.contains(e.target as Node)
      ) {
        setMoreMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [moreMenuOpen]);

  // Handle Like / Dislike interaction
  const handleVote = async (type: 'LIKE' | 'DISLIKE') => {
    if (readOnly) return;
    if (!currentUsername && !getAuthToken()) {
      showToast.info('Please sign in to rate comments');
      return;
    }

    const isSameVote = voteState.userVote === type;
    const newVote: 'LIKE' | 'DISLIKE' | null = isSameVote ? null : type;

    let nextLikes = voteState.likes;
    let nextDislikes = voteState.dislikes;

    if (isSameVote) {
      if (type === 'LIKE') nextLikes = Math.max(0, nextLikes - 1);
      if (type === 'DISLIKE') nextDislikes = Math.max(0, nextDislikes - 1);
    } else {
      if (type === 'LIKE') {
        nextLikes += 1;
        if (voteState.userVote === 'DISLIKE')
          nextDislikes = Math.max(0, nextDislikes - 1);
      } else {
        nextDislikes += 1;
        if (voteState.userVote === 'LIKE')
          nextLikes = Math.max(0, nextLikes - 1);
      }
    }

    setVoteState({
      userVote: newVote,
      likes: nextLikes,
      dislikes: nextDislikes,
    });

    try {
      if (newVote) {
        localStorage.setItem(`comment_vote_${comment.id}`, newVote);
      } else {
        localStorage.removeItem(`comment_vote_${comment.id}`);
      }
    } catch {}

    // Send to backend API (atomic toggle endpoint)
    try {
      await axiosInstance.post(`/api/comments/${comment.id}/interactions`, {
        reaction: type,
      });
    } catch (e) {
      console.warn('Backend comment interaction sync failed:', e);
    }
  };

  const handleTogglePin = async () => {
    try {
      await axiosInstance.put(`/api/comments/${comment.id}/pin`);
      const nextPinned = !isPinned;
      setIsPinned(nextPinned);
      showToast.success(nextPinned ? 'Comment pinned' : 'Comment unpinned');
      onUpdated({ ...comment, isPinned: nextPinned });
    } catch (err: unknown) {
      showToast.error(parseError(err));
    }
  };

  const isFirstRender = useRef(true);
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    onUpdated({
      ...comment,
      likeCount: voteState.likes,
      dislikeCount: voteState.dislikes,
      userVote: voteState.userVote,
    });
  }, [voteState]);

  const cleanEmail = (u?: string) =>
    u && u.includes('@') ? u.split('@')[0] : u || '';
  const compareUsers = (a?: string, b?: string) => {
    if (!a || !b) return false;
    return (
      cleanEmail(a).trim().toLowerCase() === cleanEmail(b).trim().toLowerCase()
    );
  };

  const commentUsername = getCommentUsername(comment);
  const myUsername =
    currentUsername || currentUser?.username || currentUser?.actualUsername;
  const isOwner =
    (!!myUsername &&
      (compareUsers(comment.author?.username, myUsername) ||
        compareUsers(comment.author?.actualUsername, myUsername) ||
        compareUsers(comment.authorUsername, myUsername) ||
        compareUsers(commentUsername, myUsername))) ||
    (!!currentUser?.username &&
      (compareUsers(comment.author?.username, currentUser.username) ||
        compareUsers(comment.author?.actualUsername, currentUser.username) ||
        compareUsers(commentUsername, currentUser.username))) ||
    (!!currentUser?.actualUsername &&
      (compareUsers(comment.author?.username, currentUser.actualUsername) ||
        compareUsers(
          comment.author?.actualUsername,
          currentUser.actualUsername,
        ) ||
        compareUsers(commentUsername, currentUser.actualUsername)));
  const isAdmin = currentRole === 'ROLE_ADMIN';
  const replyCount =
    depth === 0 ? Math.max(getCommentReplyCount(comment), replies.length) : 0;
  const authorName = getDisplayName(comment);
  const rawImage =
    comment.author?.profileImage ||
    comment.author?.profileImageUrl ||
    (comment.author as any)?.userProfileImage ||
    (comment.author as any)?.avatarUrl ||
    comment.userProfileImage ||
    comment.authorProfileImage ||
    comment.profileImage ||
    (isOwner
      ? currentUser?.profileImage || (currentUser as any)?.profileImageUrl
      : null) ||
    null;

  const avatarUsername = commentUsername || authorName;
  const avatarSrc = getAvatarSrc(avatarUsername, rawImage);

  async function loadReplies(cursor?: number) {
    if (depth > 0) return;
    setLoadingReplies(true);
    try {
      const params = new URLSearchParams({ limit: String(LIMIT) });
      if (cursor) params.set('beforeId', String(cursor));
      const url = `/api/comments/${comment.id}/replies?${params}`;

      const res = await apiFetch(url);
      const container = res?.data ?? res;
      const rows: CommentDto[] = Array.isArray(container)
        ? container
        : (container?.data ?? container?.content ?? []);
      const newCursor =
        res?.nextCursor ??
        (rows.length === LIMIT ? rows[rows.length - 1]?.id : undefined);
      const more = res?.hasMore ?? rows.length === LIMIT;

      const flatRows = flattenAllReplies(rows);

      setReplies((prev) => {
        const combined = cursor ? [...prev, ...flatRows] : flatRows;
        const seen = new Set<number>();
        return combined.filter((r) => {
          if (seen.has(r.id)) return false;
          seen.add(r.id);
          return true;
        });
      });
      setRepliesCursor(newCursor);
      setHasMoreReplies(more);
      setRepliesOpen(true);
    } catch (e: unknown) {
      showToast.error(parseError(e));
    } finally {
      setLoadingReplies(false);
    }
  }

  function toggleReplies() {
    if (repliesOpen) {
      setRepliesOpen(false);
    } else {
      setRepliesOpen(true);
      if (replies.length === 0 && replyCount > 0) {
        loadReplies();
      }
    }
  }

  // Unified reply handler that adds reply flatly to this root comment thread
  const handleAddReplyToThread = async (
    text: string,
    targetComment: CommentDto,
  ) => {
    const idempotencyKey = generateUUID();
    const optimisticId = -Date.now();
    const userImg =
      currentUser?.profileImage ||
      (currentUser as any)?.profileImageUrl ||
      undefined;
    const authorUser = currentUsername || currentUser?.username || 'me';
    const optimisticReply: CommentDto = {
      id: optimisticId,
      text,
      createdAt: new Date().toISOString(),
      author: {
        username: authorUser,
        actualUsername: authorUser,
        profileImage: userImg,
        profileImageUrl: userImg,
        displayName: currentUser?.displayName || authorUser,
      },
      authorProfileImage: userImg,
      userProfileImage: userImg,
      profileImage: userImg,
      parentCommentId: targetComment.id,
      isPendingSync: true,
      likeCount: 0,
      dislikeCount: 0,
      userVote: null,
      replies: [],
    };

    setReplies((prev) => {
      const idx = prev.findIndex((r) => r.id === targetComment.id);
      if (idx !== -1) {
        const next = [...prev];
        next.splice(idx + 1, 0, optimisticReply);
        return next;
      }
      return [...prev, optimisticReply];
    });

    setRepliesOpen(true);
    onReplyAdded(comment.id);

    createCommentMutation.mutate(
      {
        postId,
        postType,
        payload: {
          text,
          parentCommentId: targetComment.id,
        },
        idempotencyKey,
      },
      {
        onSuccess: (res) => {
          const synced = res.data ?? res;
          setReplies((prev) =>
            prev.map((r) =>
              r.id === optimisticId
                ? { ...synced, isPendingSync: false, replies: [] }
                : r,
            ),
          );
        },
        onError: () => {
          if (!createCommentMutation.isPaused) {
            setReplies((prev) => prev.filter((r) => r.id !== optimisticId));
          }
        },
      },
    );
  };

  async function handleReply(text: string) {
    if (depth === 0) {
      await handleAddReplyToThread(text, comment);
      setShowReplyBox(false);
    } else if (onAddReplyToThread) {
      await onAddReplyToThread(text, comment);
      setShowReplyBox(false);
    }
  }

  const handleReplyDeleted = (replyId: number, countToRemove: number) => {
    setReplies((prev) => prev.filter((r) => r.id !== replyId));
    onDeleted(replyId, countToRemove, comment.id);
  };

  const handleReplyUpdated = (updated: CommentDto) => {
    setReplies((prev) =>
      prev.map((r) => (r.id === updated.id ? { ...updated, replies: [] } : r)),
    );
  };

  async function handleEdit(newText: string) {
    try {
      const updated = await apiPut(`/api/comments/${comment.id}`, {
        text: newText,
      });
      onUpdated({
        ...comment,
        text: updated?.text ?? newText,
        updatedAt: new Date().toISOString(),
      });
      setEditing(false);
      showToast.success('Comment updated');
    } catch (e: unknown) {
      showToast.error(parseError(e));
    }
  }

  async function handleDelete() {
    setDeleting(true);
    const countToRemove =
      depth === 0 ? 1 + Math.max(replies.length, replyCount) : 1;
    try {
      await apiDelete(`/api/comments/${comment.id}`);
      onDeleted(
        comment.id,
        countToRemove,
        comment.parentCommentId ?? undefined,
      );
      showToast.success('Comment deleted');
    } catch (e: unknown) {
      try {
        await apiDelete(
          `/api/comments/${postType === 'posts' ? 'post' : 'social-posts'}/${postId}/comments/${comment.id}`,
        );
        onDeleted(
          comment.id,
          countToRemove,
          comment.parentCommentId ?? undefined,
        );
        showToast.success('Comment deleted');
      } catch (err: unknown) {
        showToast.error(parseError(err));
      }
    } finally {
      setDeleting(false);
      setConfirmDeleteOpen(false);
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -4 }}
      transition={{ duration: 0.18, ease: 'easeOut' }}
      className={`relative ${depth > 0 ? 'mt-3' : 'mt-4 sm:mt-5'}`}
    >
      {/* For replies (depth > 0): YouTube curved connector and vertical stem */}
      {depth > 0 && (
        <>
          <div className="absolute left-0 top-0 w-4.5 sm:w-5 h-4 border-l-2 border-b-2 border-base-300 dark:border-white/20 rounded-bl-xl pointer-events-none" />
          {!isLastReply && (
            <div className="absolute left-0 top-4 -bottom-3 sm:-bottom-3.5 w-0 border-l-2 border-base-300 dark:border-white/20 pointer-events-none" />
          )}
        </>
      )}

      {/* Main Comment Row */}
      <div
        className={`flex items-start group gap-2.5 sm:gap-3 ${depth > 0 ? 'pl-6 sm:pl-7' : ''}`}
      >
        {/* Avatar */}
        <div className="relative shrink-0 mt-0.5">
          <img
            src={avatarSrc}
            alt={authorName}
            onError={(e) => {
              (e.target as HTMLImageElement).src =
                `https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(
                  avatarUsername || 'anonymous',
                )}`;
            }}
            className={`rounded-full object-cover border border-base-content/10 dark:border-white/10 bg-base-200 shrink-0 shadow-xs ${
              depth === 0 ? 'w-8.5 h-8.5' : 'w-7 h-7'
            }`}
          />
          {/* Vertical stem under root avatar when replies are open */}
          {depth === 0 && repliesOpen && replies.length > 0 && (
            <div className="absolute left-1/2 -translate-x-1/2 top-9.5 bottom-0 w-0 border-l-2 border-base-300 dark:border-white/20 pointer-events-none" />
          )}
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          {editing ? (
            <div className="mt-0.5">
              <CommentInput
                hideAvatar
                placeholder="Update your thought…"
                initialValue={decodedText}
                onSubmit={handleEdit}
                onCancel={() => setEditing(false)}
                submitLabel="Update"
                autoFocus
                avatarSeed={currentUsername}
              />
            </div>
          ) : (
            <>
              {/* Header: Name + Timestamp + 3-dots */}
              <div className="flex items-center justify-between gap-1.5 mb-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-xs font-bold text-base-content hover:underline cursor-pointer notranslate">
                    {authorName}
                  </span>

                  {comment.author?.username === 'admin' && (
                    <span className="bg-[#1D4ED8]/15 text-[#1D4ED8] dark:text-blue-400 text-[9px] font-black px-1.5 py-0.5 rounded uppercase tracking-wider">
                      STAFF
                    </span>
                  )}

                  <span className="text-[11px] font-medium text-base-content/40">
                    {timeAgo(comment.createdAt)}
                  </span>

                  {comment.updatedAt &&
                    comment.updatedAt !== comment.createdAt && (
                      <span className="text-[10px] italic text-base-content/35">
                        (edited)
                      </span>
                    )}

                  {comment.isPendingSync && (
                    <span className="inline-flex items-center gap-0.5 text-[8px] text-base-content/40 font-bold uppercase tracking-wider bg-base-300/40 px-1 py-0.5 rounded">
                      <Clock size={8} className="animate-pulse" />
                      Syncing
                    </span>
                  )}

                  {isPinned && (
                    <span className="inline-flex items-center gap-1 bg-[#1D4ED8]/10 text-[#1D4ED8] dark:text-blue-400 text-[10px] font-bold px-2 py-0.5 rounded-full">
                      <Pin size={10} className="rotate-45" /> Pinned
                    </span>
                  )}
                </div>

                {/* 3-Dot More Actions Menu (Far Right) */}
                <div ref={moreMenuRef} className="relative shrink-0">
                  <button
                    type="button"
                    onClick={() => setMoreMenuOpen((v) => !v)}
                    className="p-1 rounded-full hover:bg-base-200 dark:hover:bg-white/10 text-base-content/50 hover:text-base-content transition-all cursor-pointer"
                    title="More actions"
                  >
                    <MoreHorizontal size={15} />
                  </button>

                  <AnimatePresence>
                    {moreMenuOpen && (
                      <motion.div
                        initial={{ opacity: 0, scale: 0.95, y: 4 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.95, y: 4 }}
                        transition={{ duration: 0.12 }}
                        className="absolute right-0 top-full mt-1 z-50 min-w-[130px] rounded-xl border border-base-300 bg-base-100 dark:bg-base-200 p-1 shadow-xl"
                      >
                        {isOwner && (
                          <button
                            onClick={() => {
                              setMoreMenuOpen(false);
                              setEditing(true);
                            }}
                            className="w-full flex items-center gap-2 px-3 py-1.5 text-xs font-medium text-base-content/80 hover:bg-base-200 dark:hover:bg-white/10 rounded-lg text-left transition-colors cursor-pointer"
                          >
                            <Pencil size={13} /> Edit
                          </button>
                        )}

                        {(isPostAuthor || isAdmin || isCommunityOwner) && (
                          <button
                            onClick={() => {
                              setMoreMenuOpen(false);
                              handleTogglePin();
                            }}
                            className="w-full flex items-center gap-2 px-3 py-1.5 text-xs font-medium text-base-content/80 hover:bg-base-200 dark:hover:bg-white/10 rounded-lg text-left transition-colors cursor-pointer"
                          >
                            <Pin size={13} /> {isPinned ? 'Unpin' : 'Pin'}
                          </button>
                        )}

                        {(isOwner || isPostAuthor || isAdmin || isCommunityOwner) && (
                          <button
                            onClick={() => {
                              setMoreMenuOpen(false);
                              setConfirmDeleteOpen(true);
                            }}
                            disabled={deleting}
                            className="w-full flex items-center gap-2 px-3 py-1.5 text-xs font-medium text-error hover:bg-error/10 rounded-lg text-left transition-colors cursor-pointer"
                          >
                            <Trash2 size={13} /> Delete
                          </button>
                        )}

                        {currentUsername && !isOwner && (
                          <button
                            onClick={() => {
                              setMoreMenuOpen(false);
                              setReportOpen(true);
                            }}
                            className="w-full flex items-center gap-2 px-3 py-1.5 text-xs font-medium text-base-content/80 hover:bg-base-200 dark:hover:bg-white/10 rounded-lg text-left transition-colors cursor-pointer"
                          >
                            <Flag size={13} /> Report
                          </button>
                        )}
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </div>

              {/* Comment Content */}
              {comment.isDeleted || comment.text === '[Deleted]' ? (
                <p className="text-xs sm:text-[13px] italic text-base-content/40 select-none flex items-center gap-1.5 py-0.5">
                  <Trash2 size={12} className="opacity-50" />
                  <span>
                    {comment.deletedByType === 'ADMINISTRATOR'
                      ? 'This comment was deleted by the administrator'
                      : 'This comment was deleted'}
                  </span>
                </p>
              ) : (
                <div className="text-[13px] sm:text-sm leading-relaxed whitespace-pre-wrap break-words text-base-content/90 font-normal">
                  {formatCommentText(decodedText)}
                </div>
              )}

              {/* Actions Bar: ThumbsUp, ThumbsDown, Reply */}
              <div className="mt-2 flex items-center gap-2 text-xs font-medium text-base-content/60">
                <div className="inline-flex items-center rounded-full bg-base-content/10 dark:bg-white/10 px-1 py-0.5 border border-base-content/5 dark:border-white/5">
                  {/* Like Button */}
                  <button
                    type="button"
                    onClick={() => handleVote('LIKE')}
                    disabled={readOnly}
                    className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-full transition-all cursor-pointer hover:bg-base-content/10 dark:hover:bg-white/10 active:scale-95 disabled:opacity-50 outline-none focus:outline-none ${
                      voteState.userVote === 'LIKE'
                        ? 'text-[#1D4ED8] dark:text-white font-bold'
                        : 'text-base-content/75 dark:text-white/70 hover:text-base-content dark:hover:text-white'
                    }`}
                    title="Like comment"
                  >
                    <ThumbsUp
                      size={13}
                      className={`transition-transform ${
                        voteState.userVote === 'LIKE'
                          ? 'fill-[#1D4ED8] dark:fill-white text-[#1D4ED8] dark:text-white scale-110'
                          : 'text-base-content/75 dark:text-white/70'
                      }`}
                    />
                    <span className="text-[11px] font-extrabold leading-none">
                      {voteState.likes}
                    </span>
                  </button>

                  <div className="w-[1px] h-3 bg-base-content/15 dark:bg-white/15 mx-0.5" />

                  {/* Dislike Button */}
                  <button
                    type="button"
                    onClick={() => handleVote('DISLIKE')}
                    disabled={readOnly}
                    className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-full transition-all cursor-pointer hover:bg-base-content/10 dark:hover:bg-white/10 active:scale-95 disabled:opacity-50 outline-none focus:outline-none ${
                      voteState.userVote === 'DISLIKE'
                        ? 'text-[#1D4ED8] dark:text-white font-bold'
                        : 'text-base-content/75 dark:text-white/70 hover:text-base-content dark:hover:text-white'
                    }`}
                    title="Dislike comment"
                  >
                    <ThumbsDown
                      size={13}
                      className={`transition-transform ${
                        voteState.userVote === 'DISLIKE'
                          ? 'fill-[#1D4ED8] dark:fill-white text-[#1D4ED8] dark:text-white scale-110'
                          : 'text-base-content/75 dark:text-white/70'
                      }`}
                    />
                    {voteState.dislikes > 0 && (
                      <span className="text-[11px] font-extrabold leading-none">
                        {voteState.dislikes}
                      </span>
                    )}
                  </button>
                </div>

                {/* Reply Button */}
                {!readOnly && (
                  <button
                    onClick={() => setShowReplyBox((v) => !v)}
                    className="px-2.5 py-1 rounded-full hover:bg-base-200 dark:hover:bg-white/10 text-base-content/75 hover:text-base-content transition-all font-bold text-xs cursor-pointer"
                  >
                    Reply
                  </button>
                )}
              </div>
            </>
          )}
        </div>
      </div>

      {/* Reply Box under Child Comment (depth > 0) */}
      {depth > 0 && (
        <AnimatePresence>
          {showReplyBox && (
            <motion.div
              initial={{ opacity: 0, height: 0, marginTop: 0 }}
              animate={{ opacity: 1, height: 'auto', marginTop: 10 }}
              exit={{ opacity: 0, height: 0, marginTop: 0 }}
              className="pl-6 sm:pl-7 overflow-hidden"
            >
              <CommentInput
                placeholder={`Reply to @${commentUsername}…`}
                initialValue={`@${commentUsername} `}
                onSubmit={handleReply}
                onCancel={() => setShowReplyBox(false)}
                submitLabel="Reply"
                autoFocus
                avatarSeed={currentUsername}
              />
            </motion.div>
          )}
        </AnimatePresence>
      )}

      {/* Root-Level Elements (depth === 0) */}
      {depth === 0 && (
        <>
          {/* Reply Box on Root Comment */}
          <AnimatePresence>
            {showReplyBox && (
              <motion.div
                initial={{ opacity: 0, height: 0, marginTop: 0 }}
                animate={{ opacity: 1, height: 'auto', marginTop: 12 }}
                exit={{ opacity: 0, height: 0, marginTop: 0 }}
                className="ml-8 sm:ml-10 overflow-hidden"
              >
                <CommentInput
                  placeholder={`Reply to @${commentUsername}…`}
                  initialValue={`@${commentUsername} `}
                  onSubmit={handleReply}
                  onCancel={() => setShowReplyBox(false)}
                  submitLabel="Reply"
                  autoFocus
                  avatarSeed={currentUsername}
                />
              </motion.div>
            )}
          </AnimatePresence>

          {/* Replies Toggle Button */}
          {replyCount > 0 && (
            <div className="mt-2 sm:mt-2.5 ml-8 sm:ml-10">
              <button
                type="button"
                onClick={toggleReplies}
                className="inline-flex items-center gap-1.5 sm:gap-2 text-xs font-bold text-[#1D4ED8] dark:text-blue-400 hover:bg-blue-500/10 px-2.5 py-1 rounded-full transition-all cursor-pointer select-none"
              >
                {loadingReplies && replies.length === 0 ? (
                  <Loader2
                    size={13}
                    className="animate-spin text-[#1D4ED8] dark:text-blue-400"
                  />
                ) : (
                  <ChevronDown
                    size={14}
                    className={`transition-transform duration-200 ${repliesOpen ? 'rotate-180' : ''}`}
                  />
                )}
                <span>
                  {repliesOpen
                    ? 'Hide replies'
                    : `${replyCount} ${replyCount === 1 ? 'reply' : 'replies'}`}
                </span>
              </button>
            </div>
          )}

          {/* Replies Container (Flat list of all thread replies at depth 1) */}
          <AnimatePresence>
            {repliesOpen && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                transition={{ duration: 0.25, ease: 'easeInOut' }}
                className="relative mt-2.5 ml-3.5 sm:ml-4 space-y-3 sm:space-y-3.5"
              >
                {loadingReplies && replies.length === 0 && (
                  <div className="flex items-center gap-2 py-2 pl-6 sm:pl-7 text-xs font-semibold text-base-content/60">
                    <Loader2
                      size={13}
                      className="animate-spin text-[#1D4ED8] dark:text-blue-400"
                    />
                    <span>Loading replies…</span>
                  </div>
                )}
                {replies.map((reply, index) => (
                  <CommentItem
                    key={reply.id}
                    postId={postId}
                    postType={postType}
                    comment={reply}
                    currentUsername={currentUsername}
                    currentRole={currentRole}
                    depth={1}
                    isLastReply={
                      index === replies.length - 1 && !hasMoreReplies
                    }
                    onDeleted={handleReplyDeleted}
                    onUpdated={handleReplyUpdated}
                    onReplyAdded={onReplyAdded}
                    onAddReplyToThread={handleAddReplyToThread}
                    readOnly={readOnly}
                    isCommunityOwner={isCommunityOwner} isPostAuthor={isPostAuthor}
                  />
                ))}

                {hasMoreReplies && (
                  <div className="pt-1.5 pl-6 sm:pl-7">
                    <button
                      onClick={() => loadReplies(repliesCursor)}
                      disabled={loadingReplies}
                      className="text-xs font-bold text-[#1D4ED8] dark:text-blue-400 hover:underline flex items-center gap-1.5 disabled:opacity-50 cursor-pointer"
                    >
                      {loadingReplies ? (
                        <Loader2 size={12} className="animate-spin" />
                      ) : (
                        <span>↓ Load more replies</span>
                      )}
                    </button>
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </>
      )}

      {/* Confirmation and Report Modals */}
      <ConfirmModal
        isOpen={confirmDeleteOpen}
        onClose={() => setConfirmDeleteOpen(false)}
        onConfirm={handleDelete}
        title="Delete Comment"
        message="Are you sure you want to permanently delete this comment and its replies?"
        confirmLabel="Delete"
        isDanger={true}
        isLoading={deleting}
      />

      <ReportModal
        isOpen={reportOpen}
        onClose={() => setReportOpen(false)}
        reportedId={comment.id}
        reportedType="COMMENT"
        reportedName={`Comment by @${comment.author?.username}`}
      />
    </motion.div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════════
// Main CommentSection Component
// ═══════════════════════════════════════════════════════════════════════════════
export default function CommentSection({
  postId,
  postType,
  commentCount: propCount = 0,
  currentUsername,
  currentRole,
  defaultOpen: _defaultOpen = false,
  onCommentCountChange,
  readOnly = false,
  isCommunityOwner = false,
  isPostAuthor = false,
}: CommentSectionProps) {
  const { data: currentUser } = useCurrentUser();
  const [sortBy, setSortBy] = useState<'NEW' | 'TOP'>('NEW');

  const fetchKey = getCommentsCacheKey(postId, postType, sortBy);
  const initialCached = commentsCache.get(fetchKey);

  const [comments, setComments] = useState<CommentDto[]>(
    () => initialCached?.comments ?? [],
  );
  const [count, setCount] = useState(
    () => initialCached?.totalElements ?? propCount,
  );
  const [loading, setLoading] = useState(() => !initialCached);
  const [fetchedOnce, setFetchedOnce] = useState(() => !!initialCached);
  const [cursor, setCursor] = useState<number | undefined>(
    () => initialCached?.cursor,
  );
  const [hasMore, setHasMore] = useState(() => initialCached?.hasMore ?? false);
  const sectionRef = useRef<HTMLDivElement>(null);
  const createCommentMutation = useCreateComment();

  useEffect(() => {
    if (
      typeof propCount === 'number' &&
      !fetchedOnce &&
      !commentsCache.has(fetchKey)
    ) {
      setCount(propCount);
    }
  }, [propCount, fetchedOnce, fetchKey]);

  useEffect(() => {
    if (fetchedOnce) {
      onCommentCountChange?.(count);
    }
  }, [count, fetchedOnce, onCommentCountChange]);

  const fetchComments = useCallback(
    async (nextCursor?: number, isBackground = false) => {
      if (!isBackground) {
        setLoading(true);
      }
      try {
        const params = new URLSearchParams({ limit: String(LIMIT) });
        if (nextCursor) params.set('beforeId', String(nextCursor));
        params.set('sort', sortBy === 'TOP' ? 'TOP' : 'NEW');

        const url = `/api/comments/${postType === 'posts' ? 'post' : 'social-posts'}/${postId}/top-level?${params}`;

        const res = await apiFetch(url);
        const container = res?.data ?? res;
        const rows: CommentDto[] = Array.isArray(container)
          ? container
          : (container?.data ?? container?.content ?? []);
        const newCursor =
          res?.nextCursor ??
          (rows.length === LIMIT ? rows[rows.length - 1]?.id : undefined);
        const more = res?.hasMore ?? rows.length === LIMIT;

        const organized = organizeCommentsIntoTree(rows);

        const serverTotal =
          typeof res?.totalElements === 'number'
            ? res.totalElements
            : typeof res?.totalCount === 'number'
              ? res.totalCount
              : typeof container?.totalElements === 'number'
                ? container.totalElements
                : typeof container?.totalCount === 'number'
                  ? container.totalCount
                  : typeof res?.count === 'number' && !nextCursor && !more
                    ? res.count
                    : null;

        const resolvedCount =
          serverTotal !== null
            ? serverTotal
            : !nextCursor
              ? rows.length
              : count;

        setComments((prev) => {
          if (!nextCursor) {
            commentsCache.set(fetchKey, {
              comments: organized,
              totalElements: resolvedCount,
              cursor: newCursor,
              hasMore: more,
              timestamp: Date.now(),
            });
            return organized;
          }
          const combined = [...prev, ...organized];
          const seen = new Set<number>();
          const deduped = combined.filter((c) => {
            if (seen.has(c.id)) return false;
            seen.add(c.id);
            return true;
          });
          commentsCache.set(fetchKey, {
            comments: deduped,
            totalElements: resolvedCount,
            cursor: newCursor,
            hasMore: more,
            timestamp: Date.now(),
          });
          return deduped;
        });
        setCursor(newCursor);
        setHasMore(more);
        setFetchedOnce(true);

        if (serverTotal !== null) {
          setCount(serverTotal);
        } else if (!nextCursor) {
          setCount((current) => Math.max(current, rows.length));
        }
      } catch (e: unknown) {
        if (!isBackground) {
          console.error(
            '%c[CommentSection:fetchComments] Error fetching comments:',
            'color: #ef4444; font-weight: bold;',
            e,
          );
          showToast.error(parseError(e));
        }
      } finally {
        setLoading(false);
      }
    },
    [postId, postType, sortBy, fetchKey, count],
  );

  // Single canonical fetch effect — strictly deduplicates fetches per postId/postType/sortBy
  const lastFetchedKeyRef = useRef<string>('');

  useEffect(() => {
    const isCached = !!commentsCache.get(fetchKey);
    if (lastFetchedKeyRef.current !== fetchKey) {
      lastFetchedKeyRef.current = fetchKey;

      if (!isCached) {
        setFetchedOnce(false);
        setComments([]);
        setCursor(undefined);
        setLoading(true);
        fetchComments();
      } else {
        // Fast background re-validation without clearing UI
        fetchComments(undefined, true);
      }
    }
  }, [fetchKey, fetchComments]);

  async function handleNewComment(text: string) {
    const idempotencyKey = generateUUID();
    const optimisticId = -Date.now();
    const userImg =
      currentUser?.profileImage ||
      (currentUser as any)?.profileImageUrl ||
      undefined;
    const authorUser = currentUsername || currentUser?.username || 'me';
    const optimisticComment: CommentDto = {
      id: optimisticId,
      text,
      createdAt: new Date().toISOString(),
      author: {
        username: authorUser,
        actualUsername: authorUser,
        profileImage: userImg,
        profileImageUrl: userImg,
        displayName: currentUser?.displayName || authorUser,
      },
      authorProfileImage: userImg,
      userProfileImage: userImg,
      profileImage: userImg,
      isPendingSync: true,
      likeCount: 0,
      dislikeCount: 0,
      userVote: null,
    };

    setComments((prev) => [optimisticComment, ...prev]);
    setCount((n) => n + 1);
    setFetchedOnce(true);

    createCommentMutation.mutate(
      {
        postId,
        postType,
        payload: { text },
        idempotencyKey,
      },
      {
        onSuccess: (res) => {
          const synced = res.data ?? res;
          setComments((prev) =>
            prev.map((c) =>
              c.id === optimisticId ? { ...synced, isPendingSync: false } : c,
            ),
          );
        },
        onError: () => {
          if (!createCommentMutation.isPaused) {
            setComments((prev) => prev.filter((c) => c.id !== optimisticId));
            setCount((n) => Math.max(0, n - 1));
          }
        },
      },
    );
  }

  const handleReplyAdded = useCallback((parentId?: number) => {
    if (parentId) {
      setComments((prev) => {
        const now = new Date().toISOString();
        const updated = prev.map((c) =>
          c.id === parentId
            ? {
                ...c,
                replyCount: (c.replyCount ?? 0) + 1,
                lastActivityAt: now,
                updatedAt: now,
              }
            : c,
        );

        // Bump parent comment to top of the comments feed
        const parentIdx = updated.findIndex((c) => c.id === parentId);
        if (parentIdx > 0) {
          const parentItem = updated[parentIdx];
          const rest = updated.filter((c) => c.id !== parentId);
          return [parentItem, ...rest];
        }
        return updated;
      });
    }
    setCount((n) => n + 1);
  }, []);

  const handleDeleted = useCallback(
    (id: number, countToRemove: number, parentId?: number) => {
      setComments((prev) =>
        prev
          .map((c) => {
            if (c.id === parentId) {
              return {
                ...c,
                replyCount: Math.max(0, (c.replyCount ?? 0) - countToRemove),
              };
            }
            return c;
          })
          .filter((c) => c.id !== id),
      );
      setCount((n) => Math.max(0, n - countToRemove));
    },
    [],
  );

  const handleUpdated = useCallback((updated: CommentDto) => {
    setComments((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
  }, []);

  const isLoggedIn = !!getAuthToken();

  return (
    <motion.div
      ref={sectionRef}
      initial={{ opacity: 0, y: -4 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -4 }}
      transition={{ duration: 0.25, ease: 'easeOut' }}
      className="mt-1"
    >
      {/* YouTube Style Header: Comments count + Sort Filter */}
      <div className="flex items-center justify-between mb-4 px-1">
        <div className="flex items-center gap-2.5">
          <h3 className="text-base font-extrabold text-base-content tracking-tight">
            Comments
          </h3>
          <span className="inline-flex items-center justify-center px-2 py-0.5 rounded-full text-xs font-black bg-[#1D4ED8] text-white shadow-2xs">
            {count}
          </span>
        </div>

        {/* Sort Menu */}
        <div className="flex items-center gap-1">
          <button
            onClick={() => {
              setSortBy((s) => (s === 'NEW' ? 'TOP' : 'NEW'));
            }}
            className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-bold text-base-content/70 hover:bg-base-200 dark:hover:bg-white/10 transition-colors cursor-pointer"
          >
            <ArrowUpDown size={13} />
            <span>{sortBy === 'NEW' ? 'Most recent' : 'Top rated'}</span>
          </button>
        </div>
      </div>

      {/* Composer Area */}
      <div className="px-1 mb-5">
        {readOnly ? (
          <div className="text-center py-4 bg-base-200/50 border border-dashed border-base-content/10 rounded-2xl opacity-60">
            <p className="text-xs font-semibold text-base-content/75">
              New comments are disabled for this post.
            </p>
          </div>
        ) : isLoggedIn ? (
          <CommentInput
            placeholder="Add a comment…"
            onSubmit={handleNewComment}
            avatarSeed={currentUsername}
          />
        ) : (
          <div className="flex items-center gap-4 rounded-2xl border border-dashed border-base-content/20 bg-base-200/30 px-5 py-4 transition-all hover:bg-base-200/50">
            <div className="bg-base-100 p-2 rounded-full shadow-sm text-[#1D4ED8]">
              <ThumbsUp size={16} />
            </div>
            <div className="flex flex-col">
              <p className="text-sm font-bold text-base-content/80">
                Join the discussion
              </p>
              <p className="text-xs text-base-content/40">
                Please sign in to rate or leave a comment.
              </p>
            </div>
          </div>
        )}
      </div>

      {/* Comments List */}
      <motion.div
        variants={containerVariants}
        initial="hidden"
        animate="visible"
        className="space-y-4"
      >
        {loading && comments.length === 0 && (
          <div className="space-y-4 px-1 py-1">
            <div className="flex items-center gap-2 text-xs font-semibold text-base-content/60 mb-2">
              <Loader2
                size={13}
                className="animate-spin text-[#1D4ED8] dark:text-blue-400"
              />
              <span>Loading comments…</span>
            </div>
            {[1, 2, 3].map((i) => (
              <div key={i} className="flex items-start gap-3 animate-pulse">
                <div className="w-9 h-9 rounded-full bg-base-300 dark:bg-base-200 shrink-0" />
                <div className="flex-1 space-y-2 pt-1">
                  <div className="h-3.5 w-32 rounded-md bg-base-300 dark:bg-base-200" />
                  <div className="h-4 w-3/4 max-w-md rounded-md bg-base-300/70 dark:bg-base-200/60" />
                </div>
              </div>
            ))}
          </div>
        )}

        {!loading && fetchedOnce && comments.length === 0 && (
          <div className="text-center py-10 px-4 space-y-1.5 opacity-50">
            <p className="text-sm font-bold text-base-content/70">
              No comments yet
            </p>
            <p className="text-xs text-base-content/40">
              Be the first to share your thoughts!
            </p>
          </div>
        )}

        {[...comments]
          .sort((a, b) => {
            if (sortBy === 'TOP') {
              const aVotes =
                (a.likeCount ?? a.likes?.length ?? 0) -
                (a.dislikeCount ?? a.dislikes?.length ?? 0);
              const bVotes =
                (b.likeCount ?? b.likes?.length ?? 0) -
                (b.dislikeCount ?? b.dislikes?.length ?? 0);
              return bVotes - aVotes;
            }
            const aTime = new Date(a.lastActivityAt || a.createdAt).getTime();
            const bTime = new Date(b.lastActivityAt || b.createdAt).getTime();
            return bTime - aTime;
          })
          .map((comment) => (
            <CommentItem
              key={comment.id}
              postId={postId}
              postType={postType}
              comment={comment}
              currentUsername={currentUsername}
              currentRole={currentRole}
              depth={0}
              onDeleted={handleDeleted}
              onUpdated={handleUpdated}
              onReplyAdded={handleReplyAdded}
              readOnly={readOnly}
              isCommunityOwner={isCommunityOwner} isPostAuthor={isPostAuthor}
            />
          ))}

        {hasMore && (
          <div className="pt-2 px-1">
            <button
              onClick={() => fetchComments(cursor)}
              disabled={loading}
              className="group flex w-full items-center justify-center gap-2 rounded-2xl border border-base-content/10 bg-base-200/40 py-3 text-xs font-bold text-base-content/60 transition-all hover:bg-base-200/80 hover:text-[#1D4ED8] dark:hover:text-blue-400 active:scale-[0.99] disabled:opacity-40 cursor-pointer"
            >
              {loading ? (
                <Loader2 size={13} className="animate-spin" />
              ) : (
                <ChevronDown
                  size={13}
                  className="group-hover:translate-y-0.5 transition-transform"
                />
              )}
              {loading ? 'Loading comments…' : 'Load more comments'}
            </button>
          </div>
        )}
      </motion.div>
    </motion.div>
  );
}
