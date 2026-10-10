import { useQuery, useInfiniteQuery } from "@tanstack/react-query";
import axiosInstance from "./axiosConfig";

export type ResultKind = "POST" | "SOCIAL_POST" | "COMMUNITY" | "HASHTAG" | "UNKNOWN";

export interface SearchResult {
  resultType: string;
  id: number;
  post?: any;
  socialPost?: any;
  communityName?: string;
  communitySlug?: string;
  communityAvatarUrl?: string;
  communityDescription?: string;
  memberCount?: number;
  privacy?: string;
  healthScore?: number;
  pincode?: string;
  locationName?: string;
  hashtag?: string;
  postCount?: number;
  relevanceScore?: number;
}

export interface SearchResponse {
  query: string;
  currentPage: number;
  nextPage: number | null;
  count: number;
  limit: number;
  hasMore: boolean;
  data: SearchResult[];
  grouped?: Record<string, SearchResult[]>;
  countByType?: Record<string, number>;
}

export interface PaginatedResponse<T> {
  data: T[];
  hasMore: boolean;
  nextCursor: number | null;
  limit: number;
  count: number;
}

// 1. Hook for Quick Autocomplete Search (Typeahead)
export const useQuickSearch = (query: string) => {
  return useQuery<SearchResponse>({
    queryKey: ["search", "quick", query],
    queryFn: async () => {
      const response = await axiosInstance.get(`/api/search/quick`, {
        params: { q: query },
      });
      return response.data;
    },
    enabled: query.trim().length > 0,
    staleTime: 1000 * 60 * 5, // Cache typeahead results for 5 minutes
  });
};

// 2. Hook for Unified/Filtered Infinite Search results
export const useSearchInfinite = (query: string, type: string) => {
  return useInfiniteQuery<any>({
    queryKey: ["search", "infinite", query, type],
    queryFn: async ({ pageParam = 0 }) => {
      const isAll = type === "ALL";
      const endpoint = isAll ? "/api/search" : "/api/search/type";
      
      const params: Record<string, any> = {
        q: query,
        page: pageParam,
        limit: 10,
      };

      if (!isAll) {
        params.type = type;
      }

      const response = await axiosInstance.get(endpoint, { params });
      return response.data;
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage: any) => {
      if (!lastPage || !lastPage.hasMore) {
        return undefined;
      }
      // For /api/search, backend returns 'nextPage'
      // For /api/search/type, backend returns 'nextCursor'
      return lastPage.nextPage !== undefined ? lastPage.nextPage : lastPage.nextCursor;
    },
    enabled: query.trim().length > 0,
  });
};
