import { rewrite, next } from '@vercel/edge';

export function middleware(request: Request) {
  const url = new URL(request.url);
  const { pathname, search } = url;

  // Use the env variable set on Vercel
  const backendUrl = process.env.BACKEND_API_URL || '';

  if (backendUrl) {
    // 1. Rewrite API requests (/api/...)
    if (pathname.startsWith('/api/')) {
      return rewrite(new URL(pathname + search, backendUrl));
    }

    // 2. Rewrite upload requests (/uploads/...)
    if (pathname.startsWith('/uploads/')) {
      return rewrite(new URL(pathname + search, backendUrl));
    }

    // 3. Rewrite websocket connections (/ws/...)
    if (pathname.startsWith('/ws/')) {
      return rewrite(new URL(pathname + search, backendUrl));
    }
  }

  // Let all other requests (static assets, page routes) pass through normally
  return next();
}

// Support both Vercel's named and default export middleware conventions
export default middleware;
