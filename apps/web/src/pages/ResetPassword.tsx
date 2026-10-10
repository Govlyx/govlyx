import { useState } from "react";
import { NavLink, useNavigate, useSearchParams } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import AuthLayout from "../components/auth/AuthLayout";
import AuthHeader from "../components/auth/AuthHeader";
import { resetPassword } from "../api/authService";
import { ArrowLeft, CheckCircle2, Loader2, Eye, EyeOff, AlertCircle, KeyRound } from "lucide-react";
import ThemeToggle from "../components/ui/ThemeToggle";
import { parseError } from "../utils/error-handler";
import { showToast } from "../utils/toast";

const ResetPassword = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get("token") || "";

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setError(null);

    if (!token) {
      setError("Password reset token is missing or invalid. Please request a new link.");
      return;
    }

    if (!newPassword) {
      setError("Please enter a new password");
      return;
    }

    if (newPassword.length < 6) {
      setError("Password must be at least 6 characters long");
      return;
    }

    if (newPassword !== confirmPassword) {
      setError("Passwords do not match");
      return;
    }

    setLoading(true);
    try {
      const response = await resetPassword({
        token,
        newPassword,
      });

      if (response.success !== false) {
        setSuccess(true);
        showToast.success("Password reset successfully! Please log in.");
      } else {
        setError(response.error || response.message || "Failed to reset password");
      }
    } catch (err: unknown) {
      setError(parseError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout>
      <Helmet>
        <title>Set New Password | Govlyx India - Civic Platform</title>
        <meta
          name="description"
          content="Choose a new secure password for your Govlyx account."
        />
        <link rel="canonical" href="https://govlyx.com/reset-password" />
        <meta property="og:title" content="Set New Password | Govlyx India" />
        <meta
          property="og:description"
          content="Choose a new secure password for your Govlyx account."
        />
      </Helmet>

      <div className="flex items-center justify-end mb-3 sm:mb-4">
        <ThemeToggle size={16} className="p-1 rounded-lg bg-base-300/50 hover:bg-base-300" />
      </div>

      <AuthHeader
        title="Set New Password"
        subtitle="Create a strong, new password for your account"
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
                Password Reset Successfully!
              </h3>
              <p className="mt-1.5 text-xs sm:text-[13px] text-base-content/70 leading-relaxed max-w-sm mx-auto">
                Your password has been securely updated. You can now log in using your new credentials.
              </p>
            </div>
          </div>

          <div className="pt-1">
            <button
              type="button"
              onClick={() => navigate("/login")}
              className="btn w-full bg-[#1D4ED8] text-white hover:bg-[#1D4ED8]/90 h-10.5 sm:h-11 text-xs sm:text-sm font-bold rounded-xl shadow-md shadow-[#1D4ED8]/20 border-none cursor-pointer"
            >
              Continue to Login
            </button>
          </div>
        </div>
      ) : !token ? (
        /* Missing Token State */
        <div className="space-y-4">
          <div className="rounded-2xl border border-red-500/20 bg-red-500/10 p-5 text-center space-y-3">
            <div className="w-12 h-12 rounded-full bg-red-500/20 text-red-500 flex items-center justify-center mx-auto">
              <AlertCircle size={26} />
            </div>
            <div>
              <h3 className="text-base font-bold text-base-content">
                Invalid or Missing Reset Link
              </h3>
              <p className="mt-1.5 text-xs text-base-content/70 leading-relaxed max-w-sm mx-auto">
                No reset token was found in your URL. Please ensure you clicked the full link from your email, or request a fresh link below.
              </p>
            </div>
          </div>

          <div className="flex flex-col gap-2 pt-1">
            <button
              type="button"
              onClick={() => navigate("/forgot-password")}
              className="btn w-full bg-[#1D4ED8] text-white hover:bg-[#1D4ED8]/90 h-10.5 sm:h-11 text-xs sm:text-sm font-bold rounded-xl shadow-md shadow-[#1D4ED8]/20 border-none cursor-pointer"
            >
              Request New Reset Link
            </button>
            <button
              type="button"
              onClick={() => navigate("/login")}
              className="w-full text-center text-xs font-semibold text-base-content/60 hover:text-base-content py-1 cursor-pointer"
            >
              Back to Login
            </button>
          </div>
        </div>
      ) : (
        /* Form State */
        <form className="space-y-3.5" onSubmit={handleSubmit}>
          {error && (
            <div className="rounded-xl bg-red-500/10 border border-red-500/30 px-3.5 py-2.5 text-xs text-red-400 flex flex-col gap-1.5">
              <div className="flex items-start gap-2">
                <AlertCircle size={15} className="shrink-0 mt-0.5 text-red-500" />
                <span className="leading-relaxed">{error}</span>
              </div>
              {error.toLowerCase().includes("expired") && (
                <div className="pt-1.5 mt-0.5 border-t border-red-500/20">
                  <NavLink
                    to="/forgot-password"
                    className="text-xs font-bold text-[#1D4ED8] dark:text-blue-400 hover:underline"
                  >
                    Click here to request a new reset link →
                  </NavLink>
                </div>
              )}
            </div>
          )}

          {/* New Password */}
          <div className="space-y-0.5">
            <label className="text-xs sm:text-sm font-semibold opacity-85 block">
              New Password
            </label>
            <div className="relative">
              <input
                type={showPassword ? "text" : "password"}
                placeholder="At least 6 characters"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                className="input input-bordered w-full h-10.5 sm:h-11 text-xs sm:text-sm px-3.5 rounded-xl focus:border-blue-700 focus:outline-none pr-10"
              />
              <button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-base-content/40 hover:text-base-content/70 transition-colors cursor-pointer"
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
              </button>
            </div>
          </div>

          {/* Confirm Password */}
          <div className="space-y-0.5">
            <label className="text-xs sm:text-sm font-semibold opacity-85 block">
              Confirm New Password
            </label>
            <div className="relative">
              <input
                type={showConfirmPassword ? "text" : "password"}
                placeholder="Re-enter your new password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="input input-bordered w-full h-10.5 sm:h-11 text-xs sm:text-sm px-3.5 rounded-xl focus:border-blue-700 focus:outline-none pr-10"
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword((prev) => !prev)}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-base-content/40 hover:text-base-content/70 transition-colors cursor-pointer"
                aria-label={showConfirmPassword ? "Hide password" : "Show password"}
              >
                {showConfirmPassword ? <EyeOff size={17} /> : <Eye size={17} />}
              </button>
            </div>
          </div>

          <button
            type="submit"
            className="btn w-full bg-[#1D4ED8] text-white hover:bg-[#1D4ED8]/90 disabled:opacity-50 disabled:cursor-not-allowed h-10.5 sm:h-11 text-xs sm:text-sm font-bold rounded-xl shadow-md shadow-[#1D4ED8]/20 border-none cursor-pointer mt-1"
            disabled={loading}
          >
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <Loader2 size={16} className="animate-spin" />
                <span>Updating password…</span>
              </span>
            ) : (
              <span className="flex items-center justify-center gap-2">
                <KeyRound size={15} />
                <span>Save New Password</span>
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

export default ResetPassword;
