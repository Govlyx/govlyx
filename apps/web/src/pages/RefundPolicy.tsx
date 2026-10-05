import {
  Scale,
  RotateCcw,
  AlertCircle,
  Calendar,
  ShieldAlert,
} from 'lucide-react';
import PageNavbar from '../components/layout/PageNavbar';
import LandingBottomCtaAndFooter from '../components/landing/LandingBottomCtaAndFooter';

export default function RefundPolicy() {
  return (
    <div className="h-screen bg-base-100 text-slate-800 dark:text-slate-200 selection:bg-blue-600/30 transition-colors duration-300 flex flex-col relative overflow-hidden">
      {/* ─── Navbar ──────────────────────────────────────────────────────────── */}
      <PageNavbar active="policy" />

      <div className="flex-1 min-h-0 overflow-y-auto flex flex-col justify-between z-10">
        <main className="w-full mx-auto max-w-[850px] px-6 py-16 relative">
          <div className="text-center mb-12">
            <div className="w-16 h-16 rounded-2xl bg-blue-600/10 text-blue-605 dark:text-blue-400 flex items-center justify-center mx-auto mb-6 shadow-lg shadow-blue-500/5 border border-blue-500/15">
              <RotateCcw className="w-8 h-8" />
            </div>
            <h1 className="text-3xl sm:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
              Refund & Cancellation Policy
            </h1>
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-405 flex items-center justify-center gap-1.5 font-bold uppercase tracking-wider">
              <Calendar className="w-4 h-4" /> Last Updated: June 29, 2026
            </p>
          </div>

          <div className="space-y-10 text-[13px] sm:text-sm md:text-base leading-relaxed text-slate-650 dark:text-slate-350">
            <p className="text-center max-w-xl mx-auto font-medium">
              Thank you for choosing Govlyx. We strive to provide a premium
              experience for all our users. Please read our Refund and
              Cancellation Policy carefully before purchasing any of our digital
              passes (e.g., Pro, VIP).
            </p>

            <hr className="border-slate-200/65 dark:border-slate-800/65" />

            <section className="space-y-3">
              <h2 className="text-sm sm:text-base md:text-lg lg:text-xl font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                <Scale className="w-5 h-5 text-blue-550 dark:text-blue-400 shrink-0" />{' '}
                1. Cancellations
              </h2>
              <ul className="list-disc pl-5 space-y-2 text-slate-600 dark:text-slate-400">
                <li>
                  <strong>Subscription Cancellations:</strong> You may cancel
                  your subscription renewal at any time through your account
                  settings or by contacting our support team.
                </li>
                <li>
                  <strong>Effect of Cancellation:</strong> If you cancel your
                  subscription, your current pass will remain active until the
                  end of your current billing cycle (e.g., the end of the month
                  you paid for). We do not prorate or cancel active passes
                  prematurely.
                </li>
                <li>
                  <strong>Auto-Renewal:</strong> If your pass is set to
                  auto-renew, you must cancel at least 24 hours before the next
                  billing date to avoid being charged for the subsequent month.
                </li>
              </ul>
            </section>

            <section className="space-y-3">
              <h2 className="text-sm sm:text-base md:text-lg lg:text-xl font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                <ShieldAlert className="w-5 h-5 text-blue-550 dark:text-blue-400 shrink-0" />{' '}
                2. Refunds for Digital Services
              </h2>
              <p>
                Due to the digital and instantaneous nature of our platform's
                services (instant access to premium features, private community
                quotas, etc.),{' '}
                <strong>all sales are final and non-refundable</strong>, except
                under the following circumstances:
              </p>
              <ul className="list-disc pl-5 space-y-2 text-slate-600 dark:text-slate-400">
                <li>
                  <strong>Duplicate Billing:</strong> If you are accidentally
                  charged twice for the same transaction due to a technical
                  error, we will refund the duplicate amount within 5-7 business
                  days.
                </li>
                <li>
                  <strong>Service Unavailability:</strong> If our core premium
                  services are entirely unavailable due to a platform outage for
                  more than 48 continuous hours, you may be eligible for a
                  partial credit or refund at our sole discretion.
                </li>
              </ul>

              <div className="bg-red-500/5 border border-red-500/10 p-5 rounded-2xl space-y-2 mt-4">
                <p className="font-bold text-red-650 dark:text-red-400 flex items-center gap-1.5 text-xs sm:text-sm">
                  <AlertCircle className="w-4 h-4 shrink-0" /> We do NOT issue
                  refunds for:
                </p>
                <ul className="list-disc pl-5 space-y-1.5 text-xs sm:text-sm text-slate-600 dark:text-slate-400">
                  <li>Change of mind after purchasing a pass.</li>
                  <li>
                    Inability to use the platform due to issues on your own
                    device or internet connection.
                  </li>
                  <li>
                    Unused private community quotas or features at the end of
                    your billing cycle.
                  </li>
                  <li>
                    Account bans or suspensions resulting from violations of our
                    Terms of Service or Community Guidelines.
                  </li>
                </ul>
              </div>
            </section>

            <section className="space-y-3">
              <h2 className="text-sm sm:text-base md:text-lg lg:text-xl font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                <RotateCcw className="w-5 h-5 text-blue-550 dark:text-blue-400 shrink-0" />{' '}
                3. Processing Refunds
              </h2>
              <p>
                If a refund is approved (e.g., in the case of duplicate
                billing):
              </p>
              <ul className="list-disc pl-5 space-y-2 text-slate-600 dark:text-slate-400">
                <li>
                  The refund will be processed back to your original method of
                  payment (Credit Card, UPI, Netbanking).
                </li>
                <li>
                  It typically takes <strong>5 to 7 business days</strong> for
                  the credited amount to reflect in your bank account, depending
                  on your bank's processing times.
                </li>
              </ul>
            </section>

            <section className="space-y-3">
              <h2 className="text-sm sm:text-base md:text-lg lg:text-xl font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                <AlertCircle className="w-5 h-5 text-blue-550 dark:text-blue-400 shrink-0" />{' '}
                4. Contact Us
              </h2>
              <p>
                If you experience a billing issue or believe you were charged in
                error, please contact our support team within 7 days of the
                transaction.
              </p>
              <div className="bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-5 rounded-2xl text-[11px] sm:text-xs md:text-sm font-semibold space-y-2 mt-2">
                <p>
                  <span className="opacity-60 text-slate-500 dark:text-slate-405">
                    Email:
                  </span>{' '}
                  <a
                    href="mailto:govlyxsupport@gmail.com"
                    className="text-blue-600 dark:text-blue-400 hover:underline"
                  >
                    govlyxsupport@gmail.com
                  </a>
                </p>
                <p>
                  <span className="opacity-60 text-slate-500 dark:text-slate-405">
                    Address:
                  </span>{' '}
                  Pune, India
                </p>
              </div>
            </section>

            <hr className="border-slate-200/60 dark:border-slate-800/60" />

            <p className="text-[11px] sm:text-xs text-slate-500 text-center font-bold italic">
              By purchasing or using any premium pass or subscription on Govlyx,
              you agree to this Refund & Cancellation Policy.
            </p>
          </div>
        </main>

        <LandingBottomCtaAndFooter />
      </div>
    </div>
  );
}
