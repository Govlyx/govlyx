import { ShieldCheck } from "lucide-react";
import { motion } from "framer-motion";

type GovernmentPostCardProps = {
  title: string;
  content: string;
  time: string;
};

const GovernmentPostCard = ({
  title,
  content,
  time,
}: GovernmentPostCardProps) => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2 }}
      className="rounded-xl border border-blue-700/30 bg-blue-700/5 p-4"
    >
      <div className="mb-2 flex items-center gap-2 text-sm text-blue-700">
        <ShieldCheck size={16} />
        Government Broadcast • {time}
      </div>

      <h3 className="font-semibold">{title}</h3>

      <p className="mt-1 text-sm opacity-80">
        {content}
      </p>
    </motion.div>
  );
};

export default GovernmentPostCard;
