import { useState } from "react";
import { NavLink, useLocation, Link, useNavigate } from "react-router-dom";
import {
  Home,
  Users,
  User,
  Settings,
  LayoutDashboard,
  Bell,
  Inbox,
  Megaphone,
  BarChart2,
  LogOut,
  HelpCircle,
} from "lucide-react";
import { useTheme } from "../../hooks/useTheme";
import { useCurrentUser } from "../../hooks/useUser";
import { useUnreadNotificationsCount } from "../../hooks/useNotification";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { resolveMediaUrl } from "../../utils/postUtils";
import { getUserRole, getAuthToken, clearAuthTokens } from "../../utils/auth";
import axiosInstance from "../../api/axiosConfig";
import { getActiveTaggedPosts } from "../../api/departmentService";
import ConfirmModal from "../post/ConfirmModal";
import ThemeToggle from "../ui/ThemeToggle";

const QUICK_CHAT_LIGHT_ICON = "/icons/quick_chat_light_theme.gif";
const QUICK_CHAT_DARK_ICON = "/icons/quick_chat_dark_theme.gif";

const BASE_NAV_ITEMS = [
  { label: "Home", icon: Home, to: "/dashboard" },
  { label: "Communities", icon: Users, to: "/communities" },
  { label: "Notifications", icon: Bell, to: "/notifications" },
  { label: "Quick Chat", icon: null, to: "/quick-chat" },
  { label: "Profile", icon: User, to: "/profile" },
  { label: "Settings", icon: Settings, to: "/settings" },
];

const ADMIN_NAV_ITEM = { label: "Admin Dashboard", icon: LayoutDashboard, to: "/admin/dashboard" };

// Admin nav sections shown in the left sidebar when on the admin dashboard
const ADMIN_NAV_SECTIONS = [
  {
    label: "Overview",
    items: [
      {
        label: "Dashboard", tab: "dashboard",
        icon: (
          <svg className="w-[18px] h-[18px] flex-shrink-0 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.8">
            <rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" />
            <rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" />
          </svg>
        ),
      },
    ],
  },
  {
    label: "Registration",
    items: [
      {
        label: "Register Department", tab: "reg-dept",
        icon: (
          <svg className="w-[18px] h-[18px] flex-shrink-0 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.8">
            <path d="M3 21h18M3 10h18M5 6l7-3 7 3M4 10v11M20 10v11M8 10v11M12 10v11M16 10v11" />
          </svg>
        ),
      },
      {
        label: "Register Admin", tab: "reg-admin",
        icon: (
          <svg className="w-[18px] h-[18px] flex-shrink-0 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.8">
            <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
            <line x1="19" y1="8" x2="19" y2="14" /><line x1="22" y1="11" x2="16" y2="11" />
          </svg>
        ),
      },
    ],
  },
  {
    label: "User Management",
    items: [
      {
        label: "All Users", tab: "users",
        icon: (
          <svg className="w-[18px] h-[18px] flex-shrink-0 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.8">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
            <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
          </svg>
        ),
      },
      {
        label: "Departments", tab: "departments",
        icon: (
          <svg className="w-[18px] h-[18px] flex-shrink-0 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.8">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
          </svg>
        ),
      },
      {
        label: "Communities", tab: "communities",
        icon: (
          <svg className="w-[18px] h-[18px] flex-shrink-0 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.8">
            <path d="M17 20h5v-2a3 3 0 0 0-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 0 1 5.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 0 1 9.288 0M15 7a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm6 3a2 2 0 1 1-4 0 2 2 0 0 1 4 0ZM7 10a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z" />
          </svg>
        ),
      },
    ],
  },
  {
    label: "Platform",
    items: [
      {
        label: "Content Monitor", tab: "content",
        icon: (
          <svg className="w-[18px] h-[18px] flex-shrink-0 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.8">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
            <line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" />
          </svg>
        ),
      },
      {
        label: "Chat Statistics", tab: "chat",
        icon: (
          <svg className="w-[18px] h-[18px] flex-shrink-0 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.8">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        ),
      },
      {
        label: "Broadcasts", tab: "broadcast",
        icon: (
          <svg className="w-[18px] h-[18px] flex-shrink-0 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.8">
            <circle cx="12" cy="12" r="2"/><path d="M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14"/>
          </svg>
        ),
      },
      {
        label: "Copyright Claims", tab: "copyright",
        icon: (
          <svg className="w-[18px] h-[18px] flex-shrink-0 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.8">
            <path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z" strokeLinecap="round" strokeLinejoin="round" />
            <path d="M14.25 14.25a3 3 0 0 1-4.5 0v-4.5a3 3 0 0 1 4.5 0" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        ),
      },
    ],
  },
  {
    label: "System",
    items: [
      {
        label: "System Health", tab: "system",
        icon: (
          <svg className="w-[18px] h-[18px] flex-shrink-0 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.8">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
          </svg>
        ),
      },
    ],
  },
];

const CommunityAvatarItem = ({ name, avatarUrl }: { name: string; avatarUrl?: string | null }) => {
  const [imgError, setImgError] = useState(false);
  const resolved = avatarUrl ? resolveMediaUrl(avatarUrl, "social-posts") : null;

  if (resolved && !imgError) {
    return (
      <img
        src={resolved}
        alt=""
        className="w-full h-full object-cover"
        onError={() => setImgError(true)}
      />
    );
  }

  const initial = name?.trim()?.charAt(0)?.toUpperCase() || "C";
  return (
    <span className="text-[10px] font-black text-primary select-none">
      {initial}
    </span>
  );
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------
const SidebarLeft = () => {
  const loggedIn = !!getAuthToken() || localStorage.getItem("isLoggedIn") === "true";
  const { data: user, isLoading: loading } = useCurrentUser({ enabled: loggedIn });
  const { data: unreadCount } = useUnreadNotificationsCount({ enabled: loggedIn });
  const { theme, toggleTheme } = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  const handleLogout = () => {
    setShowLogoutConfirm(false);
    clearAuthTokens();
    queryClient.clear();
    navigate("/login", { replace: true });
  };

  // Fetch actual user communities (both joined and owned)
  const { data: communities, isLoading: communitiesLoading } = useQuery({
    queryKey: ["my-communities"],
    queryFn: async () => {
      const [joinedRes, ownedRes] = await Promise.allSettled([
        axiosInstance.get("/api/communities/me?limit=100"),
        axiosInstance.get("/api/communities/owned"),
      ]);

      const parseCommunitiesList = (res: any): any[] => {
        if (!res) return [];
        const payload = res?.data !== undefined ? res.data : res;
        const candidates = [
          payload?.data?.content,
          payload?.data?.data,
          payload?.data?.items,
          payload?.content,
          payload?.data,
          payload?.items,
          payload,
        ];
        for (const candidate of candidates) {
          if (Array.isArray(candidate)) return candidate;
        }
        return [];
      };

      let joined: any[] = [];
      if (joinedRes.status === "fulfilled" && (joinedRes.value.status === 200 || joinedRes.value.status === 201)) {
        joined = parseCommunitiesList(joinedRes.value.data);
      }

      let owned: any[] = [];
      if (ownedRes.status === "fulfilled" && (ownedRes.value.status === 200 || ownedRes.value.status === 201)) {
        const rawOwnedList = parseCommunitiesList(ownedRes.value.data);
        owned = rawOwnedList.map((c: any) => ({
          ...c,
          isOwner: true,
          role: "OWNER",
          currentUserRole: "OWNER",
        }));
      }

      let localDeletions: Record<string, any> = {};
      try {
        localDeletions = JSON.parse(localStorage.getItem("govlyx_deleted_communities") || "{}");
      } catch {}

      const seen = new Set<number>();
      const merged: any[] = [];
      for (const c of [...owned, ...joined]) {
        const isDeleted = c?.isDeleted === true || !!c?.deletedAt || !!c?.scheduledDeletionDate || !!c?.deletionDueDate || !!localDeletions[c?.id];
        if (c?.id && !seen.has(c.id) && !isDeleted) {
          seen.add(c.id);
          merged.push(c);
        }
      }
      return merged;
    },
    enabled: loggedIn,
  });

  // Check role from JWT token which is the source of truth
  const role = getUserRole();
  const isDept = role === "ROLE_DEPARTMENT";
  const isAdmin = role === "ROLE_ADMIN";
  const username = user?.actualUsername ?? user?.username;

  // Fetch active issues count dynamically if user is a department
  const { data: activeIssuesCount } = useQuery({
    queryKey: ["active-issues-count", username],
    queryFn: async () => {
      if (!username) return 0;
      try {
        const page = await getActiveTaggedPosts(username, null, 1);
        return page.totalCount;
      } catch {
        return 0;
      }
    },
    enabled: isDept && !!username,
    refetchInterval: 30 * 1000,
  });

  const navItems = isDept
    ? [
        { label: "Issues Inbox", icon: Inbox, to: "/department/dashboard?tab=issues" },
        { label: "Official Broadcasts", icon: Megaphone, to: "/department/dashboard?tab=broadcasts" },
        { label: "Analytics", icon: BarChart2, to: "/department/dashboard?tab=analytics" },
        { label: "Notifications", icon: Bell, to: "/notifications" },
        { label: "Profile", icon: User, to: "/profile" },
        { label: "Settings", icon: Settings, to: "/settings" }
      ]
    : isAdmin
    ? [
        ADMIN_NAV_ITEM,
        { label: "Notifications", icon: Bell, to: "/notifications" },
        { label: "Profile", icon: User, to: "/profile" },
        { label: "Settings", icon: Settings, to: "/settings" }
      ]
    : loggedIn
    ? BASE_NAV_ITEMS
    : BASE_NAV_ITEMS.filter(item => item.label === "Home" || item.label === "Communities");

  // Removing unused avatarLetter
  const displayName = loading ? "Loading..." : (username ?? "Anonymous User");

  const searchParamsObj = new URLSearchParams(location.search);
  const activeTabParam = searchParamsObj.get("tab") || "issues";

  // For admin dashboard: determine which tab is active from the URL
  const isOnAdminDashboard = isAdmin && location.pathname.startsWith("/admin/dashboard");
  const adminActiveTab = isOnAdminDashboard ? (searchParamsObj.get("tab") || "dashboard") : null;

  // nav item base classes
  const navItemBase = "flex items-center gap-2.5 rounded-lg px-2.5 py-1.5 text-[13px] font-bold transition relative";
  const navItemActive = "bg-[#1D4ED8] text-white font-bold shadow-sm";
  const navItemInactive = "hover:bg-base-300/80 text-base-content/85 font-bold";

  const handleNavClick = (_e: React.MouseEvent, label: string) => {
    const isHome = label === "Home" && (location.pathname === "/dashboard" || location.pathname === "/");
    const isCommunities = label === "Communities" && location.pathname.startsWith("/communities");

    if (isHome) {
      const mainEl = document.querySelector("main.overflow-y-auto");
      if (mainEl) {
        mainEl.scrollTo({ top: 0, behavior: "smooth" });
      } else {
        window.scrollTo({ top: 0, behavior: "smooth" });
      }
      window.dispatchEvent(new CustomEvent("refreshActiveFeed", { detail: { target: "home" } }));
    } else if (isCommunities) {
      const mainEl = document.querySelector("main.overflow-y-auto");
      if (mainEl) {
        mainEl.scrollTo({ top: 0, behavior: "smooth" });
      } else {
        window.scrollTo({ top: 0, behavior: "smooth" });
      }
      window.dispatchEvent(new CustomEvent("refreshActiveFeed", { detail: { target: "communities" } }));
    }
  };

  return (
    <aside className="flex min-h-full flex-col gap-2 pb-2 justify-between">
      <div className="flex flex-col gap-2">
        {/* Profile Card or Login CTA */}
        {loggedIn ? (
          <div className="rounded-xl bg-base-200 px-3 py-2 shadow-sm border border-base-content/5">
            <div className="flex flex-row items-center justify-start text-left gap-2.5">
              <div className="avatar placeholder relative shrink-0">
                <div className="w-9 h-9 rounded-full overflow-hidden bg-base-200 border-2 transition-all border-[#1D4ED8] dark:border-white/80 shadow-sm">
                  <img src={resolveMediaUrl(user?.profileImage, "social-posts") || `https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(displayName)}`} alt="Avatar" className="w-full h-full object-cover" />
                </div>
              </div>
              <div className="flex-1 min-w-0 flex flex-col items-start">
                <p className="font-bold text-[13px] truncate max-w-full notranslate leading-tight" title={displayName}>{displayName}</p>
                {isAdmin && <p className="text-[10px] font-mono uppercase tracking-wider text-green-500 opacity-80 mt-0.5 leading-none">Admin</p>}
                {isDept && <p className="text-[10px] font-mono uppercase tracking-wider text-blue-500 opacity-80 mt-0.5 leading-none">Dept</p>}
              </div>
            </div>
          </div>
        ) : (
          <div className="rounded-xl bg-base-200 p-2.5 shadow-sm border border-base-content/5 flex flex-col gap-1.5 items-center justify-center text-center">
            <p className="text-xs opacity-60 font-semibold">Join the community</p>
            <Link to="/login" className="btn btn-xs bg-[#1D4ED8] text-white w-full h-7 min-h-0 text-xs">
              Login / Register
            </Link>
          </div>
        )}

        {/* ── ADMIN: Full nav sections visible ── */}
        {isAdmin ? (
          <>
            {ADMIN_NAV_SECTIONS.map(section => (
              <nav key={section.label} className="rounded-xl bg-base-200 p-1.5 space-y-0.5">
                <p className="text-[10px] uppercase tracking-widest font-semibold opacity-50 px-2.5 pt-0.5 pb-1">
                  {section.label}
                </p>
                {section.items.map(item => {
                  const isActive = adminActiveTab === item.tab;
                  return (
                    <NavLink
                      key={item.tab}
                      to={`/admin/dashboard?tab=${item.tab}`}
                      className={`${navItemBase} ${isActive ? navItemActive : navItemInactive}`}
                    >
                      {item.icon}
                      <span className="flex-1">{item.label}</span>
                    </NavLink>
                  );
                })}
              </nav>
            ))}

            {/* Utility links for admin */}
            <nav className="rounded-xl bg-base-200 p-1.5 space-y-0.5">
              <p className="text-[10px] uppercase tracking-widest font-semibold opacity-50 px-2.5 pt-0.5 pb-1">
                Account
              </p>
              <NavLink
                to="/notifications"
                className={`${navItemBase} ${location.pathname === "/notifications" ? navItemActive : navItemInactive}`}
              >
                <Bell size={17} />
                <span className="flex-1">Notifications</span>
                {unreadCount !== undefined && unreadCount > 0 && (
                  <span className="bg-error text-error-content text-[10px] font-bold rounded-full min-w-[16px] h-[16px] flex items-center justify-center px-1">
                    {unreadCount > 9 ? "9+" : unreadCount}
                  </span>
                )}
              </NavLink>
              <NavLink
                to="/profile"
                className={`${navItemBase} ${location.pathname === "/profile" ? navItemActive : navItemInactive}`}
              >
                <User size={17} />
                <span className="flex-1">Profile</span>
              </NavLink>
              <NavLink
                to="/settings"
                className={`${navItemBase} ${location.pathname === "/settings" ? navItemActive : navItemInactive}`}
              >
                <Settings size={17} />
                <span className="flex-1">Settings</span>
              </NavLink>
            </nav>
          </>
        ) : (
          /* ── Non-admin-dashboard: nav rendering ── */
          <nav className="rounded-xl bg-base-200 p-1.5 space-y-0.5">
            {navItems.map(({ label, icon: Icon, to }) => {
              const isNotifications = label === "Notifications";
              const isIssuesInbox = label === "Issues Inbox";
              const isQuickChat = label === "Quick Chat";

              const isDeptTabLink = to.startsWith("/department/dashboard");
              const isLinkActive = isDeptTabLink
                ? location.pathname === "/department/dashboard" &&
                  (new URLSearchParams(to.split("?")[1]).get("tab") || "issues") === activeTabParam
                : location.pathname === to;

              return (
                <NavLink
                  key={label}
                  to={to}
                  onClick={(e) => handleNavClick(e, label)}
                  className={`flex items-center gap-2.5 rounded-lg px-2.5 py-1.5 text-[13px] font-bold transition relative
                    ${isLinkActive
                      ? "bg-[#1D4ED8] text-white font-bold shadow-sm"
                      : "hover:bg-base-300/80 text-base-content/85 font-bold"
                    }`}
                >
                  {isQuickChat ? (
                    <img
                      src={theme === "dark" ? QUICK_CHAT_DARK_ICON : QUICK_CHAT_LIGHT_ICON}
                      alt=""
                      aria-hidden="true"
                      draggable={false}
                      onContextMenu={(event) => event.preventDefault()}
                      className="h-4.5 w-4.5 flex-shrink-0 rounded-[2px] object-contain"
                    />
                  ) : Icon ? (
                    <Icon size={17} />
                  ) : null}
                  <span className="flex-1">{label}</span>
                  {isNotifications && unreadCount !== undefined && unreadCount > 0 && (
                    <span className="bg-error text-error-content text-[10px] font-bold rounded-full min-w-[16px] h-[16px] flex items-center justify-center px-1">
                      {unreadCount > 9 ? "9+" : unreadCount}
                    </span>
                  )}
                  {isIssuesInbox && activeIssuesCount !== undefined && activeIssuesCount > 0 && (
                    <span className="bg-amber-400 text-black text-[10px] font-black rounded-full min-w-[16px] h-[16px] flex items-center justify-center px-1 shadow-sm">
                      {activeIssuesCount > 99 ? "99+" : activeIssuesCount}
                    </span>
                  )}
                </NavLink>
              );
            })}
          </nav>
        )}

        {/* Communities */}
        {loggedIn && !isAdmin && !isDept && (
          <div className="rounded-xl rounded-l-2xl bg-base-200 p-2.5 border-l-4 border-[#1D4ED8] shadow-xs">
            <div className="flex items-center justify-between mb-1.5 px-1">
              <p className="text-xs font-black opacity-80 uppercase tracking-wider text-slate-800 dark:text-slate-200">
                Your Communities
              </p>
              {communities && communities.length > 0 && (
                <span className="text-[10px] font-extrabold px-1.5 py-0.5 rounded-full bg-primary/15 text-primary">
                  {communities.length}
                </span>
              )}
            </div>
            {communitiesLoading ? (
              <div className="flex items-center justify-center py-2">
                <span className="loading loading-spinner loading-xs opacity-50" />
              </div>
            ) : communities && communities.length > 0 ? (
              <div>
                <ul className="space-y-1 text-[13px]">
                  {communities.slice(0, 5).map((c) => {
                    const activeCommunityParam = searchParamsObj.get("community");
                    const isActive =
                      location.pathname.startsWith("/communities") &&
                      (activeCommunityParam === c.slug ||
                        activeCommunityParam === String(c.id) ||
                        location.pathname === `/communities/${c.slug}` ||
                        location.pathname === `/communities/${c.id}`);

                    const avatarUrl =
                      c.avatarUrl || c.communityAvatar || c.avatar || c.communityLogo;

                    return (
                      <li key={c.id}>
                        <Link
                          to={`/communities?community=${c.slug || c.id}`}
                          className={`flex items-center gap-2 rounded-lg px-2 py-1 text-[13px] font-bold transition duration-150 hover:bg-base-300 notranslate ${
                            isActive ? "bg-base-300/60 text-[#1D4ED8]" : "text-base-content/85"
                          }`}
                        >
                          <div className="w-5 h-5 rounded-md overflow-hidden bg-primary/10 flex items-center justify-center shrink-0 border border-base-content/10">
                            <CommunityAvatarItem name={c.name} avatarUrl={avatarUrl} />
                          </div>
                          <span className="truncate flex-1">{c.name}</span>
                        </Link>
                      </li>
                    );
                  })}
                  {communities.length > 5 && (
                    <li className="pt-0.5">
                      <NavLink
                        to="/communities?tab=joined"
                        className="block truncate rounded-lg px-2 py-0.5 text-xs text-blue-600 font-bold transition-all duration-300 hover:text-blue-700 dark:hover:text-blue-400"
                      >
                        View All (+{communities.length - 5} more)
                      </NavLink>
                    </li>
                  )}
                </ul>
              </div>
            ) : (
              <div className="py-1 px-1">
                <p className="text-xs opacity-50 py-0.5 font-semibold">No joined communities yet.</p>
                <Link
                  to="/communities"
                  className="inline-flex items-center gap-1 text-xs font-bold text-[#1D4ED8] hover:underline mt-0.5"
                >
                  Discover Communities &rarr;
                </Link>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Mobile Theme Toggle */}
      <div className="lg:hidden mt-auto pt-2">
        <div
          role="button"
          tabIndex={0}
          onClick={toggleTheme}
          onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggleTheme(); } }}
          className="flex items-center gap-2.5 w-full px-2.5 py-2 rounded-xl bg-base-300/30 hover:bg-base-300 transition-colors duration-200 cursor-pointer select-none"
        >
          <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-base-100 shadow-sm text-slate-700 dark:text-white">
            <ThemeToggle size={16} className="!p-0 pointer-events-none" />
          </div>
          <div className="flex-1 text-left min-w-0">
            <p className="text-xs font-bold truncate">{theme === "light" ? "Dark Mode" : "Light Mode"}</p>
            <p className="text-[9px] opacity-40 uppercase font-bold tracking-wider truncate">Switch Theme</p>
          </div>
          <div className={`w-8 h-4 rounded-full relative transition-colors duration-300 ${theme === "dark" ? "bg-[#1D4ED8]" : "bg-base-content/20"}`}>
            <div className={`absolute top-0.5 w-3 h-3 rounded-full bg-white transition-transform duration-300 ${theme === "dark" ? "translate-x-4.5" : "translate-x-0.5"}`} />
          </div>
        </div>
      </div>

      {/* Help & Logout Section */}
      {loggedIn && (
        <div className="rounded-xl bg-base-200 p-1.5 space-y-0.5">
          <Link
            to="/help-support"
            className={`flex items-center gap-2.5 w-full rounded-lg px-2.5 py-1.5 text-[13px] font-bold transition duration-150 cursor-pointer group ${
              location.pathname === "/help-support"
                ? "bg-[#1D4ED8] text-white font-bold shadow-sm"
                : "text-base-content/80 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-base-300/80"
            }`}
          >
            <HelpCircle
              size={17}
              className={`transition-transform group-hover:scale-110 shrink-0 ${
                location.pathname === "/help-support"
                  ? "text-white opacity-100"
                  : "opacity-75 group-hover:opacity-100 group-hover:text-blue-600 dark:group-hover:text-blue-400"
              }`}
            />
            <span className="flex-1 text-left">Help & Support</span>
          </Link>

          <button
            type="button"
            onClick={() => setShowLogoutConfirm(true)}
            className="flex items-center gap-2.5 w-full rounded-lg px-2.5 py-1.5 text-[13px] font-bold text-base-content/80 hover:text-red-500 dark:hover:text-red-400 hover:bg-base-300/80 transition duration-150 cursor-pointer group"
          >
            <LogOut size={17} className="transition-transform group-hover:-translate-x-0.5 shrink-0 opacity-75 group-hover:opacity-100 group-hover:text-red-500 dark:group-hover:text-red-400" />
            <span className="flex-1 text-left">Logout</span>
          </button>
        </div>
      )}

      {/* Logout Confirmation Modal */}
      <ConfirmModal
        isOpen={showLogoutConfirm}
        onClose={() => setShowLogoutConfirm(false)}
        onConfirm={handleLogout}
        title="Log Out"
        message="Are you sure you want to log out of your account?"
        confirmLabel="Log Out"
        cancelLabel="Cancel"
        isDanger={true}
      />

    </aside>
  );
};

export default SidebarLeft;
