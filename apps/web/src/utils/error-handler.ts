import axios from "axios";

export interface ApiError {
  success: boolean;
  message: string;
  error?: string;
  data?: Record<string, string>;
  requestId?: string;
}

export function parseError(err: unknown): string {
  if (!axios.isAxiosError(err)) {
    if (err instanceof Error) return err.message;
    return "An unexpected error occurred";
  }

  // Network error or server not reachable
  if (!err.response) {
    if (err.code === "ECONNABORTED") {
      return "Request timed out. Please check your internet connection.";
    }
    return "Network error. Please verify your connection.";
  }

  const status = err.response.status;
  const data = err.response.data as ApiError | undefined;

  // Log requestId for debugging
  if (data?.requestId) {
    console.error(`API Error [Request ID: ${data.requestId}]:`, err.response);
  } else {
    console.error("API Error:", err.response);
  }

  // Handle server 500 errors
  if (status >= 500) {
    return "Server error. Please try again later.";
  }

  if (data) {
    // If validation failed, collect field-level errors
    if (data.message === "Validation failed" && data.data && typeof data.data === "object") {
      const fieldErrors = Object.entries(data.data)
        .map(([field, msg]) => `${field}: ${msg}`)
        .join(", ");
      return fieldErrors || data.error || data.message;
    }
    // For authentication/registration requests, prefer user-friendly message over generic error categories
    if (err.config?.url?.includes("/api/auth/")) {
      return data.message || data.error || "Request failed";
    }
    // Return detailed error first, fallback to message
    return data.error || data.message || "Request failed";
  }

  return `Request failed with status ${status}`;
}
