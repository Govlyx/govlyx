// ─────────────────────────────────────────────────────────────────────────────
// Department Dashboard – Type Definitions
// ─────────────────────────────────────────────────────────────────────────────

/** A civic post that has tagged this department */
export interface TaggedPost {
  id: number;
  content: string;
  targetPincode?: string;
  issueType?: string;
  mediaUrl?: string;

  // Author (citizen)
  citizenId?: number;
  citizenUsername?: string;
  citizenDisplayName?: string;
  citizenProfileImage?: string;
  username?: string;
  userDisplayName?: string;
  userProfileImage?: string;

  // Resolution state
  isResolved: boolean;
  resolutionMessage?: string | null;
  resolvedAt?: string | null;
  resolvedByUsername?: string | null;
  isReopened?: boolean;
  reopened?: boolean;
  reopenReason?: string | null;
  reopenedReason?: string | null;

  // Timestamps
  createdAt?: string;
  timeAgo?: string;

  // Engagement
  likeCount?: number;
  commentCount?: number;
  shareCount?: number;
  viewCount?: number;

  // Tagged departments list
  taggedUsernames?: string[];
}

/** API paginated response wrapper – matches backend Map<String,Object> shape */
export interface TaggedPostsPage {
  posts: TaggedPost[];
  totalCount: number;
  hasMore: boolean;
  nextCursor: number | null;
}

// ── Broadcast ────────────────────────────────────────────────────────────────

export type BroadcastScope = "COUNTRY" | "STATE" | "DISTRICT" | "AREA";

export interface BroadcastCreateDto {
  content: string;
  broadcastScope: BroadcastScope;
  targetCountry?: string;
  targetStates?: string[];
  targetDistricts?: string[];
  targetPincodes?: string[];
}

export interface BroadcastPost {
  id: number;
  content: string;
  broadcastScope: BroadcastScope;
  targetStates?: string[];
  targetDistricts?: string[];
  targetPincodes?: string[];
  createdAt?: string;
  timeAgo?: string;
  userDisplayName?: string;
  username?: string;
  likeCount?: number;
  commentCount?: number;
}

// ── Analytics ────────────────────────────────────────────────────────────────

export interface BroadcastStatistics {
  total?: number;
  totalBroadcasts?: number;
  country?: number;
  state?: number;
  district?: number;
  area?: number;
  active?: number;
  resolved?: number;
  [key: string]: unknown;
}

export interface BroadcastAnalytics {
  totalBroadcasts?: number;
  totalReach?: number;
  resolutionRate?: number;
  activeIssues?: number;
  resolvedIssues?: number;
  broadcastsByScope?: Record<string, number>;
  [key: string]: unknown;
}

// ── Dashboard state ──────────────────────────────────────────────────────────

export type DashboardTab = "issues" | "broadcasts" | "analytics";
export type IssueFilter = "active" | "resolved";
