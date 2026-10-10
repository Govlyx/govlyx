import { BadgeCheck, MessageSquare, Share2, Bookmark } from "lucide-react";
import { motion } from "framer-motion";

function ActionIcon({ name }: { name: "bookmark" | "share" | "comment" }) {
  const IconComponent = {
    bookmark: Bookmark,
    share: Share2,
    comment: MessageSquare,
  }[name];

  return (
    <IconComponent
      size={20}
      className="shrink-0 transition-transform duration-200 hover:scale-105"
    />
  );
}

type GovPostCardProps = {
  department: string;
  title: string;
  description: string;
  time?: string;
};

const GovPostCard = ({
  department,
  title,
  description,
  time = "1h ago",
}: GovPostCardProps) => {
  return (
    <motion.div
      whileHover={{ y: -2 }}
      transition={{ duration: 0.15 }}
      className="rounded-xl border border-info/30 bg-info/5 p-4"
     >
      {/* Header */}
      <div className="mb-2 flex items-center gap-2 text-sm text-info">
        <BadgeCheck size={18} />
        <span className="font-semibold">{department}</span>
        <span className="opacity-60">• {time}</span>
      </div>

      {/* Title */}
      <h2 className="mb-2 text-base font-semibold text-base-content">
        {title}
      </h2>

      {/* Description */}
      <p className="mb-3 text-sm opacity-80">
        {description}
      </p>

      {/* Actions */}
      <div className="flex items-center gap-4 text-sm opacity-80">
        <button className="flex items-center gap-1 hover:opacity-100">
          <ActionIcon name="bookmark" />
          Save
        </button>

        <button className="flex items-center gap-1 hover:opacity-100">
          <ActionIcon name="comment" />
          256
        </button>

        <button className="ml-auto flex items-center gap-1 hover:opacity-100">
          <ActionIcon name="share" />
          Share
        </button>
      </div>
    </motion.div>
  );
};

export default GovPostCard;
