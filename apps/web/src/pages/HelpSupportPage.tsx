import { useState } from "react";
import { Helmet } from "react-helmet-async";
import { Link, useNavigate } from "react-router-dom";
import {
  HelpCircle,
  BookOpen,
  ShieldAlert,
  Mail,
  ExternalLink,
  ChevronDown,
  CheckCircle2,
  AlertCircle,
  Copy,
  Check,
  Send,
  MessageCircle,
  Users,
  Building2,
  FileText,
  Clock,
  Sparkles,
  ArrowRight
} from "lucide-react";
import { showToast } from "../utils/toast";
import GovlyxLogo from "../components/ui/GovlyxLogo";

export default function HelpSupportPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<"how-to-use" | "copyright" | "contact">("how-to-use");
  const [copiedEmail, setCopiedEmail] = useState(false);
  const [openFaq, setOpenFaq] = useState<number | null>(0);

  const SUPPORT_EMAIL = "govlyxsupport@gmail.com";

  const handleCopyEmail = () => {
    navigator.clipboard.writeText(SUPPORT_EMAIL);
    setCopiedEmail(true);
    showToast.success("Email copied to clipboard!");
    setTimeout(() => setCopiedEmail(false), 2000);
  };

  const FAQS = [
    {
      q: "How do I post a civic issue in my neighbourhood?",
      a: "Tap the '+' button in the top navigation bar or feed header. Choose 'Report Civic Issue', select your category (Roads, Sanitation, Electricity, etc.), add clear photos/description, and your 6-digit pincode will automatically route it to neighbours and the municipal authorities."
    },
    {
      q: "How does the auto-escalation process work?",
      a: "When you report an issue, it starts at the neighbourhood level. As neighbours react, bump, or add comments, the Govlyx algorithm elevates the issue to district, state, and central authority dashboards until resolved with proof."
    },
    {
      q: "How does 1v1 Quick Chat preserve my anonymity?",
      a: "Govlyx never exposes your real phone number or email address during Quick Chat sessions. You are assigned a unique, transient pseudonym with built-in screenshot protection and encrypted messages."
    },
    {
      q: "How do Government departments verify and close complaints?",
      a: "Official verified government accounts inspect complaints tagged to their department, dispatch local inspection teams, and can only mark issues resolved after uploading genuine photographic proof."
    },
    {
      q: "How do I join or create a local community?",
      a: "Visit the Communities tab on the sidebar. You can explore pincode-based communities or request/create a localized resident community for your apartment, locality, or sector."
    }
  ];

  return (
    <div className="w-full max-w-4xl mx-auto px-3 sm:px-6 py-4 sm:py-8">
      <Helmet>
        <title>Help & Support | Govlyx</title>
      </Helmet>

      {/* Header Banner */}
      <div className="rounded-2xl bg-gradient-to-r from-blue-700 via-blue-600 to-indigo-700 text-white p-5 sm:p-8 shadow-lg relative overflow-hidden mb-6">
        <div className="relative z-10">
          <div className="flex items-center gap-3 mb-2">
            <div className="p-2 bg-white/10 backdrop-blur-md rounded-xl">
              <HelpCircle className="w-6 h-6 text-white" />
            </div>
            <span className="text-xs uppercase font-bold tracking-wider text-blue-200">User Assistance</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-black tracking-tight">Help & Support</h1>
          <p className="text-blue-100 text-xs sm:text-sm mt-1 max-w-xl leading-relaxed">
            Find guides on how to use Govlyx, report copyright violations, or reach out directly to the official support desk.
          </p>
        </div>
        <div className="absolute right-0 bottom-0 translate-x-8 translate-y-8 opacity-10 pointer-events-none">
          <GovlyxLogo size={200} />
        </div>
      </div>

      {/* Tab Controls */}
      <div className="flex border-b border-base-300 gap-1 sm:gap-2 mb-6 overflow-x-auto scrollbar-hide pb-0.5">
        <button
          onClick={() => setActiveTab("how-to-use")}
          className={`flex-1 sm:flex-initial flex items-center justify-center gap-1.5 sm:gap-2 px-2.5 sm:px-4 py-2 sm:py-2.5 text-xs sm:text-sm font-bold border-b-2 transition-colors whitespace-nowrap cursor-pointer ${
            activeTab === "how-to-use"
              ? "border-[#1D4ED8] text-[#1D4ED8] dark:text-blue-400"
              : "border-transparent text-base-content/60 hover:text-base-content"
          }`}
        >
          <BookOpen className="w-3.5 h-3.5 sm:w-4 sm:h-4 shrink-0" />
          <span>How to Use</span>
        </button>

        <button
          onClick={() => setActiveTab("copyright")}
          className={`flex-1 sm:flex-initial flex items-center justify-center gap-1.5 sm:gap-2 px-2.5 sm:px-4 py-2 sm:py-2.5 text-xs sm:text-sm font-bold border-b-2 transition-colors whitespace-nowrap cursor-pointer ${
            activeTab === "copyright"
              ? "border-[#1D4ED8] text-[#1D4ED8] dark:text-blue-400"
              : "border-transparent text-base-content/60 hover:text-base-content"
          }`}
        >
          <ShieldAlert className="w-3.5 h-3.5 sm:w-4 sm:h-4 shrink-0" />
          <span>Copyright & IP</span>
        </button>

        <button
          onClick={() => setActiveTab("contact")}
          className={`flex-1 sm:flex-initial flex items-center justify-center gap-1.5 sm:gap-2 px-2.5 sm:px-4 py-2 sm:py-2.5 text-xs sm:text-sm font-bold border-b-2 transition-colors whitespace-nowrap cursor-pointer ${
            activeTab === "contact"
              ? "border-[#1D4ED8] text-[#1D4ED8] dark:text-blue-400"
              : "border-transparent text-base-content/60 hover:text-base-content"
          }`}
        >
          <Mail className="w-3.5 h-3.5 sm:w-4 sm:h-4 shrink-0" />
          <span className="hidden xs:inline sm:inline">Govlyx Support</span>
          <span className="xs:hidden sm:hidden">Support</span>
        </button>
      </div>

      {/* ── TAB 1: HOW TO USE ── */}
      {activeTab === "how-to-use" && (
        <div className="space-y-6">
          {/* Quick Action Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
            <div className="bg-base-200 p-4 rounded-xl border border-base-content/5 flex flex-col gap-2">
              <div className="w-8 h-8 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400 flex items-center justify-center">
                <Building2 className="w-4 h-4" />
              </div>
              <h3 className="font-bold text-sm">Neighbourhood Issues</h3>
              <p className="text-xs opacity-70 leading-relaxed">
                Learn how to tag municipal departments and rally neighbour upvotes to escalate issues.
              </p>
            </div>

            <div className="bg-base-200 p-4 rounded-xl border border-base-content/5 flex flex-col gap-2">
              <div className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center">
                <MessageCircle className="w-4 h-4" />
              </div>
              <h3 className="font-bold text-sm">Anonymized 1v1 Chat</h3>
              <p className="text-xs opacity-70 leading-relaxed">
                Connect safely with nearby citizens in your pincode cluster without leaking your phone number.
              </p>
            </div>

            <div className="bg-base-200 p-4 rounded-xl border border-base-content/5 flex flex-col gap-2">
              <div className="w-8 h-8 rounded-lg bg-purple-500/10 text-purple-600 dark:text-purple-400 flex items-center justify-center">
                <Users className="w-4 h-4" />
              </div>
              <h3 className="font-bold text-sm">Local Communities</h3>
              <p className="text-xs opacity-70 leading-relaxed">
                Discover active resident associations, public safety updates, and community groups.
              </p>
            </div>
          </div>

          {/* Detailed FAQs */}
          <div className="bg-base-200 rounded-2xl p-4 sm:p-6 border border-base-content/5">
            <h2 className="text-base sm:text-lg font-extrabold mb-4 flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-[#1D4ED8]" />
              <span>Frequently Asked Questions</span>
            </h2>

            <div className="space-y-3">
              {FAQS.map((faq, idx) => {
                const isOpen = openFaq === idx;
                return (
                  <div
                    key={idx}
                    className="border border-base-300 rounded-xl overflow-hidden bg-base-100/60"
                  >
                    <button
                      onClick={() => setOpenFaq(isOpen ? null : idx)}
                      className="w-full text-left px-4 py-3.5 flex items-center justify-between gap-3 font-semibold text-xs sm:text-sm hover:bg-base-200/50 transition cursor-pointer"
                    >
                      <span>{faq.q}</span>
                      <ChevronDown
                        className={`w-4 h-4 text-base-content/60 shrink-0 transition-transform ${
                          isOpen ? "rotate-180 text-[#1D4ED8]" : ""
                        }`}
                      />
                    </button>
                    {isOpen && (
                      <div className="px-4 pb-4 pt-1 text-xs text-base-content/75 leading-relaxed border-t border-base-300/40">
                        {faq.a}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Full Public Guide Link */}
          <div className="flex items-center justify-between p-4 rounded-xl bg-base-200 border border-base-300">
            <div>
              <p className="font-bold text-sm">Want the complete visual walkthrough?</p>
              <p className="text-xs opacity-60">View diagrams, live infographics, and department response timelines.</p>
            </div>
            <Link
              to="/how-to-use"
              target="_blank"
              rel="noopener noreferrer"
              className="btn btn-sm bg-[#1D4ED8] hover:bg-blue-800 text-white rounded-lg flex items-center gap-1.5"
            >
              <span>Full Guide</span>
              <ExternalLink className="w-3.5 h-3.5" />
            </Link>
          </div>
        </div>
      )}

      {/* ── TAB 2: COPYRIGHT & DMCA ── */}
      {activeTab === "copyright" && (
        <div className="space-y-6">
          <div className="bg-base-200 rounded-2xl p-5 sm:p-6 border border-base-content/5 space-y-4">
            <div className="flex items-center gap-3">
              <div className="p-2.5 bg-amber-500/10 text-amber-600 dark:text-amber-400 rounded-xl">
                <ShieldAlert className="w-6 h-6" />
              </div>
              <div>
                <h2 className="text-base sm:text-lg font-black">Copyright & Intellectual Property</h2>
                <p className="text-xs opacity-70">Govlyx strictly adheres to Copyright laws and Indian IT intermediary rules.</p>
              </div>
            </div>

            <div className="p-4 rounded-xl bg-base-100 border border-base-300 space-y-2 text-xs leading-relaxed opacity-80">
              <p>
                Govlyx respects the intellectual property rights of creators and rights holders. If you believe your copyrighted work has been infringed upon or uploaded without authorization, you can file a takedown notice or inspect previous claims.
              </p>
              <ul className="list-disc pl-5 space-y-1 mt-2">
                <li><strong>Verified Takedowns:</strong> Legitimate claims undergo immediate manual and automated review.</li>
                <li><strong>3-Strike Rule:</strong> Accounts repeatedly violating copyright provisions face permanent suspension.</li>
                <li><strong>Tracking:</strong> Each claim is issued a reference token to monitor resolution status.</li>
              </ul>
            </div>

            {/* Direct Claim Actions */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5 pt-2">
              <div className="p-4 rounded-xl bg-base-100 border border-base-300 flex flex-col justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2 text-blue-600 dark:text-blue-400 font-bold text-sm mb-1">
                    <FileText className="w-4 h-4" />
                    <span>File a Copyright Claim</span>
                  </div>
                  <p className="text-xs opacity-70">
                    Submit a formal notice specifying the infringing post link and original work details.
                  </p>
                </div>
                <button
                  onClick={() => navigate("/copyright-claim")}
                  className="btn btn-sm bg-[#1D4ED8] hover:bg-blue-800 text-white rounded-lg flex items-center justify-center gap-1.5 w-full cursor-pointer"
                >
                  <span>Open Claim Form</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="p-4 rounded-xl bg-base-100 border border-base-300 flex flex-col justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2 text-purple-600 dark:text-purple-400 font-bold text-sm mb-1">
                    <Clock className="w-4 h-4" />
                    <span>Check Claim Status</span>
                  </div>
                  <p className="text-xs opacity-70">
                    Already submitted a takedown request? Track its review, status, and outcome.
                  </p>
                </div>
                <button
                  onClick={() => navigate("/copyright-claim/status")}
                  className="btn btn-sm btn-outline border-base-300 text-base-content hover:bg-base-200 rounded-lg flex items-center justify-center gap-1.5 w-full cursor-pointer"
                >
                  <span>Check Status</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>

            {/* Third-Party Credits & API Acknowledgements */}
            <div className="p-4 rounded-xl bg-base-100 border border-base-300 space-y-2 mt-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-xs font-bold text-base-content/80">
                  <Sparkles className="w-3.5 h-3.5 text-[#1D4ED8]" />
                  <span>Third-Party Media & API Attribution</span>
                </div>
                <span className="text-[10px] text-base-content/40 font-mono">Licensed Integration</span>
              </div>
              <p className="text-xs text-base-content/70 leading-relaxed">
                GIF animations and trending short-form animated media integrated across Govlyx Quick Chat are powered by the official <strong>KLIPY API</strong>. All GIF media rights, trademarks, and associated animations remain the exclusive property of their respective creators and <a href="https://klipy.com" target="_blank" rel="noreferrer" className="text-[#1D4ED8] dark:text-blue-400 hover:underline font-medium">KLIPY (klipy.com)</a>.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ── TAB 3: CONTACT & OFFICIAL EMAIL ── */}
      {activeTab === "contact" && (
        <div className="space-y-6">
          <div className="bg-base-200 rounded-2xl p-5 sm:p-8 border border-base-content/5 space-y-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <span className="text-xs font-bold uppercase tracking-wider text-blue-600 dark:text-blue-400">Official Grievance Desk</span>
                <h2 className="text-xl sm:text-2xl font-black mt-1">Get in Touch with Govlyx</h2>
                <p className="text-xs sm:text-sm opacity-70 mt-1">
                  We acknowledge grievances within 24 hours and ensure full resolution within 15 days.
                </p>
              </div>
              <div className="w-12 h-12 rounded-2xl bg-blue-500/10 text-[#1D4ED8] dark:text-blue-400 flex items-center justify-center shrink-0">
                <Mail className="w-6 h-6" />
              </div>
            </div>

            {/* Email Box */}
            <div className="p-4 sm:p-5 rounded-xl bg-base-100 border border-base-300 flex flex-col sm:flex-row items-center justify-between gap-4 shadow-sm">
              <div className="flex items-center gap-3 w-full sm:w-auto min-w-0">
                <div className="p-2.5 rounded-lg bg-base-200 text-base-content/70 shrink-0">
                  <Mail className="w-5 h-5" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-[11px] font-semibold opacity-60">Official Support Email</p>
                  <p className="font-mono font-bold text-xs xs:text-sm sm:text-base text-[#1D4ED8] dark:text-blue-400 select-all break-all sm:break-normal">
                    {SUPPORT_EMAIL}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2 w-full sm:w-auto">
                <button
                  onClick={handleCopyEmail}
                  className="btn btn-sm btn-ghost border border-base-300 flex-1 sm:flex-initial flex items-center gap-1.5 cursor-pointer"
                >
                  {copiedEmail ? <Check className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4" />}
                  <span>{copiedEmail ? "Copied" : "Copy"}</span>
                </button>

                <a
                  href={`mailto:${SUPPORT_EMAIL}?subject=Govlyx%20User%20Support%20Query`}
                  className="btn btn-sm bg-[#1D4ED8] hover:bg-blue-800 text-white flex-1 sm:flex-initial flex items-center gap-1.5"
                >
                  <Send className="w-4 h-4" />
                  <span>Send Email</span>
                </a>
              </div>
            </div>

            {/* Response Timeline & Guidelines */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div className="p-4 rounded-xl bg-base-100 border border-base-300 space-y-1.5">
                <div className="flex items-center gap-2 text-emerald-600 dark:text-emerald-400 font-bold text-xs">
                  <CheckCircle2 className="w-4 h-4" />
                  <span>Response Standards</span>
                </div>
                <p className="text-xs opacity-75 leading-relaxed">
                  Support tickets are reviewed by our community safety officers. Critical safety and impersonation concerns receive priority escalation.
                </p>
              </div>

              <div className="p-4 rounded-xl bg-base-100 border border-base-300 space-y-1.5">
                <div className="flex items-center gap-2 text-blue-600 dark:text-blue-400 font-bold text-xs">
                  <AlertCircle className="w-4 h-4" />
                  <span>What to Include</span>
                </div>
                <p className="text-xs opacity-75 leading-relaxed">
                  Include your registered username, relevant post IDs or URLs, and screenshots to help resolve your issue swiftly.
                </p>
              </div>
            </div>

          </div>
        </div>
      )}

    </div>
  );
}
