import { useState } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import AuthLayout from '../components/auth/AuthLayout';
import AuthHeader from '../components/auth/AuthHeader';
import AuthInput from '../components/auth/AuthInput';
import { loginUser, resendVerification } from '../api/authService';
import { Info, Eye, EyeOff, ArrowLeft } from 'lucide-react';
import ThemeToggle from '../components/ui/ThemeToggle';
import { queryClient } from '../api/queryClient';
import { persistAuthToken, decodeAuthToken } from '../utils/auth';
import { showToast } from '../utils/toast';
import { parseError } from '../utils/error-handler';
import GoogleAuthButton from '../components/auth/GoogleAuthButton';
import { vaultService } from '../services/vaultService';
import PrivacyPinModal from '../components/auth/PrivacyPinModal';

const getAuthResponseMessage = (response: {
  message?: string;
  error?: string;
}) => response.error || response.message;

const Login = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const isExpired = queryParams.get('error') === 'expired';
  const [showExpiredMsg, setShowExpiredMsg] = useState(isExpired);
  const [showPassword, setShowPassword] = useState(false);

  const [form, setForm] = useState({
    email: '',
    password: '',
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Resend Verification states
  const [resending, setResending] = useState(false);
  const [resendSuccess, setResendSuccess] = useState<string | null>(null);

  const handleResendVerification = async () => {
    if (!form.email) return;
    setResending(true);
    setResendSuccess(null);
    try {
      const response = await resendVerification(form.email);
      setResendSuccess(
        getAuthResponseMessage(response) ||
          'Verification link resent successfully.',
      );
    } catch (err: any) {
      showToast.error(parseError(err));
    } finally {
      setResending(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  // Privacy Vault state
  const [unlockVaultState, setUnlockVaultState] = useState<{
    isOpen: boolean;
    mode: 'SETUP' | 'UNLOCK';
    vaultBlob?: string | null;
    vaultSalt?: string | null;
    seedBlindSalt?: string | null;
    serverActorToken: string;
  }>({
    isOpen: false,
    mode: 'UNLOCK',
    serverActorToken: '',
  });

  const handleLogin = async () => {
    setError(null);
    setShowExpiredMsg(false); // Hide the session expired message on new login attempt

    // Frontend validation
    if (!form.email || !form.password) {
      setError('Email and password are required');
      return;
    }

    setLoading(true);
    try {
      const response = await loginUser({
        email: form.email,
        password: form.password,
      });

      const token =
        response.data?.token ||
        response.data?.authToken ||
        response.data?.accessToken ||
        response.data?.jwt;

      if (response.success && token) {
        // Save JWT token under the keys used across the app
        persistAuthToken(token);
        queryClient.clear();

        const decoded = decodeAuthToken(token);
        const role = decoded?.role || response.data?.user?.role?.name;
        const isAuthority = vaultService.isAuthorityRole(role);

        if (isAuthority) {
          // Flow 3: Authority / Department / Admin accounts bypass client privacy vault completely
          navigate('/dashboard');
          return;
        }

        // Flow 1: Shielded Citizen (ROLE_USER)
        const serverActorToken =
          response.data?.serverActorToken ||
          (decoded as any)?.serverActorToken ||
          (decoded as any)?.actorToken ||
          '';

        // Check if this browser already has local blindSalt in IndexedDB
        const hasSalt = await vaultService.hasLocalBlindSalt();
        if (hasSalt && serverActorToken) {
          // Fast silent unlock on familiar device
          const storedSalt = await vaultService.getStoredBlindSalt();
          if (storedSalt) {
            await vaultService.deriveActorToken(serverActorToken, storedSalt);
            navigate('/dashboard');
            return;
          }
        }

        // Check if account has an existing vault blob
        const hasVault = response.data?.hasVault ?? !!response.data?.vaultBlob;
        const vaultBlob = response.data?.vaultBlob;
        const vaultSalt = response.data?.vaultSalt;
        const seedBlindSalt = response.data?.seedBlindSalt;

        if (!hasVault) {
          // First-time login / migration upgrade: prompt SETUP
          setUnlockVaultState({
            isOpen: true,
            mode: 'SETUP',
            vaultBlob: null,
            vaultSalt: null,
            seedBlindSalt: seedBlindSalt || null,
            serverActorToken,
          });
        } else {
          // Returning citizen on new browser/device: prompt UNLOCK
          setUnlockVaultState({
            isOpen: true,
            mode: 'UNLOCK',
            vaultBlob: vaultBlob || null,
            vaultSalt: vaultSalt || null,
            serverActorToken,
          });
        }
      } else {
        setError(getAuthResponseMessage(response) || 'Login failed');
      }
    } catch (err: any) {
      setError(parseError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout>
      <Helmet>
        <title>Login | Govlyx India - Neighborhood & Civic Platform</title>
        <meta
          name="description"
          content="Log in to Govlyx to view local updates, report municipal grievances, and connect with your local neighborhood and municipal authorities."
        />
        <link rel="canonical" href="https://govlyx.com/login" />
        <meta property="og:type" content="website" />
        <meta property="og:url" content="https://govlyx.com/login" />
        <meta property="og:site_name" content="Govlyx" />
        <meta property="og:title" content="Login | Govlyx India" />
        <meta
          property="og:description"
          content="Log in to Govlyx to view local updates, report municipal grievances, and connect with your local neighborhood."
        />
        <meta property="og:image" content="https://govlyx.com/govlyx-og.png" />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content="Login | Govlyx India" />
        <meta
          name="twitter:description"
          content="Log in to Govlyx to view local updates, report municipal grievances, and connect with your local neighborhood."
        />
        <meta name="twitter:image" content="https://govlyx.com/govlyx-og.png" />
      </Helmet>
      <div className="flex items-center justify-end mb-3 sm:mb-4">
        <ThemeToggle
          size={16}
          className="p-1 rounded-lg bg-base-300/50 hover:bg-base-300"
        />
      </div>
      <AuthHeader title="Welcome back" subtitle="Access your Govlyx portal" />

      {/* Session Expired Message */}
      {showExpiredMsg && !error && (
        <div className="mb-3 rounded-xl bg-amber-500/10 border border-amber-500/30 px-3 py-2 text-xs text-amber-500 flex items-center gap-2.5">
          <Info size={14} className="shrink-0" />
          <span>Session expired. Please log in again to continue.</span>
        </div>
      )}

      {/* Error Message */}
      {error && (
        <div className="mb-3 rounded-xl bg-red-500/10 border border-red-500/30 px-3 py-2 text-xs text-red-400 flex flex-col gap-1.5">
          <span>{error}</span>
          {error.toLowerCase().includes('verify') && (
            <div className="mt-1 pt-1.5 border-t border-red-500/20 flex flex-col gap-1.5">
              {resendSuccess ? (
                <span className="text-xs font-bold text-green-400">
                  {resendSuccess}
                </span>
              ) : (
                <button
                  onClick={handleResendVerification}
                  disabled={resending}
                  className="text-[11px] font-black uppercase text-left tracking-wider text-[#1D4ED8] dark:text-blue-400 hover:underline disabled:opacity-50 transition-colors cursor-pointer"
                >
                  {resending
                    ? 'Sending Link...'
                    : "Didn't receive email? Resend verification link"}
                </button>
              )}
            </div>
          )}
        </div>
      )}

      {/* Form */}
      <form
        className="space-y-3 sm:space-y-3.5"
        onSubmit={(e) => {
          e.preventDefault();
          handleLogin();
        }}
      >
        <AuthInput
          label="Email Address"
          type="email"
          placeholder="you@example.com"
          name="email"
          value={form.email}
          onChange={handleChange}
        />

        <div className="space-y-0.5">
          <div className="flex justify-between items-center">
            <label className="text-xs sm:text-sm font-semibold opacity-85 block">
              Password
            </label>
            <NavLink to="/forgot-password" className="text-xs text-[#1D4ED8] dark:text-blue-400 hover:underline cursor-pointer">
              Forgot Password?
            </NavLink>
          </div>
          <div className="relative">
            <input
              type={showPassword ? 'text' : 'password'}
              placeholder="••••••••"
              name="password"
              value={form.password}
              onChange={handleChange}
              className="input input-bordered w-full h-10.5 sm:h-11 text-xs sm:text-sm px-3.5 rounded-xl focus:border-blue-700 focus:outline-none pr-10"
            />
            <button
              type="button"
              onClick={() => setShowPassword((prev) => !prev)}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-base-content/40 hover:text-base-content/70 transition-colors cursor-pointer"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
            </button>
          </div>
        </div>

        <div className="flex items-center gap-2 pt-1">
          <button
            type="submit"
            className="btn flex-1 bg-[#1D4ED8] text-white hover:bg-[#1D4ED8]/90 disabled:opacity-50 disabled:cursor-not-allowed h-10.5 sm:h-11 text-xs sm:text-sm font-bold rounded-xl shadow-md shadow-[#1D4ED8]/20 border-none cursor-pointer"
            disabled={loading}
          >
            {loading ? 'Logging in...' : 'Login to Portal'}
          </button>
          <span className="text-[11px] font-extrabold uppercase opacity-45 shrink-0 select-none px-0.5">
            or
          </span>
          <div className="flex-1 min-w-0">
            <GoogleAuthButton hideDivider buttonText="Google" />
          </div>
        </div>
      </form>

      {/* Footer */}
      <p className="mt-4 sm:mt-5 text-center text-xs sm:text-sm opacity-75">
        Don't have an account?{' '}
        <NavLink
          to="/register"
          className="text-[#1D4ED8] dark:text-blue-400 font-bold hover:underline"
        >
          Register here
        </NavLink>
      </p>

      {/* Back to Landing Page */}
      <button
        type="button"
        onClick={() => navigate('/')}
        className="w-full mt-3.5 sm:mt-4 flex items-center justify-center gap-2 h-10.5 sm:h-11 text-xs sm:text-sm font-semibold rounded-xl border border-base-300 bg-base-200/50 hover:bg-base-200 text-base-content/80 hover:text-base-content transition-colors cursor-pointer"
      >
        <ArrowLeft className="w-3.5 h-3.5" /> Back to Landing Page
      </button>

      {/* Zero-Knowledge Privacy Vault Activation / Unlock Modal */}
      <PrivacyPinModal
        isOpen={unlockVaultState.isOpen}
        mode={unlockVaultState.mode}
        vaultBlob={unlockVaultState.vaultBlob}
        vaultSalt={unlockVaultState.vaultSalt}
        seedBlindSalt={unlockVaultState.seedBlindSalt}
        serverActorToken={unlockVaultState.serverActorToken}
        onSuccess={(_actorToken) => {
          setUnlockVaultState((prev) => ({ ...prev, isOpen: false }));
          navigate('/dashboard');
        }}
        onCancel={() => {
          setUnlockVaultState((prev) => ({ ...prev, isOpen: false }));
        }}
      />
    </AuthLayout>
  );
};

export default Login;
