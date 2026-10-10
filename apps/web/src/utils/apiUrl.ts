/**
 * src/utils/apiUrl.ts
 *
 * Returns the absolute URL for a given API path.
 *
 * In development, Vite's proxy handles `/api/...` → backend, so an empty
 * base is fine. In production (static hosting — Netlify, Vercel, etc.) there
 * is no proxy, so every `fetch()` that uses a bare `/api/...` path hits the
 * frontend's own origin and gets a 404.
 *
 * Setting VITE_API_URL in your deploy environment (e.g. to
 *   https://jan-sahayak-ai-3fl3.onrender.com
 * ) makes every fetch go directly to the backend regardless of host.
 *
 * Usage:
 *   import { apiUrl } from '../utils/apiUrl';
 *   fetch(apiUrl('/api/search?q=foo'), { headers: ... })
 */

const FALLBACK_URL = "https://api.govlyx.com";
const rawUrl = import.meta.env.VITE_API_URL || FALLBACK_URL;
const BASE = import.meta.env.DEV
  ? ""
  : rawUrl.replace(/\/$/, "");

/**
 * Prepend the backend base URL to `path`.
 * `path` should start with `/api/...`.
 */
export function apiUrl(path: string): string {
  return `${BASE}${path}`;
}
