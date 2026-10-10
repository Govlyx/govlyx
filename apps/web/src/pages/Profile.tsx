import { useState, useEffect, useRef, useMemo, useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import axiosInstance from "../api/axiosConfig";
import { showToast } from "../utils/toast";
import ImageEditorModal from "../components/modals/ImageEditorModal";
import {
  ShieldCheck,
  CheckCircle,
  Clock,
  Camera,
  Heart,
  Bookmark,
  MessageSquare,
  List,
  Mail,
  MapPin,
  Loader2,
  Pencil,
  Check,
  AlertCircle,
  CheckCircle2,
  ChevronDown,
} from "lucide-react";
import EmptyState from "../components/ui/EmptyState";
import ProfileTabs from "../components/profile/ProfileTabs";
import PostCard from "../components/post/PostCard";
import PostSkeleton from "../components/post/PostSkeleton";
import type { AnyPost, CurrentUser } from "../components/post/PostCard";
import { useCurrentUser } from "../hooks/useUser";
import { toPostCardPost, resolveMediaUrl } from "../utils/postUtils";
import { getUserRole } from "../utils/auth";
import { fetchPincodeFromCoordinates } from "../utils/geocoding";

// ─── Editable Row ──────────────────────────────────────────────────────────────
function EditableRow({
  icon, label, value, placeholder, editing, onEdit, onCancel,
  inputValue, onInputChange, type = "text", maxLength, hint,
  rightContent, hideValue = false,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  placeholder: string;
  editing: boolean;
  onEdit: () => void;
  onCancel: () => void;
  inputValue: string;
  onInputChange: (v: string) => void;
  type?: string;
  maxLength?: number;
  hint?: React.ReactNode;
  rightContent?: React.ReactNode;
  hideValue?: boolean;
}) {
  if (editing) {
    return (
      <div className="space-y-2 text-left w-full mt-2">
        <label className="text-xs font-bold text-slate-800 dark:text-slate-200">{label}</label>
        {type === "textarea" ? (
          <textarea
            className="textarea w-full text-xs sm:text-sm bg-base-100 border border-black/10 dark:border-base-300 rounded-xl min-h-[80px] focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/30 font-medium"
            value={inputValue}
            onChange={(e) => onInputChange(e.target.value)}
            placeholder={placeholder}
            maxLength={maxLength}
          />
        ) : (
          <input
            className="input w-full input-sm bg-base-100 border border-black/10 dark:border-base-300 rounded-xl text-xs sm:text-sm h-10 px-3.5 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/30 font-medium"
            type={type}
            value={inputValue}
            onChange={(e) => onInputChange(e.target.value)}
            placeholder={placeholder}
            maxLength={maxLength}
            autoFocus
          />
        )}
        {hint && <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">{hint}</p>}
        {type === "textarea" && maxLength && (
          <p className="text-xs text-right text-slate-400">{inputValue.length}/{maxLength}</p>
        )}
        <div className="flex justify-end gap-2">
          <button 
            className="btn btn-xs bg-base-300/80 hover:bg-base-300 text-slate-700 dark:text-slate-200 border-none rounded-xl px-3 py-1 font-bold cursor-pointer" 
            onClick={onCancel}
          >
            Cancel
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-between py-1.5 w-full text-left">
      <div className="flex items-center gap-3 min-w-0">
        <span className="text-slate-400 dark:text-slate-500 shrink-0">{icon}</span>
        <div className="min-w-0">
          <p className="text-xs sm:text-sm font-bold text-slate-800 dark:text-slate-200">{label}</p>
          {!hideValue && (
            <div className="flex items-center gap-2 mt-0.5">
              <p className="text-xs text-slate-500 dark:text-slate-400 truncate font-medium">
                {value || <span className="italic opacity-60">Not set</span>}
              </p>
              {rightContent}
            </div>
          )}
        </div>
      </div>
      <button 
        className="bg-white hover:bg-slate-50 text-[#1D4ED8] font-bold text-xs px-3 py-1.5 rounded-xl shadow-xs border border-black/5 hover:border-[#1D4ED8]/20 transition-all active:scale-95 cursor-pointer flex items-center gap-1.5 shrink-0" 
        onClick={onEdit}
      >
        <Pencil size={12} className="stroke-[2.5]" /> Edit
      </button>
    </div>
  );
}

// ─── auth helpers ─────────────────────────────────────────────────────────────
async function apiFetch(url: string) {
  const res = await axiosInstance.get(url);
  return res.data;
}

// ─── share helper ─────────────────────────────────────────────────────────────

// ─── date helpers ─────────────────────────────────────────────────────────────
function formatDate(raw: any): string {
  if (raw === null || raw === undefined || raw === "") return "Unknown";
  let d: Date;
  if (Array.isArray(raw)) {
    d = new Date(
      raw[0],
      raw[1] - 1,
      raw[2] ?? 1,
      raw[3] ?? 0,
      raw[4] ?? 0,
      raw[5] ?? 0
    );
  } else {
    const ms = Number(raw);
    d = isNaN(ms) ? new Date(raw as string) : new Date(ms);
  }
  if (isNaN(d.getTime())) return "Unknown";
  return d.toLocaleDateString("en-IN", { month: "short", year: "numeric" });
}

function formatRelativeTime(raw: any): string {
  if (raw === null || raw === undefined || raw === "") return "Recently";
  let ms: number;
  if (typeof raw === "number") {
    ms = raw;
  } else if (raw instanceof Date) {
    ms = raw.getTime();
  } else if (Array.isArray(raw)) {
    ms = new Date(raw[0], raw[1] - 1, raw[2] ?? 1, raw[3] ?? 0, raw[4] ?? 0, raw[5] ?? 0).getTime();
  } else {
    const num = Number(raw);
    ms = isNaN(num) ? new Date(raw).getTime() : num;
  }
  if (isNaN(ms) || ms <= 0) return "Recently";

  const diffMs = Math.max(0, Date.now() - ms);
  const seconds = Math.floor(diffMs / 1000);
  if (seconds < 60) return "Just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  const weeks = Math.floor(days / 7);
  if (weeks < 4) return `${weeks}w ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months}mo ago`;
  const years = Math.floor(days / 365);
  return `${years}y ago`;
}

// ─── types ────────────────────────────────────────────────────────────────────
type PostFilter = "all" | "active" | "resolved";
type Tab = "posts" | "social" | "activity";
type ActivityFilter = "all" | "liked" | "saved" | "commented";

type ActivityItem = {
  id: string;
  post: AnyPost;
  type: "liked" | "saved" | "commented";
  time: number;
  commentText?: string;
};

// ─── shared ActionBtn ─────────────────────────────────────────────────────────

// ─── StatCard ─────────────────────────────────────────────────────────────────
function StatCard({ value, label }: { value: number | string; label: string }) {
  return (
    <div className="rounded-2xl border border-black/10 dark:border-base-300 bg-base-200 p-3 sm:p-5 text-center shadow-sm dark:shadow-none transition-all flex flex-col justify-center items-center min-w-0">
      <p className="text-xl sm:text-2xl font-black text-slate-900 dark:text-white tracking-tight">{value}</p>
      <p className="text-[9px] sm:text-xs font-black text-slate-500 dark:text-slate-400 mt-0.5 uppercase tracking-tight sm:tracking-wider truncate w-full text-center">{label}</p>
    </div>
  );
}

// Removed redundant local IssuePostCard, GovernmentBroadcastCard, PollCard, SocialPostCard

function InfiniteScrollTrigger({ onIntersect, disabled }: { onIntersect: () => void; disabled?: boolean }) {
  const ref = useRef<HTMLDivElement>(null);
  const cbRef = useRef(onIntersect);
  useEffect(() => { cbRef.current = onIntersect; }, [onIntersect]);

  useEffect(() => {
    const el = ref.current;
    if (!el || disabled) return;

    let parent: HTMLElement | null = el.parentElement;
    let scrollParent: HTMLElement | null = null;
    while (parent && parent !== document.body) {
      const overflowY = window.getComputedStyle(parent).overflowY;
      if (overflowY === "auto" || overflowY === "scroll") {
        scrollParent = parent;
        break;
      }
      parent = parent.parentElement;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && !disabled) {
          cbRef.current();
        }
      },
      {
        root: scrollParent || null,
        threshold: 0.01,
        rootMargin: "600px 0px 600px 0px",
      }
    );

    observer.observe(el);

    const rect = el.getBoundingClientRect();
    const parentBottom = scrollParent ? scrollParent.getBoundingClientRect().bottom : window.innerHeight;
    if (rect.top <= parentBottom + 600) {
      cbRef.current();
    }

    return () => observer.disconnect();
  }, [disabled]);

  return <div ref={ref} className="h-8 w-full pointer-events-none" aria-hidden="true" />;
}

// ─── shared post mappers ──────────────────────────────────────────────────────
function mapSocialPost(p: any): AnyPost {
  return toPostCardPost(p);
}

function mapIssuePost(p: any): AnyPost {
  return toPostCardPost({ ...p, variant: "issue" });
}


// ═══════════════════════════════════════════════════════════════════════════════
// Profile page
// ═══════════════════════════════════════════════════════════════════════════════
const Profile = () => {
  const queryClient = useQueryClient();
  const { data: user } = useCurrentUser();

  // Profile Details Edit State
  const [editField, setEditField] = useState<string | null>(null);
  const [email, setEmail] = useState("");
  const [pincode, setPincode] = useState("");
  const [savingDetails, setSavingDetails] = useState(false);
  const [updatingGps, setUpdatingGps] = useState(false);

  // ── Scenario B: Pincode Auto-Lookup & Email Verification ──
  const [pincodeDetails, setPincodeDetails] = useState<string | null>(null);
  const [fetchingPincode, setFetchingPincode] = useState(false);
  const [sendingVerification, setSendingVerification] = useState(false);
  const [showVerificationModal, setShowVerificationModal] = useState(false);

  // Profile Photo Upload State
  const [uploadingImg, setUploadingImg] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editorImageSrc, setEditorImageSrc] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [tab, setTab] = useState<Tab>("posts");
  const [postFilter, setPostFilter] = useState<PostFilter>("all");
  const [hasLoadedSocial, setHasLoadedSocial] = useState(false);
  const [hasLoadedActivity, setHasLoadedActivity] = useState(false);
  const [activityFilter, setActivityFilter] = useState<ActivityFilter>("all");

  const [issuesFilterOpen, setIssuesFilterOpen] = useState(false);
  const [activityFilterOpen, setActivityFilterOpen] = useState(false);
  const issuesDropdownRef = useRef<HTMLDivElement>(null);
  const activityDropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (issuesDropdownRef.current && !issuesDropdownRef.current.contains(e.target as Node)) {
        setIssuesFilterOpen(false);
      }
      if (activityDropdownRef.current && !activityDropdownRef.current.contains(e.target as Node)) {
        setActivityFilterOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Cursors & HasMore for Issues
  const [allPostsCursor, setAllPostsCursor] = useState<number | null>(null);
  const [allPostsHasMore, setAllPostsHasMore] = useState(false);
  const [activePostsCursor, setActivePostsCursor] = useState<number | null>(null);
  const [activePostsHasMore, setActivePostsHasMore] = useState(false);
  const [resolvedPostsCursor, setResolvedPostsCursor] = useState<number | null>(null);
  const [resolvedPostsHasMore, setResolvedPostsHasMore] = useState(false);

  // Cursors & HasMore for Social Posts
  const [socialPostsCursor, setSocialPostsCursor] = useState<number | null>(null);
  const [socialPostsHasMore, setSocialPostsHasMore] = useState(false);

  const [loadingMore, setLoadingMore] = useState(false);

  // Sync email and pincode from user
  useEffect(() => {
    if (!user) return;
    setEmail(user.email || "");
    setPincode(user.pincode || "");
  }, [user]);

  // Pincode lookup effect
  useEffect(() => {
    if (editField === "pincode" && /^[1-9]\d{5}$/.test(pincode)) {
      const fetchPincodeDetails = async () => {
        setFetchingPincode(true);
        setPincodeDetails(null);
        try {
          const res = await axiosInstance.get(`/api/pincode/${pincode}`);
          const json = res.data;
          const isWrapped = json?.success !== undefined && json?.data !== undefined;
          const data = isWrapped ? json.data : json;
          if (data && (data.state || data.city || data.district || data.areaName)) {
            const cityDistrict = data.city || data.district || "";
            const state = data.state || "";
            const area = data.areaName || "";
            const parts = [area, cityDistrict, state].filter(Boolean);
            const locationStr = parts.join(", ");
            setPincodeDetails(locationStr || "Verified Location");
          } else {
            setPincodeDetails("Location not found");
          }
        } catch {
          setPincodeDetails("Location not found");
        } finally {
          setFetchingPincode(false);
        }
      };
      fetchPincodeDetails();
    } else {
      setPincodeDetails(null);
    }
  }, [pincode, editField]);

  const handleSendVerification = async () => {
    if (!user?.email) return;
    setSendingVerification(true);
    try {
      await axiosInstance.post(`/api/auth/resend-verification?email=${encodeURIComponent(user.email)}`);
      showToast.success("Verification link sent! Please check your email.");
      setShowVerificationModal(true);
    } catch (err: any) {
      showToast.error(err.response?.data?.message || "Failed to send verification link");
    } finally {
      setSendingVerification(false);
    }
  };

  const handleUpdateGps = useCallback(() => {
    if (!navigator.geolocation) {
      showToast.error("GPS is not supported by this browser");
      return;
    }

    setUpdatingGps(true);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        try {
          const pincode = await fetchPincodeFromCoordinates(pos.coords.latitude, pos.coords.longitude);

          await axiosInstance.put("/api/users/location", {
            latitude: pos.coords.latitude,
            longitude: pos.coords.longitude,
          });

          if (pincode) {
            await axiosInstance.put("/api/users/update-pincode", { pincode });
          }

          await Promise.all([
            queryClient.invalidateQueries({ queryKey: ["currentUser"] }),
            queryClient.invalidateQueries({ queryKey: ["feed"] }),
          ]);
          if (pincode) {
            showToast.success(`Precise location updated to ${pincode}!`);
          } else {
            showToast.success("Precise location updated successfully!");
          }
        } catch (err: any) {
          showToast.error(err.response?.data?.message || "Failed to update precise location");
        } finally {
          setUpdatingGps(false);
        }
      },
      () => {
        showToast.error("Unable to access GPS. Please allow location access and try again.");
        setUpdatingGps(false);
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
    );
  }, [queryClient]);

  // Save details
  const saveProfile = async () => {
    if (editField === "email" && email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return showToast.error("Enter a valid email address");
    }
    if (editField === "pincode" && pincode && !/^[1-9]\d{5}$/.test(pincode)) {
      return showToast.error("Enter a valid 6-digit Indian pincode");
    }

    setSavingDetails(true);
    try {
      if (editField === "pincode") {
        await axiosInstance.put("/api/users/update-pincode", { pincode });
        await queryClient.invalidateQueries({ queryKey: ["feed"] });
      } else if (editField === "email") {
        const res = await axiosInstance.put("/api/users/profile", {
          email: email || undefined,
        });
        await queryClient.invalidateQueries({ queryKey: ["currentUser"] });
        setEditField(null);
        showToast.success(res.data?.message || "Changes saved successfully");
        return;
      }
      await queryClient.invalidateQueries({ queryKey: ["currentUser"] });
      setEditField(null);
      showToast.success("Changes saved successfully");
    } catch (err: any) {
      showToast.error(err.response?.data?.message || "Failed to update profile");
    } finally {
      setSavingDetails(false);
    }
  };

  // Image Upload Methods
  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      return showToast.error("Upload a JPEG, PNG, or WebP image");
    }
    if (file.size > 5 * 1024 * 1024) {
      return showToast.error("Image must be under 5 MB");
    }

    const reader = new FileReader();
    reader.onload = () => {
      setEditorImageSrc(reader.result as string);
      setEditorOpen(true);
    };
    reader.readAsDataURL(file);
  };

  const handleEditorSave = async (editedBlob: Blob) => {
    setUploadingImg(true);
    setEditorOpen(false);
    try {
      const fd = new FormData();
      const file = new File([editedBlob], "profile.jpg", { type: "image/jpeg" });
      fd.append("file", file);
      
      await axiosInstance.post("/api/users/profile-image", fd, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      await queryClient.invalidateQueries({ queryKey: ["currentUser"] });
      showToast.success("Profile photo updated");
    } catch (err: any) {
      showToast.error(err.response?.data?.message || "Failed to upload photo");
    } finally {
      setUploadingImg(false);
      setEditorImageSrc(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleDelete = (type: 'posts' | 'social-posts', id: number) => {
    // Parent only handles state removal. PostCard handles confirm + API.
    if (type === 'posts') {
      setAllPosts(prev => prev.filter(p => p.id !== id));
      setActivePosts(prev => prev.filter(p => p.id !== id));
      setResolvedPosts(prev => prev.filter(p => p.id !== id));
      setIssueCount(n => n !== null ? Math.max(0, n - 1) : null);
    } else {
      setSocialPosts(prev => prev.filter(p => p.id !== id));
      setSocialCount(n => n !== null ? Math.max(0, n - 1) : null);
    }
  };

  const applyResolveUpdate = (prev: AnyPost[], id: number, resolved: boolean, message: string): AnyPost[] =>
    prev.map((p) => {
      if (p.id === id && p.variant === "issue") {
        return {
          ...p,
          status: resolved ? "RESOLVED" : "ACTIVE",
          isResolved: resolved,
          resolutionMessage: resolved ? message : (p as any).resolutionMessage,
          reopened: !resolved,
          isReopened: !resolved,
          reopenedReason: !resolved ? message : (p as any).reopenedReason,
        } as any as AnyPost;
      }
      return p;
    });

  const handleResolve = (id: number, resolved: boolean, message: string) => {
    setAllPosts(prev => applyResolveUpdate(prev, id, resolved, message));
    // Keep active/resolved filtered arrays in sync too
    if (resolved) {
      // Move from activePosts → resolvedPosts
      setActivePosts(prev => prev.filter(p => p.id !== id));
      setResolvedPosts(prev => {
        const existing = prev.find(p => p.id === id);
        const updated = allPosts.find(p => p.id === id);
        if (existing || !updated) return prev.map(p => p.id === id ? applyResolveUpdate([p], id, resolved, message)[0] : p);
        return [applyResolveUpdate([updated], id, resolved, message)[0], ...prev];
      });
    } else {
      // Move from resolvedPosts → activePosts
      setResolvedPosts(prev => prev.filter(p => p.id !== id));
      setActivePosts(prev => {
        const existing = prev.find(p => p.id === id);
        const updated = allPosts.find(p => p.id === id);
        if (existing || !updated) return prev.map(p => p.id === id ? applyResolveUpdate([p], id, resolved, message)[0] : p);
        return [applyResolveUpdate([updated], id, resolved, message)[0], ...prev];
      });
    }
  };

  const [username, setUsername] = useState<string>("...");
  const [memberSince, setMemberSince] = useState("");
  const [location, setLocation] = useState("India");
  const [currentUser, setCurrentUser] = useState<CurrentUser | undefined>();

  const [issueCount, setIssueCount] = useState<number | null>(null);
  const [socialCount, setSocialCount] = useState<number | null>(null);
  const [communityCount, setCommunityCount] = useState<number | null>(null);
  const allCountsLoaded = issueCount !== null && socialCount !== null && communityCount !== null;

  const [allPosts, setAllPosts] = useState<AnyPost[]>([]);
  const [activePosts, setActivePosts] = useState<AnyPost[]>([]);
  const [resolvedPosts, setResolvedPosts] = useState<AnyPost[]>([]);
  const [hasLoadedActive, setHasLoadedActive] = useState(false);
  const [hasLoadedResolved, setHasLoadedResolved] = useState(false);
  const [socialPosts, setSocialPosts] = useState<AnyPost[]>([]);
  const [activityItems, setActivityItems] = useState<ActivityItem[]>([]);

  const [loadingPosts, setLoadingPosts] = useState(false);
  const [loadingFilteredPosts, setLoadingFilteredPosts] = useState(false);
  const [loadingSocial, setLoadingSocial] = useState(false);
  const [loadingActivity, setLoadingActivity] = useState(false);

  const isDept = getUserRole() === "ROLE_DEPARTMENT";

  const updatePostState = useCallback((postId: number, variant: string, updater: Partial<AnyPost> | ((p: AnyPost) => Partial<AnyPost>)) => {
    const updateListItem = (prev: AnyPost[]) =>
      prev.map((p) => {
        if (p.id === postId && p.variant === variant) {
          const changes = typeof updater === "function" ? updater(p) : updater;
          return { ...p, ...changes } as AnyPost;
        }
        return p;
      });

    setAllPosts(updateListItem);
    setSocialPosts(updateListItem);
    setActivityItems((prev) =>
      prev.map((item) => {
        if (item.post.id === postId && item.post.variant === variant) {
          const changes = typeof updater === "function" ? updater(item.post) : updater;
          return { ...item, post: { ...item.post, ...changes } as AnyPost };
        }
        return item;
      })
    );
  }, []);

  const handleLike = useCallback((postId: number, variant: string, liked: boolean) => {
    updatePostState(postId, variant, (post) => {
      const hasDislikeSupport = post.variant === "issue";
      const isPreviouslyDisliked = hasDislikeSupport && !!(post as any).isDislikedByCurrentUser;
      return {
        isLikedByCurrentUser: liked,
        likeCount: post.likeCount + (liked ? 1 : -1),
        ...(isPreviouslyDisliked && liked && {
          isDislikedByCurrentUser: false,
          dislikeCount: Math.max(0, ((post as any).dislikeCount ?? 0) - 1)
        })
      } as Partial<AnyPost>;
    });
  }, [updatePostState]);

  const handleDislike = useCallback((postId: number, variant: string, disliked: boolean) => {
    updatePostState(postId, variant, (post) => {
      const isPreviouslyLiked = !!post.isLikedByCurrentUser;
      return {
        isDislikedByCurrentUser: disliked,
        dislikeCount: ((post as any).dislikeCount ?? 0) + (disliked ? 1 : -1),
        ...(isPreviouslyLiked && disliked && {
          isLikedByCurrentUser: false,
          likeCount: Math.max(0, post.likeCount - 1)
        })
      } as Partial<AnyPost>;
    });
  }, [updatePostState]);

  const handleSave = useCallback((postId: number, variant: string, saved: boolean) => {
    updatePostState(postId, variant, {
      isSaved: saved,
      isSavedByCurrentUser: saved
    } as Partial<AnyPost>);
  }, [updatePostState]);

  const handleShare = useCallback((postId: number, variant: string) => {
    updatePostState(postId, variant, (post) => ({
      shareCount: (post.shareCount ?? 0) + 1
    }));
  }, [updatePostState]);

  useEffect(() => {
    if (!user) return;
    const name = user.actualUsername ?? user.username ?? "User";
    setUsername(name);
    
    if (user.createdAt)
      setMemberSince(formatDate(user.createdAt));
    if (user.pincode) setLocation(user.pincode);
    else if (user.address) setLocation(user.address);

    setCurrentUser({
      id: user.id,
      username: name,
      role: user.role,
    });
  }, [user]);

  // Fetch counts + pre-populate lists
  useEffect(() => {
    if (!user?.id) return;

    setHasLoadedSocial(false);
    setHasLoadedActive(false);
    setHasLoadedResolved(false);
    setHasLoadedActivity(false);
    setActivityItems([]);

    const isDeptUser = getUserRole() === "ROLE_DEPARTMENT";

    setLoadingPosts(true);
    // Fetch initial user posts list (limit reduced to 10 for faster page load)
    apiFetch("/api/posts/my-posts?limit=10")
      .then((b) => {
        const posts: AnyPost[] = (b?.data?.data ?? []).map((p: any) =>
          isDeptUser
            ? toPostCardPost({ ...p, variant: "government", isGovernmentBroadcast: true })
            : mapIssuePost(p)
        );
        setAllPosts(posts);
        setAllPostsHasMore(b?.data?.hasMore ?? false);
        setAllPostsCursor(b?.data?.nextCursor ?? null);
      })
      .catch((err) => {
        console.error("Failed to fetch user posts list:", err);
        setAllPosts([]);
      })
      .finally(() => {
        setLoadingPosts(false);
      });

    // Fetch user posts (Issues) count separately using the dedicated count endpoint
    apiFetch("/api/posts/count/my-posts")
      .then((b) => {
        if (b?.success && b.data !== undefined) {
          setIssueCount(Number(b.data));
        } else {
          setIssueCount(0);
        }
      })
      .catch((err) => {
        console.error("Failed to fetch user posts count:", err);
        setIssueCount(0);
      });

    if (isDeptUser) {
      setSocialCount(0);
      setCommunityCount(0);
      return;
    }

    // Fetch user social posts (S-Posts) count separately using the dedicated count endpoint
    apiFetch("/api/social-posts/count/my-posts")
      .then((b) => {
        if (b?.success && b.data !== undefined) {
          setSocialCount(Number(b.data));
        } else {
          setSocialCount(0);
        }
      })
      .catch((err) => {
        console.error("Failed to fetch user social posts count:", err);
        setSocialCount(0);
      });

    // Fetch Groups count separately using the new dedicated count endpoint
    apiFetch("/api/communities/count/me")
      .then((b) => {
        if (b?.success && b.data !== undefined) {
          setCommunityCount(Number(b.data));
        } else {
          setCommunityCount(0);
        }
      })
      .catch((err) => {
        console.error("Failed to fetch user communities count:", err);
        setCommunityCount(0);
      });
  }, [user?.id]);

  // ── Lazy-fetch active / resolved posts when filter is selected ──────────────
  useEffect(() => {
    if (tab !== "posts") return;

    if (postFilter === "active" && !hasLoadedActive) {
      setLoadingFilteredPosts(true);
      apiFetch("/api/posts/my-posts/active?limit=10")
        .then((b) => {
          setActivePosts((b?.data?.data ?? []).map(mapIssuePost));
          setActivePostsHasMore(b?.data?.hasMore ?? false);
          setActivePostsCursor(b?.data?.nextCursor ?? null);
          setHasLoadedActive(true);
        })
        .catch((err) => console.error("Failed to fetch active posts:", err))
        .finally(() => setLoadingFilteredPosts(false));
    }

    if (postFilter === "resolved" && !hasLoadedResolved) {
      setLoadingFilteredPosts(true);
      apiFetch("/api/posts/my-posts/resolved?limit=10")
        .then((b) => {
          setResolvedPosts((b?.data?.data ?? []).map(mapIssuePost));
          setResolvedPostsHasMore(b?.data?.hasMore ?? false);
          setResolvedPostsCursor(b?.data?.nextCursor ?? null);
          setHasLoadedResolved(true);
        })
        .catch((err) => console.error("Failed to fetch resolved posts:", err))
        .finally(() => setLoadingFilteredPosts(false));
    }
  }, [postFilter, tab, hasLoadedActive, hasLoadedResolved]);

  // Fetch social posts on tab open
  useEffect(() => {
    if (tab !== "social") return;
    if (hasLoadedSocial) return;
    if (socialPosts.length > 0) return;
    setLoadingSocial(true);
    apiFetch("/api/social-posts/my-posts?limit=10")
      .then((b) => {
        setSocialPosts((b?.data?.data ?? []).map(mapSocialPost));
        setSocialPostsHasMore(b?.data?.hasMore ?? false);
        setSocialPostsCursor(b?.data?.nextCursor ?? null);
        setHasLoadedSocial(true);
      })
      .catch(() => {})
      .finally(() => setLoadingSocial(false));
  }, [tab, hasLoadedSocial, socialPosts.length]);

  // Fetch activity — show saved, liked, and commented posts
  useEffect(() => {
    if (!user?.id || hasLoadedActivity) return;
    // Load immediately if user is on activity tab, or eagerly in background when user is logged in
    setLoadingActivity(true);

    Promise.allSettled([
      axiosInstance.get("/api/interactions/saved/social-posts?page=0&size=30").then((r) => r.data),
      axiosInstance.get("/api/interactions/saved/posts?page=0&size=30").then((r) => r.data),
      axiosInstance.get("/api/interactions/liked?page=0&size=30").then((r) => r.data),
      axiosInstance.get("/api/interactions/commented?page=0&size=30").then((r) => r.data),
    ])
      .then(([savedSocialRes, savedPostsRes, likedRes, commentedRes]) => {
        const items: ActivityItem[] = [];

        // 1. Process Saved Social
        if (savedSocialRes.status === "fulfilled") {
          const res = savedSocialRes.value;
          const rows = res?.data?.content ?? res?.content ?? (Array.isArray(res?.data) ? res.data : []);
          rows.forEach((row: any) => {
            const raw = row.socialPost || row.post || row;
            if (!raw?.id) return;
            const mapped = toPostCardPost({
              ...raw,
              isSaved: true,
              isSavedByCurrentUser: true,
            });
            const time = new Date(row.savedAt || row.createdAt || raw.createdAt || 0).getTime();
            items.push({
              id: `saved-social-${raw.id}-${time}`,
              post: mapped,
              time,
              type: "saved",
            });
          });
        }

        // 2. Process Saved Regular
        if (savedPostsRes.status === "fulfilled") {
          const res = savedPostsRes.value;
          const rows = res?.data?.content ?? res?.content ?? (Array.isArray(res?.data) ? res.data : []);
          rows.forEach((row: any) => {
            const raw = row.post || row.socialPost || row;
            if (!raw?.id) return;
            const mapped = toPostCardPost({
              ...raw,
              isSaved: true,
              isSavedByCurrentUser: true,
            });
            const time = new Date(row.savedAt || row.createdAt || raw.createdAt || 0).getTime();
            items.push({
              id: `saved-post-${raw.id}-${time}`,
              post: mapped,
              time,
              type: "saved",
            });
          });
        }

        // 3. Process Liked & Disliked
        if (likedRes.status === "fulfilled") {
          const res = likedRes.value;
          const data = res?.data || res || {};
          const socialLikes = data.socialLikes?.content || data.socialLikes || [];
          const issueLikes = data.issueLikes?.content || data.issueLikes || [];

          if (Array.isArray(socialLikes)) {
            socialLikes.forEach((row: any) => {
              const raw = row.socialPost || row.post || row;
              if (!raw?.id) return;
              const isLike = row.interactionType === "LIKE" || row.liked === true || (!row.interactionType && !row.disliked);
              const isDislike = row.interactionType === "DISLIKE" || row.disliked === true;
              const mapped = toPostCardPost({
                ...raw,
                isLikedByCurrentUser: isLike,
                isDislikedByCurrentUser: isDislike,
              });
              const time = new Date(row.createdAt || row.likedAt || raw.createdAt || 0).getTime();
              items.push({
                id: `liked-social-${raw.id}-${time}`,
                post: mapped,
                time,
                type: "liked",
              });
            });
          }

          if (Array.isArray(issueLikes)) {
            issueLikes.forEach((row: any) => {
              const raw = row.post || row.socialPost || row;
              if (!raw?.id) return;
              const isLike = row.interactionType === "LIKE" || row.liked === true || (!row.interactionType && !row.disliked);
              const isDislike = row.interactionType === "DISLIKE" || row.disliked === true;
              const mapped = toPostCardPost({
                ...raw,
                isLikedByCurrentUser: isLike,
                isDislikedByCurrentUser: isDislike,
              });
              const time = new Date(row.createdAt || row.likedAt || raw.createdAt || 0).getTime();
              items.push({
                id: `liked-issue-${raw.id}-${time}`,
                post: mapped,
                time,
                type: "liked",
              });
            });
          }
        }

        // 4. Process Commented
        if (commentedRes.status === "fulfilled") {
          const res = commentedRes.value;
          const data = res?.data || res || {};
          const socialComments = data.socialComments?.content || data.socialComments || [];
          const issueComments = data.issueComments?.content || data.issueComments || [];

          if (Array.isArray(socialComments)) {
            socialComments.forEach((row: any) => {
              const raw = row.socialPost || row.post;
              if (!raw?.id) return;
              const mapped = toPostCardPost(raw);
              const commentText = row.content || row.commentText || row.text || row.body || row.message || row.comment?.content || row.comment?.text || "";
              const time = new Date(row.createdAt || row.commentedAt || raw.createdAt || 0).getTime();
              items.push({
                id: `commented-social-${raw.id}-${time}`,
                post: mapped,
                time,
                type: "commented",
                commentText: commentText ? String(commentText).trim() : undefined,
              });
            });
          }

          if (Array.isArray(issueComments)) {
            issueComments.forEach((row: any) => {
              const raw = row.post || row.issue || row.socialPost;
              if (!raw?.id) return;
              const mapped = toPostCardPost(raw);
              const commentText = row.content || row.commentText || row.text || row.body || row.message || row.comment?.content || row.comment?.text || "";
              const time = new Date(row.createdAt || row.commentedAt || raw.createdAt || 0).getTime();
              items.push({
                id: `commented-issue-${raw.id}-${time}`,
                post: mapped,
                time,
                type: "commented",
                commentText: commentText ? String(commentText).trim() : undefined,
              });
            });
          }
        }

        // Sort by most recent activity
        items.sort((a, b) => b.time - a.time);
        setActivityItems(items);
        setHasLoadedActivity(true);
      })
      .catch((err) => console.error("Failed to fetch activity", err))
      .finally(() => setLoadingActivity(false));
  }, [user?.id, tab, hasLoadedActivity]);

  const activityCounts = useMemo(() => {
    const allSeen = new Set<string>();
    const likedSeen = new Set<string>();
    const savedSeen = new Set<string>();
    const commentedSeen = new Set<string>();

    activityItems.forEach((item) => {
      const key = `${item.post.variant}-${item.post.id}`;
      allSeen.add(key);
      if (item.type === "liked") likedSeen.add(key);
      if (item.type === "saved") savedSeen.add(key);
      if (item.type === "commented") commentedSeen.add(key);
    });

    return {
      all: allSeen.size,
      liked: likedSeen.size,
      saved: savedSeen.size,
      commented: commentedSeen.size,
    };
  }, [activityItems]);

  const displayedActivity = useMemo(() => {
    let filtered = activityItems;
    if (activityFilter !== "all") {
      filtered = activityItems.filter((item) => item.type === activityFilter);
    }

    const uniqueItems: ActivityItem[] = [];
    const seenKeys = new Set<string>();
    filtered.forEach((item) => {
      const key = activityFilter === "all"
        ? `${item.post.variant}-${item.post.id}`
        : `${item.type}-${item.post.variant}-${item.post.id}`;
      if (!seenKeys.has(key)) {
        seenKeys.add(key);
        uniqueItems.push(item);
      }
    });
    return uniqueItems;
  }, [activityItems, activityFilter]);

  const displayedPosts = useMemo(() => {
    if (postFilter === "active")   return activePosts;
    if (postFilter === "resolved") return resolvedPosts;
    return allPosts;
  }, [allPosts, activePosts, resolvedPosts, postFilter]);

  const loadMore = useCallback(() => {
    if (!user?.id || loadingMore) return;

    const isDeptUser = getUserRole() === "ROLE_DEPARTMENT";

    if (tab === "posts") {
      setLoadingMore(true);
      if (postFilter === "all") {
        const lastId = allPostsCursor || (allPosts.length > 0 ? allPosts[allPosts.length - 1]?.id : null);
        const cursorParam = lastId ? `&beforeId=${lastId}&lastPostId=${lastId}&cursor=${lastId}` : "";
        apiFetch(`/api/posts/my-posts?limit=10${cursorParam}`)
          .then((b) => {
            const raw = b?.data?.data ?? b?.data?.content ?? (Array.isArray(b?.data) ? b?.data : []);
            const mapped = raw.map((p: any) =>
              isDeptUser
                ? toPostCardPost({ ...p, variant: "government", isGovernmentBroadcast: true })
                : mapIssuePost(p)
            );
            setAllPosts((prev) => [...prev, ...mapped]);
            setAllPostsHasMore(b?.data?.hasMore ?? (mapped.length >= 10));
            setAllPostsCursor(b?.data?.nextCursor ?? (mapped.length > 0 ? mapped[mapped.length - 1]?.id : null));
          })
          .catch((err) => console.error("Failed to load more posts:", err))
          .finally(() => setLoadingMore(false));
      } else if (postFilter === "active") {
        const lastId = activePostsCursor || (activePosts.length > 0 ? activePosts[activePosts.length - 1]?.id : null);
        const cursorParam = lastId ? `&beforeId=${lastId}&lastPostId=${lastId}&cursor=${lastId}` : "";
        apiFetch(`/api/posts/my-posts/active?limit=10${cursorParam}`)
          .then((b) => {
            const raw = b?.data?.data ?? b?.data?.content ?? (Array.isArray(b?.data) ? b?.data : []);
            const mapped = raw.map(mapIssuePost);
            setActivePosts((prev) => [...prev, ...mapped]);
            setActivePostsHasMore(b?.data?.hasMore ?? (mapped.length >= 10));
            setActivePostsCursor(b?.data?.nextCursor ?? (mapped.length > 0 ? mapped[mapped.length - 1]?.id : null));
          })
          .catch((err) => console.error("Failed to load more active posts:", err))
          .finally(() => setLoadingMore(false));
      } else if (postFilter === "resolved") {
        const lastId = resolvedPostsCursor || (resolvedPosts.length > 0 ? resolvedPosts[resolvedPosts.length - 1]?.id : null);
        const cursorParam = lastId ? `&beforeId=${lastId}&lastPostId=${lastId}&cursor=${lastId}` : "";
        apiFetch(`/api/posts/my-posts/resolved?limit=10${cursorParam}`)
          .then((b) => {
            const raw = b?.data?.data ?? b?.data?.content ?? (Array.isArray(b?.data) ? b?.data : []);
            const mapped = raw.map(mapIssuePost);
            setResolvedPosts((prev) => [...prev, ...mapped]);
            setResolvedPostsHasMore(b?.data?.hasMore ?? (mapped.length >= 10));
            setResolvedPostsCursor(b?.data?.nextCursor ?? (mapped.length > 0 ? mapped[mapped.length - 1]?.id : null));
          })
          .catch((err) => console.error("Failed to load more resolved posts:", err))
          .finally(() => setLoadingMore(false));
      }
    } else if (tab === "social") {
      setLoadingMore(true);
      const lastId = socialPostsCursor || (socialPosts.length > 0 ? socialPosts[socialPosts.length - 1]?.id : null);
      const cursorParam = lastId ? `&beforeId=${lastId}&lastPostId=${lastId}&cursor=${lastId}` : "";
      apiFetch(`/api/social-posts/my-posts?limit=10${cursorParam}`)
        .then((b) => {
          const raw = b?.data?.data ?? b?.data?.content ?? (Array.isArray(b?.data) ? b?.data : []);
          const mapped = raw.map(mapSocialPost);
          setSocialPosts((prev) => [...prev, ...mapped]);
          setSocialPostsHasMore(b?.data?.hasMore ?? (mapped.length >= 10));
          setSocialPostsCursor(b?.data?.nextCursor ?? (mapped.length > 0 ? mapped[mapped.length - 1]?.id : null));
        })
        .catch((err) => console.error("Failed to load more social posts:", err))
        .finally(() => setLoadingMore(false));
    }
  }, [
    user?.id,
    tab,
    postFilter,
    allPosts,
    activePosts,
    resolvedPosts,
    socialPosts,
    allPostsCursor,
    activePostsCursor,
    resolvedPostsCursor,
    socialPostsCursor,
    loadingMore,
  ]);

  const currentTabHasMore = tab === "posts"
    ? (postFilter === "all" ? allPostsHasMore : postFilter === "active" ? activePostsHasMore : resolvedPostsHasMore)
    : (tab === "social" ? socialPostsHasMore : false);

  useEffect(() => {
    if (!currentTabHasMore || loadingMore) return;

    const scrollContainers = document.querySelectorAll<HTMLElement>("main.overflow-y-auto, .overflow-y-auto");

    let ticking = false;
    const handleScroll = () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        ticking = false;
        if (!currentTabHasMore || loadingMore) return;

        let shouldLoad = false;
        scrollContainers.forEach((container) => {
          if (container.scrollHeight > 0 && container.scrollTop + container.clientHeight >= container.scrollHeight - 800) {
            shouldLoad = true;
          }
        });

        const winScrollTop = window.scrollY || document.documentElement.scrollTop;
        const winScrollHeight = document.documentElement.scrollHeight;
        const winClientHeight = window.innerHeight;
        if (winScrollHeight > 0 && winScrollTop + winClientHeight >= winScrollHeight - 800) {
          shouldLoad = true;
        }

        if (shouldLoad) {
          loadMore();
        }
      });
    };

    scrollContainers.forEach((c) => c.addEventListener("scroll", handleScroll, { passive: true }));
    window.addEventListener("scroll", handleScroll, { passive: true });

    return () => {
      scrollContainers.forEach((c) => c.removeEventListener("scroll", handleScroll));
      window.removeEventListener("scroll", handleScroll);
    };
  }, [currentTabHasMore, loadingMore, loadMore]);

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* ═══════════════ PAGE HEADER ═══════════════ */}
      <div className="text-left">
        <h1 className="text-xl sm:text-2xl font-black text-slate-900 dark:text-white tracking-tight">
          Profile
        </h1>
        <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1 font-medium">
          Manage your personal information, local area, and activity
        </p>
      </div>

      {/* ═══════════════ PROFILE DETAILS CARD ═══════════════ */}
      <div className="rounded-2xl sm:rounded-3xl border border-black/10 dark:border-base-300 bg-base-200 p-5 sm:p-6 space-y-5 transition-all shadow-sm dark:shadow-none text-center">
        {/* Centered Avatar, Username, Email, and Meta */}
        <div className="flex flex-col items-center justify-center gap-3">
          <div className="relative group/avatar shrink-0">
            <div className="avatar">
              <div className="h-20 w-20 sm:h-24 sm:w-24 rounded-full overflow-hidden border border-black/10 dark:border-white/10 ring-4 ring-[#1D4ED8]/15 bg-base-300 shadow-md relative">
                <img 
                  src={resolveMediaUrl(user?.profileImage, "social-posts") || `https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(username)}`} 
                  alt="Profile Avatar" 
                  className="w-full h-full object-cover" 
                />
                {uploadingImg && (
                  <div className="absolute inset-0 bg-black/60 flex items-center justify-center text-white">
                    <span className="loading loading-spinner loading-sm" />
                  </div>
                )}
              </div>
            </div>
            {!uploadingImg && (
              <button 
                onClick={() => fileInputRef.current?.click()}
                className="absolute bottom-0 right-0 p-2 rounded-full bg-[#1D4ED8] hover:bg-blue-800 text-white shadow-sm border-2 border-base-200 hover:scale-105 active:scale-95 transition cursor-pointer"
                title="Change profile photo"
              >
                <Camera size={13} />
              </button>
            )}
            <input 
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleImageUpload}
              disabled={uploadingImg}
            />
          </div>

          <div className="flex flex-col items-center max-w-full px-2">
            <div className="flex items-center justify-center gap-1.5 overflow-hidden max-w-full">
              <h2 className="font-black text-lg sm:text-2xl text-slate-900 dark:text-white truncate leading-tight notranslate">{username}</h2>
              <ShieldCheck size={18} className="text-[#1D4ED8] shrink-0 stroke-[2.5]" />
            </div>

            {/* Email ID below Username */}
            {user?.email && (
              <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 font-semibold mt-0.5 truncate max-w-full select-all">
                {user.email}
              </p>
            )}

            <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 truncate max-w-full font-medium">
              <span className="notranslate">{memberSince ? `Member since ${memberSince}` : "Loading…"}</span>
              {location && (
                <>
                  <span className="mx-1.5">•</span>
                  <span className="notranslate">{location}</span>
                </>
              )}
            </p>
          </div>
        </div>

        <div className="border-t border-black/5 dark:border-white/5 my-0" />

        {/* Email */}
        <EditableRow
          icon={<Mail size={16} />}
          label="Email Address"
          value={user?.email || ""}
          placeholder="your@email.com"
          editing={editField === "email"}
          onEdit={() => setEditField("email")}
          onCancel={() => { setEditField(null); setEmail(user?.email || ""); }}
          inputValue={email}
          onInputChange={setEmail}
          type="email"
          hint="Used for account security, recovery, and official notifications"
          hideValue={true}
          rightContent={
            user?.pendingEmail ? (
              <span className="px-2.5 py-1 rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400 text-[11px] font-bold inline-flex items-center gap-1 shrink-0" title={`Pending verification for: ${user.pendingEmail}`}>
                <Clock size={11} /> Pending: {user.pendingEmail}
              </span>
            ) : user?.role === "ROLE_USER" && user?.email ? (
              user.isEmailVerified ? (
                <span className="px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-[11px] font-bold inline-flex items-center gap-1 shrink-0">
                  <Check size={12} className="stroke-[2.5]" /> Verified
                </span>
              ) : (
                <div className="flex items-center gap-2 shrink-0">
                  <span className="px-2.5 py-1 rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400 text-[11px] font-bold inline-flex items-center gap-1">
                    Unverified
                  </span>
                  <button
                    onClick={handleSendVerification}
                    disabled={sendingVerification}
                    className="text-xs text-[#1D4ED8] hover:text-blue-800 dark:text-blue-400 hover:underline font-bold focus:outline-none cursor-pointer"
                  >
                    {sendingVerification ? "Sending..." : "Verify"}
                  </button>
                </div>
              )
            ) : null
          }
        />

        <div className="border-t border-black/5 dark:border-white/5 my-0" />

        {/* Pincode / Location */}
        <div className="space-y-2">
          <EditableRow
            icon={<MapPin size={16} />}
            label="Local Neighborhood (Pincode)"
            value={user?.pincode || ""}
            placeholder="e.g. 411001"
            editing={editField === "pincode"}
            onEdit={() => setEditField("pincode")}
            onCancel={() => { setEditField(null); setPincode(user?.pincode || ""); setPincodeDetails(null); }}
            inputValue={pincode}
            onInputChange={setPincode}
            hideValue={true}
            hint={
              fetchingPincode ? (
                <span className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400 font-medium">
                  <Loader2 size={13} className="animate-spin text-[#1D4ED8]" />
                  <span>Fetching location details...</span>
                </span>
              ) : pincodeDetails ? (
                pincodeDetails === "Location not found" || pincodeDetails === "Invalid location" ? (
                  <span className="flex items-center gap-1.5 text-rose-500 font-bold">
                    <AlertCircle size={13} className="text-rose-500 shrink-0" />
                    <span>{pincodeDetails}</span>
                  </span>
                ) : (
                  <span className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400 font-bold">
                    <CheckCircle2 size={13} className="text-emerald-600 dark:text-emerald-400 shrink-0" />
                    <span>{pincodeDetails}</span>
                  </span>
                )
              ) : (
                "6-digit Indian postal code used to personalize your local area pulse & Q&A"
              )
            }
          />
          <div className="pt-1">
            <button
              className="bg-[#1D4ED8] hover:bg-blue-800 text-white text-xs font-bold px-3.5 py-1.5 rounded-xl transition-all shadow-xs flex items-center gap-1.5 active:scale-95 cursor-pointer"
              onClick={handleUpdateGps}
              disabled={updatingGps}
            >
              {updatingGps ? <Loader2 size={12} className="animate-spin" /> : <MapPin size={12} />}
              {updatingGps ? "Updating Location..." : "Update GPS Location"}
            </button>
          </div>
          {editField === "pincode" && (
            <div className="mt-2 p-3.5 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-800 dark:text-amber-300 text-xs flex items-start gap-2 font-medium">
              <span className="font-bold">Note:</span>
              <p>Updating your postal code will automatically reposition your local Area Pulse and Neighborhood feed.</p>
            </div>
          )}
          {user?.hasInvalidPincode && editField !== "pincode" && (
            <p className="text-xs text-rose-500 font-bold flex items-center gap-1.5 mt-1 bg-rose-500/10 border border-rose-500/20 p-2.5 rounded-xl">
              ⚠️ Your stored pincode is invalid. Please update it to a valid Indian pincode.
            </p>
          )}
        </div>

        {/* Save button */}
        {(editField === "email" || editField === "pincode") && (
          <div className="pt-2 flex justify-end">
            <button
              id="save-profile-btn"
              className="bg-[#1D4ED8] hover:bg-blue-800 text-white font-bold text-xs sm:text-sm px-5 py-2.5 rounded-xl shadow-xs transition-all flex items-center gap-2 active:scale-95 cursor-pointer"
              onClick={saveProfile}
              disabled={savingDetails}
            >
              {savingDetails ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} className="stroke-[2.5]" />}
              {savingDetails ? "Saving…" : "Save Changes"}
            </button>
          </div>
        )}
      </div>

      {/* ═══════════════ STATS CARDS ═══════════════ */}
      {!isDept && (
        <div className="grid grid-cols-3 gap-3 sm:gap-4">
          <StatCard value={allCountsLoaded ? issueCount : "..."} label="Issues" />
          <StatCard value={allCountsLoaded ? socialCount : "..."} label="S-Posts" />
          <StatCard value={allCountsLoaded ? communityCount : "..."} label="Communities" />
        </div>
      )}

      {isDept ? (
        <div className="space-y-4">
          <div className="flex items-center gap-2 border-b border-black/10 dark:border-base-300 pb-3">
            <h2 className="text-sm font-black uppercase tracking-wider text-slate-900 dark:text-white">Broadcasts</h2>
            <span className="bg-[#1D4ED8]/10 text-[#1D4ED8] dark:text-blue-400 border border-[#1D4ED8]/25 text-xs font-black rounded-full px-2.5 py-0.5">
              {issueCount !== null ? issueCount : "..."}
            </span>
          </div>

          {loadingPosts ? (
            <div className="space-y-4">
              {Array.from({ length: 5 }).map((_, i) => (
                <PostSkeleton key={`sk-broadcasts-${i}`} />
              ))}
            </div>
          ) : allPosts.length === 0 ? (
            <EmptyState
              title="No broadcasts yet"
              description="Official broadcasts you publish will appear here."
            />
          ) : (
            allPosts.map((p) => (
              <PostCard 
                key={p.id} 
                post={p} 
                currentUser={currentUser} 
                onDelete={(id) => handleDelete('posts', id)} 
                onResolve={handleResolve}
                onLike={(id, liked) => handleLike(id, p.variant, liked)}
                onDislike={(id, disliked) => handleDislike(id, p.variant, disliked)}
                onSave={(id, saved) => handleSave(id, p.variant, saved)}
                onShare={(id) => handleShare(id, p.variant)}
              />
            ))
          )}
        </div>
      ) : (
        <>
          {/* ═══════════════ TABS ═══════════════ */}
          <ProfileTabs 
            active={tab} 
            onChange={setTab} 
            issueCount={allCountsLoaded ? issueCount : null} 
            socialCount={allCountsLoaded ? socialCount : null} 
            activityCount={hasLoadedActivity ? activityCounts.all : null}
          />

          {/* ═══════════════ ISSUES TAB ═══════════════ */}
          {tab === "posts" && (
            <div className="space-y-4 text-left">
              {/* Mobile Issues Filter Dropdown (< sm) */}
              <div ref={issuesDropdownRef} className="sm:hidden notranslate text-left relative z-20">
                <button
                  type="button"
                  onClick={() => setIssuesFilterOpen((prev) => !prev)}
                  className="w-full bg-base-200 border border-black/10 dark:border-base-300 rounded-2xl px-4 py-2.5 text-xs font-bold text-slate-800 dark:text-slate-200 flex items-center justify-between shadow-xs outline-none focus:outline-none cursor-pointer"
                >
                  <div className="flex items-center gap-2">
                    {postFilter === "all" && <List size={14} className="text-[#1D4ED8]" />}
                    {postFilter === "active" && <Clock size={14} className="text-[#1D4ED8]" />}
                    {postFilter === "resolved" && <CheckCircle size={14} className="text-[#1D4ED8]" />}
                    <span>{postFilter === "all" ? "All Issues" : postFilter === "active" ? "Active Issues" : "Resolved Issues"}</span>
                  </div>
                  <ChevronDown
                    size={15}
                    className={`text-slate-400 transition-transform duration-200 ${
                      issuesFilterOpen ? "rotate-180 text-[#1D4ED8]" : ""
                    }`}
                  />
                </button>

                {issuesFilterOpen && (
                  <div className="absolute top-full mt-1.5 left-0 right-0 z-50 rounded-2xl border border-black/10 dark:border-base-300 bg-base-100 dark:bg-base-200 backdrop-blur-md shadow-2xl p-1.5 space-y-1 animate-in fade-in zoom-in-95 duration-150">
                    {([
                      { id: "all" as PostFilter, label: "All Issues", icon: List },
                      { id: "active" as PostFilter, label: "Active Issues", icon: Clock },
                      { id: "resolved" as PostFilter, label: "Resolved Issues", icon: CheckCircle },
                    ]).map((opt) => {
                      const isSelected = postFilter === opt.id;
                      const Icon = opt.icon;
                      return (
                        <button
                          key={opt.id}
                          type="button"
                          onClick={() => {
                            setPostFilter(opt.id);
                            setIssuesFilterOpen(false);
                          }}
                          className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer text-left outline-none focus:outline-none ${
                            isSelected
                              ? "bg-[#1D4ED8] text-white shadow-xs font-bold"
                              : "text-slate-700 dark:text-slate-200 hover:bg-base-300/60"
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            <Icon size={14} className={isSelected ? "text-white" : "text-slate-400"} />
                            <span>{opt.label}</span>
                          </div>
                          {isSelected && <Check size={14} className="stroke-[2.5]" />}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Desktop Issues Filter Tabs (>= sm) */}
              <div className="hidden sm:flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none notranslate select-none">
                {(["all", "active", "resolved"] as PostFilter[]).map((f) => {
                  const isSelected = postFilter === f;
                  return (
                    <button
                      key={f}
                      onClick={() => setPostFilter(f)}
                      className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer outline-none focus:outline-none flex items-center gap-1.5 ${
                        isSelected
                          ? "bg-[#1D4ED8] text-white shadow-xs"
                          : "bg-base-200 hover:bg-base-300/80 text-slate-600 dark:text-slate-300 border border-black/5 dark:border-white/5"
                      }`}
                    >
                      {f === "all" && <List size={12} className="stroke-[2.5]" />}
                      {f === "active" && <Clock size={12} className="stroke-[2.5]" />}
                      {f === "resolved" && <CheckCircle size={12} className="stroke-[2.5]" />}
                      <span>{f.charAt(0).toUpperCase() + f.slice(1)}</span>
                    </button>
                  );
                })}
              </div>

              {(loadingPosts || loadingFilteredPosts) ? (
                <div className="space-y-4">
                  {Array.from({ length: 5 }).map((_, i) => (
                    <PostSkeleton key={`sk-issues-${i}`} />
                  ))}
                </div>
              ) : displayedPosts.length === 0 ? (
                <EmptyState
                  title="No issues yet"
                  description="Issue posts you create will appear here."
                />
              ) : (
                <>
                  {displayedPosts.map((p) => (
                    <PostCard 
                      key={p.id} 
                      post={p} 
                      currentUser={currentUser} 
                      onDelete={(id) => handleDelete('posts', id)} 
                      onResolve={handleResolve}
                      onLike={(id, liked) => handleLike(id, p.variant, liked)}
                      onDislike={(id, disliked) => handleDislike(id, p.variant, disliked)}
                      onSave={(id, saved) => handleSave(id, p.variant, saved)}
                      onShare={(id) => handleShare(id, p.variant)}
                    />
                  ))}

                  {(postFilter === "all" ? allPostsHasMore : postFilter === "active" ? activePostsHasMore : resolvedPostsHasMore) && (
                    <div className="flex flex-col items-center justify-center pt-2 gap-2">
                      <button
                        onClick={loadMore}
                        disabled={loadingMore}
                        className="flex items-center justify-center gap-2 rounded-xl border border-black/10 dark:border-base-300 px-5 py-2.5 text-xs sm:text-sm font-bold transition bg-base-200 hover:bg-base-300 text-slate-800 dark:text-slate-200 shadow-xs active:scale-95 disabled:opacity-50 disabled:pointer-events-none cursor-pointer"
                      >
                        {loadingMore && (
                          <span className="loading loading-spinner loading-xs" />
                        )}
                        {loadingMore ? "Loading..." : "Load More"}
                      </button>
                      <InfiniteScrollTrigger onIntersect={loadMore} disabled={loadingMore} />
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {/* ═══════════════ SOCIAL POSTS TAB ═══════════════ */}
          {tab === "social" && (
            <div className="space-y-4 text-left">
              {loadingSocial ? (
                <div className="space-y-4">
                  {Array.from({ length: 5 }).map((_, i) => (
                    <PostSkeleton key={`sk-social-${i}`} />
                  ))}
                </div>
              ) : socialPosts.length === 0 ? (
                <EmptyState
                  title="No social posts yet"
                  description="Social posts you publish will appear here."
                />
              ) : (
                <>
                  {socialPosts.map((p) => (
                    <PostCard 
                      key={p.id} 
                      post={p} 
                      currentUser={currentUser} 
                      onDelete={(id) => handleDelete('social-posts', id)} 
                      onLike={(id, liked) => handleLike(id, p.variant, liked)}
                      onDislike={(id, disliked) => handleDislike(id, p.variant, disliked)}
                      onSave={(id, saved) => handleSave(id, p.variant, saved)}
                      onShare={(id) => handleShare(id, p.variant)}
                    />
                  ))}

                  {socialPostsHasMore && (
                    <div className="flex flex-col items-center justify-center pt-2 gap-2">
                      <button
                        onClick={loadMore}
                        disabled={loadingMore}
                        className="flex items-center justify-center gap-2 rounded-xl border border-black/10 dark:border-base-300 px-5 py-2.5 text-xs sm:text-sm font-bold transition bg-base-200 hover:bg-base-300 text-slate-800 dark:text-slate-200 shadow-xs active:scale-95 disabled:opacity-50 disabled:pointer-events-none cursor-pointer"
                      >
                        {loadingMore && (
                          <span className="loading loading-spinner loading-xs" />
                        )}
                        {loadingMore ? "Loading..." : "Load More"}
                      </button>
                      <InfiniteScrollTrigger onIntersect={loadMore} disabled={loadingMore} />
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {/* ═══════════════ ACTIVITY TAB ═══════════════ */}
          {tab === "activity" && (
            <div className="space-y-4 text-left">
              {/* Mobile Activity Filter Dropdown (< sm) */}
              <div ref={activityDropdownRef} className="sm:hidden notranslate text-left relative z-20">
                <button
                  type="button"
                  onClick={() => setActivityFilterOpen((prev) => !prev)}
                  className="w-full bg-base-200 border border-black/10 dark:border-base-300 rounded-2xl px-4 py-2.5 text-xs font-bold text-slate-800 dark:text-slate-200 flex items-center justify-between shadow-xs outline-none focus:outline-none cursor-pointer"
                >
                  <div className="flex items-center gap-2">
                    {activityFilter === "all" && <Clock size={14} className="text-[#1D4ED8]" />}
                    {activityFilter === "liked" && <Heart size={14} className="text-[#1D4ED8]" />}
                    {activityFilter === "saved" && <Bookmark size={14} className="text-[#1D4ED8]" />}
                    {activityFilter === "commented" && <MessageSquare size={14} className="text-[#1D4ED8]" />}
                    <span>
                      {activityFilter === "all" && `All (${activityCounts.all})`}
                      {activityFilter === "liked" && `Liked (${activityCounts.liked})`}
                      {activityFilter === "saved" && `Saved (${activityCounts.saved})`}
                      {activityFilter === "commented" && `Comments (${activityCounts.commented})`}
                    </span>
                  </div>
                  <ChevronDown
                    size={15}
                    className={`text-slate-400 transition-transform duration-200 ${
                      activityFilterOpen ? "rotate-180 text-[#1D4ED8]" : ""
                    }`}
                  />
                </button>

                {activityFilterOpen && (
                  <div className="absolute top-full mt-1.5 left-0 right-0 z-50 rounded-2xl border border-black/10 dark:border-base-300 bg-base-100 dark:bg-base-200 backdrop-blur-md shadow-2xl p-1.5 space-y-1 animate-in fade-in zoom-in-95 duration-150">
                    {([
                      { key: "all" as ActivityFilter, label: "All", icon: Clock, count: activityCounts.all },
                      { key: "liked" as ActivityFilter, label: "Liked", icon: Heart, count: activityCounts.liked },
                      { key: "saved" as ActivityFilter, label: "Saved", icon: Bookmark, count: activityCounts.saved },
                      { key: "commented" as ActivityFilter, label: "Comments", icon: MessageSquare, count: activityCounts.commented },
                    ]).map((opt) => {
                      const isSelected = activityFilter === opt.key;
                      const Icon = opt.icon;
                      return (
                        <button
                          key={opt.key}
                          type="button"
                          onClick={() => {
                            setActivityFilter(opt.key);
                            setActivityFilterOpen(false);
                          }}
                          className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer text-left outline-none focus:outline-none ${
                            isSelected
                              ? "bg-[#1D4ED8] text-white shadow-xs font-bold"
                              : "text-slate-700 dark:text-slate-200 hover:bg-base-300/60"
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            <Icon size={14} className={isSelected ? "text-white" : "text-slate-400"} />
                            <span>{opt.label}</span>
                            <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                              isSelected ? "bg-white/20 text-white" : "bg-base-300 text-slate-600 dark:text-slate-300"
                            }`}>
                              {opt.count}
                            </span>
                          </div>
                          {isSelected && <Check size={14} className="stroke-[2.5]" />}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Desktop Activity Filter Tabs (>= sm) */}
              <div className="hidden sm:flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none notranslate select-none">
                {([
                  { key: "all" as const, label: "All", icon: Clock, count: activityCounts.all },
                  { key: "liked" as const, label: "Liked", icon: Heart, count: activityCounts.liked },
                  { key: "saved" as const, label: "Saved", icon: Bookmark, count: activityCounts.saved },
                  { key: "commented" as const, label: "Comments", icon: MessageSquare, count: activityCounts.commented },
                ]).map(({ key, label, icon: Icon, count }) => {
                  const isSelected = activityFilter === key;
                  return (
                    <button
                      key={key}
                      onClick={() => setActivityFilter(key)}
                      className={`px-4 py-2 rounded-2xl text-xs sm:text-sm font-bold transition-all shrink-0 cursor-pointer outline-none focus:outline-none flex items-center gap-2 ${
                        isSelected
                          ? "bg-[#1D4ED8] text-white shadow-xs"
                          : "bg-base-200 dark:bg-zinc-900/90 hover:bg-base-300 dark:hover:bg-zinc-800 text-slate-700 dark:text-slate-200 border border-black/10 dark:border-white/10"
                      }`}
                    >
                      <Icon size={14} className={isSelected ? "stroke-[2.5]" : "stroke-2 text-slate-400 dark:text-zinc-400"} />
                      <span>{label}</span>
                      <span
                        className={`px-2 py-0.5 rounded-full text-xs font-bold ${
                          isSelected
                            ? "bg-white/20 text-white"
                            : "bg-base-300 dark:bg-zinc-800 text-slate-600 dark:text-zinc-300"
                        }`}
                      >
                        {count}
                      </span>
                    </button>
                  );
                })}
              </div>

              {loadingActivity ? (
                <div className="space-y-4">
                  {Array.from({ length: 5 }).map((_, i) => (
                    <PostSkeleton key={`sk-activity-${i}`} />
                  ))}
                </div>
              ) : displayedActivity.length === 0 ? (
                <EmptyState
                  title={
                    activityFilter === "all" ? "No activity yet" :
                    activityFilter === "liked" ? "No liked posts yet" :
                    activityFilter === "saved" ? "No saved posts yet" :
                    "No commented posts yet"
                  }
                  description={
                    activityFilter === "all" ? "Posts you interact with will appear here." :
                    activityFilter === "liked" ? "Posts you like will appear here." :
                    activityFilter === "saved" ? "Posts you save will appear here." :
                    "Posts you comment on will appear here."
                  }
                />
              ) : (
                <div className="space-y-6">
                  {displayedActivity.map((item) => {
                    const isLiked = item.type === "liked";
                    const isSaved = item.type === "saved";
                    const isCommented = item.type === "commented";

                    return (
                      <div key={item.id} className="space-y-2">
                        {/* Activity Context Header */}
                        <div className="flex items-center justify-between px-1 text-xs">
                          <div className="flex items-center gap-1.5 font-bold">
                            {isLiked && (
                              <span className="flex items-center gap-1.5 text-rose-500">
                                <Heart size={14} className="fill-rose-500 text-rose-500 shrink-0" />
                                <span>{activityFilter === "all" ? "Liked" : "You liked this"}</span>
                              </span>
                            )}
                            {isSaved && (
                              <span className="flex items-center gap-1.5 text-amber-500">
                                <Bookmark size={14} className="fill-amber-500 text-amber-500 shrink-0" />
                                <span>You saved this</span>
                              </span>
                            )}
                            {isCommented && (
                              <span className="flex items-center gap-1.5 text-blue-500">
                                <MessageSquare size={14} className="fill-blue-500 text-blue-500 shrink-0" />
                                <span>You commented</span>
                              </span>
                            )}
                          </div>
                          <span className="text-xs text-slate-400 dark:text-zinc-500 font-medium">
                            {formatRelativeTime(item.time)}
                          </span>
                        </div>

                        {/* Comment Content Preview (if commented) */}
                        {isCommented && item.commentText && (
                          <div className="w-full rounded-2xl bg-base-200/50 dark:bg-zinc-900/60 border border-black/5 dark:border-white/10 px-4 py-3 flex items-start gap-3">
                            <div className="w-1 self-stretch rounded-full bg-[#1D4ED8] shrink-0 min-h-[1.25rem]" />
                            <p className="text-xs sm:text-sm font-semibold text-slate-800 dark:text-slate-200 break-words leading-relaxed">
                              “{item.commentText}”
                            </p>
                          </div>
                        )}

                        {/* Post Card */}
                        <PostCard
                          post={item.post}
                          currentUser={currentUser}
                          hideDelete={true}
                          onResolve={handleResolve}
                          onLike={(id, liked) => handleLike(id, item.post.variant, liked)}
                          onDislike={(id, disliked) => handleDislike(id, item.post.variant, disliked)}
                          onSave={(id, saved) => handleSave(id, item.post.variant, saved)}
                          onShare={(id) => handleShare(id, item.post.variant)}
                        />
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </>
      )}

      {/* ═══════════════ VERIFICATION EMAIL MODAL ═══════════════ */}
      {showVerificationModal && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-fade-in">
          <div className="w-full max-w-sm rounded-3xl bg-base-200 border border-black/10 dark:border-white/15 p-6 space-y-4 shadow-2xl text-left">
            <div className="flex items-center gap-2.5 text-[#1D4ED8] dark:text-blue-400">
              <Mail size={22} className="stroke-[2.5]" />
              <h3 className="font-bold text-base text-slate-900 dark:text-white">Verify Your Email</h3>
            </div>
            <div className="text-xs text-slate-600 dark:text-slate-300 space-y-2 leading-relaxed">
              <p>We've sent a verification link to your registered email address:</p>
              <p className="font-bold text-center py-2 px-3 bg-base-100 rounded-xl text-xs text-slate-800 dark:text-slate-200 break-all border border-black/5 dark:border-white/5">{user?.email}</p>
              <p className="text-[11px] text-slate-400">Note: Please check your Spam or Junk folder if you do not receive the email within 2 minutes.</p>
            </div>
            <div className="flex justify-end pt-2">
              <button
                className="bg-[#1D4ED8] hover:bg-blue-800 text-white font-bold text-xs px-4 py-2 rounded-xl shadow-xs transition-all active:scale-95 cursor-pointer"
                onClick={() => setShowVerificationModal(false)}
              >
                Okay, I'll Check
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Image Editor Modal */}
      {editorOpen && editorImageSrc && (
        <ImageEditorModal
          isOpen={editorOpen}
          imageSrc={editorImageSrc}
          onSave={handleEditorSave}
          onClose={() => {
            setEditorOpen(false);
            setEditorImageSrc(null);
            if (fileInputRef.current) fileInputRef.current.value = "";
          }}
        />
      )}
    </div>
  );
};

export default Profile;
