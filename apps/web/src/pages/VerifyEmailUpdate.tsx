import { useEffect, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { CheckCircle2, XCircle, RefreshCw, ArrowLeft } from "lucide-react";
import axiosInstance from "../api/axiosConfig";
import AuthLayout from "../components/auth/AuthLayout";
import AuthHeader from "../components/auth/AuthHeader";
import { useQueryClient } from "@tanstack/react-query";

const REDIRECT_DELAY_MS = 3000;

const VerifyEmailUpdate = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const token = searchParams.get("token");

  const [loading, setLoading] = useState(true);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(REDIRECT_DELAY_MS / 1000);

  useEffect(() => {
    if (!token) {
      setError("No verification token found. Please check the link in your email.");
      setLoading(false);
      return;
    }

    const verify = async () => {
      try {
        const res = await axiosInstance.get(`/api/auth/verify-email-update?token=${encodeURIComponent(token)}`);
        const msg = res.data?.message || "Email updated successfully!";
        setSuccess(msg);
        // Invalidate cached user so the profile re-fetches with the new email
        await queryClient.invalidateQueries({ queryKey: ["currentUser"] });
      } catch (err: any) {
        const msg =
          err?.response?.data?.message ||
          err?.response?.data?.error ||
          "Verification failed. The link may have expired or already been used.";
        setError(msg);
      } finally {
        setLoading(false);
      }
    };

    verify();
  }, [token, queryClient]);

  // Countdown + auto-redirect after success
  useEffect(() => {
    if (!success) return;
    const interval = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          navigate("/profile", { replace: true });
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [success, navigate]);

  return (
    <AuthLayout>
      <AuthHeader
        title="Email Update Verification"
        subtitle="Confirming your new email address for Govlyx"
      />

      <div className="mt-8 flex flex-col items-center justify-center p-6 bg-base-200/50 rounded-3xl border border-base-300 backdrop-blur-md">
        {loading ? (
          <div className="flex flex-col items-center gap-4 py-8">
            <RefreshCw className="animate-spin text-[#1D4ED8]" size={36} />
            <p className="text-sm font-bold opacity-75">Verifying your new email address…</p>
          </div>
        ) : success ? (
          <div className="flex flex-col items-center text-center gap-4 py-4">
            <div className="w-16 h-16 rounded-full bg-green-500/10 flex items-center justify-center text-green-500">
              <CheckCircle2 size={36} />
            </div>
            <h3 className="text-lg font-black text-green-400">Email Updated!</h3>
            <p className="text-sm opacity-80 px-2 leading-relaxed">{success}</p>
            <p className="text-xs opacity-50">
              Redirecting you to your profile in{" "}
              <span className="font-bold tabular-nums">{countdown}</span>s…
            </p>
            <button
              onClick={() => navigate("/profile", { replace: true })}
              className="btn bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 text-white border-none rounded-xl h-11 px-6 font-bold flex items-center gap-2 mt-2 shadow-lg shadow-[#1D4ED8]/20"
            >
              Go to Profile
            </button>
          </div>
        ) : (
          <div className="w-full flex flex-col items-center text-center gap-4">
            <div className="w-16 h-16 rounded-full bg-red-500/10 flex items-center justify-center text-red-500">
              <XCircle size={36} />
            </div>
            <h3 className="text-lg font-black text-red-400">Verification Failed</h3>
            <p className="text-sm opacity-80 px-2 leading-relaxed">{error}</p>
            <button
              onClick={() => navigate("/profile", { replace: true })}
              className="text-xs font-black uppercase tracking-widest text-[#1D4ED8] hover:underline mt-2 flex items-center gap-1.5"
            >
              <ArrowLeft size={12} /> Back to Profile
            </button>
          </div>
        )}
      </div>
    </AuthLayout>
  );
};

export default VerifyEmailUpdate;
