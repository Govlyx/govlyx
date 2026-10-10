import { useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import AuthLayout from "../components/auth/AuthLayout";
import AuthHeader from "../components/auth/AuthHeader";
import AuthInput from "../components/auth/AuthInput";
import { forgotPassword } from "../api/authService";
import { ArrowLeft, CheckCircle2, Loader2, Mail, AlertCircle, RefreshCw } from "lucide-react";
import ThemeToggle from "../components/ui/ThemeToggle";
import { parseError } from "../utils/error-handler";
import { showToast } from "../utils/toast";

const ForgotPassword = () => {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [resending, setResending] = useState(false);

  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setError(null);

    const trimmedEmail = email.trim().toLowerCase();
    if (!trimmedEmail) {
      setError("Please enter your email address");
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(trimmedEmail)) {
      setError("Please enter a valid email address");
      return;
    }

    setLoading(true);
    try {
      const response = await forgotPassword(trimmedEmail);
      if (response.success !== false) {
        setSuccess(true);
        showToast.success("Password reset link sent to your email!");
      } else {
        setError(response.error || response.message || "Failed to send reset link");
      }
    } catch (err: unknown) {
      setError(parseError(err));
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    if (!email.trim()) return;
    setResending(true);
    try {
      await forgotPassword(email.trim().toLowerCase());
      showToast.success("A new reset link has been dispatched!");
    } catch (err: unknown) {
      showToast.error(parseError(err));
    } finally {
      setResending(false);
    }
  };

  return (
    <AuthLayout>
      <Helmet>
        <title>Forgot Password | Govlyx India - Civic Platform</title>
        <meta
          name="description"
          content="Reset your Govlyx account password. Enter your registered email address to receive password recovery instructions."
        />
        <link rel="canonical" href="https://govlyx.com/forgot-password" />
        <meta property="og:title" content="Forgot Password | Govlyx India" />
        <meta
          property="og:description"
          content="Reset your Govlyx account password. Enter your registered email to receive secure recovery instructions."
        />
      </Helmet>

      <div className="flex items-center justify-end mb-3 sm:mb-4">
        <ThemeToggle size={16} className="p-1 rounded-lg bg-base-300/50 hover:bg-base-300" />
      </div>

      <AuthHeader
        title="Reset Password"
        subtitle="We will send you instructions to recover your account"
      />

      {/* Success State */}
      {success ? (
        <div className="space-y-4">
          <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 p-5 text-center space-y-3">
            <div className="w-12 h-12 rounded-full bg-emerald-500/20 text-emerald-500 flex items-center justify-center mx-auto shadow-inner">
              <CheckCircle2 size={26} className="stroke-[2.5]" />
            </div>
            <div>
              <h3 className="text-base font-bold text-base-content">
                Check Your Email
              </h3>
              <p className="mt-1.5 text-xs sm:text-[13px] text-base-content/70 leading-relaxed max-w-sm mx-auto">
                If an account exists for <span className="font-semibold text-base-content">{email}</span>, a secure password reset link has been sent. The link expires in 60 minutes.
              </p>
            </div>

            <div className="pt-2 border-t border-emerald-500/20 text-xs text-base-content/60 flex flex-col items-center gap-1.5">
              <span>Didn't receive the email? Check your spam folder or</span>
              <button
                type="button"
                onClick={handleResend}
                disabled={resending}
                className="inline-flex items-center gap-1.5 text-xs font-bold text-[#1D4ED8] dark:text-blue-400 hover:underline disabled:opacity-50 cursor-pointer"
              >
                {resending ? (
                  <>
                    <Loader2 size={12} className="animate-spin" />
                    <span>Resending link…</span>
                  </>
                ) : (
                  <>
                    <RefreshCw size={12} />
                    <span>Click here to resend</span>
                  </>
                )}
              </button>
            </div>
          </div>

          <div className="flex flex-col gap-2.5 pt-1">
            <button
              type="button"
              onClick={() => navigate("/login")}
              className="btn w-full bg-[#1D4ED8] text-white hover:bg-[#1D4ED8]/90 h-10.5 sm:h-11 text-xs sm:text-sm font-bold rounded-xl shadow-md shadow-[#1D4ED8]/20 border-none cursor-pointer"
            >
              Back to Login
            </button>
            <button
              type="button"
              onClick={() => {
                setSuccess(false);
                setError(null);
              }}
              className="w-full text-center text-xs font-semibold text-base-content/60 hover:text-base-content transition-colors py-1 cursor-pointer"
            >
              Try another email address
            </button>
          </div>
        </div>
      ) : (
        /* Form State */
        <form className="space-y-3.5" onSubmit={handleSubmit}>
          {error && (
            <div className="rounded-xl bg-red-500/10 border border-red-500/30 px-3.5 py-2.5 text-xs text-red-400 flex items-start gap-2.5">
              <AlertCircle size={15} className="shrink-0 mt-0.5 text-red-500" />
              <span className="leading-relaxed">{error}</span>
            </div>
          )}

          <div className="space-y-1">
            <AuthInput
              label="Registered Email Address"
              type="email"
              placeholder="you@example.com"
              name="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
            <p className="text-[11px] text-base-content/50 px-1">
              Enter the email associated with your Govlyx account.
            </p>
          </div>

          <button
            type="submit"
            className="btn w-full bg-[#1D4ED8] text-white hover:bg-[#1D4ED8]/90 disabled:opacity-50 disabled:cursor-not-allowed h-10.5 sm:h-11 text-xs sm:text-sm font-bold rounded-xl shadow-md shadow-[#1D4ED8]/20 border-none cursor-pointer mt-1"
            disabled={loading}
          >
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <Loader2 size={16} className="animate-spin" />
                <span>Sending link…</span>
              </span>
            ) : (
              <span className="flex items-center justify-center gap-2">
                <Mail size={15} />
                <span>Send Reset Link</span>
              </span>
            )}
          </button>

          {/* Footer Back Link */}
          <div className="text-center pt-2">
            <p className="text-xs sm:text-sm opacity-75">
              Remember your password?{" "}
              <NavLink
                to="/login"
                className="text-[#1D4ED8] dark:text-blue-400 font-bold hover:underline"
              >
                Log in here
              </NavLink>
            </p>
          </div>

          {/* Back to Landing Page */}
          <button
            type="button"
            onClick={() => navigate("/")}
            className="w-full mt-2 flex items-center justify-center gap-2 h-10.5 sm:h-11 text-xs sm:text-sm font-semibold rounded-xl border border-base-300 bg-base-200/50 hover:bg-base-200 text-base-content/80 hover:text-base-content transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-3.5 h-3.5" /> Back to Landing Page
          </button>
        </form>
      )}
    </AuthLayout>
  );
};

export default ForgotPassword;
