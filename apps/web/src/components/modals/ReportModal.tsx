import { useState } from "react";
import { createPortal } from "react-dom";
import { motion, AnimatePresence } from "framer-motion";
import {
  Flag,
  X,
  CheckCircle2,
  ShieldAlert,
  Clock,
  UserX,
  EyeOff,
  UserCheck,
  AlertTriangle,
  Flame,
  Check,
  Loader2,
  Shield,
} from "lucide-react";
import axiosInstance from "../../api/axiosConfig";
import { showToast } from "../../utils/toast";
import { parseError } from "../../utils/error-handler";

interface ReportModalProps {
  isOpen: boolean;
  onClose: () => void;
  targetType?: "POST" | "SOCIAL_POST" | "COMMENT";
  targetId?: number;
  reportedType?: "POST" | "SOCIAL_POST" | "COMMENT";
  reportedId?: number;
  reportedName?: string;
}

interface CategoryOption {
  value: string;
  label: string;
  description: string;
  isEmergency: boolean;
  icon: React.ComponentType<{ size?: number; className?: string }>;
}

const CATEGORY_OPTIONS: CategoryOption[] = [
  { value: "HARASSMENT",       label: "Harassment & Abuse",   description: "Defamation, bullying, or insults.",       isEmergency: true,  icon: UserX },
  { value: "OBSCENITY",        label: "Obscenity & Privacy",  description: "Sexually explicit content or privacy.",   isEmergency: true,  icon: EyeOff },
  { value: "IMPERSONATION",    label: "Impersonation",        description: "Fake profile or identity theft.",         isEmergency: true,  icon: UserCheck },
  { value: "NATIONAL_SECURITY",label: "National Security",    description: "Threats to defense or sovereignty.",      isEmergency: true,  icon: ShieldAlert },
  { value: "MISINFORMATION",   label: "Misinformation",       description: "Deceptive or synthetic fake news.",       isEmergency: false, icon: AlertTriangle },
  { value: "HATE_SPEECH",      label: "Hate & Incitement",    description: "Promoting hatred or disorder.",           isEmergency: false, icon: Flame },
];

const ReportModal = ({
  isOpen,
  onClose,
  targetType: propTargetType,
  targetId: propTargetId,
  reportedType,
  reportedId,
  reportedName,
}: ReportModalProps) => {
  const [selectedCategory, setSelectedCategory] = useState<string>("");
  const [description, setDescription] = useState<string>("");
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [submitted, setSubmitted] = useState<boolean>(false);
  const [successMessage, setSuccessMessage] = useState<string>("");

  const resolvedTargetType = propTargetType || reportedType || "POST";
  const resolvedTargetId = propTargetId || reportedId || 0;
  const selectedOption = CATEGORY_OPTIONS.find((c) => c.value === selectedCategory);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedCategory) { showToast.error("Please select a violation category"); return; }
    setSubmitting(true);
    try {
      const response = await axiosInstance.post("/api/reports", {
        targetType: resolvedTargetType,
        targetId: resolvedTargetId,
        category: selectedCategory,
        description: description.trim() || undefined,
      });
      const message = response.data?.data ?? response.data?.message ?? "Report submitted successfully to the moderation team.";
      setSuccessMessage(message);
      setSubmitted(true);
      showToast.success("Report filed successfully");
    } catch (err: any) {
      console.error("Failed to submit report:", err);
      showToast.error(parseError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleClose = () => {
    onClose();
    setTimeout(() => { setSelectedCategory(""); setDescription(""); setSubmitted(false); setSuccessMessage(""); }, 250);
  };

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center p-3 sm:p-5 select-none">
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="fixed inset-0 bg-black/70 backdrop-blur-xs"
            onClick={handleClose}
          />

          {/* Modal Container */}
          <motion.div
            initial={{ opacity: 0, scale: 0.96, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 10 }}
            transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
            className="relative w-full max-w-xl max-h-[92vh] flex flex-col overflow-hidden rounded-2xl sm:rounded-3xl border border-black/10 dark:border-white/10 bg-base-100 shadow-2xl z-10"
            onClick={(e) => e.stopPropagation()}
          >
            {/* ── Header ── */}
            <div className="px-5 py-4 sm:px-6 sm:py-5 border-b border-black/5 dark:border-white/10 flex items-start justify-between shrink-0 bg-base-100">
              <div className="flex items-start gap-3.5 min-w-0">
                <div className="w-10 h-10 rounded-xl bg-red-500/10 text-red-500 flex items-center justify-center shrink-0 border border-red-500/20">
                  <ShieldAlert className="w-5 h-5" />
                </div>
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h2 className="text-base sm:text-lg font-bold text-base-content tracking-tight">
                      Report Content
                    </h2>
                    <span className="inline-flex items-center text-[9px] font-extrabold uppercase tracking-wider px-2 py-0.5 rounded-md bg-[#1D4ED8] text-white">
                      IT RULES 2021
                    </span>
                  </div>
                  <p className="text-xs text-base-content/65 font-normal mt-0.5 line-clamp-1 sm:line-clamp-none">
                    {reportedName
                      ? `Reporting content from @${reportedName}`
                      : "Help us keep the community safe and compliant with statutory guidelines."}
                  </p>
                </div>
              </div>

              <button
                onClick={handleClose}
                className="text-base-content/40 hover:text-base-content p-1.5 rounded-lg hover:bg-base-200 dark:hover:bg-white/10 transition-colors cursor-pointer shrink-0 ml-2"
                title="Close"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* ── Body Content ── */}
            <div className="p-4 sm:p-6 flex-1 overflow-y-auto custom-scrollbar">
              {submitted ? (
                <motion.div
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="py-10 text-center space-y-4 max-w-md mx-auto"
                >
                  <div className="mx-auto w-14 h-14 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-500">
                    <CheckCircle2 size={30} />
                  </div>
                  <h3 className="text-base sm:text-lg font-bold text-base-content">
                    Report Submitted
                  </h3>
                  <p className="text-xs text-base-content/65 leading-relaxed">
                    {successMessage}
                  </p>
                  <div className="pt-2">
                    <button
                      onClick={handleClose}
                      className="rounded-xl bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 text-white font-semibold text-xs px-6 py-2.5 transition-all active:scale-98 cursor-pointer shadow-xs"
                    >
                      Done
                    </button>
                  </div>
                </motion.div>
              ) : (
                <form id="report-modal-form" onSubmit={handleSubmit} className="space-y-4">
                  {/* Category Header Row */}
                  <div className="space-y-2">
                    <div className="flex items-center justify-between px-0.5">
                      <label className="text-[11px] font-bold text-base-content/60 uppercase tracking-wider">
                        Select Violation Category
                      </label>
                      <span className="text-[9px] font-semibold text-base-content/50 bg-base-200/60 dark:bg-white/5 px-2 py-0.5 rounded-md border border-black/5 dark:border-white/5">
                        <strong className="text-base-content/70">SLA</strong> — Resolution Timeframe
                      </span>
                    </div>

                    {/* Category Selection Cards */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 sm:gap-2.5">
                      {CATEGORY_OPTIONS.map((option) => {
                        const IconComponent = option.icon;
                        const isSelected = selectedCategory === option.value;
                        return (
                          <div
                            key={option.value}
                            onClick={() => setSelectedCategory(option.value)}
                            className={`group relative flex flex-col justify-between p-3 rounded-xl border transition-all duration-150 cursor-pointer select-none ${
                              isSelected
                                ? "bg-[#1D4ED8]/10 border-[#1D4ED8] dark:border-[#1D4ED8] shadow-xs"
                                : "bg-base-200/30 dark:bg-white/[0.03] border-black/5 dark:border-white/10 hover:border-black/15 dark:hover:border-white/20 hover:bg-base-200/60 dark:hover:bg-white/[0.06]"
                            }`}
                          >
                            <div className="flex items-start justify-between gap-2">
                              <div className="flex items-center gap-2.5 min-w-0">
                                <div
                                  className={`w-7 h-7 rounded-lg flex items-center justify-center shrink-0 transition-colors ${
                                    isSelected
                                      ? "bg-[#1D4ED8] text-white"
                                      : option.isEmergency
                                      ? "bg-red-500/10 text-red-500 dark:text-red-400 border border-red-500/20"
                                      : "bg-base-200 text-base-content/60 dark:bg-white/10 dark:text-white/60"
                                  }`}
                                >
                                  <IconComponent size={13} />
                                </div>
                                <div className="min-w-0">
                                  <h4 className={`text-xs font-bold leading-tight tracking-tight ${
                                    isSelected ? "text-[#1D4ED8] dark:text-blue-400" : "text-base-content"
                                  }`}>
                                    {option.label}
                                  </h4>
                                </div>
                              </div>

                              <div className="flex items-center gap-1.5 shrink-0">
                                {option.isEmergency ? (
                                  <span className="inline-flex text-[8px] font-black uppercase px-1.5 py-0.5 rounded-md bg-red-500/10 text-red-600 dark:text-red-400 border border-red-500/20">
                                    24H SLA
                                  </span>
                                ) : (
                                  <span className="inline-flex text-[8px] font-black uppercase px-1.5 py-0.5 rounded-md bg-base-200 text-base-content/50 dark:bg-white/5 dark:text-white/40 border border-black/5 dark:border-white/5">
                                    15D SLA
                                  </span>
                                )}

                                {/* Radio dot */}
                                <div className={`w-3.5 h-3.5 rounded-full border flex items-center justify-center transition-all ${
                                  isSelected
                                    ? "bg-[#1D4ED8] border-[#1D4ED8]"
                                    : "border-black/20 dark:border-white/20 bg-transparent"
                                }`}>
                                  {isSelected && <Check className="w-2.5 h-2.5 text-white" strokeWidth={3} />}
                                </div>
                              </div>
                            </div>

                            <p className={`text-[10px] sm:text-[11px] font-medium leading-relaxed mt-2 line-clamp-2 ${
                              isSelected ? "text-base-content/85" : "text-base-content/55"
                            }`}>
                              {option.description}
                            </p>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  {/* Dynamic SLA info banner */}
                  {selectedOption && (
                    <motion.div
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: "auto" }}
                      className="flex items-start gap-2.5 p-3 rounded-xl bg-[#1D4ED8]/10 border border-[#1D4ED8]/25 text-[#1D4ED8] dark:text-blue-300"
                    >
                      {selectedOption.isEmergency ? (
                        <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5 text-red-500" />
                      ) : (
                        <Clock className="w-4 h-4 shrink-0 mt-0.5 text-[#1D4ED8] dark:text-blue-400" />
                      )}
                      <div className="text-[11px] leading-relaxed text-base-content/80">
                        {selectedOption.isEmergency ? (
                          <span>
                            <strong className="font-bold text-red-600 dark:text-red-400">24-Hour Statutory SLA:</strong> Under IT Rules 2021, reports in this category are fast-tracked and prioritized for review by our Grievance Officer within <strong className="text-base-content">24 hours</strong>.
                          </span>
                        ) : (
                          <span>
                            <strong className="font-bold text-[#1D4ED8] dark:text-blue-400">15-Day Statutory SLA:</strong> Standard grievances are acknowledged within 24 hours and addressed within <strong className="text-base-content">15 business days</strong>.
                          </span>
                        )}
                      </div>
                    </motion.div>
                  )}

                  {/* Additional Context Textarea */}
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between px-0.5">
                      <label className="text-[11px] font-semibold text-base-content/70">
                        Additional Context (Optional)
                      </label>
                      <span className="text-[10px] text-base-content/40 font-mono">
                        {description.length}/1000
                      </span>
                    </div>
                    <textarea
                      rows={2}
                      maxLength={1000}
                      placeholder="Add details, URLs, or context to help grievance officer assess quickly…"
                      className="w-full rounded-xl bg-base-200/40 dark:bg-white/[0.04] border border-black/10 dark:border-white/10 focus:border-[#1D4ED8] focus:outline-none focus:ring-1 focus:ring-[#1D4ED8]/30 text-xs font-medium text-base-content placeholder-base-content/30 transition-all resize-none leading-relaxed p-3 min-h-[58px]"
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                    />
                  </div>
                </form>
              )}
            </div>

            {/* ── Footer Actions ── */}
            <div className="px-5 py-3.5 sm:px-6 border-t border-black/5 dark:border-white/10 bg-base-200/40 dark:bg-base-200/20 flex items-center justify-between gap-3 shrink-0">
              <div className="flex items-center gap-1 text-[9px] text-base-content/40 font-bold uppercase tracking-wider hidden xs:flex">
                <Shield size={11} className="text-[#1D4ED8]" />
                <span>Statutory Compliance • Govlyx</span>
              </div>

              <div className="flex items-center gap-2.5 w-full xs:w-auto justify-end">
                <button
                  type="button"
                  onClick={handleClose}
                  className="px-4 py-2 rounded-xl text-xs font-medium text-base-content/70 hover:text-base-content hover:bg-base-200 dark:hover:bg-white/10 transition-colors cursor-pointer border border-transparent dark:border-white/5"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  form="report-modal-form"
                  disabled={submitting || !selectedCategory || submitted}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-white bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 active:bg-[#1e40af] transition-all shadow-xs flex items-center justify-center gap-1.5 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed active:scale-98"
                >
                  {submitting ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>Filing report…</span>
                    </>
                  ) : (
                    <>
                      <Flag className="w-3 h-3 fill-white" />
                      <span>Submit Report</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  );
};

export default ReportModal;
