type GovlyxLogoProps = {
  className?: string;
  size?: number;
  showText?: boolean;
  iconClassName?: string;
  textClassName?: string;
  markScale?: number;
  orientation?: 'horizontal' | 'vertical';
};

const GovlyxLogo = ({
  className = '',
  size = 36,
  showText = false,
  iconClassName = '',
  textClassName = '',
  markScale = 1,
  orientation = 'horizontal',
}: GovlyxLogoProps) => {
  return (
    <div
      className={`flex items-center ${
        orientation === 'vertical' ? 'flex-col gap-3.5' : 'gap-3'
      } select-none ${className}`}
    >
      <span
        className={`inline-flex shrink-0 items-center justify-center rounded-full shadow-lg shadow-[#1D4ED8]/25 overflow-hidden ${iconClassName}`}
        style={{ width: size, height: size }}
      >
        <svg
          width={size}
          height={size}
          viewBox="0 0 200 200"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          className="w-full h-full transition-transform duration-300 hover:scale-105"
        >
          {/* Blue circle background */}
          <circle cx="100" cy="100" r="100" fill="#1D4ED8" />

          {/* All white elements, scaled up and centered */}
          <g
            transform={`translate(100, 102) scale(${0.32 * markScale}) translate(-256, -290)`}
          >
            {/* Building/columns top */}
            <g fill="#FFFFFF" transform="translate(0, -6)">
              <path d="M256 150c-40 0-72 32-72 72v20h144v-20c0-40-32-72-72-72z" />
              <rect x="220" y="242" width="72" height="16" />
              <rect x="204" y="220" width="12" height="40" />
              <rect x="296" y="220" width="12" height="40" />
            </g>
            {/* Decorative circles */}
            <g fill="#FFFFFF" transform="translate(0, -6)">
              <circle cx="170" cy="210" r="6" />
              <circle cx="196" cy="230" r="4" />
              <circle cx="342" cy="210" r="6" />
              <circle cx="318" cy="230" r="4" />
              <circle cx="256" cy="190" r="5" />
            </g>
            {/* Horizontal bar */}
            <path fill="#FFFFFF" d="M150 300h212l-8 16H158z" />
            {/* Columns */}
            <g fill="#FFFFFF">
              <rect x="248" y="300" width="16" height="120" />
              <rect x="198" y="300" width="16" height="80" />
              <rect x="298" y="300" width="16" height="80" />
            </g>
            {/* Bottom circles (column bases) */}
            <g fill="#FFFFFF">
              <circle cx="256" cy="440" r="18" />
              <circle cx="206" cy="380" r="20" />
              <circle cx="306" cy="380" r="20" />
            </g>
            {/* Flag/banner top */}
            <g>
              <rect x="252" y="118" width="8" height="32" fill="#FFFFFF" />
              <path d="M260 118h45v22l-45-8z" fill="#FFFFFF" />
              <path d="M260 118l35 16l-35-6z" fill="#FFFFFF" opacity="0.4" />
            </g>
          </g>
        </svg>
      </span>
      {showText && (
        <span
          className={`font-bold text-xl sm:text-2xl tracking-tight text-slate-900 dark:text-white transition-colors duration-300 notranslate ${textClassName}`}
        >
          Govlyx
        </span>
      )}
    </div>
  );
};

export default GovlyxLogo;
