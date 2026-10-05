import { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import AuthLayout from '../components/auth/AuthLayout';
import AuthHeader from '../components/auth/AuthHeader';
import AuthInput from '../components/auth/AuthInput';
import { registerCitizen } from '../api/authService';
import { Mail, Eye, EyeOff, Info, ArrowLeft, Check, X } from 'lucide-react';
import ThemeToggle from '../components/ui/ThemeToggle';
import { parseError } from '../utils/error-handler';
import GoogleAuthButton from '../components/auth/GoogleAuthButton';

type RegisterType = 'citizen' | 'department';

const Register = () => {
  const navigate = useNavigate();

  const [type] = useState<RegisterType>('citizen');
  const [form, setForm] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    pincode: '',
    isAdult: false,
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    let { name, value, type, checked } = e.target;
    if (name === 'pincode') {
      // Only allow digits and cap at 6 digits
      value = value.replace(/\D/g, '').slice(0, 6);
    }
    setForm({ ...form, [name]: type === 'checkbox' ? checked : value });
  };

  const handleRegister = async () => {
    setError(null);
    setSuccess(null);

    // Frontend validation
    if (
      !form.email ||
      !form.password ||
      !form.confirmPassword ||
      !form.pincode
    ) {
      setError('All fields are required');
      return;
    }

    const passwordRegex =
      /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!.*_\-])(?=\S+$).{8,20}$/;
    if (!passwordRegex.test(form.password)) {
      setError(
        'Password does not meet the strength requirements. Verify the checklist below.',
      );
      return;
    }

    if (form.password !== form.confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    const pincodeRegex = /^[1-9][0-9]{5}$/;
    if (!pincodeRegex.test(form.pincode)) {
      setError(
        'Please enter a valid 6-digit Indian pincode (cannot start with 0)',
      );
      return;
    }

    if (!form.isAdult) {
      setError('You must be 18 or older to register');
      return;
    }

    setLoading(true);
    try {
      const payload = {
        email: form.email,
        password: form.password,
        pincode: form.pincode,
        username: 'anonymous', // Satisfies backend validation requirement; ignored by citizen registration logic
        isAdult: form.isAdult,
      };

      const response = await registerCitizen(payload);

      if (response.success) {
        setSuccess(
          response.error ||
            response.message ||
            'Welcome to Govlyx!\nA verification link has been sent to your email address.\nPlease check your inbox and click the verification link to activate your account.',
        );
      } else {
        setError(response.error || response.message || 'Registration failed');
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
        <title>
          Register | Govlyx India - Join Your Local Neighborhood App
        </title>
        <meta
          name="description"
          content="Sign up for Govlyx to connect with your local neighborhood, submit municipal grievances to authorities anonymously, and participate in local discussions."
        />
        <link rel="canonical" href="https://govlyx.com/register" />
        <meta property="og:type" content="website" />
        <meta property="og:url" content="https://govlyx.com/register" />
        <meta property="og:site_name" content="Govlyx" />
        <meta property="og:title" content="Register | Govlyx India" />
        <meta
          property="og:description"
          content="Sign up for Govlyx to connect with your local neighborhood and submit municipal grievances anonymously."
        />
        <meta property="og:image" content="https://govlyx.com/govlyx-og.png" />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content="Register | Govlyx India" />
        <meta
          name="twitter:description"
          content="Sign up for Govlyx to connect with your local neighborhood and submit municipal grievances anonymously."
        />
        <meta name="twitter:image" content="https://govlyx.com/govlyx-og.png" />
      </Helmet>
      <div className="flex items-center justify-end mb-3 sm:mb-4">
        <ThemeToggle
          size={16}
          className="p-1 rounded-lg bg-base-300/50 hover:bg-base-300"
        />
      </div>
      <AuthHeader
        title={type === 'citizen' ? 'Join Govlyx' : 'Onboard Department'}
        subtitle={
          type === 'citizen'
            ? 'Join Govlyx anonymously'
            : 'Register a verified government body'
        }
      />

      {/* Error Message */}
      {error && (
        <div className="mb-3 rounded-xl bg-red-500/10 border border-red-500/30 px-3 py-2 text-xs text-red-400 flex items-center gap-2 animate-in fade-in slide-in-from-top-2">
          <span className="h-1 w-1 rounded-full bg-red-500 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {success ? (
        <div className="mt-4 flex flex-col items-center justify-center p-5 bg-base-200/50 rounded-2xl border border-base-300 backdrop-blur-md text-center gap-3">
          <div className="w-12 h-12 rounded-full bg-green-500/10 flex items-center justify-center text-green-500">
            <Mail size={28} />
          </div>
          <h3 className="text-base font-black text-green-400">
            Check Your Email
          </h3>
          <p className="text-xs opacity-80 px-2 leading-relaxed whitespace-pre-line">
            {success}
          </p>
          <div className="mt-1 rounded-xl bg-red-500/5 border border-red-500/20 px-3 py-2 text-xs flex items-start text-left gap-2 animate-subtle-blink w-full">
            <Info size={14} className="shrink-0 mt-0.5 glow-red-text" />
            <span className="glow-red-text text-[11px]">
              If you do not see the verification email in your inbox, please
              check your <strong>Spam</strong> or <strong>Junk</strong> folder.
            </span>
          </div>
          <button
            onClick={() => navigate('/login')}
            className="btn bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 text-white border-none rounded-lg sm:rounded-xl h-9.5 sm:h-10 text-xs sm:text-sm px-6 font-bold flex items-center gap-2 mt-2 shadow-md shadow-[#1D4ED8]/20 w-full cursor-pointer"
          >
            Go to Login
          </button>
        </div>
      ) : (
        <>
          {/* Form */}
          <form
            className="space-y-2 sm:space-y-2.5"
            onSubmit={(e) => {
              e.preventDefault();
              handleRegister();
            }}
          >
            <AuthInput
              label="Email Address"
              type="email"
              placeholder="user@govlyx.com"
              name="email"
              value={form.email}
              onChange={handleChange}
            />

            <div className="space-y-0.5">
              <label className="text-xs sm:text-sm font-semibold opacity-85 block">
                Password
              </label>
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

            {form.password.length > 0 && (
              <div className="space-y-1 p-2 rounded-xl bg-base-300/30 border border-base-300 text-[11px] mt-0.5">
                <p className="font-semibold opacity-70">
                  Password Requirements:
                </p>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-x-3 gap-y-0.5">
                  {[
                    {
                      label: '8-20 characters',
                      met:
                        form.password.length >= 8 && form.password.length <= 20,
                    },
                    {
                      label: '1 uppercase letter (A-Z)',
                      met: /[A-Z]/.test(form.password),
                    },
                    {
                      label: '1 lowercase letter (a-z)',
                      met: /[a-z]/.test(form.password),
                    },
                    { label: '1 number (0-9)', met: /\d/.test(form.password) },
                    {
                      label: '1 special character (@#$%^&+=!.*_-)',
                      met: /[@#$%^&+=!.*_\-]/.test(form.password),
                    },
                    {
                      label: 'No spaces',
                      met:
                        !/\s/.test(form.password) && form.password.length > 0,
                    },
                  ].map((check, idx) => (
                    <div key={idx} className="flex items-center gap-1.5 py-0.5">
                      <span
                        className={`w-3.5 h-3.5 rounded-full flex items-center justify-center border ${
                          check.met
                            ? 'bg-green-500/10 text-green-500 border-green-500/30'
                            : 'bg-red-500/10 text-red-500 border-red-500/30'
                        }`}
                      >
                        {check.met ? <Check size={9} /> : <X size={9} />}
                      </span>
                      <span
                        className={
                          check.met ? 'opacity-90 font-medium' : 'opacity-50'
                        }
                      >
                        {check.label}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="space-y-0.5">
              <label className="text-xs sm:text-sm font-semibold opacity-85 block">
                Confirm Password
              </label>
              <div className="relative">
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  placeholder="••••••••"
                  name="confirmPassword"
                  value={form.confirmPassword}
                  onChange={handleChange}
                  className="input input-bordered w-full h-10.5 sm:h-11 text-xs sm:text-sm px-3.5 rounded-xl focus:border-blue-700 focus:outline-none pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword((prev) => !prev)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-base-content/40 hover:text-base-content/70 transition-colors cursor-pointer"
                  aria-label={
                    showConfirmPassword ? 'Hide password' : 'Show password'
                  }
                >
                  {showConfirmPassword ? (
                    <EyeOff size={17} />
                  ) : (
                    <Eye size={17} />
                  )}
                </button>
              </div>
            </div>

            <AuthInput
              label="Pincode"
              type="text"
              placeholder="110001"
              helperText="Used to show you local civic issues"
              name="pincode"
              value={form.pincode}
              onChange={handleChange}
            />

            <div className="flex items-start gap-2 my-2">
              <input
                type="checkbox"
                id="isAdult"
                name="isAdult"
                checked={form.isAdult}
                onChange={handleChange}
                className="checkbox checkbox-primary checkbox-xs sm:checkbox-sm mt-0.5 rounded cursor-pointer focus:ring-1 focus:ring-primary"
              />
              <label
                htmlFor="isAdult"
                className="text-[11px] sm:text-xs opacity-80 cursor-pointer select-none leading-snug"
              >
                I confirm that I am 18 years of age or older and agree to the{' '}
                <NavLink
                  to="/privacy-policy"
                  target="_blank"
                  className="text-[#1D4ED8] dark:text-blue-400 font-bold hover:underline"
                >
                  Privacy Policy & Terms
                </NavLink>
                , including the Copyright Policy and 3-Strike account suspension
                rule.
              </label>
            </div>

            <div className="flex items-center gap-2 pt-1">
              <button
                type="submit"
                className="btn flex-1 bg-[#1D4ED8] text-white hover:bg-[#1D4ED8]/90 disabled:opacity-50 disabled:cursor-not-allowed h-10.5 sm:h-11 text-xs sm:text-sm font-bold rounded-xl shadow-md shadow-[#1D4ED8]/20 border-none cursor-pointer"
                disabled={loading}
              >
                {loading ? 'Registering...' : 'Join Govlyx'}
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
          <p className="mt-4 sm:mt-5 text-center text-xs sm:text-sm opacity-70">
            Already have an account?{' '}
            <NavLink
              to="/login"
              className="text-[#1D4ED8] dark:text-blue-400 font-bold hover:underline"
            >
              Login
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
        </>
      )}
    </AuthLayout>
  );
};

export default Register;
