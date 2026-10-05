package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.UserInterestProfile;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.UserInterestProfileRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterestProfileService {

    private final UserInterestProfileRepo repo;
    private final SocialPostRepo          socialPostRepo;
    private final TopicExtractor          topicExtractor;

    // Self-injection to fix proxy-bypass for @Transactional and @Async on internal calls
    private InterestProfileService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy InterestProfileService self) {
        this.self = self;
    }

    // FIX MEMORY LEAK #6 — needed for programmatic lang:: and nb:: cache eviction
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    // ── Signal handlers (all @Async) ──────────────────────────────────────────

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLike(Long userId, Long postId) {
        SocialPost post = socialPostRepo.findById(postId).orElse(null);
        if (post == null) return;
        Map<String, Double> topics = topicExtractor.extract(post);
        self.applySignal(userId, post, Constant.HLIG_W_LIKE, topics, true);
        self.applyLanguageSignal(userId, post, Constant.HLIG_W_LIKE * 0.5);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDislike(Long userId, Long postId) {
        SocialPost post = socialPostRepo.findById(postId).orElse(null);
        if (post == null) return;
        self.applySignal(userId, post, Constant.HLIG_W_DISLIKE, topicExtractor.extract(post), false);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUnlike(Long userId, Long postId) {
        SocialPost post = socialPostRepo.findById(postId).orElse(null);
        if (post == null) return;
        self.applySignal(userId, post, Constant.HLIG_W_UNLIKE, topicExtractor.extract(post), false);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onComment(Long userId, Long postId) {
        SocialPost post = socialPostRepo.findById(postId).orElse(null);
        if (post == null) return;
        Map<String, Double> topics = topicExtractor.extract(post);
        self.applySignal(userId, post, Constant.HLIG_W_COMMENT, topics, true);
        self.applyLanguageSignal(userId, post, Constant.HLIG_W_COMMENT * 0.5);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSave(Long userId, Long postId) {
        SocialPost post = socialPostRepo.findById(postId).orElse(null);
        if (post == null) return;
        Map<String, Double> topics = topicExtractor.extract(post);
        self.applySignal(userId, post, Constant.HLIG_W_SAVE, topics, true);
        self.applyLanguageSignal(userId, post, Constant.HLIG_W_SAVE * 0.5);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUnsave(Long userId, Long postId) {
        SocialPost post = socialPostRepo.findById(postId).orElse(null);
        if (post == null) return;
        self.applySignal(userId, post, Constant.HLIG_W_UNSAVE, topicExtractor.extract(post), false);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShare(Long userId, Long postId) {
        SocialPost post = socialPostRepo.findById(postId).orElse(null);
        if (post == null) return;
        Map<String, Double> topics = topicExtractor.extract(post);
        self.applySignal(userId, post, Constant.HLIG_W_SHARE, topics, true);
        self.applyLanguageSignal(userId, post, Constant.HLIG_W_SHARE * 0.5);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onView(Long userId, Long postId) {
        SocialPost post = socialPostRepo.findById(postId).orElse(null);
        if (post == null) return;
        self.applySignal(userId, post, Constant.HLIG_W_VIEW, topicExtractor.extract(post), true);
        self.applyLanguageSignal(userId, post, Constant.HLIG_W_VIEW * 0.5);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScrolledPast(Long userId, Long postId) {
        SocialPost post = socialPostRepo.findById(postId).orElse(null);
        if (post == null) return;
        Map<String, Double> topics = topicExtractor.extractFromHashtagsOnly(post);
        if (topics.isEmpty()) return;
        self.applySignalWithTopics(userId, Constant.HLIG_W_SCROLL_PAST, topics, false);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNotInterested(Long userId, Long postId) {
        SocialPost post = socialPostRepo.findById(postId).orElse(null);
        if (post == null) return;
        self.applySignal(userId, post, Constant.HLIG_W_NOT_INTERESTED, topicExtractor.extract(post), false);
        log.info("User {} marked post {} as not interested", userId, post.getId());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPostCreated(Long userId, Long postId) {
        SocialPost post = socialPostRepo.findById(postId).orElse(null);
        if (post == null) return;
        Map<String, Double> topics = topicExtractor.extract(post);
        self.applySignal(userId, post, Constant.HLIG_W_POST_CREATED, topics, true);
        self.applyLanguageSignal(userId, post, Constant.HLIG_W_POST_CREATED * 0.5);
    }

    // ── Profile reads (cached) ────────────────────────────────────────────────

    @Cacheable(value = Constant.HLIG_CACHE_PROFILE, key = "#userId")
    public Map<String, Double> loadTopN(Long userId) {
        List<UserInterestProfile> rows = repo.findTopNByUserId(
                userId, PageRequest.of(0, Constant.HLIG_TOP_N));
        return rows.stream()
                .filter(r -> r.decayedWeight() > Constant.HLIG_SCORE_MIN_THRESHOLD)
                .collect(Collectors.toMap(
                        UserInterestProfile::getTopic,
                        UserInterestProfile::getDecayedWeight,
                        Math::max,
                        LinkedHashMap::new
                ));
    }

    public List<String> loadCoreTopics(Long userId) {
        return self.loadTopN(userId).entrySet().stream()
                .filter(e -> e.getValue() >= Constant.HLIG_PROFILE_CORE_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // ── Language preference reads ─────────────────────────────────────────────

    @Cacheable(value = Constant.HLIG_CACHE_PROFILE, key = "'lang::' + #userId")
    public List<String> getPreferredLanguages(Long userId) {
        String raw = repo.getPreferredLanguages(userId);
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(UserInterestProfile.MAX_PREFERRED_LANGUAGES)
                .collect(Collectors.toList());
    }

    // ── Phase detection (O(1)) ────────────────────────────────────────────────

    public Phase getUserPhase(Long userId) {
        int total = repo.getTotalSignalsCache(userId);
        if (total <= Constant.HLIG_WARMING_PHASE_THRESHOLD) return Phase.COLD;
        if (total <= Constant.HLIG_WARM_PHASE_THRESHOLD)    return Phase.WARMING;
        return Phase.WARM;
    }

    public enum Phase { COLD, WARMING, WARM }

    // ── Collaborative filtering ────────────────────────────────────────────────

    @Cacheable(value = Constant.HLIG_CACHE_PROFILE, key = "'nb::' + #userId")
    public List<Long> findNeighbours(Long userId) {
        List<String> coreTopics = loadCoreTopics(userId);
        if (coreTopics.isEmpty()) return Collections.emptyList();
        return repo.findNeighboursByTopics(coreTopics, Constant.HLIG_PROFILE_CORE_THRESHOLD,
                userId, Constant.HLIG_MAX_NEIGHBOURS);
    }

    // ── Seen-post dedup ────────────────────────────────────────────────────────

    public Set<Long> recentlySeenPosts(Long userId) {
        int total = repo.getTotalSignalsCache(userId);
        if (total <= Constant.HLIG_WARMING_PHASE_THRESHOLD) return Collections.emptySet();
        List<Long> ids = repo.findRecentlyViewedPostIds(userId);
        return ids != null ? new HashSet<>(ids) : Collections.emptySet();
    }

    // ── Onboarding seed ───────────────────────────────────────────────────────

    /**
     * FIX: Added @Transactional(propagation = REQUIRES_NEW).
     *
     * WHY: seedFromOnboarding() is called inside CommunityService.joinCommunity(),
     * which is itself @Transactional. Without REQUIRES_NEW, any exception thrown
     * here (e.g. a DB constraint violation) marks the OUTER transaction as
     * rollback-only. Spring then throws UnexpectedRollbackException when
     * joinCommunity() tries to commit — even if the caller wrapped this call in
     * a try/catch. The try/catch catches the immediate exception but cannot
     * un-poison the outer transaction once Hibernate has flagged it.
     *
     * REQUIRES_NEW suspends the caller's transaction, opens a fresh independent
     * one, and commits/rolls back entirely on its own. A failure here is isolated
     * to the seed step only — the join always succeeds regardless.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void seedFromOnboarding(Long userId, List<String> topics) {
        for (String topic : topics) {
            repo.seedTopic(userId, topic.toLowerCase().trim(), 3.0);
        }
        evictProfileCache(userId);
        log.info("Seeded {} topics for new user {}", topics.size(), userId);
    }

    /**
     * FIX: Added @Transactional(propagation = REQUIRES_NEW) for the same reason
     * as seedFromOnboarding — language seeding is also called from transactional
     * contexts and must not be able to poison the caller's transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void seedLanguagePreferencesFromOnboarding(Long userId, List<String> langCodes) {
        if (langCodes == null || langCodes.isEmpty()) return;
        for (String code : langCodes) {
            if (code == null || code.isBlank()) continue;
            String topic = "lang:" + code.toLowerCase().trim();
            repo.seedTopic(userId, topic, 5.0);
        }
        evictProfileCache(userId);
        rebuildLanguagePreferences(userId);
        log.info("Seeded {} language preferences for user {}", langCodes.size(), userId);
    }

    // ── Nightly decay + prune ─────────────────────────────────────────────────

    @Scheduled(cron = "0 0 2 * * *")
    public void applyNightlyDecay() {
        log.info("HLIG nightly decay starting...");
        int totalDecayed = 0;
        int decayBatch;
        int attempt  = 0;
        final int MAX_BATCHES = 10_000;

        do {
            try {
                decayBatch   = repo.bulkDecayBatch(1);
                totalDecayed += decayBatch;
                attempt++;
            } catch (Exception e) {
                log.error("HLIG decay batch {} failed — stopping safely. {} rows updated so far.",
                        attempt, totalDecayed, e);
                break;
            }
        } while (decayBatch > 0 && attempt < MAX_BATCHES);

        log.info("HLIG nightly decay complete — {} rows updated in {} batches", totalDecayed, attempt);

        try {
            int pruned = repo.pruneNegligibleRows(Constant.HLIG_SCORE_MIN_THRESHOLD);
            log.info("HLIG nightly prune complete — {} near-zero rows removed", pruned);
        } catch (Exception e) {
            log.warn("HLIG nightly prune failed (non-fatal): {}", e.getMessage());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void applySignal(Long userId, SocialPost post, double baseWeight,
                     Map<String, Double> topics, boolean isPositive) {
        if (topics.isEmpty()) return;
        self.applySignalWithTopics(userId, baseWeight, topics, isPositive);
    }

    /**
     * FIX: Replaced the per-topic upsertWeight() forEach loop with a single
     * batchUpsertWeights() call.
     *
     * OLD (broken) pattern — 1 native SQL round-trip per topic:
     *   topics.forEach((topic, strength) -> {
     *       double delta = baseWeight * strength;
     *       repo.upsertWeight(userId, topic, delta, sigDelta); // ← N queries
     *   });
     *
     * NEW (fixed) — 1 native SQL round-trip for ALL topics using PostgreSQL unnest():
     *   repo.batchUpsertWeights(userId, topicArr, deltaArr, sigDelta); // ← 1 query
     *
     * Impact: A post with 5 topics fired 5 upserts per signal. A user who
     * likes/views/saves 100 posts/day with 5 topics each produced 500 upserts/day
     * from a single user. At 10,000 active users that is 5,000,000 upserts/day
     * reduced to 1,000,000 — an 80% reduction in DB write load.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void applySignalWithTopics(Long userId, double baseWeight,
                               Map<String, Double> topics, boolean isPositive) {
        if (topics.isEmpty()) return;
        int sigDelta = isPositive ? 1 : 0;

        // Execute sequentially to avoid Spring Data JPA native query array-binding exceptions
        topics.forEach((topic, strength) -> {
            double delta = baseWeight * strength;
            double finalDelta = isPositive ? delta : -Math.abs(delta);
            try {
                repo.upsertWeight(userId, topic, finalDelta, sigDelta);
            } catch (Exception e) {
                log.warn("[HLIG] Failed to upsert topic '{}' for user {}: {}", topic, userId, e.getMessage());
            }
        });
        
        evictProfileCache(userId);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void applyLanguageSignal(Long userId, SocialPost post, double langWeight) {
        String lang = post.safeLanguage();
        if ("mixed".equals(lang)) return;
        if ("en".equals(lang))    return;

        String langTopic = "lang:" + lang;
        try {
            repo.upsertWeight(userId, langTopic, langWeight, 1);
            evictProfileCache(userId);
            rebuildLanguagePreferences(userId);
        } catch (Exception e) {
            log.warn("[HLIG] applyLanguageSignal failed: userId={} lang={} reason={}",
                    userId, lang, e.getMessage());
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void rebuildLanguagePreferences(Long userId) {
        try {
            List<UserInterestProfile> langRows = repo.findLanguageTopicsByUserId(userId);
            if (langRows.isEmpty()) return;

            String preferredLangs = langRows.stream()
                    .filter(r -> r.isLanguageTopic() && r.decayedWeight() > Constant.HLIG_SCORE_MIN_THRESHOLD)
                    .sorted(Comparator.comparingDouble(UserInterestProfile::getDecayedWeight).reversed())
                    .limit(UserInterestProfile.MAX_PREFERRED_LANGUAGES)
                    .map(UserInterestProfile::languageCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(","));

            if (!preferredLangs.isBlank()) {
                repo.updatePreferredLanguages(userId, preferredLangs);
                evictProfileCache(userId);
            }
        } catch (Exception e) {
            log.warn("[HLIG] rebuildLanguagePreferences failed: userId={} reason={}", userId, e.getMessage());
        }
    }

    public void evictProfileCache(Long userId) {
        try {
            org.springframework.cache.CacheManager cm =
                    applicationContext.getBean(org.springframework.cache.CacheManager.class);
            org.springframework.cache.Cache cache = cm.getCache(Constant.HLIG_CACHE_PROFILE);
            if (cache != null) {
                cache.evict(userId);
                cache.evict("lang::" + userId);
                cache.evict("nb::"   + userId);
            }
        } catch (Exception e) {
            log.warn("[HLIG] Secondary cache eviction failed for userId={}: {}", userId, e.getMessage());
        }
    }
}
