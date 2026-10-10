import { motion } from "framer-motion";
import { Megaphone, MessageSquare } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { toPostCardPost } from "../../utils/postUtils";

interface Props {
  post?: any;
}

const OfficialAlertWidget = ({ post: rawPost }: Props) => {
  const navigate = useNavigate();
  if (!rawPost) return null;

  const post = toPostCardPost(rawPost) as any;

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="p-2.5 rounded-xl border border-white bg-base-100 shadow-sm relative overflow-hidden group cursor-pointer dark:border-[#1D4ED8] dark:bg-[#1D4ED8] dark:shadow-none"
      onClick={() => navigate(`/post/${post.id}?type=posts`)}
    >
      <div className="relative z-10 flex flex-col gap-1">
        <div className="flex items-center gap-1.5 border-b border-base-200 dark:border-white/20 pb-1">
          <div className="w-5 h-5 rounded-md bg-white flex items-center justify-center text-red-500 border border-white shrink-0">
            <Megaphone size={11} className="animate-bounce" />
          </div>
          <span className="text-[9px] font-black text-red-500 dark:text-red-300 uppercase tracking-[0.15em]">Latest Official Alert</span>
        </div>

        <div className="flex flex-col gap-0.5">
          <div className="flex items-center justify-between gap-2">
            <span className="text-[8px] font-black text-red-500 dark:text-red-300 uppercase tracking-wider">
              {post.department || "OFFICIAL ANNOUNCEMENT"}
            </span>
            <span className="text-[8px] font-bold text-base-content/40 dark:text-white/80">
              {post.timeAgo || "just now"}
            </span>
          </div>

          <p className="text-[11px] font-medium text-base-content/85 dark:text-white line-clamp-1 leading-snug group-hover:text-red-500 dark:group-hover:text-white transition-colors">
            {post.content}
          </p>

          <div className="flex items-center gap-2.5 mt-0.5 text-[8px] font-black text-base-content/40 dark:text-white/80 uppercase tracking-wider">
            <div className="flex items-center gap-1">
              <MessageSquare size={9} />
              <span>{post.commentCount || 0} Comments</span>
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  );
};

export default OfficialAlertWidget;
