package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserInterestProfileRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * HLIGScorer — HLIG v2 relevance scorer.
 *
 * Computes a personalised relevance score for one (user, post) pair.
 *
 * ═══════════════════════════════════════════════════════════════════
 *  FORMULA
 * ═══════════════════════════════════════════════════════════════════
 *
 *   score = InterestMatch × GeoProximity × Freshness
 *         × NeighbourBoost × QualityGate × TrendingBoost
 *         × SessionPenalty × LanguageBoost          ← NEW
 *
 * ═══════════════════════════════════════════════════════════════════
 *  LANGUAGE BOOST (new component)
 * ═══════════════════════════════════════════════════════════════════
 *
 *  LanguageBoost reads the post's `language` column (set by PostLanguageDetector)
 *  and compares it against the user's preferred language list (stored as "lang:XX"
 *  topics in UserInterestProfile).
 *
 *  Multipliers:
 *    2.0  — post language exactly matches user's top preferred language
 *    1.5  — post language is in user's preferred list (not the #1 slot)
 *    1.3  — post is "mixed" (contains 2+ scripts, readable by all)
 *    1.0  — post is English (lingua franca — no penalty, no boost)
 *    0.5  — post is in a language the user has not shown preference for
 *           (still surfaced for diversity; scored lower so it doesn't
 *            dominate the feed for a user who can't read the script)
 *
 *  When to skip the language boost:
 *    - User has no established language preference (COLD / early WARMING phase
 *      with totalSignals < 10): return 1.0 (neutral). Without preference data
 *      we must not penalise any language.
 *    - Post language is null or blank: treated as "en" (neutral).
 *
 * ═══════════════════════════════════════════════════════════════════
 *  PUBLIC API  (called by HLIGFeedService)
 * ═══════════════════════════════════════════════════════════════════
 *
 *   scoreWarmWithPtf()     — hot path for WARM users
 *   scoreWarmingWithPtf()  — hot path for WARMING users
 *   scoreCold()            — popularity-based score for COLD users
 *   scoreColdForOwn()      — scoreCold variant that skips eligibility gate
 *   isDiversitySlot()      — determines if a feed slot is a diversity slot
 *
 * All public methods accept a pre-built ptf (post topic frequency) map.
 * HLIGFeedService builds a ptfCache once per request via TopicExtractor,
 * so topicExtractor.extract() is never called inside this class.
 *
 * ═══════════════════════════════════════════════════════════════════
 *  DESIGN NOTES
 * ═══════════════════════════════════════════════════════════════════
 *
 * All magic numbers live in Constant.java — this class contains no
 * inline numeric literals except mathematical constants (0.0, 1.0, 100.0).
 *
 * Component functions (geoProximity, freshness, etc.) are private.
 * They are tested via the public scoring methods, not directly.
 */
@Component
@RequiredArgsConstructor
public class HLIGScorer {

    private final UserInterestProfileRepo repo;

    // ── Language boost constants ────────────────────────────────────────────
    // Not in Constant.java to keep language concerns localised here.
    // If product decides to tune these, change only this file.
    private static final double LANG_BOOST_EXACT_MATCH  = 2.0;
    private static final double LANG_BOOST_PREF_LIST    = 1.5;
    private static final double LANG_BOOST_MIXED        = 1.3;
    private static final double LANG_BOOST_ENGLISH      = 1.0;
    private static final double LANG_BOOST_MISMATCH     = 0.5;

    /** Minimum total signal count before language preference is applied.
     *  Below this count the user hasn't read enough posts to have a reliable
     *  preference — applying the mismatch penalty would be premature. */
    private static final int LANG_MIN_SIGNALS_FOR_PREF  = 10;

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Full HLIG score for a WARM phase user.
     *
     * Called by HLIGFeedService.getWarmFeed() with a pre-built ptf map to
     * avoid calling TopicExtractor more than once per post per request.
     *
     * @param user            requesting user
     * @param post            candidate post
     * @param userProfile     decayed top-N topic → weight map (from InterestProfileService.loadTopN)
     * @param neighbours      up to 50 neighbour user IDs (from InterestProfileService.findNeighbours)
     * @param sessionTopics   topics already shown 3+ times this session (session diversity cap)
     * @param sessionRng      per-session random seed (diversity slot randomisation)
     * @param ptf             pre-extracted post topic frequency map (from TopicExtractor.extract)
     * @param preferredLangs  ordered list of BCP-47 codes the user prefers (from InterestProfileService)
     * @return                relevance score ≥ 0.0; 0.0 means do not show
     */
    public double scoreWarmWithPtf(
            User                 user,
            SocialPost           post,
            Map<String, Double>  userProfile,
            int                  neighbourLikeCount,
            Map<String, Integer> sessionTopics,
            long                 sessionRng,
            Map<String, Double>  ptf,
            List<String>         preferredLangs) {

        if (!post.isEligibleForRecommendation()) return 0.0;

        double quality = resolveQuality(post);
        if (quality < Constant.HLIG_MIN_QUALITY) return 0.0;

        double interestMatch  = normalisedDotProduct(userProfile, ptf);
        double geo            = geoProximity(user, post);
        double freshness      = freshness(post);
        double neighbourBoost = Math.min(Constant.HLIG_NEIGHBOUR_BOOST_MAX,
                1.0 + (Constant.HLIG_NEIGHBOUR_LIKE_STEP * neighbourLikeCount));
        double trendingBoost  = trendingBoost(post);
        double sessionPenalty = sessionPenalty(ptf, sessionTopics);
        double qualityMult    = quality / 100.0;
        double langBoost      = languageBoost(post, preferredLangs, totalSignals(userProfile));

        return Math.max(0.0,
                interestMatch * geo * freshness * neighbourBoost
                        * trendingBoost * sessionPenalty * qualityMult * langBoost);
    }

    public double scoreWarmWithPtf(
            User                 user,
            SocialPost           post,
            Map<String, Double>  userProfile,
            List<Long>           neighbours,
            Map<String, Integer> sessionTopics,
            long                 sessionRng,
            Map<String, Double>  ptf,
            List<String>         preferredLangs) {
        int count = (neighbours == null || neighbours.isEmpty()) ? 0 : repo.countNeighbourLikes(post.getId(), neighbours);
        return scoreWarmWithPtf(user, post, userProfile, count, sessionTopics, sessionRng, ptf, preferredLangs);
    }

    /**
     * Backward-compatible overload — no language preference (COLD callers).
     * Delegates to the full overload with an empty preferred-language list.
     */
    public double scoreWarmWithPtf(
            User                 user,
            SocialPost           post,
            Map<String, Double>  userProfile,
            List<Long>           neighbours,
            Map<String, Integer> sessionTopics,
            long                 sessionRng,
            Map<String, Double>  ptf) {
        return scoreWarmWithPtf(user, post, userProfile, neighbours, sessionTopics, sessionRng, ptf,
                Collections.emptyList());
    }

    /**
     * Simpler score for WARMING phase users.
     * No neighbour boost, no session context — interest × geo × freshness × quality × language.
     *
     * Called by HLIGFeedService.getWarmingFeed() with a pre-built ptf map.
     *
     * @param preferredLangs ordered BCP-47 language codes the user prefers
     * @return    relevance score ≥ 0.0; 0.0 means do not show
     */
    public double scoreWarmingWithPtf(
            User                user,
            SocialPost          post,
            Map<String, Double> userProfile,
            Map<String, Double> ptf,
            List<String>        preferredLangs) {

        if (!post.isEligibleForRecommendation()) return 0.0;

        double interestMatch = normalisedDotProduct(userProfile, ptf);
        double geo           = geoProximity(user, post);
        double freshness     = freshness(post);
        double quality       = resolveQuality(post) / 100.0;
        double langBoost     = languageBoost(post, preferredLangs, totalSignals(userProfile));

        return interestMatch * geo * freshness * quality * langBoost;
    }

    /**
     * Backward-compatible overload for WARMING — no language preference.
     */
    public double scoreWarmingWithPtf(
            User                user,
            SocialPost          post,
            Map<String, Double> userProfile,
            Map<String, Double> ptf) {
        return scoreWarmingWithPtf(user, post, userProfile, ptf, Collections.emptyList());
    }

    /**
     * Popularity-based score for COLD phase users (no interaction history).
     * Pure engagement × geo × freshness — no interest matching, no language boost.
     *
     * Defaults engagementScore to 1.0 when null or 0 so that geo + freshness
     * still differentiate posts on brand-new platforms with no engagement data.
     *
     * @return relevance score ≥ 0.0; 0.0 if post fails eligibility gate
     */
    public double scoreCold(User user, SocialPost post) {
        if (!post.isEligibleForRecommendation()) return 0.0;
        return scoreColdCore(user, post);
    }

    /**
     * scoreCold variant for the creator's own posts.
     *
     * Skips isEligibleForRecommendation() — the service layer has already decided
     * to include own posts in the candidate pool regardless of quality score.
     * Gives own posts a quality floor of POST_QUALITY_DEFAULT so they rank
     * meaningfully even when the quality scorer hasn't run yet on a brand-new post.
     *
     * @return relevance score > 0.0 (always ranks, never filtered)
     */
    public double scoreColdForOwn(User user, SocialPost post) {
        return scoreColdCore(user, post);
    }

    /**
     * Determines whether a feed slot should show a diversity post.
     *
     * ~1 in 7 slots is a diversity slot (≈15%). Deterministic for the same
     * (slotIndex, sessionRng) pair — stable within a scroll session.
     *
     * @param slotIndex  0-based position in the current feed page
     * @param sessionRng random seed generated once per feed request
     */
    public boolean isDiversitySlot(int slotIndex, long sessionRng) {
        return ((sessionRng + slotIndex * 31L) % 7) == 0;
    }

    // =========================================================================
    // PRIVATE — LANGUAGE BOOST
    // =========================================================================

    /**
     * Language multiplier for a post given the user's preferred language list.
     *
     * The multiplier is applied AFTER all other score components so that
     * language preference scales the final score proportionally rather than
     * capping it.
     *
     * @param post           candidate post (carries the detected language code)
     * @param preferredLangs ordered BCP-47 list from InterestProfileService
     * @param totalSignals   inferred from userProfile size (used for cold-start guard)
     * @return multiplier in {0.5, 1.0, 1.3, 1.5, 2.0}
     */
    double languageBoost(SocialPost post, List<String> preferredLangs, int totalSignals) {
        String postLang = post.safeLanguage();

        // "mixed" posts are universally accessible — always get the mixed boost
        if ("mixed".equals(postLang)) return LANG_BOOST_MIXED;

        // English is the cross-language lingua franca — no adjustment
        if ("en".equals(postLang)) return LANG_BOOST_ENGLISH;

        // Cold-start / insufficient signals: don't penalise any language
        if (totalSignals < LANG_MIN_SIGNALS_FOR_PREF || preferredLangs == null || preferredLangs.isEmpty()) {
            return LANG_BOOST_ENGLISH; // neutral
        }

        if (!preferredLangs.isEmpty() && postLang.equals(preferredLangs.get(0))) {
            return LANG_BOOST_EXACT_MATCH; // user's #1 language
        }
        if (preferredLangs.contains(postLang)) {
            return LANG_BOOST_PREF_LIST;   // in preferred list, not #1
        }

        // Post is in a language not in the user's preference list
        return LANG_BOOST_MISMATCH;
    }

    /**
     * Estimates total signal count from the profile map size.
     *
     * Using the profile map (already loaded for scoring) avoids an extra DB hit.
     * This is an approximation — a user with 30 signals and 10 distinct topics
     * will have a profile of size 10, so totalSignals returns 10 here, not 30.
     * That's acceptable: we just need to know whether the user is "very new"
     * (< LANG_MIN_SIGNALS_FOR_PREF topics) to skip the language penalty.
     */
    private int totalSignals(Map<String, Double> profile) {
        return profile != null ? profile.size() : 0;
    }

    // =========================================================================
    // PRIVATE — CORE COLD SCORING
    // =========================================================================

    private double scoreColdCore(User user, SocialPost post) {
        double rawEng    = (post.getEngagementScore() != null && post.getEngagementScore() > 0)
                ? post.getEngagementScore() : 1.0;
        double eng       = Math.log1p(rawEng);
        double geo       = geoProximity(user, post);
        double freshness = freshness(post);
        // Own posts may have null/0 quality — POST_QUALITY_DEFAULT is the floor
        double quality   = Math.max(resolveQuality(post), Constant.POST_QUALITY_DEFAULT) / 100.0;
        return eng * geo * freshness * quality;
        // NOTE: no languageBoost in COLD scoring — user has no preference data yet.
    }

    // =========================================================================
    // PRIVATE — COMPONENT FUNCTIONS
    // =========================================================================

    /**
     * Normalised dot product between user profile and post topic frequency map.
     * Equivalent to cosine similarity, scaled ×10 for human-readable debug values.
     *
     * Returns 0.0 when there is no topic overlap between user and post.
     */
    private double normalisedDotProduct(Map<String, Double> profile, Map<String, Double> ptf) {
        if (profile == null || profile.isEmpty() || ptf == null || ptf.isEmpty()) return 0.0;

        double dot = 0.0;
        for (Map.Entry<String, Double> e : ptf.entrySet()) {
            Double pw = profile.get(e.getKey());
            if (pw != null) dot += pw * e.getValue();
        }
        if (dot == 0.0) return 0.0;

        double ptfMag     = 0.0;
        double profileMag = 0.0;
        for (double v : ptf.values())     ptfMag     += v * v;
        for (double w : profile.values()) profileMag += w * w;

        if (profileMag == 0.0 || ptfMag == 0.0) return 0.0;

        return (dot / (Math.sqrt(profileMag) * Math.sqrt(ptfMag))) * 10.0;
    }

    /**
     * Geo proximity multiplier.
     *
     * Hierarchy: same pincode → same district → same state → national viral → no match.
     *
     * Posts with no location stored are treated as national scope (HLIG_GEO_NATIONAL),
     * not as a geo mismatch. A location-less post has no geographic restriction —
     * penalising it with GEO_NONE would silently suppress valid recommendations.
     */
    private double geoProximity(User user, SocialPost post) {
        if (!post.hasLocation()) return Constant.HLIG_GEO_NATIONAL;
        if (!user.hasPincode())  return Constant.HLIG_GEO_NATIONAL;

        if (Objects.equals(post.getPincode(),        user.getPincode()))        return Constant.HLIG_GEO_SAME_PINCODE;
        if (Objects.equals(post.getDistrictPrefix(), user.getDistrictPrefix())) return Constant.HLIG_GEO_SAME_DISTRICT;
        if (Objects.equals(post.getStatePrefix(),    user.getStatePrefix()))    return Constant.HLIG_GEO_SAME_STATE;
        if ("NATIONAL_VIRAL".equals(post.getViralTier()))                       return Constant.HLIG_GEO_NATIONAL;

        return Constant.HLIG_GEO_NONE;
    }

    /**
     * Freshness decay: e^(−λ × ageHours).
     * 46-hour half-life (λ = ln2/46 ≈ 0.015, stored in Constant.HLIG_FRESHNESS_LAMBDA).
     *
     * Sleeper bonus: a non-viral post gaining engagement while still fresh receives
     * a 1.25× bump (HLIG_FRESHNESS_SLEEPER_BONUS) to surface underrated local content
     * before it ages out.
     */
    private double freshness(SocialPost post) {
        if (post.getCreatedAt() == null) return 0.5;

        long   ageHours = ChronoUnit.HOURS.between(post.getCreatedAt().toInstant(), Instant.now());
        double base     = Math.exp(-Constant.HLIG_FRESHNESS_LAMBDA * Math.max(0, ageHours));

        boolean isViral   = Boolean.TRUE.equals(post.getIsViral());
        boolean isSleeper = !isViral
                && ageHours >= Constant.HLIG_FRESHNESS_SLEEPER_MIN_HOURS
                && ageHours <= Constant.HLIG_FRESHNESS_SLEEPER_MAX_HOURS
                && post.getEngagementScore() != null
                && post.getEngagementScore() > Constant.HLIG_FRESHNESS_SLEEPER_ENG_MIN;

        if (isSleeper) base *= Constant.HLIG_FRESHNESS_SLEEPER_BONUS;

        return Math.max(0.01, base);
    }

    /**
     * Neighbour boost: 1.0 + (HLIG_NEIGHBOUR_LIKE_STEP × neighbour_like_count),
     * capped at HLIG_NEIGHBOUR_BOOST_MAX (2.0).
     *
     * Each neighbour who liked the post adds 0.05 to the multiplier.
     * With 20 neighbours liking a post the multiplier hits the 2.0 cap.
     */
    private double neighbourBoost(Long postId, List<Long> neighbours) {
        if (neighbours == null || neighbours.isEmpty()) return 1.0;
        int count = repo.countNeighbourLikes(postId, neighbours);
        return Math.min(Constant.HLIG_NEIGHBOUR_BOOST_MAX,
                1.0 + (Constant.HLIG_NEIGHBOUR_LIKE_STEP * count));
    }

    /**
     * Trending boost: viral-tier multiplier (1.3–1.6×).
     * Posts with rising engagement velocity bubble up faster in ranking.
     */
    private double trendingBoost(SocialPost post) {
        if (post.getViralTier() == null) return 1.0;
        return switch (post.getViralTier()) {
            case "NATIONAL_VIRAL" -> 1.6;
            case "STATE_VIRAL"    -> 1.4;
            case "DISTRICT_VIRAL" -> 1.3;
            default               -> 1.0;
        };
    }

    /**
     * Session diversity penalty.
     *
     * If the primary topic of the post has already appeared 3+ times in this
     * session, the score is reduced to discourage topic flooding:
     *   ≥ 5 appearances → 0.3× (aggressive throttle)
     *   ≥ 3 appearances → 0.6× (moderate throttle)
     *   < 3 appearances → 1.0× (no penalty)
     */
    private double sessionPenalty(Map<String, Double> ptf, Map<String, Integer> sessionTopics) {
        if (ptf == null || ptf.isEmpty() || sessionTopics == null || sessionTopics.isEmpty()) return 1.0;

        String primaryTopic = ptf.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (primaryTopic == null) return 1.0;

        int count = sessionTopics.getOrDefault(primaryTopic, 0);
        if (count >= 5) return 0.3;
        if (count >= 3) return 0.6;
        return 1.0;
    }

    /**
     * Returns the post's quality score, defaulting to POST_QUALITY_DEFAULT (50.0)
     * when the quality scorer hasn't run yet on a newly created post.
     */
    private double resolveQuality(SocialPost post) {
        return (post.getQualityScore() != null)
                ? post.getQualityScore()
                : Constant.POST_QUALITY_DEFAULT;
    }

    // =========================================================================
    // JANSAHAYAK DISCOVERY ENGINE (Momentum & Quality Math)
    // =========================================================================

    // Gravity determines how fast a post decays over time. 
    // 1.2 allows posts to stay visible for 48-72 hours on sparse networks.
    private static final double GRAVITY = 1.2;
    
    // Laplace smoothing priors for Quality Score (Like Rate).
    // Assumes a "phantom" 1 like for every 10 views to prevent division by zero
    // and give new posts a fighting chance on a 30-user network.
    private static final double LAPLACE_LIKES = 1.0;
    private static final double LAPLACE_VIEWS = 10.0;

    /**
     * The Momentum Score (Replaces simple viralityScore)
     * Determines how fast a post is moving *right now*.
     */
    public static double calculateMomentumScore(int likes, int comments, int shares, int saves, long ageInHours) {
        double basePoints = 1.0;
        double weightedEngagement = basePoints 
                + (likes * 1.0) 
                + (comments * 2.0) 
                + (saves * 2.5) 
                + (shares * 5.0);

        double effectiveAge = Math.max(0, ageInHours) + 2.0;
        double momentum = weightedEngagement / Math.pow(effectiveAge, GRAVITY);
        
        return momentum * 100.0;
    }

    /**
     * The Quality Score (Replaces simple engagementScore)
     * Determines the actual value/relevance of the post using Bayesian Smoothing on Like Rate.
     * Uses ONLY positive engagement metrics (no dislike button in UI).
     */
    public static double calculateQualityScore(int likes, int views, int reports) {
        int effectiveViews = Math.max(views, likes);
        
        // E.g., 0 likes, 0 views = 1/10 = 10% expected quality
        double bayesianLikeRate = (likes + LAPLACE_LIKES) / (effectiveViews + LAPLACE_VIEWS);
        double qualityScore = bayesianLikeRate * 100.0;
        
        // Severe penalty for reports
        if (reports > 0) {
            double penaltyFactor = Math.pow(0.9, reports); 
            qualityScore *= penaltyFactor;
        }

        return Math.min(100.0, qualityScore);
    }
}
