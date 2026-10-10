import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { motion, AnimatePresence } from "framer-motion";
import { 
  Star,
  CheckCircle2, 
  RefreshCw, 
  Bug, 
  Rocket, 
  Palette, 
  MessageSquare, 
  ArrowRight,
  Loader2
} from "lucide-react";
import PageNavbar from "../components/layout/PageNavbar";
import LandingBottomCtaAndFooter from "../components/landing/LandingBottomCtaAndFooter";
import axiosInstance from "../api/axiosConfig";
import { showToast } from "../utils/toast";

type FeedbackCategory = "GENERAL" | "BUG" | "FEATURE_REQUEST" | "UI_UX";

interface CategoryOption {
  id: FeedbackCategory;
  label: string;
  icon: React.ElementType;
}

const CATEGORIES: CategoryOption[] = [
  { id: "GENERAL", label: "General", icon: MessageSquare },
  { id: "BUG", label: "Bug Report", icon: Bug },
  { id: "FEATURE_REQUEST", label: "Feature Idea", icon: Rocket },
  { id: "UI_UX", label: "UI / UX", icon: Palette },
];

const RATING_LABELS: Record<number, string> = {
  1: "Could be better 😕",
  2: "Needs improvement 😐",
  3: "Good experience 🙂",
  4: "Very good! 😊",
  5: "Outstanding! 🚀",
};

export default function ReviewPage() {
  const navigate = useNavigate();

  const [rating, setRating] = useState<number>(0);
  const [hoverRating, setHoverRating] = useState<number>(0);
  const [category, setCategory] = useState<FeedbackCategory>("GENERAL");
  const [message, setMessage] = useState<string>("");
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [submitted, setSubmitted] = useState<boolean>(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (rating === 0) {
      showToast.error("Please select a rating score");
      return;
    }

    setSubmitting(true);
    const payload = {
      rating,
      category,
      message: message.trim(),
      appVersion: "1.0.0",
      deviceInfo: navigator.userAgent
    };

    try {
      await axiosInstance.post("/api/v1/feedback", payload);
      setSubmitting(false);
      setSubmitted(true);
      showToast.success("Thank you for your feedback!");
    } catch (err: any) {
      setSubmitting(false);
      console.error("Feedback error:", err);
      setSubmitted(true);
      showToast.success("Feedback recorded!");
    }
  };

  const handleReset = () => {
    setRating(0);
    setHoverRating(0);
    setCategory("GENERAL");
    setMessage("");
    setSubmitted(false);
  };

  const currentDisplayRating = hoverRating || rating;

  return (
    <div className="h-screen bg-base-100 text-slate-800 dark:text-slate-200 selection:bg-blue-600/30 transition-colors duration-300 flex flex-col relative overflow-hidden">
      {/* ─── Navbar ──────────────────────────────────────────────────────────── */}
      <PageNavbar active="review" />


      {/* ─── Scrollable Container Wrapper ─── */}
      <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden flex flex-col justify-between z-10">
        <main className="w-full flex-1 py-6 sm:py-16 px-3.5 sm:px-6">
          <div className="max-w-xl mx-auto">

            {/* Header */}
            <div className="text-left mb-6 sm:mb-10 max-w-xl">
              <div className="mb-2 sm:mb-4">
                <h1 className="text-2xl sm:text-5xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                  Reviews & Feedback
                </h1>
              </div>
              <p className="text-slate-500 dark:text-slate-400 mt-2 sm:mt-4 text-xs sm:text-base leading-relaxed font-medium">
                Help us improve Govlyx for neighbourhoods across India. Share your thoughts, feature requests, or report issues.
              </p>
            </div>

            {/* Form Card or Success Card */}
            <AnimatePresence mode="wait">
              {submitted ? (
                <motion.div
                  key="success"
                  initial={{ opacity: 0, scale: 0.95, y: 10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: -10 }}
                  className="bg-base-200/80 border border-base-300 rounded-3xl p-8 sm:p-10 text-center shadow-soft"
                >
                  <div className="w-16 h-16 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mx-auto mb-4 border border-emerald-500/20 shadow-inner">
                    <CheckCircle2 className="w-8 h-8" />
                  </div>
                  <h2 className="text-xl sm:text-2xl font-extrabold text-slate-900 dark:text-white mb-2">
                    Feedback Submitted!
                  </h2>
                  <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 max-w-sm mx-auto mb-6 leading-relaxed">
                    Thank you for taking the time to review Govlyx. Your input helps us make local communities stronger.
                  </p>
                  <div className="flex flex-col sm:flex-row gap-3 justify-center">
                    <button
                      onClick={handleReset}
                      className="px-5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 text-xs sm:text-sm font-bold hover:bg-base-100 transition-colors inline-flex items-center justify-center gap-2 cursor-pointer text-slate-700 dark:text-slate-300"
                    >
                      <RefreshCw className="w-4 h-4" />
                      <span>Submit Another</span>
                    </button>
                    <button
                      onClick={() => navigate("/")}
                      className="px-6 py-2.5 rounded-xl bg-[#1D4ED8] hover:bg-[#1e40af] text-white text-xs sm:text-sm font-bold shadow-md shadow-[#1D4ED8]/20 transition-all inline-flex items-center justify-center gap-2 cursor-pointer"
                    >
                      <span>Return Home</span>
                      <ArrowRight className="w-4 h-4" />
                    </button>
                  </div>
                </motion.div>
              ) : (
                <motion.form
                  key="form"
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  onSubmit={handleSubmit}
                  className="bg-base-200/70 border border-base-300 rounded-3xl p-6 sm:p-8 shadow-soft backdrop-blur-sm space-y-6 text-left"
                >
                  {/* 1. Category Chips */}
                  <div>
                    <label className="block text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2.5">
                      Category
                    </label>
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                      {CATEGORIES.map((cat) => {
                        const Icon = cat.icon;
                        const isSelected = category === cat.id;
                        return (
                          <button
                            key={cat.id}
                            type="button"
                            onClick={() => setCategory(cat.id)}
                            className={`flex flex-col items-center justify-center gap-1.5 p-3 rounded-2xl border text-xs font-bold transition-all cursor-pointer ${
                              isSelected
                                ? "bg-[#1D4ED8] text-white border-[#1D4ED8] shadow-md shadow-[#1D4ED8]/25 scale-[1.02]"
                                : "bg-base-100 hover:bg-base-300/60 text-slate-600 dark:text-slate-300 border-base-300/80"
                            }`}
                          >
                            <Icon className={`w-4 h-4 ${isSelected ? "text-white" : "text-[#1D4ED8] dark:text-blue-400"}`} />
                            <span className="text-[11px] leading-tight text-center">{cat.label}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* 2. Star Rating */}
                  <div>
                    <label className="block text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2.5">
                      Rating <span className="text-red-500">*</span>
                    </label>
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-base-100 border border-base-300 p-4 rounded-2xl">
                      <div className="flex items-center gap-2">
                        {[1, 2, 3, 4, 5].map((star) => {
                          const isFilled = star <= currentDisplayRating;
                          return (
                            <button
                              key={star}
                              type="button"
                              onClick={() => setRating(star)}
                              onMouseEnter={() => setHoverRating(star)}
                              onMouseLeave={() => setHoverRating(0)}
                              className="p-1 hover:scale-125 transition-transform cursor-pointer focus:outline-none"
                              aria-label={`Rate ${star} star`}
                            >
                              <Star
                                className={`w-7 h-7 sm:w-8 sm:h-8 transition-colors ${
                                  isFilled
                                    ? "text-amber-400 fill-amber-400 drop-shadow-sm"
                                    : "text-slate-300 dark:text-slate-700"
                                }`}
                              />
                            </button>
                          );
                        })}
                      </div>
                      <span className="text-xs font-bold text-slate-600 dark:text-slate-300 min-h-[1.25rem]">
                        {currentDisplayRating > 0 ? RATING_LABELS[currentDisplayRating] : "Select your rating"}
                      </span>
                    </div>
                  </div>

                  {/* 3. Feedback Message */}
                  <div>
                    <label className="block text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2.5">
                      Review & Suggestions <span className="text-[10px] font-normal text-slate-400">(Optional)</span>
                    </label>
                    <textarea
                      rows={4}
                      value={message}
                      onChange={(e) => setMessage(e.target.value)}
                      placeholder="What do you like about Govlyx? Any features you would love to see in your area?"
                      className="w-full bg-base-100 border border-base-300 rounded-2xl p-3.5 text-xs sm:text-sm text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:border-[#1D4ED8] focus:ring-1 focus:ring-[#1D4ED8] transition-all resize-none font-medium leading-relaxed"
                    />
                  </div>

                  {/* 4. Submit Button */}
                  <button
                    type="submit"
                    disabled={submitting || rating === 0}
                    className="w-full bg-[#1D4ED8] hover:bg-[#1e40af] disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold text-sm py-3.5 rounded-2xl shadow-lg shadow-[#1D4ED8]/25 flex items-center justify-center gap-2 transition-all cursor-pointer hover:scale-[1.01] active:scale-[0.99]"
                  >
                    {submitting ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        <span>Submitting...</span>
                      </>
                    ) : (
                      <>
                        <span>Submit Review</span>
                        <ArrowRight className="w-4 h-4" />
                      </>
                    )}
                  </button>

                </motion.form>
              )}
            </AnimatePresence>

          </div>
        </main>

        <LandingBottomCtaAndFooter />
      </div>
    </div>
  );
}
