import { motion } from "framer-motion";
import { 
  Languages, 
  Bot, 
  MessageSquarePlus, 
  Zap, 
  Landmark, 
  Image
} from "lucide-react";
import PageNavbar from "../components/layout/PageNavbar";
import LandingBottomCtaAndFooter from "../components/landing/LandingBottomCtaAndFooter";

export default function UpcomingUpdates() {

  const updates = [
    {
      icon: <Languages className="w-5 h-5 sm:w-6 sm:h-6" />,
      title: "Regional Language Support",
      description: "Support for Marathi, Hindi, Kannada, Tamil, and other regional languages using translation APIs so every Indian citizen can participate in their mother tongue.",
      status: "Done",
      color: "from-emerald-500 to-teal-600",
      bgLight: "bg-emerald-500/10"
    },
    {
      icon: <Bot className="w-5 h-5 sm:w-6 sm:h-6" />,
      title: "Hyperlocal AI Assistant",
      description: "An AI agent that automatically translates civic complaints into formal drafts, categorizes issues, and matches them with corresponding government departments.",
      status: "Planning",
      color: "from-blue-550 to-indigo-600",
      bgLight: "bg-blue-500/10"
    },
    {
      icon: <MessageSquarePlus className="w-5 h-5 sm:w-6 sm:h-6" />,
      title: "Ephemeral Group Chats",
      description: "Real-time, self-cleaning chat rooms dedicated to active local updates (e.g., live traffic congestion, municipal repairs, water outages) with automatic message expiry.",
      status: "In Development",
      color: "from-amber-500 to-orange-600",
      bgLight: "bg-amber-500/10"
    },
    {
      icon: <Image className="w-5 h-5 sm:w-6 sm:h-6" />,
      title: "Media Uploads in Community Chats",
      description: "Share images, videos, and documents directly within community chats to provide visual context and report issues with proof in real-time.",
      status: "In Progress",
      color: "from-purple-500 to-indigo-600",
      bgLight: "bg-purple-500/10"
    },
    {
      icon: <Landmark className="w-5 h-5 sm:w-6 sm:h-6" />,
      title: "Official PG-Portal Sync",
      description: "Direct API pipelines to standard grievance management systems (CPGRAMS) to route highly-voted citizen issues directly to designated state officers.",
      status: "Planning",
      color: "from-blue-550 to-indigo-600",
      bgLight: "bg-blue-500/10"
    },
    {
      icon: <Zap className="w-5 h-5 sm:w-6 sm:h-6" />,
      title: "Verified RWA Dashboards",
      description: "Custom admin dashboards for Resident Welfare Associations (RWAs) and ward committee members to address and resolve local queries directly.",
      status: "In Progress",
      color: "from-purple-500 to-indigo-600",
      bgLight: "bg-purple-500/10"
    }
  ];

  const getStatusBadgeClass = (status: string) => {
    if (status === "Done") {
      return "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20";
    }
    if (status === "In Development" || status === "In Progress") {
      return "bg-[#1D4ED8]/10 text-[#1D4ED8] dark:text-blue-400 border border-[#1D4ED8]/25";
    }
    return "bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20";
  };

  const getIconClass = (status: string) => {
    return getStatusBadgeClass(status);
  };

  return (
    <div className="h-screen bg-base-100 text-slate-800 dark:text-slate-200 selection:bg-blue-600/30 transition-colors duration-300 flex flex-col relative overflow-hidden">
      {/* ─── Navbar ──────────────────────────────────────────────────────────── */}
      <PageNavbar active="updates" />


      {/* Scrollable Container Wrapper */}
      <div className="flex-1 min-h-0 overflow-y-auto flex flex-col justify-between z-10">
        {/* Main Content */}
        <main className="w-full mx-auto max-w-[1000px] px-3.5 sm:px-6 py-6 sm:py-16 relative">
          <div className="text-left mb-8 sm:mb-20 pb-2 sm:pb-4 max-w-3xl">
            <div className="mb-2 sm:mb-6">
              <h1 className="text-2xl sm:text-5xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                Upcoming Updates
              </h1>
            </div>
            <p className="text-slate-500 dark:text-slate-400 mt-2 sm:mt-4 text-xs sm:text-base leading-relaxed font-medium">
              Explore the evolution roadmap of Govlyx. We are actively scaling features to connect municipal wards, regional groups, and official channels.
            </p>
          </div>

          {/* Timeline Section */}
          <div className="relative w-full py-4 sm:py-8">
            
            {/* Desktop Horizontal Snake/Zigzag Roadmap */}
            <div className="hidden lg:block relative w-full h-[480px] max-w-[1000px] mx-auto z-10">
              
              {/* SVG Curved Pathway Line (Horizontal Wavy Snake Line) */}
              <svg className="absolute inset-0 w-full h-full pointer-events-none z-0" viewBox="0 0 1000 500" fill="none">
                <path 
                  d="M 52 302 C 132 302, 132 82, 212 82 C 292 82, 292 302, 372 302 C 452 302, 452 82, 532 82 C 612 82, 612 302, 692 302 C 772 302, 772 82, 852 82" 
                  stroke="currentColor" 
                  strokeWidth="3.5" 
                  strokeLinecap="round"
                  strokeDasharray="1,10" 
                  className="text-black/35 dark:text-white/25 transition-colors duration-300"
                />
              </svg>

              {/* Node 1: Regional Language Support (Bottom) */}
              <div className="absolute" style={{ left: "2%", top: "270px" }}>
                <div className="w-16 h-16 rounded-full border-4 border-emerald-500 bg-white dark:bg-[#121829] flex items-center justify-center text-emerald-500 shadow-lg hover:scale-110 transition-transform duration-300">
                  <Languages className="w-7 h-7" />
                </div>
                <div className="absolute top-20 left-1/2 -translate-x-1/2 text-center space-y-1 bg-base-100 z-10 px-2 py-1.5 rounded-xl w-[200px]">
                  <span className="text-[9px] font-black uppercase tracking-widest text-emerald-600 dark:text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-md border border-emerald-500/20">Done</span>
                  <h3 className="font-extrabold text-slate-855 dark:text-white text-xs mt-1">Regional Language</h3>
                  <p className="text-[10px] text-slate-500 dark:text-slate-400 leading-relaxed mt-0.5">Support for Hindi, Tamil, Marathi, etc. using translation APIs.</p>
                </div>
              </div>

              {/* Node 2: Hyperlocal AI Assistant (Top) */}
              <div className="absolute" style={{ left: "18%", top: "50px" }}>
                <div className="w-16 h-16 rounded-full border-4 border-orange-500 bg-white dark:bg-[#121829] flex items-center justify-center text-orange-500 shadow-lg hover:scale-110 transition-transform duration-300">
                  <Bot className="w-7 h-7" />
                </div>
                <div className="absolute bottom-20 left-1/2 -translate-x-1/2 text-center space-y-1 bg-base-100 z-10 px-2 py-1.5 rounded-xl w-[200px]">
                  <span className="text-[9px] font-black uppercase tracking-widest text-orange-605 dark:text-orange-400 bg-orange-500/10 px-2 py-0.5 rounded-md border border-orange-500/20">Planning</span>
                  <h3 className="font-extrabold text-slate-855 dark:text-white text-xs mt-1">AI Assistant</h3>
                  <p className="text-[10px] text-slate-505 dark:text-slate-400 leading-relaxed mt-0.5">Automatically translates civic complaints into formal drafts.</p>
                </div>
              </div>

              {/* Node 3: Ephemeral Group Chats (Bottom) */}
              <div className="absolute" style={{ left: "34%", top: "270px" }}>
                <div className="w-16 h-16 rounded-full border-4 border-green-500 bg-white dark:bg-[#121829] flex items-center justify-center text-green-500 shadow-lg hover:scale-110 transition-transform duration-300">
                  <MessageSquarePlus className="w-7 h-7" />
                </div>
                <div className="absolute top-20 left-1/2 -translate-x-1/2 text-center space-y-1 bg-base-100 z-10 px-2 py-1.5 rounded-xl w-[200px]">
                  <span className="text-[9px] font-black uppercase tracking-widest text-[#1D4ED8] dark:text-blue-400 bg-blue-500/10 px-2 py-0.5 rounded-md border border-blue-500/20">Development</span>
                  <h3 className="font-extrabold text-slate-855 dark:text-white text-xs mt-1">Ephemeral Chats</h3>
                  <p className="text-[10px] text-slate-505 dark:text-slate-400 leading-relaxed mt-0.5">Real-time local updates and repair group chats with expiry.</p>
                </div>
              </div>

              {/* Node 4: Media Uploads in Community (Top) */}
              <div className="absolute" style={{ left: "50%", top: "50px" }}>
                <div className="w-16 h-16 rounded-full border-4 border-blue-500 bg-white dark:bg-[#121829] flex items-center justify-center text-blue-500 shadow-lg hover:scale-110 transition-transform duration-300">
                  <Image className="w-7 h-7" />
                </div>
                <div className="absolute bottom-20 left-1/2 -translate-x-1/2 text-center space-y-1 bg-base-100 z-10 px-2 py-1.5 rounded-xl w-[200px]">
                  <span className="text-[9px] font-black uppercase tracking-widest text-[#1D4ED8] dark:text-blue-400 bg-blue-500/10 px-2 py-0.5 rounded-md border border-blue-500/20">Progress</span>
                  <h3 className="font-extrabold text-slate-855 dark:text-white text-xs mt-1">Media Uploads</h3>
                  <p className="text-[10px] text-slate-505 dark:text-slate-400 leading-relaxed mt-0.5">Share images, videos, and proof directly in ward groups.</p>
                </div>
              </div>

              {/* Node 5: Official PG-Portal Sync (Bottom) */}
              <div className="absolute" style={{ left: "66%", top: "270px" }}>
                <div className="w-16 h-16 rounded-full border-4 border-indigo-500 bg-white dark:bg-[#121829] flex items-center justify-center text-indigo-500 shadow-lg hover:scale-110 transition-transform duration-300">
                  <Landmark className="w-7 h-7" />
                </div>
                <div className="absolute top-20 left-1/2 -translate-x-1/2 text-center space-y-1 bg-base-100 z-10 px-2 py-1.5 rounded-xl w-[200px]">
                  <span className="text-[9px] font-black uppercase tracking-widest text-orange-605 dark:text-orange-400 bg-orange-500/10 px-2 py-0.5 rounded-md border border-orange-500/20">Planning</span>
                  <h3 className="font-extrabold text-slate-855 dark:text-white text-xs mt-1">PG-Portal Sync</h3>
                  <p className="text-[10px] text-slate-505 dark:text-slate-400 leading-relaxed mt-0.5">Direct CPGRAMS API pipelines to route verified civic complaints.</p>
                </div>
              </div>

              {/* Node 6: Verified RWA Dashboards (Top) */}
              <div className="absolute" style={{ left: "82%", top: "50px" }}>
                <div className="w-16 h-16 rounded-full border-4 border-slate-400 bg-white dark:bg-[#121829] flex items-center justify-center text-slate-450 shadow-lg hover:scale-110 transition-transform duration-300">
                  <Zap className="w-7 h-7" />
                </div>
                <div className="absolute bottom-20 left-1/2 -translate-x-1/2 text-center space-y-1 bg-base-100 z-10 px-2 py-1.5 rounded-xl w-[200px]">
                  <span className="text-[9px] font-black uppercase tracking-widest text-[#1D4ED8] dark:text-blue-400 bg-blue-500/10 px-2 py-0.5 rounded-md border border-blue-500/20">Progress</span>
                  <h3 className="font-extrabold text-slate-855 dark:text-white text-xs mt-1">RWA Dashboards</h3>
                  <p className="text-[10px] text-slate-550 dark:text-slate-400 leading-relaxed mt-0.5">Ward dashboards for committee heads to resolve queries directly.</p>
                </div>
              </div>

            </div>

            {/* Mobile Vertical Timeline (Responsive fallback) */}
            <div className="block lg:hidden relative w-full">
              {/* Centered vertical line */}
              <div className="absolute left-5 sm:left-8 top-4 bottom-4 w-0.5 bg-gradient-to-b from-blue-600/50 via-purple-600/30 to-slate-200 dark:to-slate-800 -translate-x-1/2 z-0" />

              <div className="space-y-8 sm:space-y-12 relative z-10">
                {updates.map((item, index) => {
                  return (
                    <motion.div
                      key={index}
                      initial={{ opacity: 0, y: 20 }}
                      whileInView={{ opacity: 1, y: 0 }}
                      viewport={{ once: true }}
                      className="flex items-start gap-3 sm:gap-4 relative pl-10 sm:pl-16"
                    >
                      {/* Connection node indicator */}
                      <div className="absolute left-5 sm:left-8 transform -translate-x-1/2 flex items-center justify-center top-2.5 sm:top-3 z-20">
                        <div className="w-6 h-6 sm:w-8 sm:h-8 rounded-full bg-white dark:bg-slate-900 border-2 border-slate-200 dark:border-slate-800 shadow-md flex items-center justify-center font-black text-[10px] sm:text-xs text-blue-650 dark:text-blue-400">
                          {index + 1}
                        </div>
                      </div>

                      <div className="w-full bg-base-100/70 dark:bg-[#121829]/65 border border-transparent p-3.5 sm:p-5 rounded-xl sm:rounded-2xl shadow-[0_6px_0_0_rgba(0,0,0,0.08)] dark:shadow-[0_6px_0_0_rgba(255,255,255,0.03)] relative group transition-all duration-300">
                        <div className="flex gap-2.5 sm:gap-3.5 items-start">
                          <div className={`p-1.5 sm:p-2.5 rounded-lg sm:rounded-xl shrink-0 ${getIconClass(item.status)}`}>
                            {item.icon}
                          </div>
                          <div className="space-y-1.5 sm:space-y-2 flex-1">
                            <div className="flex justify-between items-center gap-2 flex-wrap">
                              <h3 className="font-extrabold text-slate-850 dark:text-white text-xs sm:text-sm md:text-base">
                                {item.title}
                              </h3>
                              <span className={`text-[8px] font-black uppercase tracking-widest px-2 py-0.5 rounded ${getStatusBadgeClass(item.status)}`}>
                                {item.status}
                              </span>
                            </div>
                            <p className="text-slate-500 dark:text-slate-400 text-[11px] sm:text-xs md:text-sm leading-relaxed">
                              {item.description}
                            </p>
                          </div>
                        </div>
                      </div>
                    </motion.div>
                  );
                })}
              </div>
            </div>

          </div>
        </main>

        <LandingBottomCtaAndFooter />
      </div>
    </div>
  );
}
