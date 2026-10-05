import type { ReactNode } from 'react';
import { motion } from 'framer-motion';
import GovlyxLogo from '../ui/GovlyxLogo';
import { useTheme } from '../../hooks/useTheme';

type Props = {
  children: ReactNode;
  brandTitle?: string;
  brandSubtitle?: string;
};

const AuthLayout = ({ children }: Props) => {
  const { theme } = useTheme();
  const oppositeTheme = theme === 'dark' ? 'light' : 'dark';

  return (
    <div className="w-full h-full min-h-screen bg-base-100 flex flex-col lg:grid lg:grid-cols-12 lg:h-screen lg:overflow-hidden">
      {/* ── Left Side: Brand & Hero Showcase (Large screens only) ── */}
      <div
        data-theme={oppositeTheme}
        className="hidden lg:flex lg:col-span-4 flex-col justify-between relative bg-base-100 text-base-content p-6 xl:p-8 2xl:p-10 h-full overflow-hidden border-r border-base-300 select-none transition-colors duration-300"
      >
        {/* Middle: Brand Logo & Text (Centered) */}
        <div className="relative z-10 my-auto flex flex-col items-center justify-center text-center max-w-lg mx-auto py-4">
          <GovlyxLogo
            size={130}
            showText
            orientation="vertical"
            markScale={0.88}
            className="gap-4"
            textClassName="!text-base-content text-4xl sm:text-5xl font-extrabold tracking-tight"
          />
        </div>

        {/* Bottom: Footer Info */}
        <div className="relative z-10 flex items-center justify-between text-xs opacity-50 border-t border-base-300 pt-3.5">
          <span className="font-semibold opacity-90">Govlyx India</span>
          <span>Safe • Hyperlocal • Anonymous</span>
        </div>
      </div>

      {/* ── Right Side: Interactive Form Panel (Responsive across all screens) ── */}
      <div className="w-full h-full lg:col-span-8 overflow-y-auto bg-base-100 flex items-center justify-center px-4 py-5 sm:px-6 lg:px-8 xl:px-12 lg:py-6">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.25, ease: 'easeOut' }}
          className="w-full max-w-md rounded-2xl border border-base-300 bg-base-200 p-5 sm:p-6 shadow-xl lg:bg-transparent lg:border-none lg:p-0 lg:shadow-none lg:max-w-[440px] xl:max-w-[460px]"
        >
          {children}
        </motion.div>
      </div>
    </div>
  );
};

export default AuthLayout;
