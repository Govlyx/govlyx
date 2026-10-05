import { createPortal } from 'react-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Loader2, X } from 'lucide-react';

interface ConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  isDanger?: boolean;
  isLoading?: boolean;
  isCancelSuccess?: boolean;
  icon?: React.ReactNode;
  showCloseButton?: boolean;
}

export default function ConfirmModal({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = 'Continue',
  cancelLabel = 'Cancel',
  isDanger = true,
  isLoading = false,
  isCancelSuccess = false,
  icon,
  showCloseButton = false,
}: ConfirmModalProps) {
  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center p-4 select-none">
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="fixed inset-0 bg-black/65 backdrop-blur-xs"
            onClick={onClose}
          />

          {/* Dialog Card */}
          <motion.div
            initial={{ scale: 0.96, opacity: 0, y: 8 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            exit={{ scale: 0.96, opacity: 0, y: 8 }}
            transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
            className="relative w-full max-w-md overflow-hidden rounded-2xl border border-black/10 dark:border-white/10 bg-base-100 shadow-2xl z-10"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Optional Close Button */}
            {showCloseButton && (
              <button
                onClick={onClose}
                className="absolute top-4 right-4 text-base-content/40 hover:text-base-content transition-colors duration-150 cursor-pointer p-1 rounded-lg"
                title="Close modal"
              >
                <X size={15} />
              </button>
            )}

            {/* Header & Body Content */}
            <div className="p-6 sm:p-7 text-left">
              {icon && <div className="mb-4 flex items-center">{icon}</div>}
              <h3 className="text-base sm:text-lg font-bold text-base-content tracking-tight">
                {title}
              </h3>
              <p className="text-xs sm:text-sm font-normal text-base-content/65 mt-2 leading-relaxed">
                {message}
              </p>
            </div>

            {/* Footer Actions */}
            <div className="flex items-center justify-end gap-2.5 px-6 py-4 border-t border-black/5 dark:border-white/5 bg-base-200/40 dark:bg-base-200/20">
              <button
                type="button"
                disabled={isLoading}
                onClick={onClose}
                className={
                  isCancelSuccess
                    ? 'px-4 py-2 rounded-xl text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 transition-colors shadow-xs cursor-pointer'
                    : 'px-4 py-2 rounded-xl text-xs font-medium text-base-content/70 hover:text-base-content hover:bg-base-200 dark:hover:bg-white/10 transition-colors cursor-pointer border border-transparent dark:border-white/5'
                }
              >
                {cancelLabel}
              </button>
              <button
                type="button"
                disabled={isLoading}
                onClick={onConfirm}
                className={`px-4 py-2 rounded-xl text-xs font-semibold text-white transition-all shadow-xs flex items-center justify-center gap-1.5 cursor-pointer active:scale-98 ${
                  isDanger
                    ? 'bg-red-600 hover:bg-red-700 active:bg-red-800 disabled:bg-red-600/50'
                    : 'bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 active:bg-[#1e40af] disabled:bg-[#1D4ED8]/50'
                }`}
              >
                {isLoading ? (
                  <Loader2 size={13} className="animate-spin" />
                ) : (
                  confirmLabel
                )}
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  );
}
