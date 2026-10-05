import { motion } from 'framer-motion';
import { AlertTriangle, ThumbsUp, MessageSquare } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { toPostCardPost } from '../../utils/postUtils';

interface Props {
  post?: any;
}

const TopUnresolvedIssueWidget = ({ post: rawPost }: Props) => {
  const navigate = useNavigate();
  if (!rawPost) return null;

  const post = toPostCardPost(rawPost) as any;

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="p-2.5 rounded-xl border border-[#1D4ED8]/25 bg-[#1D4ED8]/10 shadow-sm relative overflow-hidden group cursor-pointer dark:border-[#1D4ED8]/65 dark:bg-[#1D4ED8]/22 dark:shadow-[0_0_0_1px_rgba(29,78,216,0.16)]"
      onClick={() => navigate(`/post/${post.id}?type=posts`)}
    >
      <div className="relative z-10 flex flex-col gap-1">
        <div className="flex items-center gap-1.5 border-b border-[#1D4ED8]/20 dark:border-[#1D4ED8]/50 pb-1">
          <div className="w-5 h-5 rounded-md bg-[#1D4ED8]/10 flex items-center justify-center text-[#1D4ED8] dark:bg-[#1D4ED8]/35 dark:text-white shrink-0">
            <AlertTriangle size={11} />
          </div>
          <span className="text-[9px] font-black text-[#1D4ED8] dark:text-white uppercase tracking-[0.15em]">
            Top Unresolved Issue
          </span>
        </div>

        <div className="flex flex-col gap-0.5">
          <div className="flex items-center justify-between gap-2">
            <span className="text-[8px] font-black text-[#1D4ED8] dark:text-white uppercase tracking-wider">
              {post.category || 'CRITICAL ISSUE'}
            </span>
            <span className="text-[8px] font-bold text-base-content/40 dark:text-white/55">
              {post.timeAgo || 'just now'}
            </span>
          </div>

          <p className="text-[11px] font-medium text-base-content/85 dark:text-white/90 line-clamp-1 leading-snug group-hover:text-[#1D4ED8] dark:group-hover:text-white transition-colors">
            {post.content}
          </p>

          <div className="flex items-center gap-2.5 mt-0.5 text-[8px] font-black text-base-content/40 dark:text-white/55 uppercase tracking-wider">
            <div className="flex items-center gap-1">
              <ThumbsUp size={9} className="text-[#1D4ED8] dark:text-white" />
              <span>{post.likeCount || 0} Upvotes</span>
            </div>
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

export default TopUnresolvedIssueWidget;
