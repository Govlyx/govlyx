import React, { useState, useRef, useEffect } from "react";
import { AlertCircle, Sparkles, Activity, ChevronDown, Check } from "lucide-react";

type Tab = "posts" | "social" | "activity";

type Props = {
  active: Tab;
  onChange: (tab: Tab) => void;
  issueCount?: number | null;
  socialCount?: number | null;
  activityCount?: number | null;
};

const ProfileTabs = ({ active, onChange, issueCount, socialCount, activityCount }: Props) => {
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const tabs: { key: Tab; label: string; count?: number | null; icon: React.ComponentType<{ size: number; className?: string }> }[] = [
    { key: "posts", label: "Issues", count: issueCount, icon: AlertCircle },
    { key: "social", label: "Social", count: socialCount, icon: Sparkles },
    { key: "activity", label: "Activity", count: activityCount, icon: Activity },
  ];

  const activeTabObj = tabs.find((t) => t.key === active) || tabs[0];
  const ActiveIcon = activeTabObj.icon;

  return (
    <>
      {/* ── Mobile Menu Bar Dropdown (< sm) ── */}
      <div ref={dropdownRef} className="sm:hidden notranslate text-left relative z-30 w-full">
        <button
          type="button"
          onClick={() => setDropdownOpen((prev) => !prev)}
          className="w-full bg-base-200 border border-black/10 dark:border-base-300 rounded-2xl px-4 py-2.5 text-xs font-bold text-slate-800 dark:text-slate-200 flex items-center justify-between shadow-xs outline-none focus:outline-none cursor-pointer"
        >
          <div className="flex items-center gap-2">
            <ActiveIcon size={15} className="text-[#1D4ED8]" />
            <span>{activeTabObj.label}</span>
            {activeTabObj.count !== undefined && activeTabObj.count !== null && (
              <span className="text-[10px] px-1.5 py-0.2 rounded-full font-black bg-[#1D4ED8] text-white">
                {activeTabObj.count}
              </span>
            )}
          </div>
          <ChevronDown
            size={15}
            className={`text-slate-400 transition-transform duration-200 ${
              dropdownOpen ? "rotate-180 text-[#1D4ED8]" : ""
            }`}
          />
        </button>

        {dropdownOpen && (
          <div className="absolute top-full mt-1.5 left-0 right-0 z-50 rounded-2xl border border-black/10 dark:border-base-300 bg-base-100 dark:bg-base-200 backdrop-blur-md shadow-2xl p-1.5 space-y-1 animate-in fade-in zoom-in-95 duration-150">
            {tabs.map((tab) => {
              const isSelected = active === tab.key;
              const Icon = tab.icon;
              return (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => {
                    onChange(tab.key);
                    setDropdownOpen(false);
                  }}
                  className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer text-left outline-none focus:outline-none ${
                    isSelected
                      ? "bg-[#1D4ED8] text-white shadow-xs font-bold"
                      : "text-slate-700 dark:text-slate-200 hover:bg-base-300/60"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <Icon size={14} className={isSelected ? "text-white" : "text-slate-400"} />
                    <span>{tab.label}</span>
                    {tab.count !== undefined && tab.count !== null && (
                      <span
                        className={`text-[10px] px-1.5 py-0.2 rounded-full font-black ${
                          isSelected ? "bg-white/20 text-white" : "bg-base-300 text-slate-600 dark:text-slate-300"
                        }`}
                      >
                        {tab.count}
                      </span>
                    )}
                  </div>
                  {isSelected && <Check size={14} className="stroke-[2.5]" />}
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* ── Desktop Filter Tabs (>= sm) ── */}
      <div 
        className="hidden sm:flex items-center gap-2.5 overflow-x-auto pb-1 scrollbar-none scrollbar-hide hide-scrollbar notranslate text-left select-none w-full"
        style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
      >
        {tabs.map((tab) => {
          const isSelected = active === tab.key;
          const Icon = tab.icon;
          return (
            <button
              key={tab.key}
              onClick={() => onChange(tab.key)}
              className={`px-4 py-2 rounded-2xl text-xs sm:text-sm font-bold transition-all shrink-0 cursor-pointer outline-none focus:outline-none focus-visible:outline-none focus:ring-0 flex items-center gap-2 ${
                isSelected
                  ? "bg-[#1D4ED8] text-white shadow-xs"
                  : "bg-base-200 dark:bg-zinc-900/90 hover:bg-base-300 dark:hover:bg-zinc-800 text-slate-700 dark:text-slate-200 border border-black/10 dark:border-white/10"
              }`}
            >
              <Icon size={15} className={`shrink-0 ${isSelected ? "text-white" : "text-slate-400 dark:text-zinc-400"}`} />
              <span>{tab.label}</span>
              {tab.count !== undefined && tab.count !== null && (
                <span
                  className={`text-xs font-bold px-2 py-0.5 rounded-full ${
                    isSelected ? "bg-white/20 text-white" : "bg-base-300 dark:bg-zinc-800 text-slate-600 dark:text-zinc-300"
                  }`}
                >
                  {tab.count}
                </span>
              )}
            </button>
          );
        })}
      </div>
    </>
  );
};

export default ProfileTabs;