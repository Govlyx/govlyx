import { Share2, Sparkles } from "lucide-react";

export type KarmaTier = {
  name: "Navya" | "Spandan" | "Tejas" | "Sattva";
  label: string;
  min: number;
  next?: number;
  className: string;
};

export function getKarmaTier(score = 0): KarmaTier {
  if (score >= 1000) {
    return { name: "Sattva", label: "Harmony", min: 1000, className: "bg-amber-500/15 text-amber-700 border-amber-500/30 dark:bg-amber-400/10 dark:text-amber-400 dark:border-amber-400/20" };
  }
  if (score >= 500) {
    return { name: "Tejas", label: "Radiance", min: 500, next: 1000, className: "bg-orange-500/15 text-orange-700 border-orange-500/30 dark:bg-orange-400/10 dark:text-orange-400 dark:border-orange-400/20" };
  }
  if (score >= 100) {
    return { name: "Spandan", label: "Pulse", min: 100, next: 500, className: "bg-blue-500/15 text-blue-700 border-blue-500/30 dark:bg-blue-400/10 dark:text-blue-400 dark:border-blue-400/20" };
  }
  return { name: "Navya", label: "Fresh civic voice", min: 0, next: 100, className: "bg-emerald-500/15 text-emerald-700 border-emerald-500/30 dark:bg-emerald-400/10 dark:text-emerald-400 dark:border-emerald-400/20" };
}

export function shareKarmaBadge(score = 0) {
  const tier = getKarmaTier(score);
  const text = `I just reached ${tier.name} status on Govlyx with ${score} Civic Karma. Join me in fixing our city!`;
  const url = `https://wa.me/?text=${encodeURIComponent(text)}`;
  window.open(url, "_blank", "noopener,noreferrer");
}

type KarmaBadgeProps = {
  score?: number;
  compact?: boolean;
  shareable?: boolean;
};

const KarmaBadge = ({ score = 0, compact = false, shareable = false }: KarmaBadgeProps) => {
  const tier = getKarmaTier(score);

  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full border font-black ${tier.className} ${compact ? "px-2 py-0.5 text-[9px]" : "px-3 py-1 text-xs"}`}>
      <Sparkles size={compact ? 10 : 13} className="shrink-0" />
      <span>{tier.name}</span>
      {!compact && <span className="font-bold opacity-70">{score.toLocaleString()}</span>}
      {shareable && (
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            shareKarmaBadge(score);
          }}
          className="ml-0.5 inline-flex h-5 w-5 items-center justify-center rounded-full bg-base-100/70 hover:bg-base-100"
          title="Share badge to WhatsApp"
        >
          <Share2 size={11} />
        </button>
      )}
    </span>
  );
};

export default KarmaBadge;
