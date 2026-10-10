package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.dto.PostResponse;
import com.JanSahayak.AI.dto.SocialPostDto;
import com.JanSahayak.AI.enums.FeedScope;
import com.JanSahayak.AI.enums.FeedSort;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.service.PostService;
import com.JanSahayak.AI.service.SocialPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import com.JanSahayak.AI.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * FeedController
 *
 * ════════════════════════════════════════════════════════════
 *  UI LAYOUT
 * ════════════════════════════════════════════════════════════
 *
 *   Tab row:   [ For You ]  [ Location ]  [ Following ]
 *   Sort row:  [ Hot ]  [ New ]  [ Top ]
 *
 *   "For You" replaces the old raw "All" tab.
 *   The sort row applies to every tab.
 *
 * ════════════════════════════════════════════════════════════
 *  ENDPOINT MAP
 * ════════════════════════════════════════════════════════════
 *
 *  FOR YOU   →  GET /api/v1/feed/for-you?sort=HOT|NEW|TOP   (default HOT)
 *               HLIG builds a personalised candidate pool (interest-scored,
 *               geo-weighted, seen-deduped) then re-ranks it by the sort metric.
 *               Cold-start users fall back to geo-based candidates automatically.
 *
 *  LOCATION  →  GET /api/v1/feed/location?sort=HOT|NEW|TOP  (default HOT)
 *               User's pincode → district → state waterfall, sorted.
 *
 *  FOLLOWING →  GET /api/v1/feed/following?sort=HOT|NEW|TOP (default HOT)
 *               Posts from communities the user has joined, sorted.
 *               Falls back to cold feed when user has no communities yet.
 *
 * ════════════════════════════════════════════════════════════
 *  SORT VALUES
 * ════════════════════════════════════════════════════════════
 *
 *   HOT  →  viralityScore DESC, 72-hour window
 *   NEW  →  createdAt DESC (pure chronological)
 *   TOP  →  engagementScore DESC, all-time
 *
 * ════════════════════════════════════════════════════════════
 *  INFINITE SCROLL CONTRACT (same for all three tabs)
 * ════════════════════════════════════════════════════════════
 *
 *   First page:  GET /api/v1/feed/for-you?sort=HOT&size=20
 *   Next pages:  GET /api/v1/feed/for-you?sort=HOT&lastPostId={nextCursor}&size=20
 *
 *   Response shape:
 *   {
 *     "content":    [...],    // List<SocialPostDto>
 *     "hasMore":    true,
 *     "nextCursor": 12345,    // pass as ?lastPostId= on the next call
 *     "size":       20
 *   }
 *
 *   Stop fetching when hasMore = false.
 */
@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@Slf4j
public class FeedController {

    private final SocialPostService socialPostService;
    private final PostService       postService;

    // =========================================================================
    // TAB 1 — FOR YOU  (HLIG personalised pool, re-ranked by sort)
    // =========================================================================

    /**
     * FOR YOU tab.
     *
     * Step 1: HLIG phase scorer (cold / warming / warm) builds a candidate pool
     *         matched to the user's interest profile.
     * Step 2: That pool is re-ranked by the chosen sort metric.
     *
     *   ?sort=HOT  →  trending posts from topics you care about
     *   ?sort=NEW  →  latest posts from topics you care about
     *   ?sort=TOP  →  all-time best posts from topics you care about
     *
     * GET /api/v1/feed/for-you?sort=HOT&lastPostId={cursor}&size=20
     */
    @GetMapping("/for-you")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getForYouFeed(
            @CurrentUser            User     user,
            @RequestParam(defaultValue = "NEW") FeedSort sort,
            @RequestParam(required = false)     Long     lastPostId,
            @RequestParam(defaultValue = "20")  int      size) {

        size = clampSize(size);
        log.debug("[Feed] FOR-YOU sort={}: userId={} lastPostId={} size={}",
                sort, userId(user), lastPostId, size);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(socialPostService.getBrowseFeed(user, FeedScope.FOR_YOU, sort, lastPostId, size));
    }

    // =========================================================================
    // TAB 2 — LOCATION  (user's area, sorted)
    // =========================================================================

    /**
     * LOCATION tab.
     *
     * Fetches posts from the user's pincode → district → state (waterfall).
     * Automatically widens to platform-wide when the local area is sparse.
     *
     * GET /api/v1/feed/location?sort=NEW&lastPostId={cursor}&size=20
     */
    @GetMapping("/location")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getLocationFeed(
            @CurrentUser            User     user,
            @RequestParam(defaultValue = "NEW") FeedSort sort,
            @RequestParam(required = false)     Long     lastPostId,
            @RequestParam(defaultValue = "20")  int      size) {

        size = clampSize(size);
        log.debug("[Feed] LOCATION sort={}: userId={} lastPostId={} size={}",
                sort, userId(user), lastPostId, size);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(socialPostService.getBrowseFeed(user, FeedScope.LOCATION, sort, lastPostId, size));
    }

    // =========================================================================
    // TAB 3 — FOLLOWING  (joined communities, sorted)
    // =========================================================================

    /**
     * FOLLOWING tab.
     *
     * Posts from communities the user has explicitly joined.
     * Falls back to cold-feed candidates for users with no communities yet.
     *
     * GET /api/v1/feed/following?sort=TOP&lastPostId={cursor}&size=20
     */
    @GetMapping("/following")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getFollowingFeed(
            @CurrentUser            User     user,
            @RequestParam(defaultValue = "NEW") FeedSort sort,
            @RequestParam(required = false)     Long     lastPostId,
            @RequestParam(defaultValue = "20")  int      size) {

        size = clampSize(size);
        log.debug("[Feed] FOLLOWING sort={}: userId={} lastPostId={} size={}",
                sort, userId(user), lastPostId, size);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(socialPostService.getBrowseFeed(user, FeedScope.FOLLOWING, sort, lastPostId, size));
    }

    // =========================================================================
    // FEED PEEK (NEW POST COUNTS)
    // =========================================================================

    /**
     * Fast count of new posts since a given post ID.
     * Used by the frontend polling mechanism to show the "New Posts" banner.
     *
     * GET /api/v1/feed/peek?scope=FOR_YOU&afterId=12345
     */
    @GetMapping("/peek")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> getFeedPeek(
            @CurrentUser            User      user,
            @RequestParam           FeedScope scope,
            @RequestParam           Long      afterId) {

        long count = socialPostService.peekNewPostCount(user, scope, afterId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Fast count of new posts in a specific community since a given post ID.
     * Used by the NewPostsBanner inside the Communities tab.
     *
     * GET /api/v1/feed/community/{communityId}/peek?afterId=12345
     */
    @GetMapping("/community/{communityId}/peek")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> getCommunityFeedPeek(
            @PathVariable           Long communityId,
            @RequestParam           Long afterId) {

        long count = socialPostService.peekCommunityPostCount(communityId, afterId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // =========================================================================
    // SUPPORTING FEED ENDPOINTS  (not main tabs)
    // =========================================================================

    /**
     * NEIGHBORHOOD Q&A tab.
     *
     * Fetches Q&A posts from the user's location via 4-layer scope.
     *
     * GET /api/v1/feed/neighborhood-qa?scope=NEARBY&lastPostId={cursor}&size=20
     */
    @GetMapping("/neighborhood-qa")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getNeighborhoodQAFeed(
            @CurrentUser            User     user,
            @RequestParam(defaultValue = "NEARBY") String scope, // NEARBY, AREA, CITY, or DISTRICT
            @RequestParam(defaultValue = "HOT") FeedSort sort,
            @RequestParam(required = false)     Long     lastPostId,
            @RequestParam(defaultValue = "20")  int      size) {

        size = clampSize(size);
        log.debug("[Feed] NEIGHBORHOOD-QA scope={} sort={}: userId={} lastPostId={} size={}",
                scope, sort, userId(user), lastPostId, size);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(socialPostService.getNeighborhoodQAFeed(user, scope, sort, lastPostId, size));
    }

    /**
     * User profile posts.
     *
     * GET /api/v1/feed/user/{userId}?beforeId={cursor}&limit=20
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getUserFeed(
            @PathVariable                   Long userId,
            @CurrentUser                    User currentUser,
            @RequestParam(required = false)  Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {

        limit = clampSize(limit);
        return ResponseEntity.ok(
                socialPostService.getUserPosts(userId, currentUser, beforeId, limit));
    }

    /**
     * Hashtag feed.
     *
     * GET /api/v1/feed/hashtag/{hashtag}?beforeId={cursor}&limit=20
     */
    @GetMapping("/hashtag/{hashtag}")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getHashtagFeed(
            @PathVariable                   String hashtag,
            @CurrentUser                    User   user,
            @RequestParam(required = false)  Long   beforeId,
            @RequestParam(defaultValue = "20") int  limit) {

        limit = clampSize(limit);
        return ResponseEntity.ok(
                socialPostService.searchByHashtag(hashtag, user, beforeId, limit));
    }

    /**
     * Local issue posts — geo waterfall: pincode → district → state → national.
     *
     * Backed by PostService (BroadcastScope issue posts, not social posts).
     * Returns PostResponse, not SocialPostDto.
     *
     * GET /api/v1/feed/local?beforeId={cursor}&limit=20
     */
    @GetMapping("/local")
    public ResponseEntity<PaginatedResponse<PostResponse>> getLocalFeed(
            @CurrentUser        User user,
            @RequestParam(defaultValue = "NEW") FeedSort sort,
            @RequestParam(required = false)  Long beforeId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) String targetPincode) {

        limit = clampSize(limit);
        log.debug("[Feed] LOCAL sort={}: userId={} beforeId={} limit={}", sort, userId(user), beforeId, limit);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(15, TimeUnit.SECONDS)
                        .staleWhileRevalidate(30, TimeUnit.SECONDS))
                .body(postService.getLocalFeed(user, sort, beforeId, limit, pincode, targetPincode));
    }

    /**
     * Official government broadcasts only.
     * Filtered by department/admin roles and user's geographic location.
     *
     * GET /api/v1/feed/official?beforeId={cursor}&limit=20
     */
    @GetMapping("/official")
    public ResponseEntity<PaginatedResponse<PostResponse>> getOfficialFeed(
            @CurrentUser        User user,
            @RequestParam(defaultValue = "NEW") FeedSort sort,
            @RequestParam(required = false)  Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {

        limit = clampSize(limit);
        log.debug("[Feed] OFFICIAL sort={}: userId={} beforeId={} limit={}", sort, userId(user), beforeId, limit);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(15, TimeUnit.SECONDS)
                        .staleWhileRevalidate(30, TimeUnit.SECONDS))
                .body(postService.getOfficialFeed(user, sort, beforeId, limit));
    }


    // =========================================================================
    // HLIG NEGATIVE SIGNAL ENDPOINTS  (fire-and-forget)
    // =========================================================================

    /**
     * User scrolled past a post without engaging.
     * Fires a weak negative interest signal. Always returns 204.
     *
     * POST /api/v1/feed/signal/scroll-past
     * Body: { "postId": 123 }
     */
    @PostMapping("/signal/scroll-past")
    public ResponseEntity<Void> recordScrolledPast(
            @CurrentUser User              user,
            @RequestBody             Map<String, Long> body) {

        Long postId = body.get("postId");
        if (postId != null) {
            socialPostService.recordScrolledPast(postId, user);
            log.debug("[HLIG] scroll-past: postId={} userId={}", postId, userId(user));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * User tapped "Not Interested".
     * Fires a strong negative signal — suppresses this topic in future For You scoring.
     *
     * POST /api/v1/feed/signal/not-interested
     * Body: { "postId": 123 }
     */
    @PostMapping("/signal/not-interested")
    public ResponseEntity<Map<String, Object>> recordNotInterested(
            @CurrentUser User              user,
            @RequestBody             Map<String, Long> body) {

        Long postId = body.get("postId");
        if (postId != null) {
            socialPostService.recordNotInterested(postId, user);
            log.info("[HLIG] not-interested: postId={} userId={}", postId, userId(user));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "We'll show you less content like this."
        ));
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /** Clamps page size to [1, FEED_MAX_PAGE_SIZE]. */
    private int clampSize(int size) {
        return Math.max(1, Math.min(size, Constant.FEED_MAX_PAGE_SIZE));
    }

    /** Safe userId extraction for logging — guards against unauthenticated calls. */
    private String userId(User user) {
        return (user != null && user.getId() != null) ? user.getId().toString() : "anon";
    }
}
