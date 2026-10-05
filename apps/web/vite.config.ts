import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { VitePWA } from 'vite-plugin-pwa';

const zenQuotesFallback = [
  {
    q: 'Democracy is not a spectator sport. Put on your jersey and get in the game!',
    a: 'Civic Wisdom',
  },
];

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backendUrl = env.VITE_API_URL;

  return {
    root: __dirname,
    cacheDir: '../../node_modules/.vite/apps/web',
    publicDir: 'public',
    build: {
      outDir: '../../dist/apps/web',
      emptyOutDir: true,
    },
    plugins: [
      react(),
      tailwindcss(),
      VitePWA({
        registerType: 'autoUpdate',
        devOptions: {
          enabled: true, // Enables testing the service worker locally during development
        },
        manifest: {
          name: 'Govlyx Platform',
          short_name: 'Govlyx',
          theme_color: '#1D4ED8',
          background_color: '#ffffff',
          display: 'standalone',
          icons: [
            {
              src: '/favicon-192x192.png',
              sizes: '192x192',
              type: 'image/png',
            },
            {
              src: '/favicon-512x512.png',
              sizes: '512x512',
              type: 'image/png',
            },
            {
              src: '/favicon-512x512.png',
              sizes: '512x512',
              type: 'image/png',
              purpose: 'any maskable',
            },
          ],
        },
        workbox: {
          globPatterns: ['**/*.{js,css,html,ico,png,svg}'], // Tells the worker to cache all ui elements
          navigateFallbackAllowlist: [/^(?!\/?api).*/],
          maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
        },
      }),
    ],

    define: {
      // sockjs-client (CommonJS) references Node's `global` — polyfill for browser
      global: 'globalThis',
    },

    server: {
      headers: {
        'Cross-Origin-Opener-Policy': 'same-origin-allow-popups',
      },
      proxy: {
        '/external-api': {
          target: 'https://zenquotes.io',
          changeOrigin: true,
          secure: false,
          timeout: 5000,
          proxyTimeout: 5000,
          rewrite: (path) => path.replace(/^\/external-api/, ''),
          configure: (proxy) => {
            proxy.on('error', (_err, _req, res) => {
              if ('writeHead' in res && 'end' in res) {
                if (!res.headersSent) {
                  res.writeHead(200, { 'Content-Type': 'application/json' });
                }
                res.end(JSON.stringify(zenQuotesFallback));
              }
            });
          },
          headers: {
            'User-Agent':
              'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            Referer: 'https://zenquotes.io/',
          },
        },
        '/translate-api': {
          target: 'https://translate.googleapis.com',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/translate-api/, ''),
        },
        '/lingva-api': {
          target: 'https://lingva.ml',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/lingva-api/, ''),
        },
        '/mymemory-api': {
          target: 'https://api.mymemory.translated.net',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/mymemory-api/, ''),
        },
        '/api': {
          target: backendUrl,
          changeOrigin: true,
        },
        '/ws': {
          target: backendUrl,
          ws: true,
          changeOrigin: true,
        },
        '/uploads': {
          target: backendUrl,
          changeOrigin: true,
          secure: false, // For local dev against remote HTTPS
          headers: {
            Origin: backendUrl,
            Referer: backendUrl,
          },
          // Avoid 500 errors by rewriting or simple proxy
          rewrite: (path) => path,
        },
      },
    },

    preview: {
      headers: {
        'Cross-Origin-Opener-Policy': 'same-origin-allow-popups',
      },
      port: 5173,
      strictPort: true,
    },
  };
});
