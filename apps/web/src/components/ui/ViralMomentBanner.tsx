import { motion } from "framer-motion";
import { Flame, MessageCircle, PartyPopper, X } from "lucide-react";

type ViralMomentBannerProps = {
  type?: "viral" | "milestone" | "victory";
  title: string;
  message: string;
  postId?: number;
  onClose?: () => void;
};

const ViralMomentBanner = ({ type = "milestone", title, message, postId, onClose }: ViralMomentBannerProps) => {
  const isVictory = type === "victory";
  const Icon = isVictory ? PartyPopper : Flame;
  const shareText = `${title}\n${message}${postId ? `\n${window.location.origin}/post/${postId}` : ""}`;

  return (
    <motion.div
      initial={{ opacity: 0, y: -18, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -18, scale: 0.98 }}
      className={`rounded-xl border p-3 shadow-xl ${isVictory ? "border-blue-500/25 bg-blue-600 text-white" : "border-orange-500/25 bg-orange-600 text-white"}`}
    >
      <div className="flex items-start gap-3">
        <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/15">
          <Icon size={18} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-black">{title}</p>
          <p className="text-xs text-white/80">{message}</p>
          <button
            type="button"
            onClick={() => window.open(`https://wa.me/?text=${encodeURIComponent(shareText)}`, "_blank", "noopener,noreferrer")}
            className="mt-2 inline-flex items-center gap-1.5 rounded-lg bg-white/15 px-2.5 py-1 text-[11px] font-bold hover:bg-white/25"
          >
            <MessageCircle size={12} /> Share to WhatsApp
          </button>
        </div>
        {onClose && (
          <button type="button" onClick={onClose} className="rounded-lg p-1 hover:bg-white/15" title="Dismiss">
            <X size={14} />
          </button>
        )}
      </div>
    </motion.div>
  );
};

export default ViralMomentBanner;
