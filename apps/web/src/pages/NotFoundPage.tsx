import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { motion, AnimatePresence } from "framer-motion";
import {
  Home,
  ArrowLeft,
  RefreshCw,
  Sparkles,
  Loader2,
  Construction,
  Coffee,
  MapPinOff,
  Droplets,
  FileQuestion,
  Clock,
  Bike,
  Rocket,
  Navigation,
  Zap,
  Shield,
  Radio,
  type LucideIcon,
} from "lucide-react";
import GovlyxLogo from "../components/ui/GovlyxLogo";
import { useTheme } from "../hooks/useTheme";

interface PunchlineItem {
  Icon: LucideIcon;
  iconBg: string;
  iconColor: string;
  title: string;
  desc: string;
  tag: string;
}

const FUNNY_PUNCHLINES: PunchlineItem[] = [
  {
    Icon: Construction,
    iconBg: "bg-amber-500/10 border-amber-500/25",
    iconColor: "text-amber-500",
    title: "Pothole on the Digital Highway!",
    desc: "Looks like the local contractor dug up this page and forgot to patch it. We've filed a grievance to MCD, but you know how it goes.",
    tag: "Civic Issue #404"
  },
  {
    Icon: Coffee,
    iconBg: "bg-orange-500/10 border-orange-500/25",
    iconColor: "text-orange-500",
    title: "Server is on a Chai-Samosa Break",
    desc: "Government office lunch timings are strictly 1:00 PM to 4:00 PM. The file you requested is under a stack of paperweights.",
    tag: "Lunch Break Protocol"
  },
  {
    Icon: MapPinOff,
    iconBg: "bg-blue-500/10 border-blue-500/25",
    iconColor: "text-blue-500",
    title: "Lost in a Narrow Pincode Gali",
    desc: "Google Maps told us to take a shortcut through a narrow gali. Now the route cable is untraceable in the local cluster.",
    tag: "Hyperlocal Traffic Alert"
  },
  {
    Icon: Droplets,
    iconBg: "bg-cyan-500/10 border-cyan-500/25",
    iconColor: "text-cyan-500",
    title: "Missing Like Water Supply on Sunday",
    desc: "We called the tanker and pinged the Jal Board, but this page vanished faster than water pressure in 3rd-floor flats.",
    tag: "Jal Board Grievance"
  },
  {
    Icon: FileQuestion,
    iconBg: "bg-purple-500/10 border-purple-500/25",
    iconColor: "text-purple-500",
    title: "Come Back Tomorrow With 3 Photos & Aadhaar",
    desc: "The Babu in charge of this page is on casual leave. Please get your form attested by a Gazetted Officer first.",
    tag: "Document Verification"
  },
  {
    Icon: Clock,
    iconBg: "bg-rose-500/10 border-rose-500/25",
    iconColor: "text-rose-500",
    title: "Stuck in Peak Bangalore / Mumbai Traffic",
    desc: "The packets were dispatched on time, but they're currently stuck at Silk Board junction. Estimated arrival: Next decade.",
    tag: "Radial Cluster Delay"
  }
];

interface RedirectMessage {
  text: string;
  Icon: LucideIcon;
}

const REDIRECT_MESSAGES: RedirectMessage[] = [
  { text: "Rerouting via a secret shortcut uncle showed on a scooter...", Icon: Bike },
  { text: "Taking you Home before Sharma Ji asks for your salary slip...", Icon: Zap },
  { text: "Teleporting you back to your 6-digit pincode safely...", Icon: Rocket },
  { text: "Navigating around the digital pothole at 80 km/h...", Icon: Navigation },
  { text: "Deploying emergency municipal response team to rescue you...", Icon: Radio },
  { text: "Escaping this dead-end before your battery hits 1%...", Icon: Zap },
  { text: "Filing an anonymous complaint and jumping to safety...", Icon: Shield }
];

const NotFoundPage: React.FC = () => {
  const navigate = useNavigate();
  useTheme();

  const [currentIndex, setCurrentIndex] = useState(() => Math.floor(Math.random() * FUNNY_PUNCHLINES.length));
  const [redirecting, setRedirecting] = useState<RedirectMessage | null>(null);
  const [countdown, setCountdown] = useState<number>(15);

  const activePunch = FUNNY_PUNCHLINES[currentIndex];

  const shuffleExcuse = () => {
    setCurrentIndex((prev) => (prev + 1) % FUNNY_PUNCHLINES.length);
  };

  const handleRedirect = (path: string | -1) => {
    const randomMsg = REDIRECT_MESSAGES[Math.floor(Math.random() * REDIRECT_MESSAGES.length)];
    setRedirecting(randomMsg);
    setTimeout(() => {
      if (path === -1) {
        navigate(-1);
      } else {
        navigate(path);
      }
    }, 1200);
  };

  // Auto-redirect countdown
  useEffect(() => {
    if (redirecting) return;
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          handleRedirect("/");
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [redirecting]);

  return (
    <div className="min-h-screen bg-base-100 text-base-content flex flex-col items-center justify-center px-4 sm:px-6 py-10 relative overflow-hidden select-none">
      <Helmet>
        <title>404 — Lost in the Neighbourhood | Govlyx</title>
        <meta name="robots" content="noindex, nofollow" />
      </Helmet>

      {/* Ambient background glow & grid */}
      <div className="absolute inset-0 bg-grid-pattern opacity-30 pointer-events-none" />
      <div className="absolute -top-32 -left-32 w-96 h-96 bg-[#1D4ED8]/15 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute -bottom-32 -right-32 w-96 h-96 bg-[#1D4ED8]/15 rounded-full blur-[100px] pointer-events-none" />

      {/* Redirecting Overlay Screen */}
      <AnimatePresence>
        {redirecting && (
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 bg-base-100/95 backdrop-blur-md flex flex-col items-center justify-center p-6 text-center"
          >
            <div className="w-16 h-16 rounded-3xl bg-[#1D4ED8]/10 border border-[#1D4ED8]/30 flex items-center justify-center text-[#1D4ED8] mb-6 shadow-xl animate-bounce">
              <redirecting.Icon size={32} className="text-[#1D4ED8]" />
            </div>
            <h2 className="text-2xl sm:text-3xl font-black tracking-tight text-base-content mb-3">
              Hang Tight!
            </h2>
            <p className="text-base sm:text-lg text-base-content/80 font-semibold max-w-md leading-relaxed">
              {redirecting.text}
            </p>
            <div className="mt-6 flex items-center gap-2 text-xs font-bold text-[#1D4ED8] bg-[#1D4ED8]/10 px-4 py-2 rounded-full">
              <Loader2 size={14} className="animate-spin" />
              <span>Redirecting now...</span>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="relative z-10 flex flex-col items-center max-w-lg text-center w-full">
        {/* Top Logo & Floating Emblem */}
        <div className="relative mb-6">
          <motion.div
            animate={{ y: [0, -6, 0] }}
            transition={{ duration: 3, repeat: Infinity, ease: "easeInOut" }}
            className="relative"
          >
            <GovlyxLogo size={68} />
          </motion.div>
        </div>

        {/* 404 Headline */}
        <motion.div
          initial={{ opacity: 0, y: 15 }}
          animate={{ opacity: 1, y: 0 }}
          className="relative inline-block mb-3"
        >
          <span className="text-7xl sm:text-8xl md:text-9xl font-black tracking-tighter text-transparent bg-clip-text bg-gradient-to-br from-[#1D4ED8] via-blue-500 to-indigo-600 drop-shadow-sm">
            404
          </span>
          <div className="absolute -top-1 -right-7 p-2 rounded-2xl bg-white dark:bg-black border border-slate-200 dark:border-slate-800 text-black dark:text-white shadow-md animate-bounce">
            <activePunch.Icon size={20} className="text-[#1D4ED8] dark:text-blue-400" />
          </div>
        </motion.div>

        {/* Dynamic Funny Card */}
        <AnimatePresence mode="wait">
          <motion.div
            key={currentIndex}
            initial={{ opacity: 0, y: 12, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -12, scale: 0.98 }}
            transition={{ duration: 0.25 }}
            className="w-full bg-base-200/80 border border-base-300 rounded-3xl p-5 sm:p-6 shadow-xl mb-6 text-left relative overflow-hidden backdrop-blur-sm"
          >
            <div className="flex items-center justify-between gap-2 mb-3">
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-slate-200 dark:border-slate-800 bg-white dark:bg-black text-black dark:text-white text-[11px] sm:text-xs font-bold shadow-xs">
                <activePunch.Icon size={13} className="text-[#1D4ED8] dark:text-blue-400" />
                {activePunch.tag}
              </span>
              <button
                onClick={shuffleExcuse}
                className="btn btn-ghost btn-xs text-xs font-bold gap-1.5 bg-white dark:bg-black border border-slate-200 dark:border-slate-800 text-black dark:text-white hover:bg-slate-100 dark:hover:bg-slate-900 cursor-pointer rounded-full px-3 py-1 shadow-xs transition-colors"
                title="Shuffle funny excuse"
              >
                <RefreshCw size={12} className="text-[#1D4ED8] dark:text-blue-400" />
                Another Excuse
              </button>
            </div>

            <h2 className="text-lg sm:text-xl font-extrabold text-base-content leading-snug mb-1.5 flex items-center gap-2.5">
              <span className="p-1.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-black text-black dark:text-white shadow-xs">
                <activePunch.Icon size={18} className="text-[#1D4ED8] dark:text-blue-400" />
              </span>
              <span>{activePunch.title}</span>
            </h2>

            <p className="text-xs sm:text-sm text-base-content/75 leading-relaxed font-medium">
              {activePunch.desc}
            </p>
          </motion.div>
        </AnimatePresence>

        {/* Action Buttons */}
        <div className="flex flex-col sm:flex-row items-center gap-3 w-full justify-center">
          <button
            onClick={() => handleRedirect(-1)}
            disabled={!!redirecting}
            className="btn btn-outline border-base-300 hover:bg-base-200 text-base-content font-bold h-11 sm:h-12 text-sm rounded-xl px-5 flex-1 w-full sm:w-auto shadow-xs cursor-pointer flex items-center justify-center gap-2"
          >
            <ArrowLeft size={16} />
            <span>Go Back</span>
          </button>

          <button
            onClick={() => handleRedirect("/")}
            disabled={!!redirecting}
            className="btn bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 text-white font-bold h-11 sm:h-12 text-sm rounded-xl px-6 flex-1 w-full sm:w-auto shadow-lg shadow-[#1D4ED8]/25 border-none cursor-pointer flex items-center justify-center gap-2"
          >
            <Home size={16} />
            <span>Go to Home</span>
          </button>
        </div>

        {/* Countdown auto redirect ticker */}
        <div className="mt-6 flex items-center justify-center gap-1.5 text-xs text-base-content/50 font-medium">
          <Sparkles size={13} className="text-[#1D4ED8]" />
          <span>Auto-navigating to safety in <strong>{countdown}s</strong></span>
        </div>
      </div>
    </div>
  );
};

export default NotFoundPage;

