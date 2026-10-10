/**
 * src/components/layout/Navbar.tsx
 *
 * Changes from original:
 *  - Desktop search input opens SearchOverlay on click or first keystroke
 *  - Mobile search icon opens SearchOverlay
 *  - SearchOverlay imported from ../search/SearchOverlay
 */

import { useState, useCallback } from "react";
import { NavLink } from "react-router-dom";
import { Search, MessageCircle, Plus } from "lucide-react";
import { motion } from "framer-motion";
import ThemeToggle from "../ui/ThemeToggle";

import CreatePost from "../ui/CreatePost";
import SearchOverlay from "../search/SearchOverlay";
import NotificationDropdown from "./NotificationDropdown";
import { useModal } from "../../context/ModalContext";
import { useCurrentUser } from "../../hooks/useUser";
import { useUnreadNotificationsCount } from "../../hooks/useNotification";
import { resolveMediaUrl } from "../../utils/postUtils";
import { isAdminUser, getAuthToken } from "../../utils/auth";
import GovlyxLogo from "../ui/GovlyxLogo";

interface NavbarProps {
  isDrawerOpen?: boolean;
  onToggleDrawer?: () => void;
}

const Navbar = ({ isDrawerOpen: _isDrawerOpen = false, onToggleDrawer }: NavbarProps) => {
  const loggedIn = !!getAuthToken();
  const [openCreate, setOpenCreate] = useState(false);
  const { openModal, closeModal } = useModal();
  
  const { data: user } = useCurrentUser({ enabled: loggedIn });
  const { data: unreadNotifications = 0, refetch: refetchUnreadCount } = useUnreadNotificationsCount({ enabled: loggedIn });
  
  const username = user?.actualUsername ?? user?.username ?? "User";

  // ── Search overlay state ───────────────────────────────────────────────────
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchSeed, setSearchSeed] = useState("");

  /** Open overlay, optionally pre-seeding the first typed character */
  const openSearch = useCallback((seed = "") => {
    setSearchSeed(seed);
    setSearchOpen(true);
  }, []);

  const closeSearch = useCallback(() => {
    setSearchOpen(false);
    setSearchSeed("");
  }, []);



  // (Effect for unread count is now handled by useUnreadNotificationsCount hook)

  return (
    <>
      <motion.header
        className="z-30 w-full border-b border-base-300 bg-base-200 backdrop-blur-md shadow-sm pt-[env(safe-area-inset-top,0px)] lg:min-w-[1024px] xl:min-w-[1280px]"
        initial={{ y: -16, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.35 }}
      >
        <div className="mx-auto flex h-14 max-w-[1780px] items-center gap-3 px-4">

          {/* MOBILE ANIMATED HAMBURGER MENU */}
          <button
            type="button"
            aria-label="Open menu"
            className="btn btn-ghost btn-sm btn-circle lg:hidden cursor-pointer touch-manipulation relative flex flex-col items-center justify-center gap-[4.5px] p-2 overflow-hidden active:scale-90 transition-transform"
            onClick={(e) => {
              e.preventDefault();
              if (onToggleDrawer) {
                onToggleDrawer();
              } else {
                const checkbox = document.getElementById("mobile-drawer") as HTMLInputElement | null;
                if (checkbox) checkbox.checked = !checkbox.checked;
              }
            }}
          >
            <motion.span
              className="w-5 h-[2px] bg-current rounded-full origin-center"
              animate={{ rotate: 0, y: 0 }}
              transition={{ duration: 0.2, ease: "easeInOut" }}
            />
            <motion.span
              className="w-5 h-[2px] bg-current rounded-full origin-center"
              animate={{ opacity: 1, scaleX: 1 }}
              transition={{ duration: 0.2, ease: "easeInOut" }}
            />
            <motion.span
              className="w-5 h-[2px] bg-current rounded-full origin-center"
              animate={{ rotate: 0, y: 0 }}
              transition={{ duration: 0.2, ease: "easeInOut" }}
            />
          </button>

          {/* LOGO */}
          <NavLink to={loggedIn ? "/dashboard" : "/"} className="flex items-center gap-2">
            <GovlyxLogo size={36} showText textClassName="hidden sm:block text-2xl font-bold" />
          </NavLink>

          {/* DESKTOP SEARCH — read-only trigger, opens overlay */}
          {!isAdminUser() && (
            <div className="hidden lg:flex flex-1 justify-center">
              <div className="relative w-full max-w-xl">
                <Search
                  size={18}
                  className="absolute left-3 top-1/2 -translate-y-1/2 opacity-50 pointer-events-none"
                />
                <input
                  type="text"
                  placeholder="Search communities, posts…"
                  className="input input-bordered w-full pl-10 cursor-pointer caret-transparent"
                  readOnly
                  // Open overlay immediately on click
                  onClick={() => openSearch()}
                  // If user just starts typing, seed that character into the overlay
                  onKeyDown={(e) => {
                    if (e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
                      openSearch(e.key);
                    }
                  }}
                />
              </div>
            </div>
          )}

          {/* ACTIONS */}
          <div className="ml-auto flex items-center gap-2">

            {/* MOBILE SEARCH ICON */}
            {!isAdminUser() && (
              <button
                className="btn btn-ghost btn-sm lg:hidden hover:bg-blue-700/10"
                onClick={() => openSearch()}
                aria-label="Open search"
              >
                <Search size={18} />
              </button>
            )}

            {/* CREATE - Desktop */}
            {!isAdminUser() && loggedIn && (
              <button
                type="button"
                onClick={() => { setOpenCreate(true); openModal(); }}
                className="bg-[#1D4ED8] hover:bg-[#1e40af] text-white font-bold text-xs sm:text-sm px-4 py-1.5 rounded-full transition-all cursor-pointer border-none shadow-md shadow-[#1D4ED8]/25 hover:scale-[1.02] active:scale-[0.98] hidden sm:inline-flex items-center gap-1.5"
              >
                <Plus size={16} className="stroke-[2.5]" />
                <span>Create</span>
              </button>
            )}

            {/* MOBILE CREATE ICON */}
            {!isAdminUser() && loggedIn && (
              <button
                type="button"
                onClick={() => { setOpenCreate(true); openModal(); }}
                className="bg-[#1D4ED8] hover:bg-[#1e40af] text-white w-9 h-9 rounded-full flex items-center justify-center cursor-pointer transition-all border-none shadow-md shadow-[#1D4ED8]/25 active:scale-95 sm:hidden"
                aria-label="Create post"
              >
                <Plus size={18} className="stroke-[2.5]" />
              </button>
            )}

            {/* CHAT - HIDE ON MOBILE */}
            {!isAdminUser() && loggedIn && (
              <NavLink to="/quick-chat" className="btn btn-ghost btn-sm hover:bg-blue-700/10 hidden sm:inline-flex">
                <MessageCircle size={18} />
              </NavLink>
            )}

            {loggedIn && (
              <NotificationDropdown 
                unreadCount={unreadNotifications} 
                onRefresh={refetchUnreadCount} 
              />
            )}

            {/* THEME TOGGLE */}
            <div className="hidden lg:flex items-center">
              <ThemeToggle size={21} className="btn btn-ghost btn-sm hover:bg-slate-200/60 dark:hover:bg-white/10 !min-h-0 !h-8 !w-8 p-0" />
            </div>

            <NavLink to={loggedIn ? "/profile" : "/login"} className="avatar placeholder">
              <div className="w-8 rounded-full overflow-hidden bg-base-200 border-2 border-[#1D4ED8] dark:border-white">
                <img src={resolveMediaUrl(user?.profileImage, "social-posts") || `https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(username)}`} alt="Avatar" className="w-full h-full object-cover" />
              </div>
            </NavLink>
          </div>

          {/* CREATE POST MODAL */}
        </div>
      </motion.header>

      {/* CREATE POST MODAL — rendered outside header so it is positioned relative to viewport */}
      <CreatePost open={openCreate} onClose={() => { setOpenCreate(false); closeModal(); }} />

      {/* Search Overlay — rendered outside the header so it can cover full screen */}
      <SearchOverlay
        open={searchOpen}
        onClose={closeSearch}
        initialQuery={searchSeed}
      />
    </>
  );
};

export default Navbar;