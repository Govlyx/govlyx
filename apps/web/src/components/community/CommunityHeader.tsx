import { useState, useRef } from 'react';
import { Users, Camera, Clock, Trophy, Activity } from 'lucide-react';
import { communityService } from '../../api/communityService';
import { showToast } from '../../utils/toast';
import ImageEditorModal from '../modals/ImageEditorModal';
import { decodeHTML } from '../../utils/postUtils';

type CommunityHeaderProps = {
  community: {
    id?: number;
    name: string;
    description: string;
    memberCount: number;
    privacy: string;
    avatarUrl?: string | null;
    coverImageUrl?: string | null;
    isMember?: boolean;
    isOwner?: boolean;
    isAdmin?: boolean;
    isModerator?: boolean;
    role?: string | null;
    currentUserRole?: string | null;
    memberRole?: string | null;
    hasPendingRequest?: boolean;
    rankLabel?: string;
    cityRank?: number;
    percentile?: number;
    momentumScore?: number;
    healthScore?: number;
    postCount?: number;
    isDeleted?: boolean;
    deletedAt?: string | null;
    scheduledDeletionDate?: string | null;
    deletionDueDate?: string | null;
  };
  acting?: boolean;
  onJoinClick?: () => void;
  onShareClick?: () => void;
  onImageUploaded?: (type: 'avatar' | 'cover', url: string) => void;
};

const CommunityHeader = ({
  community: c,
  acting,
  onJoinClick,
  onImageUploaded,
}: CommunityHeaderProps) => {
  const finalOwner = Boolean(
    c.isOwner ||
      c.isAdmin ||
      (c.role &&
        (String(c.role).toUpperCase() === 'OWNER' ||
          String(c.role).toUpperCase() === 'ADMIN')) ||
      (c.currentUserRole &&
        (String(c.currentUserRole).toUpperCase() === 'OWNER' ||
          String(c.currentUserRole).toUpperCase() === 'ADMIN')),
  );
  const finalMember = Boolean(
    finalOwner || c.isMember || c.isAdmin || c.isModerator,
  );
  const finalPending =
    !finalMember && !finalOwner && Boolean(c.hasPendingRequest);
  const isSecret = c.privacy === 'SECRET' && !finalMember;
  const isDeleted =
    c.isDeleted === true ||
    !!c.deletedAt ||
    !!c.scheduledDeletionDate ||
    !!c.deletionDueDate;
  const momentumScore = Math.round(c.healthScore ?? c.momentumScore ?? 0);
  const rankLabel =
    c.rankLabel ||
    (c.cityRank && c.cityRank <= 3
      ? `#${c.cityRank} Most Active Community`
      : c.percentile && c.percentile <= 5
        ? `Top ${c.percentile}% Overall`
        : momentumScore >= 70
          ? 'Top 5% Growing'
          : 'Rising Community');

  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [uploadingCover, setUploadingCover] = useState(false);
  const avatarInputRef = useRef<HTMLInputElement>(null);
  const coverInputRef = useRef<HTMLInputElement>(null);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editorType, setEditorType] = useState<'avatar' | 'cover'>('avatar');
  const [editorImageSrc, setEditorImageSrc] = useState<string | null>(null);

  const handleUpload = async (
    e: React.ChangeEvent<HTMLInputElement>,
    type: 'avatar' | 'cover',
  ) => {
    const file = e.target.files?.[0];
    if (!file || !c.id) return;

    // Validation
    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type)) {
      showToast.error('Please upload a JPEG, PNG, or WebP image.');
      return;
    }
    const maxSize = 5 * 1024 * 1024; // 5MB
    if (file.size > maxSize) {
      showToast.error('File size exceeds 5MB limit.');
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      setEditorType(type);
      setEditorImageSrc(reader.result as string);
      setEditorOpen(true);
    };
    reader.readAsDataURL(file);
  };

  const handleEditorSave = async (editedBlob: Blob) => {
    if (!c.id) return;
    const isCover = editorType === 'cover';
    if (isCover) {
      setUploadingCover(true);
    } else {
      setUploadingAvatar(true);
    }
    setEditorOpen(false);
    try {
      const fileName = isCover ? 'community_cover.jpg' : 'community_avatar.jpg';
      const file = new File([editedBlob], fileName, { type: 'image/jpeg' });
      const data = await communityService.uploadCommunityImage(
        c.id,
        file,
        isCover ? 'cover' : 'avatar',
      );
      if (isCover) {
        const updatedUrl =
          data?.coverImageUrl ||
          data?.data?.coverImageUrl ||
          data?.data?.data?.coverImageUrl;
        if (updatedUrl) {
          onImageUploaded?.('cover', updatedUrl);
          showToast.success('Cover image updated!');
        } else {
          showToast.error('Upload succeeded but no image URL was returned.');
        }
      } else {
        const updatedUrl =
          data?.avatarUrl ||
          data?.data?.avatarUrl ||
          data?.data?.data?.avatarUrl;
        if (updatedUrl) {
          onImageUploaded?.('avatar', updatedUrl);
          showToast.success('Avatar image updated!');
        } else {
          showToast.error('Upload succeeded but no avatar URL was returned.');
        }
      }
    } catch (err: any) {
      console.error(err);
      showToast.error(
        err.response?.data?.message || err.message || 'Upload failed.',
      );
    } finally {
      if (isCover) {
        setUploadingCover(false);
        if (coverInputRef.current) coverInputRef.current.value = '';
      } else {
        setUploadingAvatar(false);
        if (avatarInputRef.current) avatarInputRef.current.value = '';
      }
      setEditorImageSrc(null);
    }
  };

  return (
    <div className="relative w-full rounded-3xl border border-base-300 bg-base-100 overflow-hidden shadow-sm">
      {/* Cover Image */}
      <div className="relative h-32 sm:h-44 w-full overflow-hidden bg-base-200">
        {c.coverImageUrl ? (
          <img
            src={c.coverImageUrl}
            className="w-full h-full object-cover"
            alt=""
          />
        ) : (
          <div className="w-full h-full bg-gradient-to-r from-blue-700/10 to-blue-500/5 border-b border-base-300" />
        )}

        {finalOwner && c.id && !isDeleted && (
          <>
            <button
              onClick={() => coverInputRef.current?.click()}
              disabled={uploadingCover}
              className="absolute top-3 right-3 bg-black/60 hover:bg-black/80 text-white rounded-xl px-3 py-1.5 text-xs font-bold transition-all flex items-center gap-1.5 z-10 border border-white/10 shadow"
            >
              {uploadingCover ? (
                <span className="loading loading-spinner loading-xs" />
              ) : (
                <Camera size={14} />
              )}
              <span>Edit Cover</span>
            </button>
            <input
              type="file"
              ref={coverInputRef}
              onChange={(e) => handleUpload(e, 'cover')}
              accept="image/jpeg,image/png,image/webp"
              className="hidden"
            />
          </>
        )}
      </div>

      <div className="px-5 pb-5 relative">
        {/* Avatar */}
        <div className="absolute -top-10 left-5 w-20 h-20 rounded-2xl border-4 border-base-100 bg-blue-700/10 flex items-center justify-center font-bold text-3xl text-blue-700 shadow-sm overflow-hidden text-center uppercase group">
          {c.avatarUrl ? (
            <img
              src={c.avatarUrl}
              alt="Avatar"
              className="w-full h-full object-cover"
            />
          ) : (
            (c.name?.[0] || '?').toUpperCase()
          )}

          {finalOwner && c.id && !isDeleted && (
            <>
              <button
                onClick={() => avatarInputRef.current?.click()}
                disabled={uploadingAvatar}
                className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity flex flex-col items-center justify-center text-white text-[9px] font-bold uppercase tracking-wider"
              >
                {uploadingAvatar ? (
                  <span className="loading loading-spinner loading-xs" />
                ) : (
                  <>
                    <Camera size={16} className="mb-0.5" />
                    <span>Change</span>
                  </>
                )}
              </button>
              <input
                type="file"
                ref={avatarInputRef}
                onChange={(e) => handleUpload(e, 'avatar')}
                accept="image/jpeg,image/png,image/webp"
                className="hidden"
              />
            </>
          )}
        </div>

        {/* Action Button */}
        <div className="flex justify-end pt-3 h-9">
          {finalOwner ? null : (
            <div className="flex items-center gap-1.5">
              <button
                className={`btn btn-xs h-7 min-h-[1.75rem] px-3 rounded-lg font-bold text-[11px] flex items-center gap-1 cursor-pointer transition-all ${
                  finalMember
                    ? 'bg-rose-500/10 text-rose-500 border border-rose-500/20 hover:bg-rose-500/20 hover:border-rose-500/30 hover:text-rose-500 dark:text-rose-400 dark:bg-rose-500/10 dark:hover:bg-rose-500/20 dark:border-rose-500/25 active:scale-95'
                    : finalPending
                      ? 'bg-amber-400 text-black border-none hover:bg-amber-500 active:scale-95 shadow-xs'
                      : isSecret
                        ? 'btn-disabled opacity-50'
                        : String(c.privacy).toUpperCase() === 'PRIVATE'
                          ? 'bg-blue-600 hover:bg-blue-700 text-white border-none active:scale-95 shadow-xs'
                          : 'bg-blue-700 text-white border-none hover:bg-blue-800 active:scale-95 shadow-xs'
                }`}
                onClick={onJoinClick}
                disabled={acting || isSecret}
              >
                {acting ? (
                  <span className="loading loading-spinner loading-xs" />
                ) : finalMember ? (
                  <span>Leave</span>
                ) : finalPending ? (
                  <>
                    <Clock size={11} className="text-black" />
                    <span>Pending</span>
                  </>
                ) : isSecret ? (
                  'Invite Only'
                ) : String(c.privacy).toUpperCase() === 'PRIVATE' ? (
                  'Request to Join'
                ) : (
                  'Join Community'
                )}
              </button>
            </div>
          )}
        </div>

        <div className="mt-2 text-left">
          <h1 className="text-2xl font-bold notranslate">
            {decodeHTML(c.name || 'Community')}
          </h1>
          <p className="mt-1 text-sm opacity-80 break-words leading-relaxed max-w-2xl line-clamp-2">
            {decodeHTML(c.description || 'No description provided.')}
          </p>

          <div className="mt-4 flex items-center gap-4 text-sm font-medium opacity-60">
            <span className="flex items-center gap-1.5">
              <Users size={16} />
              {(c.memberCount || 0).toLocaleString()} members
            </span>
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-amber-500/25 dark:border-amber-400/30 bg-amber-500/10 dark:bg-amber-400/10 px-2.5 py-1 text-[11px] font-black text-amber-700 dark:text-amber-400">
              <Trophy size={12} /> {rankLabel}
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/25 dark:border-emerald-400/30 bg-emerald-500/10 dark:bg-emerald-400/10 px-2.5 py-1 text-[11px] font-bold text-emerald-700 dark:text-emerald-400">
              <Activity size={12} /> {momentumScore} momentum
            </span>
          </div>
        </div>
      </div>
      {editorImageSrc && (
        <ImageEditorModal
          isOpen={editorOpen}
          cropShape={editorType === 'cover' ? 'rect' : 'circle'}
          title={
            editorType === 'cover' ? 'Edit Cover Photo' : 'Edit Avatar Photo'
          }
          onClose={() => {
            setEditorOpen(false);
            setEditorImageSrc(null);
            if (avatarInputRef.current) avatarInputRef.current.value = '';
            if (coverInputRef.current) coverInputRef.current.value = '';
          }}
          imageSrc={editorImageSrc}
          onSave={handleEditorSave}
        />
      )}
    </div>
  );
};

export default CommunityHeader;
