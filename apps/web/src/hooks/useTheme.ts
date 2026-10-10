import { useSyncExternalStore } from "react";
import axiosInstance from "../api/axiosConfig";
import { getAuthToken } from "../utils/auth";
import { queryClient } from "../api/queryClient";

export type Theme = "light" | "dark";

export const getStoredTheme = (): Theme => {
  try {
    const val = localStorage.getItem("theme");
    if (val === "dark" || val === "light") return val;
  } catch (e) {
    // Ignore storage errors
  }
  return "light";
};

export const applyThemeToDom = (theme: Theme) => {
  try {
    document.documentElement.setAttribute("data-theme", theme);
    if (theme === "dark") {
      document.documentElement.classList.add("dark");
    } else {
      document.documentElement.classList.remove("dark");
    }
  } catch (e) {
    // Ignore DOM errors
  }
};

// Singleton in-memory state
let currentTheme: Theme = getStoredTheme();
const listeners = new Set<() => void>();
let serverSyncTimeout: any = null;

// Notify all subscribers
const emitChange = () => {
  for (const listener of listeners) {
    listener();
  }
};

// Listen to cross-tab storage changes once globally
if (typeof window !== "undefined") {
  window.addEventListener("storage", (e) => {
    if (e.key === "theme" && (e.newValue === "light" || e.newValue === "dark")) {
      currentTheme = e.newValue;
      applyThemeToDom(currentTheme);
      emitChange();
    }
  });
}

export const useTheme = () => {
  const theme = useSyncExternalStore(
    (callback) => {
      listeners.add(callback);
      return () => listeners.delete(callback);
    },
    () => currentTheme,
    () => "light" // Server snapshot
  );

  const setTheme = (nextTheme: Theme, event?: React.MouseEvent | MouseEvent | { clientX: number; clientY: number }) => {
    if (nextTheme !== "light" && nextTheme !== "dark") return;
    if (nextTheme === currentTheme) return;

    const switchTheme = () => {
      currentTheme = nextTheme;
      try {
        localStorage.setItem("theme", nextTheme);
      } catch (e) {
        // Ignore storage errors
      }
      applyThemeToDom(nextTheme);
      emitChange();
    };

    // Check if View Transition API is supported and user doesn't prefer reduced motion
    const prefersReducedMotion =
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    if (
      typeof document !== "undefined" &&
      "startViewTransition" in document &&
      !prefersReducedMotion
    ) {
      const x = event ? (event.clientX ?? window.innerWidth / 2) : window.innerWidth / 2;
      const y = event ? (event.clientY ?? 0) : 0;
      const endRadius = Math.hypot(
        Math.max(x, window.innerWidth - x),
        Math.max(y, window.innerHeight - y)
      );

      const transition = (document as any).startViewTransition(() => {
        switchTheme();
      });

      transition.ready
        ?.then(() => {
          const clipPath = [
            `circle(0px at ${x}px ${y}px)`,
            `circle(${endRadius}px at ${x}px ${y}px)`,
          ];
          document.documentElement.animate(
            {
              clipPath: clipPath,
            },
            {
              duration: 500,
              easing: "cubic-bezier(0.16, 1, 0.3, 1)",
              pseudoElement: "::view-transition-new(root)",
            }
          );
        })
        .catch(() => {
          // Fallback if animation promise is rejected
        });
    } else {
      switchTheme();
    }

    // Update currentUser query cache so background refetches never revert to stale theme
    queryClient.setQueryData(["currentUser"], (old: any) => {
      if (!old) return old;
      return {
        ...old,
        theme: nextTheme,
      };
    });

    // Sync to backend if authenticated with single debounced request
    if (getAuthToken()) {
      if (serverSyncTimeout) {
        clearTimeout(serverSyncTimeout);
      }
      serverSyncTimeout = setTimeout(async () => {
        try {
          await axiosInstance.patch("/api/users/settings/theme", { theme: nextTheme });
        } catch (err) {
          console.warn("Could not sync theme to database:", err);
        }
      }, 500);
    }
  };

  const toggleTheme = (event?: React.MouseEvent | MouseEvent | { clientX: number; clientY: number }) => {
    const nextTheme: Theme = currentTheme === "light" ? "dark" : "light";
    setTheme(nextTheme, event);
  };

  return { theme, toggleTheme, setTheme };
};

