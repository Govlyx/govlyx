import { useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { 
  MapPin, 
  Scale, 
  Users, 
  MessageSquare, 
  Image, 
  Trash2, 
  Mail, 
  AlertTriangle
} from "lucide-react";
import PageNavbar from "../components/layout/PageNavbar";
import LandingBottomCtaAndFooter from "../components/landing/LandingBottomCtaAndFooter";

export default function PrivacyPolicy() {
  const navigate = useNavigate();

  return (
    <div className="h-screen bg-base-100 text-slate-800 dark:text-slate-200 selection:bg-blue-600/30 transition-colors duration-300 flex flex-col relative overflow-hidden">
      <Helmet>
        <title>Privacy Policy | Govlyx India - Anonymous & Civic Platform</title>
        <meta name="description" content="Read the Govlyx Privacy Policy. Learn how we safeguard your identity, handle geolocation data, and protect your privacy on our platform." />
        <link rel="canonical" href="https://govlyx.com/privacy-policy" />
        <meta property="og:type" content="website" />
        <meta property="og:url" content="https://govlyx.com/privacy-policy" />
        <meta property="og:site_name" content="Govlyx" />
        <meta property="og:title" content="Privacy Policy | Govlyx India" />
        <meta property="og:description" content="Read the Govlyx Privacy Policy. Learn how we safeguard your identity and privacy." />
        <meta property="og:image" content="https://govlyx.com/govlyx-og.png" />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content="Privacy Policy | Govlyx India" />
        <meta name="twitter:description" content="Read the Govlyx Privacy Policy. Learn how we safeguard your identity and privacy." />
        <meta name="twitter:image" content="https://govlyx.com/govlyx-og.png" />
      </Helmet>

      {/* ─── Navbar ──────────────────────────────────────────────────────────── */}
      <PageNavbar active="policy" />


      {/* ─── Scrollable Container Wrapper ─── */}
      <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden flex flex-col justify-between z-10">
        <main className="w-full flex-1 py-6 sm:py-16 px-3.5 sm:px-6 lg:px-12">
          
          {/* Header */}
          <div className="max-w-[1300px] mx-auto mb-6 sm:mb-12">
            <div className="text-left max-w-3xl">
              <div className="mb-2 sm:mb-4">
                <h1 className="text-2xl sm:text-5xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                  Privacy Policy
                </h1>
              </div>
              <p className="text-slate-500 dark:text-slate-400 mt-2 sm:mt-4 text-xs sm:text-base leading-relaxed font-medium">
                Learn how we safeguard your identity, handle geolocation data, and protect citizen privacy across all 19,000+ Indian pincodes.
              </p>
              <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] sm:text-xs font-semibold text-slate-500 dark:text-slate-400">
                <span><strong>Effective Date:</strong> June 2026</span>
                <span className="opacity-40">•</span>
                <span><strong>Jurisdiction:</strong> Republic of India</span>
              </div>
            </div>
          </div>

          {/* ─── Two-Page Book Spread Container ─── */}
          <div className="max-w-[1300px] mx-auto">
            <div className="bg-base-200/80 dark:bg-base-200/60 border border-base-300 rounded-2xl sm:rounded-3xl shadow-xl overflow-hidden backdrop-blur-md">
              
              {/* Book Spread Grid: 2 Columns on lg, 1 Column on smaller screens */}
              <div className="grid grid-cols-1 lg:grid-cols-2 divide-y lg:divide-y-0 lg:divide-x divide-base-300">
                
                {/* ─── LEFT PAGE (Page 1) ─── */}
                <div className="p-4 sm:p-10 lg:p-12 space-y-4 sm:space-y-6 flex flex-col justify-between relative">
                  <div>
                    <div className="flex items-center justify-between border-b border-base-300/70 pb-3 mb-6 text-[10px] sm:text-xs font-black uppercase tracking-widest text-slate-400 dark:text-slate-500">
                      <span>Govlyx Privacy Charter</span>
                      <span>Page 01</span>
                    </div>

                    <p className="font-semibold text-slate-900 dark:text-white text-xs sm:text-sm leading-relaxed mb-5">
                      Welcome to Govlyx. This platform is here to help you connect with your neighbourhood and local government. By using Govlyx, you agree to how we collect and use your data as described below.
                    </p>

                    <div className="p-3.5 sm:p-4 bg-red-500/10 border border-red-500/20 rounded-2xl text-xs text-red-650 dark:text-red-400 font-bold flex items-start gap-2.5 mb-5">
                      <AlertTriangle className="w-4 h-4 shrink-0 text-red-500 mt-0.5" />
                      <span className="leading-relaxed">
                        <strong>18+ Warning:</strong> You must be at least 18 years old to access and use Govlyx. Registration and access to all platform services are restricted to adult users only.
                      </span>
                    </div>

                    <div className="p-3.5 sm:p-4 bg-blue-500/10 border border-blue-500/20 rounded-2xl text-xs text-slate-650 dark:text-slate-300 font-medium mb-6 leading-relaxed">
                      Govlyx acts as an Intermediary under Indian IT laws. We follow the official rules to keep the platform safe and secure for all citizens.
                    </div>

                    <div className="space-y-6 text-xs sm:text-sm text-slate-650 dark:text-slate-300 leading-relaxed">
                      
                      {/* Section 1 */}
                      <section className="space-y-2">
                        <h2 className="text-sm sm:text-base font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                          <MapPin className="w-4 h-4 text-[#1D4ED8] dark:text-blue-400 shrink-0" />
                          <span>1. Location & Pincode</span>
                        </h2>
                        <p>We use your pincode to show local updates.</p>
                        <ul className="list-disc pl-5 space-y-1 text-slate-500 dark:text-slate-400">
                          <li><strong>No GPS Tracking:</strong> We do not track your real-time location.</li>
                          <li><strong>Information Only:</strong> Govlyx is not an emergency service. We cannot guarantee notice accuracy.</li>
                        </ul>
                      </section>

                      {/* Section 2 */}
                      <section className="space-y-2">
                        <h2 className="text-sm sm:text-base font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                          <Scale className="w-4 h-4 text-[#1D4ED8] dark:text-blue-400 shrink-0" />
                          <span>2. Anonymous Chat & Law Enforcement (CRITICAL)</span>
                        </h2>
                        <p>Anonymous chat hides your name from other users, not from the law.</p>
                        <ul className="list-disc pl-5 space-y-1 text-slate-500 dark:text-slate-400">
                          <li><strong>Logs Saved:</strong> We save encrypted logs linking your chat to your account profile, IP, and device.</li>
                          <li><strong>Zero Abuse:</strong> No illegal chats, hate speech, or harassment allowed.</li>
                          <li><strong>Legal Action:</strong> We will share your chat logs and identity with the police if legally ordered.</li>
                        </ul>
                      </section>

                      {/* Section 3 */}
                      <section className="space-y-2">
                        <h2 className="text-sm sm:text-base font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                          <Users className="w-4 h-4 text-[#1D4ED8] dark:text-blue-400 shrink-0" />
                          <span>3. Communities & What You Share</span>
                        </h2>
                        <p>You can post in public and private groups.</p>
                        <ul className="list-disc pl-5 space-y-1 text-slate-500 dark:text-slate-400">
                          <li><strong>You are Responsible:</strong> You are legally responsible for what you post. We do not edit your posts.</li>
                          <li><strong>Content Removal:</strong> If reported or ordered by the government, we will remove illegal posts within 36 hours.</li>
                        </ul>
                      </section>

                      {/* Section 4 */}
                      <section className="space-y-2">
                        <h2 className="text-sm sm:text-base font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                          <MessageSquare className="w-4 h-4 text-[#1D4ED8] dark:text-blue-400 shrink-0" />
                          <span>4. Government Communication</span>
                        </h2>
                        <p>We help you report issues to local departments.</p>
                        <ul className="list-disc pl-5 space-y-1 text-slate-500 dark:text-slate-400">
                          <li><strong>Shared Stats:</strong> We share basic statistics (like total complaints in a pincode) with local bodies.</li>
                          <li><strong>Public Reports:</strong> Direct messages or public reports will be visible to the government. We cannot force them to act.</li>
                        </ul>
                      </section>

                    </div>
                  </div>

                  <div className="pt-4 border-t border-base-300/50 flex justify-between items-center text-[10px] font-bold text-slate-400">
                    <span>Govlyx Intermediary Guidelines</span>
                    <span>1 / 2</span>
                  </div>
                </div>

                {/* ─── RIGHT PAGE (Page 2) ─── */}
                <div className="p-6 sm:p-10 lg:p-12 space-y-6 flex flex-col justify-between bg-base-100/40 dark:bg-base-100/20 relative">
                  <div>
                    <div className="flex items-center justify-between border-b border-base-300/70 pb-3 mb-6 text-[10px] sm:text-xs font-black uppercase tracking-widest text-slate-400 dark:text-slate-500">
                      <span>Legal & Compliance</span>
                      <span>Page 02</span>
                    </div>

                    <div className="space-y-6 text-xs sm:text-sm text-slate-650 dark:text-slate-300 leading-relaxed">
                      
                      {/* Section 5 */}
                      <section className="space-y-2">
                        <h2 className="text-sm sm:text-base font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                          <Image className="w-4 h-4 text-[#1D4ED8] dark:text-blue-400 shrink-0" />
                          <span>5. Uploading Photos & Media</span>
                        </h2>
                        <ul className="list-disc pl-5 space-y-1 text-slate-500 dark:text-slate-400">
                          <li><strong>Privacy:</strong> We try to clean location data from uploaded photos. Please check your camera settings too.</li>
                          <li><strong>Banned Files:</strong> Uploading fake, illegal, or copyrighted media will ban your account.</li>
                        </ul>
                      </section>

                      {/* Section 6 */}
                      <section className="space-y-2">
                        <h2 className="text-sm sm:text-base font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                          <Trash2 className="w-4 h-4 text-[#1D4ED8] dark:text-blue-400 shrink-0" />
                          <span>6. Deleting Your Data</span>
                        </h2>
                        <p>We process data in compliance with the Digital Personal Data Protection (DPDP) Act.</p>
                        <ul className="list-disc pl-5 space-y-1 text-slate-500 dark:text-slate-400">
                          <li><strong>Easy Deletion:</strong> Delete your account in Settings. Profile data is cleared in 30 days.</li>
                          <li><strong>Police Exception:</strong> If your account is flagged for abuse or police investigation, we must keep your data by law.</li>
                        </ul>
                      </section>

                      {/* Section 7 */}
                      <section className="space-y-2">
                        <h2 className="text-sm sm:text-base font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                          <AlertTriangle className="w-4 h-4 text-[#1D4ED8] dark:text-blue-400 shrink-0" />
                          <span>7. Copyright & 3-Strike Policy</span>
                        </h2>
                        <p>Govlyx respects intellectual property rights. Users must not upload, post, or share content that infringes copyrights:</p>
                        <ul className="list-disc pl-5 space-y-1 text-slate-500 dark:text-slate-400">
                          <li>
                            <strong>Takedown Notices:</strong> Copyright owners can submit formal infringement claims through our dedicated{" "}
                            <button onClick={() => navigate("/copyright-claim")} className="text-[#1D4ED8] dark:text-blue-400 hover:underline font-bold bg-transparent border-none p-0 cursor-pointer">
                              Copyright Infringement Portal
                            </button>.
                          </li>
                          <li>
                            <strong>3-Strike Suspension:</strong> Accumulating 3 verified copyright strikes will result in the permanent suspension of your Govlyx account and removal of all contributions.
                          </li>
                        </ul>
                      </section>

                      {/* Section 8 */}
                      <section className="space-y-2.5">
                        <h2 className="text-sm sm:text-base font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                          <Mail className="w-4 h-4 text-[#1D4ED8] dark:text-blue-400 shrink-0" />
                          <span>8. Grievance Support Desk</span>
                        </h2>
                        <p>If you have questions, copyright notices, or want to report illegal content, please contact us:</p>
                        
                        <div className="bg-base-100 border border-base-300 p-4 rounded-2xl text-xs font-semibold space-y-2 mt-2">
                          <p><span className="text-slate-400">Email:</span> <a href="mailto:govlyxsupport@gmail.com" className="text-[#1D4ED8] dark:text-blue-400 hover:underline">govlyxsupport@gmail.com</a></p>
                          <p><span className="text-slate-400">DMCA Portal:</span> <button onClick={() => navigate("/copyright-claim")} className="text-[#1D4ED8] dark:text-blue-400 hover:underline font-bold bg-transparent border-none p-0 cursor-pointer">File Copyright Claim</button></p>
                          <p><span className="text-slate-400">Response:</span> Acknowledged within 24 hours</p>
                          <p><span className="text-slate-400">Resolution:</span> Resolved within 15 days</p>
                        </div>
                      </section>

                    </div>

                    <div className="mt-6 pt-5 border-t border-base-300/70">
                      <p className="text-[11px] text-slate-500 dark:text-slate-400 text-center font-bold italic leading-relaxed">
                        By creating an account, you confirm you are 18 years of age or older and agree to this Privacy Policy under the laws of the Republic of India.
                      </p>
                    </div>
                  </div>

                  <div className="pt-4 border-t border-base-300/50 flex justify-between items-center text-[10px] font-bold text-slate-400">
                    <span>Digital Personal Data Protection (DPDP)</span>
                    <span>2 / 2</span>
                  </div>
                </div>

              </div>

            </div>
          </div>

        </main>

        <LandingBottomCtaAndFooter />
      </div>
    </div>
  );
}
