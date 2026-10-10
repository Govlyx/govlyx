package com.JanSahayak.AI.dto;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Poll;
import com.JanSahayak.AI.model.PollOption;
import com.JanSahayak.AI.model.SocialPost;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPostDto implements Serializable {

    // Basic Info
    private Long id;
    private com.JanSahayak.AI.enums.SocialPostCategory category;
    private String content;
    private String translatedContent;
    private Boolean isTranslated;
    private Date createdAt;
    private Date updatedAt;

    // Author Info
    private AuthorDto author;

    // Media
    private List<String> mediaUrls;
    private Integer mediaCount;
    private Boolean hasMedia;

    // Location
    private String pincode;
    private String locationName;
    private Boolean showLocation;

    // Hashtags & Mentions
    private List<String> hashtags;
    private Integer hashtagCount;
    @JsonIgnore private List<Long> mentionedUserIds;
    @JsonIgnore private Integer mentionCount;

    // Engagement Metrics
    private Integer likeCount;
    private Integer commentCount;
    private Integer shareCount;
    @JsonIgnore private Integer saveCount;
    private Integer viewCount;
    @JsonIgnore private Integer totalEngagementCount;

    // Recommendation Scores (internal — not used by frontend)
    @JsonIgnore private Double engagementScore;
    @JsonIgnore private Double viralityScore;
    @JsonIgnore private Double qualityScore;

    // Viral Status (internal — not used by frontend)
    @JsonIgnore private Boolean isViral;
    @JsonIgnore private String viralTier;
    @JsonIgnore private Integer expansionLevel;
    @JsonIgnore private String viralStatusDescription;
    @JsonIgnore private Date viralReachedAt;

    // Status
    private PostStatus status;
    private Boolean allowComments;
    private Boolean isFlagged;
    @JsonIgnore private Integer reportCount;

    // User Interaction Flags (set per request)
    private Boolean isLikedByCurrentUser;
    private Boolean isSavedByCurrentUser;
    private Boolean isViewedByCurrentUser;
    private Boolean canDelete;

    // Timestamps (internal — not used by frontend)
    @JsonIgnore private Date lastEngagedAt;
    @JsonIgnore private Long ageInHours;

    // ── Post variant & poll payload ────────────────────────────────────────────
    // variant tells the frontend PostCard HOW to render this post.
    // Possible values: "social" | "community" | "poll"
    @Builder.Default
    private String variant = "social";

    // Non-null only when variant = "poll"
    private PollSummaryDto poll;

    // ── Community context fields ──────────────────────────────────────────────
    // Populated when variant = "community" (or poll inside a community).
    // communityId   — the community this post belongs to
    // communityName — display name of the community
    // communityAvatar — avatar URL of the community (may be null)
    // communityMemberCount — total members (denormalized Integer, shown as a string on the card)
    // isMember      — whether the requesting user is an active member of this community
    // authorRole    — the post author's role inside the community ("MEMBER"/"MODERATOR"/"ADMIN")
    private Long    communityId;
    private String  communityName;
    private String  communitySlug;
    private String  communityAvatar;
    private Integer communityMemberCount;
    private Boolean isMember;     // user-specific; set in the service layer
    private String  authorRole;   // user-specific; set in the service layer

    // ── Nested DTO ───────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PollSummaryDto implements Serializable {

        private Long    pollId;
        private String  question;
        private List<PollOptionDto> options;
        private int     totalVotes;
        private boolean allowMultipleVotes;
        private boolean isExpired;
        private String  expiresAt;       // ISO-8601 string or null
        private String  timeLeft;        // e.g. "2d left", "5h left", null if no expiry
        private boolean userHasVoted;
        private List<Long> votedOptionIds;
        private boolean showResults;     // driven by Poll.shouldShowResults()

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PollOptionDto implements Serializable {
            private Long   id;
            private String optionText;
            private int    voteCount;
            private int    percentage;   // 0–100, 0 when results are hidden
        }
    }

    // =========================================================================
    // FACTORY METHODS
    // =========================================================================

    /**
     * Basic conversion — no interactions, no poll.  variant defaults to "social".
     */
    public static SocialPostDto fromSocialPost(SocialPost socialPost) {
        return fromSocialPost(socialPost, null);
    }

    public static SocialPostDto fromSocialPost(SocialPost socialPost, String roleName) {
        if (socialPost == null) return null;

        return SocialPostDto.builder()
                .id(socialPost.getId())
                .category(socialPost.getCategory())
                .content(socialPost.getContent())
                .createdAt(socialPost.getCreatedAt())
                .updatedAt(socialPost.getUpdatedAt())
                .author(AuthorDto.fromUser(socialPost.getUser(), roleName))
                .mediaUrls(socialPost.getMediaUrlsList())
                .mediaCount(socialPost.getMediaCount())
                .hasMedia(socialPost.hasMedia())
                .pincode(socialPost.getPincode())
                .locationName(socialPost.getLocationName())
                .showLocation(socialPost.getShowLocation())
                .hashtags(socialPost.getHashtagsList())
                .hashtagCount(socialPost.getHashtagCount())
                .mentionedUserIds(socialPost.getMentionedUserIdsList())
                .mentionCount(socialPost.getMentionCount())
                .likeCount(socialPost.getLikeCount())
                .commentCount(socialPost.getCommentCount())
                .shareCount(socialPost.getShareCount())
                .saveCount(socialPost.getSaveCount())
                .viewCount(socialPost.getViewCount())
                .totalEngagementCount(socialPost.getTotalEngagementCount())
                .engagementScore(socialPost.getEngagementScore())
                .viralityScore(socialPost.getViralityScore())
                .qualityScore(socialPost.getQualityScore())
                .isViral(socialPost.getIsViral())
                .viralTier(socialPost.getViralTier())
                .expansionLevel(socialPost.getExpansionLevel())
                .viralStatusDescription(socialPost.getViralStatusDescription())
                .viralReachedAt(socialPost.getViralReachedAt())
                .status(socialPost.getStatus())
                .allowComments(socialPost.getAllowComments())
                .isFlagged(socialPost.getIsFlagged())
                .reportCount(socialPost.getReportCount())
                .lastEngagedAt(socialPost.getLastEngagedAt())
                .ageInHours(socialPost.getAgeInHours())
                // variant defaults to "social" via @Builder.Default
                .build();
    }

    /**
     * Conversion with interaction flags only (social / community posts).
     * Keeps the original 3-arg signature so no existing call sites break.
     */
    public static SocialPostDto fromSocialPostWithInteractions(
            SocialPost socialPost,
            Boolean isLiked,
            Boolean isSaved,
            Boolean isViewed) {
        return fromSocialPostWithInteractions(socialPost, isLiked, isSaved, isViewed, null);
    }

    public static SocialPostDto fromSocialPostWithInteractions(
            SocialPost socialPost,
            Boolean isLiked,
            Boolean isSaved,
            Boolean isViewed,
            String roleName) {

        SocialPostDto dto = fromSocialPost(socialPost, roleName);
        if (dto == null) return null;

        dto.setIsLikedByCurrentUser(isLiked);
        dto.setIsSavedByCurrentUser(isSaved);
        dto.setIsViewedByCurrentUser(isViewed);

        // Derive community variant and populate community context fields
        if (socialPost.getCommunityId() != null) {
            dto.setVariant("community");
            dto.setCommunityId(socialPost.getCommunityId());
        }

        return dto;
    }

    /**
     * Enriches an existing DTO with user-specific membership context.
     * Called from the service layer after batch-loading membership records,
     * so PostCard can show the correct Join/Joined button state and author badge.
     *
     * @param isMember   true if the requesting user is an active, non-banned member
     * @param authorRole the post author's role in the community (e.g. "MODERATOR"), or null
     */
    public static SocialPostDto enrichWithMembership(
            SocialPostDto dto,
            boolean isMember,
            String authorRole) {
        if (dto == null) return null;
        dto.setIsMember(isMember);
        dto.setAuthorRole(authorRole);
        return dto;
    }

    /**
     * Conversion with interaction flags + poll data.
     * Called from SocialPostService.convertToDto() when the post has an attached Poll.
     *
     * Pass poll = null to get the same result as the 4-arg overload above.
     */
    public static SocialPostDto fromSocialPostWithInteractions(
            SocialPost socialPost,
            Boolean isLiked,
            Boolean isSaved,
            Boolean isViewed,
            Poll poll,
            boolean userHasVoted,
            List<Long> votedOptionIds) {
        return fromSocialPostWithInteractions(socialPost, isLiked, isSaved, isViewed, poll, userHasVoted, votedOptionIds, null);
    }

    public static SocialPostDto fromSocialPostWithInteractions(
            SocialPost socialPost,
            Boolean isLiked,
            Boolean isSaved,
            Boolean isViewed,
            Poll poll,
            boolean userHasVoted,
            List<Long> votedOptionIds,
            String roleName) {

        // Start from the base conversion
        SocialPostDto dto = fromSocialPostWithInteractions(
                socialPost, isLiked, isSaved, isViewed, roleName);
        if (dto == null) return null;

        if (poll != null) {
            // Override variant — THIS is what the frontend PostCard checks to decide
            // whether to render like/dislike buttons or poll option buttons.
            dto.setVariant("poll");
            dto.setPoll(buildPollSummary(poll, userHasVoted, votedOptionIds));
        }

        return dto;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    public static PollSummaryDto buildPollSummary(
            Poll poll,
            boolean userHasVoted,
            List<Long> votedOptionIds) {

        int     total       = poll.getTotalVotes();
        boolean showResults = poll.shouldShowResults(userHasVoted);
        boolean isExpired   = !poll.isOpenForVoting();

        List<PollSummaryDto.PollOptionDto> optionDtos = poll.getOptions().stream()
                .sorted(Comparator.comparingInt(PollOption::getOptionOrder))
                .map(opt -> PollSummaryDto.PollOptionDto.builder()
                        .id(opt.getId())
                        .optionText(opt.getOptionText())
                        .voteCount(showResults ? opt.getVoteCount() : 0)
                        .percentage(showResults && total > 0
                                ? (int) Math.round((opt.getVoteCount() * 100.0) / total)
                                : 0)
                        .build())
                .collect(Collectors.toList());

        // Human-readable countdown
        String timeLeft = null;
        if (!isExpired && poll.getExpiresAt() != null) {
            long diffMs = poll.getExpiresAt().getTime() - System.currentTimeMillis();
            long days   = diffMs / (1000L * 60 * 60 * 24);
            long hours  = (diffMs % (1000L * 60 * 60 * 24)) / (1000L * 60 * 60);
            timeLeft = days > 0 ? days + "d left" : hours + "h left";
        } else if (!isExpired && poll.getExpiresAt() == null) {
            timeLeft = "Always";
        }

        return PollSummaryDto.builder()
                .pollId(poll.getId())
                .question(poll.getQuestion())
                .options(optionDtos)
                .totalVotes(total)
                .allowMultipleVotes(Boolean.TRUE.equals(poll.getAllowMultipleVotes()))
                .isExpired(isExpired)
                .expiresAt(poll.getExpiresAt() != null
                        ? poll.getExpiresAt().toInstant().toString()
                        : null)
                .timeLeft(timeLeft)
                .userHasVoted(userHasVoted)
                .votedOptionIds(votedOptionIds != null ? votedOptionIds : List.of())
                .showResults(showResults)
                .build();
    }

    // =========================================================================
    // HELPER METHODS (unchanged from original)
    // =========================================================================

    public boolean isTrending() {
        return Boolean.TRUE.equals(isViral) && expansionLevel != null && expansionLevel >= 1;
    }

    public boolean isNationalViral() {
        return "NATIONAL_VIRAL".equals(viralTier);
    }

    public boolean isStateViral() {
        return "STATE_VIRAL".equals(viralTier);
    }

    public boolean isDistrictViral() {
        return "DISTRICT_VIRAL".equals(viralTier);
    }

    public boolean isLocal() {
        return "LOCAL".equals(viralTier);
    }

    public String getViralBadge() {
        if (isNationalViral())  return "🔥 Trending in India";
        if (isStateViral())     return "🔥 Trending in State";
        if (isDistrictViral())  return "🔥 Trending Locally";
        return null;
    }

    public double getEngagementRate() {
        if (viewCount == null || viewCount == 0) return 0.0;
        return ((double) totalEngagementCount / viewCount) * 100;
    }

    public String getTimeAgo() {
        if (createdAt == null) return "Unknown";

        long diffInMillies = new Date().getTime() - createdAt.getTime();
        long diffInMinutes = diffInMillies / (60 * 1000);
        long diffInHours   = diffInMillies / (60 * 60 * 1000);
        long diffInDays    = diffInMillies / (24 * 60 * 60 * 1000);

        if (diffInMinutes < 1)  return "Just now";
        if (diffInMinutes < 60) return diffInMinutes + " minute"  + (diffInMinutes == 1 ? "" : "s") + " ago";
        if (diffInHours   < 24) return diffInHours   + " hour"    + (diffInHours   == 1 ? "" : "s") + " ago";
        if (diffInDays    < 7)  return diffInDays    + " day"     + (diffInDays    == 1 ? "" : "s") + " ago";
        long weeks = diffInDays / 7;
        return weeks + " week" + (weeks == 1 ? "" : "s") + " ago";
    }

    public boolean isRecentPost()  { return ageInHours != null && ageInHours <= 24; }
    public boolean hasLocation()   { return pincode != null && !pincode.isEmpty(); }
    public boolean hasHashtags()   { return hashtags != null && !hashtags.isEmpty(); }
    public boolean hasMentions()   { return mentionedUserIds != null && !mentionedUserIds.isEmpty(); }

    public String getPreviewContent(int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }

    public boolean canBeEdited() {
        return status == PostStatus.ACTIVE && !Boolean.TRUE.equals(isFlagged);
    }

    public boolean canBeCommented() {
        return status == PostStatus.ACTIVE
                && Boolean.TRUE.equals(allowComments)
                && !Boolean.TRUE.equals(isFlagged);
    }

    public boolean isHighQuality()    { return qualityScore != null && qualityScore >= 80.0; }
    public boolean needsModeration()  {
        return Boolean.TRUE.equals(isFlagged)
                || (reportCount != null && reportCount >= 5);
    }
}
