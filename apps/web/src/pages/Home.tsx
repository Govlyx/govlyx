import { useState, useEffect, useCallback, useRef } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { Flame, Clock, ArrowUp, SlidersHorizontal, Sparkles, ChevronDown, MapPin, Loader2, Info, AlertTriangle } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import PostCard from "../components/post/PostCard";
import type { AnyPost } from "../components/post/PostCard";
import EmptyState from "../components/ui/EmptyState";
import LoadingAnimation from "../components/ui/LoadingAnimation";
import axiosInstance from "../api/axiosConfig";
import axios from "axios";
import { parseError } from "../utils/error-handler";

import { useCurrentUser } from "../hooks/useUser";
import { toPostCardPost } from "../utils/postUtils";
import { showToast } from "../utils/toast";
import { fetchPincodeFromCoordinates } from "../utils/geocoding";
import { Helmet } from "react-helmet-async";
import NewPostsBanner from "../components/NewPostsBanner";
import { useFeedRefresh } from "../hooks/useFeedRefresh";
import PullToRefresh from "../components/ui/PullToRefresh";

const FEED_SIZE = 15;

function useFeed(sourceTab: string, sortTab: string, qaScope: "NEARBY" | "AREA" | "CITY" | "DISTRICT") {
  const queryClient = useQueryClient();
  const { data: user } = useCurrentUser();
  const queryKey = [
    "feed",
    sourceTab,
    sortTab,
    sourceTab === "location" ? user?.pincode : undefined,
    sourceTab === "neighborhood_qa" ? qaScope : undefined,
  ];

  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetching,
    isFetchingNextPage,
    isLoading: initialLoading,
    isError,
    error: queryError,
    refetch,
  } = useInfiniteQuery({
    queryKey,
    queryFn: async ({ pageParam = null }: { pageParam?: number | null }) => {
      const isLocalOrOfficial = sourceTab === "location" || sourceTab === "official";
      const params: any = {
        sort: sortTab.toUpperCase(),
        limit: FEED_SIZE,
        size: FEED_SIZE,
      };

      if (pageParam !== null && pageParam !== undefined) {
        if (isLocalOrOfficial) {
          params.beforeId = pageParam;
        } else {
          params.lastPostId = pageParam;
          params.beforeId = pageParam;
        }
      }

      let endpoint = "/api/v1/feed/for-you";
      if (sourceTab === "location") {
        endpoint = "/api/v1/feed/local";
        if (user?.pincode) {
          params.pincode = user.pincode;
          params.targetPincode = user.pincode;
        }
      } else if (sourceTab === "following") {
        endpoint = "/api/v1/feed/following";
      } else if (sourceTab === "official") {
        endpoint = "/api/v1/feed/official";
      } else if (sourceTab === "neighborhood_qa") {
        endpoint = "/api/v1/feed/neighborhood-qa";
        params.scope = qaScope;
        params.sort = sortTab === "top" ? "TOP" : "NEW";
      }

      const res = await axiosInstance.get(endpoint, { params });
      const json = res.data;

      const isWrapped = json.success !== undefined && json.data !== undefined;
      const container = isWrapped ? json.data : json;

      let items: any[] = [];
      if (Array.isArray(container)) {
        items = container;
      } else if (container && typeof container === "object") {
        const candidate =
          container.content ??
          container.data ??
          container.posts ??
          container.items ??
          container.feed ??
          [];
        items = Array.isArray(candidate) ? candidate : [];
      }

      // If pageParam is null (first page) and we got very few posts (e.g. 1 post from HOT sort because time window is narrow),
      // seamlessly enrich from NEW sort so the user doesn't hit an immediate premature dead-end.
      if (!pageParam && items.length < 5 && sortTab === "hot" && (sourceTab === "all" || sourceTab === "location")) {
        try {
          const enrichParams: any = {
            ...params,
            sort: "NEW",
          };
          const enrichRes = await axiosInstance.get(endpoint, { params: enrichParams });
          const enrichJson = enrichRes.data;
          const enrichContainer = enrichJson.success !== undefined && enrichJson.data !== undefined ? enrichJson.data : enrichJson;
          const enrichItems = Array.isArray(enrichContainer)
            ? enrichContainer
            : (enrichContainer?.content ?? enrichContainer?.data ?? enrichContainer?.posts ?? enrichContainer?.items ?? []);
          
          if (Array.isArray(enrichItems) && enrichItems.length > 0) {
            const existingIds = new Set(items.map((it: any) => it.id));
            enrichItems.forEach((it: any) => {
              if (!existingIds.has(it.id)) {
                items.push(it);
                existingIds.add(it.id);
              }
            });
          }
        } catch {
          // non-fatal enrichment fallback
        }
      }

      const mapped = items.map(toPostCardPost);

      // Determine next cursor: support explicit nextCursor, lastId, or fallback to the last item's id
      const resolvedNextCursor =
        container?.nextCursor ??
        container?.nextPage ??
        container?.lastPostId ??
        container?.lastId ??
        (mapped.length > 0 ? mapped[mapped.length - 1]?.id : (items.length > 0 ? items[items.length - 1]?.id : null));

      // Determine hasMore:
      // 1. Explicit boolean on container (hasMore, hasNext, !last)
      // 2. Or if nextCursor is explicitly returned by server and not null
      // 3. Or if totalPages / number indicates more pages
      // 4. Fallback: true if returned items reached FEED_SIZE or if next cursor exists
      let resolvedHasMore = false;
      if (typeof container?.hasMore === "boolean") {
        resolvedHasMore = container.hasMore;
      } else if (typeof container?.hasNext === "boolean") {
        resolvedHasMore = container.hasNext;
      } else if (typeof container?.last === "boolean") {
        resolvedHasMore = !container.last;
      } else if (typeof container?.data?.hasMore === "boolean") {
        resolvedHasMore = container.data.hasMore;
      } else if (typeof container?.data?.hasNext === "boolean") {
        resolvedHasMore = container.data.hasNext;
      } else if (typeof container?.data?.last === "boolean") {
        resolvedHasMore = !container.data.last;
      } else if (container?.totalPages !== undefined && container?.number !== undefined) {
        resolvedHasMore = container.number + 1 < container.totalPages;
      } else if (container?.nextCursor !== undefined && container?.nextCursor !== null) {
        resolvedHasMore = true;
      } else if (typeof container?.totalElements === "number") {
        resolvedHasMore = items.length > 0 && container.totalElements > items.length;
      } else {
        resolvedHasMore = items.length >= FEED_SIZE;
      }

      // If we enriched the initial page with NEW posts and there are items, ensure user can scroll if more exist
      if (!pageParam && items.length >= 5 && resolvedNextCursor) {
        resolvedHasMore = true;
      }

      return {
        posts: mapped,
        hasMore: resolvedHasMore,
        nextCursor: resolvedNextCursor,
        isFallback: container?.fallback ?? container?.districtFallback ?? container?.isFallback ?? false,
      };
    },
    getNextPageParam: (lastPage, allPages) => {
      if (!lastPage.hasMore) return undefined;
      if (lastPage.nextCursor !== undefined && lastPage.nextCursor !== null) {
        return lastPage.nextCursor;
      }
      return allPages.length;
    },
    initialPageParam: null,
    placeholderData: (prev) => prev,
  });

  const map = new Map<string, AnyPost>();
  if (data?.pages) {
    data.pages.forEach((page) => {
      page.posts.forEach((item: AnyPost) => {
        map.set(item.id + "-" + item.variant, item);
      });
    });
  }
  const posts = Array.from(map.values());

  const loading = isFetching;
  const hasMore = hasNextPage;
  const error = isError ? parseError(queryError) : null;
  const fatalError =
    isError &&
    axios.isAxiosError(queryError) &&
    (queryError.response?.status === 401 || queryError.response?.status === 403);

  const loadMore = useCallback(() => {
    if (!isFetchingNextPage && hasNextPage) fetchNextPage();
  }, [isFetchingNextPage, hasNextPage, fetchNextPage]);

  const retry = useCallback(() => {
    refetch();
  }, [refetch]);

  const updatePost = useCallback((postId: number, updater: Partial<AnyPost> | ((p: AnyPost) => Partial<AnyPost>)) => {
    queryClient.setQueryData(queryKey, (oldData: any) => {
      if (!oldData) return oldData;
      return {
        ...oldData,
        pages: oldData.pages.map((page: any) => ({
          ...page,
          posts: page.posts.map((p: AnyPost) => {
            if (p.id !== postId) return p;
            const changes = typeof updater === "function" ? updater(p) : updater;
            return { ...p, ...changes };
          })
        }))
      };
    });
  }, [queryClient, queryKey]);

  const prependPost = useCallback((rawPost: any) => {
    const mapped = toPostCardPost(rawPost);
    queryClient.setQueryData(queryKey, (oldData: any) => {
      if (!oldData) {
        return {
          pages: [{ posts: [mapped], hasMore: false, nextCursor: null, isFallback: false }],
          pageParams: [null]
        };
      }
      const firstPage = oldData.pages[0];
      return {
        ...oldData,
        pages: [
          { ...firstPage, posts: [mapped, ...firstPage.posts] },
          ...oldData.pages.slice(1)
        ]
      };
    });
  }, [queryClient, queryKey]);

  const deletePost = useCallback((postId: number) => {
    queryClient.setQueryData(queryKey, (oldData: any) => {
      if (!oldData) return oldData;
      return {
        ...oldData,
        pages: oldData.pages.map((page: any) => ({
          ...page,
          posts: page.posts.filter((p: AnyPost) => p.id !== postId)
        }))
      };
    });
  }, [queryClient, queryKey]);

  const isFallback = !!data?.pages?.[0]?.isFallback;

  const refreshFeed = useCallback(async () => {
    try {
      const mainEl = document.querySelector("main.overflow-y-auto");
      if (mainEl) {
        mainEl.scrollTo({ top: 0, behavior: "smooth" });
      } else {
        window.scrollTo({ top: 0, behavior: "smooth" });
      }

      const params: any = {
        sort: sortTab.toUpperCase(),
        limit: FEED_SIZE,
        size: FEED_SIZE,
      };

      let endpoint = "/api/v1/feed/for-you";
      if (sourceTab === "location") {
        endpoint = "/api/v1/feed/local";
        if (user?.pincode) {
          params.pincode = user.pincode;
          params.targetPincode = user.pincode;
        }
      } else if (sourceTab === "following") {
        endpoint = "/api/v1/feed/following";
      } else if (sourceTab === "official") {
        endpoint = "/api/v1/feed/official";
      } else if (sourceTab === "neighborhood_qa") {
        endpoint = "/api/v1/feed/neighborhood-qa";
        params.scope = qaScope;
        params.sort = sortTab === "top" ? "TOP" : "NEW";
      }

      const res = await axiosInstance.get(endpoint, { params });
      const json = res.data;

      const isWrapped = json.success !== undefined && json.data !== undefined;
      const container = isWrapped ? json.data : json;

      let items: any[] = [];
      if (Array.isArray(container)) {
        items = container;
      } else if (container && typeof container === "object") {
        const candidate =
          container.content ??
          container.data ??
          container.posts ??
          container.items ??
          container.feed ??
          [];
        items = Array.isArray(candidate) ? candidate : [];
      }

      // If we got very few posts (e.g. 1 post from HOT sort because time window is narrow),
      // seamlessly enrich from NEW sort so the user doesn't hit an immediate premature dead-end.
      if (items.length < 5 && sortTab === "hot" && (sourceTab === "all" || sourceTab === "location")) {
        try {
          const enrichParams: any = {
            ...params,
            sort: "NEW",
          };
          const enrichRes = await axiosInstance.get(endpoint, { params: enrichParams });
          const enrichJson = enrichRes.data;
          const enrichContainer = enrichJson.success !== undefined && enrichJson.data !== undefined ? enrichJson.data : enrichJson;
          const enrichItems = Array.isArray(enrichContainer)
            ? enrichContainer
            : (enrichContainer?.content ?? enrichContainer?.data ?? enrichContainer?.posts ?? enrichContainer?.items ?? []);
          
          if (Array.isArray(enrichItems) && enrichItems.length > 0) {
            const existingIds = new Set(items.map((it: any) => it.id));
            enrichItems.forEach((it: any) => {
              if (!existingIds.has(it.id)) {
                items.push(it);
                existingIds.add(it.id);
              }
            });
          }
        } catch {
          // non-fatal enrichment fallback
        }
      }

      const mapped = items.map(toPostCardPost);

      const resolvedNextCursor =
        container?.nextCursor ??
        container?.nextPage ??
        container?.lastPostId ??
        container?.lastId ??
        (mapped.length > 0 ? mapped[mapped.length - 1]?.id : (items.length > 0 ? items[items.length - 1]?.id : null));

      let resolvedHasMore = false;
      if (typeof container?.hasMore === "boolean") {
        resolvedHasMore = container.hasMore;
      } else if (typeof container?.hasNext === "boolean") {
        resolvedHasMore = container.hasNext;
      } else if (typeof container?.last === "boolean") {
        resolvedHasMore = !container.last;
      } else if (typeof container?.data?.hasMore === "boolean") {
        resolvedHasMore = container.data.hasMore;
      } else if (typeof container?.data?.hasNext === "boolean") {
        resolvedHasMore = container.data.hasNext;
      } else if (typeof container?.data?.last === "boolean") {
        resolvedHasMore = !container.data.last;
      } else if (container?.totalPages !== undefined && container?.number !== undefined) {
        resolvedHasMore = container.number + 1 < container.totalPages;
      } else if (container?.nextCursor !== undefined && container?.nextCursor !== null) {
        resolvedHasMore = true;
      } else if (typeof container?.totalElements === "number") {
        resolvedHasMore = items.length > 0 && container.totalElements > items.length;
      } else {
        resolvedHasMore = items.length >= FEED_SIZE;
      }

      if (items.length >= 5 && resolvedNextCursor) {
        resolvedHasMore = true;
      }

      const newPage1 = {
        posts: mapped,
        hasMore: resolvedHasMore,
        nextCursor: resolvedNextCursor,
        isFallback: container?.fallback ?? container?.districtFallback ?? container?.isFallback ?? false,
      };

      queryClient.setQueryData(queryKey, {
        pages: [newPage1],
        pageParams: [null],
      });
    } catch (err) {
      console.error("Failed to refresh feed:", err);
      refetch();
    }
  }, [sourceTab, sortTab, qaScope, user?.pincode, queryClient, queryKey, refetch]);

  return { posts, loading, initialLoading, hasMore, error, fatalError, loadMore, retry, updatePost, prependPost, deletePost, isFallback, refetch, refreshFeed, isFetchingNextPage };
}

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

    // Initial check in case user is already scrolled to bottom area
    const rect = el.getBoundingClientRect();
    const parentBottom = scrollParent ? scrollParent.getBoundingClientRect().bottom : window.innerHeight;
    if (rect.top <= parentBottom + 600) {
      cbRef.current();
    }

    return () => observer.disconnect();
  }, [disabled]);

  return <div ref={ref} className="h-8 w-full pointer-events-none" aria-hidden="true" />;
}

const SOURCE_TABS: { key: "all" | "location" | "following" | "official" | "neighborhood_qa"; label: string }[] = [
  { key: "all", label: "For You" },
  { key: "location", label: "Location" },
  { key: "following", label: "Following" },
  { key: "official", label: "Official" },
  { key: "neighborhood_qa", label: "Neighborhood Q&A" },
];

const SORT_TABS: { key: "hot" | "new" | "top"; label: string; icon: any }[] = [
  { key: "hot", label: "Hot", icon: Flame },
  { key: "new", label: "New", icon: Clock },
  { key: "top", label: "Top", icon: ArrowUp },
];

const Home = () => {
  const [sourceTab, setSourceTab] = useState<"all" | "location" | "following" | "official" | "neighborhood_qa">(() => {
    const saved = sessionStorage.getItem("active_home_tab");
    return (saved === "all" || saved === "location" || saved === "following" || saved === "official" || saved === "neighborhood_qa")
      ? saved
      : "all";
  });
  const [sortTab, setSortTab] = useState<"hot" | "new" | "top">("hot");
  const [qaScope, setQaScope] = useState<"NEARBY" | "AREA" | "CITY" | "DISTRICT">("NEARBY");
  const [gpsPromptOpen, setGpsPromptOpen] = useState(false);
  const [updatingGps, setUpdatingGps] = useState(false);

  useEffect(() => {
    sessionStorage.setItem("active_home_tab", sourceTab);
    window.dispatchEvent(new CustomEvent("homeTabChanged", { detail: { tab: sourceTab } }));
  }, [sourceTab]);
  const [sortDropdownOpen, setSortDropdownOpen] = useState(false);
  const [sourceDropdownOpen, setSourceDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const sourceDropdownRef = useRef<HTMLDivElement>(null);

  const { data: user } = useCurrentUser();
  const queryClient = useQueryClient();
  const { posts, loading, initialLoading, hasMore, error, fatalError, loadMore, retry, updatePost, prependPost, deletePost, isFallback, refreshFeed, isFetchingNextPage } =
    useFeed(sourceTab, sortTab, qaScope);

  // Native scroll event listener for smooth, continuous infinite scrolling on main container and window
  useEffect(() => {
    if (!hasMore || isFetchingNextPage) return;

    const scrollContainers = Array.from(document.querySelectorAll<HTMLElement>("main, .overflow-y-auto"));

    let ticking = false;
    const checkScrollPosition = () => {
      if (!hasMore || isFetchingNextPage) return;

      let shouldLoad = false;
      scrollContainers.forEach((container) => {
        if (container.scrollHeight > 0 && container.scrollTop + container.clientHeight >= container.scrollHeight - 600) {
          shouldLoad = true;
        }
      });

      const winScrollTop = window.scrollY || document.documentElement.scrollTop;
      const winScrollHeight = document.documentElement.scrollHeight;
      const winClientHeight = window.innerHeight;
      if (winScrollHeight > 0 && winScrollTop + winClientHeight >= winScrollHeight - 600) {
        shouldLoad = true;
      }

      if (shouldLoad) {
        loadMore();
      }
    };

    const handleScroll = () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        ticking = false;
        checkScrollPosition();
      });
    };

    scrollContainers.forEach((c) => c.addEventListener("scroll", handleScroll, { passive: true }));
    window.addEventListener("scroll", handleScroll, { passive: true });

    return () => {
      scrollContainers.forEach((c) => c.removeEventListener("scroll", handleScroll));
      window.removeEventListener("scroll", handleScroll);
    };
  }, [hasMore, isFetchingNextPage, loadMore]);

  const { data: hasOfficialPosts } = useQuery({
    queryKey: ["hasOfficialPosts"],
    queryFn: async () => {
      try {
        const res = await axiosInstance.get("/api/v1/feed/official", {
          params: { limit: 1, sort: "RECENT" },
        });
        const json = res.data;
        const isWrapped = json.success !== undefined && json.data !== undefined;
        const container = isWrapped ? json.data : json;
        let items: any[] = [];
        if (Array.isArray(container)) {
          items = container;
        } else if (container && typeof container === "object") {
          items = container.data ?? container.content ?? [];
        }
        return items.length > 0;
      } catch {
        return false;
      }
    },
    staleTime: 60 * 1000,
  });

  const isOfficialEmpty = sourceTab === "official" && !initialLoading && posts.length === 0;

  useEffect(() => {
    if ((hasOfficialPosts === false || isOfficialEmpty) && sourceTab === "official") {
      setSourceTab("all");
    }
  }, [hasOfficialPosts, isOfficialEmpty, sourceTab]);

  const visibleSourceTabs = SOURCE_TABS.filter((t) => {
    if (t.key === "official") {
      if (hasOfficialPosts === false || isOfficialEmpty) return false;
    }
    return true;
  });

  const scopeMap: Record<string, "FOR_YOU" | "LOCATION" | "FOLLOWING" | "OFFICIAL" | "NEIGHBORHOOD_QA"> = {
    all: "FOR_YOU",
    location: "LOCATION",
    following: "FOLLOWING",
    official: "OFFICIAL",
    neighborhood_qa: "NEIGHBORHOOD_QA",
  };

  const topPostId = posts[0]?.id;
  const lacksLocationData = sourceTab === "neighborhood_qa" && qaScope === "NEARBY" && !user?.pincode && !user?.hasTrueGps;

  const { newPostCount, clearAndRefresh } = useFeedRefresh({
    scope: scopeMap[sourceTab] || "FOR_YOU",
    topPostId,
    onRefresh: () => {
      if (sortTab !== "new") {
        setSortTab("new");
      } else {
        refreshFeed();
      }
    },
  });

  // Listen for sidebar or global feed refresh events
  useEffect(() => {
    const handleRefreshActiveFeed = (e: Event) => {
      const customEvent = e as CustomEvent;
      const target = customEvent.detail?.target;
      if (!target || target === "home" || target === "feed") {
        refreshFeed();
      }
    };
    window.addEventListener("refreshActiveFeed", handleRefreshActiveFeed);
    return () => window.removeEventListener("refreshActiveFeed", handleRefreshActiveFeed);
  }, [refreshFeed]);

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
          setGpsPromptOpen(false);
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

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setSortDropdownOpen(false);
      }
      if (sourceDropdownRef.current && !sourceDropdownRef.current.contains(event.target as Node)) {
        setSourceDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const currentUser = user ? {
    id: user.id,
    username: user.actualUsername || user.username,
    role: user.role
  } : undefined;

  const handleLike = useCallback((postId: number, liked: boolean) => {
    updatePost(postId, (post) => {
      if (!!post.isLikedByCurrentUser === liked) return {};
      const hasDislikeSupport = post.variant === "issue";
      const isPreviouslyDisliked = hasDislikeSupport && !!(post as any).isDislikedByCurrentUser;
      return {
        isLikedByCurrentUser: liked,
        likeCount: (post.likeCount ?? 0) + (liked ? 1 : -1),
        ...(isPreviouslyDisliked && liked && {
          isDislikedByCurrentUser: false,
          dislikeCount: Math.max(0, ((post as any).dislikeCount ?? 0) - 1)
        })
      } as Partial<AnyPost>;
    });
  }, [updatePost]);

  const handleDislike = useCallback((postId: number, disliked: boolean) => {
    updatePost(postId, (post) => {
      if (!!(post as any).isDislikedByCurrentUser === disliked) return {};
      const isPreviouslyLiked = !!post.isLikedByCurrentUser;
      return {
        isDislikedByCurrentUser: disliked,
        dislikeCount: ((post as any).dislikeCount ?? 0) + (disliked ? 1 : -1),
        ...(isPreviouslyLiked && disliked && {
          isLikedByCurrentUser: false,
          likeCount: Math.max(0, (post.likeCount ?? 0) - 1)
        })
      } as Partial<AnyPost>;
    });
  }, [updatePost]);

  const handleSave = useCallback((postId: number, saved: boolean) => {
    updatePost(postId, (post) => {
      const isSaved = !!((post as any).isSavedByCurrentUser ?? (post as any).isSaved ?? false);
      if (isSaved === saved) return {};
      return {
        isSaved: saved,
        isSavedByCurrentUser: saved
      } as Partial<AnyPost>;
    });
  }, [updatePost]);

  const handleShare = useCallback((postId: number) => {
    updatePost(postId, (post) => ({
      shareCount: (post.shareCount ?? 0) + 1
    }));
  }, [updatePost]);

  const handleComment = useCallback((postId: number) => {
    window.location.href = `/post/${postId}`;
  }, []);

  const handleDelete = useCallback((postId: number) => {
    deletePost(postId);
  }, [deletePost]);

  const handleNotInterested = useCallback(async (postId: number) => {
    deletePost(postId);
    try {
      await axiosInstance.post("/api/v1/feed/signal/not-interested", { postId });
    } catch (err) {
      console.error("Failed to submit not interested signal:", err);
    }
  }, [deletePost]);

  useEffect(() => {
    const onPostCreated = (e: Event) => {
      const customEvent = e as CustomEvent;
      const newPostData = customEvent.detail?.post;
      if (newPostData) prependPost(newPostData);
      else retry();
      queryClient.invalidateQueries({ queryKey: ["hasOfficialPosts"] });
    };
    window.addEventListener("postCreated", onPostCreated);
    return () => window.removeEventListener("postCreated", onPostCreated);
  }, [retry, prependPost, queryClient]);

  return (
    <div className="space-y-4">
      <Helmet>
        <title>Home Dashboard | Govlyx Hyperlocal Community Network</title>
        <meta name="description" content="See active neighborhood Q&As, municipal resolutions, and local discussions in your area." />
      </Helmet>
      <div className={`sticky top-4 transition-all ${sourceDropdownOpen || sortDropdownOpen ? "z-50" : "z-30"}`}>
        <div className="flex items-center justify-between gap-2 rounded-xl border border-base-content/5 bg-base-200/95 p-1.5 backdrop-blur-md shadow-sm">
          {/* Desktop Scrollable Feed Tabs */}
          <div className="hidden sm:flex gap-1 bg-base-200/50 p-1 rounded-xl overflow-x-auto scrollbar-hide flex-1">
            {visibleSourceTabs.map((t) => (
              <button
                key={t.key}
                onClick={() => {
                  if (sourceTab === t.key) {
                    refreshFeed();
                  } else {
                    setSourceTab(t.key);
                  }
                }}
                onDoubleClick={() => {
                  refreshFeed();
                }}
                className={`shrink-0 rounded-lg px-4 py-1.5 text-sm font-bold transition-all whitespace-nowrap cursor-pointer ${sourceTab === t.key ? "bg-[#1D4ED8] text-white shadow-sm" : "text-base-content/70 hover:text-base-content hover:bg-base-300/50"}`}
              >
                {t.label}
              </button>
            ))}
          </div>

          {/* Mobile Source Dropdown */}
          <div className="relative flex-1 sm:hidden z-40" ref={sourceDropdownRef}>
            <button
              onClick={() => setSourceDropdownOpen(!sourceDropdownOpen)}
              className={`flex items-center justify-between w-full px-4 py-2 h-[36px] sm:h-[38px] rounded-xl border transition-all text-sm font-bold cursor-pointer ${
                sourceDropdownOpen
                  ? "bg-[#1D4ED8] text-white border-[#1D4ED8]"
                  : "border-base-300 bg-base-200/50 hover:bg-base-300/50 text-base-content"
              }`}
            >
              <span key={sourceTab}>{visibleSourceTabs.find((t) => t.key === sourceTab)?.label || SOURCE_TABS.find((t) => t.key === sourceTab)?.label}</span>
              <ChevronDown size={14} className={`transition-transform duration-200 ${sourceDropdownOpen ? "rotate-180" : ""}`} />
            </button>

            <AnimatePresence>
              {sourceDropdownOpen && (
                <motion.div
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: 8 }}
                  className="absolute left-0 mt-1.5 z-40 w-full overflow-hidden rounded-xl border border-base-300 bg-base-100 p-1 shadow-lg"
                >
                  {visibleSourceTabs.map((t) => (
                    <button
                      key={t.key}
                      onClick={() => {
                        if (sourceTab === t.key) {
                          refreshFeed();
                        } else {
                          setSourceTab(t.key);
                        }
                        setSourceDropdownOpen(false);
                      }}
                      className={`flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm font-bold transition-all cursor-pointer ${sourceTab === t.key ? "bg-[#1D4ED8] text-white" : "text-base-content/70 hover:bg-base-200/60 hover:text-base-content"}`}
                    >
                      {t.label}
                    </button>
                  ))}
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* Sort Dropdown */}
          <div className="relative shrink-0" ref={dropdownRef}>
            <button
              onClick={() => setSortDropdownOpen(!sortDropdownOpen)}
              className={`flex items-center gap-1.5 px-3 py-1.5 h-[36px] sm:h-[38px] rounded-xl border transition-all text-xs sm:text-sm font-bold cursor-pointer ${
                sortDropdownOpen
                  ? "bg-[#1D4ED8] text-white border-[#1D4ED8]"
                  : "border-base-300 bg-base-200/50 hover:bg-base-300/50 text-base-content/70 hover:text-base-content"
              }`}
            >
              <SlidersHorizontal size={14} className="sm:w-4 sm:h-4" />
              <span key={sortTab} className="capitalize">{sortTab}</span>
              <ChevronDown size={12} className={`transition-transform duration-200 ${sortDropdownOpen ? "rotate-180" : ""}`} />
            </button>

            <AnimatePresence>
              {sortDropdownOpen && (
                <motion.div
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: 8 }}
                  className="absolute right-0 mt-1.5 z-40 overflow-hidden rounded-xl border border-base-300 bg-base-100 p-1 shadow-lg min-w-[120px]"
                >
                  {SORT_TABS.map((t) => {
                    const ActiveIcon = t.icon;
                    return (
                      <button
                        key={t.key}
                        onClick={() => {
                          setSortTab(t.key);
                          setSortDropdownOpen(false);
                        }}
                        className={`flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-xs sm:text-sm font-bold transition-all cursor-pointer ${sortTab === t.key ? "bg-[#1D4ED8] text-white" : "text-base-content/70 hover:bg-base-200/60 hover:text-base-content"}`}
                      >
                        <ActiveIcon size={14} />
                        {t.label}
                      </button>
                    );
                  })}
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>
      </div>

      {/* Sticky New Posts Banner positioned exactly below the filter bar */}
      <NewPostsBanner count={newPostCount} onTap={clearAndRefresh} topOffset="4.25rem" />

      {sourceTab === "neighborhood_qa" && (
        <div className="flex flex-col gap-3">
          {/* Geoscope pills without "Scope" label */}
          <div className="flex flex-wrap items-center gap-2 py-1">
            <button
              onClick={() => {
                if (qaScope === "NEARBY") {
                  refreshFeed();
                } else {
                  setQaScope("NEARBY");
                }
              }}
              onDoubleClick={() => refreshFeed()}
              className={`btn btn-sm rounded-full px-4 border font-bold cursor-pointer transition-all ${
                qaScope === "NEARBY" 
                  ? "bg-[#1D4ED8] text-white border-[#1D4ED8] shadow-[0_6px_18px_rgba(29,78,216,0.32)]"
                  : "bg-base-200/70 border-base-300 text-base-content/60 hover:bg-base-300/70 hover:text-base-content"
              }`}
            >
              Nearby (5km)
            </button>
            <button
              onClick={() => {
                if (qaScope === "AREA") {
                  refreshFeed();
                } else {
                  setQaScope("AREA");
                }
              }}
              onDoubleClick={() => refreshFeed()}
              className={`btn btn-sm rounded-full px-4 border font-bold cursor-pointer transition-all ${
                qaScope === "AREA"
                  ? "bg-[#1D4ED8] text-white border-[#1D4ED8] shadow-[0_6px_18px_rgba(29,78,216,0.32)]"
                  : "bg-base-200/70 border-base-300 text-base-content/60 hover:bg-base-300/70 hover:text-base-content"
              }`}
            >
              Area
            </button>
            <button
              onClick={() => {
                if (qaScope === "CITY") {
                  refreshFeed();
                } else {
                  setQaScope("CITY");
                }
              }}
              onDoubleClick={() => refreshFeed()}
              className={`btn btn-sm rounded-full px-4 border font-bold cursor-pointer transition-all ${
                qaScope === "CITY"
                  ? "bg-[#1D4ED8] text-white border-[#1D4ED8] shadow-[0_6px_18px_rgba(29,78,216,0.32)]"
                  : "bg-base-200/70 border-base-300 text-base-content/60 hover:bg-base-300/70 hover:text-base-content"
              }`}
            >
              City
            </button>
            <button
              onClick={() => {
                if (qaScope === "DISTRICT") {
                  refreshFeed();
                } else {
                  setQaScope("DISTRICT");
                }
              }}
              onDoubleClick={() => refreshFeed()}
              className={`btn btn-sm rounded-full px-4 border font-bold cursor-pointer transition-all ${
                qaScope === "DISTRICT" 
                  ? "bg-[#1D4ED8] text-white border-[#1D4ED8] shadow-[0_6px_18px_rgba(29,78,216,0.32)]"
                  : "bg-base-200/70 border-base-300 text-base-content/60 hover:bg-base-300/70 hover:text-base-content"
              }`}
            >
              District
            </button>
          </div>

          {/* One-Time GPS Banner */}
          {!user?.hasTrueGps && (
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-3 rounded-xl border border-blue-500/20 bg-blue-500/10 shadow-sm w-full">
              <div className="flex items-center gap-2 text-[#1D4ED8] dark:text-white">
                <MapPin size={18} className="shrink-0 text-[#1D4ED8] dark:text-white" />
                <span className="text-sm font-semibold">
                  Allow GPS access one time to get highly accurate neighborhood posts!
                </span>
              </div>
              <button
                className="btn btn-sm bg-[#1D4ED8] hover:bg-blue-800 text-white border-none shrink-0 flex items-center gap-1.5"
                onClick={() => setGpsPromptOpen(true)}
              >
                <MapPin size={14} />
                <span>Allow GPS</span>
              </button>
            </div>
          )}

          {isFallback && qaScope === "NEARBY" && (
            <div className="rounded-2xl border border-transparent p-3 flex items-center gap-2 text-xs font-bold text-white bg-[#1D4ED8] shadow-sm">
              <Info size={16} className="shrink-0 text-white" />
              <span>No nearby posts, showing district Q&As.</span>
            </div>
          )}
        </div>
      )}

      {user?.hasInvalidPincode && (
        <div className="alert alert-warning shadow-sm rounded-2xl border border-warning/25 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-4">
          <div className="flex items-center gap-3">
            <AlertTriangle size={22} className="shrink-0 text-warning" />
            <div>
              <h4 className="font-bold text-sm">Location Verification Failed</h4>
              <p className="text-xs opacity-85">We couldn't verify your location. Please update your pincode in Settings to access local features.</p>
            </div>
          </div>
          <a href="/settings" className="btn btn-xs sm:btn-sm btn-outline border-warning-content/30 hover:bg-warning-content/10 shrink-0 self-end sm:self-auto font-bold">
            Update Pincode
          </a>
        </div>
      )}

      {error && (
        <div className="rounded-xl border border-error/30 bg-error/10 px-4 py-3 text-sm text-error flex items-center justify-between gap-3">
          <span>{error}</span>
          {fatalError ? (
            <a href="/login" className="shrink-0 underline font-medium">Log in</a>
          ) : (
            <button className="shrink-0 underline font-medium" onClick={retry}>Retry</button>
          )}
        </div>
      )}

      {/* Pull-To-Refresh Wrapped Feed */}
      <PullToRefresh onRefresh={refreshFeed}>
        {/* Single Column Feed Layout */}
        <div className="flex flex-col gap-6 w-full max-w-4xl mx-auto">
          {initialLoading ? (
            <div className="relative min-h-[360px] rounded-3xl">
              <LoadingAnimation overlay label="Loading posts" />
            </div>
          ) : posts.length === 0 && !loading && !error ? (
            lacksLocationData ? (
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2 }}
                className="flex flex-col items-center justify-center rounded-2xl border border-base-300 bg-base-200/60 p-8 sm:p-12 text-center"
              >
                <div className="flex h-14 w-14 items-center justify-center rounded-full bg-[#1D4ED8]/10 text-[#1D4ED8] mb-4 shadow-sm">
                  <MapPin size={28} />
                </div>
                <h3 className="text-lg font-bold text-base-content">We need your location</h3>
                <p className="mt-1.5 max-w-sm text-sm text-base-content/70">
                  We need your location to show you nearby posts. Allow GPS access or update your pincode in Settings.
                </p>
                <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
                  <button
                    onClick={() => setGpsPromptOpen(true)}
                    className="btn btn-sm bg-[#1D4ED8] text-white hover:bg-blue-800 border-none flex items-center gap-1.5 shadow-sm font-semibold"
                  >
                    <MapPin size={14} />
                    <span>Allow GPS</span>
                  </button>
                  <Link
                    to="/settings"
                    className="btn btn-sm btn-outline border-base-300 hover:bg-base-300/60 flex items-center gap-1.5 font-semibold"
                  >
                    <span>Update Pincode</span>
                  </Link>
                </div>
              </motion.div>
            ) : (
              <EmptyState 
                title="Nothing here yet" 
                description={sourceTab === "neighborhood_qa" ? "Ask a neighbor, talk to a neighbor, or discuss with your neighborhood!" : "Be the first to post, or try a different tab."} 
              />
            )
          ) : (
            <div className="flex flex-col gap-6">
              {posts.map((post) => (
                <PostCard
                  key={`${post.id}-${post.variant}`}
                  post={post}
                  currentUser={currentUser}
                  onLike={handleLike}
                  onDislike={handleDislike}
                  hideAuthorRoleBadge={true}
                  onSave={handleSave}
                  onShare={handleShare}
                  onComment={handleComment}
                  onDelete={handleDelete}
                  onNotInterested={handleNotInterested}
                  onResolve={(id, resolved, message) => {
                    updatePost(id, {
                      status: resolved ? "RESOLVED" : "ACTIVE",
                      isResolved: resolved,
                      resolutionMessage: resolved ? message : undefined,
                      reopened: !resolved,
                      isReopened: !resolved,
                      reopenedReason: !resolved ? message : undefined,
                    } as Partial<AnyPost>);
                  }}
                />
              ))}
            </div>
          )}

          {/* Bottom Feed State: Loading Animation when fetching next page */}
          {!initialLoading && isFetchingNextPage && (
            <div className="w-full py-8 flex flex-col items-center justify-center animate-in fade-in duration-200">
              <LoadingAnimation label="Loading more posts" />
            </div>
          )}

          {/* Infinite Scroll Trigger when more posts exist */}
          {!initialLoading && hasMore && !error && (
            <div className="w-full py-2 flex flex-col items-center justify-center">
              <InfiniteScrollTrigger onIntersect={loadMore} disabled={isFetchingNextPage} />
            </div>
          )}

          {!hasMore && posts.length > 0 && !error && (
            <div className="w-full py-20 flex flex-col items-center justify-center gap-4 group/end">
              <div className="flex items-center gap-4 w-full px-4">
                <div className="h-px flex-1 bg-gradient-to-r from-transparent via-base-content/5 to-transparent" />
                <div className="p-3 rounded-2xl bg-base-200/30 backdrop-blur-sm border border-base-content/5 group-hover/end:scale-110 group-hover/end:bg-base-200/50 transition-all duration-500">
                  <Sparkles size={20} className="text-amber-400 group-hover/end:rotate-12 transition-transform" />
                </div>
                <div className="h-px flex-1 bg-gradient-to-l from-transparent via-base-content/5 to-transparent" />
              </div>
              <p className="text-[10px] sm:text-xs opacity-30 font-black tracking-[0.2em] uppercase text-center max-w-[280px] leading-relaxed">
                You've officially reached the end of the feed
              </p>
            </div>
          )}
        </div>
      </PullToRefresh>

      {typeof document !== "undefined" && createPortal(
        <AnimatePresence>
          {gpsPromptOpen && (
            <motion.div
              className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50 px-4 backdrop-blur-sm"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
            >
              <motion.div
                className="w-full max-w-sm rounded-2xl border border-black/10 dark:border-white/15 bg-base-100 p-5 shadow-2xl"
                initial={{ opacity: 0, y: 18, scale: 0.96 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 12, scale: 0.96 }}
                transition={{ duration: 0.18 }}
              >
                <div className="flex items-start gap-3">
                  <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[#1D4ED8]/10 text-[#1D4ED8]">
                    <MapPin size={22} />
                  </div>
                  <div>
                    <h3 className="text-base font-extrabold text-base-content text-left">Allow GPS access?</h3>
                    <p className="mt-1 text-sm leading-6 text-base-content/70 text-left">
                      Govlyx will use your current location once to tune Neighborhood Q&A posts around you.
                    </p>
                  </div>
                </div>
                <div className="mt-5 flex justify-end gap-2">
                  <button
                    className="btn btn-sm btn-ghost"
                    onClick={() => setGpsPromptOpen(false)}
                    disabled={updatingGps}
                  >
                    Cancel
                  </button>
                  <button
                    className="btn btn-sm bg-[#1D4ED8] text-white hover:bg-blue-800 border-none"
                    onClick={handleUpdateGps}
                    disabled={updatingGps}
                  >
                    {updatingGps ? <Loader2 size={14} className="animate-spin" /> : <MapPin size={14} />}
                    {updatingGps ? "Updating..." : "Allow GPS"}
                  </button>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>,
        document.body
      )}
    </div>
  );
};

export default Home;
