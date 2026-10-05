import React, { useState, useEffect, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';
import {
  Trash2,
  Check,
  X,
  AlertTriangle,
  Loader2,
  Lock,
  Globe,
  Shield,
  Eye,
  EyeOff,
  UserX,
  ChevronDown,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import axiosInstance from '../api/axiosConfig';
import { useCurrentUser } from '../hooks/useUser';
import { clearAuthTokens } from '../utils/auth';
import {
  useLanguage,
  type LangCode,
  SUPPORTED_LANGUAGES,
} from '../context/LanguageContext';
import { showToast } from '../utils/toast';
import ConfirmModal from '../components/post/ConfirmModal';

// ═════════════════════════════════════════════════════════════════════════════════
// Custom Sleek Select Dropdown Component
// ═════════════════════════════════════════════════════════════════════════════════
interface CustomSelectProps {
  value: string;
  onChange: (val: string) => void;
  options: { value: string; label: string }[];
  className?: string;
}

function CustomSelect({
  value,
  onChange,
  options,
  className = '',
}: CustomSelectProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const selectedOption = options.find((o) => o.value === value) || options[0];

  return (
    <div ref={ref} className={`relative notranslate ${className}`}>
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        className="w-full bg-base-100 border border-black/10 dark:border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-bold text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/30 transition-all cursor-pointer flex items-center justify-between gap-2 shadow-xs hover:border-[#1D4ED8]/40 text-left"
      >
        <span className="truncate">{selectedOption?.label}</span>
        <ChevronDown
          className={`w-4 h-4 text-slate-400 shrink-0 transition-transform duration-200 ${
            open ? 'rotate-180 text-[#1D4ED8]' : ''
          }`}
        />
      </button>

      {open && (
        <div className="absolute top-full mt-1.5 left-0 right-0 z-50 rounded-2xl border border-black/10 dark:border-base-300 bg-base-100 dark:bg-base-100 backdrop-blur-md shadow-2xl p-1.5 space-y-0.5 max-h-60 overflow-y-auto scrollbar-thin animate-in fade-in zoom-in-95 duration-150">
          {options.map((option) => {
            const isSelected = option.value === value;
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => {
                  onChange(option.value);
                  setOpen(false);
                }}
                className={`w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs sm:text-sm font-semibold transition-all cursor-pointer text-left ${
                  isSelected
                    ? 'bg-[#1D4ED8] text-white shadow-sm font-bold'
                    : 'text-slate-700 dark:text-slate-200 hover:bg-base-200 dark:hover:bg-white/10'
                }`}
              >
                <span className="truncate">{option.label}</span>
                {isSelected && (
                  <Check size={14} className="shrink-0 stroke-[2.5]" />
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

const getErrorMessage = (err: any, fallback: string): string => {
  if (err.response?.data) {
    const data = err.response.data;
    if (data.error && typeof data.error === 'string') {
      return data.error;
    }
    if (data.message && typeof data.message === 'string') {
      return data.message;
    }
    if (data.data && typeof data.data === 'object') {
      const values = Object.values(data.data);
      if (values.length > 0 && typeof values[0] === 'string') {
        return values[0];
      }
    }
  }
  return err.message || fallback;
};

// ═════════════════════════════════════════════════════════════════════════════════
// Settings page
// ═════════════════════════════════════════════════════════════════════════════════
const Settings = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: user } = useCurrentUser();

  // ── Account editing ──
  const [editField, setEditField] = useState<string | null>(null);
  const [preferredLanguage, setPreferredLanguage] = useState('en');
  const [interfaceLanguage, setInterfaceLanguage] = useState('en');
  const [autoTranslate, setAutoTranslate] = useState(false);
  const { setLanguage: setGlobalLanguage } = useLanguage();
  const [profanityFilterLevel, setProfanityFilterLevel] = useState('STRICT');
  const [mutedWords, setMutedWords] = useState('');
  const [saving, setSaving] = useState(false);

  // ── Password change ──
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [changingPassword, setChangingPassword] = useState(false);
  const [passwordStatus, setPasswordStatus] = useState<{
    message: string;
    type: 'success' | 'error';
  } | null>(null);
  const [showOldPassword, setShowOldPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);

  // ── Deactivation ──
  const [showDeactivate, setShowDeactivate] = useState(false);
  const [confirmText, setConfirmText] = useState('');
  const [deactivating, setDeactivating] = useState(false);
  const [showDeletedModal, setShowDeletedModal] = useState(false);
  const [countdown, setCountdown] = useState(5);

  // ── Logout confirmation ──
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  // ── Sync user data ──
  useEffect(() => {
    if (!user) return;
    const u = user as any;
    setPreferredLanguage(u.preferredLanguage || 'en');
    setAutoTranslate(u.autoTranslate || false);
    setProfanityFilterLevel(u.profanityFilterLevel || 'STRICT');
    setMutedWords(u.mutedWords || '');

    // Sync global interface language from localStorage, fallback to profile preference
    const savedInterfaceLang = localStorage.getItem('govlyx_ui_language');
    if (savedInterfaceLang) {
      setInterfaceLanguage(savedInterfaceLang);
    } else {
      const fallbackLang = u.interfaceLanguage || u.preferredLanguage || 'en';
      setInterfaceLanguage(fallbackLang);
      setGlobalLanguage(fallbackLang as LangCode);
    }
  }, [user]);

  // ── Save profile ──
  const saveProfile = async () => {
    setSaving(true);
    try {
      if (editField === 'localization') {
        localStorage.setItem('govlyx_ui_language', interfaceLanguage);
        setGlobalLanguage(interfaceLanguage as LangCode);
      }
      await axiosInstance.put('/api/users/profile', {
        preferredLanguage,
        interfaceLanguage, // Sync preference to backend database
        autoTranslate,
        profanityFilterLevel,
        mutedWords,
      });
      queryClient.invalidateQueries({ queryKey: ['currentUser'] });
      setEditField(null);
      showToast.success('Changes saved successfully');
    } catch (err: any) {
      showToast.error(getErrorMessage(err, 'Failed to update profile'));
    } finally {
      setSaving(false);
    }
  };

  // ── Change Password ──
  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordStatus(null);
    const passwordRegex =
      /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!.*_\-])(?=\S+$).{8,20}$/;
    if (!passwordRegex.test(newPassword)) {
      return setPasswordStatus({
        message:
          'New password does not meet strength requirements. Check the list below.',
        type: 'error',
      });
    }
    setChangingPassword(true);
    try {
      await axiosInstance.put('/api/users/change-password', {
        oldPassword,
        newPassword,
      });
      setPasswordStatus({
        message: 'Password updated successfully!',
        type: 'success',
      });
      setOldPassword('');
      setNewPassword('');
    } catch (err: any) {
      setPasswordStatus({
        message: getErrorMessage(
          err,
          'Failed to update password. Verify current password.',
        ),
        type: 'error',
      });
    } finally {
      setChangingPassword(false);
    }
  };

  const handleConfirmDeleteRedirect = useCallback(() => {
    clearAuthTokens();
    queryClient.clear();
    navigate('/login', { replace: true });
  }, [navigate, queryClient]);

  useEffect(() => {
    if (!showDeletedModal) return;
    if (countdown <= 0) {
      handleConfirmDeleteRedirect();
      return;
    }
    const timer = setTimeout(() => {
      setCountdown((prev) => prev - 1);
    }, 1000);
    return () => clearTimeout(timer);
  }, [showDeletedModal, countdown, handleConfirmDeleteRedirect]);

  // ── Deactivate ──
  const deactivateAccount = async () => {
    if (confirmText !== 'DELETE') return;
    setDeactivating(true);
    try {
      await axiosInstance.delete('/api/users/me');
      setShowDeactivate(false);
      setShowDeletedModal(true);
    } catch (err: any) {
      showToast.error(getErrorMessage(err, 'Failed to delete account'));
      setDeactivating(false);
    }
  };

  // ── Logout ──
  const handleLogout = () => {
    clearAuthTokens();
    navigate('/login', { replace: true });
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* PAGE HEADER */}
      <div className="text-left">
        <h1 className="text-xl sm:text-2xl font-black text-slate-900 dark:text-white tracking-tight">
          Settings
        </h1>
        <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1 font-medium">
          Manage your account, privacy, and preferences
        </p>
      </div>

      {/* ═══════════════ LANGUAGE & LOCALIZATION ═══════════════ */}
      <div className="rounded-2xl border border-black/10 dark:border-base-300 bg-base-200 p-5 sm:p-6 space-y-5 transition-all shadow-sm dark:shadow-none text-left">
        <div className="flex items-center gap-2.5 border-b border-black/5 dark:border-white/5 pb-3.5 text-base-content">
          <Globe size={18} className="text-[#1D4ED8] dark:text-blue-400" />
          <h2 className="font-bold text-sm sm:text-base tracking-tight text-slate-900 dark:text-white">
            Language & Localization
          </h2>
        </div>

        <div className="space-y-4">
          {/* 1. Interface Language */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <p className="text-xs sm:text-sm font-bold text-slate-800 dark:text-slate-200">
                Interface Language
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Set the primary language for buttons, menus, and application
                layout
              </p>
            </div>
            <CustomSelect
              value={interfaceLanguage}
              options={SUPPORTED_LANGUAGES.map((l) => ({
                value: l.code,
                label:
                  l.code === 'en' ? 'English' : `${l.label} (${l.nativeLabel})`,
              }))}
              onChange={(val) => {
                setInterfaceLanguage(val as LangCode);
                setEditField('localization');
              }}
              className="w-full sm:w-64 shrink-0"
            />
          </div>

          <div className="border-t border-black/5 dark:border-white/5" />

          {/* 2. Post Translation Language */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <p className="text-xs sm:text-sm font-bold text-slate-800 dark:text-slate-200">
                Post Translation Language
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Set the target language for translating feed posts
              </p>
            </div>
            <CustomSelect
              value={preferredLanguage}
              options={SUPPORTED_LANGUAGES.map((l) => ({
                value: l.code,
                label:
                  l.code === 'en' ? 'English' : `${l.label} (${l.nativeLabel})`,
              }))}
              onChange={(val) => {
                setPreferredLanguage(val as LangCode);
                setEditField('localization');
              }}
              className="w-full sm:w-64 shrink-0"
            />
          </div>

          <div className="border-t border-black/5 dark:border-white/5" />

          {/* 3. Auto-Translate Feed Toggle */}
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs sm:text-sm font-bold text-slate-800 dark:text-slate-200">
                Auto-Translate Feed
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Automatically translate posts to your post translation language
              </p>
            </div>
            <input
              type="checkbox"
              className="toggle toggle-primary toggle-sm shrink-0"
              checked={autoTranslate}
              onChange={(e) => {
                setAutoTranslate(e.target.checked);
                setEditField('localization');
              }}
            />
          </div>
        </div>

        {editField === 'localization' && (
          <div className="pt-2 flex justify-end">
            <button
              className="bg-[#1D4ED8] hover:bg-[#1e40af] text-white font-bold text-xs sm:text-sm px-5 py-2 rounded-xl shadow-md shadow-[#1D4ED8]/20 flex items-center gap-2 transition-all cursor-pointer active:scale-95 border border-[#1D4ED8]"
              onClick={saveProfile}
              disabled={saving}
            >
              {saving ? (
                <Loader2 size={14} className="animate-spin" />
              ) : (
                <Check size={14} />
              )}
              {saving ? 'Saving…' : 'Save Translation Settings'}
            </button>
          </div>
        )}
      </div>

      {/* ═══════════════ FEED FILTERING & MODERATION ═══════════════ */}
      <div className="rounded-2xl border border-black/10 dark:border-base-300 bg-base-200 p-5 sm:p-6 space-y-5 transition-all shadow-sm dark:shadow-none text-left">
        <div className="flex items-center gap-2.5 border-b border-black/5 dark:border-white/5 pb-3.5 text-base-content">
          <Shield size={18} className="text-[#1D4ED8] dark:text-blue-400" />
          <h2 className="font-bold text-sm sm:text-base tracking-tight text-slate-900 dark:text-white">
            Feed Filtering & Moderation
          </h2>
        </div>

        <div className="space-y-4">
          <div className="space-y-1.5">
            <div>
              <p className="text-xs sm:text-sm font-bold text-slate-800 dark:text-slate-200">
                Profanity Filter Level
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Control how sensitive content is displayed in your feed
              </p>
            </div>
            <CustomSelect
              value={profanityFilterLevel}
              options={[
                { value: 'STRICT', label: 'Strict (Hide completely)' },
                { value: 'BLUR', label: 'Blur (Click to reveal)' },
                { value: 'OFF', label: 'Off (Show all content)' },
              ]}
              onChange={(val) => {
                setProfanityFilterLevel(val);
                setEditField('moderation');
              }}
              className="w-full"
            />
          </div>

          <div className="space-y-1.5 pt-2 border-t border-black/5 dark:border-white/5">
            <div>
              <p className="text-xs sm:text-sm font-bold text-slate-800 dark:text-slate-200">
                Muted Words
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Hide posts containing specific words (comma-separated)
              </p>
            </div>
            <textarea
              className="w-full bg-base-100 border border-black/10 dark:border-base-300 rounded-xl p-3.5 text-xs sm:text-sm text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/30 transition-all min-h-[70px] resize-y placeholder:text-slate-400"
              placeholder="e.g. politics, violence, spam"
              value={mutedWords}
              onChange={(e) => {
                setMutedWords(e.target.value);
                setEditField('moderation');
              }}
            />
          </div>
        </div>

        {editField === 'moderation' && (
          <div className="pt-2 flex justify-end">
            <button
              className="bg-[#1D4ED8] hover:bg-[#1e40af] text-white font-bold text-xs sm:text-sm px-5 py-2 rounded-xl shadow-md shadow-[#1D4ED8]/20 flex items-center gap-2 transition-all cursor-pointer active:scale-95 border border-[#1D4ED8]"
              onClick={saveProfile}
              disabled={saving}
            >
              {saving ? (
                <Loader2 size={14} className="animate-spin" />
              ) : (
                <Check size={14} />
              )}
              {saving ? 'Saving…' : 'Save Moderation Filters'}
            </button>
          </div>
        )}
      </div>

      {/* ═══════════════ SECURITY & PASSWORD ═══════════════ */}
      <div className="rounded-2xl border border-black/10 dark:border-base-300 bg-base-200 p-5 sm:p-6 space-y-5 transition-all shadow-sm dark:shadow-none text-left">
        <div className="flex items-center gap-2.5 border-b border-black/5 dark:border-white/5 pb-3.5 text-base-content">
          <Lock size={18} className="text-[#1D4ED8] dark:text-blue-400" />
          <h2 className="font-bold text-sm sm:text-base tracking-tight text-slate-900 dark:text-white">
            Security & Password
          </h2>
        </div>

        <form onSubmit={handlePasswordChange} className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
            <div className="space-y-1.5 relative">
              <label className="text-xs font-bold text-slate-700 dark:text-slate-300">
                Current Password
              </label>
              <div className="relative">
                <input
                  type={showOldPassword ? 'text' : 'password'}
                  className="w-full bg-base-100 border border-black/10 dark:border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/30 transition-all pr-10"
                  placeholder="Enter current password"
                  value={oldPassword}
                  onChange={(e) => {
                    setOldPassword(e.target.value);
                    setPasswordStatus(null);
                  }}
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowOldPassword(!showOldPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors bg-transparent border-none p-0 flex items-center justify-center cursor-pointer"
                  aria-label={
                    showOldPassword ? 'Hide password' : 'Show password'
                  }
                >
                  {showOldPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>

            <div className="space-y-1.5 relative">
              <label className="text-xs font-bold text-slate-700 dark:text-slate-300">
                New Password
              </label>
              <div className="relative">
                <input
                  type={showNewPassword ? 'text' : 'password'}
                  className="w-full bg-base-100 border border-black/10 dark:border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/30 transition-all pr-10"
                  placeholder="At least 8 characters"
                  value={newPassword}
                  onChange={(e) => {
                    setNewPassword(e.target.value);
                    setPasswordStatus(null);
                  }}
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowNewPassword(!showNewPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors bg-transparent border-none p-0 flex items-center justify-center cursor-pointer"
                  aria-label={
                    showNewPassword ? 'Hide password' : 'Show password'
                  }
                >
                  {showNewPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>

            {newPassword.length > 0 && (
              <div className="col-span-1 md:col-span-2 space-y-2 p-3.5 rounded-xl bg-base-100 border border-black/10 dark:border-base-300 text-xs mt-1">
                <p className="font-bold text-slate-700 dark:text-slate-300">
                  Password Requirements:
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5">
                  {[
                    {
                      label: '8-20 characters',
                      met: newPassword.length >= 8 && newPassword.length <= 20,
                    },
                    {
                      label: '1 uppercase letter (A-Z)',
                      met: /[A-Z]/.test(newPassword),
                    },
                    {
                      label: '1 lowercase letter (a-z)',
                      met: /[a-z]/.test(newPassword),
                    },
                    { label: '1 number (0-9)', met: /\d/.test(newPassword) },
                    {
                      label: '1 special character (@#$%^&+=!.*_-)',
                      met: /[@#$%^&+=!.*_\-]/.test(newPassword),
                    },
                    {
                      label: 'No spaces',
                      met: !/\s/.test(newPassword) && newPassword.length > 0,
                    },
                  ].map((check, idx) => (
                    <div key={idx} className="flex items-center gap-2 py-0.5">
                      <span
                        className={`w-4 h-4 rounded-full flex items-center justify-center border text-[9px] shrink-0 ${
                          check.met
                            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/30'
                            : 'bg-rose-500/10 text-rose-500 border-rose-500/30'
                        }`}
                      >
                        {check.met ? (
                          <Check size={11} strokeWidth={3} />
                        ) : (
                          <X size={11} strokeWidth={3} />
                        )}
                      </span>
                      <span
                        className={
                          check.met
                            ? 'text-slate-800 dark:text-slate-200 font-semibold'
                            : 'text-slate-400 font-medium'
                        }
                      >
                        {check.label}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          {passwordStatus && (
            <div
              className={`p-3 rounded-xl text-xs font-bold flex items-center gap-2 ${
                passwordStatus.type === 'success'
                  ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/20'
                  : 'bg-rose-500/10 text-rose-700 dark:text-rose-400 border border-rose-500/20'
              }`}
            >
              {passwordStatus.type === 'success' ? (
                <Check size={15} />
              ) : (
                <AlertTriangle size={15} />
              )}
              <span>{passwordStatus.message}</span>
            </div>
          )}

          <div className="flex justify-end pt-1">
            <button
              type="submit"
              id="change-password-btn"
              className="bg-[#1D4ED8] hover:bg-[#1e40af] text-white font-bold text-xs sm:text-sm px-5 py-2.5 rounded-xl shadow-md shadow-[#1D4ED8]/20 flex items-center gap-2 transition-all cursor-pointer active:scale-95 border border-[#1D4ED8]"
              disabled={changingPassword}
            >
              {changingPassword ? (
                <Loader2 size={14} className="animate-spin" />
              ) : (
                <Check size={14} />
              )}
              {changingPassword ? 'Updating…' : 'Update Password'}
            </button>
          </div>
        </form>
      </div>

      {/* ═══════════════ ACCOUNT ACTIONS ═══════════════ */}
      <div className="rounded-2xl border border-black/10 dark:border-base-300 bg-base-200 p-5 sm:p-6 space-y-5 transition-all shadow-sm dark:shadow-none text-left">
        <div className="flex items-center gap-2.5 border-b border-black/5 dark:border-white/5 pb-3.5 text-base-content">
          <Shield size={18} className="text-[#1D4ED8] dark:text-blue-400" />
          <h2 className="font-bold text-xs sm:text-sm uppercase tracking-wider text-slate-900 dark:text-white">
            Account Actions
          </h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 sm:gap-8 relative">
          {/* Middle vertical divider for desktop */}
          <div className="hidden md:block absolute left-1/2 top-0 bottom-0 w-px bg-black/5 dark:bg-white/5 -translate-x-1/2" />

          {/* Left half: Delete Account */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="min-w-0 flex-1">
              <p className="text-sm font-bold text-slate-900 dark:text-white">
                Delete Account
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 leading-relaxed">
                Permanently delete your account and all your data
              </p>
            </div>
            <button
              id="deactivate-btn"
              className="border border-rose-500/40 text-rose-600 dark:text-rose-400 hover:bg-rose-500/10 font-bold text-xs sm:text-sm px-4 py-2 rounded-xl transition-all cursor-pointer shrink-0"
              onClick={() => setShowDeactivate(true)}
            >
              Delete Account
            </button>
          </div>

          {/* Right half: Logout */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 md:pl-8 pt-6 md:pt-0 border-t md:border-t-0 border-black/5 dark:border-white/5">
            <div className="min-w-0 flex-1">
              <p className="text-sm font-bold text-slate-900 dark:text-white">
                Logout
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 leading-relaxed">
                Log out of your current session on this device
              </p>
            </div>
            <button
              id="logout-btn"
              onClick={() => setShowLogoutConfirm(true)}
              className="bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs sm:text-sm px-6 py-2 rounded-xl shadow-md shadow-rose-600/20 transition-all cursor-pointer shrink-0"
            >
              Logout
            </button>
          </div>
        </div>
      </div>

      {/* ═══════════════ LOGOUT CONFIRMATION MODAL ═══════════════ */}
      <ConfirmModal
        isOpen={showLogoutConfirm}
        onClose={() => setShowLogoutConfirm(false)}
        onConfirm={handleLogout}
        title="Log Out"
        message="Are you sure you want to log out of your account?"
        confirmLabel="Log Out"
        cancelLabel="Cancel"
        isDanger={true}
      />

      {/* ═══════════════ DEACTIVATION MODAL (PORTALED) ═══════════════ */}
      {typeof document !== 'undefined' &&
        showDeactivate &&
        createPortal(
          <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-in fade-in duration-200">
            <div className="w-full max-w-sm rounded-2xl bg-base-100 border border-black/10 dark:border-white/15 p-6 space-y-4 shadow-2xl text-left">
              <div className="flex items-center gap-2.5 text-rose-600 dark:text-rose-400">
                <AlertTriangle size={22} />
                <h3 className="font-bold text-lg text-slate-900 dark:text-white">
                  Delete Account
                </h3>
              </div>

              <div className="space-y-2 text-xs sm:text-sm text-slate-600 dark:text-slate-300">
                <p className="font-semibold">This will permanently:</p>
                <ul className="list-disc list-inside space-y-1 text-xs text-slate-500 dark:text-slate-400">
                  <li>Delete your account and identity</li>
                  <li>Remove all posts, comments, and interactions</li>
                  <li>Cannot be undone or recovered</li>
                </ul>
              </div>

              <div className="space-y-1.5 pt-1">
                <label className="text-xs font-bold text-slate-700 dark:text-slate-300">
                  Type{' '}
                  <span className="font-black text-rose-600 dark:text-rose-400">
                    DELETE
                  </span>{' '}
                  to confirm
                </label>
                <input
                  className="w-full bg-base-200 border border-black/10 dark:border-white/15 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-rose-500/40"
                  value={confirmText}
                  onChange={(e) => setConfirmText(e.target.value)}
                  placeholder="DELETE"
                  autoFocus
                />
              </div>

              <div className="flex gap-2.5 justify-end pt-2">
                <button
                  className="px-4 py-2 rounded-xl text-xs sm:text-sm font-bold text-slate-600 dark:text-slate-400 hover:bg-base-200 transition-colors cursor-pointer"
                  onClick={() => {
                    setShowDeactivate(false);
                    setConfirmText('');
                  }}
                  disabled={deactivating}
                >
                  Cancel
                </button>
                <button
                  className="bg-rose-600 hover:bg-rose-700 disabled:opacity-50 text-white font-bold text-xs sm:text-sm px-4 py-2 rounded-xl shadow-md shadow-rose-600/20 flex items-center gap-1.5 transition-all cursor-pointer"
                  onClick={deactivateAccount}
                  disabled={confirmText !== 'DELETE' || deactivating}
                >
                  {deactivating ? (
                    <>
                      <Loader2 size={14} className="animate-spin" /> Deleting…
                    </>
                  ) : (
                    <>
                      <Trash2 size={14} /> Delete Account
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>,
          document.body,
        )}

      {/* ═══════════════ SUCCESSFUL DELETION MODAL (PORTALED) ═══════════════ */}
      {typeof document !== 'undefined' &&
        showDeletedModal &&
        createPortal(
          <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-in fade-in duration-200">
            <div className="w-full max-w-sm rounded-2xl bg-base-100 border border-black/10 dark:border-white/15 p-6 text-center space-y-5 shadow-2xl">
              {/* Animated Icon */}
              <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-rose-500/10 text-rose-600 dark:text-rose-400 ring-8 ring-rose-500/5">
                <UserX size={30} className="animate-pulse" />
              </div>

              <div className="space-y-1.5">
                <h3 className="font-extrabold text-lg text-slate-900 dark:text-white">
                  Account Deleted
                </h3>
                <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed">
                  Your account and all associated data have been permanently
                  removed. We're sorry to see you go!
                </p>
              </div>

              {/* Auto-redirect progress indicator */}
              <div className="space-y-1.5">
                <div className="w-full bg-base-200 h-1.5 rounded-full overflow-hidden">
                  <div
                    className="bg-rose-500 h-full transition-all duration-1000 ease-linear"
                    style={{ width: `${(countdown / 5) * 100}%` }}
                  />
                </div>
                <p className="text-[11px] text-slate-400 font-medium">
                  Redirecting to login in {countdown}s...
                </p>
              </div>

              <button
                className="bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs sm:text-sm w-full py-2.5 rounded-xl shadow-md shadow-rose-600/20 transition-all cursor-pointer"
                onClick={handleConfirmDeleteRedirect}
              >
                Okay, Go to Login
              </button>
            </div>
          </div>,
          document.body,
        )}
    </div>
  );
};

export default Settings;
