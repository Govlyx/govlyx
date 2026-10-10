import { Clock, MessageSquare, Share2 } from "lucide-react";
import { motion } from "framer-motion";

function ActionIcon({ name }: { name: "comment" | "share" }) {
  const IconComponent = {
    comment: MessageSquare,
    share: Share2,
  }[name];

  return (
    <IconComponent
      size={20}
      className="shrink-0 transition-transform duration-200 hover:scale-105"
    />
  );
}

type PollOption = {
  label: string;
  percentage: number;
  selected?: boolean;
};

type PollPostCardProps = {
  author: string;
  location: string;
  question: string;
  options: PollOption[];
  votes: number;
  timeLeft: string;
};

const PollPostCard = ({
  author,
  location,
  question,
  options,
  votes,
  timeLeft,
}: PollPostCardProps) => {
  return (
    <motion.div
      whileHover={{ y: -2 }}
      transition={{ duration: 0.15 }}
      className="rounded-xl border border-base-300 bg-base-200 p-4"
    >
      {/* Header */}
      <div className="mb-2 flex items-center gap-2 text-sm opacity-70">
        <span className="font-mono">{author}</span>
        <span>•</span>
        <span>{location}</span>
      </div>

      {/* Question */}
      <h2 className="mb-3 font-semibold">
        {question}
      </h2>

      {/* Options */}
      <div className="space-y-2">
        {options.map((opt, idx) => (
          <div
            key={idx}
            className={`relative overflow-hidden rounded-lg border
              ${opt.selected ? "border-blue-700" : "border-base-300"}
            `}
          >
            {/* Progress */}
            <div
              className={`absolute left-0 top-0 h-full
                ${opt.selected ? "bg-blue-700/30" : "bg-base-300/40"}
              `}
              style={{ width: `${opt.percentage}%` }}
            />

            {/* Content */}
            <div className="relative z-10 flex items-center justify-between px-3 py-2 text-sm">
              <span>{opt.label}</span>
              <span className="font-semibold">
                {opt.percentage}%
              </span>
            </div>
          </div>
        ))}
      </div>

      {/* Meta */}
      <div className="mt-3 flex items-center gap-3 text-xs opacity-70">
        <span>{votes.toLocaleString()} votes</span>
        <span className="flex items-center gap-1">
          <Clock size={14} />
          {timeLeft}
        </span>
      </div>

      {/* Actions */}
      <div className="mt-3 flex items-center gap-4 text-sm opacity-80">
        <button className="flex items-center gap-1 hover:opacity-100">
          <ActionIcon name="comment" />
          45
        </button>

        <button className="ml-auto flex items-center gap-1 hover:opacity-100">
          <ActionIcon name="share" />
          Share
        </button>
      </div>
    </motion.div>
  );
};

export default PollPostCard;
