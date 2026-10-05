package com.govlyx.AI.service;

import com.govlyx.AI.config.Constant;
import com.govlyx.AI.enums.FeedScope;
import com.govlyx.AI.enums.FeedSort;
import com.govlyx.AI.model.SocialPost;
import com.govlyx.AI.model.User;
import com.govlyx.AI.repository.SocialPostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * HLIGFeedService — HLIG v2 feed engine.
 *
 * ═══════════════════════════════════════════════════════════════════
 *  LANGUAGE SUPPORT (v3 additions)
 * ═══════════════════════════════════════════════════════════════════
 *
 *  1. preferredLangs is resolved ONCE per feed request and passed into
 *     every scorer call, so language preference affects scoring for both
 *     WARMING and WARM phase feeds.
 *
 *  2. Language-aware candidate widening:
 *       - When the primary geo candidate pool is sparse AND the user has
 *         an established language preference, a language-filtered query
 *         is run BEFORE the full platform widening pass.
 *       - This surfaces same-language content from across India before
 *         falling back to all-language content, preserving regional
 *         identity without over-restricting the candidate pool.
 *
 *  3. Diversity slot language rule:
 *       - Diversity posts now PREFER a language different from the user's
 *         top preferred language. This prevents language echo chambers while
 *         still scoring them down via HLIGScorer.languageBoost(MISMATCH).
 *         The scorer handles the ranking penalty; the assembler just ensures
 *         the diversity post isn't accidentally in the user's primary language.
 *
 * ═══════════════════════════════════════════════════════════════════
 *  PUBLIC API — unchanged from v2
 * ═══════════════════════════════════════════════════════════════════
 *
 *   getBrowseFeed(user, scope, sort, lastPostId, size)
 *
 * ═══════════════════════════════════════════════════════════════════
 *  ADDITIONAL REPO METHODS REQUIRED (language support)
 * ═══════════════════════════════════════════════════════════════════
 *
 *  // State-level candidates filtered to a set of language codes
 *  @Query("""
 *    SELECT p FROM SocialPost p
 *    WHERE p.status = 'ACTIVE'
 *      AND p.statePrefix = :statePrefix
 *      AND p.language IN :languages
 *    ORDER BY p.engagementScore DESC NULLS LAST
 *  """)
 *  List<SocialPost> findActivePostsByStateAndLanguages(
 *      @Param("statePrefix") String statePrefix,
 *      @Param("languages")   List<String> languages,
 *      Pageable pageable);
 *
 *  // Platform-wide candidates filtered to a set of language codes
 *  @Query("""
 *    SELECT p FROM SocialPost p
 *    WHERE p.status = 'ACTIVE'
 *      AND p.language IN :languages
 *    ORDER BY p.createdAt DESC
 *  """)
 *  List<SocialPost> findActivePostsByLanguages(
 *      @Param("languages") List<String> languages,
 *      Pageable pageable);
 *
 * ═══════════════════════════════════════════════════════════════════
 *  TRANSACTION NOTE — unchanged from v2
 * ═══════════════════════════════════════════════════════════════════
 *
 *   @Transactional(NOT_SUPPORTED) — no Hibernate session wraps this
 *   class. All repository calls open their own short transactions.
 *   DO NOT call p.getUser() on candidate entities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
public class HLIGFeedService {

    private final SocialPostRepo         postRepo;
    private final InterestProfileService interestService;
    private final HLIGScorer             scorer;
    private final TopicExtractor         topicExtractor;
    private final org.springframework.cache.CacheManager cacheManager;

    private static final int OWN_POST_INJECT_LIMIT  = 3;
    private static final int CANDIDATE_SPARSE_FLOOR = 20;

    /**
     * FIX MEMORY LEAK #7 — hard cap on pool map size.
     * Each widening pass (geo → state → language → platform) adds up to
     * HLIG_CANDIDATE_LIMIT entries. Without a cap the pool can hold
     * 4 × HLIG_CANDIDATE_LIMIT SocialPost references simultaneously, all
     * live for the duration of the scoring step.
     * This cap is applied after each widening pass inside fetchBrowsePool().
     * It must be ≥ (requested feed size × 2) to leave room for diversity injection.
     */
    private static final int POOL_MAX_SIZE =
            Math.max(Constant.HLIG_CANDIDATE_LIMIT * 2, 400);

    /** Minimum pool size for HOT before extending to 7-day window. */
    private static final int HOT_MIN_POOL = 5;

    /** 72-hour window in milliseconds. */
    private static final long WINDOW_72H_MS = 72L * 60 * 60 * 1000;

    /** 7-day extended window in milliseconds (HOT fallback). */
    private static final long WINDOW_7D_MS = 7L * 24 * 60 * 60 * 1000;

    // =========================================================================
    // PUBLIC — single entry point for all 9 feed combinations
    // =========================================================================

    /**
     * Returns a sorted, cursor-windowed list of posts for the given scope + sort.
     *
     * Caller must request size+1 posts so SocialPostService can detect hasMore
     * without a separate COUNT query.
     */
    public List<SocialPost> getBrowseFeed(
            User user, FeedScope scope, FeedSort sort, Long lastPostId, int size) {

        List<SocialPost> sorted;
        String cacheKey;
        if (scope == FeedScope.FOR_YOU) {
            cacheKey = user.getId() + "_" + sort.name();
        } else if (scope == FeedScope.LOCATION) {
            cacheKey = "LOC_" + (user.hasPincode() ? user.getPincode() : (user.getStatePrefix() != null ? user.getStatePrefix() : "ALL")) + "_" + sort.name();
        } else if (scope == FeedScope.FOLLOWING) {
            cacheKey = "FOL_" + user.getId() + "_" + sort.name();
        } else {
            cacheKey = null;
        }

        org.springframework.cache.Cache cache = cacheManager != null ? cacheManager.getCache("hlig_feed") : null;
        List<SocialPost> cachedList = null;
        if (cache != null && cacheKey != null) {
            cachedList = cache.get(cacheKey, List.class);
        }

        if (cachedList != null) {
            sorted = cachedList;
            log.debug("[HLIG] Feed CACHE HIT for key={}", cacheKey);
        } else {
            if (scope == FeedScope.FOR_YOU) {
                sorted = getPersonalisedPoolSortedBy(user, sort, Constant.HLIG_CANDIDATE_LIMIT);
            } else {
                sorted = sortBrowsePool(fetchBrowsePool(user, scope, sort), sort);
            }
            if (cache != null && cacheKey != null && sorted != null && !sorted.isEmpty()) {
                cache.put(cacheKey, sorted);
                log.debug("[HLIG] Feed CACHE MISS for key={}. Cached {} posts", cacheKey, sorted.size());
            }
        }

        if (sorted == null || sorted.isEmpty()) {
            if (lastPostId != null) {
                log.debug("[HLIG] BROWSE scope={} sort={}: empty on page 2+ — skipping absoluteFallback userId={}",
                        scope, sort, user.getId());
                sorted = new ArrayList<>();
            } else {
                log.debug("[HLIG] BROWSE scope={} sort={}: empty — absoluteFallback userId={}",
                        scope, sort, user.getId());
                sorted = absoluteFallback(user, sort, size * 2);
            }
        }

        return applyCursorWindow(sorted, lastPostId, size);
    }

    // =========================================================================
    // PRIVATE — FOR YOU: phase-scored pool, re-ranked by sort
    // =========================================================================

    private List<SocialPost> getPersonalisedPoolSortedBy(
            User user, FeedSort sort, int candidateSize) {

        InterestProfileService.Phase phase = interestService.getUserPhase(user.getId());

        List<SocialPost> pool = switch (phase) {
            case COLD    -> getColdFeed(user, candidateSize);
            case WARMING -> getWarmingFeed(user, candidateSize);
            case WARM    -> getWarmFeed(user, candidateSize);
        };

        if (pool.isEmpty()) {
            log.debug("[HLIG] FOR_YOU pool empty (phase={}) — absoluteFallback userId={}",
                    phase, user.getId());
            pool = absoluteFallback(user, sort, candidateSize);
        }

        return applySort(pool, sort);
    }

    // =========================================================================
    // PRIVATE — BROWSE POOL: scope-specific candidate fetching
    // =========================================================================

    private List<SocialPost> fetchBrowsePool(User user, FeedScope scope, FeedSort sort) {
        Map<Long, SocialPost> pool = new LinkedHashMap<>();
        Date since72h = new Date(System.currentTimeMillis() - WINDOW_72H_MS);

        switch (scope) {

            case LOCATION -> {
                if (user.hasPincode()) {
                    switch (sort) {
                        case HOT ->
                                postRepo.findHotPostsForUser(
                                        user.getPincode(), user.getDistrictPrefix(), since72h,
                                        PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT)
                                ).forEach(p -> pool.put(p.getId(), p));

                        case TOP ->
                                postRepo.findTopPostsForUser(
                                        user.getStatePrefix(),
                                        PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT)
                                ).forEach(p -> pool.put(p.getId(), p));

                        case NEW ->
                                postRepo.findNewPostsForUser(
                                        user.getStatePrefix(),
                                        PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT)
                                ).forEach(p -> pool.put(p.getId(), p));
                    }
                }

                // Sparse widening — language-aware then state then platform
                if (pool.size() < CANDIDATE_SPARSE_FLOOR) {
                    log.debug("[HLIG] LOCATION {} sparse ({}) — widening to state", sort, pool.size());
                    widenToState(user, sort, since72h, pool);
                    capPool(pool); // FIX LEAK #7
                }
                if (pool.size() < CANDIDATE_SPARSE_FLOOR) {
                    log.debug("[HLIG] LOCATION {} still sparse ({}) — language-aware widening", sort, pool.size());
                    widenByLanguage(user, pool);
                    capPool(pool); // FIX LEAK #7
                }
                if (pool.size() < CANDIDATE_SPARSE_FLOOR) {
                    log.debug("[HLIG] LOCATION {} still sparse ({}) — widening to platform", sort, pool.size());
                    widenToPlatform(sort, since72h, pool);
                }
            }

            case FOLLOWING -> {
                postRepo.findPostsFromUserCommunities(
                        user.getId(), PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT)
                ).forEach(p -> pool.put(p.getId(), p));

                if (pool.isEmpty()) {
                    log.debug("[HLIG] FOLLOWING: userId={} no communities — cold fallback", user.getId());
                    fetchCandidates(user, Constant.HLIG_CANDIDATE_LIMIT)
                            .forEach(p -> pool.put(p.getId(), p));
                }
            }

            default -> {
                log.warn("[HLIG] fetchBrowsePool called with scope=FOR_YOU — using active fallback");
                postRepo.findRecentActivePosts(PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT))
                        .forEach(p -> pool.put(p.getId(), p));
            }
        }

        injectOwnPosts(user, pool);
        capPool(pool); // FIX LEAK #7 — final cap before materialising
        
        // Ensure Neighborhood Q&A posts never leak into standard feeds
        return pool.values().stream()
                .filter(p -> p.getCategory() != com.govlyx.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
                .collect(Collectors.toList());
    }

    private void widenToState(User user, FeedSort sort, Date since72h,
                              Map<Long, SocialPost> pool) {
        if (user.getStatePrefix() == null) return;

        List<SocialPost> statePosts = switch (sort) {
            case HOT ->
                    postRepo.findHotPostsForUser(
                            null, user.getStatePrefix(), since72h,
                            PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT));
            case TOP ->
                    postRepo.findTopPostsForUser(
                            user.getStatePrefix(),
                            PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT));
            case NEW ->
                    postRepo.findNewPostsForUser(
                            user.getStatePrefix(),
                            PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT));
        };

        statePosts.forEach(p -> pool.putIfAbsent(p.getId(), p));
    }

    /**
     * Language-aware widening — runs BEFORE platform widening.
     *
     * When the state-level pool is still sparse, we fetch same-language posts
     * from across India before falling back to all-language platform posts.
     *
     * This is important for users in low-density pincodes (e.g. rural Tamil Nadu)
     * who want Tamil content but have few local Tamil posts. Without this pass,
     * the platform widening would flood their feed with Hindi posts from UP/MH
     * which dominate by sheer volume.
     *
     * If the user has no language preference (COLD / early WARMING), this method
     * is a no-op and the platform widening pass handles the sparse pool normally.
     */
    private void widenByLanguage(User user, Map<Long, SocialPost> pool) {
        List<String> preferredLangs = interestService.getPreferredLanguages(user.getId());
        if (preferredLangs.isEmpty()) return;

        // Include "en" + "mixed" as secondary languages so the query isn't too restrictive
        List<String> queryLangs = new ArrayList<>(preferredLangs);
        if (!queryLangs.contains("en"))    queryLangs.add("en");
        if (!queryLangs.contains("mixed")) queryLangs.add("mixed");

        try {
            postRepo.findActivePostsByLanguages(
                    queryLangs,
                    PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT)
            ).forEach(p -> pool.putIfAbsent(p.getId(), p));

            log.debug("[HLIG] language widening: {} posts added for langs={} userId={}",
                    pool.size(), queryLangs, user.getId());
        } catch (Exception e) {
            // Non-fatal — fall through to platform widening
            log.debug("[HLIG] language widening failed (non-fatal): {}", e.getMessage());
        }
    }

    private void widenToPlatform(FeedSort sort, Date since72h, Map<Long, SocialPost> pool) {
        switch (sort) {
            case HOT ->
                    postRepo.findHotActivePostsForFeed(
                            since72h,
                            PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT_SPARSE)
                    ).forEach(p -> pool.putIfAbsent(p.getId(), p));

            case TOP ->
                    postRepo.findTopActivePostsForFeed(
                            PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT_SPARSE)
                    ).forEach(p -> pool.putIfAbsent(p.getId(), p));

            case NEW ->
                    postRepo.findAllActivePostsForFeed(
                            PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT_SPARSE)
                    ).forEach(p -> pool.putIfAbsent(p.getId(), p));
        }
    }

    // =========================================================================
    // PRIVATE — SORT APPLICATION
    // =========================================================================

    private List<SocialPost> applySort(List<SocialPost> candidates, FeedSort sort) {
        return switch (sort) {
            case HOT -> applyHotSort(candidates);
            case NEW -> applyNewSort(candidates);
            case TOP -> applyTopSort(candidates);
        };
    }

    private List<SocialPost> sortBrowsePool(List<SocialPost> candidates, FeedSort sort) {
        return applySort(candidates, sort);
    }

    private List<SocialPost> applyHotSort(List<SocialPost> candidates) {
        long now72hAgo = System.currentTimeMillis() - WINDOW_72H_MS;
        long now7dAgo  = System.currentTimeMillis() - WINDOW_7D_MS;

        List<SocialPost> active = activeOnly(candidates);

        List<SocialPost> window72h = active.stream()
                .filter(p -> p.getCreatedAt() != null
                        && p.getCreatedAt().getTime() >= now72hAgo)
                .sorted(hotComparator())
                .collect(Collectors.toList());

        if (window72h.size() >= HOT_MIN_POOL) return window72h;

        log.debug("[HLIG] HOT 72h pool size={} < {} — extending to 7d window",
                window72h.size(), HOT_MIN_POOL);

        List<SocialPost> window7d = active.stream()
                .filter(p -> p.getCreatedAt() != null
                        && p.getCreatedAt().getTime() >= now7dAgo)
                .sorted(hotComparator())
                .collect(Collectors.toList());

        if (window7d.size() >= HOT_MIN_POOL) return window7d;

        // Sparse-platform ultimate fallback: remove time window entirely.
        // On a platform with 10 users posting once a week the 7-day window
        // still returns < HOT_MIN_POOL posts. Return ALL active posts sorted
        // by viralityScore DESC, createdAt DESC so the feed is never empty.
        log.debug("[HLIG] HOT 7d pool size={} < {} — sparse platform: returning all active posts",
                window7d.size(), HOT_MIN_POOL);
        return active.stream()
                .sorted(hotComparator())
                .collect(Collectors.toList());
    }

    private List<SocialPost> applyNewSort(List<SocialPost> candidates) {
        return activeOnly(candidates).stream()
                .sorted(Comparator.comparingLong(
                        (SocialPost p) -> -(p.getCreatedAt() != null ? p.getCreatedAt().getTime() : 0L)))
                .collect(Collectors.toList());
    }

    private List<SocialPost> applyTopSort(List<SocialPost> candidates) {
        return activeOnly(candidates).stream()
                .sorted(Comparator
                        .comparingDouble(
                                (SocialPost p) -> -(p.getEngagementScore() != null ? p.getEngagementScore() : 0.0))
                        .thenComparingLong(
                                p -> -(p.getCreatedAt() != null ? p.getCreatedAt().getTime() : 0L)))
                .collect(Collectors.toList());
    }

    private Comparator<SocialPost> hotComparator() {
        return Comparator
                .comparingDouble(
                        (SocialPost p) -> -(p.getViralityScore() != null ? p.getViralityScore() : 0.0))
                .thenComparingLong(
                        p -> -(p.getCreatedAt() != null ? p.getCreatedAt().getTime() : 0L));
    }

    private List<SocialPost> activeOnly(List<SocialPost> posts) {
        return posts.stream()
                .filter(p -> p.getStatus() != null && "ACTIVE".equals(p.getStatus().name()))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // PRIVATE — HLIG PHASE FEEDS (FOR YOU scoring internals)
    // =========================================================================

    private List<SocialPost> getColdFeed(User user, int size) {
        List<SocialPost> candidates = fetchCandidates(user, Constant.HLIG_CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            log.info("[HLIG] COLD: 0 posts in DB for userId={}", user.getId());
            return Collections.emptyList();
        }

        long      sessionSeed = ThreadLocalRandom.current().nextLong();
        Set<Long> ownPostIds  = buildOwnPostIdSet(user);

        List<SocialPost> eligible = candidates.stream()
                .filter(p -> ownPostIds.contains(p.getId()) || p.isEligibleForRecommendation())
                .collect(Collectors.toList());

        // Sparse-platform fix: always relax to ACTIVE-only when eligible posts are
        // fewer than the requested feed size. The old threshold Math.min(size, 50)
        // equated to 50 (since size = HLIG_CANDIDATE_LIMIT = 200) which was never
        // triggered on a platform with fewer than 50 quality-eligible posts.
        if (eligible.size() < size) {
            log.debug("[HLIG] COLD eligible pool {} < {} — relaxing to ACTIVE-only", eligible.size(), size);
            eligible = activeOnly(candidates);
        }

        List<SocialPost> scored = eligible.stream()
                .sorted(Comparator.comparingDouble(p -> {
                    double base = ownPostIds.contains(p.getId())
                            ? scorer.scoreColdForOwn(user, p)
                            : scorer.scoreCold(user, p);
                    double jitter = 1.0 + ((((p.getId() ^ sessionSeed) & 0xFF) / 255.0) - 0.5) * 0.20;
                    return -(base * jitter);
                }))
                .limit(size)
                .collect(Collectors.toList());

        fireImplicitViewSignals(user, scored, size);
        return scored;
    }

    /**
     * WARMING phase — 50/50 blend of HLIG interest score and cold popularity score.
     *
     * Language preference is incorporated via scoreWarmingWithPtf() which now
     * accepts the preferredLangs list. The language boost scales the HLIG half
     * of the blend, not the cold popularity half — this keeps the COLD fallback
     * language-neutral while still rewarding preferred-language content once the
     * user has enough signal.
     */
    private List<SocialPost> getWarmingFeed(User user, int size) {
        Map<String, Double> profile    = interestService.loadTopN(user.getId());
        Set<Long>           seen       = interestService.recentlySeenPosts(user.getId());
        List<SocialPost>    candidates = fetchCandidates(user, Constant.HLIG_CANDIDATE_LIMIT);
        // ── Language preference resolved ONCE per feed request ─────────────
        List<String>        preferredLangs = interestService.getPreferredLanguages(user.getId());

        Map<Long, Map<String, Double>> ptfCache   = buildPtfCache(candidates);
        Set<Long>                      ownPostIds = buildOwnPostIdSet(user);

        List<ScoredPost> scored = candidates.stream()
                .filter(p -> ownPostIds.contains(p.getId())
                        || (!seen.contains(p.getId()) && p.isEligibleForRecommendation()))
                .map(p -> {
                    Map<String, Double> ptf   = ptfCache.getOrDefault(p.getId(), Collections.emptyMap());
                    boolean             isOwn = ownPostIds.contains(p.getId());
                    // Language boost is baked into scoreWarmingWithPtf for HLIG half
                    double              hlig  = isOwn ? scorer.scoreColdForOwn(user, p)
                            : scorer.scoreWarmingWithPtf(user, p, profile, ptf, preferredLangs);
                    double              pop   = isOwn ? scorer.scoreColdForOwn(user, p)
                            : scorer.scoreCold(user, p);
                    return new ScoredPost(p, 0.5 * hlig + 0.5 * pop);
                })
                .sorted(Comparator.comparingDouble(s -> -s.score))
                .collect(Collectors.toList());

        // Lower threshold from hard-coded 20 to actual requested size on a sparse platform.
        // With 10 users posting once a week the scored pool will rarely reach 20.
        if (scored.size() < Math.min(size, 10)) {
            log.debug("[HLIG] WARMING: pool too small ({}) — cold fallback userId={}", scored.size(), user.getId());
            return getColdFeed(user, size);
        }

        List<SocialPost> result = scored.stream().limit(size).map(s -> s.post).collect(Collectors.toList());
        fireImplicitViewSignals(user, result, size);
        return result;
    }

    /**
     * WARM phase — full HLIG score with diversity shuffle, bubble-risk guard,
     * and language preference multiplier.
     *
     * Language preference is resolved once and passed into every scoreWarmWithPtf()
     * call. The language multiplier is a component of the final score so it
     * participates in the sort naturally — no separate language filter is applied.
     *
     * Diversity slot language rule: diversity posts are preferentially selected
     * from posts NOT in the user's primary language. This is a best-effort
     * selection — if no non-primary-language diversity posts exist we fall
     * back to any diversity post as before.
     */
    private List<SocialPost> getWarmFeed(User user, int size) {
        Map<String, Double>  profile       = interestService.loadTopN(user.getId());
        List<Long>           neighbours    = interestService.findNeighbours(user.getId());
        Set<Long>            seen          = interestService.recentlySeenPosts(user.getId());
        List<SocialPost>     candidates    = fetchCandidates(user, Constant.HLIG_CANDIDATE_LIMIT);
        long                 sessionRng    = ThreadLocalRandom.current().nextLong();
        Map<String, Integer> sessionTopics = new HashMap<>();
        // ── Language preference resolved ONCE per feed request ─────────────
        List<String>         preferredLangs = interestService.getPreferredLanguages(user.getId());
        String               primaryLang    = preferredLangs.isEmpty() ? null : preferredLangs.get(0);

        Map<Long, Map<String, Double>> ptfCache   = buildPtfCache(candidates);
        Set<Long>                      ownPostIds = buildOwnPostIdSet(user);

        boolean bubbleRisk = profile.values().stream().anyMatch(w -> w > 20.0);

        Map<Long, Integer> neighbourLikesMap = new HashMap<>();
        if (neighbours != null && !neighbours.isEmpty() && !candidates.isEmpty()) {
            try {
                List<Long> candidateIds = candidates.stream().map(SocialPost::getId).collect(Collectors.toList());
                List<Object[]> rows = postRepo.countNeighbourLikesForPosts(candidateIds, neighbours);
                if (rows != null) {
                    for (Object[] row : rows) {
                        if (row != null && row.length >= 2) {
                            Number postIdNum = (Number) row[0];
                            Number countNum = (Number) row[1];
                            if (postIdNum != null && countNum != null) {
                                neighbourLikesMap.put(postIdNum.longValue(), countNum.intValue());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[HLIG] Failed to batch query countNeighbourLikesForPosts: {}", e.getMessage());
            }
        }

        List<ScoredPost> scored = candidates.stream()
                .filter(p -> ownPostIds.contains(p.getId())
                        || (!seen.contains(p.getId()) && p.isEligibleForRecommendation()))
                .map(p -> {
                    Map<String, Double> ptf = ptfCache.getOrDefault(p.getId(), Collections.emptyMap());
                    int neighbourLikeCount = neighbourLikesMap.getOrDefault(p.getId(), 0);
                    // Pass preferred language list and batched count into the full WARM scorer
                    double s = scorer.scoreWarmWithPtf(user, p, profile, neighbourLikeCount, sessionTopics,
                            sessionRng, ptf, preferredLangs);
                    if (ownPostIds.contains(p.getId()) && s <= Constant.HLIG_SCORE_MIN_THRESHOLD) {
                        s = Constant.HLIG_SCORE_MIN_THRESHOLD + 0.001;
                    }
                    return new ScoredPost(p, s);
                })
                .filter(s -> s.score > Constant.HLIG_SCORE_MIN_THRESHOLD)
                .sorted(Comparator.comparingDouble(s -> -s.score))
                .collect(Collectors.toList());

        // Same sparse-platform fix as WARMING: lower the hard-coded 20 threshold.
        if (scored.size() < Math.min(size, 10)) {
            log.debug("[HLIG] WARM: pool too small ({}) — cold fallback userId={}", scored.size(), user.getId());
            return getColdFeed(user, size);
        }

        // Split into core-topic posts and diversity posts
        Set<String> coreTopics = profile.entrySet().stream()
                .filter(e -> e.getValue() >= Constant.HLIG_PROFILE_CORE_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        List<ScoredPost> mainFeed  = new ArrayList<>();
        List<ScoredPost> diversity = new ArrayList<>();
        for (ScoredPost sp : scored) {
            Map<String, Double> ptf = ptfCache.getOrDefault(sp.post.getId(), Collections.emptyMap());
            if (ptf.keySet().stream().anyMatch(coreTopics::contains)) mainFeed.add(sp);
            else                                                        diversity.add(sp);
        }

        // ── Language-aware diversity split ─────────────────────────────────
        // Separate diversity posts into primary-language and non-primary-language buckets.
        // Non-primary-language posts are preferred for diversity slots to prevent a
        // language echo chamber from forming within the diversity tier.
        List<ScoredPost> diversityNonPrimary = new ArrayList<>();
        List<ScoredPost> diversityPrimary    = new ArrayList<>();
        if (primaryLang != null) {
            for (ScoredPost sp : diversity) {
                if (primaryLang.equals(sp.post.safeLanguage())) diversityPrimary.add(sp);
                else                                              diversityNonPrimary.add(sp);
            }
        } else {
            diversityNonPrimary.addAll(diversity); // no preference — treat all equally
        }

        // Assemble result: interleave diversity slots, respect per-topic session caps
        List<SocialPost> result      = new ArrayList<>(size);
        int mainIdx = 0;
        int divNPIdx = 0, divPIdx = 0; // separate cursors for non-primary / primary diversity

        for (int i = 0; i < size; i++) {
            boolean isDiv = scorer.isDiversitySlot(i, sessionRng)
                    && (divNPIdx < diversityNonPrimary.size() || divPIdx < diversityPrimary.size());

            if (isDiv) {
                // Prefer non-primary-language diversity; fall back to primary-language diversity
                ScoredPost divPost;
                if (divNPIdx < diversityNonPrimary.size()) {
                    divPost = diversityNonPrimary.get(divNPIdx++);
                } else {
                    divPost = diversityPrimary.get(divPIdx++);
                }
                result.add(divPost.post);
                updateSessionContext(sessionTopics, divPost.post, ptfCache);

            } else if (mainIdx < mainFeed.size()) {
                SocialPost chosen  = null;
                int        scanIdx = mainIdx;
                while (scanIdx < mainFeed.size()) {
                    SocialPost candidate = mainFeed.get(scanIdx).post;
                    Map<String, Double> topics = ptfCache.getOrDefault(candidate.getId(), Collections.emptyMap());
                    boolean overRepresented = topics.keySet().stream()
                            .anyMatch(t -> sessionTopics.getOrDefault(t, 0) >= Constant.HLIG_SESSION_TOPIC_MAX_REPEAT);
                    if (!overRepresented) { chosen = candidate; mainIdx = scanIdx + 1; break; }
                    scanIdx++;
                }
                if (chosen == null) {
                    mainIdx = mainFeed.size();
                    if (divNPIdx < diversityNonPrimary.size()) chosen = diversityNonPrimary.get(divNPIdx++).post;
                    else if (divPIdx < diversityPrimary.size()) chosen = diversityPrimary.get(divPIdx++).post;
                }
                if (chosen != null) { result.add(chosen); updateSessionContext(sessionTopics, chosen, ptfCache); }
                else break;

            } else {
                // Main feed exhausted — pull from remaining diversity
                if (divNPIdx < diversityNonPrimary.size()) {
                    SocialPost dp = diversityNonPrimary.get(divNPIdx++).post;
                    result.add(dp); updateSessionContext(sessionTopics, dp, ptfCache);
                } else if (divPIdx < diversityPrimary.size()) {
                    SocialPost dp = diversityPrimary.get(divPIdx++).post;
                    result.add(dp); updateSessionContext(sessionTopics, dp, ptfCache);
                } else break;
            }
        }

        if (result.isEmpty()) {
            log.debug("[HLIG] WARM assembly empty — cold fallback userId={}", user.getId());
            return getColdFeed(user, size);
        }

        return result;
    }

    // =========================================================================
    // PRIVATE — CANDIDATE FETCHING (FOR YOU geo waterfall)
    // =========================================================================

    private List<SocialPost> fetchCandidates(User user, int limit) {
        Map<Long, SocialPost> byId = new LinkedHashMap<>();

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.CompletableFuture<List<SocialPost>> localFuture;
            java.util.concurrent.CompletableFuture<List<SocialPost>> districtFuture;
            java.util.concurrent.CompletableFuture<List<SocialPost>> stateFuture;

            if (user.hasPincode()) {
                localFuture = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        postRepo.findLocalFeedPosts(user.getPincode(), PageRequest.of(0, limit / 3)), executor);
                districtFuture = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        postRepo.findDistrictViralPosts(user.getDistrictPrefix(), PageRequest.of(0, limit / 4)), executor);
                stateFuture = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        postRepo.findStateViralPosts(user.getStatePrefix(), PageRequest.of(0, limit / 4)), executor);
            } else {
                localFuture = java.util.concurrent.CompletableFuture.completedFuture(Collections.emptyList());
                districtFuture = java.util.concurrent.CompletableFuture.completedFuture(Collections.emptyList());
                stateFuture = java.util.concurrent.CompletableFuture.completedFuture(Collections.emptyList());
            }

            java.util.concurrent.CompletableFuture<List<SocialPost>> nationalFuture = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                    postRepo.findNationalViralPosts(PageRequest.of(0, limit / 6)), executor);

            // Wait for all queries to complete in parallel
            java.util.concurrent.CompletableFuture.allOf(localFuture, districtFuture, stateFuture, nationalFuture).join();

            // Collect results in strict priority order (Local -> District -> State -> National)
            localFuture.join().forEach(p -> byId.put(p.getId(), p));
            districtFuture.join().forEach(p -> byId.putIfAbsent(p.getId(), p));
            stateFuture.join().forEach(p -> byId.putIfAbsent(p.getId(), p));
            nationalFuture.join().forEach(p -> byId.putIfAbsent(p.getId(), p));
        } catch (Exception e) {
            log.error("[HLIG] Parallel candidate fetching failed, falling back to sequential execution", e);
            if (user.hasPincode()) {
                postRepo.findLocalFeedPosts(user.getPincode(),            PageRequest.of(0, limit / 3))
                        .forEach(p -> byId.put(p.getId(), p));
                postRepo.findDistrictViralPosts(user.getDistrictPrefix(), PageRequest.of(0, limit / 4))
                        .forEach(p -> byId.putIfAbsent(p.getId(), p));
                postRepo.findStateViralPosts(user.getStatePrefix(),       PageRequest.of(0, limit / 4))
                        .forEach(p -> byId.putIfAbsent(p.getId(), p));
            }
            postRepo.findNationalViralPosts(PageRequest.of(0, limit / 6))
                    .forEach(p -> byId.putIfAbsent(p.getId(), p));
        }

        if (byId.size() < 50) {
            postRepo.findRecentActivePosts(PageRequest.of(0, limit))
                    .forEach(p -> byId.putIfAbsent(p.getId(), p));
        }

        // ── Language-aware candidate injection ──────────────────────────────
        // After the geo waterfall, if the pool is still sparse, inject
        // same-language posts from across India before the absolute fallback.
        // The scorer will further rank them by language boost + interest match.
        if (byId.size() < CANDIDATE_SPARSE_FLOOR) {
            List<String> preferredLangs = interestService.getPreferredLanguages(user.getId());
            if (!preferredLangs.isEmpty()) {
                log.debug("[HLIG] fetchCandidates: sparse ({}) — language-aware injection langs={}",
                        byId.size(), preferredLangs);
                List<String> queryLangs = new ArrayList<>(preferredLangs);
                if (!queryLangs.contains("en"))    queryLangs.add("en");
                if (!queryLangs.contains("mixed")) queryLangs.add("mixed");
                try {
                    postRepo.findActivePostsByLanguages(queryLangs, PageRequest.of(0, limit))
                            .forEach(p -> byId.putIfAbsent(p.getId(), p));
                } catch (Exception e) {
                    log.debug("[HLIG] language injection failed (non-fatal): {}", e.getMessage());
                }
            }
        }

        if (byId.size() < CANDIDATE_SPARSE_FLOOR) {
            log.debug("[HLIG] fetchCandidates: still sparse ({}) — loading ALL active", byId.size());
            postRepo.findAllActivePostsForFeed(PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT_SPARSE))
                    .forEach(p -> byId.putIfAbsent(p.getId(), p));
        }

        injectOwnPosts(user, byId);
        return new ArrayList<>(byId.values());
    }

    // =========================================================================
    // PRIVATE — OWN-POST INJECTION
    // =========================================================================

    /**
     * FIX MEMORY LEAK #7 — caps the pool map to POOL_MAX_SIZE.
     * Called after each widening pass so the map never holds more than
     * POOL_MAX_SIZE SocialPost references simultaneously.
     * Entries are removed from the tail of the LinkedHashMap (oldest insertions)
     * which preserves geographic/engagement priority order.
     */
    private void capPool(Map<Long, SocialPost> pool) {
        if (pool.size() <= POOL_MAX_SIZE) return;
        Iterator<Long> it = pool.keySet().iterator();
        int toRemove = pool.size() - POOL_MAX_SIZE;
        while (it.hasNext() && toRemove-- > 0) {
            it.next();
            it.remove();
        }
        log.debug("[HLIG] Pool capped at {} entries", pool.size());
    }

    private void injectOwnPosts(User user, Map<Long, SocialPost> candidateMap) {
        try {
            postRepo.findRecentPostsByUser(user.getId(), PageRequest.of(0, OWN_POST_INJECT_LIMIT))
                    .forEach(p -> candidateMap.putIfAbsent(p.getId(), p));
        } catch (Exception e) {
            log.debug("[HLIG] injectOwnPosts failed userId={}: {}", user.getId(), e.getMessage());
        }
    }

    private Set<Long> buildOwnPostIdSet(User user) {
        try {
            List<SocialPost> own = postRepo.findRecentPostsByUser(
                    user.getId(), PageRequest.of(0, OWN_POST_INJECT_LIMIT));
            Set<Long> ids = new HashSet<>(own.size());
            for (SocialPost p : own) ids.add(p.getId());
            return ids;
        } catch (Exception e) {
            log.debug("[HLIG] buildOwnPostIdSet failed userId={}: {}", user.getId(), e.getMessage());
            return Collections.emptySet();
        }
    }

    // =========================================================================
    // PRIVATE — FALLBACK
    // =========================================================================

    private List<SocialPost> absoluteFallback(User user, FeedSort sort, int size) {
        log.debug("[HLIG] absoluteFallback scope sort={} userId={}", sort, user.getId());
        Date since72h = new Date(System.currentTimeMillis() - WINDOW_72H_MS);

        return switch (sort) {
            case HOT -> {
                List<SocialPost> hot = postRepo.findHotActivePostsForFeed(
                        since72h, PageRequest.of(0, size));
                yield hot.isEmpty()
                        ? postRepo.findAllActivePostsForFeed(PageRequest.of(0, size))
                        : hot;
            }
            case TOP ->
                    postRepo.findTopActivePostsForFeed(PageRequest.of(0, size));
            case NEW ->
                    postRepo.findAllActivePostsForFeed(PageRequest.of(0, size));
        };
    }

    // =========================================================================
    // PRIVATE — SIGNALS, HELPERS
    // =========================================================================

    private void fireImplicitViewSignals(User user, List<SocialPost> posts, int limit) {
        posts.stream().limit(limit).forEach(post -> {
            try { interestService.onView(user.getId(), post.getId()); }
            catch (Exception e) {
                log.debug("[HLIG] implicit onView skipped postId={} userId={}: {}",
                        post.getId(), user.getId(), e.getMessage());
            }
        });
    }

    private Map<Long, Map<String, Double>> buildPtfCache(List<SocialPost> candidates) {
        Map<Long, Map<String, Double>> cache = new HashMap<>(candidates.size());
        for (SocialPost p : candidates) cache.put(p.getId(), topicExtractor.extract(p));
        return cache;
    }

    private void updateSessionContext(Map<String, Integer> ctx, SocialPost post,
                                      Map<Long, Map<String, Double>> ptfCache) {
        ptfCache.getOrDefault(post.getId(), Collections.emptyMap())
                .keySet().forEach(t -> ctx.merge(t, 1, Integer::sum));
    }

    // =========================================================================
    // PRIVATE — CURSOR WINDOW
    // =========================================================================

    private List<SocialPost> applyCursorWindow(List<SocialPost> sorted, Long lastPostId, int size) {
        if (sorted == null || sorted.isEmpty()) return Collections.emptyList();
        if (lastPostId == null) return sorted.stream().limit(size).collect(Collectors.toList());

        int cursorIdx = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (Objects.equals(sorted.get(i).getId(), lastPostId)) { cursorIdx = i; break; }
        }

        if (cursorIdx == -1) {
            log.debug("[HLIG] cursor {} not in pool — returning first page", lastPostId);
            return sorted.stream().limit(size).collect(Collectors.toList());
        }

        int fromIdx = Math.min(cursorIdx + 1, sorted.size());
        return sorted.subList(fromIdx, sorted.size()).stream().limit(size).collect(Collectors.toList());
    }

    // =========================================================================
    // PRIVATE — INTERNAL RECORD
    // =========================================================================

    private record ScoredPost(SocialPost post, double score) {}
}
