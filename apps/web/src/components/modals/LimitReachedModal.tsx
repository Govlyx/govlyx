import { createPortal } from 'react-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { AlertTriangle, X } from 'lucide-react';

interface LimitReachedModalProps {
  isOpen: boolean;
  onClose: () => void;
  onUpgrade: () => void;
  title?: string;
  message: string;
}

export default function LimitReachedModal({
  isOpen,
  onClose,
  onUpgrade,
  title = 'Limit Reached',
  message,
}: LimitReachedModalProps) {
  return null; // MONETIZATION DISABLED
  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{ scale: 0.95, y: 15, opacity: 0 }}
            animate={{ scale: 1, y: 0, opacity: 1 }}
            exit={{ scale: 0.95, y: 15, opacity: 0 }}
            transition={{ type: 'spring', duration: 0.4, bounce: 0.1 }}
            className="w-full max-w-sm rounded-[2rem] border border-black/10 dark:border-white/15 bg-base-100 p-6 shadow-2xl text-center relative flex flex-col items-center"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Close Button */}
            <button
              onClick={onClose}
              className="absolute top-5 right-5 text-base-content/40 hover:text-base-content transition-colors duration-200 cursor-pointer"
              title="Close modal"
            >
              <X size={18} />
            </button>

            {/* Warning Circle */}
            <div className="w-12 h-12 rounded-full flex items-center justify-center mb-4 bg-amber-500/10 text-amber-500">
              <AlertTriangle size={24} />
            </div>

            {/* Content */}
            <h3 className="text-xl font-extrabold text-base-content mb-2 tracking-tight">
              {title}
            </h3>
            <p className="text-xs text-base-content/60 leading-relaxed mb-6">
              {message}
            </p>

            {/* Actions */}
            <div className="flex flex-col gap-2 w-full">
              <button
                onClick={() => {
                  onClose();
                  onUpgrade();
                }}
                className="btn btn-sm w-full bg-[#1D4ED8] hover:bg-blue-800 text-white border-none rounded-xl py-2 flex items-center justify-center gap-1.5 text-xs font-semibold shadow-sm transition-all duration-200 cursor-pointer animate-none"
              >
                Buy a Plan
              </button>
              <button
                onClick={onClose}
                className="btn btn-sm btn-ghost w-full rounded-xl text-xs font-semibold cursor-pointer"
              >
                Close
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  );
}
