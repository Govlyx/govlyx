import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import { jwtDecode } from 'jwt-decode';
import { getAuthToken, clearAuthTokens } from '../utils/auth';
import GovlyxLogo from '../components/ui/GovlyxLogo';
import PageNavbar from '../components/layout/PageNavbar';
import LandingBottomCtaAndFooter from '../components/landing/LandingBottomCtaAndFooter';
import {
  ArrowRight,
  Menu,
  MessageCircle,
  Plus,
  Search,
  Heart,
  MessageSquare,
  Share2,
  Bookmark,
  SlidersHorizontal,
  Flag,
  Zap,
  Lock,
  Settings as SettingsIcon,
  Wifi,
  Battery,
  Users,
  Eye,
  TrafficCone,
  Droplet,
  Bot,
  MapPin,
  Landmark,
  Handshake,
  AlertTriangle,
  BarChart3,
  UserX,
} from 'lucide-react';
import QuickChatReviewsCarousel from '../components/landing/QuickChatReviewsCarousel';

const isLoggedIn = (): boolean => {
  const token = getAuthToken();
  if (!token) return false;

  try {
    const decoded = jwtDecode<{ exp: number }>(token);
    const isExpired = decoded.exp * 1000 < Date.now();
    if (isExpired) {
      clearAuthTokens();
      return false;
    }
    return true;
  } catch {
    return false;
  }
};

export default function LandingPage() {
  const navigate = useNavigate();

  const words = React.useMemo(() => {
    const textWords = ['Neighborhood', '&', 'Govt'];
    let globalIndex = 0;
    return textWords.map((word) => {
      const chars = Array.from(word).map((char) => ({
        char,
        idx: globalIndex++,
      }));
      globalIndex++;
      return chars;
    });
  }, []);

  // ─── Interactive Phone Mockup Post States ──────────────────────────────────
  const [posts, setPosts] = useState([
    {
      id: 'post-1',
      author: 'ZENYETI8480',
      avatarInitials: 'ZY',
      avatarUrl: 'https://i.pravatar.cc/100?img=11',
      avatarBg: 'bg-slate-800',
      time: '1 HOUR AGO',
      content:
        'Major pothole on MG Road near the main junction finally got patched up today! Traffic is flowing smoothly now. Thanks to everyone who bumped the issue last week. 🙌',
      media:
        'https://images.unsplash.com/photo-1515162816999-a0c47dc192f7?w=400&q=80',
      reactions: 412,
      comments: 88,
      shares: 14,
      liked: false,
      bookmarked: false,
      commented: false,
      shared: false,
      flagged: false,
    },
    {
      id: 'post-2',
      author: 'PUNECIVIC404',
      avatarInitials: 'PC',
      avatarUrl: 'https://i.pravatar.cc/100?img=5',
      avatarBg: 'bg-indigo-600',
      time: '5 HOURS AGO',
      content:
        "Does anyone know if the Sunday farmer's market is still happening at Shivaji Park this weekend despite the rain forecast?",
      media: null,
      reactions: 156,
      comments: 34,
      shares: 2,
      liked: false,
      bookmarked: false,
      commented: false,
      shared: false,
      flagged: false,
    },
    {
      id: 'post-3',
      author: 'KINDCROW9071',
      avatarInitials: 'KC',
      avatarUrl: 'https://i.pravatar.cc/100?img=47',
      avatarBg: 'bg-[#eff4ff] text-[#1D4ED8]',
      time: '1 DAY AGO',
      content:
        "Streetlights have been out on 4th Cross Street for three days straight. It's completely pitch dark at night and unsafe for pedestrians. @CityCouncil please look into this urgently! 🔦⚠️",
      media:
        'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=400&q=80',
      reactions: 892,
      comments: 134,
      shares: 45,
      liked: false,
      bookmarked: false,
      commented: false,
      shared: false,
      flagged: false,
    },
  ]);

  const handleLikePost = (postId: string) => {
    setPosts((prevPosts) =>
      prevPosts.map((post) => {
        if (post.id === postId) {
          const isLikedNow = !post.liked;
          return {
            ...post,
            liked: isLikedNow,
            reactions: isLikedNow ? post.reactions + 1 : post.reactions - 1,
          };
        }
        return post;
      }),
    );
  };

  const handleBookmarkPost = (postId: string) => {
    setPosts((prevPosts) =>
      prevPosts.map((post) => {
        if (post.id === postId) {
          return {
            ...post,
            bookmarked: !post.bookmarked,
          };
        }
        return post;
      }),
    );
  };

  const handleCommentPost = (postId: string) => {
    setPosts((prevPosts) =>
      prevPosts.map((post) => {
        if (post.id === postId) {
          const isCommentedNow = !post.commented;
          return {
            ...post,
            commented: isCommentedNow,
            comments: isCommentedNow ? post.comments + 1 : post.comments - 1,
          };
        }
        return post;
      }),
    );
  };

  const handleSharePost = (postId: string) => {
    setPosts((prevPosts) =>
      prevPosts.map((post) => {
        if (post.id === postId) {
          const isSharedNow = !post.shared;
          return {
            ...post,
            shared: isSharedNow,
            shares: isSharedNow ? post.shares + 1 : post.shares - 1,
          };
        }
        return post;
      }),
    );
  };

  const handleFlagPost = (postId: string) => {
    setPosts((prevPosts) =>
      prevPosts.map((post) => {
        if (post.id === postId) {
          return {
            ...post,
            flagged: !post.flagged,
          };
        }
        return post;
      }),
    );
  };

  useEffect(() => {
    // Enable scrollable behavior by adding a CSS class helper on Mount
    document.documentElement.classList.add('scrollable-page');
    return () => {
      // Restore scroll lock on Unmount
      document.documentElement.classList.remove('scrollable-page');
    };
  }, []);

  const handleEnterPlatform = (e: React.MouseEvent) => {
    e.preventDefault();
    if (isLoggedIn()) {
      navigate('/dashboard');
    } else {
      navigate('/login');
    }
  };

  return (
    <div className="h-screen bg-base-100 text-base-content selection:bg-[#1D4ED8]/20 selection:text-[#1e3a8a] dark:selection:text-white transition-colors duration-300 flex flex-col relative overflow-hidden">
      <Helmet>
        <title>
          Govlyx - Report Civic Issues Online & Connect Locally in India
        </title>
        <meta
          name="description"
          content="Report civic issues like potholes and garbage collection online. Connect with your neighbors on Govlyx, the hyperlocal community network and MCD 311 / Nextdoor India alternative."
        />
        <meta
          name="keywords"
          content="neighborhood app india, report civic issues online, local municipal complaint platform, MCD 311 alternative, Nextdoor alternative india, connect with neighbors online, Govlyx, Govlyx India, omegle alternative for local neighborhood, 1v1 anonymous chat in my area India, anonymous chat with nearby people, civic engagement platform India, neighborhood government app India, smart city citizen portal India, talk to people in my area anonymously, 1vs1 random chat app for Indian neighborhoods, citizen grievance redressal platform"
        />
        <link rel="canonical" href="https://govlyx.com" />
        <meta property="og:type" content="website" />
        <meta property="og:url" content="https://govlyx.com" />
        <meta property="og:site_name" content="Govlyx" />
        <meta
          property="og:title"
          content="Govlyx - Report Civic Issues Online & Connect Locally in India"
        />
        <meta
          property="og:description"
          content="Report civic issues like potholes and garbage collection online. Connect with your neighbors on Govlyx, the hyperlocal community network."
        />
        <meta property="og:image" content="https://govlyx.com/govlyx-og.png" />
        <meta property="og:image:width" content="1200" />
        <meta property="og:image:height" content="630" />
        <meta name="twitter:card" content="summary_large_image" />
        <meta
          name="twitter:title"
          content="Govlyx - Report Civic Issues Online & Connect Locally in India"
        />
        <meta
          name="twitter:description"
          content="Report civic issues like potholes and garbage collection online. Connect with your neighbors on Govlyx."
        />
        <meta name="twitter:image" content="https://govlyx.com/govlyx-og.png" />
        <script type="application/ld+json">
          {JSON.stringify({
            '@context': 'https://schema.org',
            '@type': 'WebSite',
            name: 'Govlyx',
            url: 'https://govlyx.com',
            description:
              'Hyperlocal civic grievance redressal and anonymous neighborhood engagement platform India.',
            potentialAction: {
              '@type': 'SearchAction',
              target: 'https://govlyx.com/search?q={search_term_string}',
              'query-input': 'required name=search_term_string',
            },
          })}
        </script>
      </Helmet>

      {/* ─── Navbar ──────────────────────────────────────────────────────────── */}
      <PageNavbar active="home" />

      {/* ─── Scrollable Container Wrapper ─── */}
      <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden flex flex-col justify-between z-10">
        <main className="w-full flex-1">
          {/* ─── Hero Section ────────────────────────────────────────────────────── */}
          <section className="relative z-20 pt-10 pb-8 sm:pt-4 sm:pb-8 lg:pt-5 lg:pb-10 px-3 xs:px-4 sm:px-6 lg:px-24 xl:px-32 min-h-[85vh] sm:min-h-[70vh] lg:min-h-0 flex items-center bg-transparent">
            {/* Background Decor */}
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
              <div className="absolute inset-0 bg-grid-pattern opacity-40 dark:opacity-30 pointer-events-none"></div>
              <div className="absolute top-0 right-0 w-[240px] sm:w-[400px] lg:w-[600px] h-[240px] sm:h-[400px] lg:h-[600px] bg-blue-50 dark:bg-[#1D4ED8]/10 rounded-full mix-blend-multiply filter blur-[50px] sm:blur-[70px] lg:blur-[100px] opacity-70 dark:opacity-40 translate-x-1/3 -translate-y-1/4 pointer-events-none"></div>
              <div className="absolute bottom-0 left-0 w-[200px] sm:w-[300px] lg:w-[500px] h-[200px] sm:h-[300px] lg:h-[500px] bg-blue-50 dark:bg-[#1D4ED8]/10 rounded-full mix-blend-multiply filter blur-[50px] sm:blur-[70px] lg:blur-[100px] opacity-70 dark:opacity-40 -translate-x-1/4 translate-y-1/4 pointer-events-none"></div>
            </div>

            <div className="max-w-[1400px] mx-auto w-full grid grid-cols-1 lg:grid-cols-[1.15fr_0.85fr] gap-8 lg:gap-8 items-center relative z-10">
              {/* Left Column: Title & Enter Platform Action */}
              <div className="max-w-2xl mx-auto lg:mx-0 w-full text-center lg:text-left order-1 lg:order-1 mt-0 lg:pl-6 xl:pl-10 flex flex-col items-center lg:items-start min-h-[78vh] sm:min-h-0 justify-between sm:justify-start">
                <div className="flex flex-col items-center lg:items-start w-full my-auto sm:my-0">
                  <h1 className="text-5xl xs:text-6xl sm:text-6xl md:text-7xl lg:text-[3.6rem] xl:text-[4.2rem] font-black tracking-tight text-slate-900 dark:text-white leading-[1.03] sm:leading-[1.06] mb-4 sm:mb-5 flex flex-col items-center lg:items-start select-none w-full">
                    <span className="block whitespace-normal sm:whitespace-nowrap">
                      Connecting Every Indian
                    </span>
                    <span className="block whitespace-nowrap text-slate-800 dark:text-slate-100">
                      to Their
                    </span>
                    <span className="relative inline-flex flex-wrap sm:flex-nowrap justify-center lg:justify-start items-center gap-x-[0.2em] sm:gap-x-[0.25em] cursor-default select-none mt-1">
                      {/* Word 0: "Neighborhood" with snug highlighter */}
                      {words[0] && (
                        <span className="inline-block whitespace-nowrap bg-[#1D4ED8] text-white px-1.5 py-0.5 rounded-md sm:rounded-lg shadow-xs leading-none">
                          {words[0].map(({ char, idx }) => (
                            <span key={idx} style={{ display: 'inline-block' }}>
                              {char}
                            </span>
                          ))}
                        </span>
                      )}

                      {/* Words 1 & 2: "&" and "Govt" each in their own separate span */}
                      <span className="inline-flex flex-nowrap items-center gap-x-[0.2em] sm:gap-x-[0.25em] basis-full sm:basis-auto justify-center lg:justify-start mt-1 sm:mt-0">
                        {/* Word 1: "&" separate span */}
                        {words[1] && (
                          <span className="inline-block whitespace-nowrap bg-[#1D4ED8] text-white px-1.5 py-0.5 rounded-md sm:rounded-lg shadow-xs leading-none">
                            {words[1].map(({ char, idx }) => (
                              <span
                                key={idx}
                                style={{ display: 'inline-block' }}
                              >
                                {char}
                              </span>
                            ))}
                          </span>
                        )}

                        {/* Word 2: "Govt" separate span */}
                        {words[2] && (
                          <span className="inline-block whitespace-nowrap bg-[#1D4ED8] text-white px-1.5 py-0.5 rounded-md sm:rounded-lg shadow-xs leading-none">
                            {words[2].map(({ char, idx }) => (
                              <span
                                key={idx}
                                style={{ display: 'inline-block' }}
                              >
                                {char}
                              </span>
                            ))}
                          </span>
                        )}
                      </span>
                    </span>
                  </h1>

                  <p className="text-xs xs:text-sm sm:text-base text-slate-600 dark:text-slate-300 leading-relaxed mb-6 sm:mb-7 max-w-lg mx-auto lg:mx-0 font-normal">
                    Govlyx is a neighborhood government app India trust. A
                    student-built platform to give every citizen a voice. Safe,
                    anonymous, and centered around your 6-digit pincode, it
                    provides a civic engagement platform India needs, as well as
                    an anonymous chat with nearby people.
                  </p>
                </div>

                {/* Enter Platform Button with 18+ Badge (Lower on Mobile Screen) */}
                <div className="relative inline-block w-auto mx-auto lg:mx-0 z-30 mt-4 sm:mt-0 mb-4 sm:mb-0">
                  {/* Tilted 18+ Badge on Top-Right Corner */}
                  <div className="absolute -top-2 -right-2 z-40 bg-red-600 border border-red-400 text-white font-black text-[9px] sm:text-[10px] px-2 py-0.5 rounded-full shadow-md shadow-red-500/35 rotate-12 select-none pointer-events-none flex items-center justify-center">
                    18+
                  </div>

                  <button
                    onClick={handleEnterPlatform}
                    className="bg-[#1D4ED8] hover:bg-[#1e40af] text-white font-bold rounded-xl py-3 px-7 sm:py-3 sm:px-6 text-sm sm:text-sm transition-all duration-200 cursor-pointer shadow-lg shadow-[#1D4ED8]/30 hover:shadow-xl hover:shadow-[#1D4ED8]/40 flex justify-center items-center gap-2 active:scale-[0.98] border border-[#1D4ED8] group"
                  >
                    <span>Enter Platform</span>
                    <ArrowRight className="w-3.5 h-3.5 sm:w-4 sm:h-4 transition-transform duration-200 group-hover:translate-x-1" />
                  </button>
                </div>
              </div>

              {/* Right Column: Scaled-Down Realistic CSS Mobile Phone Mockup */}
              <div className="relative flex justify-center items-center order-2 lg:order-2 mt-4 sm:mt-6 lg:mt-0 w-full">
                {/* Decorative Blur behind phone */}
                <div className="absolute w-[240px] xs:w-[290px] sm:w-[420px] h-[260px] xs:h-[320px] sm:h-[500px] bg-[#1D4ED8]/25 dark:bg-[#1D4ED8]/15 rounded-full blur-[50px] sm:blur-[70px] animate-pulse pointer-events-none"></div>

                {/* 3D perspective tilt wrapper */}
                <div
                  style={{ perspective: '1200px' }}
                  className="w-full flex justify-center"
                >
                  <div
                    style={{
                      transform: 'rotateY(-16deg) rotateX(4deg)',
                      transformStyle: 'preserve-3d',
                      transition: 'transform 0.3s ease',
                    }}
                  >
                    {/* Phone Body - Increased width for better proportions */}
                    <div
                      className="relative w-[230px] xs:w-[260px] sm:w-[310px] md:w-[340px] h-[360px] xs:h-[410px] sm:h-[490px] md:h-[550px] bg-slate-900 dark:bg-slate-950 rounded-[24px] xs:rounded-[30px] sm:rounded-[44px] border-[6px] xs:border-[8px] sm:border-[12px] border-slate-900 dark:border-base-300 overflow-hidden animate-float flex flex-col select-none transition-colors"
                      style={{
                        boxShadow:
                          '-15px 20px 45px rgba(0,0,0,0.4), -6px 8px 18px rgba(0,0,0,0.2), 2px -4px 12px rgba(255,255,255,0.04)',
                      }}
                    >
                      {/* 3D Right edge depth panel */}
                      <div
                        style={{
                          position: 'absolute',
                          right: '-10px',
                          top: '10px',
                          bottom: '10px',
                          width: '10px',
                          background:
                            'linear-gradient(to right, #1a1a2e, #0f0f1a)',
                          borderRadius: '0 4px 4px 0',
                          transform: 'rotateY(90deg)',
                          transformOrigin: 'left center',
                          pointerEvents: 'none',
                        }}
                      />

                      {/* 3D Bottom edge depth panel */}
                      <div
                        style={{
                          position: 'absolute',
                          bottom: '-10px',
                          left: '10px',
                          right: '10px',
                          height: '10px',
                          background:
                            'linear-gradient(to bottom, #1a1a2e, #0d0d1a)',
                          borderRadius: '0 0 4px 4px',
                          transform: 'rotateX(-90deg)',
                          transformOrigin: 'top center',
                          pointerEvents: 'none',
                        }}
                      />

                      {/* Gloss/shine overlay */}
                      <div
                        style={{
                          position: 'absolute',
                          inset: 0,
                          background:
                            'linear-gradient(135deg, rgba(255,255,255,0.07) 0%, transparent 50%)',
                          pointerEvents: 'none',
                          zIndex: 50,
                          borderRadius: '24px',
                        }}
                      />

                      {/* Top Status Bar Mock */}
                      <div className="h-4 sm:h-5 md:h-6 w-full bg-white dark:bg-[#0A0F1D] flex justify-between items-center px-3 sm:px-5 md:px-6 text-[8px] sm:text-[9px] md:text-[10px] font-bold text-slate-800 dark:text-slate-300 z-20 transition-colors shrink-0">
                        <span>9:41</span>
                        <div className="flex items-center gap-1 sm:gap-1.5">
                          <Wifi className="w-2.5 h-2.5 sm:w-3 sm:h-3 text-slate-800 dark:text-slate-300" />
                          <Battery className="w-2.5 h-2.5 sm:w-3 sm:h-3 md:w-3.5 md:h-3.5 text-slate-800 dark:text-slate-300" />
                        </div>
                      </div>

                      {/* Dynamic Island / Camera Notch */}
                      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-14 sm:w-20 md:w-24 h-3.5 sm:h-5 md:h-6 bg-slate-900 dark:bg-base-300 rounded-b-xl sm:rounded-b-2xl z-30 transition-colors"></div>

                      {/* App Header */}
                      <div className="bg-white dark:bg-[#121829] px-2.5 sm:px-3.5 md:px-4 py-1.5 sm:py-2.5 md:py-3 flex items-center justify-between border-b border-slate-100 dark:border-base-300/80 z-10 shrink-0 transition-colors">
                        <div className="flex items-center gap-1.5 sm:gap-2">
                          <Menu className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-slate-700 dark:text-slate-300" />
                          <GovlyxLogo size={18} />
                        </div>
                        <div className="flex items-center gap-2 sm:gap-3">
                          <Search className="w-3 h-3 sm:w-3.5 sm:h-3.5 md:w-4 md:h-4 text-slate-500 dark:text-slate-400" />
                          <div className="bg-[#1D4ED8] text-white text-[7.5px] sm:text-[8px] md:text-[9px] font-bold px-1.5 sm:px-2 py-0.5 rounded flex items-center gap-0.5">
                            <Plus className="w-2 h-2 sm:w-2.5 sm:h-2.5" />{' '}
                            Create
                          </div>
                        </div>
                      </div>

                      {/* App Content Area (Feed) */}
                      <div className="flex-1 bg-[#F8FAFC] dark:bg-[#0B0F1A] overflow-y-auto scrollbar-hide p-1.5 sm:p-2.5 md:p-3 flex flex-col gap-1.5 sm:gap-2.5 md:gap-3 pb-6 sm:pb-8 relative transition-colors">
                        {/* Floating Pill */}
                        <div className="sticky top-0 z-10 flex justify-center mb-0.5 sm:mb-1">
                          <div className="bg-white/90 dark:bg-[#121829]/95 backdrop-blur border border-slate-200 dark:border-base-300/80 text-[8px] sm:text-[9px] md:text-[10px] font-bold text-slate-600 dark:text-slate-300 px-2.5 sm:px-3.5 py-0.5 sm:py-1 rounded-full shadow-sm flex items-center gap-1 sm:gap-1.5 transition-colors">
                            <SlidersHorizontal className="w-2.5 h-2.5 sm:w-3 sm:h-3" />{' '}
                            Explore Feed
                          </div>
                        </div>

                        {/* Posts mapping */}
                        {posts.map((post) => (
                          <div
                            key={post.id}
                            className="bg-white dark:bg-[#121829] border border-slate-200 dark:border-base-300/80 rounded-xl sm:rounded-2xl p-2 sm:p-3 shadow-xs transition-all hover:shadow-md"
                          >
                            <div className="flex items-start justify-between mb-1.5 sm:mb-2">
                              <div className="flex items-center gap-1.5 sm:gap-2">
                                <img
                                  src={post.avatarUrl}
                                  alt={post.author}
                                  className="w-6 h-6 sm:w-7 sm:h-7 md:w-8 md:h-8 rounded-full object-cover shrink-0 shadow ring-1.5 ring-slate-100 dark:ring-slate-700"
                                  onError={(e) => {
                                    (
                                      e.target as HTMLImageElement
                                    ).style.display = 'none';
                                  }}
                                />
                                <div className="leading-tight">
                                  <div className="text-[8.5px] sm:text-[9.5px] md:text-[10px] font-bold text-slate-900 dark:text-white">
                                    {post.author}
                                  </div>
                                  <div className="text-[6.5px] sm:text-[7.5px] md:text-[8px] font-semibold text-slate-400 dark:text-slate-500 uppercase">
                                    {post.time}
                                  </div>
                                </div>
                              </div>
                              <button
                                onClick={() => handleFlagPost(post.id)}
                                className={`transition-colors p-0.5 rounded cursor-pointer ${
                                  post.flagged
                                    ? 'text-red-500 dark:text-red-400'
                                    : 'text-slate-300 dark:text-slate-600 hover:text-red-500 dark:hover:text-red-400'
                                }`}
                              >
                                <Flag
                                  className={`w-2.5 h-2.5 sm:w-3 sm:h-3 ${post.flagged ? 'fill-red-500 dark:fill-red-400' : ''}`}
                                />
                              </button>
                            </div>

                            <p className="text-[8.5px] sm:text-[9.5px] md:text-[11px] text-slate-700 dark:text-slate-300 mb-1.5 sm:mb-2 ml-0.5 leading-snug sm:leading-relaxed text-left">
                              {post.content}
                            </p>

                            {/* Media Image */}
                            {post.media && (
                              <div className="mb-1.5 sm:mb-2 rounded-lg sm:rounded-xl overflow-hidden border border-slate-100 dark:border-base-300">
                                <img
                                  src={post.media}
                                  alt="post media"
                                  className="w-full h-[70px] xs:h-[85px] sm:h-[100px] md:h-[110px] object-cover"
                                  loading="lazy"
                                />
                              </div>
                            )}

                            {/* Interaction Buttons (Active and incrementable!) */}
                            <div className="flex items-center gap-2.5 sm:gap-3.5 md:gap-4 text-[7.5px] sm:text-[8.5px] md:text-[9px] font-bold text-slate-500 dark:text-slate-400 border-t border-slate-50 dark:border-base-300/60 pt-1.5 sm:pt-2 ml-0.5">
                              {/* Heart (Like) button */}
                              <button
                                onClick={() => handleLikePost(post.id)}
                                className={`flex items-center gap-1 transition-all duration-200 cursor-pointer p-0.5 rounded hover:bg-slate-50 dark:hover:bg-slate-800/40 ${
                                  post.liked
                                    ? 'text-pink-600 dark:text-pink-500'
                                    : 'hover:text-pink-600 dark:hover:text-pink-400'
                                }`}
                              >
                                <Heart
                                  className={`w-2.5 h-2.5 sm:w-3 sm:h-3 md:w-3.5 md:h-3.5 ${post.liked ? 'fill-pink-600 dark:fill-pink-500' : ''}`}
                                />
                                <span>{post.reactions}</span>
                              </button>

                              {/* Comments count */}
                              <button
                                onClick={() => handleCommentPost(post.id)}
                                className={`flex items-center gap-1 transition-all duration-200 cursor-pointer p-0.5 rounded hover:bg-slate-50 dark:hover:bg-slate-800/40 ${
                                  post.commented
                                    ? 'text-blue-600 dark:text-blue-400'
                                    : 'hover:text-blue-600 dark:hover:text-blue-400'
                                }`}
                              >
                                <MessageSquare
                                  className={`w-2.5 h-2.5 sm:w-3 sm:h-3 md:w-3.5 md:h-3.5 ${post.commented ? 'fill-blue-600 dark:fill-blue-400' : ''}`}
                                />
                                <span>{post.comments}</span>
                              </button>

                              {/* Shares count */}
                              <button
                                onClick={() => handleSharePost(post.id)}
                                className={`flex items-center gap-1 transition-all duration-200 cursor-pointer p-0.5 rounded hover:bg-slate-50 dark:hover:bg-slate-800/40 ${
                                  post.shared
                                    ? 'text-emerald-600 dark:text-emerald-500'
                                    : 'hover:text-emerald-600 dark:hover:text-emerald-400'
                                }`}
                              >
                                <Share2
                                  className={`w-2.5 h-2.5 sm:w-3 sm:h-3 md:w-3.5 md:h-3.5 ${post.shared ? 'fill-emerald-600 dark:fill-emerald-500' : ''}`}
                                />
                                <span>{post.shares}</span>
                              </button>

                              {/* Bookmark button */}
                              <button
                                onClick={() => handleBookmarkPost(post.id)}
                                className={`ml-auto transition-colors duration-200 cursor-pointer p-0.5 rounded hover:bg-slate-50 dark:hover:bg-slate-800/40 ${
                                  post.bookmarked
                                    ? 'text-amber-500 dark:text-amber-400'
                                    : 'text-slate-400 dark:text-slate-500 hover:text-amber-500 dark:hover:text-amber-400'
                                }`}
                                aria-label="Bookmark"
                              >
                                <Bookmark
                                  className={`w-2.5 h-2.5 sm:w-3 sm:h-3 md:w-3.5 md:h-3.5 ${post.bookmarked ? 'fill-amber-500 dark:fill-amber-400' : ''}`}
                                />
                              </button>
                            </div>
                          </div>
                        ))}
                      </div>
                      {/* end feed */}
                    </div>
                    {/* end phone body */}
                  </div>
                  {/* end tilt div */}
                </div>
                {/* end perspective div */}
              </div>
              {/* end column */}
            </div>
          </section>

          {/* ─── Ticker Section ──────────────────────────────────────────────────── */}
          <div className="bg-slate-50 dark:bg-[#121829] border-y border-slate-200/80 dark:border-base-300 py-2 sm:py-2.5 overflow-hidden relative flex items-center transition-colors">
            <div className="absolute left-0 top-0 bottom-0 z-20 bg-[#1D4ED8] dark:bg-[#1e40af] px-2.5 sm:px-4 flex items-center font-bold text-[8px] sm:text-[10px] text-white tracking-widest uppercase shadow-md">
              <span className="w-1.5 h-1.5 rounded-full bg-white mr-1.5 animate-pulse"></span>{' '}
              LIVE
            </div>
            <div className="flex whitespace-nowrap animate-marquee pl-[75px] sm:pl-[120px] space-x-6 sm:space-x-12 text-[11px] sm:text-sm font-semibold text-slate-600 dark:text-slate-400">
              <span className="inline-flex items-center gap-1.5">
                Street light fixed after 200+ reactions{' '}
                <span className="text-[#1D4ED8] dark:text-blue-400 inline-flex items-center gap-0.5">
                  <MapPin className="w-3.5 h-3.5" /> Kolhapur
                </span>
              </span>
              <span className="inline-flex items-center gap-1.5">
                Free health camp this Saturday{' '}
                <span className="text-[#1D4ED8] dark:text-blue-400 inline-flex items-center gap-0.5">
                  <MapPin className="w-3.5 h-3.5" /> Sangli
                </span>
              </span>
              <span className="inline-flex items-center gap-1.5">
                New bus route approved by MSRTC{' '}
                <span className="text-[#1D4ED8] dark:text-blue-400 inline-flex items-center gap-0.5">
                  <MapPin className="w-3.5 h-3.5" /> Nashik
                </span>
              </span>
              <span className="inline-flex items-center gap-1.5">
                Pothole on SH-10 repaired!{' '}
                <span className="text-[#1D4ED8] dark:text-blue-400 inline-flex items-center gap-0.5">
                  <MapPin className="w-3.5 h-3.5" /> Solapur
                </span>
              </span>
              <span className="inline-flex items-center gap-1.5">
                Power cut Sunday 9AM–1PM{' '}
                <span className="text-[#1D4ED8] dark:text-blue-400 inline-flex items-center gap-0.5">
                  <MapPin className="w-3.5 h-3.5" /> Chh Sambhajinagar
                </span>
              </span>
              {/* Duplicated for seamless loop */}
              <span className="inline-flex items-center gap-1.5">
                Street light fixed after 200+ reactions{' '}
                <span className="text-[#1D4ED8] dark:text-blue-400 inline-flex items-center gap-0.5">
                  <MapPin className="w-3.5 h-3.5" /> Kolhapur
                </span>
              </span>
              <span className="inline-flex items-center gap-1.5">
                Free health camp this Saturday{' '}
                <span className="text-[#1D4ED8] dark:text-blue-400 inline-flex items-center gap-0.5">
                  <MapPin className="w-3.5 h-3.5" /> Sangli
                </span>
              </span>
              <span className="inline-flex items-center gap-1.5">
                New bus route approved by MSRTC{' '}
                <span className="text-[#1D4ED8] dark:text-blue-400 inline-flex items-center gap-0.5">
                  <MapPin className="w-3.5 h-3.5" /> Nashik
                </span>
              </span>
            </div>
          </div>

          {/* ─── Platform Features Section (Features Indicator for anchor link) ─── */}
          <div id="features" className="scroll-mt-20"></div>

          {/* ─── WHAT IS GOVLYX & PROBLEM/SOLUTION ─── */}
          <section className="py-10 sm:py-16 lg:py-20 px-3.5 sm:px-6 lg:px-24 bg-slate-50/50 dark:bg-base-200/40 border-t border-slate-200/80 dark:border-base-300/40 transition-colors">
            <div className="max-w-[1400px] mx-auto">
              {/* Header */}
              <div className="text-center max-w-3xl mx-auto mb-8 sm:mb-14">
                <span className="inline-block text-white text-[10px] sm:text-xs font-black tracking-widest uppercase bg-[#1D4ED8] px-3 sm:px-4 py-1 sm:py-1.5 rounded-full shadow-md shadow-[#1D4ED8]/25 border border-[#1D4ED8]">
                  What is Govlyx?
                </span>
                <h2 className="text-2xl sm:text-3xl md:text-4xl font-extrabold text-slate-900 dark:text-white mt-3 sm:mt-4 tracking-tight leading-tight">
                  A Simple App Built for Every Indian Citizen
                </h2>
                <p className="text-slate-600 dark:text-slate-400 mt-2.5 sm:mt-4 text-xs sm:text-sm md:text-base leading-relaxed font-medium">
                  Just enter your <strong>6-digit pincode</strong> and instantly
                  see news, civic issues, government announcements, and
                  community discussions happening in your own neighbourhood —
                  safely and anonymously.
                </p>
              </div>

              {/* Pillars Grid */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 sm:gap-6 lg:gap-8 mb-6 sm:mb-8">
                <div className="bg-white dark:bg-base-200 border border-slate-200/80 dark:border-base-300 p-4 sm:p-6 rounded-xl sm:rounded-2xl shadow-xs hover:shadow-md transition-all">
                  <div className="flex items-center gap-2.5 sm:gap-3 mb-3 sm:mb-4">
                    <div className="w-9 h-9 sm:w-12 sm:h-12 rounded-lg sm:rounded-xl bg-[#1D4ED8]/10 dark:bg-[#1D4ED8]/20 text-[#1D4ED8] dark:text-[#1D4ED8] flex items-center justify-center shrink-0">
                      <MapPin className="w-4.5 h-4.5 sm:w-6 sm:h-6" />
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-sm sm:text-lg leading-tight">
                      Your Neighbourhood
                    </h3>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 text-[11px] sm:text-xs md:text-sm leading-relaxed">
                    See only posts and issues from your pincode area. Not
                    national noise — just your street, ward, and colony.
                  </p>
                </div>
                <div className="bg-white dark:bg-base-200 border border-slate-200/80 dark:border-base-300 p-4 sm:p-6 rounded-xl sm:rounded-2xl shadow-xs hover:shadow-md transition-all">
                  <div className="flex items-center gap-2.5 sm:gap-3 mb-3 sm:mb-4">
                    <div className="w-9 h-9 sm:w-12 sm:h-12 rounded-lg sm:rounded-xl bg-[#1D4ED8]/10 dark:bg-[#1D4ED8]/20 text-[#1D4ED8] dark:text-[#1D4ED8] flex items-center justify-center shrink-0">
                      <Landmark className="w-4.5 h-4.5 sm:w-6 sm:h-6" />
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-sm sm:text-lg leading-tight">
                      Your Government
                    </h3>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 text-[11px] sm:text-xs md:text-sm leading-relaxed">
                    Government departments post updates, schemes, real-time
                    alerts and announcements directly to you.
                  </p>
                </div>
                <div className="bg-white dark:bg-base-200 border border-slate-200/80 dark:border-base-300 p-4 sm:p-6 rounded-xl sm:rounded-2xl shadow-xs hover:shadow-md transition-all">
                  <div className="flex items-center gap-2.5 sm:gap-3 mb-3 sm:mb-4">
                    <div className="w-9 h-9 sm:w-12 sm:h-12 rounded-lg sm:rounded-xl bg-[#1D4ED8]/10 dark:bg-[#1D4ED8]/20 text-[#1D4ED8] dark:text-[#1D4ED8] flex items-center justify-center shrink-0">
                      <Handshake className="w-4.5 h-4.5 sm:w-6 sm:h-6" />
                    </div>
                    <h3 className="font-extrabold text-slate-900 dark:text-white text-sm sm:text-lg leading-tight">
                      Your Community
                    </h3>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 text-[11px] sm:text-xs md:text-sm leading-relaxed">
                    Join local groups, discuss issues, vote in polls, and
                    connect with neighbours — all in one place.
                  </p>
                </div>
              </div>

              {/* Quick link to How to Use */}
              <div className="text-center pt-1 sm:pt-2">
                <button
                  onClick={() => navigate('/how-to-use')}
                  className="inline-flex items-center gap-2 text-xs sm:text-sm font-extrabold text-white bg-[#1D4ED8] hover:bg-[#1e40af] border border-[#1D4ED8] px-4 sm:px-6 py-2.5 sm:py-3 rounded-xl shadow-md shadow-[#1D4ED8]/20 transition-all cursor-pointer hover:scale-[1.02] active:scale-[0.98]"
                >
                  <span>
                    See how Govlyx works step-by-step & full platform guide
                  </span>
                  <ArrowRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </section>

          {/* ─── KEY FEATURES GRID ─── */}
          <section className="py-10 sm:py-16 lg:py-20 px-3.5 sm:px-6 lg:px-24 bg-white dark:bg-base-200/20 border-t border-slate-200/80 dark:border-base-300/40 transition-colors">
            <div className="max-w-[1400px] mx-auto">
              {/* Header */}
              <div className="text-center max-w-3xl mx-auto mb-8 sm:mb-14">
                <span className="inline-block text-white text-[10px] sm:text-xs font-black tracking-widest uppercase bg-[#1D4ED8] px-3 sm:px-4 py-1 sm:py-1.5 rounded-full shadow-md shadow-[#1D4ED8]/25 border border-[#1D4ED8]">
                  Feature Suite
                </span>
                <h2 className="text-2xl sm:text-3xl md:text-4xl font-extrabold text-slate-900 dark:text-white mt-3 sm:mt-4 tracking-tight leading-tight">
                  Everything You Can Do on Govlyx
                </h2>
                <p className="text-slate-500 dark:text-slate-400 mt-2 sm:mt-3 text-xs sm:text-sm leading-relaxed font-semibold">
                  Power packed features tailored specifically for hyperlocal
                  communities
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5 sm:gap-6">
                {/* 1. Local Feed */}
                <div className="bg-slate-50/90 dark:bg-base-200/90 border border-slate-200/80 dark:border-base-300 p-4 sm:p-5 rounded-xl sm:rounded-2xl shadow-xs hover:shadow-md transition-all">
                  <div className="flex items-center gap-2.5 mb-2 sm:mb-3">
                    <MapPin className="w-4.5 h-4.5 text-[#1D4ED8] shrink-0" />
                    <h4 className="font-extrabold text-slate-900 dark:text-white text-xs sm:text-sm leading-tight">
                      Local Feed
                    </h4>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 text-[11px] sm:text-xs leading-relaxed">
                    See posts and issues only from your pincode. Sort by Hot,
                    New, and Top tabs.
                  </p>
                </div>

                {/* 2. Govt Broadcasts */}
                <div className="bg-slate-50/90 dark:bg-base-200/90 border border-slate-200/80 dark:border-base-300 p-4 sm:p-5 rounded-xl sm:rounded-2xl shadow-xs hover:shadow-md transition-all">
                  <div className="flex items-center gap-2.5 mb-2 sm:mb-3">
                    <Landmark className="w-4.5 h-4.5 text-[#1D4ED8] shrink-0" />
                    <h4 className="font-extrabold text-slate-900 dark:text-white text-xs sm:text-sm leading-tight">
                      Govt Broadcasts
                    </h4>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 text-[11px] sm:text-xs leading-relaxed">
                    Departments post schemes, alerts, and real-time notices to
                    citizens by area.
                  </p>
                </div>

                {/* 3. Civic Tracker */}
                <div className="bg-slate-50/90 dark:bg-base-200/90 border border-slate-200/80 dark:border-base-300 p-4 sm:p-5 rounded-xl sm:rounded-2xl shadow-xs hover:shadow-md transition-all">
                  <div className="flex items-center gap-2.5 mb-2 sm:mb-3">
                    <AlertTriangle className="w-4.5 h-4.5 text-[#1D4ED8] shrink-0" />
                    <h4 className="font-extrabold text-slate-900 dark:text-white text-xs sm:text-sm leading-tight">
                      Grievance Redressal Platform
                    </h4>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 text-[11px] sm:text-xs leading-relaxed">
                    Learn how to report civic issues online in India. A robust
                    citizen grievance redressal platform where problems
                    auto-escalate from ward → district → state.
                  </p>
                </div>

                {/* 4. Communities */}
                <div className="bg-slate-50/90 dark:bg-base-200/90 border border-slate-200/80 dark:border-base-300 p-4 sm:p-5 rounded-xl sm:rounded-2xl shadow-xs hover:shadow-md transition-all">
                  <div className="flex items-center gap-2.5 mb-2 sm:mb-3">
                    <Users className="w-4.5 h-4.5 text-[#1D4ED8] shrink-0" />
                    <h4 className="font-extrabold text-slate-900 dark:text-white text-xs sm:text-sm leading-tight">
                      Communities
                    </h4>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 text-[11px] sm:text-xs leading-relaxed">
                    Create or join local groups — colony, school, RWA. Filter by
                    Public, Private, or Secret.
                  </p>
                </div>

                {/* 5. Polls */}
                <div className="bg-slate-50/90 dark:bg-base-200/90 border border-slate-200/80 dark:border-base-300 p-4 sm:p-5 rounded-xl sm:rounded-2xl shadow-xs hover:shadow-md transition-all">
                  <div className="flex items-center gap-2.5 mb-2 sm:mb-3">
                    <BarChart3 className="w-4.5 h-4.5 text-[#1D4ED8] shrink-0" />
                    <h4 className="font-extrabold text-slate-900 dark:text-white text-xs sm:text-sm leading-tight">
                      Neighbourhood Polls
                    </h4>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 text-[11px] sm:text-xs leading-relaxed">
                    Ask your neighbourhood a question and get instant votes and
                    stats.
                  </p>
                </div>

                {/* 6. Identity Protection */}
                <div className="bg-slate-50/90 dark:bg-base-200/90 border border-slate-200/80 dark:border-base-300 p-4 sm:p-5 rounded-xl sm:rounded-2xl shadow-xs hover:shadow-md transition-all">
                  <div className="flex items-center gap-2.5 mb-2 sm:mb-3">
                    <UserX className="w-4.5 h-4.5 text-[#1D4ED8] shrink-0" />
                    <h4 className="font-extrabold text-slate-900 dark:text-white text-xs sm:text-sm leading-tight">
                      Identity Protection
                    </h4>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 text-[11px] sm:text-xs leading-relaxed">
                    Random username hides who you are. Post videos safely
                    without your real name.
                  </p>
                </div>

                {/* 7. Anonymous Chat */}
                <div className="bg-slate-50/90 dark:bg-base-200/90 border border-slate-200/80 dark:border-base-300 p-4 sm:p-5 rounded-xl sm:rounded-2xl shadow-xs hover:shadow-md transition-all">
                  <div className="flex items-center gap-2.5 mb-2 sm:mb-3">
                    <MessageCircle className="w-4.5 h-4.5 text-[#1D4ED8] shrink-0" />
                    <h4 className="font-extrabold text-slate-900 dark:text-white text-xs sm:text-sm leading-tight">
                      1v1 Anonymous Chat
                    </h4>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 text-[11px] sm:text-xs leading-relaxed">
                    A secure Omegle alternative for local neighborhood. Talk to
                    people in my area anonymously via 1vs1 random chat app for
                    Indian neighborhoods.
                  </p>
                </div>

                {/* 8. Local Search */}
                <div className="bg-slate-50/90 dark:bg-base-200/90 border border-slate-200/80 dark:border-base-300 p-4 sm:p-5 rounded-xl sm:rounded-2xl shadow-xs hover:shadow-md transition-all">
                  <div className="flex items-center gap-2.5 mb-2 sm:mb-3">
                    <Search className="w-4.5 h-4.5 text-[#1D4ED8] shrink-0" />
                    <h4 className="font-extrabold text-slate-900 dark:text-white text-xs sm:text-sm leading-tight">
                      Hyperlocal Search
                    </h4>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 text-[11px] sm:text-xs leading-relaxed">
                    Search posts, communities, and hashtags specifically near
                    your pincode.
                  </p>
                </div>
              </div>
            </div>
          </section>

          {/* ─── QUICK CHAT REVIEWS & TESTIMONIALS CAROUSEL ─── */}
          <QuickChatReviewsCarousel />

          {/* ─── Communities Section ─────────────────────────────────────────────── */}
          <section
            id="communities"
            className="py-10 sm:py-16 lg:py-20 px-3.5 sm:px-6 bg-slate-50/50 dark:bg-base-200/40 border-t border-slate-200/80 dark:border-base-300/40 transition-colors"
          >
            <div className="max-w-[1400px] mx-auto">
              <div className="mb-8 sm:mb-14 text-center max-w-2xl mx-auto px-2">
                <h2 className="text-2xl sm:text-3xl md:text-4xl font-extrabold text-slate-900 dark:text-white mb-2.5 sm:mb-4 tracking-tight">
                  Discover your local network
                </h2>
                <p className="text-slate-600 dark:text-slate-400 text-xs sm:text-base md:text-lg leading-relaxed">
                  Join communities based on your interests, get daily updates,
                  or chat anonymously with neighbors in your pincode.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-6">
                {/* Card 1 */}
                <div className="bg-white dark:bg-base-200 border border-slate-200/80 dark:border-base-300 rounded-xl sm:rounded-2xl p-4 sm:p-5 shadow-xs hover:shadow-md transition-all duration-300 flex flex-col justify-between group">
                  <div>
                    <div className="flex items-start gap-3.5 sm:gap-4 mb-3 sm:mb-4">
                      <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-full bg-orange-50 dark:bg-orange-950/20 flex items-center justify-center flex-shrink-0 text-orange-500 dark:text-orange-400 shadow-inner group-hover:scale-105 transition-transform duration-300">
                        <TrafficCone className="w-4.5 h-4.5 sm:w-5 sm:h-5" />
                      </div>
                      <div className="space-y-1 min-w-0 flex-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <h3 className="font-bold text-slate-900 dark:text-white text-sm sm:text-base">
                            Pune traffic update
                          </h3>
                          <span className="bg-orange-50 dark:bg-orange-950/45 text-orange-600 dark:text-orange-400 border border-orange-200 dark:border-orange-900/60 text-[9px] font-bold px-1.5 py-0.5 rounded uppercase flex items-center gap-0.5 shrink-0">
                            <SettingsIcon className="w-2.5 h-2.5" /> Owner
                          </span>
                        </div>
                        <p className="text-[11px] sm:text-xs text-slate-500 dark:text-slate-400 line-clamp-2">
                          Get all traffic related updates in pune
                        </p>
                      </div>
                    </div>
                  </div>
                  <div className="mt-3 sm:mt-4 border-t border-slate-100 dark:border-base-300/60 pt-3 sm:pt-4 flex flex-col gap-2">
                    <p className="text-[10px] sm:text-xs text-slate-400 dark:text-slate-500 flex items-center gap-1 mb-1 sm:mb-2">
                      <Users className="w-3.5 h-3.5" /> 14,203 members
                    </p>
                    <div className="flex gap-2">
                      <button
                        onClick={() => navigate('/login')}
                        className="flex-1 py-1.5 text-xs font-semibold text-slate-600 dark:text-slate-300 hover:text-red-600 dark:hover:text-red-400 hover:bg-slate-50 dark:hover:bg-slate-800/40 border border-slate-200 dark:border-base-100 rounded-lg transition-all flex justify-center items-center gap-1 cursor-pointer"
                      >
                        <Eye className="w-3.5 h-3.5" /> View
                      </button>
                      <button
                        onClick={() => navigate('/login')}
                        className="flex-1 py-1.5 text-xs font-semibold text-orange-600 dark:text-orange-400 hover:bg-orange-50/40 dark:hover:bg-orange-950/30 border border-orange-200 dark:border-orange-900/40 rounded-lg transition-all flex justify-center items-center gap-1 cursor-pointer"
                      >
                        <SettingsIcon className="w-3.5 h-3.5" /> Manage
                      </button>
                    </div>
                  </div>
                </div>

                {/* Card 2 */}
                <div className="bg-white dark:bg-base-200 border border-slate-200/80 dark:border-base-300 rounded-xl sm:rounded-2xl p-4 sm:p-5 shadow-xs hover:shadow-md transition-all duration-300 flex flex-col justify-between group">
                  <div>
                    <div className="flex items-start gap-3.5 sm:gap-4 mb-3 sm:mb-4">
                      <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-full bg-blue-50 dark:bg-[#1D4ED8]/10 flex items-center justify-center flex-shrink-0 text-[#1D4ED8] dark:text-[#60a5fa] shadow-inner group-hover:scale-105 transition-transform duration-300">
                        <Droplet className="w-4.5 h-4.5 sm:w-5 sm:h-5 fill-current" />
                      </div>
                      <div className="space-y-1 min-w-0 flex-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <h3 className="font-bold text-slate-900 dark:text-white text-sm sm:text-base">
                            Water Supply Dept.
                          </h3>
                          <span className="bg-blue-50 dark:bg-[#1D4ED8]/10 text-[#1D4ED8] dark:text-blue-400 border border-blue-200 dark:border-[#1D4ED8]/20 text-[9px] font-bold px-1.5 py-0.5 rounded uppercase shrink-0">
                            Official
                          </span>
                        </div>
                        <p className="text-[11px] sm:text-xs text-slate-500 dark:text-slate-400 line-clamp-2">
                          Verified alerts for water cuts & supply scheduling.
                        </p>
                      </div>
                    </div>
                  </div>
                  <div className="mt-3 sm:mt-4 border-t border-slate-100 dark:border-base-300/60 pt-3 sm:pt-4 flex flex-col gap-2">
                    <p className="text-[10px] sm:text-xs text-slate-400 dark:text-slate-500 flex items-center gap-1 mb-1 sm:mb-2">
                      <Users className="w-3.5 h-3.5" /> 5,892 members
                    </p>
                    <button
                      onClick={() => navigate('/login')}
                      className="w-full py-1.5 sm:py-2 text-xs font-semibold text-slate-700 dark:text-slate-300 hover:text-white hover:bg-[#1D4ED8] border border-slate-200 dark:border-base-100 rounded-lg transition-all flex justify-center items-center gap-1 cursor-pointer"
                    >
                      <span>View</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                {/* Card 3 */}
                <div className="bg-white dark:bg-base-200 border border-slate-200/80 dark:border-base-300 rounded-xl sm:rounded-2xl p-4 sm:p-5 shadow-xs hover:shadow-md transition-all duration-300 flex flex-col justify-between group">
                  <div>
                    <div className="flex items-start gap-3.5 sm:gap-4 mb-3 sm:mb-4">
                      <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-full bg-purple-50 dark:bg-purple-950/20 flex items-center justify-center flex-shrink-0 text-purple-500 dark:text-purple-400 shadow-inner group-hover:scale-105 transition-transform duration-300">
                        <Bot className="w-4.5 h-4.5 sm:w-5 sm:h-5" />
                      </div>
                      <div className="space-y-1 min-w-0 flex-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <h3 className="font-bold text-slate-900 dark:text-white text-sm sm:text-base">
                            Local Chat
                          </h3>
                          <span className="bg-slate-100 dark:bg-base-300 text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-base-100 text-[9px] font-bold px-1.5 py-0.5 rounded flex items-center gap-0.5 uppercase shrink-0">
                            <Lock className="w-2.5 h-2.5" /> Secret
                          </span>
                        </div>
                        <p className="text-[11px] sm:text-xs text-slate-500 dark:text-slate-400 line-clamp-2">
                          Anonymous connections in your immediate area.
                        </p>
                      </div>
                    </div>
                  </div>
                  <div className="mt-3 sm:mt-4 border-t border-slate-100 dark:border-base-300/60 pt-3 sm:pt-4 flex flex-col gap-2">
                    <p className="text-[10px] sm:text-xs text-slate-400 dark:text-slate-500 flex items-center gap-1 mb-1 sm:mb-2">
                      <Zap className="w-3.5 h-3.5 text-yellow-500" /> Ephemeral
                      chats
                    </p>
                    <button
                      onClick={() => navigate('/login')}
                      className="w-full py-1.5 sm:py-2 text-xs font-semibold text-slate-700 dark:text-slate-300 hover:text-white hover:bg-[#1D4ED8] border border-slate-200 dark:border-base-100 rounded-lg transition-all flex justify-center items-center gap-1 cursor-pointer"
                    >
                      <span>Start Chatting</span>
                      <MessageCircle className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </main>

        {/* ─── Bottom CTA & Footer ────────────────────────────────────────────── */}
        <LandingBottomCtaAndFooter />
      </div>
    </div>
  );
}
