import { useState } from 'react';
import { Helmet } from 'react-helmet-async';
import PageNavbar from '../components/layout/PageNavbar';
import LandingBottomCtaAndFooter from '../components/landing/LandingBottomCtaAndFooter';
import {
  Camera,
  Landmark,
  Users,
  TrendingUp,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  MessageCircle,
  ShieldCheck,
  Lock,
  Zap,
  Building2,
  Cpu,
  Building,
  Rocket,
  Lightbulb,
  Radio,
  ChevronDown,
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

export default function HowToUse() {
  const [activeTab, setActiveTab] = useState<'citizens' | 'govt' | 'quickchat'>(
    'citizens',
  );
  const [openFaq, setOpenFaq] = useState<number | null>(0);

  const FAQS = [
    {
      q: 'Do I need to share my phone number to use Govlyx or Quick Chat?',
      a: 'No! Govlyx does not reveal your phone number or real name. When you join or use 1v1 Quick Chat, an anonymous identifier is automatically assigned to protect your identity from harassment and spam.',
    },
    {
      q: 'How does the civic issue auto-escalation work?',
      a: 'When you post a civic issue with your 6-digit pincode, it starts at the neighbourhood level. As neighbours react, bump, or comment, our algorithm auto-promotes the issue to district, state, and national portals until the tagged department takes action and marks it resolved with proof.',
    },
    {
      q: 'How does Quick Chat find neighbours near me?',
      a: 'Quick Chat matches you with active online users within your pincode and surrounding radial clusters (500m to 2km). If no immediate match is found locally, the search gracefully expands to your city cluster.',
    },
    {
      q: 'What is Screenshot Protection?',
      a: 'On supported browsers and mobile devices, Govlyx disables direct screen capture and renders protective overlay obfuscations during private 1v1 sessions, preventing others from capturing and leaking conversations.',
    },
    {
      q: 'How do Government departments verify and resolve issues?',
      a: 'Authorized department officers have dedicated portal dashboards where complaints tagged to their jurisdiction arrive in real time. They inspect the issue, dispatch ground fixers, and post verified resolution photos to notify citizens.',
    },
    {
      q: 'Is Govlyx available across all 19,000+ Indian pincodes?',
      a: 'Yes! Every standard 6-digit Indian pincode is indexed. Whether you are in Pune (411038), Bengaluru (560038), Delhi (110016), or a rural Gram Panchayat, your local feed automatically configures to your locality.',
    },
  ];

  const faqJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: FAQS.map((faq) => ({
      '@type': 'Question',
      name: faq.q,
      acceptedAnswer: {
        '@type': 'Answer',
        text: faq.a,
      },
    })),
  };

  return (
    <div className="h-screen bg-base-100 text-slate-800 dark:text-slate-200 selection:bg-blue-600/30 transition-colors duration-300 flex flex-col relative overflow-hidden">
      <Helmet>
        <title>
          How to Use Govlyx - Hyperlocal Platform & Quick Chat Guide
        </title>
        <meta
          name="description"
          content="Complete guide on how to report civic issues, track automatic complaint escalation, use anonymous 1v1 Quick Chat, and engage locally on Govlyx."
        />
        <link rel="canonical" href="https://govlyx.com/how-to-use" />
        <meta property="og:type" content="article" />
        <meta property="og:url" content="https://govlyx.com/how-to-use" />
        <meta property="og:site_name" content="Govlyx" />
        <meta
          property="og:title"
          content="How to Use Govlyx - Hyperlocal Platform & Quick Chat Guide"
        />
        <meta
          property="og:description"
          content="Complete guide on how to report civic issues, track complaint escalation, and connect locally on Govlyx."
        />
        <meta property="og:image" content="https://govlyx.com/govlyx-og.png" />
        <meta name="twitter:card" content="summary_large_image" />
        <meta
          name="twitter:title"
          content="How to Use Govlyx - Hyperlocal Platform & Quick Chat Guide"
        />
        <meta
          name="twitter:description"
          content="Complete guide on how to report civic issues, track complaint escalation, and connect locally on Govlyx."
        />
        <meta name="twitter:image" content="https://govlyx.com/govlyx-og.png" />
        <script type="application/ld+json">{JSON.stringify(faqJsonLd)}</script>
      </Helmet>

      {/* ─── Navbar ──────────────────────────────────────────────────────────── */}
      <PageNavbar active="how-to-use" />

      {/* ─── Scrollable Container Wrapper ─── */}
      <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden flex flex-col justify-between z-10">
        <main className="w-full flex-1">
          {/* ─── Hero Section ────────────────────────────────────────────────────── */}
          <section className="pt-6 sm:pt-16 pb-4 sm:pb-8 px-3.5 sm:px-6 lg:px-24">
            <div className="max-w-[1200px] mx-auto">
              <div className="text-left mb-6 sm:mb-12 max-w-3xl">
                <div className="mb-2 sm:mb-6">
                  <h1 className="text-2xl sm:text-5xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                    How to Use
                  </h1>
                </div>
                <p className="text-slate-500 dark:text-slate-400 mt-2 sm:mt-4 text-xs sm:text-base leading-relaxed font-medium">
                  Learn how Govlyx bridges citizens, hyper-local neighbourhoods,
                  and government departments into one transparent, safe
                  ecosystem.
                </p>
              </div>
            </div>
          </section>

          {/* ─── Interactive Workflow Tabs ────────────────────────────────────────── */}
          <section className="pt-4 pb-8 sm:pt-8 sm:pb-16 px-3.5 sm:px-6 lg:px-24 bg-base-100">
            <div className="max-w-[1200px] mx-auto">
              {/* Tab Buttons */}
              <div className="flex justify-center mb-4 sm:mb-8 px-1 sm:px-0">
                <div className="bg-base-200 p-1 sm:p-1.5 rounded-xl sm:rounded-2xl border border-black/10 dark:border-base-300 grid grid-cols-3 sm:inline-flex sm:flex-row gap-1 sm:gap-2 w-full max-w-lg sm:max-w-none sm:w-auto">
                  <button
                    onClick={() => setActiveTab('citizens')}
                    className={`px-2 sm:px-5 py-2 sm:py-2.5 rounded-xl text-[11px] sm:text-sm font-extrabold transition-all cursor-pointer flex flex-col xs:flex-row items-center justify-center gap-1 sm:gap-2 text-center ${
                      activeTab === 'citizens'
                        ? 'bg-[#1D4ED8] text-white shadow-md shadow-[#1D4ED8]/25'
                        : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                    }`}
                  >
                    <Users className="w-4 h-4 shrink-0" />
                    <span className="truncate">For Citizens</span>
                  </button>

                  <button
                    onClick={() => setActiveTab('quickchat')}
                    className={`px-2 sm:px-5 py-2 sm:py-2.5 rounded-xl text-[11px] sm:text-sm font-extrabold transition-all cursor-pointer flex flex-col xs:flex-row items-center justify-center gap-1 sm:gap-2 text-center ${
                      activeTab === 'quickchat'
                        ? 'bg-[#1D4ED8] text-white shadow-md shadow-[#1D4ED8]/25'
                        : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                    }`}
                  >
                    <MessageCircle className="w-4 h-4 shrink-0" />
                    <span className="truncate">1v1 Quick Chat</span>
                  </button>

                  <button
                    onClick={() => setActiveTab('govt')}
                    className={`px-2 sm:px-5 py-2 sm:py-2.5 rounded-xl text-[11px] sm:text-sm font-extrabold transition-all cursor-pointer flex flex-col xs:flex-row items-center justify-center gap-1 sm:gap-2 text-center ${
                      activeTab === 'govt'
                        ? 'bg-[#1D4ED8] text-white shadow-md shadow-[#1D4ED8]/25'
                        : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                    }`}
                  >
                    <Landmark className="w-4 h-4 shrink-0" />
                    <span className="truncate">For Government</span>
                  </button>
                </div>
              </div>

              {/* Tab 1: Citizens */}
              {activeTab === 'citizens' && (
                <motion.div
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="grid grid-cols-1 md:grid-cols-3 gap-6"
                >
                  <div className="bg-base-200 border border-base-300 p-6 rounded-2xl">
                    <div className="w-10 h-10 rounded-xl bg-[#1D4ED8]/10 text-[#1D4ED8] dark:text-blue-400 flex items-center justify-center font-black text-base mb-4">
                      1
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-base mb-2">
                      Set Your Pincode
                    </h3>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                      Enter your 6-digit pincode (e.g. 411038). Your feed
                      instantly populates with local street issues, municipal
                      notices, and discussions in your colony.
                    </p>
                  </div>

                  <div className="bg-base-200 border border-base-300 p-6 rounded-2xl">
                    <div className="w-10 h-10 rounded-xl bg-[#1D4ED8]/10 text-[#1D4ED8] dark:text-blue-400 flex items-center justify-center font-black text-base mb-4">
                      2
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-base mb-2">
                      Post Civic Grievances
                    </h3>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                      Take a photo of a pothole, open garbage, or broken
                      streetlight. Tag the department (e.g. Electricity, Water,
                      Road). Anonymity protects you by default.
                    </p>
                  </div>

                  <div className="bg-base-200 border border-base-300 p-6 rounded-2xl">
                    <div className="w-10 h-10 rounded-xl bg-[#1D4ED8]/10 text-[#1D4ED8] dark:text-blue-400 flex items-center justify-center font-black text-base mb-4">
                      3
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-base mb-2">
                      Neighbours Bump & Resolve
                    </h3>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                      Locals upvote your complaint. The app automatically pushes
                      the issue from ward → district → state until the
                      department marks it fixed with proof.
                    </p>
                  </div>
                </motion.div>
              )}

              {/* Tab 2: Quick Chat */}
              {activeTab === 'quickchat' && (
                <motion.div
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="grid grid-cols-1 md:grid-cols-3 gap-6"
                >
                  <div className="bg-base-200 border border-base-300 p-6 rounded-2xl">
                    <div className="w-10 h-10 rounded-xl bg-violet-500/10 text-violet-600 dark:text-violet-400 flex items-center justify-center font-black text-base mb-4">
                      <Zap className="w-5 h-5" />
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-base mb-2">
                      Instant Local Matching
                    </h3>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                      Matches you in under 4 seconds with a verified resident
                      within a 500m to 2km radius. No phone number or social
                      handle needed.
                    </p>
                  </div>

                  <div className="bg-base-200 border border-base-300 p-6 rounded-2xl">
                    <div className="w-10 h-10 rounded-xl bg-violet-500/10 text-violet-600 dark:text-violet-400 flex items-center justify-center font-black text-base mb-4">
                      <ShieldCheck className="w-5 h-5" />
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-base mb-2">
                      Screenshot Protected
                    </h3>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                      Built-in security overlay blocks screenshots during 1v1
                      sessions. Ask emergency local questions or find sports
                      buddies with complete peace of mind.
                    </p>
                  </div>

                  <div className="bg-base-200 border border-base-300 p-6 rounded-2xl">
                    <div className="w-10 h-10 rounded-xl bg-violet-500/10 text-violet-600 dark:text-violet-400 flex items-center justify-center font-black text-base mb-4">
                      <Lock className="w-5 h-5" />
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-base mb-2">
                      Ephemeral & Zero Footprint
                    </h3>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                      When either person leaves, the session disappears
                      permanently. Zero lingering chat history, zero stalkers,
                      zero spam.
                    </p>
                  </div>
                </motion.div>
              )}

              {/* Tab 3: Government */}
              {activeTab === 'govt' && (
                <motion.div
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="grid grid-cols-1 md:grid-cols-3 gap-6"
                >
                  <div className="bg-base-200 border border-base-300 p-6 rounded-2xl">
                    <div className="w-10 h-10 rounded-xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center font-black text-base mb-4">
                      <Landmark className="w-5 h-5" />
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-base mb-2">
                      Pincode Broadcasts
                    </h3>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                      Broadcast water cut schedules, power shutdowns,
                      vaccination drives, and new welfare schemes directly to
                      targeted pincodes.
                    </p>
                  </div>

                  <div className="bg-base-200 border border-base-300 p-6 rounded-2xl">
                    <div className="w-10 h-10 rounded-xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center font-black text-base mb-4">
                      <TrendingUp className="w-5 h-5" />
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-base mb-2">
                      Real-time Grievance Inbox
                    </h3>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                      Officers view ranked complaints tagged to their
                      department. High-reaction community issues appear at the
                      top for immediate resolution.
                    </p>
                  </div>

                  <div className="bg-base-200 border border-base-300 p-6 rounded-2xl">
                    <div className="w-10 h-10 rounded-xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center font-black text-base mb-4">
                      <CheckCircle2 className="w-5 h-5" />
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-base mb-2">
                      Verified Resolution Proof
                    </h3>
                    <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                      Upload fixed-site photos to mark issues resolved. All
                      affected citizens in that area receive instant resolution
                      notifications.
                    </p>
                  </div>
                </motion.div>
              )}
            </div>
          </section>

          {/* ─── CIVIC ISSUE LIFECYCLE ─── */}
          <section className="py-14 sm:py-20 px-4 sm:px-6 lg:px-24 bg-base-200/50 border-t border-base-300">
            <div className="max-w-[1200px] mx-auto">
              <div className="text-center max-w-2xl mx-auto mb-12">
                <span className="inline-block text-white text-xs font-black tracking-widest uppercase bg-[#1D4ED8] px-4 py-1.5 rounded-full shadow-md shadow-[#1D4ED8]/25 border border-[#1D4ED8]">
                  Automated Escalation
                </span>
                <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-900 dark:text-white mt-3">
                  Civic Issue 5-Stage Lifecycle
                </h2>
                <p className="text-slate-500 dark:text-slate-400 text-xs sm:text-sm mt-2 font-medium">
                  Your complaint never gets lost in paperwork — Govlyx
                  auto-promotes it based on real neighbourhood engagement.
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
                <div className="bg-base-100 border border-base-300 p-5 rounded-2xl text-center flex flex-col items-center">
                  <div className="w-12 h-12 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-700 dark:text-slate-300 shadow-sm mb-3">
                    <Camera className="w-5 h-5" />
                  </div>
                  <h4 className="font-bold text-xs text-slate-900 dark:text-white">
                    1. You Post
                  </h4>
                  <p className="text-slate-400 text-[11px] mt-1">
                    Snap photo, specify pincode, and tag department.
                  </p>
                </div>

                <div className="bg-base-100 border border-base-300 p-5 rounded-2xl text-center flex flex-col items-center">
                  <div className="w-12 h-12 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-700 dark:text-slate-300 shadow-sm mb-3">
                    <Landmark className="w-5 h-5" />
                  </div>
                  <h4 className="font-bold text-xs text-slate-900 dark:text-white">
                    2. Govt Alerted
                  </h4>
                  <p className="text-slate-400 text-[11px] mt-1">
                    Tagged department receives alert on portal dashboard.
                  </p>
                </div>

                <div className="bg-base-100 border border-base-300 p-5 rounded-2xl text-center flex flex-col items-center">
                  <div className="w-12 h-12 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-700 dark:text-slate-300 shadow-sm mb-3">
                    <Users className="w-5 h-5" />
                  </div>
                  <h4 className="font-bold text-xs text-slate-900 dark:text-white">
                    3. Community Bumps
                  </h4>
                  <p className="text-slate-400 text-[11px] mt-1">
                    Neighbours react, comment, and add supporting photos.
                  </p>
                </div>

                <div className="bg-base-100 border border-base-300 p-5 rounded-2xl text-center flex flex-col items-center">
                  <div className="w-12 h-12 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-700 dark:text-slate-300 shadow-sm mb-3">
                    <TrendingUp className="w-5 h-5" />
                  </div>
                  <h4 className="font-bold text-xs text-slate-900 dark:text-white">
                    4. Auto-Escalated
                  </h4>
                  <p className="text-slate-400 text-[11px] mt-1">
                    Expands from ward → district → state automatically.
                  </p>
                </div>

                <div className="bg-base-100 border border-base-300 p-5 rounded-2xl text-center flex flex-col items-center">
                  <div className="w-12 h-12 rounded-full bg-emerald-500/10 text-emerald-500 flex items-center justify-center shadow-sm mb-3">
                    <CheckCircle2 className="w-5 h-5" />
                  </div>
                  <h4 className="font-bold text-xs text-emerald-600 dark:text-emerald-400">
                    5. Fixed with Proof
                  </h4>
                  <p className="text-slate-400 text-[11px] mt-1">
                    Department posts resolution proof and closes ticket.
                  </p>
                </div>
              </div>
            </div>
          </section>

          {/* ─── WHY GOVLYX COMPARISON TABLE ─── */}
          <section className="py-14 sm:py-20 px-4 sm:px-6 lg:px-24 bg-base-100 border-t border-base-300">
            <div className="max-w-[1200px] mx-auto">
              <div className="text-center max-w-2xl mx-auto mb-12">
                <span className="inline-block text-white text-xs font-black tracking-widest uppercase bg-[#1D4ED8] px-4 py-1.5 rounded-full shadow-md shadow-[#1D4ED8]/25 border border-[#1D4ED8]">
                  Platform Matrix
                </span>
                <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-900 dark:text-white mt-3">
                  Why Govlyx vs Traditional Apps?
                </h2>
                <p className="text-slate-500 dark:text-slate-400 text-xs sm:text-sm mt-2 font-medium">
                  Purpose-built specifically for Indian hyperlocal civic
                  engagement and anonymous neighbourhood networking.
                </p>
              </div>

              <div className="overflow-x-auto border border-base-300 rounded-2xl bg-base-200 shadow-sm">
                <table className="w-full border-collapse text-left text-xs sm:text-sm">
                  <thead>
                    <tr className="border-b border-[#1D4ED8]/30 bg-[#1D4ED8] text-white">
                      <th className="p-4 font-black text-white">Feature</th>
                      <th className="p-4 font-black text-white text-center">
                        Govlyx
                      </th>
                      <th className="p-4 font-black text-white text-center">
                        WhatsApp
                      </th>
                      <th className="p-4 font-black text-white text-center">
                        Facebook
                      </th>
                      <th className="p-4 font-black text-white text-center">
                        MyGov
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr className="border-b border-slate-100 dark:border-slate-800/60">
                      <td className="p-4 font-bold text-slate-800 dark:text-slate-200">
                        Hyperlocal (Pincode) Stream
                      </td>
                      <td className="p-4 text-center">
                        <CheckCircle2 className="w-5 h-5 text-emerald-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                    </tr>
                    <tr className="border-b border-slate-100 dark:border-slate-800/60">
                      <td className="p-4 font-bold text-slate-800 dark:text-slate-200">
                        Identity Protection (No Phone Number)
                      </td>
                      <td className="p-4 text-center">
                        <CheckCircle2 className="w-5 h-5 text-emerald-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                    </tr>
                    <tr className="border-b border-slate-100 dark:border-slate-800/60">
                      <td className="p-4 font-bold text-slate-800 dark:text-slate-200">
                        Direct Municipal Broadcasts
                      </td>
                      <td className="p-4 text-center">
                        <CheckCircle2 className="w-5 h-5 text-emerald-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <AlertTriangle className="w-5 h-5 text-amber-500 mx-auto" />
                      </td>
                    </tr>
                    <tr className="border-b border-slate-100 dark:border-slate-800/60">
                      <td className="p-4 font-bold text-slate-800 dark:text-slate-200">
                        Automatic Issue Escalation
                      </td>
                      <td className="p-4 text-center">
                        <CheckCircle2 className="w-5 h-5 text-emerald-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                    </tr>
                    <tr className="border-b border-slate-100 dark:border-slate-800/60">
                      <td className="p-4 font-bold text-slate-800 dark:text-slate-200">
                        Secure 1v1 Anonymous Quick Chat
                      </td>
                      <td className="p-4 text-center">
                        <CheckCircle2 className="w-5 h-5 text-emerald-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                    </tr>
                    <tr>
                      <td className="p-4 font-bold text-slate-800 dark:text-slate-200">
                        Screenshot Protection
                      </td>
                      <td className="p-4 text-center">
                        <CheckCircle2 className="w-5 h-5 text-emerald-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                      <td className="p-4 text-center">
                        <XCircle className="w-5 h-5 text-rose-500 mx-auto" />
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </section>

          {/* ─── NATIONAL MISSIONS ALIGNMENT ─── */}
          <section className="py-14 sm:py-20 px-4 sm:px-6 lg:px-24 bg-base-200/50 border-t border-base-300">
            <div className="max-w-[1200px] mx-auto">
              <div className="text-center max-w-2xl mx-auto mb-12">
                <span className="inline-block text-white text-xs font-black tracking-widest uppercase bg-[#1D4ED8] px-4 py-1.5 rounded-full shadow-md shadow-[#1D4ED8]/25 border border-[#1D4ED8]">
                  Government Alignment
                </span>
                <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-900 dark:text-white mt-3">
                  Aligned with National Digital Missions
                </h2>
                <p className="text-slate-500 dark:text-slate-400 text-xs sm:text-sm mt-2 font-medium">
                  Supporting citizen-centric governance and smart city digital
                  initiatives.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-left">
                <div className="bg-base-100 p-5 rounded-2xl border border-base-300">
                  <h4 className="font-bold text-sm text-slate-900 dark:text-white mb-1.5 flex items-center gap-2">
                    <Building2 className="w-4 h-4 text-blue-500 shrink-0" />{' '}
                    Smart Cities Mission
                  </h4>
                  <p className="text-slate-500 dark:text-slate-400 text-xs leading-relaxed">
                    Empowering urban local bodies with real-time feedback loops
                    and citizen-driven grievance escalation.
                  </p>
                </div>

                <div className="bg-base-100 p-5 rounded-2xl border border-base-300">
                  <h4 className="font-bold text-sm text-slate-900 dark:text-white mb-1.5 flex items-center gap-2">
                    <Cpu className="w-4 h-4 text-blue-500 shrink-0" /> Digital
                    India
                  </h4>
                  <p className="text-slate-500 dark:text-slate-400 text-xs leading-relaxed">
                    Direct two-way digital communication between citizens and
                    administration with multi-lingual support.
                  </p>
                </div>

                <div className="bg-base-100 p-5 rounded-2xl border border-base-300">
                  <h4 className="font-bold text-sm text-slate-900 dark:text-white mb-1.5 flex items-center gap-2">
                    <Building className="w-4 h-4 text-blue-500 shrink-0" /> Gram
                    Panchayat GPDP
                  </h4>
                  <p className="text-slate-500 dark:text-slate-400 text-xs leading-relaxed">
                    Digital noticeboards for rural panchayats to broadcast job
                    cards, water schemes, and budget allocations.
                  </p>
                </div>

                <div className="bg-base-100 p-5 rounded-2xl border border-base-300">
                  <h4 className="font-bold text-sm text-slate-900 dark:text-white mb-1.5 flex items-center gap-2">
                    <Rocket className="w-4 h-4 text-blue-500 shrink-0" />{' '}
                    Startup India
                  </h4>
                  <p className="text-slate-500 dark:text-slate-400 text-xs leading-relaxed">
                    DPIIT recognized student innovation solving fundamental
                    grassroots civic problems in India.
                  </p>
                </div>

                <div className="bg-base-100 p-5 rounded-2xl border border-base-300">
                  <h4 className="font-bold text-sm text-slate-900 dark:text-white mb-1.5 flex items-center gap-2">
                    <Lightbulb className="w-4 h-4 text-blue-500 shrink-0" />{' '}
                    Atal Innovation Mission
                  </h4>
                  <p className="text-slate-500 dark:text-slate-400 text-xs leading-relaxed">
                    Designed to nurture young innovators building high-impact
                    civic-tech infrastructure.
                  </p>
                </div>

                <div className="bg-base-100 p-5 rounded-2xl border border-base-300">
                  <h4 className="font-bold text-sm text-slate-900 dark:text-white mb-1.5 flex items-center gap-2">
                    <Radio className="w-4 h-4 text-blue-500 shrink-0" />{' '}
                    BharatNet & PM-WANI
                  </h4>
                  <p className="text-slate-500 dark:text-slate-400 text-xs leading-relaxed">
                    Optimized for fast lightweight loading even in tier-3 cities
                    and rural public Wi-Fi access spots.
                  </p>
                </div>
              </div>
            </div>
          </section>

          {/* ─── FREQUENTLY ASKED QUESTIONS ─── */}
          <section className="py-14 sm:py-20 px-4 sm:px-6 lg:px-24 bg-base-100 border-t border-base-300">
            <div className="max-w-[900px] mx-auto">
              <div className="text-center mb-10">
                <span className="inline-block text-white text-xs font-black tracking-widest uppercase bg-[#1D4ED8] px-4 py-1.5 rounded-full shadow-md shadow-[#1D4ED8]/25 border border-[#1D4ED8]">
                  Common Questions
                </span>
                <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-900 dark:text-white mt-3">
                  Frequently Asked Questions
                </h2>
              </div>

              <div className="space-y-3">
                {FAQS.map((faq, idx) => {
                  const isOpen = openFaq === idx;
                  return (
                    <div
                      key={idx}
                      className="border border-base-300 rounded-2xl bg-base-200 overflow-hidden transition-all"
                    >
                      <button
                        onClick={() => setOpenFaq(isOpen ? null : idx)}
                        className="w-full p-4 sm:p-5 text-left font-bold text-slate-900 dark:text-white text-sm sm:text-base flex items-center justify-between gap-4 cursor-pointer"
                      >
                        <span>{faq.q}</span>
                        <ChevronDown
                          className={`w-4 h-4 text-slate-400 shrink-0 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`}
                        />
                      </button>

                      <AnimatePresence>
                        {isOpen && (
                          <motion.div
                            initial={{ height: 0, opacity: 0 }}
                            animate={{ height: 'auto', opacity: 1 }}
                            exit={{ height: 0, opacity: 0 }}
                            transition={{ duration: 0.2 }}
                            className="px-4 pb-4 sm:px-5 sm:pb-5 text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed border-t border-slate-100 dark:border-slate-800 pt-3"
                          >
                            {faq.a}
                          </motion.div>
                        )}
                      </AnimatePresence>
                    </div>
                  );
                })}
              </div>
            </div>
          </section>
        </main>

        <LandingBottomCtaAndFooter />
      </div>
    </div>
  );
}
