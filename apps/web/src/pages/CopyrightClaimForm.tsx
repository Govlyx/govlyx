import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import {
  CheckCircle2,
  AlertTriangle,
  Loader2,
  Search,
  ArrowRight,
  Copy,
  Check,
} from 'lucide-react';
import PageNavbar from '../components/layout/PageNavbar';
import LandingBottomCtaAndFooter from '../components/landing/LandingBottomCtaAndFooter';
import axiosInstance from '../api/axiosConfig';
import { showToast } from '../utils/toast';

export default function CopyrightClaimForm() {
  const navigate = useNavigate();

  const [form, setForm] = useState({
    claimantName: '',
    claimantCompany: '',
    claimantEmail: '',
    claimantPhone: '',
    claimantAddress: '',
    infringingUrls: '',
    infringementDescription: '',
    originalWorkUrls: '',
    originalWorkDescription: '',
    originalWorkType: 'Text',
    goodFaithDeclaration: false,
    accuracyDeclaration: false,
    signature: '',
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [referenceId, setReferenceId] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const handleChange = (
    e: React.ChangeEvent<
      HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
    >,
  ) => {
    const { name, value, type } = e.target;
    if (type === 'checkbox') {
      const checked = (e.target as HTMLInputElement).checked;
      setForm((prev) => ({ ...prev, [name]: checked }));
    } else {
      setForm((prev) => ({ ...prev, [name]: value }));
    }
  };

  const handleCopyRef = () => {
    if (!referenceId) return;
    navigator.clipboard.writeText(referenceId);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
    showToast.success('Reference ID copied to clipboard');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // Validations
    if (
      !form.claimantName ||
      !form.claimantEmail ||
      !form.claimantAddress ||
      !form.infringingUrls ||
      !form.infringementDescription ||
      !form.originalWorkDescription ||
      !form.signature
    ) {
      setError('Please fill in all required fields and sign the declaration.');
      return;
    }

    if (!form.goodFaithDeclaration || !form.accuracyDeclaration) {
      setError('You must agree to both legal declarations.');
      return;
    }

    // Convert URLs comma/newline separated string to array
    const infringingUrlsArr = form.infringingUrls
      .split(/[\n,]/)
      .map((u) => u.trim())
      .filter((u) => u.length > 0);

    const originalWorkUrlsArr = form.originalWorkUrls
      .split(/[\n,]/)
      .map((u) => u.trim())
      .filter((u) => u.length > 0);

    if (infringingUrlsArr.length === 0) {
      setError('Please provide at least one valid infringing URL.');
      return;
    }

    setLoading(true);
    try {
      const response = await axiosInstance.post('/api/copyright-claims', {
        claimantName: form.claimantName,
        claimantCompany: form.claimantCompany || null,
        claimantEmail: form.claimantEmail,
        claimantPhone: form.claimantPhone || null,
        claimantAddress: form.claimantAddress,
        infringingUrls: infringingUrlsArr,
        infringementDescription: form.infringementDescription,
        originalWorkUrls: originalWorkUrlsArr,
        originalWorkDescription: form.originalWorkDescription,
        originalWorkType: form.originalWorkType,
        goodFaithDeclaration: form.goodFaithDeclaration,
        accuracyDeclaration: form.accuracyDeclaration,
        signature: form.signature,
      });

      const data = response.data?.data ?? response.data;
      if (data?.referenceId) {
        setReferenceId(data.referenceId);
        showToast.success('Copyright claim submitted successfully.');
      } else {
        throw new Error('Missing Reference ID in response.');
      }
    } catch (err: any) {
      console.error('Error submitting copyright claim:', err);
      if (err.response?.status === 429) {
        setError(
          'Too many requests. You have reached the maximum limit of 5 copyright claims per day from this IP address.',
        );
      } else {
        setError(
          err.response?.data?.message ||
            err.response?.data?.error ||
            err.message ||
            'Failed to submit copyright claim. Please try again.',
        );
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="h-screen bg-base-100 text-slate-800 dark:text-slate-200 selection:bg-blue-600/30 transition-colors duration-300 flex flex-col relative overflow-hidden">
      <Helmet>
        <title>DMCA & Copyright Infringement Portal | Govlyx</title>
        <meta
          name="description"
          content="Submit a formal copyright takedown notice or DMCA claim to Govlyx. Fast, compliant, and statutory grievance handling."
        />
        <link rel="canonical" href="https://govlyx.com/copyright-claim" />
      </Helmet>

      {/* ─── Navbar ──────────────────────────────────────────────────────────── */}
      <PageNavbar active="copyright" />

      {/* ─── Scrollable Container Wrapper ─── */}
      <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden flex flex-col justify-between z-10">
        <main className="w-full flex-1 py-6 sm:py-16 px-3.5 sm:px-6">
          {/* Header */}
          <div className="max-w-2xl mx-auto mb-6 sm:mb-12">
            <div className="text-left max-w-2xl">
              <div className="mb-2 sm:mb-4">
                <h1 className="text-2xl sm:text-5xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                  Copyright
                </h1>
              </div>
              <p className="text-slate-500 dark:text-slate-400 mt-2 sm:mt-4 text-xs sm:text-base leading-relaxed font-medium">
                Submit a formal copyright takedown notice or DMCA claim. Our
                Grievance Desk reviews and processes every verified request
                within 24 hours.
              </p>
              <div className="mt-3">
                <button
                  onClick={() => navigate('/copyright-claim/status')}
                  className="inline-flex items-center gap-1.5 text-xs font-bold text-[#1D4ED8] dark:text-blue-400 hover:underline cursor-pointer bg-transparent border-none p-0"
                >
                  <Search className="w-3.5 h-3.5" />
                  <span>Already filed a notice? Track claim status →</span>
                </button>
              </div>
            </div>
          </div>

          {/* Success Card */}
          {referenceId ? (
            <div className="max-w-lg mx-auto bg-base-200/80 dark:bg-base-200/60 border border-base-300 p-6 sm:p-10 rounded-3xl shadow-xl backdrop-blur-md text-center space-y-6">
              <div className="w-14 h-14 rounded-full bg-emerald-500/10 text-emerald-500 flex items-center justify-center mx-auto shadow-inner">
                <CheckCircle2 className="w-8 h-8" />
              </div>
              <div>
                <h3 className="text-xl sm:text-2xl font-extrabold text-slate-900 dark:text-white">
                  Claim Submitted Successfully
                </h3>
                <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed">
                  Your infringement notice has been logged. Our Grievance Desk
                  will review the reported content and respond within 24 hours.
                </p>
              </div>

              <div className="bg-base-100 border border-base-300 p-4 sm:p-5 rounded-2xl">
                <span className="text-[10px] sm:text-xs font-bold text-slate-400 uppercase tracking-wider block mb-1">
                  Your Reference ID
                </span>
                <div className="flex items-center justify-center gap-2">
                  <span className="text-lg sm:text-xl font-mono font-black text-[#1D4ED8] dark:text-blue-400 select-all">
                    {referenceId}
                  </span>
                  <button
                    type="button"
                    onClick={handleCopyRef}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-base-200 transition-colors cursor-pointer"
                    title="Copy Reference ID"
                  >
                    {copied ? (
                      <Check className="w-4 h-4 text-emerald-500" />
                    ) : (
                      <Copy className="w-4 h-4" />
                    )}
                  </button>
                </div>
              </div>

              <div className="flex flex-col sm:flex-row gap-3 justify-center pt-2">
                <button
                  onClick={() =>
                    navigate(
                      `/copyright-claim/status?ref=${referenceId}&email=${form.claimantEmail}`,
                    )
                  }
                  className="w-full sm:w-auto bg-[#1D4ED8] hover:bg-[#1e40af] text-white font-bold text-xs sm:text-sm px-6 py-3 rounded-xl shadow-md shadow-[#1D4ED8]/25 flex items-center justify-center gap-2 transition-all cursor-pointer"
                >
                  <Search className="w-4 h-4" />
                  <span>Track Claim Status</span>
                </button>
                <button
                  onClick={() => setReferenceId(null)}
                  className="w-full sm:w-auto bg-base-100 hover:bg-base-300 border border-base-300 text-slate-700 dark:text-slate-200 font-bold text-xs sm:text-sm px-5 py-3 rounded-xl transition-all cursor-pointer"
                >
                  <span>Submit Another</span>
                </button>
              </div>
            </div>
          ) : (
            /* Minimal Clean Form */
            <div className="max-w-2xl mx-auto bg-base-200/80 dark:bg-base-200/60 border border-base-300 p-5 sm:p-8 rounded-3xl shadow-xl backdrop-blur-md">
              <form onSubmit={handleSubmit} className="space-y-6">
                {error && (
                  <div className="p-3.5 bg-red-500/10 border border-red-500/20 rounded-2xl text-xs text-red-600 dark:text-red-400 font-bold flex items-start gap-2.5">
                    <AlertTriangle className="w-4 h-4 shrink-0 text-red-500 mt-0.5" />
                    <span>{error}</span>
                  </div>
                )}

                {/* Section 1: Claimant Details */}
                <div className="space-y-3.5">
                  <h2 className="text-xs sm:text-sm font-extrabold uppercase tracking-wider text-[#1D4ED8] dark:text-blue-400 pb-1 border-b border-base-300">
                    1. Claimant Contact Information
                  </h2>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Full Name <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="text"
                        name="claimantName"
                        value={form.claimantName}
                        onChange={handleChange}
                        placeholder="e.g. John Doe"
                        className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-medium text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all"
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Company / Organization (Optional)
                      </label>
                      <input
                        type="text"
                        name="claimantCompany"
                        value={form.claimantCompany}
                        onChange={handleChange}
                        placeholder="e.g. Acme Media Works"
                        className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-medium text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Email Address <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="email"
                        name="claimantEmail"
                        value={form.claimantEmail}
                        onChange={handleChange}
                        placeholder="e.g. legal@example.com"
                        className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-medium text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all"
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Phone Number (Optional)
                      </label>
                      <input
                        type="tel"
                        name="claimantPhone"
                        value={form.claimantPhone}
                        onChange={handleChange}
                        placeholder="e.g. +91 98765 43210"
                        className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-medium text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                      Full Postal Address{' '}
                      <span className="text-red-500">*</span>
                    </label>
                    <textarea
                      name="claimantAddress"
                      value={form.claimantAddress}
                      onChange={handleChange}
                      placeholder="Provide your complete legal postal address"
                      rows={2}
                      className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-medium text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all leading-relaxed"
                      required
                    />
                  </div>
                </div>

                {/* Section 2: Infringing URLs */}
                <div className="space-y-3.5">
                  <h2 className="text-xs sm:text-sm font-extrabold uppercase tracking-wider text-[#1D4ED8] dark:text-blue-400 pb-1 border-b border-base-300">
                    2. Infringing Content on Govlyx
                  </h2>

                  <div>
                    <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                      Infringing Post / Media URLs{' '}
                      <span className="text-red-500">*</span>
                    </label>
                    <textarea
                      name="infringingUrls"
                      value={form.infringingUrls}
                      onChange={handleChange}
                      placeholder="Paste infringing Govlyx URLs here (one URL per line)"
                      rows={3}
                      className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-mono text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all leading-relaxed"
                      required
                    />
                    <span className="block text-[10px] text-slate-400 mt-1">
                      Example: https://govlyx.com/post/12345
                    </span>
                  </div>

                  <div>
                    <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                      Description of Infringement{' '}
                      <span className="text-red-500">*</span>
                    </label>
                    <textarea
                      name="infringementDescription"
                      value={form.infringementDescription}
                      onChange={handleChange}
                      placeholder="Explain how this content violates your copyright"
                      rows={3}
                      className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-medium text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all leading-relaxed"
                      required
                    />
                  </div>
                </div>

                {/* Section 3: Original Work */}
                <div className="space-y-3.5">
                  <h2 className="text-xs sm:text-sm font-extrabold uppercase tracking-wider text-[#1D4ED8] dark:text-blue-400 pb-1 border-b border-base-300">
                    3. Original Copyrighted Work
                  </h2>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Content Type <span className="text-red-500">*</span>
                      </label>
                      <select
                        name="originalWorkType"
                        value={form.originalWorkType}
                        onChange={handleChange}
                        className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-bold text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all"
                      >
                        <option value="Text">
                          Text (Articles, Literary Works)
                        </option>
                        <option value="Audio">Audio (Songs, Podcasts)</option>
                        <option value="Video">Video (Clips, Movies)</option>
                        <option value="Image">Image (Photos, Graphics)</option>
                      </select>
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Original Source URLs (Optional)
                      </label>
                      <input
                        type="text"
                        name="originalWorkUrls"
                        value={form.originalWorkUrls}
                        onChange={handleChange}
                        placeholder="URLs to your original work"
                        className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-medium text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                      Original Work Description{' '}
                      <span className="text-red-500">*</span>
                    </label>
                    <textarea
                      name="originalWorkDescription"
                      value={form.originalWorkDescription}
                      onChange={handleChange}
                      placeholder="Provide registration details, title, or identification details of your work"
                      rows={3}
                      className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-medium text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all leading-relaxed"
                      required
                    />
                  </div>
                </div>

                {/* Section 4: Legal Declarations & Signature */}
                <div className="space-y-3.5">
                  <h2 className="text-xs sm:text-sm font-extrabold uppercase tracking-wider text-[#1D4ED8] dark:text-blue-400 pb-1 border-b border-base-300">
                    4. Legal Declarations
                  </h2>

                  <div className="space-y-2.5 bg-base-100 p-3.5 rounded-2xl border border-base-300">
                    <label className="flex items-start gap-2.5 cursor-pointer select-none">
                      <input
                        type="checkbox"
                        name="goodFaithDeclaration"
                        checked={form.goodFaithDeclaration}
                        onChange={handleChange}
                        className="mt-0.5 rounded text-[#1D4ED8] focus:ring-[#1D4ED8]"
                        required
                      />
                      <span className="text-[11px] sm:text-xs text-slate-600 dark:text-slate-300 leading-relaxed font-medium">
                        I declare that I have a good faith belief that the use
                        of the material in the manner complained of is not
                        authorized by the copyright owner, its agent, or the
                        law. <span className="text-red-500">*</span>
                      </span>
                    </label>

                    <label className="flex items-start gap-2.5 cursor-pointer select-none pt-2 border-t border-base-200">
                      <input
                        type="checkbox"
                        name="accuracyDeclaration"
                        checked={form.accuracyDeclaration}
                        onChange={handleChange}
                        className="mt-0.5 rounded text-[#1D4ED8] focus:ring-[#1D4ED8]"
                        required
                      />
                      <span className="text-[11px] sm:text-xs text-slate-600 dark:text-slate-300 leading-relaxed font-medium">
                        I declare that the information in this notification is
                        accurate, and under penalty of perjury, that I am
                        authorized to act on behalf of the owner of an exclusive
                        right that is allegedly infringed.{' '}
                        <span className="text-red-500">*</span>
                      </span>
                    </label>
                  </div>

                  <div>
                    <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                      Electronic Signature{' '}
                      <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="text"
                      name="signature"
                      value={form.signature}
                      onChange={handleChange}
                      placeholder="Type your full legal name as your signature"
                      className="w-full bg-base-100 border border-base-300 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm font-semibold italic text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-[#1D4ED8]/40 transition-all"
                      required
                    />
                  </div>
                </div>

                {/* Primary Submit */}
                <button
                  type="submit"
                  disabled={loading}
                  className="w-full bg-[#1D4ED8] hover:bg-[#1e40af] text-white font-bold text-xs sm:text-sm py-3.5 rounded-xl shadow-md shadow-[#1D4ED8]/25 flex items-center justify-center gap-2 transition-all cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed border border-[#1D4ED8] active:scale-[0.98]"
                >
                  {loading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Submitting Claim...</span>
                    </>
                  ) : (
                    <>
                      <span>Submit Copyright Takedown Claim</span>
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>
              </form>
            </div>
          )}
        </main>

        <LandingBottomCtaAndFooter />
      </div>
    </div>
  );
}
