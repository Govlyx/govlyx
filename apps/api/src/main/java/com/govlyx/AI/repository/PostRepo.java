package com.govlyx.AI.repository;

import com.govlyx.AI.enums.BroadcastScope;
import com.govlyx.AI.enums.PostStatus;
import com.govlyx.AI.model.Post;
import com.govlyx.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepo extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    Optional<Post> findByIdempotencyKey(String idempotencyKey);

    // ===== ATOMIC COUNTER UPDATES =====
    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END WHERE p.id = :id")
    void decrementLikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.dislikeCount = p.dislikeCount + 1 WHERE p.id = :id")
    void incrementDislikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.dislikeCount = CASE WHEN p.dislikeCount > 0 THEN p.dislikeCount - 1 ELSE 0 END WHERE p.id = :id")
    void decrementDislikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.id = :id")
    void incrementCommentCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.commentCount = :count WHERE p.id = :id")
    void updateCommentCount(@Param("id") Long id, @Param("count") int count);

    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.commentCount = CASE WHEN p.commentCount > :count THEN p.commentCount - :count ELSE 0 END WHERE p.id = :id")
    void decrementCommentCountBy(@Param("id") Long id, @Param("count") int count);

    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    /** Batch increment — used by ViewCountFlusher to flush accumulated view counts in one UPDATE. */
    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.viewCount = p.viewCount + :count WHERE p.id = :id")
    void incrementViewCountBy(@Param("id") Long id, @Param("count") int count);

    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.shareCount = p.shareCount + 1 WHERE p.id = :id")
    void incrementShareCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.saveCount = p.saveCount + 1 WHERE p.id = :id")
    void incrementSaveCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true) @Query("UPDATE Post p SET p.saveCount = CASE WHEN p.saveCount > 0 THEN p.saveCount - 1 ELSE 0 END WHERE p.id = :id")
    void decrementSaveCount(@Param("id") Long id);

    @Query("SELECT p.commentCount FROM Post p WHERE p.id = :id")
    Integer findCommentCountById(@Param("id") Long id);

    @Query("SELECT p.shareCount FROM Post p WHERE p.id = :id")
    Integer findShareCountById(@Param("id") Long id);

    // ===== Basic Post Query Methods =====
    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, PostStatus status);

    List<Post> findByActorTokenOrderByCreatedAtDesc(String actorToken);
    List<Post> findByActorTokenAndStatusOrderByCreatedAtDesc(String actorToken, PostStatus status);

    @Query("SELECT p FROM Post p WHERE p.actorToken = :actorToken AND p.status IN :statuses ORDER BY p.createdAt DESC")
    List<Post> findByActorTokenAndStatusInOrderByCreatedAtDesc(
            @Param("actorToken") String actorToken,
            @Param("statuses") List<PostStatus> statuses);

    @Query("SELECT p FROM Post p WHERE p.actorToken = :actorToken AND p.status IN :statuses AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByActorTokenAndStatusInAndIdLessThanOrderByCreatedAtDesc(
            @Param("actorToken") String actorToken,
            @Param("statuses") List<PostStatus> statuses,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.actorToken = :actorToken AND p.status IN :statuses ORDER BY p.createdAt DESC")
    List<Post> findByActorTokenAndStatusInOrderByCreatedAtDesc(
            @Param("actorToken") String actorToken,
            @Param("statuses") List<PostStatus> statuses,
            Pageable pageable);

    long countByActorToken(String actorToken);
    long countByActorTokenAndStatus(String actorToken, PostStatus status);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.actorToken = :actorToken AND p.status IN :statuses")
    long countByActorTokenAndStatusIn(
            @Param("actorToken") String actorToken,
            @Param("statuses") List<PostStatus> statuses);


    // Find all posts (ACTIVE + RESOLVED) by userId
    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role WHERE u.id = :userId AND p.status IN :statuses ORDER BY p.createdAt DESC")
    List<Post> findByUserIdWithUserAndStatusInOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("statuses") List<PostStatus> statuses);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role WHERE u.id = :userId AND p.status IN :statuses AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserIdWithUserAndStatusInAndIdLessThanOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("statuses") List<PostStatus> statuses,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // Find posts by specific status by userId
    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role WHERE u.id = :userId AND p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findByUserIdWithUserAndStatusOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("status") PostStatus status);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role WHERE u.id = :userId AND p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserIdWithUserAndStatusAndIdLessThanOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // ===== Count Methods =====
    Long countByStatus(PostStatus status);
    Long countByUserId(Long userId);
    Long countByUserIdAndStatus(Long userId, PostStatus status);
    Long countByUserIdAndStatusIn(Long userId, java.util.Collection<PostStatus> statuses);
    Long countByUserIdAndCreatedAtAfter(Long userId, Timestamp createdAt);

    // ===== Broadcasting Query Methods =====
    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullAndStatusOrderByCreatedAtDesc(@Param("status") PostStatus status);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByBroadcastScopeAndStatusOrderByCreatedAtDesc(
            BroadcastScope broadcastScope, PostStatus status);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByBroadcastScopeAndStatusOrderByCreatedAtDesc(
            BroadcastScope broadcastScope, PostStatus status, Pageable pageable);

    // ===== Pincode Prefix Broadcasting Query Methods =====
    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetStates LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :prefix) OR " +
            "p.targetStates = :prefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope, @Param("status") PostStatus status, @Param("prefix") String prefix);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :prefix) OR " +
            "p.targetDistricts = :prefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope, @Param("status") PostStatus status, @Param("prefix") String prefix);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope, @Param("status") PostStatus status, @Param("pincode") String pincode);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) AND p.createdAt >= :startDate " +
            "ORDER BY p.createdAt DESC")
    List<Post> findRecentAreaBroadcasts(
            @Param("scope") BroadcastScope scope, 
            @Param("status") PostStatus status, 
            @Param("pincode") String pincode,
            @Param("startDate") Timestamp startDate,
            Pageable pageable);

    // ===== Efficient User Visibility Query Methods =====
    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetStates LIKE CONCAT('%,', :userStatePrefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:userStatePrefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :userStatePrefix) OR " +
            "p.targetStates = :userStatePrefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findStateBroadcastsForUser(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("userStatePrefix") String userStatePrefix);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :userDistrictPrefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:userDistrictPrefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :userDistrictPrefix) OR " +
            "p.targetDistricts = :userDistrictPrefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findDistrictBroadcastsForUser(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("userDistrictPrefix") String userDistrictPrefix);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :userPincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:userPincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :userPincode) OR " +
            "p.targetPincodes = :userPincode) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findAreaBroadcastsForUser(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("userPincode") String userPincode);

    // ===== Broadcasting Count Methods =====
    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope IS NOT NULL")
    Long countByBroadcastScopeIsNotNull();

    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.status != :status")
    Long countByBroadcastScopeIsNotNullAndStatusNot(@Param("status") PostStatus status);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.status = :status")
    Long countByBroadcastScopeIsNotNullAndStatus(@Param("status") PostStatus status);

    Long countByBroadcastScope(BroadcastScope scope);
    Long countByBroadcastScopeAndStatusNot(BroadcastScope scope, PostStatus status);
    Long countByBroadcastScopeAndStatus(BroadcastScope scope, PostStatus status);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.user.id = :userId AND p.broadcastScope IS NOT NULL")
    Long countByUserIdAndBroadcastScopeIsNotNull(@Param("userId") Long userId);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.user.id = :userId AND p.broadcastScope IS NOT NULL AND p.status != :status")
    Long countByUserIdAndBroadcastScopeIsNotNullAndStatusNot(@Param("userId") Long userId, @Param("status") PostStatus status);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.user.id = :userId AND p.broadcastScope IS NOT NULL AND p.createdAt > :timestamp")
    Long countByUserIdAndBroadcastScopeIsNotNullAndCreatedAtAfter(
            @Param("userId") Long userId, @Param("timestamp") Timestamp timestamp);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.user.id = :userId AND p.broadcastScope IS NOT NULL AND p.createdAt > :timestamp AND p.status != :status")
    Long countByUserIdAndBroadcastScopeIsNotNullAndCreatedAtAfterAndStatusNot(
            @Param("userId") Long userId, @Param("timestamp") Timestamp timestamp, @Param("status") PostStatus status);

    Long countByUserIdAndBroadcastScope(Long userId, BroadcastScope scope);
    Long countByUserIdAndBroadcastScopeAndStatusNot(Long userId, BroadcastScope scope, PostStatus status);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.user.id = :userId AND p.broadcastScope IS NOT NULL")
    List<Post> findByUserIdAndBroadcastScopeIsNotNull(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.user.id = :userId AND p.broadcastScope IS NOT NULL AND p.status != :status")
    List<Post> findByUserIdAndBroadcastScopeIsNotNullAndStatusNot(@Param("userId") Long userId, @Param("status") PostStatus status);

    // ===== FIX: Single GROUP BY query to replace N per-scope COUNT loops =====
    // Used by PostService.getBroadcastStatistics() to avoid 2×N individual COUNT queries.
    // Returns rows of [BroadcastScope, PostStatus, count].
    @Query("SELECT p.broadcastScope, p.status, COUNT(p) FROM Post p " +
            "WHERE p.broadcastScope IS NOT NULL GROUP BY p.broadcastScope, p.status")
    List<Object[]> countByBroadcastScopeGrouped();

    // ===== Enhanced Feed System Methods =====
    Long countByUserIdInAndStatus(List<Long> userIds, PostStatus status);

    // ===== Paginated Query Methods (Legacy - keeping for backward compatibility) =====
    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findByStatusWithPageable(@Param("status") PostStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p ORDER BY p.createdAt DESC")
    List<Post> findAllOrderByCreatedAtDesc(Pageable pageable);

    // ===== User Tagging Query Methods =====
    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE SIZE(p.userTags) > 1 AND p.status = :status")
    List<Post> findPostsWithMultipleUserTags(@Param("status") PostStatus status);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE SIZE(p.userTags) > 1 AND p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findPostsWithMultipleUserTagsAndIdLessThan(
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT DISTINCT p FROM Post p " +
            "JOIN p.userTags ut " +
            "WHERE ut.taggedUser.id = :userId " +
            "AND ut.isActive = true " +
            "ORDER BY p.createdAt DESC")
    List<Post> findPostsTaggedWithUserId(@Param("userId") Long userId);

    // ===== Trending and Analytics Methods =====
    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.createdAt >= :startDate AND p.status = :status " +
            "ORDER BY (p.likeCount + p.commentCount + p.viewCount) DESC")
    List<Post> findTrendingPosts(@Param("startDate") Timestamp startDate, @Param("status") PostStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.createdAt >= :startDate AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY (p.likeCount + p.commentCount + p.viewCount) DESC")
    List<Post> findTrendingPostsWithCursor(
            @Param("startDate") Timestamp startDate,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT " +
            "SUM(CASE WHEN p.userTags IS NOT EMPTY THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN p.userTags IS EMPTY THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN SIZE(p.userTags) > 1 THEN 1 ELSE 0 END), " +
            "AVG(SIZE(p.userTags)) " +
            "FROM Post p")
    Object[] getTaggedPostsStatistics();

    // ===== Search Methods for PostSearchService =====
    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByStatusAndContentContainingIgnoreCaseOrderByCreatedAtDesc(PostStatus status, String content);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByUserIdInAndStatusOrderByCreatedAtDesc(List<Long> userIds, PostStatus status);
    // Note: The 3-param versions of findByBroadcastScopeAndStatus*Containing* are defined
    // with @Query above (lines 46‑71). No un-annotated duplicates here.
    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByStatusAndCreatedAtAfterOrderByCreatedAtDesc(PostStatus status, Date fromDate);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByStatusOrderByCreatedAtDesc(PostStatus status);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByUserIdInOrderByCreatedAtDesc(List<Long> userIds);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.user.id IN :userIds AND p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :content, '%')) ESCAPE '\\' " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByUserIdInAndStatusAndContentContainingIgnoreCaseOrderByCreatedAtDesc(
            @Param("userIds") List<Long> userIds,
            @Param("status") PostStatus status,
            @Param("content") String content);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(:hashtag) ESCAPE '\\' " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByStatusAndHashtagOrderByCreatedAtDesc(
            @Param("status") PostStatus status,
            @Param("hashtag") String hashtag);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :content, '%')) ESCAPE '\\'")
    Long countByStatusAndContentContainingIgnoreCase(
            @Param("status") PostStatus status,
            @Param("content") String content);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.user.id IN :userIds AND p.status = :status " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByUserIdInAndStatusOrderByCreatedAtDesc(
            @Param("userIds") List<Long> userIds,
            @Param("status") PostStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IN :scopes AND p.status = :status " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeInAndStatusOrderByCreatedAtDesc(
            @Param("scopes") List<BroadcastScope> scopes,
            @Param("status") PostStatus status);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT DISTINCT p FROM Post p WHERE " +
            "(p.broadcastScope IS NULL OR " +
            "(p.broadcastScope = :countryScope) OR " +
            "(p.broadcastScope = :stateScope AND p.targetStates LIKE CONCAT('%', :statePrefix, '%')) OR " +
            "(p.broadcastScope = :districtScope AND p.targetDistricts LIKE CONCAT('%', :districtPrefix, '%')) OR " +
            "(p.broadcastScope = :areaScope AND p.targetPincodes LIKE CONCAT('%', :pincode, '%'))) " +
            "AND p.status = :status " +
            "ORDER BY p.createdAt DESC")
    List<Post> findVisiblePostsForUserByLocation(
            @Param("countryScope") BroadcastScope countryScope,
            @Param("stateScope") BroadcastScope stateScope,
            @Param("districtScope") BroadcastScope districtScope,
            @Param("areaScope") BroadcastScope areaScope,
            @Param("statePrefix") String statePrefix,
            @Param("districtPrefix") String districtPrefix,
            @Param("pincode") String pincode,
            @Param("status") PostStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByBroadcastScopeAndTargetCountryOrderByCreatedAtDesc(BroadcastScope scope, String targetCountry);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByBroadcastScopeAndStatusAndTargetCountryOrderByCreatedAtDesc(
            BroadcastScope scope, PostStatus status, String targetCountry);
    Long countByBroadcastScopeAndTargetCountry(BroadcastScope scope, String targetCountry);
    Long countByBroadcastScopeAndTargetCountryAndStatusNot(BroadcastScope scope, String targetCountry, PostStatus status);
    Long countByBroadcastScopeAndStatusAndTargetCountry(BroadcastScope scope, PostStatus status, String targetCountry);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT DISTINCT p FROM Post p WHERE " +
            "(p.broadcastScope IS NULL OR " +
            "(p.broadcastScope = :countryScope AND p.targetCountry = 'IN') OR " +
            "(p.broadcastScope = :stateScope AND p.targetStates LIKE CONCAT('%', :statePrefix, '%')) OR " +
            "(p.broadcastScope = :districtScope AND p.targetDistricts LIKE CONCAT('%', :districtPrefix, '%')) OR " +
            "(p.broadcastScope = :areaScope AND p.targetPincodes LIKE CONCAT('%', :pincode, '%'))) " +
            "AND p.status = :status " +
            "ORDER BY p.createdAt DESC")
    List<Post> findVisiblePostsForIndianUser(
            @Param("countryScope") BroadcastScope countryScope,
            @Param("stateScope") BroadcastScope stateScope,
            @Param("districtScope") BroadcastScope districtScope,
            @Param("areaScope") BroadcastScope areaScope,
            @Param("statePrefix") String statePrefix,
            @Param("districtPrefix") String districtPrefix,
            @Param("pincode") String pincode,
            @Param("status") PostStatus status);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND " +
            "p.targetCountry = 'IN' ORDER BY p.createdAt DESC")
    List<Post> findAllIndiaBroadcastPosts();

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND " +
            "p.targetCountry = 'IN' AND p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findActiveIndiaBroadcastPosts(@Param("status") PostStatus status);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.targetCountry = 'IN'")
    Long countIndiaBroadcastPosts();

    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope IS NOT NULL AND " +
            "p.targetCountry = 'IN' AND p.status = :status")
    Long countActiveIndiaBroadcastPosts(@Param("status") PostStatus status);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :countryScope AND " +
            "p.targetCountry = 'IN' AND p.user.role.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN') " +
            "ORDER BY p.createdAt DESC")
    List<Post> findGovernmentCountryBroadcasts(@Param("countryScope") BroadcastScope countryScope);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role r WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "p.targetPincodes LIKE CONCAT('%', :pincode, '%') AND " +
            "r.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN') ORDER BY p.createdAt DESC")
    List<Post> findOfficialAreaBroadcasts(@Param("scope") BroadcastScope scope, @Param("status") PostStatus status, @Param("pincode") String pincode);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role r WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "p.targetDistricts LIKE CONCAT('%', :prefix, '%') AND " +
            "r.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN') ORDER BY p.createdAt DESC")
    List<Post> findOfficialDistrictBroadcasts(@Param("scope") BroadcastScope scope, @Param("status") PostStatus status, @Param("prefix") String prefix);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role r WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "p.targetStates LIKE CONCAT('%', :prefix, '%') AND " +
            "r.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN') ORDER BY p.createdAt DESC")
    List<Post> findOfficialStateBroadcasts(@Param("scope") BroadcastScope scope, @Param("status") PostStatus status, @Param("prefix") String prefix);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role r WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "r.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN') " +
            "ORDER BY p.createdAt DESC")
    List<Post> findOfficialCountryBroadcasts(@Param("scope") BroadcastScope scope, @Param("status") PostStatus status);

    /**
     * Absolute fallback — ALL active broadcasts from departments/admins regardless of geo-targeting.
     * Used when all 4 geo tiers return empty (e.g. targetCountry is null, or scope is not set).
     */
    @Query("""
            SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role r
            WHERE p.status = :status
              AND p.broadcastScope IS NOT NULL
              AND r.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN')
            ORDER BY p.createdAt DESC
            """)
    List<Post> findAllOfficialBroadcasts(
            @Param("status") PostStatus status,
            Pageable pageable);



    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope = :countryScope AND " +
            "p.user.role.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    Long countGovernmentCountryBroadcasts(@Param("countryScope") BroadcastScope countryScope);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByTargetCountry(String targetCountry);

    @Query("UPDATE Post p SET p.targetCountry = 'IN' WHERE p.targetCountry IS NULL OR p.targetCountry = ''")
    @Modifying
    int updateNullTargetCountryToIndia();

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.targetCountry IS NULL OR p.targetCountry = ''")
    List<Post> findPostsWithoutTargetCountry();

    Long countByUserIdAndBroadcastScopeAndTargetCountry(Long userId, BroadcastScope scope, String targetCountry);
    Long countByUserIdAndBroadcastScopeAndTargetCountryAndStatusNot(Long userId, BroadcastScope scope, String targetCountry, PostStatus status);

    // ===== FIX: JOIN FETCH user+role on primary status feed queries =====
    // These replace the bare findByStatusOrderByCreatedAtDesc variants to eliminate
    // N lazy-load queries for post.getUser() and user.getRole() in feed rendering.
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user u LEFT JOIN FETCH u.role " +
            "WHERE p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findByStatusOrderByCreatedAtDesc(
            @Param("status") PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user u LEFT JOIN FETCH u.role " +
            "WHERE p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByStatusAndIdLessThanOrderByCreatedAtDesc(
            PostStatus status,
            Long beforeId,
            Pageable pageable);

    // ===== CURSOR-BASED PAGINATION METHODS =====

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByIdLessThanOrderByCreatedAtDesc(
            @Param("beforeId") Long beforeId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.user.id = :userId AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserIdAndIdLessThanOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.user.id = :userId AND p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserIdAndStatusAndIdLessThanOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.user.role.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN') AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullAndIdLessThanOrderByCreatedAtDesc(
            @Param("beforeId") Long beforeId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.user.role.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN') ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullAndStatusAndIdLessThanOrderByCreatedAtDesc(
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullAndStatusOrderByCreatedAtDesc(
            @Param("status") PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role " +
            "WHERE p.broadcastScope = :scope AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.targetCountry = :targetCountry AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetCountryAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("targetCountry") String targetCountry,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.targetCountry = :targetCountry ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetCountryOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("targetCountry") String targetCountry,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND p.targetCountry = :targetCountry AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetCountryAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("targetCountry") String targetCountry,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetStates LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :prefix) OR " +
            "p.targetStates = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetStatesContainingAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :prefix) OR " +
            "p.targetDistricts = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetDistrictsContainingAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetPincodesContainingAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("pincode") String pincode,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) AND p.createdAt >= :startDate")
    Long countByBroadcastScopeAndTargetPincodesContainingAndCreatedAtAfter(
            @Param("scope") BroadcastScope scope, 
            @Param("pincode") String pincode,
            @Param("startDate") Date startDate);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) AND p.isResolved = :isResolved AND p.createdAt >= :startDate")
    Long countByBroadcastScopeAndTargetPincodesContainingAndIsResolvedAndCreatedAtAfter(
            @Param("scope") BroadcastScope scope, 
            @Param("pincode") String pincode,
            @Param("isResolved") boolean isResolved,
            @Param("startDate") Date startDate);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) AND p.isResolved = :isResolved AND p.status = :status " +
            "ORDER BY p.likeCount DESC, p.createdAt ASC")
    List<Post> findTopUnresolvedIssueByPincode(
            @Param("scope") BroadcastScope scope, 
            @Param("pincode") String pincode,
            @Param("isResolved") boolean isResolved,
            @Param("status") PostStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT DISTINCT p FROM Post p " +
            "JOIN p.userTags ut " +
            "WHERE ut.taggedUser.id = :userId AND ut.isActive = true AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findPostsTaggedWithUserIdAndIdLessThan(
            @Param("userId") Long userId,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE SIZE(p.userTags) > 1 AND p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findPostsWithMultipleUserTagsAndIdLessThanLegacy(
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.user.id = :userId AND p.broadcastScope IS NOT NULL AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserIdAndBroadcastScopeIsNotNullAndIdLessThanOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.user.id IN :userIds AND p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserIdInAndStatusAndIdLessThanOrderByCreatedAtDesc(
            @Param("userIds") List<Long> userIds,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :content, '%')) ESCAPE '\\' AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByStatusAndContentContainingIgnoreCaseAndIdLessThanOrderByCreatedAtDesc(
            @Param("status") PostStatus status,
            @Param("content") String content,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.createdAt >= :startDate AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY (p.likeCount + p.commentCount + p.viewCount) DESC")
    List<Post> findTrendingPostsWithCursorLegacy(
            @Param("startDate") Timestamp startDate,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // ===== ADDITIONAL OPTIMIZED PAGINATION METHODS =====

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT DISTINCT p FROM Post p WHERE " +
            "(p.broadcastScope IS NULL OR " +
            "(p.broadcastScope = :countryScope) OR " +
            "(p.broadcastScope = :stateScope AND p.targetStates LIKE CONCAT('%', :statePrefix, '%')) OR " +
            "(p.broadcastScope = :districtScope AND p.targetDistricts LIKE CONCAT('%', :districtPrefix, '%')) OR " +
            "(p.broadcastScope = :areaScope AND p.targetPincodes LIKE CONCAT('%', :pincode, '%'))) " +
            "AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findVisiblePostsForUserByLocationAndIdLessThan(
            @Param("countryScope") BroadcastScope countryScope,
            @Param("stateScope") BroadcastScope stateScope,
            @Param("districtScope") BroadcastScope districtScope,
            @Param("areaScope") BroadcastScope areaScope,
            @Param("statePrefix") String statePrefix,
            @Param("districtPrefix") String districtPrefix,
            @Param("pincode") String pincode,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.user.id IN :userIds AND p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :content, '%')) ESCAPE '\\' AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByUserIdInAndStatusAndContentContainingIgnoreCaseAndIdLessThanOrderByCreatedAtDesc(
            @Param("userIds") List<Long> userIds,
            @Param("status") PostStatus status,
            @Param("content") String content,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(:hashtag) ESCAPE '\\' AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByStatusAndHashtagAndIdLessThanOrderByCreatedAtDesc(
            @Param("status") PostStatus status,
            @Param("hashtag") String hashtag,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :countryScope AND " +
            "p.user.role.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN') AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findGovernmentCountryBroadcastsAndIdLessThan(
            @Param("countryScope") BroadcastScope countryScope,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND " +
            "p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findAllIndiaBroadcastPostsAndIdLessThan(
            @Param("beforeId") Long beforeId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND " +
            "p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findActiveIndiaBroadcastPostsAndIdLessThan(
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.createdAt >= :fromDate AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByStatusAndCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(
            @Param("status") PostStatus status,
            @Param("fromDate") Date fromDate,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IN :scopes AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeInAndStatusAndIdLessThanOrderByCreatedAtDesc(
            @Param("scopes") List<BroadcastScope> scopes,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // ===== PERFORMANCE-OPTIMIZED NATIVE QUERIES =====

    @Query(value = "SELECT p.* FROM posts p " +
            "WHERE p.user_id = :userId AND p.id < :beforeId " +
            "ORDER BY p.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Post> findUserPostsFast(
            @Param("userId") Long userId,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    @Query(value = "SELECT p.* FROM posts p " +
            "WHERE p.broadcast_scope IS NOT NULL AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Post> findBroadcastPostsFast(
            @Param("status") String status,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    @Query(value = "SELECT p.* FROM posts p " +
            "WHERE p.broadcast_scope = 'COUNTRY' AND p.target_country = 'IN' " +
            "AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Post> findCountryBroadcastsFast(
            @Param("status") String status,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    @Query(value = "SELECT p.* FROM posts p " +
            "WHERE ((p.broadcast_scope = 'AREA' AND p.target_pincodes LIKE CONCAT('%',:pincode,'%')) " +
            "OR (p.broadcast_scope = 'COUNTRY' AND p.target_country = 'IN')) " +
            "AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Post> findLocationSpecificPostsFast(
            @Param("pincode") String pincode,
            @Param("status") String status,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    // ===== UTILITY METHODS FOR CURSOR-BASED PAGINATION =====

    @Query("SELECT MAX(p.id) FROM Post p")
    Long findMaxPostId();

    @Query("SELECT MIN(p.id) FROM Post p")
    Long findMinPostId();

    @Query("SELECT COUNT(p) FROM Post p WHERE p.id >= :startId AND p.id <= :endId")
    Long countPostsInIdRange(@Param("startId") Long startId, @Param("endId") Long endId);

    @Query("SELECT MAX(p.id) FROM Post p WHERE p.user.id = :userId")
    Long findLatestPostIdForUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(p) > 0 FROM Post p WHERE p.id < :beforeId AND p.status = :status")
    Boolean hasMorePostsAfterCursor(
            @Param("beforeId") Long beforeId, @Param("status") PostStatus status);

    // ===== OPTIMIZED COUNT METHODS FOR PAGINATION METADATA =====

    @Query(value = "SELECT reltuples::BIGINT FROM pg_class WHERE relname = 'posts'",
            nativeQuery = true)
    Long estimatePostCount();

    @Query("SELECT COUNT(p) FROM Post p WHERE p.id > :afterId AND p.status = :status")
    Long countPostsNewerThan(
            @Param("afterId") Long afterId, @Param("status") PostStatus status);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.user.id = :userId AND p.id > :afterId AND p.status = :status")
    Long countUserPostsNewerThan(
            @Param("userId") Long userId,
            @Param("afterId") Long afterId,
            @Param("status") PostStatus status);

    // ===== BATCH OPERATIONS =====

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.id IN :ids ORDER BY p.createdAt DESC")
    List<Post> findPostsByIds(@Param("ids") List<Long> ids);

    @Query("SELECT p FROM Post p " +
            "LEFT JOIN FETCH p.user u " +
            "LEFT JOIN FETCH u.role " +
            "WHERE p.id IN :ids ORDER BY p.createdAt DESC")
    List<Post> findPostsByIdsWithUser(@Param("ids") List<Long> ids);

    // ===== CLEANUP AND MAINTENANCE METHODS =====

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.createdAt < :cutoffDate ORDER BY p.id DESC")
    List<Post> findOldPostsForMaintenance(
            @Param("cutoffDate") Date cutoffDate, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.targetCountry IS NULL")
    List<Post> findBroadcastPostsWithoutTargetCountry();

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND p.targetCountry = :targetCountry ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetCountryOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("targetCountry") String targetCountry,
            Pageable pageable);

    // ===== REQUIRED METHODS FOR POSTSEARCHSERVICE CURSOR-BASED PAGINATION =====

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByStatusAndContentContainingIgnoreCaseOrderByIdDesc(
            PostStatus status, String content, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :content, '%')) ESCAPE '\\' AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByStatusAndContentContainingIgnoreCaseAndIdBeforeOrderByIdDesc(
            @Param("status") PostStatus status,
            @Param("content") String content,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByUserIdInAndStatusOrderByIdDesc(
            List<Long> userIds, PostStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.user.id IN :userIds AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByUserIdInAndStatusAndIdBeforeOrderByIdDesc(
            @Param("userIds") List<Long> userIds,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByIdDesc(
            BroadcastScope scope, PostStatus status, String pincode, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetPincodesContainingAndIdBeforeOrderByIdDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("pincode") String pincode,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByIdDesc(
            BroadcastScope scope, PostStatus status, String statePrefix, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetStates LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :prefix) OR " +
            "p.targetStates = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetStatesContainingAndIdBeforeOrderByIdDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByIdDesc(
            BroadcastScope scope, PostStatus status, String districtPrefix, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :prefix) OR " +
            "p.targetDistricts = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetDistrictsContainingAndIdBeforeOrderByIdDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByStatusAndCreatedAtAfterOrderByIdDesc(
            PostStatus status, Date fromDate, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.createdAt >= :fromDate AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByStatusAndCreatedAtAfterAndIdBeforeOrderByIdDesc(
            @Param("status") PostStatus status,
            @Param("fromDate") Date fromDate,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByUserInAndStatusAndBroadcastScopeIsNullOrderByCreatedAtDesc(
            List<User> users, PostStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByUserInAndStatusAndBroadcastScopeIsNullAndIdLessThanOrderByCreatedAtDesc(
            List<User> users, PostStatus status, Long beforeId, Pageable pageable);

    // ===== GEOGRAPHIC BROADCAST METHODS WITH PAGINATION SUPPORT =====

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetStates LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :prefix) OR " +
            "p.targetStates = :prefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetStatesContainingOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("prefix") String prefix,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :prefix) OR " +
            "p.targetDistricts = :prefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetDistrictsContainingOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("prefix") String prefix,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetPincodesContainingOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("pincode") String pincode,
            Pageable pageable);

    // ===== CURSOR-BASED GEOGRAPHIC BROADCAST METHODS =====

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetStates LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :prefix) OR " +
            "p.targetStates = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetStatesContainingAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :prefix) OR " +
            "p.targetDistricts = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetDistrictsContainingAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetPincodesContainingAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("pincode") String pincode,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // ===== COUNT METHODS WITH GEOGRAPHIC TARGETING =====

    Long countByBroadcastScopeAndStatusAndTargetStatesContaining(
            BroadcastScope scope, PostStatus status, String statePrefix);

    Long countByBroadcastScopeAndStatusAndTargetDistrictsContaining(
            BroadcastScope scope, PostStatus status, String districtPrefix);

    Long countByBroadcastScopeAndStatusAndTargetPincodesContaining(
            BroadcastScope scope, PostStatus status, String pincode);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(
            BroadcastScope scope, PostStatus status, String statePrefix, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(
            BroadcastScope scope, PostStatus status, String districtPrefix, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Post> findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(
            BroadcastScope scope, PostStatus status, String pincode, Pageable pageable);

    // ==========================================================================
    // ===== UNIFIED SEARCH — CURSOR-BASED (used by SearchService) ==============
    // ==========================================================================

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("""
            SELECT p FROM Post p
            LEFT JOIN p.user u
            WHERE p.status = :status
              AND (LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
               OR p.authorPincode = :query
               OR (u IS NOT NULL AND u.pincode = :query)
               OR p.targetPincodes LIKE CONCAT('%', :query, '%'))
            ORDER BY p.id DESC
            """)
    List<Post> searchPage(
            @Param("query")  String query,
            @Param("status") PostStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "user.role"})
    @Query("""
            SELECT p FROM Post p
            LEFT JOIN p.user u
            WHERE p.status = :status
              AND (
                  ((p.authorPincode = :pincode OR (u IS NOT NULL AND u.pincode = :pincode)) AND LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\')
                  OR p.authorPincode = :query
                  OR (u IS NOT NULL AND u.pincode = :query)
                  OR p.targetPincodes LIKE CONCAT('%', :query, '%')
              )
            ORDER BY p.id DESC
            """)
    List<Post> searchPageByPincode(
            @Param("query")   String query,
            @Param("status")  PostStatus status,
            @Param("pincode") String pincode,
            Pageable pageable
    );

    // ==========================================================================
    // ===== JOIN FETCH METHODS — fixes LazyInitializationException on User =====
    // ==========================================================================

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role WHERE p.user = :user ORDER BY p.createdAt DESC")
    List<Post> findByUserWithUserOrderByCreatedAtDesc(@Param("user") User user);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role WHERE p.user = :user AND p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findByUserWithUserAndStatusOrderByCreatedAtDesc(
            @Param("user") User user,
            @Param("status") PostStatus status);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role WHERE p.user = :user AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserWithUserAndIdLessThanOrderByCreatedAtDesc(
            @Param("user") User user,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role WHERE p.user = :user AND p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserWithUserAndStatusAndIdLessThanOrderByCreatedAtDesc(
            @Param("user") User user,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role WHERE p.user = :user AND p.status IN :statuses AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserWithUserAndStatusInAndIdLessThanOrderByCreatedAtDesc(
            @Param("user") User user,
            @Param("statuses") List<PostStatus> statuses,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role WHERE p.user = :user AND p.status IN :statuses ORDER BY p.createdAt DESC")
    List<Post> findByUserWithUserAndStatusInOrderByCreatedAtDesc(
            @Param("user") User user,
            @Param("statuses") List<PostStatus> statuses);

    // ==========================================================================
    // ===== CITIZEN ISSUE POST — GEOGRAPHIC WATERFALL (Location Tab) ===========
    // ==========================================================================

    /**
     * Citizen posts from the EXACT same pincode.
     * Waterfall tier 1: same neighbourhood.
     */
    @Query("""
            SELECT p FROM Post p 
            LEFT JOIN FETCH p.user u 
            LEFT JOIN FETCH u.role r
            WHERE p.status = :status
              AND (r.name = 'ROLE_USER' OR r.name IS NULL)
              AND (p.targetPincodes LIKE CONCAT('%', :pincode, '%') OR p.authorPincode = :pincode OR u.pincode = :pincode)
            ORDER BY p.createdAt DESC
            """)
    List<Post> findCitizenPostsByPincode(
            @Param("status") PostStatus status,
            @Param("pincode") String pincode,
            Pageable pageable);

    /**
     * Citizen posts from the same DISTRICT (first 3 digits of pincode match).
     * Waterfall tier 2: same district.
     */
    @Query("""
            SELECT p FROM Post p 
            LEFT JOIN FETCH p.user u 
            LEFT JOIN FETCH u.role r
            WHERE p.status = :status
              AND (r.name = 'ROLE_USER' OR r.name IS NULL)
              AND (p.targetPincodes LIKE CONCAT('%', :districtPrefix, '%') OR p.authorPincode LIKE CONCAT(:districtPrefix, '%') OR u.pincode LIKE CONCAT(:districtPrefix, '%'))
            ORDER BY p.createdAt DESC
            """)
    List<Post> findCitizenPostsByDistrictPrefix(
            @Param("status") PostStatus status,
            @Param("districtPrefix") String districtPrefix,
            Pageable pageable);

    /**
     * Citizen posts from the same STATE (first 2 digits of pincode match).
     * Waterfall tier 3: same state.
     */
    @Query("""
            SELECT p FROM Post p 
            LEFT JOIN FETCH p.user u 
            LEFT JOIN FETCH u.role r
            WHERE p.status = :status
              AND (r.name = 'ROLE_USER' OR r.name IS NULL)
              AND (p.targetPincodes LIKE CONCAT('%', :statePrefix, '%') OR p.authorPincode LIKE CONCAT(:statePrefix, '%') OR u.pincode LIKE CONCAT(:statePrefix, '%'))
            ORDER BY p.createdAt DESC
            """)
    List<Post> findCitizenPostsByStatePrefix(
            @Param("status") PostStatus status,
            @Param("statePrefix") String statePrefix,
            Pageable pageable);

    /**
     * ALL citizen posts across the platform (national fallback).
     * Waterfall tier 4: when local/district/state pools are all sparse.
     */
    @Query("""
            SELECT p FROM Post p 
            LEFT JOIN FETCH p.user u 
            LEFT JOIN FETCH u.role r
            WHERE p.status = :status
              AND (r.name = 'ROLE_USER' OR r.name IS NULL)
            ORDER BY p.createdAt DESC
            """)
    List<Post> findAllCitizenPosts(
            @Param("status") PostStatus status,
            Pageable pageable);

    /**
     * Finds posts for the Location feed using the interaction-based waterfall model.
     */
    @Query("""
            SELECT p FROM Post p 
            LEFT JOIN FETCH p.user u 
            LEFT JOIN FETCH u.role r
            WHERE p.status = 'ACTIVE' 
              AND (r.name = 'ROLE_USER' OR r.name IS NULL)
              AND (
                p.targetPincodes LIKE CONCAT('%', :pincode, '%') OR 
                p.authorPincode = :pincode OR 
                u.pincode = :pincode OR
                (p.broadcastScope = 'AREA' AND (p.targetPincodes LIKE CONCAT('%', :pincode, '%') OR p.authorPincode = :pincode OR u.pincode = :pincode)) OR
                (p.broadcastScope = 'NEARBY' AND (p.authorPincode IN :nearbyPincodes OR u.pincode IN :nearbyPincodes OR p.targetPincodes IN :nearbyPincodes)) OR
                (p.broadcastScope = 'DISTRICT' AND (p.authorPincode LIKE CONCAT(:districtPrefix, '%') OR u.pincode LIKE CONCAT(:districtPrefix, '%') OR p.targetPincodes LIKE CONCAT('%', :districtPrefix, '%'))) OR
                (p.broadcastScope = 'STATE' AND (p.authorPincode LIKE CONCAT(:statePrefix, '%') OR u.pincode LIKE CONCAT(:statePrefix, '%') OR p.targetPincodes LIKE CONCAT('%', :statePrefix, '%'))) OR
                (p.broadcastScope = 'COUNTRY')
              )
            ORDER BY p.createdAt DESC
            """)
    List<Post> findLocationFeedPosts(
            @Param("pincode") String pincode, 
            @Param("nearbyPincodes") java.util.Collection<String> nearbyPincodes,
            @Param("districtPrefix") String districtPrefix,
            @Param("statePrefix") String statePrefix,
            Pageable pageable);

    /**
     * Finds ACTIVE unresolved citizen posts that have reached interaction thresholds for escalation.
     */
    @Query("""
            SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH u.role r
            WHERE p.status = 'ACTIVE' 
              AND p.isResolved = false 
              AND r.name = 'ROLE_USER'
              AND (
                   (p.broadcastScope = 'AREA' AND (p.likeCount * 2 + p.commentCount * 3 + p.shareCount * 4) > 20) OR
                   (p.broadcastScope = 'NEARBY' AND (p.likeCount * 2 + p.commentCount * 3 + p.shareCount * 4) > 50) OR
                   (p.broadcastScope = 'DISTRICT' AND (p.likeCount * 2 + p.commentCount * 3 + p.shareCount * 4) > 200) OR
                   (p.broadcastScope = 'STATE' AND (p.likeCount * 2 + p.commentCount * 3 + p.shareCount * 4) > 1000)
              )
            """)
    List<Post> findPostsEligibleForEscalation();

    @Query("""
            SELECT COUNT(p) FROM Post p 
            WHERE p.id > :afterId 
              AND p.status = com.govlyx.AI.enums.PostStatus.ACTIVE
              AND p.broadcastScope IN (com.govlyx.AI.enums.BroadcastScope.COUNTRY, com.govlyx.AI.enums.BroadcastScope.STATE, com.govlyx.AI.enums.BroadcastScope.DISTRICT, com.govlyx.AI.enums.BroadcastScope.AREA)
            """)
    long countNewOfficialPostsAfter(@Param("afterId") Long afterId);

    @Query("SELECT COUNT(DISTINCT p) FROM Post p JOIN p.userTags ut JOIN ut.taggedUser tu JOIN tu.role r " +
           "WHERE r.name = 'ROLE_DEPARTMENT' AND p.targetPincodes LIKE %:pincode% AND p.createdAt > :createdAt")
    long countIssuesByDepartmentTagAndPincode(@Param("pincode") String pincode, @Param("createdAt") Date createdAt);

    @Query("SELECT COUNT(DISTINCT p) FROM Post p JOIN p.userTags ut JOIN ut.taggedUser tu JOIN tu.role r " +
           "WHERE r.name = 'ROLE_DEPARTMENT' AND p.targetPincodes LIKE %:pincode% AND p.isResolved = :isResolved AND p.createdAt > :createdAt")
    long countResolvedIssuesByDepartmentTagAndPincode(@Param("pincode") String pincode, @Param("isResolved") boolean isResolved, @Param("createdAt") Date createdAt);

    @Query("SELECT DISTINCT p FROM Post p JOIN p.userTags ut JOIN ut.taggedUser tu JOIN tu.role r " +
           "WHERE r.name = 'ROLE_DEPARTMENT' AND p.targetPincodes LIKE %:pincode% AND p.isResolved = :isResolved AND p.status = :status " +
           "ORDER BY p.createdAt DESC")
    List<Post> findTopUnresolvedIssueByDepartmentTagAndPincode(
            @Param("pincode") String pincode,
            @Param("isResolved") boolean isResolved,
            @Param("status") PostStatus status,
            Pageable pageable);

    List<Post> findByActorTokenAndStatusOrderByCreatedAtDesc(String actorToken, PostStatus status, Pageable pageable);
    List<Post> findByActorTokenAndStatusAndIdLessThanOrderByCreatedAtDesc(String actorToken, PostStatus status, Long id, Pageable pageable);
    List<Post> findByActorTokenAndStatusInOrderByCreatedAtDesc(String actorToken, java.util.Collection<PostStatus> statuses, Pageable pageable);
    List<Post> findByActorTokenAndStatusInAndIdLessThanOrderByCreatedAtDesc(String actorToken, java.util.Collection<PostStatus> statuses, Long id, Pageable pageable);

}
