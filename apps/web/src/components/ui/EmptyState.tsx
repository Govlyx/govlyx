import { motion } from "framer-motion";
import { Inbox } from "lucide-react";

type EmptyStateProps = {
  title: string;
  description?: string;
};

const EmptyState = ({ title, description }: EmptyStateProps) => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2 }}
      className="flex flex-col items-center justify-center rounded-xl bg-base-200 p-8 text-center"
    >
      <Inbox size={36} className="mb-3 opacity-50" />
      <h3 className="font-semibold">{title}</h3>
      {description && (
        <p className="mt-1 text-sm opacity-70">
          {description}
        </p>
      )}
    </motion.div>
  );
};

export default EmptyState;
