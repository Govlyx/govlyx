import type { AnyPost, GovernmentPost } from '../components/post/PostCard';

export function decodeHTML(str: string): string {
  if (!str) return '';
  if (typeof document === 'undefined') {
    return str
      .replace(/&amp;/g, '&')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'");
  }
  const txt = document.createElement('textarea');
  txt.innerHTML = str;
  return txt.value;
}

export function resolveMediaUrl(
  path?: string | null,
  type: 'posts' | 'social-posts' | 'community-chat' = 'posts',
): string {
  if (!path) return '';

  // If it's already a full URL (Cloudinary, S3, etc.)
  if (path.startsWith('http')) return path;

  // If it starts with a slash, it's an absolute path from the root
  if (path.startsWith('/')) return path;

  // Special case: "hasImage: false" in some DTOs
  if (path === 'false') return '';

  // Fallback for legacy filenames that are strictly just IDs/filenames
  // We point them to the proper /uploads/ endpoint, but we don't automatically
  // include the prefix if the path already seems resolved.
  return `/uploads/${type === 'posts' ? 'posts' : type === 'social-posts' ? 'social-posts' : 'community-chat'}/${path}`;
}

export function formatTimeAgo(dateVal: any): string {
  if (!dateVal) return 'just now';
  const date =
    typeof dateVal === 'string' || typeof dateVal === 'number'
      ? new Date(dateVal)
      : dateVal;
  if (isNaN(date.getTime())) return 'just now';
  const diffMs = Date.now() - date.getTime();
  const diffMins = Math.floor(diffMs / 60_000);
  const diffHrs = Math.floor(diffMs / 3_600_000);
  const diffDays = Math.floor(diffMs / 86_400_000);
  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHrs < 24) return `${diffHrs}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return `${Math.floor(diffDays / 7)}w ago`;
}

export function formatTimeLeft(expiresAtVal: any): string | null {
  if (!expiresAtVal) return 'Always';
  const expiresAt =
    typeof expiresAtVal === 'string' || typeof expiresAtVal === 'number'
      ? new Date(expiresAtVal)
      : expiresAtVal;
  if (isNaN(expiresAt.getTime())) return 'Always';

  const diffMs = expiresAt.getTime() - Date.now();
  if (diffMs <= 0) return 'Ended';

  const diffMins = Math.floor(diffMs / 60_000);
  const diffHrs = Math.floor(diffMs / 3_600_000);
  const diffDays = Math.floor(diffMs / 86_400_000);

  if (diffDays > 0) {
    return `${diffDays}d left`;
  }
  if (diffHrs > 0) {
    return `${diffHrs}h left`;
  }
  if (diffMins > 0) {
    return `${diffMins}m left`;
  }
  return 'less than a minute left';
}

function cleanEmail(val: string): string {
  if (!val) return '';
  if (val.includes('@')) {
    return val.split('@')[0];
  }
  return val;
}

export function normalizePassTier(
  value: any,
): 'GOVLYX_FREE' | 'GOVLYX_PRO' | 'GOVLYX_VIP' | undefined {
  if (!value || typeof value !== 'string') return undefined;
  const normalized = value.trim().toUpperCase();
  if (normalized === 'GOVLYX_VIP' || normalized === 'VIP') return 'GOVLYX_VIP';
  if (normalized === 'GOVLYX_PRO' || normalized === 'PRO') return 'GOVLYX_PRO';
  if (normalized === 'GOVLYX_FREE' || normalized === 'FREE')
    return 'GOVLYX_FREE';
  return undefined;
}

export function toPostCardPost(dto: any): AnyPost {
  if (!dto) return {} as AnyPost;

  // ── Normalize author fields ──────────────────────────────────────────────
  // AuthorDto fields: { username, actualUsername, profileImage, roleName, isActive }
  // PostCard AuthorRow expects top-level: username, userDisplayName, userProfileImage
  const rawAuthorUsername =
    dto.authorActualUsername ??
    dto.author?.actualUsername ??
    dto.user?.actualUsername ??
    dto.actualUsername ??
    dto.authorUsername ??
    dto.author?.username ??
    dto.user?.username ??
    dto.username ??
    '';

  const authorUsername = cleanEmail(rawAuthorUsername);

  const authorImage = resolveMediaUrl(
    dto.userProfileImage ??
      dto.author?.profileImage ??
      dto.author?.profileImageUrl ??
      dto.user?.profileImage ??
      dto.authorProfileImage ??
      null,
  );
  const timeAgo = dto.timeAgo ?? formatTimeAgo(dto.createdAt);

  const content = dto.content ? decodeHTML(dto.content) : '';
  const translatedContent = dto.translatedContent
    ? decodeHTML(dto.translatedContent)
    : undefined;
  const authorBillingTier = normalizePassTier(
    dto.authorBillingTier ??
      dto.userBillingTier ??
      dto.billingTier ??
      dto.currentTier ??
      dto.subscriptionTier ??
      dto.planTier ??
      dto.passTier ??
      dto.authorTier ??
      dto.userTier ??
      dto.tier ??
      dto.author?.currentTier ??
      dto.author?.billingTier ??
      dto.author?.subscriptionTier ??
      dto.author?.planTier ??
      dto.author?.passTier ??
      dto.author?.tier ??
      dto.user?.currentTier ??
      dto.user?.billingTier ??
      dto.user?.subscriptionTier ??
      dto.user?.planTier ??
      dto.user?.passTier ??
      dto.user?.tier,
  );

  const normalized = {
    ...dto,
    content,
    translatedContent,
    id:
      dto.id ??
      dto.socialPostId ??
      (dto.poll ? dto.poll.socialPostId : undefined),
    username: authorUsername,
    userDisplayName: cleanEmail(dto.userDisplayName ?? authorUsername), // AuthorDto has no displayName
    userProfileImage: authorImage,
    timeAgo,
    contentHidden:
      dto.contentHidden || dto.isFlagged || dto.status === 'FLAGGED' || false,
    hiddenReason:
      dto.hiddenReason || dto.flagReason || 'Violates community guidelines',
    isLikedByCurrentUser:
      dto.isLikedByMe ?? dto.likedByMe ?? dto.isLikedByCurrentUser ?? false,
    isSavedByCurrentUser:
      dto.isSavedByMe ??
      dto.savedByMe ??
      dto.isSavedByCurrentUser ??
      dto.isSaved ??
      false,
    authorBillingTier,
    authorRole: dto.authorCommunityRole ?? dto.authorRole ?? undefined,
    isMember:
      dto.isMember ??
      dto.isJoined ??
      dto.member ??
      dto.community?.isMember ??
      dto.community?.member ??
      false,
    communityId: dto.communityId ?? dto.community?.id,
    communityName: dto.communityName ?? dto.community?.name,
    communitySlug: dto.communitySlug ?? dto.community?.slug,
    communityAvatar: dto.communityAvatar ?? dto.community?.avatarUrl,
    commentCount:
      typeof dto.commentCount === 'number'
        ? dto.commentCount
        : typeof dto.commentCount === 'string' &&
            !isNaN(Number(dto.commentCount))
          ? Number(dto.commentCount)
          : (dto.commentsCount ?? dto.comments ?? 0),
    shareCount:
      typeof dto.shareCount === 'number'
        ? dto.shareCount
        : typeof dto.shareCount === 'string' && !isNaN(Number(dto.shareCount))
          ? Number(dto.shareCount)
          : (dto.sharesCount ?? dto.shares ?? 0),
    likeCount:
      typeof dto.likeCount === 'number'
        ? dto.likeCount
        : typeof dto.likeCount === 'string' && !isNaN(Number(dto.likeCount))
          ? Number(dto.likeCount)
          : (dto.likesCount ?? dto.likes ?? dto.upvotes ?? 0),
    dislikeCount:
      typeof dto.dislikeCount === 'number'
        ? dto.dislikeCount
        : typeof dto.dislikeCount === 'string' &&
            !isNaN(Number(dto.dislikeCount))
          ? Number(dto.dislikeCount)
          : (dto.dislikesCount ?? dto.dislikes ?? dto.downvotes ?? 0),
  };

  // If it's explicitly a government post or is marked as a government broadcast
  if (dto.variant === 'government' || dto.isGovernmentBroadcast === true) {
    return {
      ...normalized,
      variant: 'government',
      department:
        dto.department ?? normalized.userDisplayName ?? normalized.username,
      isGovernmentBroadcast: true,
    } as GovernmentPost;
  }

  // If it's an issue post (has target pincode, explicit variant, or unique PostResponse fields)
  if (
    dto.variant === 'issue' ||
    dto.isResolved !== undefined ||
    dto.canBeResolved !== undefined ||
    dto.targetPincodes ||
    dto.issueType
  ) {
    return {
      ...normalized,
      variant: 'issue',
    } as AnyPost;
  }

  // Handle Polls — poll data lives in dto.poll (PollSummaryDto)
  if (dto.variant === 'poll' || dto.isPoll === true) {
    const poll = dto.poll || {};
    const questionText = poll.question ?? dto.question ?? dto.content ?? '';
    return {
      ...normalized,
      variant: 'poll',
      id:
        normalized.id ??
        poll.socialPostId ??
        poll.pollId ??
        dto.pollId ??
        dto.id,
      content: normalized.content || decodeHTML(questionText),
      pollId: poll.pollId ?? dto.pollId ?? dto.id,
      question: decodeHTML(questionText),
      options: (poll.options ?? dto.options ?? []).map((o: any) => ({
        id: o.optionId ?? o.id,
        optionText: decodeHTML(o.optionText ?? ''),
        voteCount: o.voteCount ?? 0,
        percentage: o.votePercentage ?? o.percentage ?? 0,
      })),
      totalVotes: poll.totalVotes ?? dto.totalVotes ?? 0,
      allowMultipleVotes:
        poll.allowMultipleVotes ?? dto.allowMultipleVotes ?? false,
      isExpired:
        poll.expired ?? poll.isExpired ?? dto.expired ?? dto.isExpired ?? false,
      userHasVoted: poll.userHasVoted ?? dto.userHasVoted ?? false,
      votedOptionIds:
        poll.userVotedOptionIds ??
        poll.votedOptionIds ??
        dto.userVotedOptionIds ??
        dto.votedOptionIds ??
        [],
      showResults:
        poll.showResults ??
        (poll.userHasVoted || poll.expired) ??
        dto.showResults ??
        false,
      timeLeft:
        poll.timeLeft ??
        dto.timeLeft ??
        formatTimeLeft(poll.expiresAt ?? dto.expiresAt),
      expiresAt: poll.expiresAt ?? dto.expiresAt ?? null,
    } as any;
  }

  // Default to social (includes community posts)
  return { ...normalized, variant: dto.variant ?? 'social' } as AnyPost;
}
