package com.govlyx.AI.dto;


import com.govlyx.AI.model.Community;
import com.govlyx.AI.model.CommunityMember;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;

public final class CommunityDto {

    private CommunityDto() {}

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateCommunityRequest {

        @NotBlank(message = "Community name is required")
        @Size(min = 3, max = 100, message = "Name must be 3–100 characters")
        private String name;

        @Size(max = 1000, message = "Description cannot exceed 1000 characters")
        private String description;

        private String category;
        private String tags;
        private Community.CommunityPrivacy privacy;

        @Builder.Default
        private Boolean locationRestricted  = false;
        @Builder.Default
        private Boolean allowMemberPosts    = true;
        @Builder.Default
        private Boolean requirePostApproval = false;

        /**
         * FIX #10: This field was missing — createCommunity() always defaulted
         * feedEligible to (privacy == PUBLIC), ignoring the caller's intent.
         *
         * When null: feedEligible defaults to true for PUBLIC communities, false otherwise
         *            (preserves existing default behaviour — no breaking change).
         * When provided: the explicit value takes precedence, subject to the rule
         *                that only PUBLIC communities may set feedEligible=true.
         */
        private Boolean feedEligible;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateCommunityRequest {
        /**
         * F7: Added name field — previously missing, causing name changes from the
         * AdminPanel settings form to be silently dropped by the backend.
         * Validation mirrors CreateCommunityRequest: 3–100 characters when provided.
         */
        @Size(min = 3, max = 100, message = "Name must be 3–100 characters")
        private String   name;

        @Size(max = 1000) private String description;
        private String   category;
        private String   tags;
        private String   coverImageUrl;
        private String   avatarUrl;
        private Community.CommunityPrivacy privacy;
        private Boolean  locationRestricted;
        private Boolean  requirePostApproval;

        /**
         * Owner opt-in / opt-out from main-feed surfacing.
         *
         * true  → posts automatically surface in the main feed when engagement_score
         *         crosses the location-tier threshold. Like Reddit's r/all inclusion.
         *
         * false → community posts stay inside the community only. Like a WhatsApp group.
         *
         * Only settable for PUBLIC communities (non-null + community.privacy != PUBLIC → rejected).
         */
        private Boolean feedEligible;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class JoinCommunityRequest {
        @Size(max = 500, message = "Message cannot exceed 500 characters")
        private String message;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ReviewJoinRequest {
        private boolean approve;
        @Size(max = 500) private String rejectionReason;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpdateMemberRoleRequest {
            @NotNull(message = "New role cannot be null")
        private CommunityMember.MemberRole newRole;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BanMemberRequest {
        @Size(max = 500) private String reason;
    }

    // =========================================================================
    // RESPONSE DTOs
    // =========================================================================

    /**
     * Lightweight card shown in discovery / search results / my-communities list.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CommunitySummaryResponse {
        private Long    id;
        private String  name;
        private String  slug;
        private String  description;       // truncated to 200 chars
        private String  category;
        private String  avatarUrl;
        private String  coverImageUrl;
        private String  privacy;
        private String  locationName;
        private Integer memberCount;
        private Integer postCount;
        private boolean isMember;
        private boolean isOwner;
        private boolean isModerator;
        private Date    createdAt;
        // Feed surfacing (v3)
        private boolean feedEligible;
        private Integer feedSurfaceCount;
        // Health
        private Double  healthScore;
        private String  healthTier;
        private String  healthTierEmoji;
        // Hyperlocal
        private boolean isSystemSeeded;
        private String  wardName;
        // Status & Deletion
        private String  status;
        private boolean isDeleted;               // true when status is DELETED or PERMANENTLY_DELETED
        private Date    scheduledDeletionDate;   // renamed from scheduledDeletionAt to match frontend
    }

    /**
     * Full detail for the community profile / settings page.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CommunityDetailResponse {
        private Long    id;
        private String  name;
        private String  slug;
        private String  description;
        private String  category;
        private String  tags;
        private String  avatarUrl;
        private String  coverImageUrl;
        private String  privacy;
        private String  status;
        private boolean isDeleted;               // true when status is DELETED or PERMANENTLY_DELETED
        private Date    scheduledDeletionDate;   // renamed from scheduledDeletionAt to match frontend
        private String  locationName;
        private boolean locationRestricted;
        private boolean allowMemberPosts;
        private boolean requirePostApproval;
        private boolean allowAnonymousPosts; // always true — shown for client info
        private Integer memberCount;
        private Integer postCount;
        // Feed surfacing (v3)
        private boolean feedEligible;
        private Integer feedSurfaceCount;
        private Date    createdAt;
        private Date    lastActiveAt;
        // Current-user context
        private boolean isMember;
        private boolean isOwner;
        private boolean isModerator;
        private String  currentUserRole;
        private boolean hasPendingRequest;
        // Anonymous owner card
        private UserBriefResponse owner;
        // Health
        private Double  healthScore;
        private String  healthTier;
        private String  healthTierEmoji;
        // Hyperlocal
        private boolean isSystemSeeded;
        private String  wardName;
    }

    /**
     * Embedded in community responses.
     * ANONYMITY RULE: only system-generated username + profileImage. No email, no pincode.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UserBriefResponse {
        private Long   id;
        private String username;
        private String profileImage;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CommunityMemberResponse {
        private Long    id;
        private Long    userId;
        private String  username;
        private String  profileImage;
        private String  memberRole;
        private boolean isMuted;
        private boolean isBanned;
        private Date    joinedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class JoinRequestResponse {
        private Long   id;
        private Long   userId;
        private String username;
        private String profileImage;
        private String message;
        private String status;
        private Date   requestedAt;
        private Date   reviewedAt;
    }

    /**
     * Attribution badge embedded INSIDE SocialPostDto.
     *
     * When SocialPost.isCommunityPost() == true, the SocialPost mapper should populate
     * this object from post.getCommunity() — the community entity is already loaded so
     * no extra DB call is made.
     *
     * Frontend renders this as: "📌 From [communityName]  [healthTierEmoji]"
     *
     * Also shows engagement progress:
     *   currentFeedReach = "LOCAL"        → post visible in same-pincode feeds
     *   nextThreshold    = 15             → needs 15 total score to reach DISTRICT
     *   (null nextThreshold = already NATIONAL)
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CommunityPostAttributionInfo {
        private Long    communityId;
        private String  communityName;
        private String  communitySlug;
        private String  communityAvatarUrl;
        private String  healthTier;
        private String  healthTierEmoji;
        private boolean isSystemSeeded;
        private String  wardName;
        // Feed reach progress (shown as a small indicator on the post card)
        private Double  currentEngagementScore;
        private String  currentFeedReach;  // "LOCAL" / "DISTRICT" / "STATE" / "NATIONAL" / "COMMUNITY_ONLY"
        private Integer nextThreshold;     // engagement score needed for next tier (null = already NATIONAL)
        private String  nextFeedReach;
    }

    /**
     * Owner-only health insights dashboard.
     * feedSurfaceCount replaces sharedToFeedCount from v2.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HealthInsightResponse {
        private Long    communityId;
        private String  communityName;
        private Double  healthScore;
        private String  healthTier;
        private String  healthTierEmoji;
        // Component scores (0–100 each)
        private Double  postFreqScore;
        private Double  memberGrowthScore;
        private Double  engagementScore;
        private Double  modActivityScore;
        private Double  retentionScore;
        // Raw 7-day metrics
        private Integer postsLast7d;
        private Integer newMembersLast7d;
        private Integer activePostersLast7d;
        private Integer totalMembers;
        private Integer totalPosts;
        // Feed surfacing metrics (v3)
        private Integer feedSurfaceCount;
        private Integer postsCurrentlyInFeed;  // posts with score >= LOCAL threshold
        private String  feedReachLevel;         // highest tier reached by any post ("LOCAL" / ... / "NATIONAL")
        // Trend & suggestion
        private String  weeklyPostTrend;   // "↑" / "→" / "↓"
        private String  suggestion;        // actionable tip based on weakest component
        private Date    scoreUpdatedAt;
    }

    /**
     * Post card returned from community feed endpoints.
     *
     * <p>Deliberately avoids exposing the raw {@code SocialPost} entity so that
     * internal DB columns (e.g. {@code feedEligible}, denormalised community fields,
     * JPA relations) are never serialised over the wire. Only fields the frontend
     * actually needs are projected here.</p>
     *
     * <p>Visibility rules enforced by the service layer:
     * <ul>
     *   <li>PUBLIC community  — anyone can read</li>
     *   <li>PRIVATE community — members only</li>
     *   <li>SECRET  community — members only</li>
     * </ul>
     * </p>
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CommunityPostResponse {

        // ── Post identity ─────────────────────────────────────────────────────
        private Long    id;
        private String  content;
        private String  imageUrl;
        private List<String> mediaUrls;
        private String  postType;          // "TEXT" | "IMAGE" | "ANONYMOUS"
        private boolean isAnonymous;

        // ── Author (null when isAnonymous=true) ───────────────────────────────
        private Long    authorId;
        private String  authorUsername;
        private String  authorProfileImage;

        // ── Engagement counters ───────────────────────────────────────────────
        private int     likeCount;
        private int     commentCount;
        private int     shareCount;

        // ── Viewer-context flags ──────────────────────────────────────────────
        /** Whether the authenticated caller has liked this post. */
        @JsonProperty("isLikedByMe")
        private boolean isLikedByMe;
        /** Whether the authenticated caller has saved this post. */
        @JsonProperty("isSavedByMe")
        private boolean isSavedByMe;
        /** Whether this post is pending moderator approval. */
        private boolean isPendingApproval;
        /** Whether the caller is the author of this post. */
        private boolean isMyPost;

        // ── Feed reach (shown as a small badge on the card) ───────────────────
        /** Highest feed tier this post has reached: LOCAL/DISTRICT/STATE/NATIONAL/COMMUNITY_ONLY */
        private String  feedReach;

        // ── Community attribution (used when post appears outside community) ──
        private CommunityPostAttributionInfo community;

        // ── Poll Data ─────────────────────────────────────────────────────────
        private com.govlyx.AI.dto.SocialPostDto.PollSummaryDto poll;

        // ── Author role tag (null when author is a plain MEMBER) ──────────────────
        /**
         * Role badge shown next to the author name on the post card.
         * Values: "ADMIN" | "MODERATOR" | null (plain MEMBER -> no badge)
         * Always sourced from the community_members table (DB is source of truth).
         */
        private String authorCommunityRole;

        // ── Timestamps ────────────────────────────────────────────────────────────
        private Date    createdAt;
        private Date    updatedAt;
    }

    /**
     * Generic cursor-paginated response — mirrors PaginatedResponse<T> structure
     * used across the rest of the Govlyx API.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CommunityPageResponse<T> {
        private List<T>  content;
        private boolean  hasMore;
        private Long     nextCursor;
        private int      limit;
    }
    public class CommunityInviteDto {

        // ── REQUEST: Admin sends an invite ───────────────────────────────────────

        /**
         * POST /api/communities/{id}/invites
         *
         * Two modes:
         *  - inviteeUsername present  → targeted invite to that specific user
         *  - inviteeUsername absent   → generate a shareable multi-use link
         */
        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class SendInviteRequest {

            /** ID of the person to invite. Prioritized over username. */
            private Long inviteeId;

            /** Username or Display Name of the person to invite. Fallback for link-only invites. */
            private String inviteeUsername;

            /** Optional personal message (max 300 chars). */
            private String message;
        }

        // ── RESPONSE: Returned after creating an invite ───────────────────────────

        /**
         * Returned by POST /api/communities/{id}/invites
         * and by GET /api/communities/{id}/invites (list items).
         */
        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class InviteResponse {

            private Long   id;
            private String token;

            /**
             * Full frontend URL the invitee clicks to join.
             * e.g. https://Govlyx.in/invite/a3f9b2c1d7e8f0a1
             */
            private String inviteLink;

            /** null for link-only invites */
            private String  inviteeUsername;
            private String  inviteeProfileImage;

            private String  inviterUsername;
            private String  message;
            private String  status;      // PENDING | ACCEPTED | REVOKED | EXPIRED
            private boolean singleUse;
            private int     useCount;
            private Date    createdAt;
            private Date    expiresAt;
            private Date    actionedAt;
        }

        // ── RESPONSE: Public preview (no auth needed) ─────────────────────────────

        /**
         * GET /api/communities/invites/preview/{token}
         *
         * Called by the AcceptInvitePage BEFORE the user is logged in,
         * to show community name/description/member count so they know what they're joining.
         * Does NOT expose sensitive data (no inviter details unless you want them).
         */
        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class InvitePreviewResponse {

            private String  communityName;
            private String  communitySlug;
            private String  communityDescription;
            private String  communityPrivacy;   // "PRIVATE" or "SECRET"
            private int     memberCount;

            /** Username of person who sent the invite (shown as "Invited by @xyz") */
            private String  inviterUsername;

            /** Personal message from the inviter, if any */
            private String  message;

            private Date    expiresAt;

            /**
             * Whether this invite is still usable.
             * false = expired or already used (single-use already accepted / revoked).
             */
            private boolean valid;
        }

        // ── RESPONSE: Returned after accepting an invite ──────────────────────────

        /**
         * POST /api/communities/invites/accept/{token}
         */
        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class AcceptInviteResponse {

            private Long    communityId;
            private String  communityName;
            private String  communitySlug;
            private boolean joined;          // always true on success
            private String  message;
        }
    }

}
