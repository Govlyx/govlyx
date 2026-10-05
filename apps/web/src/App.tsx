import { useEffect, useState } from 'react';
import AppRouter from './router/AppRouter';
import axios from 'axios';
import { API_BASE_URL } from './api/axiosConfig';
import {
  persistAuthToken,
  clearAuthTokens,
  decodeAuthToken,
} from './utils/auth';
import { feedSocket } from './api/feedSocket.service';
import { vaultService } from './services/vaultService';

import LoadingAnimation from './components/ui/LoadingAnimation';

const App = () => {
  const [isInitializing, setIsInitializing] = useState(() => {
    try {
      return localStorage.getItem('isLoggedIn') === 'true';
    } catch {
      return false;
    }
  });

  useEffect(() => {
    // Warm up the backend API/cache on initial mount
    fetch('/api/public/health', { method: 'HEAD' }).catch(() => {});

    // Warm up citizen actorToken in memory from local IndexedDB if not already set
    const warmUpVault = async () => {
      try {
        const cachedActor = vaultService.getCachedActorToken();
        if (!cachedActor) {
          const hasSalt = await vaultService.hasLocalBlindSalt();
          if (hasSalt) {
            const decoded = decodeAuthToken();
            const serverActor =
              (decoded as any)?.serverActorToken ||
              (decoded as any)?.actorToken;
            if (serverActor) {
              const blindSalt = await vaultService.getStoredBlindSalt();
              if (blindSalt) {
                await vaultService.deriveActorToken(serverActor, blindSalt);
              }
            }
          }
        }
      } catch {
        /* ignore background warm up error */
      }
    };
    warmUpVault();

    if (isInitializing) {
      axios
        .post(`${API_BASE_URL}/api/auth/refresh`, {}, { withCredentials: true })
        .then(async (res) => {
          const token =
            res.data?.data?.token ||
            res.data?.token ||
            res.data?.data?.authToken ||
            res.data?.authToken ||
            res.data?.data?.accessToken ||
            res.data?.accessToken ||
            res.data?.data?.jwt ||
            res.data?.jwt;
          if (token) {
            persistAuthToken(token);
            // Refresh token response may also provide updated serverActorToken
            const serverActor =
              res.data?.data?.serverActorToken || res.data?.serverActorToken;
            if (serverActor) {
              const blindSalt = await vaultService.getStoredBlindSalt();
              if (blindSalt) {
                await vaultService.deriveActorToken(serverActor, blindSalt);
              }
            }
          } else {
            throw new Error('No token returned');
          }
        })
        .catch(() => {
          clearAuthTokens();
        })
        .finally(() => {
          setIsInitializing(false);
        });
    }
  }, [isInitializing]);

  useEffect(() => {
    if (!isInitializing) {
      feedSocket.connect();
    }
  }, [isInitializing]);

  if (isInitializing) {
    return (
      <div className="flex h-screen w-screen items-center justify-center bg-base-100">
        <LoadingAnimation label="Govlyx" />
      </div>
    );
  }

  return <AppRouter />;
};

export default App;
