package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.SocialPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;


@Repository
public interface SocialPostRepo extends JpaRepository<SocialPost, Long> {

    Optional<SocialPost> findByIdempotencyKey(String idempotencyKey);

    // =========================================================================
    // ATOMIC COUNTER UPDATES
    // =========================================================================

    @Modifying @Query("UPDATE SocialPost p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying @Query("UPDATE SocialPost p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END WHERE p.id = :id")
    void decrementLikeCount(@Param("id") Long id);

    @Modifying @Query("UPDATE SocialPost p SET p.dislikeCount = p.dislikeCount + 1 WHERE p.id = :id")
    void incrementDislikeCount(@Param("id") Long id);

    @Modifying @Query("UPDATE SocialPost p SET p.dislikeCount = CASE WHEN p.dislikeCount > 0 THEN p.dislikeCount - 1 ELSE 0 END WHERE p.id = :id")
    void decrementDislikeCount(@Param("id") Long id);

    @Modifying @Query("UPDATE SocialPost p SET p.commentCount = p.commentCount + 1 WHERE p.id = :id")
    void incrementCommentCount(@Param("id") Long id);

    @Modifying @Query("UPDATE SocialPost p SET p.commentCount = CASE WHEN p.commentCount > :count THEN p.commentCount - :count ELSE 0 END WHERE p.id = :id")
    void decrementCommentCountBy(@Param("id") Long id, @Param("count") int count);

    @Modifying @Query("UPDATE SocialPost p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    /** Batch increment — used by ViewCountFlusher to flush accumulated view counts in one UPDATE. */
    @Modifying @Query("UPDATE SocialPost p SET p.viewCount = p.viewCount + :count WHERE p.id = :id")
    void incrementViewCountBy(@Param("id") Long id, @Param("count") int count);

    @Modifying @Query("UPDATE SocialPost p SET p.shareCount = p.shareCount + 1 WHERE p.id = :id")
    void incrementShareCount(@Param("id") Long id);

    @Modifying @Query("UPDATE SocialPost p SET p.saveCount = p.saveCount + 1 WHERE p.id = :id")
    void incrementSaveCount(@Param("id") Long id);

    @Modifying @Query("UPDATE SocialPost p SET p.saveCount = CASE WHEN p.saveCount > 0 THEN p.saveCount - 1 ELSE 0 END WHERE p.id = :id")
    void decrementSaveCount(@Param("id") Long id);

    // =========================================================================
    // BASIC QUERIES
    // =========================================================================

    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.actorToken = :actorToken
              AND sp.status IN :statuses
              AND (sp.community IS NULL OR sp.communityPrivacy = 'PUBLIC')
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findByActorTokenAndStatusInOrderByCreatedAtDesc(
            @Param("actorToken") String actorToken, 
            @Param("statuses") List<PostStatus> statuses, 
            Pageable pageable);

    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.actorToken = :actorToken
              AND sp.status IN :statuses
              AND sp.id < :beforeId
              AND (sp.community IS NULL OR sp.communityPrivacy = 'PUBLIC')
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findByActorTokenAndStatusInAndIdLessThanOrderByCreatedAtDesc(
            @Param("actorToken") String actorToken,
            @Param("statuses") List<PostStatus> statuses,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("""
            SELECT COUNT(sp) FROM SocialPost sp
            WHERE sp.actorToken = :actorToken
              AND sp.status IN :statuses
              AND (sp.community IS NULL OR sp.communityPrivacy = 'PUBLIC')
            """)
    long countByActorTokenAndStatusIn(
            @Param("actorToken") String actorToken,
            @Param("statuses") List<PostStatus> statuses);

    long countByActorToken(String actorToken);

    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.user.id = :userId
              AND sp.status IN :statuses
              AND (sp.community IS NULL OR sp.communityPrivacy = 'PUBLIC')
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findByUserIdAndStatusInOrderByCreatedAtDesc(
            @Param("userId") Long userId, 
            @Param("statuses") List<PostStatus> statuses, 
            Pageable pageable);


    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.user.id = :userId
              AND sp.status IN :statuses
              AND sp.id < :id
              AND (sp.community IS NULL OR sp.communityPrivacy = 'PUBLIC')
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findByUserIdAndStatusInAndIdLessThanOrderByCreatedAtDesc(
            @Param("userId") Long userId, 
            @Param("statuses") List<PostStatus> statuses, 
            @Param("id") Long id, 
            Pageable pageable);

    List<SocialPost> findByStatusOrderByCreatedAtDesc(PostStatus status, Pageable pageable);

    /**
     * Cursor-paginated fallback for status-only queries.
     * Used by SocialPostService.fetchRecommendedPosts() and fetchTrendingPosts()
     * when geo/viral filters return fewer posts than the requested page size
     * (sparse-platform safety net for 0–5 user platforms).
     */
    List<SocialPost> findByStatusAndIdLessThanOrderByCreatedAtDesc(
            PostStatus status, Long id, Pageable pageable);

    @Modifying @Query("UPDATE SocialPost p SET p.commentCount = :count WHERE p.id = :id")
    void updateCommentCount(@Param("id") Long id, @Param("count") int count);

    @Query("SELECT p FROM SocialPost p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.community WHERE p.id IN :ids")
    List<SocialPost> findAllByIdsWithUserAndCommunity(@Param("ids") List<Long> ids);

    // =========================================================================
    // VIRAL & TRENDING
    // =========================================================================

    List<SocialPost> findByIsViralTrueAndStatusOrderByViralityScoreDesc(
            PostStatus status, Pageable pageable);

    List<SocialPost> findByIsViralTrueAndStatusAndIdLessThanOrderByViralityScoreDesc(
            PostStatus status, Long id, Pageable pageable);

    List<SocialPost> findByViralTierAndStatusOrderByEngagementScoreDesc(
            String viralTier, PostStatus status, Pageable pageable);

    // =========================================================================
    // LOCATION-BASED
    // =========================================================================

    List<SocialPost> findByPincodeAndStatusOrderByCreatedAtDesc(
            String pincode, PostStatus status, Pageable pageable);

    List<SocialPost> findByStatePrefixAndStatusOrderByCreatedAtDesc(
            String statePrefix, PostStatus status, Pageable pageable);

    List<SocialPost> findByDistrictPrefixAndStatusOrderByCreatedAtDesc(
            String districtPrefix, PostStatus status, Pageable pageable);

    List<SocialPost> findByDistrictPrefixAndStatusAndIdLessThanOrderByCreatedAtDesc(
            String districtPrefix, PostStatus status, Long id, Pageable pageable);

    // =========================================================================
    // HASHTAG
    // =========================================================================

    @Query("""
            SELECT sp FROM SocialPost sp 
            WHERE LOWER(sp.hashtags) LIKE LOWER(CONCAT('%', :hashtag, '%')) ESCAPE '\\' 
              AND sp.status = :status 
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE')) 
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findByHashtagContaining(@Param("hashtag") String hashtag,
                                             @Param("status") PostStatus status,
                                             Pageable pageable);

    @Query("""
            SELECT sp FROM SocialPost sp 
            WHERE LOWER(sp.hashtags) LIKE LOWER(CONCAT('%', :hashtag, '%')) ESCAPE '\\' 
              AND sp.status = :status 
              AND sp.id < :id 
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE')) 
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findByHashtagContainingAndIdLessThan(@Param("hashtag") String hashtag,
                                                          @Param("status") PostStatus status,
                                                          @Param("id") Long id,
                                                          Pageable pageable);

    // =========================================================================
    // =========================================================================
    // NEIGHBORHOOD Q&A FEED
    // =========================================================================
    // NEIGHBORHOOD Q&A FEED
    // =========================================================================

    @Query(value = """
        SELECT * FROM social_posts sp 
        WHERE sp.category = :category 
          AND sp.status = 'ACTIVE' 
          AND sp.is_flagged = false
          AND (
            (
              sp.latitude IS NOT NULL 
              AND sp.longitude IS NOT NULL
              AND sp.latitude BETWEEN :minLat AND :maxLat
              AND sp.longitude BETWEEN :minLng AND :maxLng
              AND (
                  6371 * acos(
                      LEAST(1.0, GREATEST(-1.0,
                          cos(radians(:userLat)) * cos(radians(sp.latitude)) * 
                          cos(radians(sp.longitude) - radians(:userLng)) + 
                          sin(radians(:userLat)) * sin(radians(sp.latitude))
                      ))
                  )
              ) <= :maxDistance
            )
            OR (:userPincode IS NOT NULL AND sp.pincode = :userPincode)
          )
        ORDER BY sp.created_at DESC
        """, nativeQuery = true)
    List<SocialPost> findNearbyPostsUsingGPS(
        @Param("userLat") java.math.BigDecimal userLat, 
        @Param("userLng") java.math.BigDecimal userLng,
        @Param("minLat") java.math.BigDecimal minLat, 
        @Param("maxLat") java.math.BigDecimal maxLat,
        @Param("minLng") java.math.BigDecimal minLng, 
        @Param("maxLng") java.math.BigDecimal maxLng, 
        @Param("category") String category,
        @Param("maxDistance") double maxDistance,
        @Param("userPincode") String userPincode);

    @EntityGraph(attributePaths = {"user"})
    @Query("""
        SELECT sp FROM SocialPost sp
        WHERE sp.category = :category
          AND sp.status = :status
          AND sp.isFlagged = false
          AND sp.pincode IN (
              SELECT p.pincode FROM PincodeLookup p WHERE LOWER(p.city) = LOWER(:city)
          )
        ORDER BY sp.createdAt DESC
        """)
    List<SocialPost> findQAPostsByCity(
        @Param("category") com.JanSahayak.AI.enums.SocialPostCategory category,
        @Param("city") String city,
        @Param("status") PostStatus status,
        Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("""
        SELECT sp FROM SocialPost sp
        WHERE sp.category = :category
          AND sp.status = :status
          AND sp.isFlagged = false
          AND sp.pincode IN (
              SELECT p.pincode FROM PincodeLookup p WHERE p.urbanClusterId = :clusterId
          )
        ORDER BY sp.createdAt DESC
        """)
    List<SocialPost> findQAPostsByUrbanCluster(
        @Param("category") com.JanSahayak.AI.enums.SocialPostCategory category,
        @Param("clusterId") String clusterId,
        @Param("status") PostStatus status,
        Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("""
        SELECT sp FROM SocialPost sp
        WHERE sp.category = :category
          AND sp.status = :status
          AND sp.isFlagged = false
          AND sp.pincode IN (
              SELECT p.pincode FROM PincodeLookup p WHERE LOWER(p.district) = LOWER(:district)
          )
        ORDER BY sp.createdAt DESC
        """)
    List<SocialPost> findQAPostsByRealDistrict(
        @Param("category") com.JanSahayak.AI.enums.SocialPostCategory category,
        @Param("district") String district,
        @Param("status") PostStatus status,
        Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.category = :category
              AND sp.pincode IN :pincodes
              AND sp.status = :status
            ORDER BY sp.engagementScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findTopQAPostsByPincodes(
            @Param("category") com.JanSahayak.AI.enums.SocialPostCategory category,
            @Param("pincodes") List<String> pincodes,
            @Param("status") PostStatus status,
            Pageable pageable);

    @Query("SELECT COUNT(sp) FROM SocialPost sp WHERE sp.category = :category AND sp.pincode IN :pincodes AND sp.commentCount = :commentCount AND sp.status = :status")
    Long countByCategoryAndPincodeInAndCommentCountAndStatus(
            @Param("category") com.JanSahayak.AI.enums.SocialPostCategory category,
            @Param("pincodes") List<String> pincodes,
            @Param("commentCount") int commentCount,
            @Param("status") PostStatus status);

    // =========================================================================
    // RECOMMENDATION — ORIGINAL (geographic + quality fallback)
    // Used by SocialPostService.getHomeFeed() for non-HLIG users.
    // =========================================================================

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
            AND sp.isFlagged = false
            AND sp.qualityScore >= 50
            AND sp.user.isActive = true
            AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
            AND (
                sp.viralTier = 'NATIONAL_VIRAL'
                OR (sp.viralTier = 'STATE_VIRAL'    AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 2) = :statePrefix))
                OR (sp.viralTier = 'DISTRICT_VIRAL' AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 3) = :districtPrefix))
                OR (sp.viralTier = 'LOCAL'          AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 3) = :districtPrefix))
            )
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY (sp.engagementScore * 0.4 + sp.viralityScore * 0.3) DESC, sp.createdAt DESC
            """)
    List<SocialPost> findRecommendedPostsForUser(@Param("statePrefix") String statePrefix,
                                                 @Param("districtPrefix") String districtPrefix,
                                                 Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
            AND sp.isFlagged = false
            AND sp.qualityScore >= 50
            AND sp.user.isActive = true
            AND sp.id < :beforeId
            AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
            AND (
                sp.viralTier = 'NATIONAL_VIRAL'
                OR (sp.viralTier = 'STATE_VIRAL'    AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 2) = :statePrefix))
                OR (sp.viralTier = 'DISTRICT_VIRAL' AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 3) = :districtPrefix))
                OR (sp.viralTier = 'LOCAL'          AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 3) = :districtPrefix))
            )
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY (sp.engagementScore * 0.4 + sp.viralityScore * 0.3) DESC, sp.createdAt DESC
            """)
    List<SocialPost> findRecommendedPostsForUserWithCursor(@Param("statePrefix") String statePrefix,
                                                           @Param("districtPrefix") String districtPrefix,
                                                           @Param("beforeId") Long beforeId,
                                                           Pageable pageable);

    // =========================================================================
    // STATISTICS
    // =========================================================================

    Long countByUserId(Long userId);
    Long countByUserIdAndStatus(Long userId, PostStatus status);
    Long countByUserIdAndStatusNot(Long userId, PostStatus status);

    Long countByStatus(PostStatus status);

    Long countByCreatedAtAfter(Date date);
    Long countByCreatedAtAfterAndStatusNot(Date date, PostStatus status);

    @Query("SELECT sp.shareCount FROM SocialPost sp WHERE sp.id = :id")
    Integer findShareCountById(@Param("id") Long id);

    // =========================================================================
    // COMMUNITY FEED — LOCAL / PERSONALIZED
    // =========================================================================

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND (:cursor IS NULL OR sp.id < :cursor)
              AND (
                sp.community IS NULL
                OR (
                    sp.communityFeedEligible = true
                    AND sp.communityPrivacy  = 'PUBLIC'
                    AND sp.communityStatus   = 'ACTIVE'
                    AND (
                        (sp.pincode        = :pincode        AND sp.engagementScore >= :localThreshold)
                        OR (sp.districtPrefix = :districtPrefix AND sp.engagementScore >= :districtThreshold)
                        OR (sp.statePrefix    = :statePrefix    AND sp.engagementScore >= :stateThreshold)
                    )
                )
              )
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.engagementScore DESC, sp.createdAt DESC, sp.id DESC
            """)
    List<SocialPost> findLocalFeedIncludingCommunityPosts(
            @Param("pincode")           String  pincode,
            @Param("districtPrefix")    String  districtPrefix,
            @Param("statePrefix")       String  statePrefix,
            @Param("localThreshold")    int     localThreshold,
            @Param("districtThreshold") int     districtThreshold,
            @Param("stateThreshold")    int     stateThreshold,
            @Param("cursor")            Long    cursor,
            Pageable pageable);

    // =========================================================================
    // COMMUNITY FEED — NATIONAL / EXPLORE
    // =========================================================================

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND (:cursor IS NULL OR sp.id < :cursor)
              AND (
                sp.community IS NULL
                OR (
                    sp.communityFeedEligible = true
                    AND sp.communityPrivacy  = 'PUBLIC'
                    AND sp.communityStatus   = 'ACTIVE'
                    AND sp.engagementScore  >= :nationalThreshold
                )
              )
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.engagementScore DESC, sp.createdAt DESC, sp.id DESC
            """)
    List<SocialPost> findNationalFeedIncludingCommunityPosts(
            @Param("nationalThreshold") int  nationalThreshold,
            @Param("cursor")            Long cursor,
            Pageable pageable);

    // =========================================================================
    // COMMUNITY DETAIL — RECENCY SORT
    // =========================================================================

    @EntityGraph(attributePaths = {"user", "community"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.community.id = :communityId
              AND sp.status = 'ACTIVE'
              AND (:cursor IS NULL OR sp.id < :cursor)
            ORDER BY sp.createdAt DESC, sp.id DESC
            """)
    List<SocialPost> findCommunityPostsCursor(@Param("communityId") Long communityId,
                                              @Param("cursor")      Long cursor,
                                              Pageable pageable);

    @EntityGraph(attributePaths = {"user", "community"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.community.id = :communityId
              AND sp.user.id = :userId
              AND sp.status IN :statuses
              AND (:cursor IS NULL OR sp.id < :cursor)
            ORDER BY sp.createdAt DESC, sp.id DESC
            """)
    List<SocialPost> findCommunityPostsByUserCursorAndStatuses(@Param("communityId") Long communityId,
                                                               @Param("userId")      Long userId,
                                                               @Param("statuses")    List<PostStatus> statuses,
                                                               @Param("cursor")      Long cursor,
                                                               Pageable pageable);

    @EntityGraph(attributePaths = {"user", "community"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.community.id = :communityId
              AND (sp.user.id = :userId OR (:actorToken IS NOT NULL AND sp.actorToken = :actorToken))
              AND sp.status IN :statuses
              AND (:cursor IS NULL OR sp.id < :cursor)
            ORDER BY sp.createdAt DESC, sp.id DESC
            """)
    List<SocialPost> findCommunityPostsByUserOrActorTokenCursorAndStatuses(@Param("communityId") Long communityId,
                                                                           @Param("userId")      Long userId,
                                                                           @Param("actorToken")  String actorToken,
                                                                           @Param("statuses")    List<PostStatus> statuses,
                                                                           @Param("cursor")      Long cursor,
                                                                           Pageable pageable);


    @EntityGraph(attributePaths = {"user", "community"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.community.id = :communityId
              AND sp.status = 'PENDING_APPROVAL'
              AND (:cursor IS NULL OR sp.id < :cursor)
            ORDER BY sp.createdAt DESC, sp.id DESC
            """)
    List<SocialPost> findPendingCommunityPostsCursor(@Param("communityId") Long communityId,
                                                     @Param("cursor")      Long cursor,
                                                     Pageable pageable);

    // =========================================================================
    // COMMUNITY DETAIL — ENGAGEMENT SORT ("Top" / "Hot")
    // =========================================================================

    @EntityGraph(attributePaths = {"user", "community"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.community.id = :communityId
              AND sp.status = 'ACTIVE'
              AND (:cursor IS NULL OR
                   sp.engagementScore < :cursorScore OR
                   (sp.engagementScore = :cursorScore AND sp.id < :cursor))
            ORDER BY sp.engagementScore DESC, sp.id DESC
            """)
    List<SocialPost> findCommunityPostsByEngagement(@Param("communityId") Long   communityId,
                                                    @Param("cursor")      Long   cursor,
                                                    @Param("cursorScore") Double cursorScore,
                                                    Pageable pageable);

    // =========================================================================
    // DENORMALIZED FIELD SYNC
    // =========================================================================

    @Modifying
    @Query("""
            UPDATE SocialPost sp
            SET sp.communityPrivacy      = :privacy,
                sp.communityFeedEligible = :feedEligible,
                sp.communityStatus       = :status
            WHERE sp.community.id = :communityId
            """)
    void syncCommunityDenormalizedFields(@Param("communityId")  Long    communityId,
                                         @Param("privacy")       String  privacy,
                                         @Param("feedEligible")  boolean feedEligible,
                                         @Param("status")        String  status);

    // =========================================================================
    // REMOVE FROM FEED (community archived / deleted)
    // =========================================================================

    @Modifying
    @Query("UPDATE SocialPost sp SET sp.communityFeedEligible = false WHERE sp.community.id = :communityId")
    void removeCommunityPostsFromFeed(@Param("communityId") Long communityId);

    // ==========================================================================
    // UNIFIED SEARCH — CURSOR-BASED (used by SearchService)
    // ==========================================================================

    /** Keyword search across content + hashtags. */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = :status
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (LOWER(sp.content)  LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
               OR  LOWER(sp.hashtags) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
               OR  sp.pincode = :query)
            ORDER BY sp.id DESC
            """)
    List<SocialPost> searchPage(
            @Param("query")  String query,
            @Param("status") PostStatus status,
            Pageable pageable);

    /** Pincode-scoped keyword search. */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status  = :status
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (
                  (sp.pincode = :pincode AND (
                      LOWER(sp.content) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
                      OR LOWER(sp.hashtags) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
                  ))
                  OR sp.pincode = :query
              )
            ORDER BY sp.id DESC
            """)
    List<SocialPost> searchPageByPincode(
            @Param("query")   String query,
            @Param("status")  PostStatus status,
            @Param("pincode") String pincode,
            Pageable pageable);

    /** Hashtag-exact search. */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = :status
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND LOWER(sp.hashtags) LIKE LOWER(CONCAT('%', :hashtag, '%')) ESCAPE '\\'
            ORDER BY sp.id DESC
            """)
    List<SocialPost> searchByHashtagPage(
            @Param("hashtag") String hashtag,
            @Param("status")  PostStatus status,
            Pageable pageable);

    /** Top N distinct hashtags matching the search query. */
    @Query(value = """
            SELECT token               AS hashtag,
                   COUNT(sp.id)        AS post_count
            FROM   social_posts sp,
                   LATERAL (
                       SELECT TRIM(t) AS token
                       FROM   regexp_split_to_table(sp.hashtags, '\\s+') AS t
                       WHERE  TRIM(t) <> ''
                   ) tokens
            WHERE  sp.status   = 'ACTIVE'
              AND  (sp.community_id IS NULL OR ((sp.community_privacy = 'PUBLIC' OR sp.community_feed_eligible = true) AND sp.community_status = 'ACTIVE'))
              AND  sp.hashtags IS NOT NULL
              AND  LOWER(token) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
            GROUP  BY token
            ORDER  BY post_count DESC
            LIMIT  :limit
            """, nativeQuery = true)
    List<Object[]> findTopHashtags(
            @Param("query") String query,
            @Param("limit") int limit);

    // ==========================================================================
    // DATA MIGRATION — CHUNKED
    // ==========================================================================

    @Query("SELECT MAX(sp.id) FROM SocialPost sp")
    Long findMaxId();
    
    @Modifying
    @Query(value = "UPDATE social_posts sp SET community_status = c.status FROM communities c WHERE sp.community_id = c.id AND sp.id >= :startId AND sp.id < :endId AND sp.community_status IS NULL", nativeQuery = true)
    int backfillCommunityStatusChunk(@Param("startId") Long startId, @Param("endId") Long endId);

    // ==========================================================================
    // HLIG v2 — PERSONALISED FEED QUERIES (used by HLIGFeedService)
    // ==========================================================================

    /**
     * FOR YOU tab — local pincode candidates.
     * First pool in HLIGFeedService.fetchCandidates().
     * Only quality-scored active posts from the user's own pincode.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.pincode = :pincode
              AND sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.qualityScore >= 40
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findLocalFeedPosts(
            @Param("pincode") String pincode, Pageable pageable);

    /**
     * FOR YOU tab — district-viral candidates.
     * Second pool in HLIGFeedService.fetchCandidates().
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.districtPrefix = :districtPrefix
              AND sp.viralTier = 'DISTRICT_VIRAL'
              AND sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.viralityScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findDistrictViralPosts(
            @Param("districtPrefix") String districtPrefix, Pageable pageable);

    /**
     * FOR YOU tab — state-viral candidates.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.statePrefix = :statePrefix
              AND sp.viralTier = 'STATE_VIRAL'
              AND sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.viralityScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findStateViralPosts(
            @Param("statePrefix") String statePrefix, Pageable pageable);

    /**
     * FOR YOU tab — national-viral candidates.
     * Shown to all users regardless of location.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.viralTier = 'NATIONAL_VIRAL'
              AND sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.viralityScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findNationalViralPosts(Pageable pageable);

    /**
     * Cold-area fallback: recent quality posts nationwide.
     * Used when the user's local pool is too small (< 50 candidates).
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.qualityScore >= 50
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findRecentPostsNational(Pageable pageable);

    /**
     * Cross-area sparse-platform fallback — NO geo filter, NO viral-tier filter,
     * NO qualityScore filter. Returns any ACTIVE post ordered by recency.
     *
     * WHY THIS EXISTS: fetchCandidates() runs a geo waterfall. On a brand-new
     * platform with 2 users from different cities all geo waterfall steps return 0.
     * This query has none of those filters, guaranteeing cross-area users always
     * see each other's posts regardless of viral tier, quality score, or geo match.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findRecentActivePosts(Pageable pageable);

    /**
     * Sparse-platform safety net — returns ALL active posts ordered by recency.
     *
     * Last-resort fallback in HLIGFeedService sort=NEW and as a catch-all when
     * every other fallback is exhausted. Unlike findRecentPostsNational, this query
     * has NO qualityScore filter, so even brand-new posts with qualityScore=0 surface.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findAllActivePostsForFeed(Pageable pageable);

    /**
     * HOT tab — geo-scoped trending posts within the last 72 hours.
     *
     * Used by HLIGFeedService.fetchBrowsePool() for scope=LOCATION + sort=HOT,
     * and by widenToState() when the pincode-level HOT pool is sparse.
     *
     * Geo filter: posts matching the user's pincode OR districtPrefix
     *             OR state/national viral tier (null params are ignored).
     * Time filter: createdAt >= :since  (caller passes now-72h)
     * Sort: viralityScore DESC, createdAt DESC
     *
     * Index hint: idx_social_post_virality_created on (status, viralityScore, createdAt)
     * covers the WHERE and ORDER BY clauses at scale.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.createdAt >= :since
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (
                  (:pincode IS NULL        OR sp.pincode        = :pincode)
                  OR (:districtPrefix IS NULL OR sp.districtPrefix = :districtPrefix)
                  OR sp.viralTier IN ('STATE_VIRAL', 'NATIONAL_VIRAL')
              )
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.viralityScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findHotPostsForUser(
            @Param("pincode")         String pincode,
            @Param("districtPrefix")  String districtPrefix,
            @Param("since")           Date   since,
            Pageable pageable);

    /**
     * NEW tab — chronological feed for the user's state + national viral.
     *
     * Used by HLIGFeedService.fetchBrowsePool() for scope=LOCATION + sort=NEW,
     * and by widenToState() when the state-level NEW pool is sparse.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (
                  sp.statePrefix = :statePrefix
                  OR sp.viralTier = 'NATIONAL_VIRAL'
              )
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findNewPostsForUser(
            @Param("statePrefix") String statePrefix, Pageable pageable);

    /**
     * TOP tab — highest engagement posts for the user's state + national, all-time.
     *
     * Used by HLIGFeedService.fetchBrowsePool() for scope=LOCATION + sort=TOP,
     * and by widenToState() when the state-level TOP pool is sparse.
     *
     * No time window — TOP surfaces the best posts ever in the region.
     * qualityScore >= 50 guards against brand-new zero-engagement posts
     * from cluttering the all-time leaderboard.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.qualityScore >= 50
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (
                  sp.statePrefix = :statePrefix
                  OR sp.viralTier IN ('STATE_VIRAL', 'NATIONAL_VIRAL')
              )
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.engagementScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findTopPostsForUser(
            @Param("statePrefix") String statePrefix, Pageable pageable);

    /**
     * FOLLOWING tab — posts from communities the user has joined.
     * Only ACTIVE posts from communities where user has an active membership.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.community.id IN (
                SELECT cm.community.id FROM CommunityMember cm
                WHERE cm.user.id = :userId
                  AND cm.isActive = true
            )
            AND sp.status = 'ACTIVE'
            AND sp.isFlagged = false
            AND (sp.communityPrivacy <> 'SECRET' OR sp.communityFeedEligible = true)
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findPostsFromUserCommunities(
            @Param("userId") Long userId, Pageable pageable);

    // ==========================================================================
    // HLIG v2 — PLATFORM-WIDE SORT FALLBACKS
    // Used by HLIGFeedService.widenToPlatform() and absoluteFallback()
    // when geo-scoped queries return fewer than CANDIDATE_SPARSE_FLOOR posts.
    // ==========================================================================

    /**
     * HOT platform-wide fallback — viralityScore DESC within the given time window.
     *
     * Called by HLIGFeedService when:
     *   - widenToPlatform() for sort=HOT (sparse area, no geo posts viral in 72h)
     *   - absoluteFallback() for sort=HOT (last resort before returning empty feed)
     *
     * The :since parameter is supplied by the caller (typically now-72h).
     * No geo filter — surfaces the most viral posts on the entire platform.
     *
     * NOTE: viralityScore > 0 filter intentionally removed — on a sparse / early-stage
     * platform every post starts with viralityScore=0.0 until the scoring job runs.
     * Keeping that filter would make the HOT fallback return 0 results and collapse
     * the feed. Posts are still sorted DESC so genuinely viral posts lead.
     *
     * Index hint: idx_social_post_virality_created on (status, viralityScore, createdAt)
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.createdAt >= :since
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.viralityScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findHotActivePostsForFeed(
            @Param("since") Date since, Pageable pageable);

    /**
     * TOP platform-wide fallback — engagementScore DESC, all-time, no time window.
     *
     * Called by HLIGFeedService when:
     *   - widenToPlatform() for sort=TOP (sparse area, no high-engagement geo posts)
     *   - absoluteFallback() for sort=TOP (last resort before returning empty feed)
     *
     * qualityScore >= 50 guards against zero-engagement brand-new posts
     * dominating the all-time leaderboard on a sparse platform.
     *
     * Index hint: idx_social_post_engagement on (status, engagementScore DESC)
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.qualityScore >= 50
              AND (sp.user IS NULL OR sp.user.isActive = true)
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.engagementScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findTopActivePostsForFeed(Pageable pageable);

    // ==========================================================================
    // HLIG v2 — OWN-POSTS INJECTION (used by HLIGFeedService.injectOwnPosts)
    // ==========================================================================

    /**
     * Returns the user's own most recent ACTIVE posts for own-post injection.
     *
     * WHY THIS EXISTS: On a new/sparse platform a creator's post may not yet
     * have a viralTier, qualityScore, or geo-tag that passes any of the normal
     * geo waterfall queries. Without this the creator would not see their own
     * post in the feed immediately after posting.
     *
     * No qualityScore filter — a brand-new post with qualityScore=0 must
     * still be visible to its creator immediately after posting.
     *
     * Capped to 3 posts (HLIG_OWN_POST_INJECT_LIMIT) by the caller.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.user.id = :userId
              AND sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND (sp.community IS NULL OR (sp.communityFeedEligible = true AND sp.communityStatus = 'ACTIVE'))
              AND (sp.category IS NULL OR sp.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findRecentPostsByUser(
            @Param("userId") Long userId, Pageable pageable);


    // Used by language-aware widening in HLIGFeedService + fetchCandidates
    @EntityGraph(attributePaths = {"user"})
    @Query("""
  SELECT p FROM SocialPost p
  WHERE p.status = 'ACTIVE'
    AND p.language IN :languages
    AND (p.community IS NULL OR (p.communityPrivacy = 'PUBLIC' AND p.communityStatus = 'ACTIVE'))
    AND (p.category IS NULL OR p.category <> com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION)
            ORDER BY p.createdAt DESC
""")
    List<SocialPost> findActivePostsByLanguages(
            @Param("languages") List<String> languages, Pageable pageable);

    @Query(value = """
            SELECT social_post_id, COUNT(*)
            FROM post_likes
            WHERE social_post_id IN (:postIds)
              AND user_id IN (:nids)
              AND reaction_type = 'LIKE'
            GROUP BY social_post_id
            """, nativeQuery = true)
    List<Object[]> countNeighbourLikesForPosts(
            @Param("postIds") List<Long> socialPostIds,
            @Param("nids")    List<Long> neighbourIds);

    /**
     * Nullifies the community FK on all posts that belonged to a permanently-deleted community.
     * Posts are kept so they remain visible on the author's profile, but are no longer
     * associated with any community.
     */
    @Modifying
    @Query("UPDATE SocialPost p SET p.community = null, p.communityFeedEligible = false WHERE p.community.id = :communityId")
    void detachFromCommunity(@Param("communityId") Long communityId);

    // =========================================================================
    // FEED PEEK (NEW POST COUNTS)
    // =========================================================================

    /**
     * Fast approximation of new posts for the global feed (For You / Location).
     *
     * Counts ACTIVE posts newer than the given ID that are visible in the main feed:
     *   - Non-community posts (communityFeedEligible = false, communityPrivacy = null)
     *   - Community posts that are PUBLIC + feed-eligible
     *
     * NOTE: We intentionally avoid a JOIN to the communities table here.
     * All required fields are denormalized onto social_posts (communityFeedEligible,
     * communityPrivacy) for exactly this reason — single-table, index-covered COUNT.
     */
    @Query("""
            SELECT COUNT(sp.id) FROM SocialPost sp
            WHERE sp.id > :afterId
              AND sp.status = com.JanSahayak.AI.enums.PostStatus.ACTIVE
              AND sp.isFlagged = false
              AND (
                    sp.community IS NULL
                    OR (sp.communityFeedEligible = true AND sp.communityPrivacy = 'PUBLIC')
                  )
            """)
    long countNewGlobalPostsAfter(@Param("afterId") Long afterId);

    /**
     * Counts new posts in the user's joined communities (Following feed).
     */
    @Query("""
            SELECT COUNT(sp.id) FROM SocialPost sp
            WHERE sp.id > :afterId
              AND sp.status = com.JanSahayak.AI.enums.PostStatus.ACTIVE
              AND sp.community.id IN (
                  SELECT cm.community.id FROM CommunityMember cm
                  WHERE cm.user.id = :userId
                    AND cm.isActive = true
                    AND cm.isBanned = false
              )
            """)
    long countNewFollowingPostsAfter(@Param("userId") Long userId, @Param("afterId") Long afterId);

    /**
     * Counts new posts for a specific community feed.
     */
    @Query("""
            SELECT COUNT(sp.id) FROM SocialPost sp
            WHERE sp.id > :afterId
              AND sp.status = com.JanSahayak.AI.enums.PostStatus.ACTIVE
              AND sp.community.id = :communityId
            """)
    long countNewCommunityPostsAfter(@Param("communityId") Long communityId, @Param("afterId") Long afterId);


    @Query("""
            SELECT COUNT(sp) FROM SocialPost sp 
            WHERE sp.id > :afterId 
              AND sp.status = com.JanSahayak.AI.enums.PostStatus.ACTIVE 
              AND sp.isFlagged = false 
              AND sp.pincode = :pincode
              AND (
                    sp.community IS NULL 
                    OR (sp.communityFeedEligible = true AND sp.communityPrivacy = 'PUBLIC')
                  )
            """)
    long countNewLocationPostsAfter(@Param("afterId") Long afterId, @Param("pincode") String pincode);

    @Query("""
            SELECT COUNT(sp) FROM SocialPost sp 
            WHERE sp.id > :afterId 
              AND sp.status = com.JanSahayak.AI.enums.PostStatus.ACTIVE 
              AND sp.isFlagged = false 
              AND sp.category = com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION
              AND sp.pincode = :pincode
            """)
    long countNewNeighborhoodQAPostsAfter(@Param("afterId") Long afterId, @Param("pincode") String pincode);

    List<SocialPost> findByActorTokenAndStatusOrderByCreatedAtDesc(String actorToken, PostStatus status, Pageable pageable);
    List<SocialPost> findByActorTokenAndStatusAndIdLessThanOrderByCreatedAtDesc(String actorToken, PostStatus status, Long id, Pageable pageable);
    List<SocialPost> findByActorTokenAndStatusInOrderByCreatedAtDesc(String actorToken, java.util.Collection<PostStatus> statuses, Pageable pageable);
    List<SocialPost> findByActorTokenAndStatusInAndIdLessThanOrderByCreatedAtDesc(String actorToken, java.util.Collection<PostStatus> statuses, Long id, Pageable pageable);
    long countByActorTokenAndStatus(String actorToken, PostStatus status);

}
