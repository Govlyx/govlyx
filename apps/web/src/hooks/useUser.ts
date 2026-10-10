import { useQuery } from "@tanstack/react-query";
import axiosInstance from "../api/axiosConfig";
import { getAuthToken } from "../utils/auth";

export type UserRole = "ROLE_USER" | "ROLE_DEPARTMENT" | "ROLE_ADMIN";

export interface UserProfile {
  id: number;
  username: string;
  actualUsername: string;
  email: string;
  profileImage: string | null;
  role: UserRole;
  createdAt: string;
  pincode?: string;
  address?: string;
  preferredLanguage?: string;
  interfaceLanguage?: string;
  autoTranslate?: boolean;
  profanityFilterLevel?: "STRICT" | "BLUR" | "OFF";
  mutedWords?: string;
  hasInvalidPincode?: boolean;
  hasTrueGps?: boolean;
  isEmailVerified?: boolean;
  pendingEmail?: string;
  theme?: string;
}

/**
 * Hook to fetch and cache the currently authenticated user's profile.
 * Standardizes access to 'actualUsername' and 'profileImage'.
 */
export const useCurrentUser = (options?: { enabled?: boolean }) => {
  const hasToken = !!getAuthToken();
  return useQuery({
    queryKey: ["currentUser"],
    queryFn: async () => {
      if (!hasToken) return null;
      const response = await axiosInstance.get<{ data: UserProfile }>("/api/users/me");
      const profile = response.data.data;
      
      if (profile && (profile.theme === "dark" || profile.theme === "light")) {
        const storedTheme = localStorage.getItem("theme");
        // Only initialize from profile if localStorage theme was not explicitly saved yet
        if (!storedTheme) {
          localStorage.setItem("theme", profile.theme);
          document.documentElement.setAttribute("data-theme", profile.theme);
          if (profile.theme === "dark") {
            document.documentElement.classList.add("dark");
          } else {
            document.documentElement.classList.remove("dark");
          }
          window.dispatchEvent(new CustomEvent("govlyx-theme-change", { detail: profile.theme }));
        }
      }
      
      return profile;
    },
    staleTime: 1000 * 60 * 5, // 5 minutes
    retry: 1,
    enabled: (options?.enabled !== false) && hasToken,
  });
};
