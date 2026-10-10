/**
 * src/utils/searchCache.ts
 * 
 * Logic for caching search results and history in localStorage
 * for offline-capable auto-suggestions.
 */

export interface CacheItem {
  id?: number | string;
  kind: "POST" | "SOCIAL_POST" | "COMMUNITY" | "HASHTAG" | "UNKNOWN";
  displayText: string;
  subText?: string;
  avatarUrl?: string | null;
  slug?: string;
  timestamp: number;
}

const RECENT_KEY = "govlyx_recent_searches";
const SUGGESTIONS_KEY = "govlyx_offline_suggestions";
const MAX_RECENT = 10;
const MAX_SUGGESTIONS = 100;

/**
 * Save a query to the "Recent Searches" history
 */
export const saveRecentSearch = (query: string) => {
  if (!query || query.trim().length < 2) return;
  try {
    const recent = getRecentSearches();
    const filtered = [query.trim(), ...recent.filter((q) => q.toLowerCase() !== query.toLowerCase().trim())];
    localStorage.setItem(RECENT_KEY, JSON.stringify(filtered.slice(0, MAX_RECENT)));
  } catch (e) {
    console.error("Error saving recent search", e);
  }
};

export const getRecentSearches = (): string[] => {
  try {
    return JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
  } catch {
    return [];
  }
};

/**
 * Cache a specific result item (Community, Hashtag, etc.) for offline use
 */
export const cacheSuggestion = (item: Omit<CacheItem, "timestamp">) => {
  try {
    const suggestions = getAllCachedSuggestions();
    // Unique by kind + id/slug
    const idStr = item.id ? String(item.id) : (item.slug || item.displayText);
    const filtered = suggestions.filter((s) => {
      const sId = s.id ? String(s.id) : (s.slug || s.displayText);
      return !(s.kind === item.kind && sId === idStr);
    });

    const newList = [{ ...item, timestamp: Date.now() }, ...filtered];
    localStorage.setItem(SUGGESTIONS_KEY, JSON.stringify(newList.slice(0, MAX_SUGGESTIONS)));
  } catch (e) {
    console.error("Error caching suggestion", e);
  }
};

export const getAllCachedSuggestions = (): CacheItem[] => {
  try {
    return JSON.parse(localStorage.getItem(SUGGESTIONS_KEY) || "[]");
  } catch {
    return [];
  }
};

/**
 * Filter cached suggestions by query
 */
export const getOfflineSuggestions = (query: string): CacheItem[] => {
  const q = query.toLowerCase().trim();
  if (!q) return [];
  
  return getAllCachedSuggestions()
    .filter((item) => {
      return (
        item.displayText.toLowerCase().includes(q) ||
        item.subText?.toLowerCase().includes(q) ||
        (item.kind === "HASHTAG" && item.displayText.replace("#", "").toLowerCase().startsWith(q))
      );
    })
    .sort((a, b) => b.timestamp - a.timestamp)
    .slice(0, 10);
};

export const removeRecentSearch = (query: string) => {
  try {
    const recent = getRecentSearches();
    const filtered = recent.filter((q) => q.toLowerCase() !== query.toLowerCase().trim());
    localStorage.setItem(RECENT_KEY, JSON.stringify(filtered));
  } catch (e) {
    console.error("Error removing recent search", e);
  }
};

export const clearRecentSearches = () => {
  try {
    localStorage.removeItem(RECENT_KEY);
  } catch (e) {
    console.error("Error clearing recent searches", e);
  }
};

export const clearSearchCache = () => {
  localStorage.removeItem(RECENT_KEY);
  localStorage.removeItem(SUGGESTIONS_KEY);
};
