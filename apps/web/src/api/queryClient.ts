import { QueryClient, MutationCache } from "@tanstack/react-query";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      gcTime: 1000 * 60 * 60, // 1 hour (must be >= persister maxAge)
      staleTime: 1000 * 30, // 30 seconds - show cached data, refetch silently in background
      retry: 1,
      refetchOnWindowFocus: false,
    },
    mutations: {
      networkMode: "offlineFirst",
      retry: 3,
    },
  },
  mutationCache: new MutationCache(),
});