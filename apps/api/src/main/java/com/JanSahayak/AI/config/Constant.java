package com.JanSahayak.AI.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Central constants registry for the entire JanSahayak application.
 *
 * All magic numbers and string literals that were previously scattered across
 * service, controller, model and utility classes are consolidated here.
 * Each section is labeled with the original source file(s).
 *
 * CHANGES v2:
 *  - Removed stale "Redis cache name" comment on HLIG_CACHE_PROFILE (no Redis)
 *  - Added HLIG_CANDIDATE_LIMIT_SPARSE for platforms with < 20 active posts
 *  - Added HLIG_SCORE_MIN_THRESHOLD to avoid re-scoring zero-signal posts
 *  - Added HLIG_FRESHNESS_SLEEPER_MIN/MAX_HOURS & HLIG_FRESHNESS_SLEEPER_BONUS
 *  - Added HLIG_NEIGHBOUR_BOOST_MAX & HLIG_NEIGHBOUR_LIKE_STEP
 *  - Resolved duplicate constant group (COMMUNITY_HEALTH_* vs COMMUNITY_IDEAL_*)
 *  - Added FEED_MAX_PAGE_SIZE alias to resolve FeedController compile error
 *  - Added HLIG_WARM_PHASE_THRESHOLD / HLIG_WARMING_PHASE_THRESHOLD for clarity
 *  - Added HLIG_AUTHOR_POST_BOOST (creator boost multiplier used in v2 formula)
 *  - Added POST_QUALITY_DEFAULT for posts whose qualityScore is null
 *
 * CHANGES v3:
 *  - Added CHAT_RECONNECT_GRACE_PERIOD_SECONDS for page-refresh reconnect window
 *
 * CHANGES v4:
 *  - RE-ADDED entire HLIG profile / signal-weight / phase / cache section that
 *    was accidentally dropped. Without these, InterestProfileService and
 *    HLIGScorer fail with "cannot find symbol" at compile time:
 *      HLIG_CACHE_PROFILE, HLIG_TOP_N, HLIG_MAX_NEIGHBOURS,
 *      HLIG_WARM_PHASE_THRESHOLD, HLIG_WARMING_PHASE_THRESHOLD,
 *      HLIG_PROFILE_CORE_THRESHOLD, HLIG_PROFILE_CASUAL_THRESHOLD,
 *      HLIG_PROFILE_LAMBDA, HLIG_W_LIKE … HLIG_W_POST_CREATED
 *
 * CHANGES v5 (this version):
 *  - Added DEFAULT_PAGE_SIZE alias (= DEFAULT_FEED_LIMIT = 20)
 *    Used by SocialPostService.getLocalPosts() page-size clamping logic.
 *  - Added MAX_PAGE_SIZE alias (= FEED_MAX_SIZE = 50)
 *    Used by SocialPostService.getLocalPosts() page-size clamping logic.
 *  Both are pure aliases — no existing constant values are changed.
 */
public final class Constant {

    private Constant() {} // non-instantiable

    // =========================================================================
    // MEDIA — extensions & upload path
    // =========================================================================

    public static final List<String> ALLOWED_IMAGE_EXTENSIONS =
            Arrays.asList(".jpg", ".jpeg", ".png", ".webp");
    public static final List<String> ALLOWED_VIDEO_EXTENSIONS =
            Arrays.asList(".mp4", ".mov");

    // SOCIAL_POST_UPLOAD_DIR removed — all media is stored on Google Drive.

    // =========================================================================
    // MEDIA — size & count limits
    // =========================================================================

    public static final int  MAX_SOCIAL_POST_IMAGES      = 10;
    public static final int  MAX_SOCIAL_POST_VIDEOS      = 5;
    public static final int  MAX_SOCIAL_POST_TOTAL_MEDIA = 10;
    public static final int  MAX_SOCIAL_POST_MEDIA_COUNT = 10;
    public static final long MAX_SOCIAL_POST_IMAGE_SIZE  = 5L  * 1024 * 1024;   // 5 MB
    public static final long MAX_SOCIAL_POST_VIDEO_SIZE  = 100L * 1024 * 1024;  // 100 MB

    public static final int    MAX_VIDEO_DURATION_SECONDS = 140;
    public static final int    MIN_VIDEO_RESOLUTION       = 32;
    public static final int    MAX_VIDEO_RESOLUTION       = 1920;
    public static final double MAX_VIDEO_FRAME_RATE       = 40.0;
    public static final int    MAX_VIDEO_BITRATE          = 25_000_000;

    public static final int MAX_IMAGE_WIDTH     = 4096;
    public static final int MAX_IMAGE_HEIGHT    = 4096;
    public static final int MIN_IMAGE_DIMENSION = 1;

    // =========================================================================
    // CONTENT — length & tag limits
    // =========================================================================

    public static final int MAX_SOCIAL_POST_CONTENT_LENGTH = 3000;
    public static final int MAX_POST_CONTENT_LENGTH        = 2000;
    public static final int MAX_COMMENT_LENGTH             = 1000;
    public static final int MAX_LOCATION_LENGTH            = 200;
    public static final int MAX_HASHTAGS_PER_POST          = 30;
    public static final int MAX_MENTIONS_PER_POST          = 20;
    public static final int POST_CONTENT_PREVIEW_LENGTH    = 100;
    public static final String POST_CONTENT_TRUNCATION_SUFFIX = "...";

    // =========================================================================
    // USER TAGGING
    // =========================================================================

    public static final Pattern USERNAME_TAG_PATTERN =
            Pattern.compile("@([a-zA-Z0-9_]+)");
    public static final int MAX_TAGS_PER_POST                 = 10;
    public static final int MAX_TAG_CONTEXT_LENGTH            = 100;
    public static final int MAX_TAGGING_SUGGESTIONS_LIMIT     = MAX_TAGS_PER_POST * 4;
    public static final int DEFAULT_TAGGING_SUGGESTIONS_LIMIT = MAX_TAGS_PER_POST * 2;

    // =========================================================================
    // ROLES
    // =========================================================================

    public static final String ROLE_ADMIN      = "ROLE_ADMIN";
    public static final String ROLE_DEPARTMENT = "ROLE_DEPARTMENT";
    public static final String ROLE_USER       = "ROLE_USER";

    // =========================================================================
    // FEED — pagination defaults
    // =========================================================================

    public static final int DEFAULT_FEED_LIMIT    = 20;
    public static final int MAX_FEED_LIMIT        = 100;
    public static final int DEFAULT_PAGE          = 0;

    /** Hard upper bound on page size accepted by FeedController (alias for clarity). */
    public static final int FEED_MAX_SIZE         = 50;
    /** Alias used by FeedController — identical value. */
    public static final int FEED_MAX_PAGE_SIZE    = 50;
    public static final int FEED_DEFAULT_PAGE_SIZE = 20;
    public static final int FEED_DEFAULT_PAGE      = 0;

    /**
     * Generic page-size default used by service-layer methods that accept a
     * nullable {@code limit} parameter (e.g. SocialPostService.getLocalPosts).
     * Value mirrors DEFAULT_FEED_LIMIT.
     */
    public static final int DEFAULT_PAGE_SIZE = DEFAULT_FEED_LIMIT;   // 20

    /**
     * Generic hard cap on page size for service-layer clamping logic
     * (e.g. SocialPostService.getLocalPosts).
     * Value mirrors FEED_MAX_SIZE.
     */
    public static final int MAX_PAGE_SIZE = FEED_MAX_SIZE;             // 50

    // =========================================================================
    // HLIG FEED SERVICE — candidate pool & diversity
    // =========================================================================

    /** Posts fetched from DB before in-memory HLIG scoring (normal scale). */
    public static final int HLIG_CANDIDATE_LIMIT        = 200;
    /**
     * Wider net used when the geo waterfall returns < 50 candidates.
     * Ensures sparse platforms (new app, remote area) never show an empty feed.
     */
    public static final int HLIG_CANDIDATE_LIMIT_SPARSE = 500;

    public static final int HLIG_DEFAULT_PAGE_SIZE        = 20;
    /** % of feed slots for non-core-topic posts — normal users. */
    public static final int HLIG_DIVERSITY_PCT_WARM       = 15;
    /** % of feed slots for diversity — bubble-risk users (dominant weight > 20). */
    public static final int HLIG_DIVERSITY_PCT_BUBBLE     = 30;
    /** Times a topic may appear before session-penalty kicks in. */
    public static final int HLIG_SESSION_TOPIC_MAX_REPEAT = 3;

    // =========================================================================
    // HLIG SCORER — geo multipliers & quality gate
    // =========================================================================

    public static final double HLIG_GEO_SAME_PINCODE  = 2.5;
    public static final double HLIG_GEO_SAME_DISTRICT = 1.8;
    public static final double HLIG_GEO_SAME_STATE    = 1.3;
    public static final double HLIG_GEO_NATIONAL      = 1.1;
    public static final double HLIG_GEO_NONE          = 0.9;

    /** Freshness decay λ; e^(-λ×hours). ~46-hour half-life. */
    public static final double HLIG_FRESHNESS_LAMBDA            = 0.015;
    /** Minimum post age (hours) to qualify for the sleeper bonus. */
    public static final long   HLIG_FRESHNESS_SLEEPER_MIN_HOURS = 12L;
    /** Maximum post age (hours) to qualify for the sleeper bonus. */
    public static final long   HLIG_FRESHNESS_SLEEPER_MAX_HOURS = 96L;
    /** Score multiplier applied to sleeper posts. */
    public static final double HLIG_FRESHNESS_SLEEPER_BONUS     = 1.25;
    /** Minimum engagementScore for a post to be a sleeper candidate. */
    public static final double HLIG_FRESHNESS_SLEEPER_ENG_MIN   = 50.0;

    /** Posts below this qualityScore are invisible to all recommenders. */
    public static final double HLIG_MIN_QUALITY     = 40.0;
    /** Default quality score when post.qualityScore is null (brand-new post). */
    public static final double POST_QUALITY_DEFAULT = 50.0;

    /** Minimum HLIG score for a post to survive the warm-feed filter. */
    public static final double HLIG_SCORE_MIN_THRESHOLD = 0.001;

    /** Collaborative-filter neighbour boost — step per neighbour like. */
    public static final double HLIG_NEIGHBOUR_LIKE_STEP  = 0.05;
    /** Maximum neighbour boost multiplier. */
    public static final double HLIG_NEIGHBOUR_BOOST_MAX  = 2.0;

    /** Creator boost multiplier applied when scoring author's own posts. */
    public static final double HLIG_AUTHOR_POST_BOOST = 1.5;

    // =========================================================================
    // HLIG INTEREST PROFILE — signal weights
    // =========================================================================
    // Used by InterestProfileService to update per-user topic weights.
    // Positive weights increase topic affinity; negative weights decrease it.

    public static final double HLIG_W_LIKE           =  3.0;
    public static final double HLIG_W_COMMENT        =  4.0;
    public static final double HLIG_W_SAVE           =  3.5;
    public static final double HLIG_W_SHARE          =  3.0;
    public static final double HLIG_W_VIEW           =  0.5;
    public static final double HLIG_W_POST_CREATED   =  5.0;
    public static final double HLIG_W_DISLIKE        = -5.0;
    public static final double HLIG_W_UNLIKE         = -3.0;
    public static final double HLIG_W_UNSAVE         = -3.5;
    public static final double HLIG_W_NOT_INTERESTED = -8.0;
    public static final double HLIG_W_SCROLL_PAST    = -0.3;

    // =========================================================================
    // HLIG INTEREST PROFILE — cache, top-N, neighbours
    // =========================================================================

    /** Caffeine cache name for user interest profiles and neighbour lists. */
    public static final String HLIG_CACHE_PROFILE = "uip_profile";

    /** Number of top topics loaded per user for feed scoring. */
    public static final int    HLIG_TOP_N         = 20;

    /** Maximum collaborative-filter neighbours returned per user. */
    public static final int    HLIG_MAX_NEIGHBOURS = 50;

    // =========================================================================
    // PERFORMANCE CACHE NAMES — used in @Cacheable / @CacheEvict annotations
    // =========================================================================

    /** Unread notification count — polled every 60 s by frontend, cached 30 s. */
    public static final String CACHE_NOTIF_UNREAD_COUNT = "notif-unread-count";

    /** User profile — loaded on every authenticated request, cached 10 min. */
    public static final String CACHE_USER_PROFILE       = "user-profiles";

    /** Pincode reference data (states/districts) — static, cached 24 h. */
    public static final String CACHE_PINCODE_DATA       = "pincode-data";

    /** Community list for browse page — cached 5 min. */
    public static final String CACHE_COMMUNITY_LIST     = "community-list";

    // =========================================================================
    // HLIG INTEREST PROFILE — phase thresholds
    // =========================================================================
    // getUserPhase() in InterestProfileService uses these to decide which
    // feed strategy to apply (COLD → WARMING → WARM).

    /**
     * total_signals_cache must exceed this to leave COLD phase.
     * Value 0 means any single signal (even one view) exits COLD.
     */
    public static final int HLIG_WARMING_PHASE_THRESHOLD = 0;

    /**
     * total_signals_cache must exceed this to reach WARM phase.
     * Users between HLIG_WARMING_PHASE_THRESHOLD and this value are WARMING.
     */
    public static final int HLIG_WARM_PHASE_THRESHOLD    = 30;

    // =========================================================================
    // HLIG INTEREST PROFILE — topic weight thresholds
    // =========================================================================

    /**
     * Minimum decayed weight for a topic to count as CASUAL interest.
     * Topics below this are pruned from the active profile map.
     */
    public static final double HLIG_PROFILE_CASUAL_THRESHOLD = 2.0;

    /**
     * Minimum decayed weight for a topic to count as CORE interest.
     * CORE topics are used for neighbour matching and diversity separation.
     */
    public static final double HLIG_PROFILE_CORE_THRESHOLD   = 8.0;

    /**
     * Daily decay rate applied to topic weights in the nightly batch job.
     * Mirrors the lambda used inside UserInterestProfile.decayedWeight().
     * e^(-LAMBDA × days) — ~46-day half-life for topic weights.
     */
    public static final double HLIG_PROFILE_LAMBDA = 0.015;

    // =========================================================================
    // HLIG TOPIC EXTRACTOR — source strength multipliers
    // =========================================================================
    // Weights applied per extraction source inside TopicExtractor.extract().

    public static final double TOPIC_HASHTAG_STRENGTH  = 1.0;
    public static final double TOPIC_CATEGORY_STRENGTH = 0.9;
    public static final double TOPIC_COMM_TAG_STRENGTH = 0.75;
    public static final double TOPIC_POLL_STRENGTH     = 0.7;
    public static final double TOPIC_CONTENT_STRENGTH  = 0.6;

    // =========================================================================
    // CHAT SESSION
    // =========================================================================

    public static final int CHAT_MAX_INACTIVE_MINUTES = 30;
    public static final int CHAT_MAX_RECENT_MESSAGES  = 50;

    /**
     * Grace period (seconds) after a WebSocket disconnect before the session is
     * hard-ended and the partner notified. Gives the user time to reconnect
     * after a page refresh without losing their chat session.
     */
    public static final int CHAT_RECONNECT_GRACE_PERIOD_SECONDS = 30;

    // =========================================================================
    // CHAT ENCRYPTION
    // =========================================================================

    public static final String CHAT_ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    public static final int    CHAT_GCM_IV_LENGTH         = 12;
    public static final int    CHAT_GCM_TAG_LENGTH        = 128;
    public static final int    CHAT_AES_KEY_SIZE          = 256;

    // =========================================================================
    // MATCHMAKING
    // =========================================================================

    public static final int MATCHMAKING_SEARCH_TIMEOUT_SECONDS = 300;

    // =========================================================================
    // BAD WORD SERVICE
    // =========================================================================

    public static final String BAD_WORDS_FILE             = "badwords/strict.txt";
    public static final String BAD_WORDS_FILE_PATH        = BAD_WORDS_FILE;  // alias
    public static final int    BAD_WORDS_INITIAL_CAPACITY = 1000;

    // =========================================================================
    // POST INTERACTION CONTROLLER — type discriminators
    // =========================================================================

    public static final String INTERACTION_TYPE_POSTS        = "posts";
    public static final String INTERACTION_TYPE_SOCIAL_POSTS = "social-posts";

    // =========================================================================
    // USER INTERACTION — timing windows
    // =========================================================================

    public static final int  VIEW_DUPLICATE_PREVENTION_HOURS  = 1;
    public static final long VIEW_DUPLICATE_PREVENTION_MILLIS =
            VIEW_DUPLICATE_PREVENTION_HOURS * 60L * 60 * 1000;

    public static final int  RECENT_ACTIVITY_DAYS   = 7;
    public static final long RECENT_ACTIVITY_MILLIS =
            RECENT_ACTIVITY_DAYS * 24L * 60 * 60 * 1000;

    public static final int  RECENT_INTERACTIONS_HOURS  = 24;
    public static final long RECENT_INTERACTIONS_MILLIS =
            RECENT_INTERACTIONS_HOURS * 60L * 60 * 1000;

    public static final int TOP_ENGAGING_POSTS_LIMIT = 5;

    // =========================================================================
    // INDIA-ONLY APPLICATION
    // =========================================================================

    public static final String  DEFAULT_TARGET_COUNTRY  = "IN";
    public static final String  APP_COUNTRY_CODE        = "IN";
    public static final String  COUNTRY_NAME_INDIA      = "India";
    public static final boolean FORCE_INDIA_ONLY        = true;
    public static final String  INVALID_COUNTRY_MESSAGE =
            "This application only supports India (IN)";

    public static final boolean COUNTRY_BROADCASTS_VISIBLE_TO_ALL = true;
    public static final boolean GOVERNMENT_BROADCASTS_PRIORITY    = true;

    public static final Set<String> COUNTRY_BROADCAST_ROLES  =
            new HashSet<>(Arrays.asList(ROLE_ADMIN, ROLE_DEPARTMENT));
    public static final Set<String> STATE_BROADCAST_ROLES    =
            new HashSet<>(Arrays.asList(ROLE_ADMIN, ROLE_DEPARTMENT));
    public static final Set<String> DISTRICT_BROADCAST_ROLES =
            new HashSet<>(Arrays.asList(ROLE_ADMIN, ROLE_DEPARTMENT));
    public static final Set<String> AREA_BROADCAST_ROLES     =
            new HashSet<>(Arrays.asList(ROLE_ADMIN, ROLE_DEPARTMENT, ROLE_USER));

    public static final int MAX_TARGET_STATES    = 10;
    public static final int MAX_TARGET_DISTRICTS = 25;
    public static final int MAX_TARGET_PINCODES  = 100;

    public static final String  INDIAN_PINCODE_PATTERN         = "^[1-9]\\d{5}$";
    public static final String  INDIAN_STATE_PREFIX_PATTERN    = "^[1-9]\\d$";
    public static final String  INDIAN_DISTRICT_PREFIX_PATTERN = "^[1-9]\\d{2}$";
    public static final Pattern INDIAN_PINCODE_REGEX           =
            Pattern.compile(INDIAN_PINCODE_PATTERN);

    public static final double STATE_DISTANCE_KM   = 25.0;
    public static final double COUNTRY_DISTANCE_KM = 200.0;

    public static final Set<String> INDIA_STATE_PREFIXES = new HashSet<>(Arrays.asList(
            "11","12","13","14","15","16","17","18","19",
            "20","21","22","23","24","25","26","27","28","29",
            "30","31","32","33","34","35","36","37","38","39",
            "40","41","42","43","44","45","46","47","48","49",
            "50","51","52","53","54","56","57","58","59",
            "60","61","62","63","64","67","68","69",
            "70","71","72","73","74","75","76","77","78","79",
            "80","81","82","83","84","85"
    ));

    // =========================================================================
    // PAGINATION — cursor & search limits
    // =========================================================================

    public static final long MIN_VALID_CURSOR         = 1L;
    public static final long MAX_VALID_CURSOR         = Long.MAX_VALUE;
    public static final long MAX_TIMESTAMP_AGE_MILLIS = 365L * 24 * 60 * 60 * 1000;

    public static final int DEFAULT_USER_SEARCH_LIMIT       = 10;
    public static final int MAX_USER_SEARCH_LIMIT           = 50;
    public static final int DEFAULT_DEPARTMENT_SEARCH_LIMIT = 15;
    public static final int MAX_DEPARTMENT_SEARCH_LIMIT     = 100;

    public static final int MAX_PINCODE_SEARCH_RESULTS  = 200;
    public static final int MAX_STATE_SEARCH_RESULTS    = 500;
    public static final int MAX_DISTRICT_SEARCH_RESULTS = 300;

    public static final int DEFAULT_ACTIVE_USER_LIMIT = DEFAULT_FEED_LIMIT;
    public static final int MAX_ACTIVE_USER_LIMIT     = MAX_FEED_LIMIT;

    public static final boolean ENABLE_PAGINATION_LOGGING = false;

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    public static boolean isValidIndianPincode(String pincode) {
        return pincode != null && INDIAN_PINCODE_REGEX.matcher(pincode).matches();
    }

    public static String getStatePrefixFromPincode(String pincode) {
        return isValidIndianPincode(pincode) ? pincode.substring(0, 2) : null;
    }

    public static String getDistrictPrefixFromPincode(String pincode) {
        return isValidIndianPincode(pincode) ? pincode.substring(0, 3) : null;
    }

    public static boolean isValidIndianStatePrefix(String statePrefix) {
        return statePrefix != null && INDIA_STATE_PREFIXES.contains(statePrefix);
    }

    public static String normalizeTargetCountry(String targetCountry) {
        return DEFAULT_TARGET_COUNTRY;
    }

    public static boolean canCreateCountryBroadcast(String roleName) {
        return roleName != null && COUNTRY_BROADCAST_ROLES.contains(roleName);
    }

    public static boolean isGovernmentRole(String roleName) {
        return ROLE_ADMIN.equals(roleName) || ROLE_DEPARTMENT.equals(roleName);
    }

    public static boolean shouldCountryBroadcastBeVisibleToAll() {
        return COUNTRY_BROADCASTS_VISIBLE_TO_ALL && FORCE_INDIA_ONLY;
    }

    // =========================================================================
    // COMMUNITY — list / member / join-request pagination limits
    // =========================================================================

    public static final int DEFAULT_COMMUNITY_LIST_LIMIT = 20;
    public static final int MAX_COMMUNITY_LIST_LIMIT     = 100;
    public static final int DEFAULT_MEMBER_LIST_LIMIT    = 20;
    public static final int MAX_MEMBER_LIST_LIMIT        = 100;
    public static final int DEFAULT_JOIN_REQUEST_LIMIT   = 20;
    public static final int MAX_JOIN_REQUEST_LIMIT       = 50;

    // =========================================================================
    // COMMUNITY HEALTH SCORE — ideal benchmarks & dormancy threshold
    // =========================================================================

    /** Target posts per week for a fully active community (score = 100). */
    public static final int    COMMUNITY_HEALTH_IDEAL_POSTS_PER_WEEK   = 14;
    /** Target average (likes + comments) per post for full engagement score. */
    public static final double COMMUNITY_HEALTH_IDEAL_ENGAGEMENT_RATIO = 10.0;
    /** Target new members per week for full member-growth score. */
    public static final int    COMMUNITY_HEALTH_IDEAL_NEW_MEMBERS      = 10;
    /** Target fraction of members who posted in last 7 days (0.10 = 10%). */
    public static final double COMMUNITY_HEALTH_IDEAL_RETENTION_RATIO  = 0.10;
    /** Days since last mod activity before modActivity score = 0. */
    public static final int    COMMUNITY_HEALTH_DORMANT_DAYS           = 30;

    // =========================================================================
    // COMMUNITY — feed-surfacing engagement thresholds
    // =========================================================================

    public static final double COMMUNITY_THRESHOLD_NATIONAL = 50.0;
    public static final double COMMUNITY_THRESHOLD_STATE    = 20.0;
    public static final double COMMUNITY_THRESHOLD_DISTRICT = 10.0;
    public static final double COMMUNITY_THRESHOLD_LOCAL    =  3.0;

    // =========================================================================
    // HYPERLOCAL SEED SERVICE
    // =========================================================================

    /** Min active users per pincode before auto-creating a hyperlocal community. */
    public static final int HYPERLOCAL_SEED_THRESHOLD = 5;
    /** Page size when batch-enrolling users into a newly seeded community. */
    public static final int HYPERLOCAL_ENROLL_BATCH   = 100;

    // =========================================================================
    // POST (ISSUE POST) — scoring weights & decay
    // =========================================================================

    public static final double POST_WEIGHT_LIKE    = 3.0;
    public static final double POST_WEIGHT_COMMENT = 5.0;
    public static final double POST_WEIGHT_VIEW    = 0.5;
    /** freshness = 1 / (1 + ageHours * POST_DECAY_RATE). ~50% at 100 h. */
    public static final double POST_DECAY_RATE     = 0.01;
    /** Days of inactivity before a post is eligible for scope demotion. */
    public static final int    POST_DEMOTION_INACTIVE_DAYS = 14;

    // =========================================================================
    // POST (ISSUE POST) — geo boost multipliers
    // =========================================================================

    public static final double ISSUE_GEO_BOOST_SAME_PINCODE = 3.0;
    public static final double ISSUE_GEO_BOOST_NEARBY       = 2.0;
    public static final double ISSUE_GEO_BOOST_DISTRICT     = 1.5;
    public static final double ISSUE_GEO_BOOST_STATE        = 1.2;
    public static final double ISSUE_GEO_BOOST_NATIONAL     = 1.0;

    public static final double POST_BOOST_AREA     = 3.0;
    public static final double POST_BOOST_DISTRICT = 1.5;
    public static final double POST_BOOST_STATE    = 1.2;
    public static final double POST_BOOST_NATIONAL = 1.0;

    // =========================================================================
    // POST (ISSUE POST) — recommendation pagination
    // =========================================================================

    public static final int ISSUE_RECOMMENDATION_DEFAULT_LIMIT        = 20;
    public static final int ISSUE_RECOMMENDATION_MAX_LIMIT            = 50;
    /** Candidate pool = validLimit * this multiplier, then scored and trimmed. */
    public static final int ISSUE_RECOMMENDATION_CANDIDATE_MULTIPLIER = 3;

    // =========================================================================
    // POST (ISSUE POST) — nearby expansion & blend
    // =========================================================================

    /** Widen to nearby pincodes when exact-area pool is smaller than this. */
    public static final int ISSUE_NEARBY_EXPANSION_THRESHOLD = 5;
    /** Max Tier-2 (nearby pincode) posts blended into results. */
    public static final int ISSUE_NEARBY_BLEND_LIMIT         = 10;

    // =========================================================================
    // POST (ISSUE POST) — viral promotion thresholds
    // =========================================================================

    public static final int  ISSUE_DISTRICT_PROMOTE_LIKES         = 20;
    public static final int  ISSUE_DISTRICT_PROMOTE_COMMENTS      = 10;
    public static final long ISSUE_DISTRICT_PROMOTE_MAX_AGE_HOURS = 48L;

    public static final int  ISSUE_STATE_PROMOTE_LIKES            = 50;
    public static final int  ISSUE_STATE_PROMOTE_COMMENTS         = 25;
    public static final long ISSUE_STATE_PROMOTE_MAX_AGE_HOURS    = 72L;

    public static final int  ISSUE_NATIONAL_PROMOTE_LIKES         = 200;
    public static final int  ISSUE_NATIONAL_PROMOTE_COMMENTS      = 100;
    public static final long ISSUE_NATIONAL_PROMOTE_MAX_AGE_HOURS = 96L;

    // =========================================================================
    // NOTIFICATIONS
    // =========================================================================

    public static final int DEFAULT_NOTIFICATION_LIMIT = 20;
    public static final int MAX_NOTIFICATION_LIMIT     = 100;
    /** Notifications older than this many days are removed by nightly cleanup. */
    public static final int MAX_NOTIFICATION_AGE_DAYS  = 30;
    /** Aliases used across notification services — same values as above. */
    public static final int NOTIFICATION_DEFAULT_LIMIT = DEFAULT_NOTIFICATION_LIMIT;
    public static final int NOTIFICATION_MAX_LIMIT     = MAX_NOTIFICATION_LIMIT;
    public static final int NOTIFICATION_MAX_AGE_DAYS  = MAX_NOTIFICATION_AGE_DAYS;

}
