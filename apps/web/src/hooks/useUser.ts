import { useQuery } from '@tanstack/react-query';
import axiosInstance from '../api/axiosConfig';
import { getAuthToken, decodeAuthToken } from '../utils/auth';
import { vaultService } from '../services/vaultService';

export type UserRole = 'ROLE_USER' | 'ROLE_DEPARTMENT' | 'ROLE_ADMIN';

export interface UserProfile {
  id: number;
  username: string;
  actualUsername: string;
  displayName?: string;
  email: string;
  profileImage: string | null;
  role: UserRole;
  createdAt: string;
  pincode?: string;
  address?: string;
  preferredLanguage?: string;
  interfaceLanguage?: string;
  autoTranslate?: boolean;
  profanityFilterLevel?: 'STRICT' | 'BLUR' | 'OFF';
  mutedWords?: string;
  hasInvalidPincode?: boolean;
  hasTrueGps?: boolean;
  isEmailVerified?: boolean;
  pendingEmail?: string;
  theme?: string;
  serverActorToken?: string;
  actorToken?: string;
  seedBlindSalt?: string;
  hasVault?: boolean;
  vaultBlob?: string;
  vaultSalt?: string;
}

/**
 * Hook to fetch and cache the currently authenticated user's profile.
 * Standardizes access to 'actualUsername' and 'profileImage'.
 */
export const useCurrentUser = (options?: { enabled?: boolean }) => {
  const hasToken = !!getAuthToken();
  return useQuery({
    queryKey: ['currentUser'],
    queryFn: async () => {
      if (!hasToken) return null;

      // 1. Ensure actorToken is warmed up before making request so X-Actor-Token header is sent
      if (!vaultService.getCachedActorToken()) {
        try {
          const decoded = decodeAuthToken();
          const serverActor =
            (decoded as any)?.serverActorToken || (decoded as any)?.actorToken;
          if (serverActor) {
            const blindSalt = await vaultService.getStoredBlindSalt();
            if (blindSalt) {
              await vaultService.deriveActorToken(serverActor, blindSalt);
            }
          }
        } catch {
          /* ignore warmup error */
        }
      }

      const response = await axiosInstance.get<{
        data: UserProfile & { authorities?: string[] };
      }>('/api/users/me');
      const profile = response.data.data;

      // 2. Derive role from authorities or fallback safely
      if (
        !profile.role &&
        profile.authorities &&
        profile.authorities.length > 0
      ) {
        const roleAuthority = profile.authorities.find(
          (a: string) =>
            a === 'ROLE_USER' || a === 'ROLE_DEPARTMENT' || a === 'ROLE_ADMIN',
        );
        if (roleAuthority) {
          (profile as any).role = roleAuthority as UserRole;
        }
      }
      if (!profile.role) {
        (profile as any).role = 'ROLE_USER';
      }

      // 3. Cache actorToken if provided by backend or derive from serverActorToken
      if (profile.actorToken && !vaultService.getCachedActorToken()) {
        vaultService.setCachedActorToken(profile.actorToken);
      } else if (
        profile.serverActorToken &&
        !vaultService.getCachedActorToken()
      ) {
        try {
          const blindSalt = await vaultService.getStoredBlindSalt();
          if (blindSalt) {
            const derived = await vaultService.deriveActorToken(
              profile.serverActorToken,
              blindSalt,
            );
            profile.actorToken = derived;
          }
        } catch {
          /* ignore */
        }
      }

      // If actorToken is cached in vaultService, ensure profile.actorToken is populated
      if (!profile.actorToken) {
        const cached = vaultService.getCachedActorToken();
        if (cached) {
          profile.actorToken = cached;
        }
      }

      // If we now have an actorToken and the profile returned opaque acc_ handle, refresh /api/users/me with X-Actor-Token
      if (
        profile.actorToken &&
        (profile.actualUsername?.startsWith('acc_') ||
          profile.username?.startsWith('acc_'))
      ) {
        try {
          const refRes = await axiosInstance.get<{ data: UserProfile }>(
            '/api/users/me',
            {
              headers: { 'X-Actor-Token': profile.actorToken },
            },
          );
          if (refRes.data?.data) {
            const fresh = refRes.data.data;
            if (fresh.username && !fresh.username.startsWith('acc_')) {
              profile.username = fresh.username;
              profile.actualUsername = fresh.actualUsername || fresh.username;
              profile.displayName = fresh.displayName || fresh.username;
            }
            if (fresh.profileImage) {
              profile.profileImage = fresh.profileImage;
            }
          }
        } catch {
          /* ignore refresh error */
        }
      }

      // 4. Sanitize username if raw DB account key acc_<id>_<hash> was returned
      if (profile.actualUsername && profile.actualUsername.startsWith('acc_')) {
        if (profile.displayName && !profile.displayName.startsWith('acc_')) {
          profile.actualUsername = profile.displayName;
          profile.username = profile.displayName;
        } else {
          profile.actualUsername = 'Citizen';
          profile.username = 'Citizen';
        }
      }

      if (profile && (profile.theme === 'dark' || profile.theme === 'light')) {
        const storedTheme = localStorage.getItem('theme');
        // Only initialize from profile if localStorage theme was not explicitly saved yet
        if (!storedTheme) {
          localStorage.setItem('theme', profile.theme);
          document.documentElement.setAttribute('data-theme', profile.theme);
          if (profile.theme === 'dark') {
            document.documentElement.classList.add('dark');
          } else {
            document.documentElement.classList.remove('dark');
          }
          window.dispatchEvent(
            new CustomEvent('govlyx-theme-change', { detail: profile.theme }),
          );
        }
      }

      return profile;
    },
    staleTime: 1000 * 60 * 5, // 5 minutes
    retry: 1,
    enabled: options?.enabled !== false && hasToken,
  });
};
