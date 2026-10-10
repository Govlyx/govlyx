import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import StrangerChat from "../components/layout/StrangerChat";
import ScreenshotProtectionOverlay from "../components/security/ScreenshotProtectionOverlay";

const NAVBAR_H = 56; // matches Navbar h-14
const LG_BREAKPOINT = 1024; // Tailwind lg

const QuickChatPage = () => {
  const navigate = useNavigate();

  // Mobile → position:fixed (bypasses DaisyUI grid height chain entirely).
  // Desktop → normal flex child (sits between left/right sidebars).
  const [isMobile, setIsMobile] = useState(
    () => typeof window !== "undefined" && window.innerWidth < LG_BREAKPOINT
  );

  useEffect(() => {
    const mq = window.matchMedia(`(min-width: ${LG_BREAKPOINT}px)`);
    const handler = (e: MediaQueryListEvent) => setIsMobile(!e.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);

  return (
    <>
      <Helmet>
        <title>Chat Anonymously with Neighbors Near You | Govlyx Quick Chat</title>
        <meta name="description" content="Connect in real-time, text-based anonymous chat with people in your neighborhood without sharing your phone number." />
        <meta name="keywords" content="chat with strangers near me, anonymous chat app no phone number, find people nearby, hyperlocal chat" />
      </Helmet>

      <div
        style={
          isMobile
            ? {
                position: "fixed",
                top: NAVBAR_H,
                left: 0,
                right: 0,
                bottom: 0,
                display: "flex",
                flexDirection: "column",
                zIndex: 20,
                overflow: "hidden",
              }
            : {
                height: "100%",
                minHeight: 0,
                display: "flex",
                flexDirection: "column",
                overflow: "hidden",
                width: "100%",
                borderRadius: "1rem",
                border: "1px solid var(--color-base-300)",
              }
        }
      >
        <StrangerChat standalone onClose={() => navigate("/dashboard")} />
      </div>

      <ScreenshotProtectionOverlay />
    </>
  );
};

export default QuickChatPage;
