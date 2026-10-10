import { useState, useEffect } from "react";
import { createPortal } from "react-dom";
import { motion, AnimatePresence } from "framer-motion";
import { X, ShieldCheck, Clock } from "lucide-react";
import axiosInstance from "../../api/axiosConfig";
import { resolveMediaUrl } from "../../utils/postUtils";
import { useCurrentUser } from "../../hooks/useUser";

type Props = {
  isOpen: boolean;
  onClose: () => void;
  username: string;
  fallbackDisplayName?: string;
  fallbackProfileImage?: string | null;
};

function cleanEmail(val: string): string {
  if (!val) return "";
  if (val.includes("@")) {
    return val.split("@")[0];
  }
  return val;
}

// Date helper
function formatJoinDate(raw: any): string {
  const dateVal = raw || "2026-05-25";
  let d: Date;
  if (Array.isArray(dateVal)) {
    d = new Date(
      dateVal[0],
      dateVal[1] - 1,
      dateVal[2] ?? 1
    );
  } else {
    const ms = Number(dateVal);
    d = isNaN(ms) ? new Date(dateVal as string) : new Date(ms);
  }
  if (isNaN(d.getTime())) return "May 2026";
  return d.toLocaleDateString("en-US", { month: "short", year: "numeric" });
}

export default function UserProfileModal({
  isOpen,
  onClose,
  username,
  fallbackDisplayName,
  fallbackProfileImage,
}: Props) {
  const { data: currentUser } = useCurrentUser();
  const [profile, setProfile] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!isOpen || !username) return;

    const cleanQuery = cleanEmail(username);

    // If it's the current user, resolve immediately from active context
    const isSelf = currentUser && (
      cleanEmail(currentUser.actualUsername || currentUser.username || "").toLowerCase() === cleanQuery.toLowerCase() ||
      cleanEmail(currentUser.username || "").toLowerCase() === cleanQuery.toLowerCase()
    );

    if (isSelf) {
      setProfile({
        username: currentUser.username,
        actualUsername: currentUser.actualUsername || currentUser.username,
        profileImage: currentUser.profileImage || null,
        createdAt: currentUser.createdAt || null,
        tier: "GOVLYX_FREE",
        role: currentUser.role,
      });
      setLoading(false);
      return;
    }

    setLoading(true);
    setProfile(null);

    axiosInstance
      .get(`/api/users/search?query=${encodeURIComponent(cleanQuery)}&limit=10`)
      .then((res) => {
        const data =
          res.data?.data?.data ??
          res.data?.data?.content ??
          res.data?.data ??
          res.data?.content ??
          [];
        if (Array.isArray(data)) {
          const match = data.find((u: any) => {
            const candidate1 = cleanEmail(u.actualUsername || u.username || "").toLowerCase();
            const candidate2 = cleanEmail(u.username || "").toLowerCase();
            const candidate3 = cleanEmail(u.displayName || "").toLowerCase();
            const target = cleanQuery.toLowerCase();
            return (
              candidate1 === target ||
              candidate2 === target ||
              candidate3 === target
            );
          });
          if (match) {
            setProfile({
              ...match,
              actualUsername: match.actualUsername || match.displayName || match.username
            });
            return;
          }
        }
        // Fallback for mock users/failed exact matches
        setProfile({
          username: cleanQuery,
          actualUsername: fallbackDisplayName || cleanQuery,
          profileImage: fallbackProfileImage || null,
          createdAt: null,
          tier: "GOVLYX_FREE"
        });
      })
      .catch((err) => {
        console.error("Failed to load user profile in modal, using fallbacks:", err);
        setProfile({
          username: cleanQuery,
          actualUsername: fallbackDisplayName || cleanQuery,
          profileImage: fallbackProfileImage || null,
          createdAt: null,
          tier: "GOVLYX_FREE"
        });
      })
      .finally(() => {
        setLoading(false);
      });
  }, [isOpen, username, fallbackDisplayName, fallbackProfileImage, currentUser]);

  if (typeof document === "undefined") return null;

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center p-4">
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/60 backdrop-blur-sm cursor-pointer"
          />

          {/* Modal Container */}
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 15 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 15 }}
            transition={{ type: "spring", stiffness: 350, damping: 25 }}
            className="relative w-full max-w-sm overflow-hidden rounded-2xl sm:rounded-3xl border border-black/10 dark:border-white/15 bg-base-100 p-4 sm:p-6 shadow-2xl z-10"
          >
            {/* Close Button */}
            <button
              onClick={onClose}
              className="absolute right-3.5 top-3.5 sm:right-4 sm:top-4 btn btn-ghost btn-xs btn-square text-base-content/50 hover:text-base-content rounded-full cursor-pointer"
            >
              <X size={15} className="sm:size-4" />
            </button>

            {loading ? (
              <div className="flex h-40 sm:h-48 flex-col items-center justify-center gap-2.5 sm:gap-3">
                <span className="loading loading-spinner loading-md text-[#1D4ED8]" />
                <p className="text-[11px] sm:text-xs text-base-content/50 font-bold uppercase tracking-wider">Loading profile...</p>
              </div>
            ) : profile ? (
              <div className="flex flex-col items-center text-center gap-3 sm:gap-4 mt-1 sm:mt-2">
                {/* Avatar */}
                <div className="avatar">
                  <div className="h-16 w-16 sm:h-20 sm:w-20 rounded-full overflow-hidden border-2 border-blue-700 dark:border-blue-500 shadow-md bg-base-300">
                    <img
                      src={
                        resolveMediaUrl(
                          profile.profileImage || profile.profileImageUrl,
                          "social-posts"
                        ) ||
                        `https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(
                          profile.actualUsername || username
                        )}`
                      }
                      alt="User Avatar"
                      className="w-full h-full object-cover"
                    />
                  </div>
                </div>

                {/* Username & Verification */}
                <div className="space-y-0.5 sm:space-y-1">
                  <div className="flex items-center justify-center gap-1.5">
                    <h3 className="font-black text-base sm:text-lg text-base-content leading-tight notranslate">
                      {cleanEmail(profile.actualUsername || profile.username || username)}
                    </h3>
                    <ShieldCheck size={15} className="text-[#1D4ED8] shrink-0 sm:size-4" />
                  </div>
                  <p className="text-[9px] sm:text-[10px] text-base-content/40 font-black uppercase tracking-wider notranslate">
                    @{profile.actualUsername || username}
                  </p>
                </div>

                {/* Info Cards (Join Date & Tier/Pass) */}
                <div className="grid grid-cols-2 gap-2 sm:gap-3 w-full mt-1 sm:mt-2">
                  {/* Join Date Card */}
                  <div className="flex flex-col items-center justify-center p-2.5 sm:p-3 rounded-xl sm:rounded-2xl bg-base-200 border border-base-content/5 gap-0.5 sm:gap-1 shadow-sm">
                    <Clock size={15} className="text-base-content/50 sm:size-4" />
                    <p className="text-[8.5px] sm:text-[9px] text-base-content/40 font-bold uppercase tracking-wider">Joined</p>
                    <p className="text-[11px] sm:text-xs font-black text-base-content notranslate">
                      {formatJoinDate(profile.createdAt)}
                    </p>
                  </div>

                  {/* Pass Card */}
                  <div className={`flex flex-col items-center justify-center p-2.5 sm:p-3 rounded-xl sm:rounded-2xl border gap-0.5 sm:gap-1 transition-all duration-300 ${
                    profile.role === "ROLE_DEPARTMENT" || profile.username?.toLowerCase() === "pcmc" || username.toLowerCase() === "pcmc"
                      ? "bg-red-500/[0.03] border-red-500/25 shadow-[0_0_15px_rgba(239,68,68,0.25)] dark:bg-red-500/[0.06] dark:border-red-500/35"
                      : "bg-base-200 border border-base-content/5 shadow-sm"
                  }`}>
                    {(() => {
                      const isOfficial =
                        profile.role === "ROLE_DEPARTMENT" ||
                        profile.username?.toLowerCase() === "pcmc" ||
                        username.toLowerCase() === "pcmc";

                      if (isOfficial) {
                        return (
                          <>
                            <ShieldCheck size={15} className="text-red-500 drop-shadow-[0_0_4px_rgba(239,68,68,0.4)] sm:size-4" />
                            <p className="text-[8.5px] sm:text-[9px] text-red-500/80 font-bold uppercase tracking-wider">Pass</p>
                            <p className="text-[11px] sm:text-xs font-black text-red-500 uppercase tracking-wide [text-shadow:0_0_8px_rgba(239,68,68,0.6)]">Official</p>
                          </>
                        );
                      }

                      return (
                        <>
                          <ShieldCheck size={15} className="text-base-content/40 sm:size-4" />
                          <p className="text-[8.5px] sm:text-[9px] text-base-content/40 font-bold uppercase tracking-wider">Pass</p>
                          <p className="text-[11px] sm:text-xs font-black text-base-content/60 uppercase tracking-wide">Free</p>
                        </>
                      );
                    })()}
                  </div>
                </div>
              </div>
            ) : (
              <div className="flex h-40 sm:h-48 flex-col items-center justify-center gap-2 text-center mt-1 sm:mt-2">
                <ShieldCheck size={28} className="text-base-content/20 sm:size-8" />
                <h4 className="font-extrabold text-xs sm:text-sm text-base-content/75 mt-1">Profile Not Found</h4>
                <p className="text-[11px] sm:text-xs text-base-content/40 max-w-[200px] leading-relaxed">Could not locate profile details for @{username}.</p>
              </div>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  );
}
