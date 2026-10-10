type LoadingAnimationProps = {
  label?: string;
  overlay?: boolean;
  className?: string;
};

const LoadingAnimation = ({
  label = "Loading...",
  overlay = false,
  className = "",
}: LoadingAnimationProps) => {
  return (
    <div
      className={`flex items-center justify-center ${
        overlay
          ? "absolute inset-0 z-30 bg-base-100/55 backdrop-blur-[2px]"
          : "w-full py-10"
      } ${className}`}
      role="status"
      aria-live="polite"
    >
      <div className="flex flex-col items-center gap-4 rounded-2xl bg-base-100/20 px-6 py-5 text-center">
        <div className="loader text-[#1D4ED8]" aria-hidden="true" />
        {label && (
          <span className="text-[10px] font-black uppercase tracking-[0.28em] text-[#1D4ED8]">
            {label}
          </span>
        )}
      </div>
    </div>
  );
};

export default LoadingAnimation;
