import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import {
  Search,
  Loader2,
  AlertTriangle,
  Calendar,
  CheckCircle,
  Clock,
  ExternalLink,
  ShieldCheck,
} from 'lucide-react';
import PageNavbar from '../components/layout/PageNavbar';
import LandingBottomCtaAndFooter from '../components/landing/LandingBottomCtaAndFooter';
import axiosInstance from '../api/axiosConfig';

interface ClaimStatusData {
  id: number;
  referenceId: string;
  claimantName: string;
  claimantCompany: string | null;
  claimantEmail: string;
  status: 'PENDING' | 'ACKNOWLEDGED' | 'RESOLVED';
  originalWorkType: string;
  originalWorkDescription: string;
  infringingUrls: string[];
  createdAt: string;
  updatedAt: string;
}

export default function CopyrightClaimStatus() {
  const [searchParams, setSearchParams] = useSearchParams();

  const [refInput, setRefInput] = useState(searchParams.get('ref') || '');
  const [emailInput, setEmailInput] = useState(searchParams.get('email') || '');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [claim, setClaim] = useState<ClaimStatusData | null>(null);

  const fetchStatus = async (ref: string, email: string) => {
    if (!ref || !email) return;
    setLoading(true);
    setError(null);
    setClaim(null);

    try {
      const response = await axiosInstance.get(
        `/api/copyright-claims/status?ref=${encodeURIComponent(ref.trim())}&email=${encodeURIComponent(email.trim())}`,
      );
      const data = response.data?.data ?? response.data;
      if (data) {
        setClaim(data);
      } else {
        throw new Error('No claim data found.');
      }
    } catch (err: any) {
      console.error('Error fetching claim status:', err);
      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          err.message ||
          "We couldn't find any copyright claim matching this Reference ID and Email combination.",
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const ref = searchParams.get('ref');
    const email = searchParams.get('email');
    if (ref && email) {
      fetchStatus(ref, email);
    }
  }, [searchParams]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!refInput || !emailInput) {
      setError('Please fill in both the Reference ID and your email.');
      return;
    }
    setSearchParams({ ref: refInput.trim(), email: emailInput.trim() });
  };

  const getStatusBadge = (status: ClaimStatusData['status']) => {
    switch (status) {
      case 'PENDING':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-500/10 text-amber-500 border border-amber-500/20">
            <Clock className="w-3.5 h-3.5" /> Pending Review
          </span>
        );
      case 'ACKNOWLEDGED':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-blue-500/10 text-blue-500 border border-blue-500/20">
            <ShieldCheck className="w-3.5 h-3.5" /> Acknowledged (Reviewing)
          </span>
        );
      case 'RESOLVED':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-500/10 text-emerald-500 border border-emerald-500/20">
            <CheckCircle className="w-3.5 h-3.5" /> Resolved
          </span>
        );
      default:
        return null;
    }
  };

  return (
    <div className="h-screen bg-base-100 text-slate-800 dark:text-slate-200 selection:bg-blue-600/30 transition-colors duration-300 flex flex-col relative overflow-hidden">
      <Helmet>
        <title>Track Copyright Claim Status | Govlyx</title>
        <meta
          name="description"
          content="Track the current status and review logs of your submitted copyright takedown notice on Govlyx."
        />
        <link
          rel="canonical"
          href="https://govlyx.com/copyright-claim/status"
        />
      </Helmet>

      {/* ─── Navbar ──────────────────────────────────────────────────────────── */}
      <PageNavbar active="copyright" />

      {/* ─── Scrollable Container Wrapper ─── */}
      <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden flex flex-col justify-between z-10">
        <main className="w-full flex-1 py-10 sm:py-16 px-4 sm:px-6">
          {/* Header */}
          <div className="max-w-xl mx-auto mb-8 sm:mb-12">
            <div className="text-left max-w-xl">
              <div className="mb-2 sm:mb-4">
                <h1 className="text-2xl sm:text-5xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                  Claim Status
                </h1>
              </div>
              <p className="text-slate-500 dark:text-slate-400 mt-2 sm:mt-4 text-xs sm:text-base leading-relaxed font-medium">
                Enter your Reference ID and contact email to check real-time
                grievance review progress.
              </p>
            </div>
          </div>

          {/* Search Box */}
          <div className="max-w-xl mx-auto bg-base-200/80 dark:bg-base-200/60 border border-base-300 p-5 sm:p-6 rounded-3xl shadow-lg backdrop-blur-md mb-8">
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                    Reference ID
                  </label>
                  <input
                    type="text"
                    value={refInput}
                    onChange={(e) => setRefInput(e.target.value)}
                    placeholder="e.g. CLA-XXXXXXXX"
                    className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-mono text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all"
                    required
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                    Claimant Email
                  </label>
                  <input
                    type="email"
                    value={emailInput}
                    onChange={(e) => setEmailInput(e.target.value)}
                    placeholder="e.g. legal@example.com"
                    className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-medium text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all"
                    required
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full bg-[#1D4ED8] hover:bg-[#1e40af] text-white font-bold text-xs sm:text-sm py-3 rounded-xl shadow-md shadow-[#1D4ED8]/25 flex items-center justify-center gap-2 transition-all cursor-pointer disabled:opacity-60 border border-[#1D4ED8] active:scale-[0.98]"
              >
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Searching Claim...</span>
                  </>
                ) : (
                  <>
                    <Search className="w-4 h-4" />
                    <span>Check Claim Status</span>
                  </>
                )}
              </button>
            </form>
          </div>

          {error && (
            <div className="max-w-xl mx-auto mb-8 p-3.5 bg-red-500/10 border border-red-500/20 rounded-2xl text-xs text-red-600 dark:text-red-400 font-bold flex items-start gap-2.5">
              <AlertTriangle className="w-4 h-4 shrink-0 text-red-500 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {/* Claim Details View */}
          {claim && (
            <div className="max-w-2xl mx-auto bg-base-200/80 dark:bg-base-200/60 border border-base-300 p-5 sm:p-8 rounded-3xl shadow-xl backdrop-blur-md space-y-6">
              {/* Header */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-base-300 pb-4">
                <div>
                  <span className="text-[10px] sm:text-xs font-mono font-bold text-slate-400 uppercase block">
                    Reference: {claim.referenceId}
                  </span>
                  <h2 className="text-base sm:text-lg font-extrabold text-slate-900 dark:text-white">
                    Takedown Notice Detail
                  </h2>
                </div>
                <div className="self-start sm:self-center">
                  {getStatusBadge(claim.status)}
                </div>
              </div>

              {/* Timestamps */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 bg-base-100 border border-base-300 p-3.5 rounded-2xl text-xs">
                <div className="flex items-center gap-2">
                  <Calendar className="w-4 h-4 text-slate-400 shrink-0" />
                  <div>
                    <span className="text-slate-400 block text-[10px]">
                      Submitted At
                    </span>
                    <span className="font-semibold text-slate-700 dark:text-slate-200">
                      {new Date(claim.createdAt).toLocaleString()}
                    </span>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Calendar className="w-4 h-4 text-slate-400 shrink-0" />
                  <div>
                    <span className="text-slate-400 block text-[10px]">
                      Last Updated
                    </span>
                    <span className="font-semibold text-slate-700 dark:text-slate-200">
                      {new Date(claim.updatedAt).toLocaleString()}
                    </span>
                  </div>
                </div>
              </div>

              {/* Claimant Details */}
              <div className="space-y-1 bg-base-100 p-3.5 rounded-2xl border border-base-300 text-xs">
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">
                  Claimant
                </span>
                <p className="font-bold text-slate-900 dark:text-white">
                  {claim.claimantName}{' '}
                  {claim.claimantCompany ? `(${claim.claimantCompany})` : ''}
                </p>
                <p className="text-slate-500 font-medium">
                  {claim.claimantEmail}
                </p>
              </div>

              {/* Original Work */}
              <div className="space-y-1 bg-base-100 p-3.5 rounded-2xl border border-base-300 text-xs">
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">
                  Original Copyrighted Work
                </span>
                <span className="inline-block text-[10px] uppercase font-bold px-2 py-0.5 bg-[#1D4ED8]/10 text-[#1D4ED8] dark:text-blue-400 rounded-md mb-1">
                  {claim.originalWorkType} Content
                </span>
                <p className="text-slate-700 dark:text-slate-200 leading-relaxed font-medium">
                  {claim.originalWorkDescription}
                </p>
              </div>

              {/* Infringing URLs */}
              <div className="space-y-2">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-400 block">
                  Reported URLs
                </span>
                <div className="space-y-2">
                  {claim.infringingUrls.map((url, idx) => (
                    <a
                      key={idx}
                      href={url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center justify-between p-3 rounded-xl bg-base-100 border border-base-300 hover:border-[#1D4ED8]/60 text-xs font-mono text-[#1D4ED8] dark:text-blue-400 transition-colors"
                    >
                      <span className="truncate max-w-[85%]">{url}</span>
                      <ExternalLink className="w-3.5 h-3.5 shrink-0 text-slate-400" />
                    </a>
                  ))}
                </div>
              </div>
            </div>
          )}
        </main>

        <LandingBottomCtaAndFooter />
      </div>
    </div>
  );
}
