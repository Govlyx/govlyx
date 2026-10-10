import { useState, useRef, useEffect } from "react";
import { 
  Star, 
  ShieldCheck, 
  MapPin, 
  Zap, 
  Heart, 
  ChevronLeft, 
  ChevronRight, 
  CheckCircle2, 
  ArrowRight,
  Flame
} from "lucide-react";
import { HiFire } from "react-icons/hi2";
import { useNavigate } from "react-router-dom";

export interface QuickChatReview {
  id: string;
  name: string;
  handle: string;
  pincode: string;
  locality: string;
  city: string;
  rating: number;
  tag: string;
  tagColor: string;
  avatarBg: string;
  avatarInitials: string;
  matchTime: string;
  timeAgo: string;
  review: string;
  highlight: string;
  likes: number;
  verifiedResident: boolean;
}

const REVIEWS_DATA: QuickChatReview[] = [
  {
    id: "rev-1",
    name: "Omkar Kulkarni",
    handle: "kothrud_omkar",
    pincode: "411038",
    locality: "Kothrud",
    city: "Pune",
    rating: 5,
    tag: "#BadmintonBuddy",
    tagColor: "bg-[#1D4ED8] text-white border-[#1D4ED8]",
    avatarBg: "bg-gradient-to-br from-blue-600 to-indigo-700",
    avatarInitials: "OK",
    matchTime: "Matched in 3s",
    timeAgo: "2 hours ago",
    highlight: "Found a badminton partner in just 2 minutes",
    review: "Our colony WhatsApp group is full of random forwards, so could never find anyone to play with. Tried Quick Chat in the evening and matched with a guy living 2 lanes away in Kothrud. We play daily now. Very simple and fast.",
    likes: 48,
    verifiedResident: true,
  },
  {
    id: "rev-2",
    name: "Tanvi Joshi",
    handle: "baner_tanvi",
    pincode: "411045",
    locality: "Baner Road",
    city: "Pune",
    rating: 5,
    tag: "#SafeForGirls",
    tagColor: "bg-[#1D4ED8] text-white border-[#1D4ED8]",
    avatarBg: "bg-gradient-to-br from-pink-500 to-rose-600",
    avatarInitials: "TJ",
    matchTime: "Matched in 4s",
    timeAgo: "5 hours ago",
    highlight: "I thought it would be weird like Omegle, but it is actually safe",
    review: "Honestly I was scared to talk to random people online. But here no need to share mobile number or real name, and screenshot is blocked. Matched with nice girls from Baner and we planned a plant drive. Really good safety.",
    likes: 72,
    verifiedResident: true,
  },
  {
    id: "rev-3",
    name: "Swapnil Patil",
    handle: "powai_swapnil",
    pincode: "400076",
    locality: "Hiranandani, Powai",
    city: "Mumbai",
    rating: 5,
    tag: "#NoSpamOrCalls",
    tagColor: "bg-[#1D4ED8] text-white border-[#1D4ED8]",
    avatarBg: "bg-gradient-to-br from-amber-500 to-orange-600",
    avatarInitials: "SP",
    matchTime: "Matched in 2s",
    timeAgo: "Yesterday",
    highlight: "Got annoyed when chat closed, but now I see why it is best",
    review: "At first I got irritated because once you disconnect the chat is gone forever. But then I realized this is why nobody can disturb or message you again. When power went off in Powai, asked a neighbour on Quick Chat and got update in seconds.",
    likes: 89,
    verifiedResident: true,
  },
  {
    id: "rev-4",
    name: "Prajakta Shinde",
    handle: "nashik_prajakta",
    pincode: "422005",
    locality: "College Road",
    city: "Nashik",
    rating: 5,
    tag: "#LiveRoadUpdate",
    tagColor: "bg-[#1D4ED8] text-white border-[#1D4ED8]",
    avatarBg: "bg-gradient-to-br from-teal-500 to-emerald-600",
    avatarInitials: "PS",
    matchTime: "Matched in 3s",
    timeAgo: "2 days ago",
    highlight: "Got live rain and traffic update before leaving office",
    review: "It was raining heavily in Nashik and I wanted to know if the circle road was full of water. Matched in 3 seconds with a student standing near the bus stop. Saved me from a huge 1 hour traffic jam!",
    likes: 54,
    verifiedResident: true,
  },
  {
    id: "rev-5",
    name: "Rohan Deshmukh",
    handle: "cidco_rohan",
    pincode: "431001",
    locality: "CIDCO",
    city: "Chhatrapati Sambhajinagar",
    rating: 5,
    tag: "#JoggingPartner",
    tagColor: "bg-[#1D4ED8] text-white border-[#1D4ED8]",
    avatarBg: "bg-gradient-to-br from-purple-600 to-indigo-700",
    avatarInitials: "RD",
    matchTime: "Matched in 5s",
    timeAgo: "3 days ago",
    highlight: "Skipped 2 timepass chats, then found my morning jogging buddy",
    review: "First two people just said 'hi' and disconnected, so I was about to close it. But then third person was a runner living near Prozone mall. Now we go for morning jog together every day. Skip button is quick.",
    likes: 61,
    verifiedResident: true,
  }
];

export default function QuickChatReviewsCarousel() {
  const navigate = useNavigate();
  const [reviews, setReviews] = useState<QuickChatReview[]>(REVIEWS_DATA);
  const [likedReviews, setLikedReviews] = useState<Record<string, boolean>>({});
  const [isPaused, setIsPaused] = useState(false);
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isPaused) return;
    const interval = setInterval(() => {
      // Disable timer auto-scroll on small screens (< 768px) so mobile users never get stuck
      if (typeof window !== "undefined" && window.innerWidth < 768) return;

      if (scrollContainerRef.current) {
        const { scrollLeft, scrollWidth, clientWidth } = scrollContainerRef.current;
        const cardWidth = scrollContainerRef.current.firstElementChild?.clientWidth || 340;
        if (scrollLeft + clientWidth >= scrollWidth - 20) {
          scrollContainerRef.current.scrollTo({ left: 0, behavior: "smooth" });
        } else {
          scrollContainerRef.current.scrollBy({ left: cardWidth + 20, behavior: "smooth" });
        }
      }
    }, 6000);

    return () => clearInterval(interval);
  }, [isPaused]);

  const handleLike = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const isCurrentlyLiked = likedReviews[id];
    setLikedReviews((prev) => ({
      ...prev,
      [id]: !isCurrentlyLiked,
    }));
    setReviews((prev) =>
      prev.map((item) => {
        if (item.id === id) {
          return {
            ...item,
            likes: isCurrentlyLiked ? item.likes - 1 : item.likes + 1,
          };
        }
        return item;
      })
    );
  };

  const scroll = (direction: "left" | "right") => {
    if (scrollContainerRef.current) {
      const cardWidth = scrollContainerRef.current.firstElementChild?.clientWidth || 340;
      const scrollAmount = direction === "left" ? -(cardWidth + 20) : (cardWidth + 20);
      scrollContainerRef.current.scrollBy({ left: scrollAmount, behavior: "smooth" });
    }
  };

  return (
    <section className="py-10 sm:py-16 px-3.5 sm:px-6 lg:px-24 bg-transparent border-t border-slate-200/80 dark:border-slate-800/60 relative overflow-hidden transition-colors">
      {/* Subtle Background Glows */}
      <div className="absolute top-1/2 left-0 w-80 h-80 bg-[#1D4ED8]/10 dark:bg-[#1D4ED8]/15 rounded-full blur-[100px] pointer-events-none -translate-y-1/2 -translate-x-1/2"></div>
      <div className="absolute bottom-0 right-0 w-80 h-80 bg-blue-500/10 dark:bg-blue-400/10 rounded-full blur-[100px] pointer-events-none translate-x-1/3"></div>

      <div className="max-w-[1400px] mx-auto relative z-10">
        
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-end justify-between mb-6 sm:mb-10 gap-4 sm:gap-6">
          <div className="max-w-2xl text-left">
            <div className="inline-flex items-center gap-1.5 sm:gap-2 bg-white text-black px-2.5 sm:px-3 py-0.5 sm:py-1 rounded-full mb-2 shadow-xs border border-slate-200">
              <span className="flex items-center justify-center text-[#1D4ED8] text-xs">
                <HiFire className="w-3.5 h-3.5 text-[#1D4ED8] fill-[#1D4ED8] animate-pulse" />
              </span>
              <span className="text-[9px] sm:text-[11px] font-black tracking-wider uppercase text-black">
                #1 Most Used Feature • 1-on-1 Quick Chat
              </span>
              <span className="relative flex h-2 w-2 ml-0.5">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-500 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
              </span>
            </div>
            
            <h2 className="text-lg sm:text-2xl lg:text-3xl font-extrabold text-slate-900 dark:text-white tracking-tight leading-tight flex flex-wrap items-center gap-x-2 gap-y-1">
              <span>Our Most Popular Feature:</span>
              <span className="bg-[#1D4ED8] text-white px-2 py-0.5 rounded-lg inline-block shadow-md shadow-[#1D4ED8]/25 text-base sm:text-xl lg:text-2xl">
                1v1 Quick Chat
              </span>
            </h2>
            
            <p className="text-slate-600 dark:text-slate-400 text-xs sm:text-sm mt-1 sm:mt-1.5 leading-relaxed font-medium">
              Over 80% of active neighbours use Quick Chat to ask emergency questions, check local updates, and connect safely without sharing mobile numbers.
            </p>
          </div>

          {/* Review Page Action & Navigation Controls */}
          <div className="flex items-center gap-2 sm:gap-3 self-start md:self-auto shrink-0">
            <button
              onClick={() => navigate("/review")}
              className="bg-[#1D4ED8] hover:bg-[#1e40af] text-white font-bold text-xs sm:text-sm px-3.5 sm:px-5 py-2 sm:py-2.5 rounded-xl shadow-md shadow-[#1D4ED8]/25 flex items-center gap-1.5 sm:gap-2 transition-all cursor-pointer hover:scale-[1.02] active:scale-[0.98] border border-[#1D4ED8]"
            >
              <Star className="w-3.5 h-3.5 fill-amber-300 text-amber-300" />
              <span>Write a Review</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>

            <div className="hidden sm:flex items-center gap-2">
              <button
                onClick={() => scroll("left")}
                aria-label="Previous Review"
                className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 border border-slate-200 dark:border-slate-700 flex items-center justify-center text-slate-600 dark:text-slate-300 hover:text-[#1D4ED8] dark:hover:text-blue-400 transition-all cursor-pointer shadow-2xs active:scale-95"
              >
                <ChevronLeft className="w-5 h-5" />
              </button>
              <button
                onClick={() => scroll("right")}
                aria-label="Next Review"
                className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 border border-slate-200 dark:border-slate-700 flex items-center justify-center text-slate-600 dark:text-slate-300 hover:text-[#1D4ED8] dark:hover:text-blue-400 transition-all cursor-pointer shadow-2xs active:scale-95"
              >
                <ChevronRight className="w-5 h-5" />
              </button>
            </div>
          </div>
        </div>

        {/* Reviews Carousel Track */}
        <div
          ref={scrollContainerRef}
          onMouseEnter={() => setIsPaused(true)}
          onMouseLeave={() => setIsPaused(false)}
          onTouchStart={() => setIsPaused(true)}
          onTouchEnd={() => setTimeout(() => setIsPaused(false), 5000)}
          className="flex gap-3.5 sm:gap-5 overflow-x-auto scrollbar-hide snap-x snap-mandatory py-1 sm:py-2 pb-4 sm:pb-6 -mx-3.5 px-3.5 sm:mx-0 sm:px-0 scroll-smooth"
          style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
        >
          {reviews.map((item) => {
            const isLiked = !!likedReviews[item.id];
            return (
              <div
                key={item.id}
                className="w-[82vw] max-w-[300px] xs:max-w-[320px] sm:w-[360px] md:w-[380px] shrink-0 snap-start bg-slate-50/90 dark:bg-base-200 border border-slate-200/80 dark:border-slate-800 rounded-2xl sm:rounded-3xl p-3.5 sm:p-5 shadow-xs hover:shadow-lg transition-all duration-300 flex flex-col justify-between group relative select-none"
              >
                <div>
                  <div className="flex items-start justify-between gap-2 sm:gap-3 mb-2.5 sm:mb-3.5">
                    <div className="flex items-center gap-2 sm:gap-3 min-w-0 flex-1">
                      <div className={`w-8 h-8 sm:w-11 sm:h-11 rounded-full ${item.avatarBg} text-white flex items-center justify-center font-extrabold text-xs sm:text-sm shadow-md ring-2 ring-white/20 shrink-0`}>
                        {item.avatarInitials}
                      </div>
                      <div className="text-left min-w-0 flex-1">
                        <div className="flex items-center gap-1 min-w-0">
                          <span className="font-extrabold text-slate-900 dark:text-white text-xs sm:text-sm leading-snug truncate">
                            {item.name}
                          </span>
                          {item.verifiedResident && (
                            <span title="Verified Resident in Pincode" className="text-emerald-500 dark:text-emerald-400 shrink-0">
                              <CheckCircle2 className="w-3.5 h-3.5 fill-emerald-500/20" />
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-1 text-[10px] sm:text-[11px] font-semibold text-slate-500 dark:text-slate-400 min-w-0">
                          <MapPin className="w-3 h-3 text-[#1D4ED8] dark:text-blue-400 shrink-0" />
                          <span className="truncate">{item.locality}, {item.city}</span>
                        </div>
                      </div>
                    </div>

                    <span className="bg-white border border-slate-200 text-black text-[9px] sm:text-[10px] font-black px-1.5 sm:px-2 py-0.5 rounded-md sm:rounded-lg shrink-0 ml-1">
                      {item.pincode}
                    </span>
                  </div>

                  <div className="flex items-center justify-between gap-2 mb-2 sm:mb-3">
                    <span className="text-[9px] sm:text-[10px] font-bold px-2 sm:px-2.5 py-0.5 rounded-md bg-[#1D4ED8] text-white border border-[#1D4ED8]">
                      {item.tag}
                    </span>
                    <span className="text-[9px] sm:text-[10px] font-semibold text-slate-400 dark:text-slate-500 flex items-center gap-1 shrink-0">
                      <Zap className="w-3 h-3 text-amber-500" />
                      {item.matchTime}
                    </span>
                  </div>

                  <div className="flex items-center gap-0.5 sm:gap-1 mb-1.5 sm:mb-2">
                    {[...Array(item.rating)].map((_, i) => (
                      <Star key={i} className="w-3 sm:w-3.5 h-3 sm:h-3.5 fill-amber-400 text-amber-400" />
                    ))}
                    <span className="text-[10px] sm:text-[11px] font-bold text-slate-400 dark:text-slate-500 ml-1">
                      {item.timeAgo}
                    </span>
                  </div>

                  <h4 className="font-extrabold text-slate-900 dark:text-white text-xs sm:text-sm mb-1.5 sm:mb-2 leading-snug text-left">
                    "{item.highlight}"
                  </h4>

                  <p className="text-slate-600 dark:text-slate-300 text-[11px] sm:text-xs md:text-[13px] leading-relaxed text-left font-medium">
                    {item.review}
                  </p>
                </div>

                <div className="mt-4 sm:mt-5 pt-2.5 sm:pt-3.5 border-t border-slate-200/70 dark:border-slate-800/80 flex items-center justify-between">
                  <div className="flex items-center gap-1 text-[10px] sm:text-[11px] font-semibold text-emerald-600 dark:text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-md">
                    <ShieldCheck className="w-3 h-3 sm:w-3.5 sm:h-3.5" />
                    <span>Resident Verified</span>
                  </div>

                  <button
                    onClick={(e) => handleLike(item.id, e)}
                    className={`flex items-center gap-1 sm:gap-1.5 px-2 sm:px-2.5 py-0.5 sm:py-1 rounded-lg text-xs font-bold transition-all duration-200 cursor-pointer ${
                      isLiked
                        ? "text-pink-600 bg-pink-50 dark:bg-pink-950/40 scale-105"
                        : "text-slate-400 dark:text-slate-500 hover:text-pink-600 dark:hover:text-pink-400 hover:bg-slate-100 dark:hover:bg-slate-800"
                    }`}
                    aria-label="Helpful review"
                  >
                    <Heart className={`w-3.5 h-3.5 transition-transform ${isLiked ? "fill-pink-600 text-pink-600" : ""}`} />
                    <span>{item.likes}</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        {/* Feature Highlights Banner below Carousel */}
        <div className="mt-4 sm:mt-6 bg-slate-50/90 dark:bg-base-200 border border-slate-200/80 dark:border-slate-800 rounded-xl sm:rounded-3xl p-3.5 sm:p-6 shadow-xs flex flex-col md:flex-row items-start md:items-center justify-between gap-3.5 sm:gap-6 text-left">
          <div className="flex items-start sm:items-center gap-3 sm:gap-4 min-w-0 flex-1">
            <div className="w-9 h-9 sm:w-12 sm:h-12 rounded-lg sm:rounded-2xl bg-orange-500/10 text-orange-500 dark:text-orange-400 flex items-center justify-center shrink-0 shadow-inner mt-0.5 sm:mt-0">
              <Flame className="w-4.5 h-4.5 sm:w-6 sm:h-6 fill-orange-500/20 text-orange-500" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-1.5 sm:gap-2 mb-0.5 sm:mb-1">
                <h3 className="font-extrabold text-slate-900 dark:text-white text-xs sm:text-base lg:text-lg leading-snug">
                  Try India's #1 Hyperlocal Quick Chat
                </h3>
                <span className="bg-orange-500/15 text-orange-600 dark:text-orange-400 text-[9px] sm:text-[10px] font-black px-1.5 sm:px-2 py-0.5 rounded-full uppercase shrink-0">
                  Most Popular
                </span>
              </div>
              <p className="text-slate-500 dark:text-slate-400 text-[10px] sm:text-xs md:text-sm font-medium leading-relaxed">
                Instant 1-on-1 pairing • Screenshot protected • 100% anonymous without sharing phone numbers
              </p>
            </div>
          </div>

          <button
            onClick={() => navigate("/login")}
            className="w-full md:w-auto bg-[#1D4ED8] hover:bg-[#1e40af] text-white font-bold text-xs sm:text-sm px-4 sm:px-6 py-2.5 sm:py-3 rounded-lg sm:rounded-xl shadow-md shadow-[#1D4ED8]/25 flex items-center justify-center gap-1.5 sm:gap-2 transition-all cursor-pointer shrink-0 active:scale-[0.98] border border-[#1D4ED8]"
          >
            <span>Start Quick Chat</span>
            <ArrowRight className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
          </button>
        </div>

      </div>
    </section>
  );
}
