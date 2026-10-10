import { useNavigate } from "react-router-dom";
import PageNavbar from "../components/layout/PageNavbar";
import { BookOpen, Terminal, Shield, Network } from "lucide-react";

export default function Docs() {
  const navigate = useNavigate();

  return (
    <div className="h-screen bg-[#F8FAFC] dark:bg-[#0A0F1D] text-slate-800 dark:text-slate-200 selection:bg-[#1D4ED8]/20 transition-colors duration-300 flex flex-col overflow-hidden">
      {/* Navbar */}
      <PageNavbar />


      {/* Main Container */}
      <div className="flex-1 min-h-0 w-full max-w-[1200px] mx-auto px-4 grid grid-cols-1 lg:grid-cols-[280px_1fr] gap-10 overflow-hidden">
        
        {/* Sidebar Navigation */}
        <aside className="space-y-6 hidden lg:block py-12 overflow-y-auto pr-4 h-full shrink-0">
          <div>
            <h4 className="text-[10px] font-black tracking-wider uppercase text-slate-400 dark:text-slate-500 mb-3">GETTING STARTED</h4>
            <ul className="space-y-2 text-xs font-semibold text-slate-600 dark:text-slate-400">
              <li><a href="#overview" className="block hover:text-red-600 dark:hover:text-red-400 transition-colors py-1">Architecture Overview</a></li>
              <li><a href="#anonymity" className="block hover:text-red-600 dark:hover:text-red-400 transition-colors py-1">Anonymity & Privacy Layer</a></li>
              <li><a href="#escalation" className="block hover:text-red-600 dark:hover:text-red-400 transition-colors py-1">Escalation Algorithm</a></li>
            </ul>
          </div>
          <div>
            <h4 className="text-[10px] font-black tracking-wider uppercase text-slate-400 dark:text-slate-500 mb-3">API REFERENCE</h4>
            <ul className="space-y-2 text-xs font-semibold text-slate-600 dark:text-slate-400">
              <li><a href="#api-auth" className="block hover:text-red-600 dark:hover:text-red-400 transition-colors py-1">Auth Endpoints</a></li>
              <li><a href="#api-feed" className="block hover:text-red-600 dark:hover:text-red-400 transition-colors py-1">Pincode Feed API</a></li>
              <li><a href="#api-issues" className="block hover:text-red-600 dark:hover:text-red-400 transition-colors py-1">Civic Issues & Alerts</a></li>
            </ul>
          </div>
          <div>
            <h4 className="text-[10px] font-black tracking-wider uppercase text-slate-400 dark:text-slate-500 mb-3">SUSTAINABILITY</h4>
            <ul className="space-y-2 text-xs font-semibold text-slate-600 dark:text-slate-400">
              <li><a href="#sustainability" className="block hover:text-red-600 dark:hover:text-red-400 transition-colors py-1">SaaS & Revenue Model</a></li>
            </ul>
          </div>
        </aside>

        {/* Content Area & Footer (Scrollable Feed) */}
        <div className="h-full overflow-y-auto flex flex-col justify-between py-12 pr-1 min-h-0 scrollbar-thin">
          <main className="space-y-16">
            
            {/* Header */}
            <section className="border-b border-slate-200 dark:border-slate-800 pb-10">
              <div className="flex items-center gap-3 mb-4 text-[#1D4ED8] dark:text-[#60A5FA]">
                <BookOpen className="w-8 h-8" />
                <span className="font-extrabold text-xs tracking-wider uppercase bg-[#1D4ED8]/10 dark:bg-[#60A5FA]/10 px-3 py-1 rounded-full">Developer Documentation</span>
              </div>
              <h1 className="text-3xl sm:text-4xl font-extrabold text-slate-900 dark:text-white tracking-tight">
                Govlyx System Architecture
              </h1>
              <p className="text-slate-500 dark:text-slate-400 mt-2 text-sm sm:text-base leading-relaxed">
                Technical details, api endpoints, privacy implementation, and the automated grievance escalation system of Govlyx.
              </p>
            </section>

            {/* Section 1: Overview */}
            <section id="overview" className="space-y-4">
              <h2 className="text-xl sm:text-2xl font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                <Network className="w-5 h-5 text-[#1D4ED8] dark:text-[#60A5FA]" /> Architecture Overview
              </h2>
              <p className="text-slate-600 dark:text-slate-400 text-xs sm:text-sm leading-relaxed">
                Govlyx is architected using a decoupled client-server model designed for scaling. The platform partitions all feeds, messages, and alerts around a 6-digit municipal pincode.
              </p>
              <div className="bg-slate-50 dark:bg-[#121829]/60 backdrop-blur-md border border-slate-200 dark:border-slate-800/80 p-5 rounded-2xl">
                <h4 className="font-extrabold text-sm text-slate-900 dark:text-white mb-2">Stack Details:</h4>
                <ul className="list-disc pl-5 text-xs text-slate-500 dark:text-slate-400 space-y-1.5 font-medium">
                  <li><strong>Frontend:</strong> React + Vite + Tailwind CSS + Framer Motion</li>
                  <li><strong>Backend:</strong> Node.js / Express + TypeScript</li>
                  <li><strong>Database:</strong> PostgreSQL (Indexed by pincode mapping constraints)</li>
                  <li><strong>Authentication:</strong> JWT tokens with regional salt verification</li>
                </ul>
              </div>
            </section>

            {/* Section 2: Anonymity */}
            <section id="anonymity" className="space-y-4">
              <h2 className="text-xl sm:text-2xl font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                <Shield className="w-5 h-5 text-[#1D4ED8] dark:text-[#60A5FA]" /> Anonymity & Privacy Layer
              </h2>
              <p className="text-slate-600 dark:text-slate-400 text-xs sm:text-sm leading-relaxed">
                To encourage public reporting of municipal corruption and local failures, Govlyx never links user identities (real names, email accounts) directly to public feeds.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                <div className="p-4 bg-slate-50 dark:bg-[#121829]/60 backdrop-blur-md border border-slate-200 dark:border-slate-800/80 rounded-xl">
                  <h4 className="font-bold text-xs text-[#1D4ED8] dark:text-[#60A5FA] uppercase mb-1">Random User Mapping</h4>
                  <p className="text-slate-500 dark:text-slate-400 text-[11px] leading-relaxed">
                    Upon registration, each account is mapped dynamically to a randomly generated deterministic phrase (e.g., BoldLion4810).
                  </p>
                </div>
                <div className="p-4 bg-slate-50 dark:bg-[#121829]/60 backdrop-blur-md border border-slate-200 dark:border-slate-800/80 rounded-xl">
                  <h4 className="font-bold text-xs text-[#1D4ED8] dark:text-[#60A5FA] uppercase mb-1">No GPS Tracking</h4>
                  <p className="text-slate-500 dark:text-slate-400 text-[11px] leading-relaxed">
                    Govlyx operates exclusively on the manually-entered 6-digit pincode area. No coordinates or device paths are logged.
                  </p>
                </div>
              </div>
            </section>

            {/* Section 3: Escalation Algorithm */}
            <section id="escalation" className="space-y-4">
              <h2 className="text-xl sm:text-2xl font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                <Terminal className="w-5 h-5 text-[#1D4ED8]" /> Grievance Escalation Algorithm
              </h2>
              <p className="text-slate-600 dark:text-slate-400 text-xs sm:text-sm leading-relaxed">
                Govlyx automatically triggers escalation hooks as posts receive community interaction bumps:
              </p>
              <div className="space-y-3 font-semibold text-xs">
                <div className="flex items-center gap-3 p-3 bg-white dark:bg-[#121829] border border-slate-200 dark:border-slate-850 rounded-xl">
                  <span className="w-8 h-8 rounded-full bg-[#1D4ED8]/10 text-[#1D4ED8] flex items-center justify-center text-xs shrink-0">1</span>
                  <div>
                    <h4 className="text-slate-900 dark:text-white text-xs font-bold">Local Ward Level (0 - 50 bumps)</h4>
                    <p className="text-slate-400 text-[11px] font-normal font-medium mt-0.5">Post is visible only to users inside the matching 6-digit pincode.</p>
                  </div>
                </div>
                <div className="flex items-center gap-3 p-3 bg-white dark:bg-[#121829] border border-slate-200 dark:border-slate-850 rounded-xl">
                  <span className="w-8 h-8 rounded-full bg-[#1D4ED8]/10 text-[#1D4ED8] flex items-center justify-center text-xs shrink-0">2</span>
                  <div>
                    <h4 className="text-slate-900 dark:text-white text-xs font-bold">District Level (50 - 250 bumps)</h4>
                    <p className="text-slate-400 text-[11px] font-normal font-medium mt-0.5">App expands the visibility bubble to the entire district, inviting wider support.</p>
                  </div>
                </div>
                <div className="flex items-center gap-3 p-3 bg-white dark:bg-[#121829] border border-slate-200 dark:border-slate-850 rounded-xl">
                  <span className="w-8 h-8 rounded-full bg-[#1D4ED8]/10 text-[#1D4ED8] flex items-center justify-center text-xs shrink-0">3</span>
                  <div>
                    <h4 className="text-slate-900 dark:text-white text-xs font-bold">State Level (250+ bumps)</h4>
                    <p className="text-slate-400 text-[11px] font-normal font-medium mt-0.5">Post goes viral across the state. Automated grievance reports are queued for state portal sync.</p>
                  </div>
                </div>
              </div>
            </section>

            {/* Section 4: API References */}
            <section id="api" className="space-y-6">
              <h2 className="text-xl sm:text-2xl font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                <Terminal className="w-5 h-5 text-[#1D4ED8]" /> Core API Specification
              </h2>

              {/* API Endpoint 1 */}
              <div id="api-auth" className="border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden">
                <div className="bg-slate-50 dark:bg-[#121829] px-4 py-3 border-b border-slate-200 dark:border-slate-800 flex items-center gap-3">
                  <span className="bg-emerald-500/10 text-emerald-500 font-bold text-[10px] uppercase px-2 py-0.5 rounded">GET</span>
                  <code className="text-xs font-mono font-bold text-slate-700 dark:text-slate-300">/api/posts/feed</code>
                </div>
                <div className="p-4 space-y-2 text-xs">
                  <p className="text-slate-500 dark:text-slate-400 font-medium">Retrieves active posts matching a target pincode feed.</p>
                  <h5 className="font-bold text-[10px] tracking-wider uppercase text-slate-400">Parameters:</h5>
                  <code className="block bg-slate-100 dark:bg-[#0B0F1A] p-2.5 rounded text-[11px] text-slate-600 dark:text-slate-400 font-mono">
                    ?pincode=411001&limit=20
                  </code>
                </div>
              </div>

              {/* API Endpoint 2 */}
              <div id="api-feed" className="border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden">
                <div className="bg-slate-50 dark:bg-[#121829] px-4 py-3 border-b border-slate-200 dark:border-slate-800 flex items-center gap-3">
                  <span className="bg-blue-600/10 text-blue-500 font-bold text-[10px] uppercase px-2 py-0.5 rounded">POST</span>
                  <code className="text-xs font-mono font-bold text-slate-700 dark:text-slate-300">/api/posts/create</code>
                </div>
                <div className="p-4 space-y-2 text-xs">
                  <p className="text-slate-500 dark:text-slate-400 font-medium">Creates a new hyperlocal thread with media payload.</p>
                  <h5 className="font-bold text-[10px] tracking-wider uppercase text-slate-400">Payload:</h5>
                  <pre className="bg-slate-100 dark:bg-[#0B0F1A] p-3 rounded text-[11px] text-slate-600 dark:text-slate-400 font-mono overflow-x-auto">
  {`{
    "content": "Pothole on Main Road",
    "pincode": "411001",
    "departmentTag": "Sanitation",
    "mediaUrl": "https://govlyx.s3/post.jpg"
  }`}
                  </pre>
                </div>
              </div>

            </section>

            {/* Section 5: Sustainability & Revenue Architecture */}
            <section id="sustainability" className="space-y-4">
              <h2 className="text-xl sm:text-2xl font-extrabold text-slate-900 dark:text-white flex items-center gap-2">
                <Network className="w-5 h-5 text-[#1D4ED8] dark:text-[#60A5FA]" /> Sustainability & SaaS Model
              </h2>
              <p className="text-slate-600 dark:text-slate-400 text-xs sm:text-sm leading-relaxed">
                Core Rule: Every citizen uses Govlyx for free. Long-term operations are funded via specialized B2G and B2B SaaS features:
              </p>
              
              <div className="space-y-4 pt-2">
                <div className="bg-slate-50 dark:bg-[#121829]/60 backdrop-blur-md border border-slate-200 dark:border-slate-800/80 p-5 rounded-2xl">
                  <div className="flex justify-between items-center mb-2">
                    <span className="font-black text-xs text-[#1D4ED8] dark:text-[#60A5FA] uppercase tracking-wider">Phase 1 · Month 6</span>
                    <span className="bg-blue-500/10 text-[#60A5FA] text-[10px] font-bold px-2 py-0.5 rounded">Govt SaaS</span>
                  </div>
                  <h4 className="font-extrabold text-sm text-slate-900 dark:text-white">Government Portal Subscriptions</h4>
                  <p className="text-slate-500 dark:text-slate-400 text-xs mt-1 leading-relaxed">
                    Departments pay a monthly subscription to get verified broadcast dashboards, regional jurisdiction analytics, and automated push notification channels.
                  </p>
                </div>

                <div className="bg-slate-50 dark:bg-[#121829]/60 backdrop-blur-md border border-slate-200 dark:border-slate-800/80 p-5 rounded-2xl">
                  <div className="flex justify-between items-center mb-2">
                    <span className="font-black text-xs text-[#1D4ED8] dark:text-[#60A5FA] uppercase tracking-wider">Phase 1 · Month 9</span>
                    <span className="bg-emerald-500/10 text-emerald-500 text-[10px] font-bold px-2 py-0.5 rounded">Communities</span>
                  </div>
                  <h4 className="font-extrabold text-sm text-slate-900 dark:text-white">Premium Communities</h4>
                  <p className="text-slate-500 dark:text-slate-400 text-xs mt-1 leading-relaxed">
                    Advanced management features for RWAs, schools, and private local groups including customized dashboard controls, custom styling, and private member analytics.
                  </p>
                </div>

                <div className="bg-slate-50 dark:bg-[#121829]/60 backdrop-blur-md border border-slate-200 dark:border-slate-800/80 p-5 rounded-2xl">
                  <div className="flex justify-between items-center mb-2">
                    <span className="font-black text-xs text-[#1D4ED8] dark:text-[#60A5FA] uppercase tracking-wider">Phase 2 · Month 15</span>
                    <span className="bg-amber-500/10 text-amber-500 text-[10px] font-bold px-2 py-0.5 rounded">Ad Engine</span>
                  </div>
                  <h4 className="font-extrabold text-sm text-slate-900 dark:text-white">Hyperlocal Advertising</h4>
                  <p className="text-slate-500 dark:text-slate-400 text-xs mt-1 leading-relaxed">
                    Allows local shops, service hubs, and schools to advertise contextually to citizens strictly in their specific municipal pincode.
                  </p>
                </div>
              </div>
            </section>

          </main>

          {/* Footer */}
          <footer className="mt-16 border-t border-slate-200 dark:border-slate-800/80 py-10 transition-colors duration-300">
            <div className="flex flex-col md:flex-row justify-between items-center gap-6 text-center md:text-left">
              <div className="flex items-center gap-3">
                <svg
                  width={26}
                  height={26}
                  viewBox="0 0 512 540"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  className="shrink-0"
                >
                  <path
                    fill="#1D4ED8"
                    d="M256 32L96 112v120c0 112 64 208 160 248c96-40 160-136 160-248V112L256 32z"
                  />
                  <g fill="#FFFFFF" transform="translate(0, -6)">
                    <path d="M256 150c-40 0-72 32-72 72v20h144v-20c0-40-32-72-72-72z"/>
                    <rect x="220" y="242" width="72" height="16"/>
                    <rect x="204" y="220" width="12" height="40"/>
                    <rect x="296" y="220" width="12" height="40"/>
                  </g>
                  <g fill="#FFFFFF" transform="translate(0, -6)">
                    <circle cx="170" cy="210" r="6"/>
                    <circle cx="196" cy="230" r="4"/>
                    <circle cx="342" cy="210" r="6"/>
                    <circle cx="318" cy="230" r="4"/>
                    <circle cx="256" cy="190" r="5"/>
                  </g>
                  <path fill="#FFFFFF" d="M150 300h212l-8 16H158z"/>
                  <g fill="#FFFFFF">
                    <rect x="248" y="300" width="16" height="120"/>
                    <rect x="198" y="300" width="16" height="80"/>
                    <rect x="298" y="300" width="16" height="80"/>
                  </g>
                  <g fill="#FFFFFF">
                    <circle cx="256" cy="440" r="18"/>
                    <circle cx="206" cy="380" r="20"/>
                    <circle cx="306" cy="380" r="20"/>
                  </g>
                  <g>
                    <rect x="252" y="118" width="8" height="32" fill="#FFFFFF"/>
                    <path d="M260 118h45v22l-45-8z" fill="#FFFFFF"/>
                    <path d="M260 118l35 16l-35-6z" fill="#FFFFFF" opacity="0.4"/>
                  </g>
                </svg>
                <span className="font-extrabold text-slate-900 dark:text-white text-lg notranslate">Govlyx</span>
              </div>
              <div className="flex flex-wrap justify-center gap-4 sm:gap-6 text-sm text-slate-500 dark:text-slate-400 font-semibold">
                <button onClick={() => navigate("/")} className="hover:text-red-600 dark:hover:text-red-400 transition-colors bg-transparent border-none p-0 cursor-pointer font-semibold">Home</button>
                <button onClick={() => navigate("/upcoming-updates")} className="hover:text-red-600 dark:hover:text-red-400 transition-colors bg-transparent border-none p-0 cursor-pointer font-semibold">Upcoming Updates</button>
                <button onClick={() => navigate("/docs")} className="hover:text-red-600 dark:hover:text-red-400 transition-colors bg-transparent border-none p-0 cursor-pointer font-semibold text-[#1D4ED8]">Docs</button>
              </div>
              <p className="text-[11px] sm:text-xs text-slate-400 dark:text-slate-500 font-medium">
                © 2026&nbsp;<span className="notranslate" translate="no">Govlyx</span> · Hyperlocal Civic Infrastructure
              </p>
            </div>
          </footer>

        </div>
      </div>
    </div>
  );
}
