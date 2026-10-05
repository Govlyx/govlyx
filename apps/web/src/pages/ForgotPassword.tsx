import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import AuthLayout from '../components/auth/AuthLayout';
import AuthHeader from '../components/auth/AuthHeader';
import AuthInput from '../components/auth/AuthInput';
import { showToast } from '../utils/toast';
import { parseError } from '../utils/error-handler';
import ThemeToggle from '../components/ui/ThemeToggle';
import api from '../api/axios'; // Or however they call API

const ForgotPassword = () => {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email) {
      showToast.error('Email is required');
      return;
    }

    setLoading(true);
    try {
      await api.post('/api/auth/forgot-password', { email });
      setSuccess(true);
      showToast.success('Password reset link sent if account exists.');
    } catch (err: any) {
      showToast.error(parseError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout>
      <Helmet>
        <title>Forgot Password | Govlyx</title>
      </Helmet>
      <div className="flex items-center justify-end mb-3 sm:mb-4">
        <ThemeToggle
          size={16}
          className="p-1 rounded-lg bg-base-300/50 hover:bg-base-300"
        />
      </div>
      <AuthHeader title="Forgot Password" subtitle="Enter your email to reset your password" />

      {success ? (
        <div className="text-center space-y-4">
          <p className="text-sm opacity-80">
            If an account exists with {email}, we have sent a password reset link to it. Please check your inbox.
          </p>
          <NavLink to="/login" className="btn btn-outline w-full rounded-xl">
            Back to Login
          </NavLink>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <AuthInput
            label="Email Address"
            type="email"
            placeholder="you@example.com"
            name="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />

          <button
            type="submit"
            className="btn w-full bg-[#1D4ED8] text-white hover:bg-[#1D4ED8]/90 disabled:opacity-50 border-none rounded-xl"
            disabled={loading}
          >
            {loading ? 'Sending...' : 'Send Reset Link'}
          </button>

          <p className="mt-4 text-center text-xs sm:text-sm opacity-75">
            Remember your password?{' '}
            <NavLink
              to="/login"
              className="text-[#1D4ED8] dark:text-blue-400 font-bold hover:underline cursor-pointer"
            >
              Log in here
            </NavLink>
          </p>
        </form>
      )}
    </AuthLayout>
  );
};

export default ForgotPassword;
