import { Around } from '@theme-toggles/react';
import '@theme-toggles/react/styles/around.css';
import { useTheme } from '../../hooks/useTheme';

interface ThemeToggleProps {
  className?: string;
  size?: number;
}

export const ThemeToggle = ({
  className = '',
  size = 21,
}: ThemeToggleProps) => {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';

  return (
    <Around
      toggled={isDark}
      onClick={toggleTheme}
      duration={500}
      title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      aria-label="Toggle theme"
      className={`theme-toggle-around inline-flex items-center justify-center cursor-pointer transition-colors border-none bg-transparent rounded-lg text-[#1D4ED8] hover:bg-slate-200/60 dark:text-white dark:hover:text-white dark:hover:bg-white/10 ${className}`}
      style={{ fontSize: `${size}px` }}
    />
  );
};

export default ThemeToggle;
