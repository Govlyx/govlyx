import { useState } from 'react';
import { GoogleLogin, type CredentialResponse } from '@react-oauth/google';
import { useNavigate } from 'react-router-dom';
import { checkGoogleUser, registerWithGoogle } from '../../api/googleAuthService';
import { persistAuthToken } from '../../utils/auth';
import { queryClient } from '../../api/queryClient';
import { showToast } from '../../utils/toast';
import { parseError } from '../../utils/error-handler';

const GoogleColorIcon = () => (
  <div className="w-5 h-5 rounded-full bg-white flex items-center justify-center p-0.5 shrink-0 shadow-sm">
    <svg className="w-full h-full" viewBox="0 0 24 24">
      <path
        fill="#4285F4"
        d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.665-5.17 3.665-9.17z"
      />
      <path
        fill="#34A853"
        d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.25v3.15C3.26 21.36 7.33 24 12 24z"
      />
      <path
        fill="#FBBC05"
        d="M5.28 14.27c-.25-.72-.38-1.49-.38-2.27s.13-1.55.38-2.27V6.58H1.25C.45 8.16 0 9.97 0 12s.45 3.84 1.25 5.42l4.03-3.15z"
      />
      <path
        fill="#EA4335"
        d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.26 2.64 1.25 6.58l4.03 3.15c.95-2.83 3.6-4.98 6.72-4.98z"
      />
    </svg>
  </div>
);

interface GoogleAuthButtonProps {
  hideDivider?: boolean;
  buttonText?: string;
}

const GoogleAuthButton = ({ hideDivider = false, buttonText = "Google" }: GoogleAuthButtonProps) => {
  const navigate = useNavigate();
  const [pendingToken, setPendingToken] = useState<string | null>(null);
  const [showOnboarding, setShowOnboarding] = useState(false);
  const [form, setForm] = useState({ pincode: '', isAdult: false, acceptedPolicy: false });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleGoogleSuccess = async (credentialResponse: CredentialResponse) => {
    const idToken = credentialResponse.credential;
    if (!idToken) {
      showToast.error('Failed to receive credentials from Google.');
      return;
    }
    setLoading(true);
    try {
      const response = await checkGoogleUser(idToken);

      // Existing user — log straight in
      const token =
        response.data?.token ||
        response.data?.authToken ||
        response.data?.accessToken ||
        response.data?.jwt;

      if (response.success && token) {
        persistAuthToken(token);
        queryClient.clear();
        navigate('/dashboard');
        return;
      }

      // New user — show onboarding form, keep idToken in state
      if (response.message === 'onboarding_required' || !response.data) {
        setPendingToken(idToken);
        setShowOnboarding(true);
      }
    } catch (err: any) {
      showToast.error(parseError(err));
    } finally {
      setLoading(false);
    }
  };

  const handleOnboardingSubmit = async () => {
    setError(null);
    const pincodeRegex = /^[1-9][0-9]{5}$/;
    if (!pincodeRegex.test(form.pincode)) {
      setError('Please enter a valid 6-digit Indian pincode (cannot start with 0)');
      return;
    }
    if (!form.isAdult) {
      setError('You must be 18 or older to create an account');
      return;
    }
    if (!form.acceptedPolicy) {
      setError('You must accept the Privacy Policy & Terms to continue');
      return;
    }

    setLoading(true);
    try {
      const response = await registerWithGoogle({
        token: pendingToken!,
        pincode: form.pincode,
        isAdult: true,
        acceptedPolicy: true,
      });

      const token =
        response.data?.token ||
        response.data?.authToken ||
        response.data?.accessToken ||
        response.data?.jwt;

      if (response.success && token) {
        persistAuthToken(token);
        queryClient.clear();
        navigate('/dashboard');
      } else {
        setError(response.error || response.message || 'Registration failed. Please try again.');
      }
    } catch (err: any) {
      setError(parseError(err));
    } finally {
      setLoading(false);
    }
  };

  // ── Onboarding Modal (shown inline/overlay after Google popup closes) ──
  if (showOnboarding) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
        <div className="bg-base-100 rounded-3xl border border-black/10 dark:border-white/15 shadow-2xl p-8 w-full max-w-md">
          <h2 className="text-xl font-black mb-1">Almost there!</h2>
          <p className="text-sm opacity-60 mb-6">
            Just a few details to set up your Govlyx account.
          </p>

          {error && (
            <div className="mb-4 rounded-xl bg-red-500/10 border border-red-500/30 px-4 py-2.5 text-sm text-red-400">
              {error}
            </div>
          )}

          {/* Pincode */}
          <div className="space-y-1 mb-4">
            <label className="text-sm font-medium opacity-80">📍 Your Area Pincode</label>
            <input
              type="text"
              placeholder="e.g. 110001"
              maxLength={6}
              value={form.pincode}
              onChange={e => setForm({ ...form, pincode: e.target.value.replace(/\D/g, '').slice(0, 6) })}
              className="input input-bordered w-full focus:border-blue-700 focus:outline-none"
            />
            <p className="text-xs opacity-50">Used to show you civic issues and updates near you</p>
          </div>

          {/* 18+ Consent */}
          <div className="flex items-start gap-2.5 mb-3">
            <input
              type="checkbox"
              id="isAdult"
              checked={form.isAdult}
              onChange={e => setForm({ ...form, isAdult: e.target.checked })}
              className="checkbox checkbox-primary checkbox-sm mt-0.5 rounded-md cursor-pointer"
            />
            <label htmlFor="isAdult" className="text-xs opacity-80 cursor-pointer select-none leading-relaxed">
              I confirm that I am <strong>18 years of age or older</strong>
            </label>
          </div>

          {/* Policy Acceptance */}
          <div className="flex items-start gap-2.5 mb-6">
            <input
              type="checkbox"
              id="acceptedPolicy"
              checked={form.acceptedPolicy}
              onChange={e => setForm({ ...form, acceptedPolicy: e.target.checked })}
              className="checkbox checkbox-primary checkbox-sm mt-0.5 rounded-md cursor-pointer"
            />
            <label htmlFor="acceptedPolicy" className="text-xs opacity-80 cursor-pointer select-none leading-relaxed">
              I agree to the{' '}
              <a href="/privacy-policy" target="_blank" rel="noreferrer" className="text-[#1D4ED8] dark:text-blue-400 hover:underline font-semibold">
                Privacy Policy & Terms
              </a>
              , including the Copyright Policy and 3-Strike account suspension rule.
            </label>
          </div>

          <button
            onClick={handleOnboardingSubmit}
            disabled={loading}
            className="btn w-full bg-[#1D4ED8] text-white hover:bg-[#1D4ED8]/90 border-none rounded-xl h-12 font-bold shadow-lg shadow-[#1D4ED8]/20 disabled:opacity-50 cursor-pointer"
          >
            {loading ? 'Creating Account...' : 'Get Started →'}
          </button>

          <button
            onClick={() => { setShowOnboarding(false); setPendingToken(null); }}
            className="mt-3 w-full text-xs opacity-50 hover:opacity-80 transition-opacity bg-transparent border-none cursor-pointer text-center"
          >
            Cancel
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full">
      {!hideDivider && (
        <div className="divider text-[10px] sm:text-xs opacity-50 my-2 sm:my-2.5">OR CONTINUE WITH</div>
      )}
      
      {/* Custom styled Google Button */}
      <div className="relative w-full overflow-hidden rounded-xl">
        <button
          type="button"
          disabled={loading}
          className="btn w-full bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 text-white font-bold h-10.5 sm:h-11 rounded-xl border-none shadow-md shadow-[#1D4ED8]/20 flex items-center justify-center gap-2 transition-all active:scale-[0.99] disabled:opacity-80"
        >
          {loading ? (
            <div className="flex items-center gap-2">
              <svg
                className="animate-spin h-4 w-4 text-white"
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle
                  className="opacity-25"
                  cx="12"
                  cy="12"
                  r="10"
                  stroke="currentColor"
                  strokeWidth="4"
                ></circle>
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                ></path>
              </svg>
              <span className="text-xs sm:text-sm font-semibold">Signing you in...</span>
            </div>
          ) : (
            <>
              <GoogleColorIcon />
              <span className="text-xs sm:text-sm font-bold truncate">{buttonText}</span>
            </>
          )}
        </button>

        {/* Hidden transparent GoogleLogin overlay - kept permanently mounted to avoid GSI re-initialization */}
        <div
          className={`absolute inset-0 opacity-[0.0001] [&>div]:!w-full [&>div]:!h-full [&_iframe]:!w-full [&_iframe]:!h-full overflow-hidden ${
            loading ? 'pointer-events-none' : 'cursor-pointer'
          }`}
        >
          <GoogleLogin
            onSuccess={handleGoogleSuccess}
            onError={() => showToast.error('Google sign-in failed. Please try again.')}
            width="400"
            text="continue_with"
          />
        </div>
      </div>
    </div>
  );
};

export default GoogleAuthButton;
