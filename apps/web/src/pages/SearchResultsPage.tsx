import { useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import { useCurrentUser } from "../hooks/useUser";
import { useSearchInfinite } from "../api/searchService";
import { useQueryClient } from "@tanstack/react-query";
import InfiniteScrollList from "../components/search/InfiniteScrollList";
import { Search } from "lucide-react";

const SEARCH_TABS = [
  { key: "ALL", label: "All" },
  { key: "POST", label: "Issues" },
  { key: "SOCIAL_POST", label: "Social" },
  { key: "COMMUNITY", label: "Communities" },
];

export default function SearchResultsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get("q") || "";
  const activeTab = searchParams.get("tab") || "ALL";
  const queryClient = useQueryClient();

  const updateSearchQueryCache = useCallback((postId: number, updater: (postDto: any) => any) => {
    queryClient.setQueryData(["search", "infinite", query, activeTab], (oldData: any) => {
      if (!oldData) return oldData;
      return {
        ...oldData,
        pages: oldData.pages.map((page: any) => {
          const items = page?.data || [];
          const updatedItems = items.map((r: any) => {
            if ((r.kind === "POST" || r.kind === "SOCIAL_POST") && (r.id === postId || r.postDto?.id === postId || r.postDto?.socialPostId === postId)) {
              return {
                ...r,
                postDto: updater(r.postDto)
              };
            }
            return r;
          });
          return {
            ...page,
            data: updatedItems
          };
        })
      };
    });
  }, [queryClient, query, activeTab]);

  const handleLike = useCallback((postId: number, liked: boolean) => {
    updateSearchQueryCache(postId, (dto: any) => {
      if (!dto) return dto;
      const isLiked = !!(dto.isLikedByCurrentUser ?? dto.isLikedByMe);
      if (isLiked === liked) return dto;
      const isPreviouslyDisliked = !!(dto.isDislikedByCurrentUser ?? dto.isDislikedByMe);
      return {
        ...dto,
        isLikedByMe: liked,
        isLikedByCurrentUser: liked,
        likeCount: (dto.likeCount ?? 0) + (liked ? 1 : -1),
        ...(isPreviouslyDisliked && liked && {
          isDislikedByCurrentUser: false,
          isDislikedByMe: false,
          dislikeCount: Math.max(0, (dto.dislikeCount ?? 0) - 1)
        })
      };
    });
  }, [updateSearchQueryCache]);

  const handleDislike = useCallback((postId: number, disliked: boolean) => {
    updateSearchQueryCache(postId, (dto: any) => {
      if (!dto) return dto;
      const isDisliked = !!(dto.isDislikedByCurrentUser ?? dto.isDislikedByMe);
      if (isDisliked === disliked) return dto;
      const isPreviouslyLiked = !!(dto.isLikedByCurrentUser ?? dto.isLikedByMe);
      return {
        ...dto,
        isDislikedByCurrentUser: disliked,
        isDislikedByMe: disliked,
        dislikeCount: (dto.dislikeCount ?? 0) + (disliked ? 1 : -1),
        ...(isPreviouslyLiked && disliked && {
          isLikedByCurrentUser: false,
          isLikedByMe: false,
          likeCount: Math.max(0, (dto.likeCount ?? 0) - 1)
        })
      };
    });
  }, [updateSearchQueryCache]);

  const handleSave = useCallback((postId: number, saved: boolean) => {
    updateSearchQueryCache(postId, (dto: any) => {
      if (!dto) return dto;
      const isSaved = !!(dto.isSavedByCurrentUser ?? dto.isSavedByMe ?? dto.isSaved ?? false);
      if (isSaved === saved) return dto;
      return {
        ...dto,
        isSavedByMe: saved,
        isSavedByCurrentUser: saved,
        isSaved: saved
      };
    });
  }, [updateSearchQueryCache]);

  const handleShare = useCallback((postId: number) => {
    updateSearchQueryCache(postId, (dto: any) => {
      if (!dto) return dto;
      return {
        ...dto,
        shareCount: (dto.shareCount ?? 0) + 1
      };
    });
  }, [updateSearchQueryCache]);

  const { data: user } = useCurrentUser();
  const currentUser = user
    ? {
        id: user.id,
        username: user.actualUsername || user.username,
        role: user.role,
      }
    : undefined;

  const {
    data,
    fetchNextPage,
    hasNextPage,
    isLoading,
    isFetchingNextPage,
    error,
  } = useSearchInfinite(query, activeTab);

  // Flatten page arrays
  const results = data?.pages
    ? data.pages.flatMap((page: any) => {
        // If it's a filtered request returning PaginatedResponse
        // or a unified request returning SearchResponse, items are in .data
        return page?.data || [];
      })
    : [];

  const handleTabChange = (tabKey: string) => {
    setSearchParams({ q: query, tab: tabKey });
  };

  return (
    <div className="space-y-4 max-w-4xl mx-auto px-2 py-4">
      {/* Search Header */}
      <div className="flex items-center gap-3 rounded-2xl border border-base-300 bg-base-100/90 p-4 backdrop-blur-md shadow-sm">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10">
          <Search className="text-primary" size={20} />
        </div>
        <div>
          <h1 className="text-lg font-bold text-base-content">Search Results</h1>
          {query ? (
            <p className="text-xs opacity-60 mt-0.5">
              Showing results for "<span className="font-semibold text-primary">{query}</span>"
            </p>
          ) : (
            <p className="text-xs opacity-50 mt-0.5">Enter a search query in the search bar</p>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-base-100 p-1.5 rounded-2xl border border-base-300 shadow-sm overflow-x-auto scrollbar-hide">
        {SEARCH_TABS.map((tab) => (
          <button
            key={tab.key}
            onClick={() => handleTabChange(tab.key)}
            className={`shrink-0 rounded-xl px-5 py-2 text-xs font-bold transition-all whitespace-nowrap cursor-pointer ${
              activeTab === tab.key
                ? "bg-[#1D4ED8] text-white shadow-sm"
                : "text-base-content/70 hover:text-base-content hover:bg-base-200/50"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Results Content */}
      <div className="rounded-2xl border border-base-300 bg-base-100/95 p-4 shadow-sm min-h-[400px]">
        {query ? (
          <InfiniteScrollList
            results={results}
            hasMore={!!hasNextPage}
            loading={isLoading || isFetchingNextPage}
            fetchNextPage={fetchNextPage}
            currentUser={currentUser}
            error={error ? error.message : null}
            onLike={handleLike}
            onDislike={handleDislike}
            onSave={handleSave}
            onShare={handleShare}
          />
        ) : (
          <div className="flex flex-col items-center justify-center py-20 opacity-40">
            <Search size={40} className="mb-3" />
            <p className="text-sm">Search for posts, communities or hashtags</p>
          </div>
        )}
      </div>
    </div>
  );
}
