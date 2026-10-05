import { useEffect, useState, useRef } from 'react';

interface LoveBurstProps {
  active: boolean;
  size?: number;
}

export default function LoveBurst({ active, size = 22 }: LoveBurstProps) {
  const [animating, setAnimating] = useState(false);
  const [step, setStep] = useState(0); // 0: idle, 1: shrunk, 2: ring burst, 3: scale up, 4: settle/dots show, 5: dots hide, 6: done
  const prevActive = useRef(active);

  useEffect(() => {
    if (active && !prevActive.current) {
      // Trigger burst animation
      setAnimating(true);
      setStep(1);

      // Step 1: heart hides
      const t1 = setTimeout(() => {
        setStep(2);
      }, 100);

      // Step 2: ring bursts open, ornament ring scales in
      const t2 = setTimeout(() => {
        setStep(3);
      }, 200);

      // Step 3: ring stroke pulses in, heart reappears colored + overshoots
      const t3 = setTimeout(() => {
        setStep(4);
      }, 400);

      // Step 4 & 5: ring stroke pulses out, ornament ring settles with rotation, heart settles, dots flash in
      const t4 = setTimeout(() => {
        setStep(5);
      }, 600);

      // Step 6: ornament dots vanish
      const t5 = setTimeout(() => {
        setStep(6);
        setAnimating(false);
      }, 800);

      return () => {
        clearTimeout(t1);
        clearTimeout(t2);
        clearTimeout(t3);
        clearTimeout(t4);
        clearTimeout(t5);
      };
    } else if (!active) {
      // Reset instantly without animation
      setStep(0);
      setAnimating(false);
    }
    prevActive.current = active;
  }, [active]);

  // Dimensions relative to the 135px base design, but scaling the 46px heart to match `size`
  const baseSize = 135;
  const scale = size / 46;

  // Animation values based on the step
  // Heart wrap styles
  let heartScale = active ? 1 : 1;
  let heartTransition = 'transform 0.2s ease';
  if (animating) {
    if (step === 1) {
      heartScale = 0;
      heartTransition = 'transform 0.1s ease-in';
    } else if (step === 2) {
      heartScale = 0;
      heartTransition = 'transform 0.1s ease-in';
    } else if (step === 3) {
      heartScale = 1.3;
      heartTransition = 'transform 0.2s cubic-bezier(.34,1.56,.64,1)';
    } else if (step >= 4) {
      heartScale = 1;
      heartTransition = 'transform 0.2s ease-out';
    }
  }

  // Heart color
  const heartStroke = active
    ? 'var(--heart-active-color, #1D4ED8)'
    : 'var(--heart-default, #c0c1c3)';
  const heartFill = active ? 'var(--heart-active-color, #1D4ED8)' : 'none';

  // Ring styles
  let ringScale = 0;
  let ringOpacity = 0;
  let ringStrokeWidth = 0;
  let ringTransition = 'none';
  let ringCircleTransition = 'none';

  if (animating) {
    if (step >= 2) {
      ringScale = 1.2;
      ringOpacity = step >= 4 ? 0 : 1;
      ringTransition =
        'transform 0.2s cubic-bezier(.34,1.56,.64,1), opacity 0.2s ease-out';
    }
    if (step === 3) {
      ringStrokeWidth = 10;
      ringCircleTransition = 'stroke-width 0.2s cubic-bezier(.34,1.56,.64,1)';
    } else if (step >= 4) {
      ringStrokeWidth = 0;
      ringCircleTransition = 'stroke-width 0.2s cubic-bezier(.34,1.56,.64,1)';
    }
  }

  // Ornament wrapper styles
  let ornScale = 0;
  let ornRotate = 0;
  let ornTransition = 'none';

  if (animating) {
    if (step === 2 || step === 3) {
      ornScale = 0.8;
      ornRotate = 0;
      ornTransition = 'transform 0.3s linear';
    } else if (step >= 4) {
      ornScale = 1.2;
      ornRotate = 15;
      ornTransition = 'transform 0.2s ease-out';
    }
  }

  // Dots opacity & scale
  let dotOpacity = 0;
  let dotScale = 1;
  let dotTransition = 'none';

  if (animating) {
    if (step === 4) {
      dotOpacity = 1;
      dotScale = 1;
      dotTransition = 'opacity 0.2s ease';
    } else if (step >= 5) {
      dotOpacity = 1;
      dotScale = 0;
      dotTransition = 'transform 0.1s ease-out, opacity 0.1s ease-out';
    }
  }

  // Inline CSS variables style
  const containerStyle: React.CSSProperties = {
    position: 'relative',
    width: `${size}px`,
    height: `${size}px`,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    pointerEvents: 'none',
    userSelect: 'none',
  };

  const scalerStyle: React.CSSProperties = {
    position: 'absolute',
    width: `${baseSize}px`,
    height: `${baseSize}px`,
    left: '50%',
    top: '50%',
    transform: `translate(-50%, -50%) scale(${scale})`,
    pointerEvents: 'none',
  };

  return (
    <div style={containerStyle} className="love-burst-container">
      <style>{`
        .love-burst-container {
          --accent: #1D4ED8;
          --ornament-alt: #000000;
          --heart-default: #c0c1c3;
          --heart-active-color: #1D4ED8;
        }
        .dark .love-burst-container,
        [data-theme="dark"] .love-burst-container {
          --ornament-alt: #ffffff;
          --heart-default: #8a8f98;
          --accent: #1D4ED8;
          --heart-active-color: #ffffff;
        }
      `}</style>
      <div style={scalerStyle}>
        {/* Heart */}
        <div
          style={{
            position: 'absolute',
            left: '50%',
            top: '50%',
            transform: `translate(-50%, -50%) scale(${heartScale})`,
            transition: heartTransition,
            zIndex: 2,
          }}
        >
          <svg
            viewBox="0 0 24 24"
            style={{ width: '46px', height: '46px', display: 'block' }}
          >
            <g
              fill={heartFill}
              stroke={heartStroke}
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
              style={{ transition: 'fill 0.2s ease, stroke 0.2s ease' }}
            >
              <path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-1.91z" />
            </g>
          </svg>
        </div>

        {/* Ring */}
        <div
          style={{
            position: 'absolute',
            left: '50%',
            top: '50%',
            width: `${baseSize}px`,
            height: `${baseSize}px`,
            marginLeft: `-${baseSize / 2}px`,
            marginTop: `-${baseSize / 2}px`,
            transform: `scale(${ringScale})`,
            opacity: ringOpacity,
            transition: ringTransition,
          }}
        >
          <svg width={baseSize} height={baseSize}>
            <circle
              cx={baseSize / 2}
              cy={baseSize / 2}
              r="50"
              fill="transparent"
              stroke="var(--accent)"
              strokeWidth={ringStrokeWidth}
              style={{ transition: ringCircleTransition }}
            />
          </svg>
        </div>

        {/* Ornaments */}
        <div
          style={{
            position: 'absolute',
            left: '50%',
            top: '50%',
            width: `${baseSize}px`,
            height: `${baseSize}px`,
            marginLeft: `-${baseSize / 2}px`,
            marginTop: `-${baseSize / 2}px`,
            transform: `scale(${ornScale}) rotate(${ornRotate}deg)`,
            transition: ornTransition,
          }}
        >
          {[
            { x: -70, y: -38, color: 'var(--accent)' },
            { x: 0, y: -78, color: 'var(--ornament-alt)' },
            { x: 70, y: -38, color: 'var(--accent)' },
            { x: 70, y: 38, color: 'var(--ornament-alt)' },
            { x: 0, y: 78, color: 'var(--accent)' },
            { x: -70, y: 38, color: 'var(--ornament-alt)' },
          ].map((dot, idx) => (
            <span
              key={idx}
              style={{
                position: 'absolute',
                left: '50%',
                top: '50%',
                width: '14px',
                height: '14px',
                marginLeft: '-7px',
                marginTop: '-7px',
                borderRadius: '7px',
                backgroundColor: dot.color,
                opacity: dotOpacity,
                transform: `translate(${dot.x}px, ${dot.y}px) scale(${dotScale})`,
                transition: dotTransition,
              }}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
