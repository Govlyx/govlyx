import { useEffect, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import AuthLayout from "../components/auth/AuthLayout";
import AuthHeader from "../components/auth/AuthHeader";
import { CheckCircle2, XCircle, RefreshCw, ArrowRight, Mail } from "lucide-react";
import { verifyEmail, resendVerification } from "../api/authService";
import { showToast } from "../utils/toast";
import { parseError } from "../utils/error-handler";

const VerifyEmail = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get("token");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Resend state
  const [resendEmail, setResendEmail] = useState("");
  const [resending, setResending] = useState(false);
  const [resendSuccess, setResendSuccess] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setError("No verification token found. Please check your verification link.");
      setLoading(false);
      return;
    }

    const performVerification = async () => {
      try {
        const data = await verifyEmail(token);
        if (data?.success) {
          setSuccess(data?.error || data?.message || "Email verified successfully!");
        } else {
          setError(data?.error || data?.message || "Verification failed");
        }
      } catch (err: any) {
        console.error("Email verification error:", err);
        setError(parseError(err));
      } finally {
        setLoading(false);
      }
    };

    performVerification();
  }, [token]);

  const handleResend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resendEmail.trim()) {
      showToast.error("Please enter your email");
      return;
    }

    setResending(true);
    setResendSuccess(null);
    try {
      const data = await resendVerification(resendEmail);
      setResendSuccess(data.error || data.message || "Verification link resent successfully.");
      showToast.success("Email sent!");
    } catch (err: any) {
      showToast.error(parseError(err));
    } finally {
      setResending(false);
    }
  };

  return (
    <AuthLayout>
      <AuthHeader
        title="Email Verification"
        subtitle="Confirming your digital identity for Govlyx compliance"
      />

      <div className="mt-8 flex flex-col items-center justify-center p-6 bg-base-200/50 rounded-3xl border border-base-300 backdrop-blur-md">
        {loading ? (
          <div className="flex flex-col items-center gap-4 py-8">
            <RefreshCw className="animate-spin text-[#1D4ED8]" size={36} />
            <p className="text-sm font-bold opacity-75">Verifying verification token with the server...</p>
          </div>
        ) : success ? (
          <div className="flex flex-col items-center text-center gap-4 py-4">
            <div className="w-16 h-16 rounded-full bg-green-500/10 flex items-center justify-center text-green-500">
              <CheckCircle2 size={36} />
            </div>
            <h3 className="text-lg font-black text-green-400">Account Activated</h3>
            <p className="text-sm opacity-80 px-2 leading-relaxed">
              {success}
            </p>
            <button
              onClick={() => navigate("/login")}
              className="btn bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 text-white border-none rounded-xl h-11 px-6 font-bold flex items-center gap-2 mt-4 shadow-lg shadow-[#1D4ED8]/20"
            >
              Go to Login <ArrowRight size={16} />
            </button>
          </div>
        ) : (
          <div className="w-full flex flex-col items-center text-center gap-4">
            <div className="w-16 h-16 rounded-full bg-red-500/10 flex items-center justify-center text-red-500">
              <XCircle size={36} />
            </div>
            <h3 className="text-lg font-black text-red-400">Verification Failed</h3>
            <p className="text-sm opacity-80 px-2 leading-relaxed">
              {error}
            </p>

            <div className="w-full border-t border-base-300 my-4 pt-4 text-left">
              <h4 className="text-xs font-black uppercase opacity-65 flex items-center gap-2 mb-2">
                <Mail size={12} /> Resend Verification Link
              </h4>
              <p className="text-[11px] opacity-70 mb-3 leading-normal">
                If the link expired, enter your email below to request a new verification token.
              </p>

              {resendSuccess ? (
                <div className="p-3 bg-green-500/5 border border-green-500/15 text-green-400 rounded-xl text-xs font-bold text-center">
                  {resendSuccess}
                </div>
              ) : (
                <form onSubmit={handleResend} className="flex gap-2">
                  <input
                    type="email"
                    placeholder="your-email@example.com"
                    required
                    className="input input-bordered flex-1 rounded-xl bg-base-200/50 border-none text-xs focus:ring-1 focus:ring-[#1D4ED8]"
                    value={resendEmail}
                    onChange={(e) => setResendEmail(e.target.value)}
                  />
                  <button
                    type="submit"
                    disabled={resending}
                    className="btn bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 text-white border-none rounded-xl text-xs px-4 h-9 min-h-[36px]"
                  >
                    {resending ? "Sending..." : "Resend"}
                  </button>
                </form>
              )}
            </div>

            <button
              onClick={() => navigate("/login")}
              className="text-xs font-black uppercase tracking-widest text-[#1D4ED8] hover:underline mt-2"
            >
              Back to Login
            </button>
          </div>
        )}
      </div>
    </AuthLayout>
  );
};

export default VerifyEmail;
