import { BarChart2, Inbox, Megaphone, ShieldCheck, ChevronRight, Book } from "lucide-react";
import { motion } from "framer-motion";
import type { DashboardTab } from "../../types/department";

interface Props {
  activeTab: DashboardTab;
  onTabChange: (tab: DashboardTab) => void;
  activeBadge?: number;
}

const TABS: { key: DashboardTab; label: string; icon: React.ElementType; description: string }[] = [
  {
    key: "issues",
    label: "Issues Inbox",
    icon: Inbox,
    description: "Tagged citizen requests",
  },
  {
    key: "broadcasts",
    label: "Broadcasts",
    icon: Megaphone,
    description: "Publish announcements",
  },
  {
    key: "analytics",
    label: "Analytics",
    icon: BarChart2,
    description: "Resolution & reach data",
  },
];

const DepartmentSidebar = ({ activeTab, onTabChange, activeBadge }: Props) => {
  return (
    <aside className="flex flex-col gap-4 h-full">
      {/* Premium Header */}
      <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-[#1D4ED8] via-[#1e40af] to-[#1e3a8a] p-5 text-white shadow-xl shadow-[#1D4ED8]/20 border border-white/10">
        {/* Subtle decorative circle */}
        <div className="absolute -right-4 -top-4 h-24 w-24 rounded-full bg-white/5 blur-2xl" />
        
        <div className="relative z-10 flex items-center gap-4 mb-4">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-white/15 backdrop-blur-md border border-white/20 shadow-inner">
            <ShieldCheck size={22} className="text-blue-100" />
          </div>
          <div>
            <p className="text-[10px] font-bold opacity-60 uppercase tracking-[0.15em]">Department Portal</p>
            <p className="text-base font-extrabold leading-tight tracking-tight">Control Panel</p>
          </div>
        </div>

        {activeBadge !== undefined && activeBadge > 0 && (
          <div className="relative z-10 flex items-center gap-2.5 rounded-xl bg-white/10 backdrop-blur-sm px-3 py-2 border border-white/5">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-amber-400"></span>
            </span>
            <span className="text-[11px] font-bold tracking-wide">
              {activeBadge} PENDING ISSUE{activeBadge !== 1 ? "S" : ""}
            </span>
          </div>
        )}
      </div>

      {/* Modern Navigation */}
      <nav className="rounded-2xl bg-base-100 border border-base-300 p-2 flex flex-col gap-1 shadow-sm">
        {TABS.map(({ key, label, icon: Icon, description }) => {
          const isActive = activeTab === key;
          return (
            <button
              key={key}
              id={`dept-tab-${key}`}
              onClick={() => onTabChange(key)}
              className={`group relative flex items-center gap-3.5 rounded-xl px-4 py-3.5 text-left transition-all w-full
                ${isActive
                  ? "bg-[#1D4ED8] text-white shadow-lg shadow-[#1D4ED8]/25"
                  : "hover:bg-base-200 text-base-content/70 hover:text-base-content"
                }`}
            >
              <div className={`flex h-8 w-8 items-center justify-center rounded-lg transition-colors
                ${isActive ? "bg-white/15" : "bg-base-200 group-hover:bg-base-300"}`}>
                <Icon
                  size={16}
                  className={`transition-transform group-hover:scale-110 ${isActive ? "text-white" : "opacity-70"}`}
                />
              </div>
              
              <div className="flex-1 min-w-0">
                <p className={`text-[13px] font-bold leading-none ${isActive ? "text-white" : ""}`}>
                  {label}
                </p>
                <p className={`mt-1 text-[10px] font-medium leading-none ${isActive ? "text-white/60" : "opacity-40"}`}>
                  {description}
                </p>
              </div>

              {key === "issues" && activeBadge !== undefined && activeBadge > 0 && !isActive && (
                <span className="flex h-5 min-w-[20px] items-center justify-center rounded-full bg-amber-400 px-1.5 text-[9px] font-black text-black shadow-sm">
                  {activeBadge > 99 ? "99+" : activeBadge}
                </span>
              )}

              {isActive && (
                <motion.div
                  layoutId="active-indicator"
                  className="absolute right-3"
                  initial={{ opacity: 0, x: -5 }}
                  animate={{ opacity: 1, x: 0 }}
                >
                  <ChevronRight size={14} className="text-white/40" />
                </motion.div>
              )}
            </button>
          );
        })}
      </nav>

      {/* Polished Help/Guide Card */}
      <div className="mt-auto relative group overflow-hidden rounded-2xl border border-base-300 bg-base-200/40 p-5 transition-all hover:bg-base-200/60">
        <div className="absolute top-0 left-0 w-1 h-full bg-[#1D4ED8] opacity-20 group-hover:opacity-100 transition-opacity" />
        
        <div className="flex items-center gap-2 mb-3">
          <div className="h-5 w-5 rounded bg-[#1D4ED8]/10 flex items-center justify-center">
            <Book size={10} className="text-[#1D4ED8]" />
          </div>
          <p className="text-[11px] font-extrabold uppercase tracking-widest opacity-60">Quick Guide</p>
        </div>

        <ul className="space-y-3">
          {[
            { text: "Check Inbox", sub: "Review newest citizen requests daily" },
            { text: "Resolve Issues", sub: "Update status once work is completed" },
            { text: "Broadcast Info", sub: "Push official alerts to city feeds" }
          ].map((item, i) => (
            <li key={i} className="flex gap-2.5">
              <div className="mt-1 h-1 w-1 shrink-0 rounded-full bg-[#1D4ED8]" />
              <div className="space-y-0.5">
                <p className="text-[11px] font-bold leading-tight opacity-80">{item.text}</p>
                <p className="text-[10px] leading-tight opacity-40">{item.sub}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </aside>
  );
};

export default DepartmentSidebar;
