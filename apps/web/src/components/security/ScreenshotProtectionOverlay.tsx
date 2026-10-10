import { useEffect, useMemo, useState } from "react";
import { ShieldAlert } from "lucide-react";
import { useCurrentUser } from "../../hooks/useUser";

const OVERLAY_DURATION_MS = 3500;

const ScreenshotProtectionOverlay = () => {
  const { data: user } = useCurrentUser();
  const [visible, setVisible] = useState(false);

  const displayName = useMemo(() => {
    return user?.actualUsername || user?.username || user?.email || "Govlyx user";
  }, [user?.actualUsername, user?.email, user?.username]);

  useEffect(() => {
    const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(
      navigator.userAgent
    );
    if (isMobile) return;

    let timeoutId: number | undefined;

    const showBlackOverlay = () => {
      window.clearTimeout(timeoutId);
      setVisible(true);
      timeoutId = window.setTimeout(() => {
        setVisible(false);
      }, OVERLAY_DURATION_MS);
    };

    const handleKeyboardCapture = (e: KeyboardEvent) => {
      const isPrintScreen = e.key === "PrintScreen" || e.code === "PrintScreen";
      const isWinShift = e.metaKey && e.shiftKey;

      if (isPrintScreen || isWinShift) {
        showBlackOverlay();
      }
    };

    document.addEventListener("keydown", handleKeyboardCapture, true);
    document.addEventListener("keyup", handleKeyboardCapture, true);

    return () => {
      window.clearTimeout(timeoutId);
      document.removeEventListener("keydown", handleKeyboardCapture, true);
      document.removeEventListener("keyup", handleKeyboardCapture, true);
    };
  }, []);

  if (!visible) return null;

  return (
    <div
      className="fixed inset-0 z-[999999] flex flex-col items-center justify-center bg-black text-white px-6 text-center"
      role="alert"
      aria-live="assertive"
    >
      <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-full border border-red-400/40 bg-red-500/10 text-red-300">
        <ShieldAlert size={34} />
      </div>
      <p className="text-xs font-black uppercase tracking-[0.35em] text-red-300">
        Screenshot Protected
      </p>
      <h2 className="mt-4 text-2xl font-black sm:text-4xl">{displayName}</h2>
      <p className="mt-3 max-w-md text-sm font-semibold text-white/55">
        This screen is protected to reduce unauthorized capture and data leakage.
      </p>
    </div>
  );
};

export default ScreenshotProtectionOverlay;
