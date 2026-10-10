import { useRef, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Hash, Loader2 } from "lucide-react";
import PostCard from "../post/PostCard";
import CommunityCard from "../community/CommunityCard";
import { toPostCardPost } from "../../utils/postUtils";
import type { SearchResult } from "../../api/searchService";

interface InfiniteScrollListProps {
  results: SearchResult[];
  hasMore: boolean;
  loading: boolean;
  fetchNextPage: () => void;
  currentUser: any;
  error?: string | null;
  onLike?: (postId: number, liked: boolean) => void;
  onDislike?: (postId: number, disliked: boolean) => void;
  onSave?: (postId: number, saved: boolean) => void;
  onShare?: (postId: number) => void;
}

// ─── Result type normalizer ──────────────────────────────────────────────────
export type ResultKind = "POST" | "SOCIAL_POST" | "COMMUNITY" | "HASHTAG" | "UNKNOWN";

export interface NormResult {
  kind: ResultKind;
  id?: number;
  postDto?: Record<string, unknown>;
  communityName?: string;
  communitySlug?: string;
  communityAvatarUrl?: string;
  communityDescription?: string;
  memberCount?: number;
  locationName?: string;
  hashtag?: string;
  postCount?: number;
  privacy?: string;
}

function normalise(r: SearchResult): NormResult {
  const kind: ResultKind =
    (r.resultType ?? "UNKNOWN").toString().toUpperCase() as ResultKind;

  const base: NormResult = { kind, id: r.id };

  if (kind === "POST" || kind === "SOCIAL_POST") {
    const dto = r.post ?? r.socialPost ?? null;
    return { ...base, postDto: dto ?? undefined };
  }

  if (kind === "COMMUNITY") {
    return {
      ...base,
      communityName: r.communityName,
      communitySlug: r.communitySlug,
      communityAvatarUrl: r.communityAvatarUrl,
      communityDescription: r.communityDescription,
      memberCount: r.memberCount,
      locationName: r.locationName,
      privacy: r.privacy,
    };
  }

  if (kind === "HASHTAG") {
    return {
      ...base,
      hashtag: (r.hashtag ?? "").toString().replace(/^#+/, ""),
      postCount: r.postCount,
    };
  }

  return base;
}

// ─── Scroll Sentinel Component ────────────────────────────────────────────────
function ScrollSentinel({ onIntersect }: { onIntersect: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const cb = useRef(onIntersect);
  
  useEffect(() => {
    cb.current = onIntersect;
  }, [onIntersect]);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const obs = new IntersectionObserver(
      ([e]) => {
        if (e.isIntersecting) cb.current();
      },
      { threshold: 0.1, rootMargin: "0px 0px 300px 0px" }
    );
    obs.observe(el);
    return () => obs.disconnect();
  }, []);

  return <div ref={ref} className="h-2" />;
}

// ─── Community Card ───────────────────────────────────────────────────────────
function CommunityResultCard({ r }: { r: NormResult }) {
  const navigate = useNavigate();

  const handleClick = () => {
    const slug = r.communitySlug || String(r.id || "");
    navigate(`/communities/${slug}`, {
      state: {
        selectedCommunity: {
          id: r.id || 0,
          name: r.communityName || "Unnamed",
          slug,
          description: r.communityDescription || "",
          category: null,
          tags: null,
          avatarUrl: r.communityAvatarUrl || null,
          coverImageUrl: null,
          privacy: (r.privacy || "PUBLIC") as any,
          locationName: r.locationName || null,
          memberCount: r.memberCount || 0,
          postCount: 0,
          isMember: false,
          isOwner: false,
          createdAt: new Date().toISOString(),
        },
      },
    });
  };

  return (
    <CommunityCard
      id={r.id || 0}
      slug={r.communitySlug}
      name={r.communityName || "Unnamed"}
      description={r.communityDescription || ""}
      members={r.memberCount || 0}
      avatarUrl={r.communityAvatarUrl}
      privacy={r.privacy || "PUBLIC"}
      onClick={handleClick}
    />
  );
}

// ─── Hashtag Card ─────────────────────────────────────────────────────────────
function HashtagResultCard({ r }: { r: NormResult }) {
  const navigate = useNavigate();
  return (
    <button
      onClick={() => navigate("/communities", { state: { searchQuery: r.hashtag ?? "" } })}
      className="flex w-full items-center gap-3 rounded-xl border border-base-300 bg-base-100 p-3 hover:bg-base-200 transition-colors text-left shadow-sm"
    >
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#1D4ED8]">
        <Hash size={18} className="text-white" />
      </div>
      <div>
        <p className="font-semibold text-sm">#{r.hashtag}</p>
        {r.postCount != null && (
          <p className="text-xs opacity-50 mt-0.5">{Number(r.postCount).toLocaleString()} posts</p>
        )}
      </div>
    </button>
  );
}

// ─── Main InfiniteScrollList Component ────────────────────────────────────────
export default function InfiniteScrollList({
  results,
  hasMore,
  loading,
  fetchNextPage,
  currentUser,
  error = null,
  onLike,
  onDislike,
  onSave,
  onShare,
}: InfiniteScrollListProps) {
  const navigate = useNavigate();

  if (results.length === 0 && !loading && !error) {
    return (
      <div className="flex flex-col items-center justify-center py-16 opacity-40">
        <p className="text-sm">No results found</p>
      </div>
    );
  }

  return (
    <div className="space-y-4 pb-12">
      {error && (
        <div className="alert alert-error text-sm rounded-xl">
          <span>{error}</span>
        </div>
      )}

      {results.map((item, index) => {
        const norm = normalise(item);

        if (norm.kind === "POST" || norm.kind === "SOCIAL_POST") {
          if (!norm.postDto) return null;
          const post = toPostCardPost(norm.postDto);
          const postId = norm.id ?? post.id;

          const isPoll = post.variant === "poll" || (post as any).isPoll === true;
          const isMedia = (post as any).hasMedia === true || ((post as any).mediaUrls && (post as any).mediaUrls.length > 0) || (post as any).hasImage === true;
          const isGov = (post as any).isGovernmentBroadcast === true || post.variant === "government";

          return (
            <div
              key={`${norm.kind}-${norm.id ?? index}`}
              className="cursor-pointer group"
              onClick={() => {
                if (postId) navigate(`/post/${postId}`);
              }}
            >
              {/* Format Badges */}
              {(isPoll || isMedia || isGov) && (
                <div className="flex gap-1.5 mb-2 px-1 flex-wrap">
                  {isPoll && (
                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-pink-500/10 text-pink-500 border border-pink-500/20 shadow-sm">
                      📊 Interactive Poll
                    </span>
                  )}
                  {isMedia && (
                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/10 text-amber-600 dark:text-amber-500 border border-amber-500/20 shadow-sm">
                      🖼️ Contains Media
                    </span>
                  )}
                  {isGov && (
                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-blue-500/10 text-blue-500 border border-blue-500/20 shadow-sm">
                      📢 Official Broadcast
                    </span>
                  )}
                </div>
              )}

              <PostCard
                post={post}
                currentUser={currentUser}
                onLike={onLike}
                onDislike={onDislike}
                onSave={onSave}
                onShare={onShare}
                onComment={(id: number) => navigate(`/post/${id}`)}
              />
            </div>
          );
        }

        if (norm.kind === "COMMUNITY") {
          return <CommunityResultCard key={`comm-${norm.id ?? index}`} r={norm} />;
        }

        if (norm.kind === "HASHTAG") {
          return <HashtagResultCard key={`hash-${norm.id ?? index}`} r={norm} />;
        }

        return null;
      })}

      {loading && (
        <div className="flex justify-center py-6">
          <Loader2 size={24} className="animate-spin text-primary opacity-60" />
        </div>
      )}

      {!loading && hasMore && <ScrollSentinel onIntersect={fetchNextPage} />}

      {!hasMore && results.length > 0 && (
        <p className="py-6 text-center text-xs opacity-35">End of results</p>
      )}
    </div>
  );
}
