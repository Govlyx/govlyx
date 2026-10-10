import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { motion, AnimatePresence } from "framer-motion";
import Navbar from "./Navbar";
import SidebarLeft from "./SidebarLeft";
import SidebarRight from "./SidebarRight";
import DepartmentRequestModal from "../modals/DepartmentRequestModal";
import { isAdminUser, isDepartmentUser } from "../../utils/auth";

const MainLayout = () => {
  const location = useLocation();
  const isQuickChat = location.pathname.includes("quick-chat");

  const [viewportHeight, setViewportHeight] = useState<number | null>(null);
  useEffect(() => {
    const vv = window.visualViewport;
    if (!vv) return;
    const onResize = () => {
      if (window.innerWidth < 1024) {
        setViewportHeight(vv.height);
      } else {
        setViewportHeight(null);
      }
    };
    vv.addEventListener("resize", onResize);
    return () => vv.removeEventListener("resize", onResize);
  }, []);

  const hideRightSidebar =
    location.pathname.startsWith("/admin/dashboard") ||
    isAdminUser() ||
    location.pathname.startsWith("/department/dashboard") ||
    isDepartmentUser();

  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);

  // Close mobile drawer on route change
  useEffect(() => {
    setMobileDrawerOpen(false);
    const checkbox = document.getElementById("mobile-drawer") as HTMLInputElement | null;
    if (checkbox) {
      checkbox.checked = false;
    }
  }, [location.pathname]);

  const handleDrawerToggle = (open?: boolean) => {
    setMobileDrawerOpen((prev) => {
      const next = typeof open === "boolean" ? open : !prev;
      const checkbox = document.getElementById("mobile-drawer") as HTMLInputElement | null;
      if (checkbox) {
        checkbox.checked = next;
      }
      return next;
    });
  };

  return (
    <div className="drawer lg:drawer-open">
      <DepartmentRequestModal />
      {/* Drawer toggle */}
      <input
        id="mobile-drawer"
        type="checkbox"
        className="drawer-toggle"
        checked={mobileDrawerOpen}
        onChange={(e) => setMobileDrawerOpen(e.target.checked)}
      />

      {/* MAIN CONTENT */}
      <div
        className="drawer-content overflow-hidden flex flex-col relative"
        style={{ height: viewportHeight ? `${viewportHeight}px` : "100dvh" }}
      >
        <Navbar
          isDrawerOpen={mobileDrawerOpen}
          onToggleDrawer={() => handleDrawerToggle()}
        />

        <motion.div
          className="flex flex-col bg-base-100 text-base-content overflow-hidden relative"
          style={{ flex: "1 1 0", minHeight: 0 }}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.2 }}
        >
          {/* Quick-chat on mobile: direct full-height flex column */}
          {isQuickChat ? (
            <div className="flex flex-col w-full overflow-hidden" style={{ flex: "1 1 0", minHeight: 0 }}>
              {/* Desktop layout with sidebars */}
              <div className="hidden lg:flex w-full mx-auto max-w-[1780px] px-4 min-w-[1024px] xl:min-w-[1280px] gap-4" style={{ flex: "1 1 0", minHeight: 0 }}>
                <aside className="lg:w-1/4 xl:w-1/4 h-full py-4" style={{ minHeight: 0 }}>
                  <div className="h-full overflow-y-auto scrollbar-hide">
                    <SidebarLeft />
                  </div>
                </aside>

                <main className="py-4 overflow-hidden flex flex-col" style={{ flex: "1 1 0", height: "100%", minHeight: 0 }}>
                  <Outlet />
                </main>

                {!hideRightSidebar && (
                  <aside className="hidden xl:block xl:w-1/4 h-full py-4" style={{ minHeight: 0 }}>
                    <div className="h-full overflow-y-auto scrollbar-hide">
                      <SidebarRight />
                    </div>
                  </aside>
                )}
              </div>

              {/* Mobile layout: direct full height & width container */}
              <main className="lg:hidden w-full overflow-hidden flex flex-col" style={{ flex: "1 1 0", minHeight: 0 }}>
                <Outlet />
              </main>
            </div>
          ) : (
            /* All other pages — normal layout with padding */
            <div className="flex-1 min-h-0 overflow-hidden relative flex flex-col h-full lg:overflow-x-auto">
              <div className="mx-auto max-w-[1780px] px-4 w-full flex-1 min-h-0 flex flex-col h-full lg:min-w-[1024px] xl:min-w-[1280px]">
                <div className="grid grid-cols-12 gap-4 flex-1 min-h-0 w-full h-full">

                  {/* LEFT SIDEBAR */}
                  <aside className="hidden lg:block lg:col-span-3 h-full py-2.5 min-h-0">
                    <div className="h-full overflow-y-auto scrollbar-hide">
                      <SidebarLeft />
                    </div>
                  </aside>

                  {/* CENTER */}
                  <main className={`col-span-12 lg:col-span-9 ${hideRightSidebar ? "xl:col-span-9" : "xl:col-span-6"} h-full ${location.pathname.startsWith("/admin/dashboard") ? "pb-0 overflow-hidden flex flex-col" : "pb-20 sm:pb-4 overflow-y-auto"} scrollbar-hide min-h-0`}>
                    <AnimatePresence mode="wait" initial={false}>
                      <motion.div
                        key={location.pathname}
                        className={location.pathname.startsWith("/admin/dashboard") ? "flex-1 min-h-0 h-full flex flex-col pt-0" : "pt-4"}
                        initial={{ opacity: 0, y: 6 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -6 }}
                        transition={{ duration: 0.15, ease: "easeOut" }}
                      >
                        <Outlet />
                      </motion.div>
                    </AnimatePresence>
                  </main>

                  {/* RIGHT SIDEBAR */}
                  {!hideRightSidebar && (
                    <aside className="hidden xl:block xl:col-span-3 h-full py-2.5 min-h-0">
                      <div className="h-full overflow-y-auto scrollbar-hide">
                        <SidebarRight />
                      </div>
                    </aside>
                  )}

                </div>
              </div>
            </div>
          )}
        </motion.div>
      </div>

      {/* MOBILE DRAWER */}
      <div className="drawer-side lg:hidden z-[999]">
        <label
          htmlFor="mobile-drawer"
          className="drawer-overlay !bg-black/40 backdrop-blur-sm cursor-pointer touch-manipulation"
          onClick={(e) => {
            e.preventDefault();
            handleDrawerToggle(false);
          }}
        />

        <div className="h-full w-72 bg-base-200 p-4 flex flex-col shadow-2xl transition-transform duration-300 ease-in-out pt-[max(1rem,env(safe-area-inset-top,0px))]">
          {/* Drawer Header */}
          <div className="mb-4 flex items-center justify-between">
            <span className="text-base font-bold">Menu</span>
            <label
              htmlFor="mobile-drawer"
              className="btn btn-ghost btn-sm btn-circle cursor-pointer touch-manipulation transition-transform active:scale-95 text-base-content/70 hover:text-base-content"
              onClick={(e) => {
                e.preventDefault();
                handleDrawerToggle(false);
              }}
            >
              ✕
            </label>
          </div>

          <div className="flex-1 overflow-y-auto hide-scrollbar scrollbar-hide pb-[env(safe-area-inset-bottom,0px)]">
            <SidebarLeft />
          </div>
        </div>
      </div>
    </div>
  );
};

export default MainLayout;
