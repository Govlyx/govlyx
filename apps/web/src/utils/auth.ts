import { jwtDecode } from "jwt-decode";

interface JwtPayload {
  sub: string;         // userId
  username: string;
  email: string;
  role: string | null; // e.g. "ROLE_DEPARTMENT", "ROLE_USER", "ROLE_ADMIN"
  isActive: boolean;
  exp: number;
  iat: number;
}

let inMemoryToken: string | null = null;

export function getAuthToken(): string | null {
  if (inMemoryToken) return inMemoryToken;
  try {
    const saved =
      localStorage.getItem("token") ||
      localStorage.getItem("authToken") ||
      localStorage.getItem("jwt") ||
      localStorage.getItem("access_token") ||
      localStorage.getItem("govlyx_token");
    if (saved) {
      inMemoryToken = saved;
      return saved;
    }
  } catch {
    /* ignore */
  }
  return null;
}

export function setAuthToken(token: string | null) {
  inMemoryToken = token;
  try {
    if (token) {
      localStorage.setItem("token", token);
      localStorage.setItem("isLoggedIn", "true");
    } else {
      localStorage.removeItem("token");
      localStorage.removeItem("authToken");
      localStorage.removeItem("jwt");
      localStorage.removeItem("access_token");
      localStorage.removeItem("govlyx_token");
    }
  } catch {
    /* ignore */
  }
}

export function persistAuthToken(token: string) {
  inMemoryToken = token;
  try {
    localStorage.setItem("token", token);
    localStorage.setItem("isLoggedIn", "true");
  } catch {
    /* ignore private-browsing */
  }
}

export function clearAuthTokens() {
  inMemoryToken = null;
  try {
    localStorage.setItem("isLoggedIn", "false");
    localStorage.removeItem("token");
    localStorage.removeItem("authToken");
    localStorage.removeItem("jwt");
    localStorage.removeItem("access_token");
    localStorage.removeItem("govlyx_token");
  } catch {
    /* ignore */
  }
}

// Synchronize logouts across multiple tabs
if (typeof window !== "undefined") {
  window.addEventListener("storage", (event) => {
    if (event.key === "isLoggedIn" && event.newValue === "false") {
      inMemoryToken = null;
      window.location.reload();
    }
  });
}

export function decodeAuthToken(token = getAuthToken()): JwtPayload | null {
  if (!token) return null;
  try {
    return jwtDecode<JwtPayload>(token);
  } catch {
    return null;
  }
}

export function isTokenExpired(token = getAuthToken()): boolean {
  const decoded = decodeAuthToken(token);
  if (!decoded?.exp) return true;
  return decoded.exp * 1000 <= Date.now();
}

/** Returns the decoded JWT payload, or null if no valid token is present. */
export function getTokenPayload(): JwtPayload | null {
  const decoded = decodeAuthToken();
  if (!decoded) return null;

  // Treat expired tokens as absent
  if (isTokenExpired()) {
    clearAuthTokens();
    return null;
  }

  return decoded;
}

/** Returns the raw role string from the JWT (e.g. "ROLE_DEPARTMENT"). */
export function getUserRole(): string | null {
  return getTokenPayload()?.role ?? null;
}

/** True if the logged-in user has the DEPARTMENT or ADMIN role. */
export function isDepartmentUser(): boolean {
  const role = getUserRole();
  return role === "ROLE_DEPARTMENT" || role === "ROLE_ADMIN";
}

/** True if the logged-in user has the ADMIN role. */
export function isAdminUser(): boolean {
  return getUserRole() === "ROLE_ADMIN";
}

/** True if the logged-in user is a regular CITIZEN/USER. */
export function isCitizenUser(): boolean {
  const role = getUserRole();
  return role === "ROLE_USER" || role === "ROLE_CITIZEN" || role === null;
}

/** True if the logged-in user is an Admin (checks ROLE_ADMIN). */
export function isSuperAdmin(): boolean {
  return isAdminUser();
}

