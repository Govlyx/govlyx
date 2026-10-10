import React, { useEffect, useState } from "react";
import { toast } from "react-hot-toast";
import type { Toast } from "react-hot-toast";
import { Check, AlertCircle, Info, X } from "lucide-react";

export type ToastType = "success" | "error" | "info";

export interface ToastOptions {
  duration?: number;
  subtext?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
}

interface ToastCardProps {
  t: Toast;
  msg: string;
  type: ToastType;
  duration: number;
  subtext?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
}

const ToastCard: React.FC<ToastCardProps> = ({
  t,
  msg,
  type,
  duration,
  subtext,
  action,
}) => {
  // Compute seconds remaining from duration
  const totalSeconds = Math.max(1, Math.ceil(duration / 1000));
  const [secondsRemaining, setSecondsRemaining] = useState(totalSeconds);

  useEffect(() => {
    const startTime = Date.now();
    const interval = setInterval(() => {
      const elapsed = Date.now() - startTime;
      const remainingMs = Math.max(0, duration - elapsed);
      const remainingSec = Math.ceil(remainingMs / 1000);
      setSecondsRemaining(remainingSec);
      if (remainingMs <= 0) {
        clearInterval(interval);
      }
    }, 200);

    return () => clearInterval(interval);
  }, [duration]);

  const isSuccess = type === "success";
  const isError = type === "error";

  return (
    <div
      role="alert"
      aria-live="polite"
      className={`${
        t.visible ? "opacity-100 translate-y-0 scale-100" : "opacity-0 translate-y-2 scale-95"
      } pointer-events-auto relative flex items-center gap-3 py-2.5 px-3.5 sm:px-4 rounded-2xl sm:rounded-[20px] bg-white dark:bg-[#18191b] text-slate-900 dark:text-white border border-slate-200/90 dark:border-white/10 shadow-[0_8px_30px_rgb(0,0,0,0.12)] dark:shadow-[0_12px_40px_rgb(0,0,0,0.6)] transition-all duration-200 ease-out w-full sm:w-[320px] max-w-full select-none text-left`}
    >
      {/* Icon Badge Container */}
      <div className="shrink-0 flex items-center justify-center">
        {isSuccess && (
          <div className="w-8 h-8 rounded-xl flex items-center justify-center bg-blue-50 dark:bg-[#1D4ED8]/15 border border-[#1D4ED8]/30 text-[#1D4ED8] shadow-xs">
            <div className="w-5 h-5 rounded-full border-[1.75px] border-[#1D4ED8] flex items-center justify-center">
              <Check size={11} className="stroke-[3] text-[#1D4ED8]" />
            </div>
          </div>
        )}

        {isError && (
          <div className="w-8 h-8 rounded-xl flex items-center justify-center bg-red-50 dark:bg-red-500/15 border border-red-200/70 dark:border-red-500/30 text-red-600 dark:text-red-400 shadow-xs">
            <div className="w-5 h-5 rounded-full border-[1.75px] border-red-600 dark:border-red-400 flex items-center justify-center">
              <AlertCircle size={12} className="stroke-[2.5]" />
            </div>
          </div>
        )}

        {!isSuccess && !isError && (
          <div className="w-8 h-8 rounded-xl flex items-center justify-center bg-blue-50 dark:bg-[#1D4ED8]/15 border border-[#1D4ED8]/30 text-[#1D4ED8] shadow-xs">
            <div className="w-5 h-5 rounded-full border-[1.75px] border-[#1D4ED8] flex items-center justify-center">
              <Info size={11} className="stroke-[2.5] text-[#1D4ED8]" />
            </div>
          </div>
        )}
      </div>

      {/* Message & optional subtext */}
      <div className="flex-1 min-w-0 py-0.5">
        <p className="text-[13px] sm:text-sm font-bold leading-snug text-slate-900 dark:text-white tracking-tight break-words">
          {msg}
        </p>
        {subtext && (
          <p className="text-[11px] sm:text-xs text-slate-500 dark:text-slate-400 mt-0.5 font-medium leading-normal">
            {subtext}
          </p>
        )}
      </div>

      {/* Optional action button (e.g. "Undo") */}
      {action && (
        <button
          type="button"
          onClick={() => {
            action.onClick();
            toast.dismiss(t.id);
          }}
          className="px-2.5 py-1 text-xs font-bold rounded-lg bg-slate-100 hover:bg-slate-200 dark:bg-white/10 dark:hover:bg-white/15 text-slate-800 dark:text-white border border-black/5 dark:border-white/10 transition-all active:scale-95 shrink-0 cursor-pointer"
        >
          {action.label}
        </button>
      )}

      {/* Countdown Timer Badge */}
      <div
        title={`${secondsRemaining}s until auto-dismiss`}
        className="w-5 h-5 rounded-full bg-slate-100 dark:bg-white/10 flex items-center justify-center shrink-0 border border-black/5 dark:border-white/5"
      >
        <span className="text-[10px] font-black text-slate-500 dark:text-slate-400 tabular-nums">
          {secondsRemaining}
        </span>
      </div>

      {/* Manual close button */}
      <button
        type="button"
        onClick={() => toast.dismiss(t.id)}
        className="p-1 rounded-lg text-slate-400 hover:text-slate-700 dark:text-slate-400 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-white/10 transition-colors shrink-0 cursor-pointer outline-none focus:outline-none"
        aria-label="Close notification"
      >
        <X size={14} className="stroke-[2.2]" />
      </button>
    </div>
  );
};

export const showToast = {
  success: (msg: string, durationOrOptions: number | ToastOptions = 3000) => {
    const opts: ToastOptions =
      typeof durationOrOptions === "number"
        ? { duration: durationOrOptions }
        : durationOrOptions;
    const duration = opts.duration ?? 3000;

    toast.custom(
      (t) => (
        <ToastCard
          t={t}
          msg={msg}
          type="success"
          duration={duration}
          subtext={opts.subtext}
          action={opts.action}
        />
      ),
      {
        duration,
        id: msg,
      }
    );
  },

  error: (msg: string, durationOrOptions: number | ToastOptions = 3000) => {
    const opts: ToastOptions =
      typeof durationOrOptions === "number"
        ? { duration: durationOrOptions }
        : durationOrOptions;
    const duration = opts.duration ?? 3000;

    toast.custom(
      (t) => (
        <ToastCard
          t={t}
          msg={msg}
          type="error"
          duration={duration}
          subtext={opts.subtext}
          action={opts.action}
        />
      ),
      {
        duration,
        id: msg,
      }
    );
  },

  info: (msg: string, durationOrOptions: number | ToastOptions = 3000) => {
    const opts: ToastOptions =
      typeof durationOrOptions === "number"
        ? { duration: durationOrOptions }
        : durationOrOptions;
    const duration = opts.duration ?? 3000;

    toast.custom(
      (t) => (
        <ToastCard
          t={t}
          msg={msg}
          type="info"
          duration={duration}
          subtext={opts.subtext}
          action={opts.action}
        />
      ),
      {
        duration,
        id: msg,
      }
    );
  },

  dismiss: (id?: string) => {
    if (id) toast.dismiss(id);
    else toast.dismiss();
  },
};
