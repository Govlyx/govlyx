import React from "react";
import { ArrowUp } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";

export interface NewPostsBannerProps {
  count: number;
  onTap: () => void;
  className?: string;
  topOffset?: string;
}

export const NewPostsBanner: React.FC<NewPostsBannerProps> = ({
  count,
  onTap,
  className = "",
  topOffset = "4.25rem",
}) => {
  if (count <= 0) return null;

  const label = count > 9 ? "9+ new posts" : `${count} ${count === 1 ? "new post" : "new posts"}`;

  const handleClick = () => {
    const mainEl = document.querySelector("main.overflow-y-auto");
    if (mainEl) {
      mainEl.scrollTo({ top: 0, behavior: "smooth" });
    } else {
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
    onTap();
  };

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0, y: -20, scale: 0.95 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: -20, scale: 0.95 }}
        transition={{ duration: 0.25, ease: "easeOut" }}
        style={{ top: topOffset }}
        className={`sticky z-40 flex justify-center py-2 pointer-events-none ${className}`}
      >
        <button
          onClick={handleClick}
          type="button"
          className="pointer-events-auto flex items-center gap-2 px-4 py-2 bg-primary hover:bg-primary-dark text-primary-content font-medium text-sm rounded-full shadow-lg hover:shadow-xl transition-all transform hover:-translate-y-0.5 active:translate-y-0 cursor-pointer border border-white/20 dark:border-white/10"
        >
          <ArrowUp size={16} className="animate-bounce" />
          <span>{label} — tap to refresh</span>
        </button>
      </motion.div>
    </AnimatePresence>
  );
};

export default NewPostsBanner;
