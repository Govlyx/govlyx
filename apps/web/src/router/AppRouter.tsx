import { Routes, Route, Navigate, useLocation, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { useEffect, useState } from "react";
import { jwtDecode } from "jwt-decode";
import { clearAuthTokens, getAuthToken, isDepartmentUser } from "../utils/auth";
import { ModalProvider } from "../context/ModalContext";
import { LanguageProvider } from "../context/LanguageContext";
import { Toaster } from "react-hot-toast";

import MainLayout from "../components/layout/MainLayout";

// Pages
import Home from "../pages/Home";
import Communities from "../pages/Communities";
import DepartmentFeed from "../pages/DepartmentFeed";
import DepartmentDashboard from "../pages/DepartmentDashboard";
import AdminDashboard from "../pages/AdminDashboard";
import QuickChatPage from "../pages/QuickChatPage";
import { isAdminUser } from "../utils/auth";
import Profile from "../pages/Profile";
import Settings from "../pages/Settings";
import NotificationsPage from "../pages/NotificationsPage";
import PostDetail from "../pages/PostDetail";
import Login from "../pages/Login";
import Register from "../pages/Register";
import ForgotPassword from "../pages/ForgotPassword";
import ResetPassword from "../pages/ResetPassword";
import LandingPage from "../pages/LandingPage";
import { AcceptInvitePage } from "../pages/Communities";
import VerifyEmail from "../pages/VerifyEmail";
import VerifyEmailUpdate from "../pages/VerifyEmailUpdate";
import UpcomingUpdates from "../pages/UpcomingUpdates";
import PrivacyPolicy from "../pages/PrivacyPolicy";
import ReviewPage from "../pages/ReviewPage";
import HowToUse from "../pages/HowToUse";
import SearchResultsPage from "../pages/SearchResultsPage";
import CopyrightClaimForm from "../pages/CopyrightClaimForm";
import CopyrightClaimStatus from "../pages/CopyrightClaimStatus";
import HelpSupportPage from "../pages/HelpSupportPage";
import NotFoundPage from "../pages/NotFoundPage";

import PincodePage from "../pages/PincodePage";
import IssuePage from "../pages/IssuePage";
import QaPage from "../pages/QaPage";
import CityPage from "../pages/CityPage";
import DeptPage from "../pages/DeptPage";
import CategoryPage from "../pages/CategoryPage";


// ── Page transition wrapper ───────────────────────────────────────────────────
const PageWrapper = ({ children, className = "" }: { children: React.ReactNode; className?: string }) => (
  <motion.div
    initial={{ opacity: 0, y: 8 }}
    animate={{ opacity: 1, y: 0 }}
    exit={{ opacity: 0, y: -8 }}
    transition={{ duration: 0.18, ease: "easeOut" }}
    className={className}
  >
    {children}
  </motion.div>
);

// ── Token validation ──────────────────────────────────────────────────────────
const isLoggedIn = (): boolean => {
  const token = getAuthToken();
  if (!token) return false;

  try {
    const decoded = jwtDecode<{ exp: number }>(token);
    const isExpired = decoded.exp * 1000 < Date.now();
    if (isExpired) {
      clearAuthTokens();
      return false;
    }
    return true;
  } catch {
    clearAuthTokens();
    return false;
  }
};

// ── Token expiry watcher ──────────────────────────────────────────────────────
const useTokenExpiryWatcher = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const token = getAuthToken();

  useEffect(() => {
    // Disable expiry checking if already on public routes
    if (
      location.pathname === "/" ||
      location.pathname === "/how-to-use" ||
      location.pathname === "/about" ||
      location.pathname === "/more-info" ||
      location.pathname === "/upcoming-updates" ||
      location.pathname === "/privacy-policy" ||
      location.pathname === "/review" ||
      location.pathname === "/docs" ||
      location.pathname === "/login" ||
      location.pathname === "/register" ||
      location.pathname === "/forgot-password" ||
      location.pathname === "/reset-password" ||
      location.pathname === "/verify-email" ||
      location.pathname.startsWith("/invite/") ||
      location.pathname === "/copyright-claim" ||
      location.pathname === "/copyright-claim/status" ||
      location.pathname.startsWith("/communities") ||
      location.pathname.startsWith("/post/") ||
      location.pathname.startsWith("/pincode/") ||
      location.pathname.startsWith("/issue/") ||
      location.pathname.startsWith("/q/") ||
      location.pathname.startsWith("/city/") ||
      location.pathname.startsWith("/dept/") ||
      location.pathname.startsWith("/category/")
    ) {
      return;
    }

    const check = () => {
      if (!isLoggedIn()) {
        navigate("/login?error=expired", { replace: true });
      }
    };

    // Check every 60 seconds
    const interval = setInterval(check, 60 * 1000);

    // Also schedule a precise redirect exactly when the token expires
    if (token) {
      try {
        const decoded = jwtDecode<{ exp: number }>(token);
        const msUntilExpiry = decoded.exp * 1000 - Date.now();
        if (msUntilExpiry > 0) {
          const timeout = setTimeout(() => {
            clearAuthTokens();
            navigate("/login?error=expired", { replace: true });
          }, msUntilExpiry);

          return () => {
            clearInterval(interval);
            clearTimeout(timeout);
          };
        }
      } catch {
        clearAuthTokens();
        navigate("/login?error=expired", { replace: true });
      }
    }

    return () => clearInterval(interval);
  }, [navigate, location.pathname, token]);
};

// ── Dashboard Redirect Helper ──────────────────────────────────────────────────
const DashboardRedirect = () => {
  if (isAdminUser()) {
    return <Navigate to="/admin/dashboard" replace />;
  }
  if (isDepartmentUser()) {
    return <Navigate to="/department/dashboard" replace />;
  }
  return <Home />;
};

// ── Router ────────────────────────────────────────────────────────────────────
const AppRouter = () => {
  useTokenExpiryWatcher();
  const loggedIn = isLoggedIn();

  const [isLargeScreen, setIsLargeScreen] = useState(() => {
    return typeof window !== "undefined" ? window.innerWidth >= 1024 : true;
  });

  useEffect(() => {
    if (typeof window === "undefined") return;
    const handleResize = () => {
      setIsLargeScreen(window.innerWidth >= 1024);
    };
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  return (
    <LanguageProvider>
    <ModalProvider>
        <Toaster position={isLargeScreen ? "bottom-right" : "top-center"} reverseOrder={false} />
        <Routes>

        {/* ── Public landing page route ── */}
        <Route
          path="/"
          element={<PageWrapper><LandingPage /></PageWrapper>}
        />
        <Route
          path="/how-to-use"
          element={<PageWrapper><HowToUse /></PageWrapper>}
        />
        <Route
          path="/about"
          element={<PageWrapper><HowToUse /></PageWrapper>}
        />
        <Route
          path="/more-info"
          element={<PageWrapper><HowToUse /></PageWrapper>}
        />
        <Route
          path="/upcoming-updates"
          element={<PageWrapper><UpcomingUpdates /></PageWrapper>}
        />
        <Route
          path="/privacy-policy"
          element={<PageWrapper><PrivacyPolicy /></PageWrapper>}
        />
        <Route
          path="/review"
          element={<PageWrapper><ReviewPage /></PageWrapper>}
        />
        <Route
          path="/docs"
          element={<Navigate to="/upcoming-updates" replace />}
        />
        <Route
          path="/copyright-claim"
          element={<PageWrapper><CopyrightClaimForm /></PageWrapper>}
        />
        <Route
          path="/copyright-claim/status"
          element={<PageWrapper><CopyrightClaimStatus /></PageWrapper>}
        />


        {/* ── Public auth routes ── */}
        <Route
          path="/login"
          element={
            loggedIn
              ? <Navigate to="/dashboard" replace />
              : <PageWrapper className="w-full h-full"><Login /></PageWrapper>
          }
        />
        <Route
          path="/register"
          element={
            loggedIn
              ? <Navigate to="/dashboard" replace />
              : <PageWrapper className="w-full h-full"><Register /></PageWrapper>
          }
        />
        <Route
          path="/forgot-password"
          element={
            loggedIn
              ? <Navigate to="/dashboard" replace />
              : <PageWrapper className="w-full h-full"><ForgotPassword /></PageWrapper>
          }
        />
        <Route
          path="/reset-password"
          element={
            loggedIn
              ? <Navigate to="/dashboard" replace />
              : <PageWrapper className="w-full h-full"><ResetPassword /></PageWrapper>
          }
        />
        <Route
          path="/verify-email"
          element={<PageWrapper className="w-full h-full"><VerifyEmail /></PageWrapper>}
        />
        <Route
          path="/verify-email-update"
          element={<PageWrapper className="w-full h-full"><VerifyEmailUpdate /></PageWrapper>}
        />

        {/* ── Invite accept route ── */}
        <Route
          path="/invite/:token"
          element={
            <PageWrapper>
              <AcceptInvitePage />
            </PageWrapper>
          }
        />

        {/* ── Public MainLayout routes ── */}
        <Route element={<MainLayout />}>
          <Route path="/communities/:id?" element={<PageWrapper><Communities /></PageWrapper>} />
          <Route path="/post/:id" element={<PageWrapper><PostDetail /></PageWrapper>} />
          <Route path="/pincode/:pincode" element={<PageWrapper><PincodePage /></PageWrapper>} />
          <Route path="/issue/:id/:slug" element={<PageWrapper><IssuePage /></PageWrapper>} />
          <Route path="/q/:id/:slug" element={<PageWrapper><QaPage /></PageWrapper>} />
          <Route path="/city/:citySlug" element={<PageWrapper><CityPage /></PageWrapper>} />
          <Route path="/dept/:deptSlug" element={<PageWrapper><DeptPage /></PageWrapper>} />
          <Route path="/category/:categorySlug" element={<PageWrapper><CategoryPage /></PageWrapper>} />
        </Route>

        {/* ── Protected routes ── */}
        <Route
          element={loggedIn ? <MainLayout /> : <Navigate to="/login" replace />}
        >
          <Route path="/dashboard" element={<PageWrapper><DashboardRedirect /></PageWrapper>} />
          <Route path="/department-feed" element={<PageWrapper><DepartmentFeed /></PageWrapper>} />
          <Route path="/department/dashboard"
            element={
              !isDepartmentUser()
                ? <Navigate to="/dashboard" replace />
                : <PageWrapper><DepartmentDashboard /></PageWrapper>
            }
          />
          <Route path="/quick-chat" element={<PageWrapper className="h-full flex flex-col flex-1 min-h-0"><QuickChatPage /></PageWrapper>} />
          <Route path="/profile" element={<PageWrapper><Profile /></PageWrapper>} />
          <Route path="/notifications" element={<PageWrapper><NotificationsPage /></PageWrapper>} />
          <Route path="/search" element={<PageWrapper><SearchResultsPage /></PageWrapper>} />
          <Route path="/settings" element={<PageWrapper><Settings /></PageWrapper>} />
          <Route path="/help-support" element={<PageWrapper><HelpSupportPage /></PageWrapper>} />
          <Route path="/admin/dashboard" 
            element={
              !isAdminUser() 
                ? <Navigate to="/dashboard" replace /> 
                : <PageWrapper><AdminDashboard /></PageWrapper>
            } 
          />

        </Route>

        {/* ── Fallback ── */}
        <Route
          path="*"
          element={<NotFoundPage />}
        />

      </Routes>
    </ModalProvider>
    </LanguageProvider>
  );
};

export default AppRouter;
