package com.Govlyx.AI.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.Govlyx.AI.config.Constant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.Govlyx.AI.enums.PostStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;
import lombok.extern.slf4j.Slf4j;


import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "social_posts", indexes = {
        @Index(name = "idx_social_post_user",            columnList = "user_id"),
        @Index(name = "idx_social_post_actor_token",     columnList = "actor_token"),
        @Index(name = "idx_social_post_actor_created",   columnList = "actor_token, created_at"),
        @Index(name = "idx_social_post_status",          columnList = "status"),
        @Index(name = "idx_social_post_created_at",      columnList = "created_at"),
        @Index(name = "idx_social_post_engagement",      columnList = "engagement_score, created_at"),
        @Index(name = "idx_social_post_trending",        columnList = "viral_tier, status, created_at"),
        @Index(name = "idx_social_post_pincode",         columnList = "pincode, status"),
        @Index(name = "idx_social_post_state_prefix",    columnList = "state_prefix, status"),
        @Index(name = "idx_social_post_district_prefix", columnList = "district_prefix, status"),
        @Index(name = "idx_social_post_viral_expansion", columnList = "viral_tier, expansion_level, status"),
        @Index(name = "idx_social_post_active_trending", columnList = "status, viral_tier, engagement_score, created_at"),
        @Index(name = "idx_social_post_last_engaged",    columnList = "last_engaged_at"),
        @Index(name = "idx_social_post_quality",         columnList = "quality_score, status"),
        // Community feed surfacing index — covers the single-table feed query
        @Index(name = "idx_social_post_community_feed",  columnList = "community_id, community_feed_eligible, community_privacy, engagement_score"),
        // Language-aware feed index — used by language-filtered candidate queries
        // DB migration: ALTER TABLE social_posts ADD INDEX idx_social_post_language (language, status, created_at);
        @Index(name = "idx_social_post_language",        columnList = "language, status, created_at"),
        // Optimised composite indexes for geo and viral candidate pre-fetching waterfall queries
        @Index(name = "idx_social_post_local_feed",      columnList = "pincode, status, is_flagged, quality_score, created_at DESC"),
        @Index(name = "idx_social_post_district_viral",  columnList = "district_prefix, viral_tier, status, is_flagged, virality_score DESC"),
        @Index(name = "idx_social_post_state_viral",     columnList = "state_prefix, viral_tier, status, is_flagged, virality_score DESC"),
        @Index(name = "idx_social_post_national_viral",  columnList = "viral_tier, status, is_flagged, virality_score DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SocialPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", unique = true, length = 36)
    private String idempotencyKey;

    // =========================================================================
    // CORE CONTENT
    // =========================================================================

    @Column(nullable = false, length = 3000)
    @NotBlank(message = "Social post content cannot be empty")
    @Size(max = 3000, message = "Social post content cannot exceed 3000 characters")
    private String content;

    @Column(name = "media_urls", length = 2000)
    private String mediaUrls;

    @Column(name = "media_count", nullable = false)
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer mediaCount = 0;

    // =========================================================================
    // LANGUAGE
    // =========================================================================

    /**
     * BCP-47 language code detected or declared for the post content.
     *
     * Stored as a 10-char column so it can hold codes like "zh-Hans" in future,
     * but in practice all 22 Indian scheduled languages + English fit in ≤5 chars.
     *
     * Canonical values used in this platform:
     *   "hi"  — Hindi       (Devanagari)
     *   "mr"  — Marathi     (Devanagari)
     *   "bn"  — Bengali
     *   "te"  — Telugu
     *   "ta"  — Tamil
     *   "gu"  — Gujarati
     *   "kn"  — Kannada
     *   "ml"  — Malayalam
     *   "pa"  — Punjabi     (Gurmukhi)
     *   "or"  — Odia
     *   "ur"  — Urdu        (Nastaliq / Arabic script)
     *   "as"  — Assamese
     *   "en"  — English     (default when no other script is detected)
     *   "mixed" — post contains meaningful content in 2+ languages
     *
     * HOW IT IS SET:
     *   PostLanguageDetector.detect(post.getContent()) is called inside
     *   SocialPostService.buildSocialPost() before save — so the column is
     *   always populated at creation time.  On update, the detector runs again
     *   when content changes.
     *
     * FEED IMPACT:
     *   HLIGScorer.languageBoost() returns:
     *     2.0  — exact match with user's preferred language
     *     1.3  — "mixed" post (accessible to everyone)
     *     1.0  — English (lingua franca, no penalty)
     *     0.5  — mismatch (still shown for diversity, but ranked lower)
     *
     * DB migration:
     *   ALTER TABLE social_posts
     *     ADD COLUMN language VARCHAR(10) NOT NULL DEFAULT 'en';
     */
    @Column(name = "language", length = 10, nullable = false)
    @Builder.Default
    private String language = "en";

    // =========================================================================
    // STATUS & VISIBILITY
    // =========================================================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PostStatus status = PostStatus.ACTIVE;

    @Column(name = "allow_comments", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    private Boolean allowComments = true;

    // =========================================================================
    // LOCATION (inherited from user's pincode at creation)
    // =========================================================================

    @Column(name = "pincode", length = 6)
    @Size(min = 6, max = 6, message = "Pincode must be exactly 6 digits")
    private String pincode;

    @Column(name = "state_prefix", length = 2)
    private String statePrefix;

    @Column(name = "district_prefix", length = 3)
    private String districtPrefix;

    @Column(name = "location_name", length = 200)
    private String locationName;

    @Column(name = "show_location", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    private Boolean showLocation = false;

    @Column(name = "latitude", precision = 10, scale = 8)
    @JsonIgnore // Prevent GPS stalking
    private java.math.BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 8)
    @JsonIgnore // Prevent GPS stalking
    private java.math.BigDecimal longitude;

    @Column(name = "qa_poster_scope", length = 20)
    private String qaPosterScope;

    @Column(name = "target_urban_cluster_id", length = 100)
    private String targetUrbanClusterId;

    @Column(name = "target_district", length = 100)
    private String targetDistrict;

    // =========================================================================
    // COMMUNITY (Reddit-style feed surfacing)
    // =========================================================================

    /**
     * The community this post belongs to.
     *
     * null  = normal feed post — shown to everyone based on location / viral logic.
     * !null = community post — lives inside the community AND surfaces in the main
     *         feed automatically when engagement_score crosses the location-tier threshold.
     *
     * One post, one row, zero duplication.
     * Added by: V7__community_reddit_style_feed.sql
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id",
            foreignKey = @ForeignKey(name = "fk_sp_community"))
    private Community community;

    /**
     * Denormalized copy of community.privacy ("PUBLIC" / "PRIVATE" / "SECRET").
     *
     * WHY: The main feed query runs thousands of times per minute. Without this field
     * every feed load needs a JOIN to the communities table. With it, the query is
     * single-table and covered by one index.
     *
     * WHEN UPDATED: CommunityService.onPostPublished() calls syncCommunityDenormalizedFields()
     * after each new post. If community privacy changes, CommunityService calls
     * socialPostRepo.syncCommunityDenormalizedFields(communityId, ...) to bulk-update all posts.
     *
     * null for non-community posts.
     */
    @Column(name = "community_privacy", length = 20)
    private String communityPrivacy;

    /**
     * Denormalized copy of community.feedEligible.
     *
     * false → this post NEVER surfaces in the main feed (community is PRIVATE/SECRET or
     *         owner has opted out of feed surfacing).
     * true  → eligible to surface when engagement_score >= location-tier threshold.
     *
     * Always false for non-community posts.
     */
    @Column(name = "community_feed_eligible",
            nullable = false, columnDefinition = "boolean")
    @Builder.Default
    private Boolean communityFeedEligible = false;

    /**
     * Denormalized copy of community.status ("ACTIVE" / "ARCHIVED" / "DELETED").
     * 
     * Used to efficiently filter out posts from dead communities in search and feeds
     * without overwriting the post's original status.
     * 
     * null for non-community posts.
     */
    @Column(name = "community_status", length = 20)
    private String communityStatus;

    // =========================================================================
    // CONTENT CLASSIFICATION
    // =========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    @org.hibernate.annotations.ColumnDefault("'GENERAL'")
    @Builder.Default
    private com.Govlyx.AI.enums.SocialPostCategory category = com.Govlyx.AI.enums.SocialPostCategory.GENERAL;

    @Column(name = "hashtags", length = 1000)
    private String hashtags;

    @Column(name = "hashtag_count", nullable = false)
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer hashtagCount = 0;

    // =========================================================================
    // ENGAGEMENT METRICS (denormalized for performance)
    // =========================================================================

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer commentCount = 0;

    @Column(name = "share_count", nullable = false)
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer shareCount = 0;

    @Column(name = "save_count", nullable = false)
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer saveCount = 0;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer viewCount = 0;

    // DB migration: ALTER TABLE social_posts ADD COLUMN dislike_count INT NOT NULL DEFAULT 0;
    @Column(name = "dislike_count", nullable = false)
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer dislikeCount = 0;

    // =========================================================================
    // RECOMMENDATION SYSTEM SCORES
    // =========================================================================

    @Column(name = "engagement_score", nullable = false, columnDefinition = "numeric(10,4) DEFAULT 0.0000")
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double engagementScore = 0.0;

    @Column(name = "virality_score", nullable = false, columnDefinition = "numeric(10,4) DEFAULT 0.0000")
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double viralityScore = 0.0;

    @Column(name = "quality_score", nullable = false, columnDefinition = "numeric(10,4) DEFAULT 100.0000")
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double qualityScore = 100.0;

    // =========================================================================
    // VIRAL EXPANSION
    // =========================================================================

    @Column(name = "is_viral", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean isViral = false;

    @Column(name = "viral_reached_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date viralReachedAt;

    @Column(name = "expansion_level", nullable = false)
    @Builder.Default
    private Integer expansionLevel = 0; // 0=Local, 1=District, 2=State, 3=National

    @Column(name = "viral_tier", length = 20)
    @Builder.Default
    private String viralTier = "LOCAL"; // LOCAL, DISTRICT_VIRAL, STATE_VIRAL, NATIONAL_VIRAL

    // =========================================================================
    // TEMPORAL
    // =========================================================================

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Date createdAt = new Date();

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    @Column(name = "last_engaged_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastEngagedAt;

    // =========================================================================
    // MODERATION & SAFETY
    // =========================================================================

    @Column(name = "report_count", nullable = false)
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer reportCount = 0;

    @Column(name = "is_flagged", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean isFlagged = false;

    @Column(name = "flagged_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date flaggedAt;

    @Column(name = "flag_reason", length = 500)
    private String flagReason;

    // =========================================================================
    // LEGAL & COPYRIGHT TAKEDOWNS
    // =========================================================================

    @Column(name = "takedown_reason", length = 500)
    private String takedownReason;

    @Column(name = "taken_down_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date takenDownAt;

    @Column(name = "taken_down_by_id")
    private Long takenDownById;

    @Column(name = "retention_expires_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date retentionExpiresAt;

    // =========================================================================
    // USER MENTIONS
    // =========================================================================

    @Column(name = "mentioned_user_ids", length = 1000)
    private String mentionedUserIds;

    @Column(name = "mention_count", nullable = false)
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer mentionCount = 0;

    // =========================================================================
    // RELATIONSHIPS
    // =========================================================================

    @Column(name = "actor_token", length = 70)
    private String actorToken;

    @Column(name = "author_username", length = 100)
    private String authorUsername;

    public String getAuthorDisplayName() {
        return this.authorUsername;
    }

    public void setAuthorDisplayName(String authorDisplayName) {
    }

    @Column(name = "author_profile_image", length = 500)
    private String authorProfileImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true, foreignKey = @ForeignKey(name = "fk_social_post_user"))
    private User user;

    @Column(name = "ip_address", length = 64)
    @JsonIgnore
    private String ipAddress;

    @OneToMany(mappedBy = "socialPost", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "socialPost", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<PostLike> likes = new ArrayList<>();

    @OneToMany(mappedBy = "socialPost", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<PostView> views = new ArrayList<>();

    @OneToMany(mappedBy = "socialPost", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<SavedPost> savedByUsers = new ArrayList<>();

    @OneToMany(mappedBy = "socialPost", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<PostShare> shares = new ArrayList<>();

    /**
     * Inverse side of Poll ↔ SocialPost OneToOne.
     * Poll owns the FK (poll.social_post_id).
     * cascade ALL + orphanRemoval = true guarantees Poll is hard-deleted with its parent.
     */
    @OneToOne(mappedBy = "socialPost", cascade = CascadeType.ALL,
            fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonIgnore
    private Poll poll;

    // =========================================================================
    // VIRAL DETECTION THRESHOLDS
    // =========================================================================

    private static final int    DISTRICT_VIRAL_THRESHOLD  = 50;
    private static final int    STATE_VIRAL_THRESHOLD     = 200;
    private static final int    NATIONAL_VIRAL_THRESHOLD  = 1000;
    private static final double ENGAGEMENT_RATE_THRESHOLD = 10.0;

    // =========================================================================
    // LANGUAGE HELPERS
    // =========================================================================

    /**
     * Returns the canonical language code for this post, defaulting to "en".
     * Callers should prefer this over getLanguage() for null-safety.
     */
    public String safeLanguage() {
        return (language != null && !language.isBlank()) ? language : "en";
    }

    /**
     * True when the post is written in a language other than English.
     * Used by HLIGScorer to decide whether the language multiplier matters.
     */
    @JsonIgnore
    public boolean isNonEnglish() {
        return !"en".equals(safeLanguage());
    }

    /**
     * True when the post contains content in multiple languages.
     * "mixed" posts get a 1.3× language boost (universally accessible).
     */
    @JsonIgnore
    public boolean isMixedLanguage() {
        return "mixed".equals(safeLanguage());
    }

    // =========================================================================
    // LOCATION HELPERS
    // =========================================================================

    public boolean hasLocation() {
        return pincode != null && Constant.isValidIndianPincode(pincode);
    }

    public boolean isFromSameState(User user) {
        if (!hasLocation() || user == null || !user.hasPincode()) return false;
        return this.statePrefix != null && this.statePrefix.equals(user.getStatePrefix());
    }

    public boolean isFromSameDistrict(User user) {
        if (!hasLocation() || user == null || !user.hasPincode()) return false;
        return this.districtPrefix != null && this.districtPrefix.equals(user.getDistrictPrefix());
    }

    public boolean isFromSameArea(User user) {
        if (!hasLocation() || user == null || !user.hasPincode()) return false;
        return this.pincode.equals(user.getPincode());
    }

    public void inheritLocationFromUser(User user) {
        if (user != null) {
            if (user.hasPincode()) {
                this.pincode        = user.getPincode();
                this.statePrefix    = user.getStatePrefix();
                this.districtPrefix = user.getDistrictPrefix();
            }
            if (user.getHomeLatitude() != null && user.getHomeLongitude() != null) {
                this.latitude = user.getHomeLatitude();
                this.longitude = user.getHomeLongitude();
            }
        }
    }

    // =========================================================================
    // COMMUNITY HELPERS
    // =========================================================================

    /** Safe community ID getter — avoids triggering a lazy load. */
    public Long getCommunityId() {
        return community != null ? community.getId() : null;
    }

    @JsonIgnore
    public boolean isCommunityPost() {
        return community != null;
    }

    /**
     * true if this post qualifies to appear in the main public feed.
     *
     * Conditions (all must hold):
     *  1. It is a community post
     *  2. The community is PUBLIC           (checked via denormalized field — no JOIN)
     *  3. Feed surfacing is enabled         (denormalized field — no JOIN)
     *  4. The post itself is active/not flagged
     *
     * NOTE: The engagement_score threshold (LOCAL / DISTRICT / STATE / NATIONAL)
     * is enforced at the QUERY level in SocialPostRepo — not here — because it
     * varies based on the requesting user's location.
     */
    @JsonIgnore
    public boolean isCommunityFeedEligible() {
        return isCommunityPost()
                && "PUBLIC".equals(communityPrivacy)
                && Boolean.TRUE.equals(communityFeedEligible)
                && isEligibleForDisplay();
    }

    /**
     * Called by CommunityService.onPostPublished() immediately after a new post is saved,
     * and by CommunityService via a bulk UPDATE query when community privacy / feedEligible changes.
     *
     * Copies community.privacy and community.feedEligible into the two denormalized columns
     * so the feed query never needs to JOIN the communities table.
     */
    public void syncCommunityDenormalizedFields(Community c) {
        if (c == null) {
            this.communityPrivacy      = null;
            this.communityFeedEligible = false;
            this.communityStatus       = null;
            return;
        }
        this.communityPrivacy      = c.getPrivacy() != null ? c.getPrivacy().name() : null;
        this.communityFeedEligible = c.isFeedEligible();
        this.communityStatus       = c.getStatus() != null ? c.getStatus().name() : null;
    }

    // =========================================================================
    // POLL HELPERS
    // =========================================================================

    public boolean hasPoll() {
        return poll != null;
    }

    public boolean hasActivePoll() {
        return poll != null && poll.isOpenForVoting();
    }

    // =========================================================================
    // MEDIA HELPERS
    // =========================================================================

    public List<String> getMediaUrlsList() {
        if (mediaUrls == null || mediaUrls.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(mediaUrls.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public void setMediaUrlsList(List<String> urls) {
        if (urls != null && !urls.isEmpty()) {
            this.mediaUrls  = String.join(",", urls);
            this.mediaCount = urls.size();
        } else {
            this.mediaUrls  = null;
            this.mediaCount = 0;
        }
    }

    public boolean hasMedia() {
        return mediaCount != null && mediaCount > 0;
    }

    public boolean hasMultipleMedia() {
        return mediaCount != null && mediaCount > 1;
    }

    // =========================================================================
    // HASHTAG HELPERS
    // =========================================================================

    public List<String> getHashtagsList() {
        if (hashtags == null || hashtags.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(hashtags.split("\\s+"))
                .map(String::trim)
                .filter(s -> s.startsWith("#"))
                .collect(Collectors.toList());
    }

    public void setHashtagsList(List<String> tags) {
        if (tags != null && !tags.isEmpty()) {
            List<String> formattedTags = tags.stream()
                    .map(tag -> tag.startsWith("#") ? tag : "#" + tag)
                    .collect(Collectors.toList());
            this.hashtags     = String.join(" ", formattedTags);
            this.hashtagCount = formattedTags.size();
        } else {
            this.hashtags     = null;
            this.hashtagCount = 0;
        }
    }

    public boolean hasHashtags() {
        return hashtagCount != null && hashtagCount > 0;
    }

    // =========================================================================
    // MENTION HELPERS
    // =========================================================================

    public List<Long> getMentionedUserIdsList() {
        if (mentionedUserIds == null || mentionedUserIds.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(mentionedUserIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    public void setMentionedUserIdsList(List<Long> userIds) {
        if (userIds != null && !userIds.isEmpty()) {
            this.mentionedUserIds = userIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            this.mentionCount = userIds.size();
        } else {
            this.mentionedUserIds = null;
            this.mentionCount     = 0;
        }
    }

    public boolean hasMentions() {
        return mentionCount != null && mentionCount > 0;
    }

    // =========================================================================
    // ENGAGEMENT COUNTERS
    // =========================================================================

    public void incrementLikeCount() {
        this.likeCount = (this.likeCount != null ? this.likeCount : 0) + 1;
        updateLastEngagedAt();
        recalculateEngagementScore();
        checkAndUpdateViralStatus();
    }

    public void decrementLikeCount() {
        this.likeCount = Math.max(0, (this.likeCount != null ? this.likeCount : 0) - 1);
        recalculateEngagementScore();
    }

    public void incrementDislikeCount() {
        this.dislikeCount = (this.dislikeCount != null ? this.dislikeCount : 0) + 1;
        updateLastEngagedAt();
    }

    public void decrementDislikeCount() {
        this.dislikeCount = Math.max(0, (this.dislikeCount != null ? this.dislikeCount : 0) - 1);
    }

    public Integer getDislikeCount() {
        return dislikeCount != null ? dislikeCount : 0;
    }

    public void incrementCommentCount() {
        this.commentCount = (this.commentCount != null ? this.commentCount : 0) + 1;
        updateLastEngagedAt();
        recalculateEngagementScore();
        checkAndUpdateViralStatus();
    }

    public void decrementCommentCount() {
        this.commentCount = Math.max(0, (this.commentCount != null ? this.commentCount : 0) - 1);
        recalculateEngagementScore();
    }

    public void incrementShareCount() {
        this.shareCount = (this.shareCount != null ? this.shareCount : 0) + 1;
        updateLastEngagedAt();
        recalculateViralityScore();
        checkAndUpdateViralStatus();
    }

    public void decrementShareCount() {
        this.shareCount = Math.max(0, (this.shareCount != null ? this.shareCount : 0) - 1);
        recalculateViralityScore();
    }

    public void incrementSaveCount() {
        this.saveCount = (this.saveCount != null ? this.saveCount : 0) + 1;
        updateLastEngagedAt();
        recalculateEngagementScore();
    }

    public void decrementSaveCount() {
        this.saveCount = Math.max(0, (this.saveCount != null ? this.saveCount : 0) - 1);
        recalculateEngagementScore();
    }

    public void incrementViewCount() {
        this.viewCount = (this.viewCount != null ? this.viewCount : 0) + 1;
    }

    public void incrementReportCount() {
        this.reportCount = (this.reportCount != null ? this.reportCount : 0) + 1;
        double currentQuality = (this.qualityScore != null ? this.qualityScore : 100.0);
        this.qualityScore = Math.max(0, currentQuality - 10.0);
        checkAutoFlag();
    }

    public void takedownForCopyright(String reason, Long adminId) {
        this.status = PostStatus.TAKEN_DOWN;
        this.takedownReason = reason;
        this.takenDownAt = new Date();
        this.takenDownById = adminId;
        // Keep media for 180 days for legal investigation
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, 180);
        this.retentionExpiresAt = cal.getTime();
        this.updatedAt = new Date();
    }

    // =========================================================================
    // SCORE CALCULATION
    // =========================================================================

    public void recalculateEngagementScore() {
        int totalViews    = this.viewCount    != null ? this.viewCount    : 0;
        int totalLikes    = this.likeCount    != null ? this.likeCount    : 0;
        int totalReports  = this.reportCount  != null ? this.reportCount  : 0;

        this.engagementScore = com.Govlyx.AI.service.HLIGScorer.calculateQualityScore(
                totalLikes, totalViews, totalReports);
    }

    public void recalculateViralityScore() {
        if (createdAt == null) { this.viralityScore = 0.0; return; }

        long ageInHours = (System.currentTimeMillis() - createdAt.getTime()) / (1000 * 60 * 60);
        
        int totalLikes    = this.likeCount    != null ? this.likeCount    : 0;
        int totalComments = this.commentCount != null ? this.commentCount : 0;
        int totalShares   = this.shareCount   != null ? this.shareCount   : 0;
        int totalSaves    = this.saveCount    != null ? this.saveCount    : 0;

        this.viralityScore = com.Govlyx.AI.service.HLIGScorer.calculateMomentumScore(
                totalLikes, totalComments, totalShares, totalSaves, ageInHours);
    }

    private void applyFreshnessDecay() {
        if (createdAt == null) return;
        long ageInDays = (System.currentTimeMillis() - createdAt.getTime()) / (1000 * 60 * 60 * 24);
        if (ageInDays > 7) {
            double decayFactor = Math.pow(0.9, (ageInDays - 7) / 7.0);
            this.engagementScore = this.engagementScore * decayFactor;
        }
    }

    // =========================================================================
    // VIRAL STATUS DETECTION
    // =========================================================================

    public void checkAndUpdateViralStatus() {
        if (!isEligibleForDisplay()) return;

        int  totalEngagement = getTotalEngagementCount();
        long ageInHours      = getAgeInHours();

        if (ageInHours > 48) {
            if (Boolean.TRUE.equals(this.isViral)) {
                log.info("Social Post {} aged out of viral window ({}h), resetting tier from {} to LOCAL",
                        this.id, ageInHours, this.viralTier);
            }
            this.viralTier      = "LOCAL";
            this.expansionLevel = 0;
            this.isViral        = false;
            return;
        }

        double engagementVelocity = ageInHours > 0 ? (double) totalEngagement / ageInHours : 0;

        if (this.engagementScore < ENGAGEMENT_RATE_THRESHOLD) return;

        String previousTier = this.viralTier != null ? this.viralTier : "LOCAL";

        if (totalEngagement >= NATIONAL_VIRAL_THRESHOLD && engagementVelocity > 20) {
            this.viralTier = "NATIONAL_VIRAL"; this.expansionLevel = 3; this.isViral = true;
        } else if (totalEngagement >= STATE_VIRAL_THRESHOLD && engagementVelocity > 10) {
            this.viralTier = "STATE_VIRAL";    this.expansionLevel = 2; this.isViral = true;
        } else if (totalEngagement >= DISTRICT_VIRAL_THRESHOLD && engagementVelocity > 5) {
            this.viralTier = "DISTRICT_VIRAL"; this.expansionLevel = 1; this.isViral = true;
        } else {
            this.viralTier = "LOCAL";          this.expansionLevel = 0; this.isViral = false;
        }

        if ("LOCAL".equals(this.viralTier) && !"LOCAL".equals(previousTier)) {
            this.viralReachedAt = null;
        }

        if (!previousTier.equals(this.viralTier) && Boolean.TRUE.equals(this.isViral)
                && this.viralReachedAt == null) {
            this.viralReachedAt = new Date();
            log.info("Social Post {} went VIRAL! Tier: {} → {}", this.id, previousTier, this.viralTier);
        }
    }


    public boolean isVisibleToUserWithViralExpansion(User user) {
        if (!isEligibleForDisplay()) return false;
        checkAndUpdateViralStatus();

        if ("NATIONAL_VIRAL".equals(this.viralTier)) return true;
        if (user == null || !user.hasPincode()) return "NATIONAL_VIRAL".equals(this.viralTier);

        switch (this.viralTier) {
            case "STATE_VIRAL":    return !hasLocation() || isFromSameState(user);
            case "DISTRICT_VIRAL": return !hasLocation() || isFromSameDistrict(user);
            case "LOCAL":
            default:               return !hasLocation() || isFromSameDistrict(user);
        }
    }

    public double calculateRelevanceScoreWithViralBoost(User viewer) {
        double score = 0.0;

        if (this.hasLocation() && viewer.hasPincode()) {
            if      (this.isFromSameArea(viewer))     score += 40.0;
            else if (this.isFromSameDistrict(viewer)) score += 30.0;
            else if (this.isFromSameState(viewer))    score += 20.0;
            else                                      score += 10.0;
        } else {
            score += 15.0;
        }

        score += ((this.qualityScore != null ? this.qualityScore : 100.0) / 100.0) * 30.0;
        score += Math.min(30.0, (this.engagementScore != null ? this.engagementScore : 0.0) * 0.3);

        String tier = this.viralTier != null ? this.viralTier : "LOCAL";
        switch (tier) {
            case "NATIONAL_VIRAL": score = Math.min(100.0, score * 1.5); break;
            case "STATE_VIRAL":    score = Math.min(100.0, score * 1.3); break;
            case "DISTRICT_VIRAL": score = Math.min(100.0, score * 1.2); break;
        }

        return score;
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    @JsonIgnore
    public boolean isEligibleForDisplay() {
        if (status != PostStatus.ACTIVE) return false;
        if (Boolean.TRUE.equals(isFlagged)) return false;
        if (user == null) {
            // In ZK Blind Shield, citizen posts have user == null and are tracked via actor_token
            return true;
        }
        // Guard against uninitialized Hibernate lazy proxy — if the User association
        // was never loaded in this session, assume the user is active. The user's
        // active status is already enforced at login (JWT issuance) and by
        // PostInteractionService before this method is ever called.
        if (user instanceof HibernateProxy) {
            HibernateProxy proxy = (HibernateProxy) user;
            if (proxy.getHibernateLazyInitializer().isUninitialized()) {
                return true; // proxy not loaded — optimistically treat as active
            }
        }
        return user.getIsActive() == null || Boolean.TRUE.equals(user.getIsActive());
    }

    @JsonIgnore
    public boolean isEligibleForRecommendation() {
        // NULL fallback is 100.0, matching the DB column default (numeric DEFAULT 100.0000).
        // Old records with a null qualityScore should not be treated as zero-quality posts.
        double quality = (this.qualityScore != null ? this.qualityScore : 100.0);
        int    reports = (this.reportCount  != null ? this.reportCount  : 0);
        // Use the centralised constant so the threshold is consistent across the codebase.
        // Constant.HLIG_MIN_QUALITY = 40.0, which is more appropriate for a sparse platform.
        return isEligibleForDisplay() && quality >= com.Govlyx.AI.config.Constant.HLIG_MIN_QUALITY && reports < 5;
    }

    @JsonIgnore
    public boolean isTrendingPost() {
        return Boolean.TRUE.equals(this.isViral) && this.expansionLevel != null && this.expansionLevel >= 1;
    }

    // =========================================================================
    // MODERATION
    // =========================================================================

    public void flagPost(String reason) {
        this.isFlagged  = true;
        this.flaggedAt  = new Date();
        this.status     = PostStatus.FLAGGED;
        this.flagReason = reason;
    }

    public void unflagPost() {
        this.isFlagged  = false;
        this.flaggedAt  = null;
        this.flagReason = null;
        if (this.status == PostStatus.FLAGGED) this.status = PostStatus.ACTIVE;
    }

    private void checkAutoFlag() {
        if (reportCount != null && reportCount >= 10 && !Boolean.TRUE.equals(isFlagged)) {
            flagPost("Auto-flagged due to multiple reports");
        }
    }

    public void softDelete() {
        this.status    = PostStatus.DELETED;
        this.updatedAt = new Date();
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    private void updateLastEngagedAt() {
        this.lastEngagedAt = new Date();
    }

    public int getTotalEngagementCount() {
        return (likeCount    != null ? likeCount    : 0) +
                (commentCount != null ? commentCount : 0) +
                (shareCount   != null ? shareCount   : 0) +
                (saveCount    != null ? saveCount    : 0);
    }

    public double getEngagementRate() {
        int views = viewCount != null && viewCount > 0 ? viewCount : 1;
        return ((double) getTotalEngagementCount() / views) * 100;
    }

    public long getAgeInHours() {
        if (createdAt == null) return 0;
        return (System.currentTimeMillis() - createdAt.getTime()) / (1000 * 60 * 60);
    }

    public long getAgeInDays() {
        if (createdAt == null) return 0;
        return (System.currentTimeMillis() - createdAt.getTime()) / (1000 * 60 * 60 * 24);
    }

    @JsonIgnore
    public boolean isRecentPost() {
        return getAgeInHours() <= 24;
    }

    public String getViralStatusDescription() {
        String tier = this.viralTier != null ? this.viralTier : "LOCAL";
        switch (tier) {
            case "NATIONAL_VIRAL":  return "🔥 Trending Nationwide";
            case "STATE_VIRAL":     return "🔥 Trending in State";
            case "DISTRICT_VIRAL":  return "🔥 Trending Locally";
            default:                return "";
        }
    }

    public String getExpansionRadiusDescription() {
        int level = this.expansionLevel != null ? this.expansionLevel : 0;
        switch (level) {
            case 3:  return "Visible to all of India";
            case 2:  return "Visible across the state";
            case 1:  return "Visible across the district";
            default: return hasLocation() ? "Visible locally" : "Visible to all";
        }
    }

    // =========================================================================
    // LIFECYCLE CALLBACKS
    // =========================================================================

    @PrePersist
    private void prePersist() {
        if (createdAt       == null) createdAt       = new Date();
        if (engagementScore == null) engagementScore = 0.0;
        if (viralityScore   == null) viralityScore   = 0.0;
        if (qualityScore    == null) qualityScore    = 100.0;
        if (viralTier       == null) viralTier       = "LOCAL";
        if (expansionLevel  == null) expansionLevel  = 0;
        if (language        == null) language        = "en"; // safe default

        if (user != null && user.hasPincode() && pincode == null) {
            inheritLocationFromUser(user);
        }

        // Community patch — ensure denormalized fields are always consistent on insert
        if (community == null) {
            communityFeedEligible = false;
            communityPrivacy      = null;
        }
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = new Date();
        if (language == null) language = "en";
        recalculateEngagementScore();
        applyFreshnessDecay();
        recalculateViralityScore();
    }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SocialPost)) return false;
        SocialPost other = (SocialPost) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
