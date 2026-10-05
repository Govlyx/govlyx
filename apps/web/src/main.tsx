(window as any).global = window;
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';

import App from './App';
import './index.css';
import { registerSW } from 'virtual:pwa-register';

registerSW({
  immediate: true,
  onRegistered(_r) {},
  onRegisterError(error) {
    console.error('Service Worker registration failed:', error);
  },
});

import { GoogleOAuthProvider } from '@react-oauth/google';
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client';
import { createSyncStoragePersister } from '@tanstack/query-sync-storage-persister';
import { onlineManager } from '@tanstack/react-query';
import { queryClient } from './api/queryClient';

// Initialize and sync onlineManager
onlineManager.setOnline(navigator.onLine);
window.addEventListener('online', () => onlineManager.setOnline(true));
window.addEventListener('offline', () => onlineManager.setOnline(false));

const persister = createSyncStoragePersister({
  storage: window.localStorage,
  key: 'govlyx-query-cache',
});

const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <GoogleOAuthProvider clientId={googleClientId}>
      <PersistQueryClientProvider
        client={queryClient}
        persistOptions={{
          persister,
          maxAge: 1000 * 60 * 60, // Cache valid for 1 hour
          dehydrateOptions: {
            shouldDehydrateQuery: (query) => {
              if (!query || !query.queryKey || !Array.isArray(query.queryKey))
                return false;
              const key = query.queryKey[0];
              return key === 'feed' || key === 'currentUser';
            },
            shouldDehydrateMutation: (mutation) =>
              Boolean(mutation?.state?.isPaused),
          },
        }}
      >
        <HelmetProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </HelmetProvider>
      </PersistQueryClientProvider>
    </GoogleOAuthProvider>
  </React.StrictMode>,
);
