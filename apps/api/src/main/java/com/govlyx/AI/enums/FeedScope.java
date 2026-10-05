package com.Govlyx.AI.enums;

/**
 * FeedScope — the content pool drawn from on each of the three tabs.
 *
 * Used internally by SocialPostService.getBrowseFeed() and HLIGFeedService.
 * The tab endpoint path determines the scope — it is NOT a query param.
 *
 *   /api/v1/feed/for-you   → FeedScope.FOR_YOU   (HLIG personalised pool)
 *   /api/v1/feed/location  → FeedScope.LOCATION  (user's district / state)
 *   /api/v1/feed/following → FeedScope.FOLLOWING,
    OFFICIAL,
    NEIGHBORHOOD_QA (user's joined communities)
 *
 * The FeedSort enum (HOT / NEW / TOP) is then applied on top of whichever
 * pool this scope produces.
 *
 * NOTE: The old FeedScope.ALL (raw platform-wide dump) is intentionally removed.
 * "For You" is strictly better — same breadth of posts but interest-ranked.
 * The legacy /all endpoint now maps to FOR_YOU for backward compatibility.
 */
public enum FeedScope {

    /**
     * HLIG-personalised candidate pool.
     * Posts are interest-scored, geo-weighted, and seen-deduped before
     * the FeedSort metric is applied on top.
     * Replaces the old raw "All" tab entirely.
     */
    FOR_YOU,

    /**
     * Geo-filtered pool — user's pincode → district → state waterfall.
     * No personalisation on the pool; FeedSort metric ranks the results.
     */
    LOCATION,

    /**
     * Community-filtered pool — only posts from communities the user joined.
     * No personalisation on the pool; FeedSort metric ranks the results.
     */
    FOLLOWING,
    OFFICIAL,
    NEIGHBORHOOD_QA
}
