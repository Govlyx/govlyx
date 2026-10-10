import { useNavigate } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import GovlyxLogo from "../ui/GovlyxLogo";

interface LandingBottomCtaAndFooterProps {
  hideCta?: boolean;
}

export default function LandingBottomCtaAndFooter({ hideCta = false }: LandingBottomCtaAndFooterProps) {
  const navigate = useNavigate();

  return (
    <>
      {/* ─── Bottom CTA Banner ─── */}
      {!hideCta && (
        <section className="py-10 sm:py-16 px-3.5 sm:px-6 bg-gradient-to-r from-[#1D4ED8] to-blue-700 text-white text-center shrink-0">
          <div className="max-w-[700px] mx-auto">
            <h2 className="text-xl sm:text-3xl font-extrabold mb-2 sm:mb-3">
              Ready to Connect with Your Neighbourhood?
            </h2>
            <p className="text-blue-100 text-xs sm:text-sm mb-5 sm:mb-6 leading-relaxed">
              Join thousands of citizens reporting local issues, discovering updates, and chatting anonymously in their pincode.
            </p>
            <button
              onClick={() => navigate("/login")}
              className="bg-white text-[#1D4ED8] hover:bg-blue-50 font-bold text-xs sm:text-sm px-5 sm:px-7 py-2.5 sm:py-3 rounded-lg sm:rounded-xl shadow-xl transition-all cursor-pointer inline-flex items-center gap-2 hover:scale-[1.02] active:scale-[0.98]"
            >
              <span>Get Started with Your Pincode</span>
              <ArrowRight className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
            </button>
          </div>
        </section>
      )}

      {/* ─── Footer ──────────────────────────────────────────────────────────── */}
      <footer className="bg-white dark:bg-slate-950 border-t border-slate-200/80 dark:border-slate-800/80 py-5 sm:py-8 px-3.5 sm:px-6 transition-colors duration-300 shrink-0 pb-[max(env(safe-area-inset-bottom,0px),12px)]">
        <div className="max-w-[1400px] mx-auto flex flex-col md:flex-row justify-between items-center gap-3.5 sm:gap-6 text-center md:text-left">
          <GovlyxLogo showText size={28} textClassName="text-lg sm:text-xl font-bold" />
          
          <div className="flex flex-wrap justify-center gap-2.5 xs:gap-3.5 sm:gap-6 text-[11px] xs:text-xs sm:text-sm text-slate-600 dark:text-slate-400 font-semibold">
            <button onClick={() => navigate("/")} className="hover:text-[#1D4ED8] dark:hover:text-blue-400 transition-colors bg-transparent border-none p-0 cursor-pointer font-semibold">Home</button>
            <button onClick={() => navigate("/how-to-use")} className="hover:text-[#1D4ED8] dark:hover:text-blue-400 transition-colors bg-transparent border-none p-0 cursor-pointer font-semibold">How to Use</button>
            <button onClick={() => navigate("/upcoming-updates")} className="hover:text-[#1D4ED8] dark:hover:text-blue-400 transition-colors bg-transparent border-none p-0 cursor-pointer font-semibold">Upcoming Updates</button>
            <button onClick={() => navigate("/review")} className="hover:text-[#1D4ED8] dark:hover:text-blue-400 transition-colors bg-transparent border-none p-0 cursor-pointer font-semibold">Review</button>
            <button onClick={() => navigate("/privacy-policy")} className="hover:text-[#1D4ED8] dark:hover:text-blue-400 transition-colors bg-transparent border-none p-0 cursor-pointer font-semibold">Privacy Policy</button>
            <button onClick={() => navigate("/copyright-claim")} className="hover:text-[#1D4ED8] dark:hover:text-blue-400 transition-colors bg-transparent border-none p-0 cursor-pointer font-semibold">Copyright</button>
          </div>

          <p className="text-[9px] xs:text-[10px] sm:text-xs text-slate-400 dark:text-slate-500 font-medium">
            © 2026 Govlyx India
          </p>
        </div>
      </footer>
    </>
  );
}
