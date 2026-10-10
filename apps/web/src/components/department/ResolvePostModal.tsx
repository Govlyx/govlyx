import { AlertCircle, CheckCircle2, FileText, X } from "lucide-react";
import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { updatePostResolution } from "../../api/departmentService";
import type { TaggedPost } from "../../types/department";

interface Props {
  post: TaggedPost;
  onClose: () => void;
  onResolved: (postId: number) => void;
}

const ResolvePostModal = ({ post, onClose, onResolved }: Props) => {
  const [message, setMessage] = useState("");
  const [status, setStatus] = useState<"idle" | "loading" | "success" | "error">("idle");
  const [errorMsg, setErrorMsg] = useState("");

  const authorName =
    post.citizenDisplayName ?? post.userDisplayName ?? post.citizenUsername ?? post.username ?? "Citizen";
  const authorHandle =
    post.citizenUsername ?? post.username ?? "";

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!message.trim()) return;
    setStatus("loading");
    setErrorMsg("");
    try {
      await updatePostResolution(post.id, true, message.trim());
      setStatus("success");
      setTimeout(() => {
        onResolved(post.id);
        onClose();
      }, 1200);
    } catch (err: unknown) {
      setStatus("error");
      setErrorMsg(err instanceof Error ? err.message : "Failed to resolve post. Please try again.");
    }
  }

  return (
    <AnimatePresence>
      <motion.div
        key="modal-backdrop"
        className="fixed inset-0 z-50 flex items-center justify-center p-2 sm:p-4"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
      >
        {/* Backdrop */}
        <div
          className="absolute inset-0 bg-black/60 backdrop-blur-sm"
          onClick={onClose}
        />

        {/* Panel */}
        <motion.div
          key="modal-panel"
          className="relative z-10 w-full max-w-lg rounded-2xl border border-black/10 dark:border-white/15 bg-base-100 shadow-2xl overflow-hidden max-h-[92vh] flex flex-col"
          initial={{ scale: 0.92, opacity: 0, y: 20 }}
          animate={{ scale: 1, opacity: 1, y: 0 }}
          exit={{ scale: 0.92, opacity: 0, y: 20 }}
          transition={{ type: "spring", stiffness: 300, damping: 30 }}
        >
          {/* Header */}
          <div className="flex items-center justify-between gap-3 border-b border-base-300 px-3.5 sm:px-5 py-2.5 sm:py-3.5 shrink-0">
            <div className="flex items-center gap-2 sm:gap-2.5">
              <span className="flex h-7 w-7 sm:h-8 sm:w-8 items-center justify-center rounded-lg bg-[#1D4ED8]/10">
                <CheckCircle2 size={16} className="text-[#1D4ED8] sm:size-[18px]" />
              </span>
              <div>
                <h2 className="text-xs sm:text-sm font-bold">Mark Issue Resolved</h2>
                <p className="text-[10px] sm:text-xs opacity-50">Issue #{post.id}</p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="flex h-7 w-7 sm:h-8 sm:w-8 items-center justify-center rounded-lg hover:bg-base-200 transition-colors cursor-pointer"
              aria-label="Close"
            >
              <X size={16} className="sm:size-[18px]" />
            </button>
          </div>

          <div className="overflow-y-auto flex-1">
            {/* Citizen Post Preview */}
            <div className="mx-3.5 sm:mx-5 mt-3 sm:mt-5 rounded-xl border border-base-300 bg-base-200/60 p-3 sm:p-4">
              <div className="flex items-center gap-2 mb-2">
                <img
                  src={`https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(authorHandle)}`}
                  alt="Citizen avatar"
                  className="h-7 w-7 sm:h-8 sm:w-8 rounded-full border border-base-300 bg-base-200"
                />
                <div>
                  <p className="text-xs font-semibold leading-none">{authorName}</p>
                  <p className="text-[10px] sm:text-[11px] opacity-40">
                    @{authorHandle}
                    {post.timeAgo ? ` · ${post.timeAgo}` : ""}
                    {post.targetPincode ? ` · ${post.targetPincode}` : ""}
                  </p>
                </div>
              </div>
              <p className="text-xs sm:text-sm leading-relaxed opacity-80 line-clamp-3 sm:line-clamp-4">{post.content}</p>
              {post.issueType && (
                <span className="mt-1.5 sm:mt-2 inline-block rounded-md bg-warning/10 px-1.5 sm:px-2 py-0.5 text-[10px] sm:text-[11px] font-semibold text-warning">
                  {post.issueType}
                </span>
              )}
            </div>

            {/* Divider with preview label */}
            <div className="mx-3.5 sm:mx-5 mt-2.5 sm:mt-4 flex items-center gap-1.5 sm:gap-2">
              <FileText size={13} className="opacity-40 sm:size-3.5" />
              <span className="text-[10px] sm:text-xs font-semibold opacity-40 uppercase tracking-wide">
                Your Official Response
              </span>
            </div>

            {/* Form */}
            <form onSubmit={handleSubmit} className="p-3.5 sm:p-5 pt-2 sm:pt-3 space-y-3 sm:space-y-4">
              <div>
                <textarea
                  id="resolution-message"
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder="Describe the action taken, resolution details, or next steps for the citizen…"
                  className="w-full rounded-xl border border-base-300 bg-base-200 px-3 sm:px-4 py-2 sm:py-3 text-xs sm:text-sm leading-relaxed
                             placeholder:opacity-40 resize-none focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40
                             transition-all min-h-[80px] sm:min-h-[120px]"
                  maxLength={1000}
                  disabled={status === "loading" || status === "success"}
                />
                <div className="flex justify-end">
                  <span className="text-[10px] sm:text-[11px] opacity-30">{message.length}/1000</span>
                </div>
              </div>

              {/* Citizen preview banner */}
              {message.trim() && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: "auto" }}
                  className="rounded-xl border border-[#1D4ED8]/20 bg-[#1D4ED8]/5 px-3 sm:px-4 py-2 sm:py-3"
                >
                  <p className="text-[10px] sm:text-[11px] font-semibold text-[#1D4ED8] mb-0.5 sm:mb-1 uppercase tracking-wide">
                    Citizens will see:
                  </p>
                  <p className="text-[11px] sm:text-xs leading-relaxed opacity-70">"{message.trim()}"</p>
                </motion.div>
              )}

              {/* Error */}
              {status === "error" && (
                <div className="flex items-start gap-2 rounded-xl bg-error/10 border border-error/20 px-3 sm:px-4 py-2 sm:py-3 text-xs sm:text-sm text-error">
                  <AlertCircle size={15} className="shrink-0 mt-0.5 sm:size-4" />
                  <span>{errorMsg}</span>
                </div>
              )}

              {/* Actions */}
              <div className="flex gap-2 sm:gap-3 pt-0.5 sm:pt-1">
                <button
                  type="button"
                  onClick={onClose}
                  disabled={status === "loading" || status === "success"}
                  className="flex-1 rounded-xl border border-base-300 py-2 sm:py-2.5 text-xs sm:text-sm font-semibold
                             hover:bg-base-200 transition-colors disabled:opacity-50 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!message.trim() || status === "loading" || status === "success"}
                  className="flex-1 flex items-center justify-center gap-1.5 sm:gap-2 rounded-xl bg-[#1D4ED8] py-2 sm:py-2.5 text-xs sm:text-sm
                             font-semibold text-white shadow-md hover:bg-[#1e40af] transition-colors
                             disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                >
                  {status === "loading" && (
                    <span className="h-3.5 w-3.5 sm:h-4 sm:w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                  )}
                  {status === "success" ? (
                    <>
                      <CheckCircle2 size={15} className="sm:size-4" /> Resolved!
                    </>
                  ) : status === "loading" ? (
                    "Resolving…"
                  ) : (
                    "Confirm Resolution"
                  )}
                </button>
              </div>
            </form>
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
};

export default ResolvePostModal;
