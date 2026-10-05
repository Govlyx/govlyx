package com.Govlyx.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Community entity — v3 Reddit-style feed surfacing.
 *
 * How a post surfaces in the main feed (no manual sharing):
 *  1. Post is created inside a community (SocialPost.community_id != null)
 *  2. Users like/comment → SocialPost.engagement_score rises
 *  3. Feed query checks:
 *       community_privacy = 'PUBLIC'       (denormalized on SocialPost — no JOIN)
 *       community_feed_eligible = true     (denormalized on SocialPost — no JOIN)
 *       engagement_score >= threshold      (varies by location tier)
 *  4. One row, one post. Likes on feed = likes inside community.
 *
 * Engagement thresholds (CommunityService constants):
 *   LOCAL    (same pincode)   >=   5
 *   DISTRICT (same district)  >=  15
 *   STATE    (same state)     >=  40
 *   NATIONAL (all India)      >= 100
 */
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "communities", indexes = {
        @Index(name = "idx_community_owner",      columnList = "owner_id"),
        @Index(name = "idx_community_pincode",    columnList = "pincode, privacy"),
        @Index(name = "idx_community_state",      columnList = "state_prefix, privacy"),
        @Index(name = "idx_community_district",   columnList = "district_prefix, privacy"),
        @Index(name = "idx_community_status",     columnList = "status"),
        @Index(name = "idx_community_name",       columnList = "name"),
        @Index(name = "idx_community_health",     columnList = "health_score, status"),
        @Index(name = "idx_community_seeded",     columnList = "is_system_seeded, pincode"),
        @Index(name = "idx_community_discovery",  columnList = "status, privacy, health_score, member_count"),
        @Index(name = "idx_community_feed",       columnList = "privacy, feed_eligible, status"),
        @Index(name = "idx_community_category",   columnList = "category"),
        @Index(name = "idx_community_created",    columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@BatchSize(size = 50)
public class Community {

    // ── Identity ──────────────────────────────────────────────────────────────

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    @NotBlank(message = "Community name cannot be empty")
    @Size(min = 3, max = 100, message = "Name must be 3–100 characters")
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(length = 1000) @Size(max = 1000)
    private String description;

    @Column(name = "cover_image_url", length = 255) private String coverImageUrl;
    @Column(name = "avatar_url",      length = 255) private String avatarUrl;
    @Column(name = "category",        length = 50)  private String category;
    @Column(name = "tags",            length = 500)  private String tags;

    // ── Privacy & Status ──────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy", nullable = false, length = 20)
    @Builder.Default private CommunityPrivacy privacy = CommunityPrivacy.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default private CommunityStatus status = CommunityStatus.ACTIVE;

    // ── Reddit-style Feed Surfacing ───────────────────────────────────────────

    /**
     * Whether posts from this community can auto-surface in the main feed.
     *
     * Rules:
     *  - Only PUBLIC + ACTIVE communities can be feed_eligible = true
     *  - Owner can opt-out via PUT /api/communities/{id}/feed-eligible?enabled=false
     *  - PRIVATE / SECRET → always false (enforced in @PrePersist + @PreUpdate)
     *
     * Posting pipeline:
     *  - When a post is saved, CommunityService.onPostPublished() calls
     *    SocialPost.syncCommunityDenormalizedFields(community) which copies
     *    this value onto SocialPost.communityFeedEligible so the feed query
     *    needs no JOIN.
     */
    @Column(name = "feed_eligible", nullable = false, columnDefinition = "boolean")
    @Builder.Default private Boolean feedEligible = true;

    /**
     * Optional per-community engagement score override.
     * null = use global thresholds (LOCAL=5, DISTRICT=15, STATE=40, NATIONAL=100).
     * Set to a lower value for verified ward communities so even quiet posts surface.
     */
    @Column(name = "feed_min_score_override")
    private Integer feedMinScoreOverride;

    /**
     * Lifetime count of times any post from this community appeared in the main feed.
     * Incremented by CommunityHealthScoreService during scheduled recalculation.
     * Shown on the owner's insights dashboard.
     */
    @Column(name = "feed_surface_count", nullable = false)
    @Builder.Default private Integer feedSurfaceCount = 0;

    // ── Location (mirrors SocialPost / User pattern) ──────────────────────────

    @Column(name = "pincode",             length = 6)   private String  pincode;
    @Column(name = "state_prefix",        length = 2)   private String  statePrefix;
    @Column(name = "district_prefix",     length = 3)   private String  districtPrefix;
    @Column(name = "location_name",       length = 200)  private String  locationName;
    @Column(name = "location_restricted", nullable = false, columnDefinition = "boolean")
    @Builder.Default private Boolean locationRestricted = false;

    // ── Hyperlocal Seed ───────────────────────────────────────────────────────

    @Column(name = "is_system_seeded", nullable = false, columnDefinition = "boolean") @Builder.Default private Boolean isSystemSeeded = false;
    @Column(name = "ward_name",          length = 200)                       private String  wardName;
    @Column(name = "seed_trigger_count", nullable = false) @Builder.Default private Integer seedTriggerCount = 0;
    @Column(name = "seed_threshold",     nullable = false) @Builder.Default private Integer seedThreshold    = 5;

    // ── Settings ──────────────────────────────────────────────────────────────

    @Column(name = "allow_member_posts", nullable = false, columnDefinition = "boolean") @Builder.Default private Boolean allowMemberPosts = true;
    @Column(name = "require_post_approval", nullable = false, columnDefinition = "boolean") @Builder.Default private Boolean requirePostApproval = false;
    /** Always true — Govlyx is fully anonymous. Enforced in lifecycle hooks. */
    @Column(name = "allow_anonymous_posts", nullable = false, columnDefinition = "boolean") @Builder.Default private Boolean allowAnonymousPosts = true;

    @Column(name = "chat_retention_days", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer chatRetentionDays = 0;

    @Column(name = "is_group_chat_enabled", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean isGroupChatEnabled = true;


    // ── Engagement Counters ───────────────────────────────────────────────────

    @Column(name = "member_count",        nullable = false) @Builder.Default private Integer memberCount       = 0;
    @Column(name = "post_count",          nullable = false) @Builder.Default private Integer postCount         = 0;
    @Column(name = "report_count",        nullable = false) @Builder.Default private Integer reportCount       = 0;
    @Column(name = "total_comment_count", nullable = false) @Builder.Default private Integer totalCommentCount = 0;
    @Column(name = "total_like_count",    nullable = false) @Builder.Default private Integer totalLikeCount    = 0;

    // ── Health Score (recalculated every 6h) ─────────────────────────────────

    @Column(name = "health_score",               nullable = false, columnDefinition = "numeric(5,2) DEFAULT 0.00")
    @Builder.Default private Double healthScore             = 0.0;
    @Column(name = "health_post_freq_score",      columnDefinition = "numeric(5,2) DEFAULT 0")
    @Builder.Default private Double healthPostFreqScore     = 0.0;
    @Column(name = "health_member_growth_score",  columnDefinition = "numeric(5,2) DEFAULT 0")
    @Builder.Default private Double healthMemberGrowthScore = 0.0;
    @Column(name = "health_engagement_score",     columnDefinition = "numeric(5,2) DEFAULT 0")
    @Builder.Default private Double healthEngagementScore   = 0.0;
    @Column(name = "health_mod_activity_score",   columnDefinition = "numeric(5,2) DEFAULT 0")
    @Builder.Default private Double healthModActivityScore  = 0.0;
    @Column(name = "health_retention_score",      columnDefinition = "numeric(5,2) DEFAULT 0")
    @Builder.Default private Double healthRetentionScore    = 0.0;

    @Column(name = "new_members_last_7d",    nullable = false) @Builder.Default private Integer newMembersLast7d    = 0;
    @Column(name = "posts_last_7d",          nullable = false) @Builder.Default private Integer postsLast7d         = 0;
    @Column(name = "active_posters_last_7d", nullable = false) @Builder.Default private Integer activePostersLast7d = 0;
    @Column(name = "health_score_updated_at") @Temporal(TemporalType.TIMESTAMP) private Date healthScoreUpdatedAt;
    @Column(name = "health_tier", length = 20) @Builder.Default private String healthTier = "QUIET";

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "created_at",   nullable = false, updatable = false) @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default private Date createdAt  = new Date();
    @Column(name = "updated_at")    @Temporal(TemporalType.TIMESTAMP) private Date updatedAt;
    @Column(name = "last_active_at") @Temporal(TemporalType.TIMESTAMP) private Date lastActiveAt;
    @Column(name = "scheduled_deletion_at") @Temporal(TemporalType.TIMESTAMP) private Date scheduledDeletionAt;

    // ── Relationships ─────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_community_owner"))
    private User owner;

    @Column(name = "ip_address", length = 64)
    @JsonIgnore
    private String ipAddress;

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore private List<CommunityMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore private List<CommunityJoinRequest> joinRequests = new ArrayList<>();

    @OneToMany(mappedBy = "community", fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore private List<SocialPost> posts = new ArrayList<>();

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum CommunityPrivacy { PUBLIC, PRIVATE, SECRET }
    public enum CommunityStatus  { ACTIVE, SUSPENDED, ARCHIVED, DELETED, PERMANENTLY_DELETED }
    public enum HealthTier       { THRIVING, ACTIVE, QUIET, DORMANT }

    // ── Business Logic Helpers ────────────────────────────────────────────────

    public boolean isActive()  { return CommunityStatus.ACTIVE.equals(status); }
    public boolean isPublic()  { return CommunityPrivacy.PUBLIC.equals(privacy); }
    public boolean isPrivate() { return CommunityPrivacy.PRIVATE.equals(privacy); }
    public boolean isSecret()  { return CommunityPrivacy.SECRET.equals(privacy); }

    public boolean isOwnedBy(Long userId) {
        return owner != null && owner.getId() != null && owner.getId().equals(userId);
    }

    public boolean isEligibleForDiscovery() {
        return CommunityStatus.ACTIVE.equals(status) && !CommunityPrivacy.SECRET.equals(privacy);
    }

    /**
     * A community's posts can surface in the main feed only when:
     *  1. Community is ACTIVE
     *  2. Community is PUBLIC
     *  3. Owner has not opted out (feedEligible = true)
     */
    public boolean isFeedEligible() {
        return isActive() && Boolean.TRUE.equals(feedEligible);
    }

    /**
     * Returns the effective engagement score threshold for the LOCAL tier.
     * Uses community-level override if set, otherwise the global constant.
     * Called by CommunityService when syncing denormalized fields on SocialPost.
     */
    public int effectiveLocalThreshold(int globalLocalThreshold) {
        return (feedMinScoreOverride != null && feedMinScoreOverride > 0)
                ? feedMinScoreOverride : globalLocalThreshold;
    }

    public void inheritLocationFromUser(User user) {
        if (user != null && user.hasPincode()) {
            this.pincode        = user.getPincode();
            this.statePrefix    = user.getStatePrefix();
            this.districtPrefix = user.getDistrictPrefix();
        }
    }

    public boolean hasLocation() { return pincode != null && !pincode.isBlank(); }

    // ── Counter helpers (null-safe) ───────────────────────────────────────────

    public void incrementMemberCount()       { memberCount       = s(memberCount)       + 1; }
    public void decrementMemberCount()       { memberCount       = Math.max(0, s(memberCount) - 1); }
    public void incrementPostCount()         { postCount         = s(postCount)         + 1; lastActiveAt = new Date(); }
    public void decrementPostCount()         { postCount         = Math.max(0, s(postCount) - 1); }
    public void incrementTotalCommentCount() { totalCommentCount = s(totalCommentCount) + 1; }
    public void incrementTotalLikeCount()    { totalLikeCount    = s(totalLikeCount)    + 1; }
    public void incrementFeedSurfaceCount()  { feedSurfaceCount  = s(feedSurfaceCount)  + 1; }

    private int s(Integer v) { return v != null ? v : 0; }

    // ── Health tier ───────────────────────────────────────────────────────────

    public void recalculateHealthTier() {
        double sc = healthScore != null ? healthScore : 0.0;
        if      (sc >= 75) healthTier = HealthTier.THRIVING.name();
        else if (sc >= 50) healthTier = HealthTier.ACTIVE.name();
        else if (sc >= 25) healthTier = HealthTier.QUIET.name();
        else               healthTier = HealthTier.DORMANT.name();
    }

    public String getHealthTierEmoji() {
        if (healthTier == null) return "";
        return switch (healthTier) {
            case "THRIVING" -> "🔥";
            case "ACTIVE"   -> "✅";
            case "QUIET"    -> "🔔";
            default         -> "💤";
        };
    }

    // ── Lifecycle Hooks ───────────────────────────────────────────────────────

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = new Date();
        if (slug == null && name != null) {
            slug = name.toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("(^-|-$)", "")
                    + "-" + (System.currentTimeMillis() % 100000);
        }
        allowAnonymousPosts = true;                              // always
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt           = new Date();
        allowAnonymousPosts = true;
    }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Community)) return false;
        Community other = (Community) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
