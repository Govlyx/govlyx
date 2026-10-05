import { motion } from 'framer-motion';
import { MessageSquare, HelpCircle } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { toPostCardPost } from '../../utils/postUtils';

interface Props {
  posts?: any[];
}

const UnansweredQuestionsWidget = ({ posts }: Props) => {
  const navigate = useNavigate();
  if (!posts || posts.length === 0) return null;

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="p-2.5 rounded-xl border border-black/10 dark:border-base-300 bg-base-200 shadow-sm relative overflow-hidden group w-full text-left"
    >
      <div className="relative z-10 flex flex-col gap-1.5">
        <div className="flex items-center gap-1.5 bg-[#1D4ED8] -mx-2.5 -mt-2.5 px-2.5 py-1.5 rounded-t-xl mb-0.5">
          <div className="w-5 h-5 rounded-md bg-white/20 flex items-center justify-center text-white shrink-0">
            <HelpCircle size={12} className="stroke-[2.5]" />
          </div>
          <span className="text-[10px] font-black text-white uppercase tracking-wider">
            Unanswered Questions Near You
          </span>
        </div>

        <div className="flex flex-col gap-1.5 max-h-[380px] overflow-y-auto custom-scrollbar pr-0.5">
          {posts.slice(0, 5).map((p, idx) => {
            const post = toPostCardPost(p) as any;
            const author = (
              post.username ||
              post.authorUsername ||
              (post.author ? post.author.username : '') ||
              'anonymous'
            ).replace(/^@/, '');
            return (
              <div
                key={post.id || idx}
                onClick={() => navigate(`/post/${post.id}?type=social-posts`)}
                className="flex flex-col gap-1 p-2 rounded-lg border border-black/5 dark:border-white/5 bg-base-100 dark:bg-base-300/30 hover:bg-base-300/70 transition-all cursor-pointer group/item"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[10px] font-bold text-slate-400 dark:text-slate-400 uppercase tracking-wider truncate">
                    @{author}
                  </span>
                  <span className="text-[10px] font-medium text-slate-400 dark:text-slate-500 shrink-0">
                    {post.timeAgo || 'recently'}
                  </span>
                </div>
                <p className="text-[12px] font-medium text-slate-800 dark:text-slate-100 line-clamp-2 leading-snug group-hover/item:text-[#1D4ED8] transition-colors">
                  {post.content}
                </p>
                <div className="flex items-center gap-1 text-[10px] font-bold text-slate-400 dark:text-slate-400 uppercase tracking-wider">
                  <MessageSquare size={11} />
                  <span>
                    {post.commentCount ?? 0}{' '}
                    {post.commentCount === 1 ? 'COMMENT' : 'COMMENTS'}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </motion.div>
  );
};

export default UnansweredQuestionsWidget;
