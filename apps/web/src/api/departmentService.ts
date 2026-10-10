import axiosInstance from "./axiosConfig";
import type {
  TaggedPost,
  TaggedPostsPage,
  BroadcastCreateDto,
  BroadcastPost,
  BroadcastStatistics,
  BroadcastAnalytics,
} from "../types/department";

// ─────────────────────────────────────────────────────────────────────────────
// Helper – normalise the backend Map<String,Object> response into TaggedPostsPage
// ─────────────────────────────────────────────────────────────────────────────
function normaliseTaggedPage(raw: unknown): TaggedPostsPage {
  const data = (raw as any)?.data ?? raw ?? {};

  // Backend returns: { posts: PaginatedResponse<PostResponse>, count: N, username, ... }
  // PaginatedResponse has shape: { content: [...], hasMore, nextCursor }
  const postsField = data.posts; // may be a PaginatedResponse object or array
  const rawItems: any[] = Array.isArray(postsField)
    ? postsField
    : postsField?.data ?? postsField?.content ?? postsField?.items ?? data.content ?? data.items ?? [];

  const totalCount: number =
    typeof data.count === "number" ? data.count :
    typeof data.totalCount === "number" ? data.totalCount :
    typeof data.totalElements === "number" ? data.totalElements :
    rawItems.length;

  const hasMore: boolean = postsField?.hasMore ?? postsField?.hasNextPage ?? data.hasMore ?? false;
  const nextCursor: number | null = postsField?.nextCursor ?? postsField?.lastId ?? data.nextCursor ?? null;

  const posts: TaggedPost[] = rawItems.map((p: any): TaggedPost => ({
    id: p.id,
    content: p.content ?? "",
    targetPincode: p.userPincode ?? p.targetPincode,
    issueType: p.issueType,
    mediaUrl: p.mediaUrl,
    citizenId: p.citizenId ?? p.author?.id,
    citizenUsername: p.citizenUsername ?? p.author?.actualUsername ?? p.author?.username ?? p.actualUsername ?? p.username,
    citizenDisplayName: p.citizenDisplayName ?? p.author?.displayName ?? p.userDisplayName,
    citizenProfileImage: p.citizenProfileImage ?? p.author?.profileImage ?? p.userProfileImage,
    username: p.author?.actualUsername ?? p.actualUsername ?? p.username ?? p.citizenUsername ?? p.author?.username,
    userDisplayName: p.userDisplayName ?? p.citizenDisplayName ?? p.author?.displayName,
    userProfileImage: p.userProfileImage ?? p.citizenProfileImage ?? p.author?.profileImage,
    isResolved: p.isResolved ?? false,
    resolutionMessage: p.resolutionMessage ?? null,
    resolvedAt: p.resolvedAt ?? null,
    resolvedByUsername: p.resolvedByUsername ?? null,
    createdAt: p.createdAt,
    timeAgo: p.timeAgo,
    likeCount: p.likeCount ?? 0,
    commentCount: p.commentCount ?? 0,
    shareCount: p.shareCount ?? 0,
    viewCount: p.viewCount ?? 0,
    taggedUsernames: p.taggedUsernames ?? [],
  }));

  return {
    posts,
    totalCount,
    hasMore,
    nextCursor,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Tagged-post queries
// ─────────────────────────────────────────────────────────────────────────────

export async function getActiveTaggedPosts(
  username: string,
  beforeId?: number | null,
  limit = 20
): Promise<TaggedPostsPage> {
  const params: Record<string, unknown> = { limit };
  if (beforeId) params.beforeId = beforeId;
  const res = await axiosInstance.get(
    `/api/user-tagging/users/${encodeURIComponent(username)}/tagged-posts/active`,
    { params }
  );
  return normaliseTaggedPage(res.data);
}

export async function getResolvedTaggedPosts(
  username: string,
  beforeId?: number | null,
  limit = 20
): Promise<TaggedPostsPage> {
  const params: Record<string, unknown> = { limit };
  if (beforeId) params.beforeId = beforeId;
  const res = await axiosInstance.get(
    `/api/user-tagging/users/${encodeURIComponent(username)}/tagged-posts/resolved`,
    { params }
  );
  return normaliseTaggedPage(res.data);
}

// ─────────────────────────────────────────────────────────────────────────────
// Resolution
// ─────────────────────────────────────────────────────────────────────────────

export async function updatePostResolution(
  postId: number,
  isResolved: boolean,
  updateMessage?: string
): Promise<void> {
  const params: Record<string, unknown> = { isResolved };
  if (updateMessage) params.updateMessage = updateMessage;
  await axiosInstance.put(`/api/posts/${postId}/resolution`, null, { params });
}

// ─────────────────────────────────────────────────────────────────────────────
// Broadcasts
// ─────────────────────────────────────────────────────────────────────────────

export async function createBroadcast(
  dto: BroadcastCreateDto,
  mediaFile?: File | null
): Promise<BroadcastPost> {
  const { content, broadcastScope, targetCountry = "IN", targetStates, targetDistricts, targetPincodes } = dto;

  if (mediaFile) {
    // Multipart – use /broadcast/with-media
    const form = new FormData();
    form.append("content", content);
    form.append("media", mediaFile);
    const params: Record<string, unknown> = { broadcastScope, targetCountry };
    if (targetStates?.length) params.targetStates = targetStates.join(",");
    if (targetDistricts?.length) params.targetDistricts = targetDistricts.join(",");
    if (targetPincodes?.length) params.targetPincodes = targetPincodes.join(",");
    const res = await axiosInstance.post("/api/posts/broadcast/with-media", form, {
      params,
      headers: { "Content-Type": "multipart/form-data" },
    });
    return res.data?.data ?? res.data;
  }

  // JSON broadcast
  const params: Record<string, unknown> = { broadcastScope, targetCountry };
  if (targetStates?.length) params.targetStates = targetStates.join(",");
  if (targetDistricts?.length) params.targetDistricts = targetDistricts.join(",");
  if (targetPincodes?.length) params.targetPincodes = targetPincodes.join(",");
  const res = await axiosInstance.post(
    "/api/posts/broadcast",
    { content },
    { params }
  );
  return res.data?.data ?? res.data;
}

// ─────────────────────────────────────────────────────────────────────────────
// Analytics
// ─────────────────────────────────────────────────────────────────────────────

export async function getBroadcastStatistics(): Promise<BroadcastStatistics> {
  const res = await axiosInstance.get("/api/posts/broadcast/statistics");
  return res.data?.data ?? res.data ?? {};
}

export async function getBroadcastAnalytics(days = 30): Promise<BroadcastAnalytics> {
  const res = await axiosInstance.get("/api/posts/broadcast/analytics", { params: { days } });
  return res.data?.data ?? res.data ?? {};
}

// ─────────────────────────────────────────────────────────────────────────────
// Location / Pincode selectors
// ─────────────────────────────────────────────────────────────────────────────

export async function getPincodeStates(): Promise<string[]> {
  const res = await axiosInstance.get("/api/pincode/states");
  return res.data?.data ?? res.data ?? [];
}

export async function getPincodeDistricts(state: string): Promise<string[]> {
  const res = await axiosInstance.get(`/api/pincode/states/${encodeURIComponent(state)}/districts`);
  return res.data?.data ?? res.data ?? [];
}
