package com.Govlyx.AI.repository;

import com.Govlyx.AI.model.Community;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityRepo extends JpaRepository<Community, Long> {

    // ── Lookup ────────────────────────────────────────────────────────────────
    @org.springframework.cache.annotation.Cacheable(value = "communities", key = "#slug")
    @EntityGraph(attributePaths = {"owner"})
    Optional<Community> findBySlug(String slug);

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "communities", key = "#id")
    @EntityGraph(attributePaths = {"owner"})
    Optional<Community> findById(Long id);

    @EntityGraph(attributePaths = {"owner"})
    Optional<Community> findByName(String name);
    boolean existsByName(String name);
    boolean existsBySlug(String slug);

    // ── Owner ─────────────────────────────────────────────────────────────────
    List<Community> findByOwnerIdAndStatusNot(Long ownerId, Community.CommunityStatus status);

    // ── Deletion & Cleanup ────────────────────────────────────────────────────
    List<Community> findByStatusAndScheduledDeletionAtBefore(Community.CommunityStatus status, java.util.Date date);
    List<Community> findByStatus(Community.CommunityStatus status);

    @Query("""
            SELECT COUNT(DISTINCT c.id) FROM Community c
            WHERE c.status NOT IN (
                com.Govlyx.AI.model.Community.CommunityStatus.DELETED,
                com.Govlyx.AI.model.Community.CommunityStatus.PERMANENTLY_DELETED
              )
              AND (
                c.owner.id = :userId
                OR EXISTS (
                  SELECT 1 FROM CommunityMember cm
                  WHERE cm.community = c
                    AND cm.user.id = :userId
                    AND cm.isActive = true
                    AND cm.isBanned = false
                )
              )
            """)
    long countMyCommunities(@Param("userId") Long userId);

    // ── Hyperlocal seed ───────────────────────────────────────────────────────
    boolean existsByPincodeAndIsSystemSeededTrue(String pincode);

    @EntityGraph(attributePaths = {"owner"})
    Optional<Community> findByPincodeAndIsSystemSeededTrue(String pincode);

    @Query("SELECT COUNT(u) FROM User u WHERE u.pincode = :pincode AND u.isActive = true")
    long countActiveUsersByPincode(@Param("pincode") String pincode);

    // ── Discovery ─────────────────────────────────────────────────────────────
    @EntityGraph(attributePaths = {"owner"})
    @Query("""
            SELECT c FROM Community c
            WHERE c.status = com.Govlyx.AI.model.Community.CommunityStatus.ACTIVE
              AND c.privacy <> com.Govlyx.AI.model.Community.CommunityPrivacy.SECRET
              AND (:cursor IS NULL OR c.id < :cursor)
            ORDER BY
              CASE
                WHEN c.pincode        = :pincode        THEN 0
                WHEN c.districtPrefix = :districtPrefix THEN 1
                WHEN c.statePrefix    = :statePrefix    THEN 2
                ELSE 3
              END ASC,
              c.healthScore DESC, c.memberCount DESC, c.id DESC
            """)
    List<Community> findDiscoverableForUser(
            @Param("pincode")        String pincode,
            @Param("districtPrefix") String districtPrefix,
            @Param("statePrefix")    String statePrefix,
            @Param("cursor")         Long   cursor,
            Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    @Query("""
            SELECT c FROM Community c
            WHERE c.status = com.Govlyx.AI.model.Community.CommunityStatus.ACTIVE
              AND c.privacy <> com.Govlyx.AI.model.Community.CommunityPrivacy.SECRET
              AND (:cursor IS NULL OR c.id < :cursor)
            ORDER BY c.healthScore DESC, c.memberCount DESC, c.id DESC
            """)
    List<Community> findDiscoverable(@Param("cursor") Long cursor, Pageable pageable);

    // ── Generic search (safe fallback) ────────────────────────────────────────
    @EntityGraph(attributePaths = {"owner"})
    @Query("""
            SELECT c FROM Community c
            WHERE c.status = com.Govlyx.AI.model.Community.CommunityStatus.ACTIVE
              AND c.privacy <> com.Govlyx.AI.model.Community.CommunityPrivacy.SECRET
              AND (LOWER(c.name)        LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
                OR LOWER(c.description) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
                OR LOWER(c.tags)        LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
                OR LOWER(c.wardName)    LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\')
            ORDER BY c.healthScore DESC, c.memberCount DESC, c.id DESC
            """)
    List<Community> searchCommunities(
            @Param("q")      String query,
            Pageable pageable);

    // ── Category ──────────────────────────────────────────────────────────────
    @EntityGraph(attributePaths = {"owner"})
    @Query("""
            SELECT c FROM Community c
            WHERE c.status = com.Govlyx.AI.model.Community.CommunityStatus.ACTIVE
              AND c.privacy <> com.Govlyx.AI.model.Community.CommunityPrivacy.SECRET
              AND c.category = :category
              AND (:cursor IS NULL OR c.id < :cursor)
            ORDER BY c.healthScore DESC, c.memberCount DESC, c.id DESC
            """)
    List<Community> findByCategory(
            @Param("category") String category,
            @Param("cursor")   Long   cursor,
            Pageable pageable);

    // ── Health scheduler ──────────────────────────────────────────────────────
    @Query("SELECT c FROM Community c WHERE c.status = com.Govlyx.AI.model.Community.CommunityStatus.ACTIVE")
    List<Community> findAllActive();

    @Query("""
            SELECT c FROM Community c
            WHERE c.status = com.Govlyx.AI.model.Community.CommunityStatus.ACTIVE
              AND (c.healthScoreUpdatedAt IS NULL OR c.healthScoreUpdatedAt < :cutoff)
            ORDER BY c.id ASC
            """)
    List<Community> findDueForHealthRecalculation(@Param("cutoff") Date cutoff);

    // ── Bulk counter updates ──────────────────────────────────────────────────
    @Transactional @Modifying @Query("UPDATE Community c SET c.memberCount = c.memberCount + 1 WHERE c.id = :id")                                                void incrementMemberCount        (@Param("id") Long id);
    @Transactional @Modifying @Query("UPDATE Community c SET c.memberCount = CASE WHEN c.memberCount > 0 THEN c.memberCount - 1 ELSE 0 END WHERE c.id = :id")    void decrementMemberCount        (@Param("id") Long id);
    @Transactional @Modifying @Query("UPDATE Community c SET c.postCount = c.postCount + 1, c.lastActiveAt = CURRENT_TIMESTAMP WHERE c.id = :id")                 void incrementPostCount          (@Param("id") Long id);
    @Transactional @Modifying @Query("UPDATE Community c SET c.postCount = CASE WHEN c.postCount > 0 THEN c.postCount - 1 ELSE 0 END WHERE c.id = :id")          void decrementPostCount          (@Param("id") Long id);
    @Transactional @Modifying @Query("UPDATE Community c SET c.reportCount = c.reportCount + 1 WHERE c.id = :id")                                                 void incrementReportCount        (@Param("id") Long id);
    @Transactional @Modifying @Query("UPDATE Community c SET c.totalCommentCount = c.totalCommentCount + 1 WHERE c.id = :id")                                     void incrementTotalCommentCount  (@Param("id") Long id);
    @Transactional @Modifying @Query("UPDATE Community c SET c.totalLikeCount = c.totalLikeCount + 1 WHERE c.id = :id")                                           void incrementTotalLikeCount     (@Param("id") Long id);
    @Transactional @Modifying @Query("UPDATE Community c SET c.feedSurfaceCount = c.feedSurfaceCount + 1 WHERE c.id = :id")                                       void incrementFeedSurfaceCount   (@Param("id") Long id);
    @Transactional @Modifying @Query("UPDATE Community c SET c.postsLast7d = c.postsLast7d + 1 WHERE c.id = :id")                                                 void incrementPostsLast7d        (@Param("id") Long id);
    @Transactional @Modifying @Query("UPDATE Community c SET c.newMembersLast7d = c.newMembersLast7d + 1 WHERE c.id = :id")                                       void incrementNewMembersLast7d   (@Param("id") Long id);
    @Transactional @Modifying @Query("UPDATE Community c SET c.activePostersLast7d = c.activePostersLast7d + 1 WHERE c.id = :id")                                 void incrementActivePostersLast7d(@Param("id") Long id);

    @Transactional @Modifying
    @Query("UPDATE Community c SET c.seedTriggerCount = c.seedTriggerCount + 1 WHERE c.pincode = :pincode AND c.isSystemSeeded = true")
    void incrementSeedTriggerCount(@Param("pincode") String pincode);

    @Transactional @Modifying
    @Query("UPDATE Community c SET c.newMembersLast7d = 0, c.postsLast7d = 0, c.activePostersLast7d = 0 WHERE c.status = com.Govlyx.AI.model.Community.CommunityStatus.ACTIVE")
    void resetWeeklyCounters();

    @Transactional @Modifying
    @Query("UPDATE Community c SET c.postCount = (SELECT COUNT(sp) FROM SocialPost sp WHERE sp.community.id = c.id AND sp.status = com.Govlyx.AI.enums.PostStatus.ACTIVE)")
    void syncAllCommunityPostCounts();

    // ── Admin stats ──────────────────────────────────────────────────────────
    @Query("SELECT COUNT(c) FROM Community c WHERE c.status = :status")
    long countByStatus(@Param("status") Community.CommunityStatus status);

    // ==========================================================================
    // ===== UNIFIED SEARCH — CURSOR-BASED (used by SearchService) ==============
    // ==========================================================================

    /**
     * FIXES APPLIED:
     *  1. Removed c.status = ACTIVE filter → inactive communities now included in results.
     *  2. Added ORDER BY CASE to sort ACTIVE communities first, others after.
     *  3. Used full enum path (com.Govlyx.AI.model.Community.CommunityPrivacy.SECRET)
     *     to avoid any JPQL string-vs-enum comparison issues.
     *  4. LIKE '%query%' ensures partial match — "pune" matches "Pune Traffic", etc.
     */

    @Query("""
            SELECT c FROM Community c
            WHERE c.privacy <> com.Govlyx.AI.model.Community.CommunityPrivacy.SECRET
              AND (LOWER(c.name)        LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
               OR  LOWER(c.description) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
               OR  LOWER(c.category)    LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
               OR  LOWER(c.tags)        LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\')
            ORDER BY
              CASE c.status
                WHEN com.Govlyx.AI.model.Community.CommunityStatus.ACTIVE THEN 0
                ELSE 1
              END ASC,
              c.healthScore DESC,
              c.memberCount DESC,
              c.id DESC
            """)
    List<Community> searchPage(
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            SELECT c FROM Community c
            WHERE c.privacy <> com.Govlyx.AI.model.Community.CommunityPrivacy.SECRET
              AND (c.pincode        = :pincode
               OR  c.districtPrefix = :districtPrefix
               OR  c.statePrefix    = :statePrefix)
              AND (LOWER(c.name)        LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
               OR  LOWER(c.description) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
               OR  LOWER(c.category)    LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
               OR  LOWER(c.tags)        LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\')
            ORDER BY
              CASE c.status
                WHEN com.Govlyx.AI.model.Community.CommunityStatus.ACTIVE THEN 0
                ELSE 1
              END ASC,
              c.healthScore DESC,
              c.memberCount DESC,
              c.id DESC
            """)
    List<Community> searchPageByLocation(
            @Param("query")          String query,
            @Param("pincode")        String pincode,
            @Param("districtPrefix") String districtPrefix,
            @Param("statePrefix")    String statePrefix,
            Pageable pageable
    );
}
