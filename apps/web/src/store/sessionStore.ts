

const KEY = "chat_session_id";

export const sessionStore = {
  save(sessionId: string): void {
    try { localStorage.setItem(KEY, sessionId); } catch { /* private-browsing */ }
  },
  get(): string | null {
    try { return localStorage.getItem(KEY); } catch { return null; }
  },
  clear(): void {
    try { localStorage.removeItem(KEY); } catch { /* ignore */ }
  },
};