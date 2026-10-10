import { useEffect, useState, useRef } from "react";

interface BookmarkFoldProps {
  active: boolean;
  size?: number;
}

export default function BookmarkFold({ active, size = 18 }: BookmarkFoldProps) {
  const [iconTransform, setIconTransform] = useState("scaleY(1) scaleX(1)");
  const [iconTransition, setIconTransition] = useState("none");
  const [clipPath, setClipPath] = useState(active ? "inset(0% 0 0 0)" : "inset(100% 0 0 0)");
  const [clipTransition, setClipTransition] = useState("none");

  const prevActive = useRef(active);

  useEffect(() => {
    if (active !== prevActive.current) {
      // Step 1: fold the ribbon flat upward
      setIconTransition("transform .15s cubic-bezier(.4,0,1,1)");
      setIconTransform("scaleY(0.06) scaleX(1.18)");

      const t1 = setTimeout(() => {
        // Step 2: swap fill state with a clip-path wipe, timed with the unfold
        setClipTransition("none");
        if (active) {
          setClipPath("inset(100% 0 0 0)");
          requestAnimationFrame(() => {
            setClipTransition("clip-path .28s ease-out");
            setClipPath("inset(0% 0 0 0)");
          });
        } else {
          setClipPath("inset(0% 0 0 0)");
          requestAnimationFrame(() => {
            setClipTransition("clip-path .28s ease-out");
            setClipPath("inset(100% 0 0 0)");
          });
        }

        // Step 3: unfold with a bounce
        setIconTransition("transform .3s cubic-bezier(.34,1.56,.64,1)");
        setIconTransform("scaleY(1) scaleX(1)");
      }, 150);

      return () => {
        clearTimeout(t1);
      };
    } else {
      // Instant initialization without animations on mount
      setClipPath(active ? "inset(0% 0 0 0)" : "inset(100% 0 0 0)");
      setClipTransition("none");
      setIconTransform("scaleY(1) scaleX(1)");
      setIconTransition("none");
    }
    prevActive.current = active;
  }, [active]);

  // Height is size, Width is size * 0.8 (matching 24x30 viewBox ratio)
  const height = size;
  const width = size * 0.8;

  return (
    <div
      style={{
        width: `${width}px`,
        height: `${height}px`,
        position: "relative",
        transformOrigin: "50% 0%",
        transform: iconTransform,
        transition: iconTransition,
        pointerEvents: "none",
        userSelect: "none",
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      {/* Outline ribbon */}
      <svg
        viewBox="0 0 24 30"
        width={width}
        height={height}
        xmlns="http://www.w3.org/2000/svg"
        style={{
          position: "absolute",
          inset: 0,
        }}
      >
        <path
          d="M2 1.5h20a1 1 0 0 1 1 1V28l-11-7-11 7V2.5a1 1 0 0 1 1-1z"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.2"
          strokeLinejoin="round"
        />
      </svg>

      {/* Fill ribbon with clip-path wipe animation */}
      <svg
        viewBox="0 0 24 30"
        width={width}
        height={height}
        xmlns="http://www.w3.org/2000/svg"
        style={{
          position: "absolute",
          inset: 0,
          clipPath: clipPath,
          transition: clipTransition,
        }}
      >
        <path
          d="M2 1.5h20a1 1 0 0 1 1 1V28l-11-7-11 7V2.5a1 1 0 0 1 1-1z"
          fill="currentColor"
          stroke="currentColor"
          strokeWidth="2.2"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  );
}
