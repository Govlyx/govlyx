import { useState } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import AuthLayout from '../components/auth/AuthLayout';
import AuthHeader from '../components/auth/AuthHeader';
import { showToast } from '../utils/toast';
import { parseError } from '../utils/error-handler';
import ThemeToggle from '../components/ui/ThemeToggle';
import { Eye, EyeOff } from 'lucide-react';
import api from '../api/axios';

const ResetPassword = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const token = queryParams.get('token');

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!password || !confirmPassword) {
      showToast.error('Both fields are required');
      return;
    }
    if (password !== confirmPassword) {
      showToast.error('Passwords do not match');
      return;
    }
    if (!token) {
      showToast.error('Invalid token');
      return;
    }

    setLoading(true);
    try {
      await api.post('/auth/reset-password', { token, newPassword: password });
      showToast.success('Password has been reset successfully.');
      navigate('/login');
    } catch (err: any) {
      showToast.error(parseError(err));
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <AuthLayout>
        <AuthHeader title="Invalid Link" subtitle="This password reset link is invalid or has expired." />
        <NavLink to="/forgot-password" className="btn btn-outline w-full rounded-xl">
          Request a new link
        </NavLink>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout>
      <Helmet>
        <title>Reset Password | Govlyx</title>
      </Helmet>
      <div className="flex items-center justify-end mb-3 sm:mb-4">
        <ThemeToggle
          size={16}
          className="p-1 rounded-lg bg-base-300/50 hover:bg-base-300"
        />
      </div>
      <AuthHeader title="Reset Password" subtitle="Enter your new password below" />

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="space-y-0.5">
          <label className="text-xs sm:text-sm font-semibold opacity-85 block">
            New Password
          </label>
          <div className="relative">
            <input
              type={showPassword ? 'text' : 'password'}
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="input input-bordered w-full h-10.5 sm:h-11 text-xs sm:text-sm px-3.5 rounded-xl focus:border-blue-700 focus:outline-none pr-10"
            />
            <button
              type="button"
              onClick={() => setShowPassword((prev) => !prev)}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-base-content/40 hover:text-base-content/70 transition-colors cursor-pointer"
            >
              {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
            </button>
          </div>
        </div>

        <div className="space-y-0.5">
          <label className="text-xs sm:text-sm font-semibold opacity-85 block">
            Confirm Password
          </label>
          <div className="relative">
            <input
              type={showConfirmPassword ? 'text' : 'password'}
              placeholder="••••••••"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="input input-bordered w-full h-10.5 sm:h-11 text-xs sm:text-sm px-3.5 rounded-xl focus:border-blue-700 focus:outline-none pr-10"
            />
            <button
              type="button"
              onClick={() => setShowConfirmPassword((prev) => !prev)}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-base-content/40 hover:text-base-content/70 transition-colors cursor-pointer"
            >
              {showConfirmPassword ? <EyeOff size={17} /> : <Eye size={17} />}
            </button>
          </div>
        </div>

        <button
          type="submit"
          className="btn w-full bg-[#1D4ED8] text-white hover:bg-[#1D4ED8]/90 disabled:opacity-50 border-none rounded-xl mt-2"
          disabled={loading}
        >
          {loading ? 'Resetting...' : 'Reset Password'}
        </button>
      </form>
    </AuthLayout>
  );
};

export default ResetPassword;
