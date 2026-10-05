import { useNavigate } from 'react-router-dom';
import { Users, Lock, EyeOff, ArrowRight, Settings } from 'lucide-react';
import { decodeHTML } from '../../utils/postUtils';

export type CommunityCardProps = {
  id: number;
  slug?: string;
  name: string;
  description: string;
  members: number;
  avatarUrl?: string | null;
  privacy?: string;
  isMember?: boolean;
  isOwner?: boolean;
  role?: 'ADMIN' | 'MODERATOR' | 'MEMBER' | 'OWNER' | string | null;
  currentUserRole?: 'ADMIN' | 'MODERATOR' | 'MEMBER' | 'OWNER' | string | null;
  hasPendingRequest?: boolean;
  rankLabel?: string;
  momentumScore?: number;
  isDeleted?: boolean;
  deletedAt?: string | null;
  scheduledDeletionDate?: string | null;
  deletionDueDate?: string | null;
  onClick?: () => void;
  onManage?: () => void;
};

function isCommunityDeleted(c: any): boolean {
  return (
    c.isDeleted === true ||
    !!c.deletedAt ||
    !!c.scheduledDeletionDate ||
    !!c.deletionDueDate
  );
}

function getDeletionDaysLeft(c: any): number {
  const dateStr =
    c.scheduledDeletionDate ||
    c.deletionDueDate ||
    (c.deletedAt
      ? new Date(
          new Date(c.deletedAt).getTime() + 1 * 24 * 60 * 60 * 1000,
        ).toISOString()
      : null);
  if (!dateStr) return 1;
  const diff = new Date(dateStr).getTime() - Date.now();
  const days = Math.ceil(diff / (1000 * 60 * 60 * 24));
  return Math.max(1, Math.min(1, days));
}

const CommunityCard = ({
  id,
  slug,
  name,
  description,
  members,
  avatarUrl,
  privacy,
  isMember,
  isOwner,
  role,
  currentUserRole,
  hasPendingRequest,
  isDeleted,
  deletedAt,
  scheduledDeletionDate,
  deletionDueDate,
  onClick,
  onManage,
}: CommunityCardProps) => {
  const navigate = useNavigate();

  const handlePress = () => {
    if (onClick) onClick();
    else navigate(`/communities/${slug || id}`);
  };

  const imgSrc =
    avatarUrl ||
    `https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(name)}`;
  const deleted = isCommunityDeleted({
    isDeleted,
    deletedAt,
    scheduledDeletionDate,
    deletionDueDate,
  });
  const rawRole =
    role || currentUserRole || (isOwner ? 'OWNER' : isMember ? 'MEMBER' : null);
  const userRole = typeof rawRole === 'string' ? rawRole.toUpperCase() : null;

  return (
    <div
      className={`group relative rounded-2xl border border-base-300/80 dark:border-white/10 hover:border-[#1D4ED8]/40 dark:hover:border-[#1D4ED8]/40 bg-base-200 p-3 sm:p-4 overflow-hidden cursor-pointer transition-all duration-200 flex flex-col justify-between min-w-0 shadow-sm hover:shadow-md ${deleted ? 'opacity-50' : ''}`}
      style={{ transform: 'translateZ(0)' }}
      onClick={handlePress}
    >
      {deleted && (
        <div className="absolute top-2.5 right-2.5 bg-error text-white text-[9px] font-black uppercase tracking-wider px-2 py-0.5 rounded-full shadow flex items-center gap-1 z-10 animate-pulse">
          <span>
            ⚠️ Deleting (
            {getDeletionDaysLeft({
              isDeleted,
              deletedAt,
              scheduledDeletionDate,
              deletionDueDate,
            })}
            d left)
          </span>
        </div>
      )}

      {/* Main Body */}
      <div className="flex gap-2.5 sm:gap-3.5 items-start">
        {/* Avatar */}
        <div className="shrink-0 relative">
          <div className="w-11 h-11 sm:w-13 sm:h-13 rounded-xl sm:rounded-2xl overflow-hidden ring-1 ring-base-300/80 dark:ring-white/10 bg-base-300 transition-all duration-200 shadow-xs">
            <img
              src={imgSrc}
              alt={name}
              className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
              onError={(e) => {
                (e.target as HTMLImageElement).src =
                  `https://api.dicebear.com/9.x/lorelei/svg?seed=${encodeURIComponent(name)}`;
              }}
            />
          </div>
          {/* Public indicator dot */}
          {privacy === 'PUBLIC' && (
            <span
              className="absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full bg-emerald-500 border-2 border-base-200 block"
              title="Public"
            />
          )}
        </div>

        {/* Info Column */}
        <div className="flex-1 min-w-0">
          {/* Header Row: Title & Role */}
          <div className="flex items-center justify-between gap-1.5 mb-1">
            <h3 className="font-bold text-sm sm:text-[15px] leading-tight truncate notranslate text-base-content">
              {decodeHTML(name)}
            </h3>
            <div className="flex items-center gap-1 shrink-0">
              {privacy && privacy !== 'PUBLIC' && (
                <span className="shrink-0 inline-flex items-center gap-0.5 text-[9px] sm:text-[10px] font-medium px-1.5 py-0.5 rounded-md bg-base-200 text-base-content/70 border border-base-300">
                  {privacy === 'SECRET' ? (
                    <>
                      <EyeOff size={9} /> Secret
                    </>
                  ) : (
                    <>
                      <Lock size={9} /> Private
                    </>
                  )}
                </span>
              )}
              {isOwner || userRole === 'OWNER' || userRole === 'ADMIN' ? (
                <span className="shrink-0 inline-flex items-center text-[9px] sm:text-[10px] font-bold px-2 py-0.5 rounded-full bg-amber-400 text-slate-950 border-none shadow-2xs">
                  Admin
                </span>
              ) : userRole === 'MODERATOR' ? (
                <span className="shrink-0 inline-flex items-center text-[9px] sm:text-[10px] font-bold px-2 py-0.5 rounded-full bg-rose-500 text-white border-none shadow-2xs">
                  Moderator
                </span>
              ) : isMember || userRole === 'MEMBER' ? (
                <span className="shrink-0 inline-flex items-center text-[9px] sm:text-[10px] font-semibold px-2 py-0.5 rounded-full bg-emerald-600 text-white border-none shadow-2xs">
                  Member
                </span>
              ) : hasPendingRequest ? (
                <span className="shrink-0 inline-flex items-center text-[9px] sm:text-[10px] font-bold px-2 py-0.5 rounded-full bg-amber-400 text-slate-950 border-none shadow-2xs">
                  Pending
                </span>
              ) : null}
            </div>
          </div>

          {/* Description */}
          {description ? (
            <p className="text-[11.5px] sm:text-xs text-base-content/70 line-clamp-2 leading-relaxed">
              {decodeHTML(description)}
            </p>
          ) : (
            <p className="text-[11.5px] sm:text-xs text-base-content/40 italic">
              No description provided.
            </p>
          )}
        </div>
      </div>

      {/* Footer Row */}
      <div className="mt-2.5 sm:mt-3 pt-2 sm:pt-2.5 border-t border-base-200/80 dark:border-white/5 flex items-center justify-between gap-1.5">
        <span className="inline-flex items-center gap-1 text-[11px] sm:text-xs font-medium text-base-content/60 shrink-0">
          <Users size={12} className="text-[#1D4ED8] shrink-0" />
          <span>
            {members.toLocaleString()} {members === 1 ? 'member' : 'members'}
          </span>
        </span>

        <div className="flex items-center gap-1.5 shrink-0">
          {(isOwner ||
            userRole === 'OWNER' ||
            userRole === 'ADMIN' ||
            userRole === 'MODERATOR') &&
            onManage && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onManage();
                }}
                className="btn btn-xs bg-base-200 hover:bg-base-300 text-base-content border border-base-300/70 rounded-lg px-2 py-1 h-auto font-bold flex items-center gap-1 transition-all cursor-pointer"
              >
                <Settings size={10} />{' '}
                <span className="text-[11px]">Manage</span>
              </button>
            )}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              handlePress();
            }}
            className="btn btn-xs bg-[#1D4ED8] hover:bg-blue-800 text-white border-none rounded-lg px-2.5 sm:px-3 py-1 h-auto font-bold flex items-center gap-1 transition-all shadow-2xs shrink-0"
          >
            <span className="text-[11px]">View</span>
            <ArrowRight
              size={10}
              className="transition-transform duration-200 group-hover:translate-x-0.5"
            />
          </button>
        </div>
      </div>
    </div>
  );
};

export default CommunityCard;
