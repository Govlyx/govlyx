package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.UserInterestProfile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


public interface UserInterestProfileRepo
        extends JpaRepository<UserInterestProfile, Long> {

    // ── Profile reads ─────────────────────────────────────────────────────────

    List<UserInterestProfile> findByUserIdOrderByWeightDesc(Long userId);

    @Query("SELECT u FROM UserInterestProfile u WHERE u.userId = :uid ORDER BY u.weight DESC")
    List<UserInterestProfile> findTopNByUserId(@Param("uid") Long userId, Pageable pageable);

    Optional<UserInterestProfile> findByUserIdAndTopic(Long userId, String topic);

    // ── Phase detection (O(1) via cached column) ──────────────────────────────

    @Query(value = """
        SELECT COALESCE(MAX(total_signals_cache), 0)
        FROM user_interest_profiles
        WHERE user_id = :uid
        LIMIT 1
        """, nativeQuery = true)
    int getTotalSignalsCache(@Param("uid") Long userId);

    // ── Atomic single-topic upsert ─────────────────────────────────────────────

    /**
     * Race-safe atomic upsert for a SINGLE topic.
     * Used for single-signal paths (e.g. applyLanguageSignal).
     * For multi-topic signals prefer batchUpsertWeights() below.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO user_interest_profiles
            (user_id, topic, weight, interaction_count, total_signals_cache,
             bubble_risk_flag, last_engaged_at, created_at)
        VALUES
            (:uid, :topic, GREATEST(0.0, :delta), GREATEST(0, :sigDelta), GREATEST(0, :sigDelta),
             FALSE,
             CASE WHEN :delta > 0 THEN NOW() ELSE NULL END, NOW())
        ON CONFLICT (user_id, topic) DO UPDATE
        SET weight              = GREATEST(0.0, user_interest_profiles.weight + :delta),
            interaction_count   = user_interest_profiles.interaction_count + :sigDelta,
            total_signals_cache = user_interest_profiles.total_signals_cache + :sigDelta,
            bubble_risk_flag    = (user_interest_profiles.interaction_count + :sigDelta > 200),
            last_engaged_at     = CASE WHEN :delta > 0 THEN NOW() ELSE user_interest_profiles.last_engaged_at END
        """, nativeQuery = true)
    void upsertWeight(
            @Param("uid")      Long   userId,
            @Param("topic")    String topic,
            @Param("delta")    double weightDelta,
            @Param("sigDelta") int    signalDelta
    );

    // ── FIX: Batch multi-topic upsert ──────────────────────────────────────────

    /**
     * FIX: Replaces calling upsertWeight() once per topic inside a forEach loop.
     *
     * The old applySignalWithTopics() pattern issued one native SQL upsert per topic
     * per user interaction (e.g. a post with 5 topics fired 5 individual upserts on
     * every like/view/save). For a busy user engaging 100×/day with 5-topic posts,
     * that is 500 upserts/day from a single user — compounding severely at scale.
     *
     * This method uses PostgreSQL's unnest() to pass the full topic/delta arrays in a
     * single round-trip. The database processes all rows in one INSERT … ON CONFLICT
     * statement, reducing N round-trips to 1.
     *
     * Usage in InterestProfileService.applySignalWithTopics():
     *   String[] topicArr = topics.keySet().toArray(new String[0]);
     *   double[] deltaArr = topics.values().stream()
     *       .mapToDouble(s -> baseWeight * s).toArray();
     *   repo.batchUpsertWeights(userId, topicArr, deltaArr, sigDelta);
     *
     * PostgreSQL requirement: pg_trgm and unnest() are both built-in; no extension needed.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO user_interest_profiles
            (user_id, topic, weight, interaction_count, total_signals_cache,
             bubble_risk_flag, last_engaged_at, created_at)
        SELECT :uid, t, GREATEST(0.0, d), GREATEST(0, :sigDelta), GREATEST(0, :sigDelta),
               FALSE,
               CASE WHEN d > 0 THEN NOW() ELSE NULL END,
               NOW()
        FROM unnest(:topics::text[], :deltas::float8[]) AS x(t, d)
        ON CONFLICT (user_id, topic) DO UPDATE
        SET weight              = GREATEST(0.0, user_interest_profiles.weight + EXCLUDED.weight),
            interaction_count   = user_interest_profiles.interaction_count + :sigDelta,
            total_signals_cache = user_interest_profiles.total_signals_cache + :sigDelta,
            bubble_risk_flag    = (user_interest_profiles.interaction_count + :sigDelta > 200),
            last_engaged_at     = CASE
                WHEN EXCLUDED.last_engaged_at IS NOT NULL THEN NOW()
                ELSE user_interest_profiles.last_engaged_at
            END
        """, nativeQuery = true)
    void batchUpsertWeights(
            @Param("uid")      Long     userId,
            @Param("topics")   String[] topics,
            @Param("deltas")   double[] deltas,
            @Param("sigDelta") int      signalDelta
    );

    // ── Collaborative filtering ────────────────────────────────────────────────

    @Query(value = """
        SELECT user_id
        FROM user_interest_profiles
        WHERE topic IN (:topics)
          AND weight >= :minWeight
          AND user_id != :excludeUid
        GROUP BY user_id
        ORDER BY MAX(weight) DESC
        LIMIT :lim
        """, nativeQuery = true)
    List<Long> findNeighboursByTopics(
            @Param("topics")     List<String> topics,
            @Param("minWeight")  double       minWeight,
            @Param("excludeUid") Long         excludeUserId,
            @Param("lim")        int          limit
    );

    // ── Nightly decay ─────────────────────────────────────────────────────────

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE user_interest_profiles
        SET weight = GREATEST(0.0, weight * EXP(-0.015 * EXTRACT(EPOCH FROM (NOW() - last_engaged_at)) / 86400.0))
        WHERE id IN (
            SELECT id FROM user_interest_profiles
            WHERE weight > 0
              AND last_engaged_at < NOW() - (:minAgeDays || ' days')::INTERVAL
            LIMIT 10000
        )
        """, nativeQuery = true)
    int bulkDecayBatch(@Param("minAgeDays") int minAgeDays);

    // ── Nightly prune ─────────────────────────────────────────────────────────

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM user_interest_profiles
        WHERE id IN (
            SELECT id FROM user_interest_profiles
            WHERE weight < :threshold
              AND weight >= 0
            LIMIT 10000
        )
        """, nativeQuery = true)
    int pruneNegligibleRows(@Param("threshold") double threshold);

    // ── NeighbourBoost helper ─────────────────────────────────────────────────

    @Query(value = """
        SELECT COUNT(*)
        FROM post_likes
        WHERE social_post_id = :postId
          AND user_id IN (:nids)
          AND reaction_type = 'LIKE'
        """, nativeQuery = true)
    int countNeighbourLikes(
            @Param("postId") Long       socialPostId,
            @Param("nids")   List<Long> neighbourIds
    );

    // ── Seen-post dedup helper ────────────────────────────────────────────────

    @Query(value = """
        SELECT social_post_id
        FROM post_views
        WHERE user_id = :uid
          AND viewed_at > NOW() - INTERVAL '72 hours'
        """, nativeQuery = true)
    List<Long> findRecentlyViewedPostIds(@Param("uid") Long userId);

    // ── Bulk seed (onboarding) ────────────────────────────────────────────────

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO user_interest_profiles
            (user_id, topic, weight, interaction_count, total_signals_cache,
             bubble_risk_flag, last_engaged_at, created_at)
        VALUES (:uid, :topic, :weight, 1, 1, FALSE, NOW(), NOW())
        ON CONFLICT (user_id, topic) DO NOTHING
        """, nativeQuery = true)
    void seedTopic(
            @Param("uid")    Long   userId,
            @Param("topic")  String topic,
            @Param("weight") double weight
    );

    // ── Language preference helpers ───────────────────────────────────────────

    @Query("""
        SELECT u.preferredLanguages FROM UserInterestProfile u
        WHERE u.userId = :userId
        ORDER BY u.weight DESC
        LIMIT 1
    """)
    String getPreferredLanguages(@Param("userId") Long userId);

    @Query("SELECT u FROM UserInterestProfile u WHERE u.userId = :userId AND u.topic LIKE 'lang:%'")
    List<UserInterestProfile> findLanguageTopicsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE UserInterestProfile u SET u.preferredLanguages = :langs " +
            "WHERE u.userId = :userId " +
            "AND u.weight = (SELECT MAX(u2.weight) FROM UserInterestProfile u2 WHERE u2.userId = :userId)")
    void updatePreferredLanguages(@Param("userId") Long userId, @Param("langs") String langs);
}
