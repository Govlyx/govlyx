import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import GovlyxLogo from '../ui/GovlyxLogo';
import { useTheme } from '../../hooks/useTheme';
import ThemeToggle from '../ui/ThemeToggle';

interface PageNavbarProps {
  active?:
    | 'home'
    | 'how-to-use'
    | 'updates'
    | 'review'
    | 'policy'
    | 'copyright';
}

export default function PageNavbar({ active = 'home' }: PageNavbarProps) {
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const [mobileOpen, setMobileOpen] = useState(false);
  const lastScrollY = useRef(0);

  // Auto-close drawer when user scrolls on small devices
  useEffect(() => {
    if (!mobileOpen) return;

    const handleScroll = () => {
      const currentY = window.scrollY;
      if (Math.abs(currentY - lastScrollY.current) > 10) {
        setMobileOpen(false);
      }
      lastScrollY.current = currentY;
    };

    lastScrollY.current = window.scrollY;
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [mobileOpen]);

  const handleEnterPlatform = (e?: React.MouseEvent) => {
    e?.preventDefault();
    if (localStorage.getItem('isLoggedIn') === 'true') {
      navigate('/dashboard');
    } else {
      navigate('/login');
    }
  };

  const navLinks = [
    {
      key: 'home',
      label: 'Home',
      action: () => {
        if (window.location.pathname === '/') {
          const scrollContainers =
            document.querySelectorAll('.overflow-y-auto');
          scrollContainers.forEach((el) =>
            el.scrollTo({ top: 0, behavior: 'smooth' }),
          );
          window.scrollTo({ top: 0, behavior: 'smooth' });
        } else {
          navigate('/');
        }
        setMobileOpen(false);
      },
    },
    {
      key: 'how-to-use',
      label: 'How to Use',
      action: () => {
        navigate('/how-to-use');
        setMobileOpen(false);
      },
    },
    {
      key: 'updates',
      label: 'Updates',
      action: () => {
        navigate('/upcoming-updates');
        setMobileOpen(false);
      },
    },
    {
      key: 'review',
      label: 'Review',
      action: () => {
        navigate('/review');
        setMobileOpen(false);
      },
    },
    {
      key: 'policy',
      label: 'Policy',
      action: () => {
        navigate('/privacy-policy');
        setMobileOpen(false);
      },
    },
    {
      key: 'copyright',
      label: 'Copyright',
      action: () => {
        navigate('/copyright-claim');
        setMobileOpen(false);
      },
    },
  ] as const;

  return (
    <>
      <nav className="border-b border-base-content/10 bg-base-100/95 backdrop-blur-md sticky top-0 z-50 h-14 sm:h-16 md:h-[68px] shrink-0 transition-colors duration-300">
        <div className="max-w-[1400px] mx-auto px-3.5 sm:px-6 h-full flex items-center justify-between">
          <a href="/" className="flex items-center">
            <GovlyxLogo
              showText
              size={32}
              markScale={0.9}
              textClassName="text-xl sm:text-2xl"
            />
          </a>

          {/* Desktop Navigation Pill */}
          <div className="hidden md:flex items-center gap-1 bg-slate-100/90 dark:bg-base-200 backdrop-blur-md p-1.5 rounded-full shadow-xs border border-slate-200/80 dark:border-base-300">
            {navLinks.map(({ key, label, action }) => (
              <button
                key={key}
                onClick={action}
                className={`text-[11px] sm:text-xs font-bold transition-all border-none cursor-pointer rounded-full px-3.5 py-1.5 ${
                  active === key
                    ? 'bg-[#1D4ED8] text-white shadow-md shadow-[#1D4ED8]/30'
                    : 'text-slate-700 dark:text-slate-200 hover:text-[#1D4ED8] dark:hover:text-white hover:bg-slate-200/50 dark:hover:bg-white/10'
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-2 sm:gap-3.5">
            {/* Desktop Theme Toggle */}
            <div className="hidden md:flex items-center">
              <ThemeToggle
                size={21}
                className="p-1.5 rounded-full hover:bg-slate-100 dark:hover:bg-white/10"
              />
            </div>

            <button
              onClick={handleEnterPlatform}
              className="bg-[#1D4ED8] hover:bg-[#1e40af] text-white font-extrabold text-xs sm:text-sm px-3.5 sm:px-4.5 py-1.5 sm:py-2 rounded-full shadow-md shadow-[#1D4ED8]/25 flex items-center gap-1.5 transition-all cursor-pointer hover:scale-[1.02] active:scale-[0.98] border border-[#1D4ED8]"
            >
              <span>Enter</span>
              <ArrowRight className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
            </button>

            {/* Mobile Hamburger Toggle */}
            <button
              onClick={() => setMobileOpen(!mobileOpen)}
              className="md:hidden p-2 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors cursor-pointer flex flex-col items-center justify-center gap-[4.5px] active:scale-90"
              aria-label="Toggle Menu"
            >
              <motion.span
                className="w-5 h-[2px] bg-current rounded-full origin-center"
                animate={
                  mobileOpen ? { rotate: 45, y: 6.5 } : { rotate: 0, y: 0 }
                }
                transition={{ duration: 0.25, ease: 'easeInOut' }}
              />
              <motion.span
                className="w-5 h-[2px] bg-current rounded-full origin-center"
                animate={
                  mobileOpen
                    ? { opacity: 0, scaleX: 0 }
                    : { opacity: 1, scaleX: 1 }
                }
                transition={{ duration: 0.2, ease: 'easeInOut' }}
              />
              <motion.span
                className="w-5 h-[2px] bg-current rounded-full origin-center"
                animate={
                  mobileOpen ? { rotate: -45, y: -6.5 } : { rotate: 0, y: 0 }
                }
                transition={{ duration: 0.25, ease: 'easeInOut' }}
              />
            </button>
          </div>
        </div>
      </nav>

      {/* Mobile Drawer Menu & Overlay */}
      <AnimatePresence>
        {mobileOpen && (
          <>
            {/* Backdrop Overlay */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
              onClick={() => setMobileOpen(false)}
              className="fixed inset-0 top-14 sm:top-16 md:top-[68px] bg-slate-950/40 backdrop-blur-xs z-[98] md:hidden"
            />

            <motion.div
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
              className="fixed top-14 sm:top-16 md:top-[68px] inset-x-0 z-[99] bg-white dark:bg-base-200 border-b border-slate-200 dark:border-base-300 shadow-xl md:hidden max-h-[calc(100dvh-56px)] overflow-y-auto"
            >
              <div className="px-3.5 pt-2 pb-4 space-y-0.5">
                {navLinks.map(({ key, label, action }) => (
                  <button
                    key={key}
                    onClick={action}
                    className={`w-full text-left block px-3.5 py-2.5 rounded-xl text-[13.5px] font-semibold transition-colors border-none cursor-pointer ${
                      active === key
                        ? 'bg-[#1D4ED8] text-white font-bold shadow-sm'
                        : 'bg-white dark:bg-transparent dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-white/10 hover:!text-[#1D4ED8] dark:hover:text-white'
                    }`}
                  >
                    {label}
                  </button>
                ))}

                <div className="pt-3 border-t border-slate-200 dark:border-slate-800 space-y-2 mt-1">
                  <div className="flex items-center gap-2.5">
                    <button
                      onClick={toggleTheme}
                      className="flex items-center justify-center gap-1.5 w-1/2 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 text-xs font-semibold text-slate-800 dark:text-white bg-white dark:bg-transparent hover:bg-slate-50 dark:hover:bg-white/10 cursor-pointer transition-colors"
                    >
                      <ThemeToggle size={15} className="!p-0" />
                      <span>
                        {theme === 'light' ? 'Dark Mode' : 'Light Mode'}
                      </span>
                    </button>
                    <button
                      onClick={(e) => {
                        setMobileOpen(false);
                        handleEnterPlatform(e);
                      }}
                      className="w-1/2 py-2.5 rounded-xl bg-[#1D4ED8] hover:bg-[#1e40af] text-white text-xs font-bold shadow-md shadow-[#1D4ED8]/20 flex justify-center items-center gap-1.5 cursor-pointer transition-colors"
                    >
                      <span>Enter</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </>
  );
}
