package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SavedPost;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedPostRepo extends JpaRepository<SavedPost, Long> {

    // =========================================================================
    // SINGLE-RECORD LOOKUPS
    // =========================================================================

    @Query("SELECT sp FROM SavedPost sp WHERE sp.user.id = :userId AND sp.socialPost = :socialPost")
    Optional<SavedPost> findByUserIdAndSocialPost(@Param("userId") Long userId, @Param("socialPost") SocialPost socialPost);

    @Query("SELECT sp FROM SavedPost sp WHERE sp.user.id = :userId AND sp.post = :post")
    Optional<SavedPost> findByUserIdAndPost(@Param("userId") Long userId, @Param("post") Post post);

    // =========================================================================
    // EXISTENCE CHECKS
    // =========================================================================

    @Query("SELECT COUNT(sp) > 0 FROM SavedPost sp WHERE sp.user.id = :userId AND sp.socialPost = :socialPost")
    boolean existsByUserIdAndSocialPost(@Param("userId") Long userId, @Param("socialPost") SocialPost socialPost);

    boolean existsByUser_IdAndSocialPost_Id(Long userId, Long socialPostId);

    @Query("SELECT COUNT(sp) > 0 FROM SavedPost sp WHERE sp.user.id = :userId AND sp.post = :post")
    boolean existsByUserIdAndPost(@Param("userId") Long userId, @Param("post") Post post);

    // =========================================================================
    // PAGINATED LISTING
    // =========================================================================

    @EntityGraph(attributePaths = {"socialPost", "post"})
    @Query("SELECT sp FROM SavedPost sp WHERE sp.user.id = :userId ORDER BY sp.savedAt DESC")
    Page<SavedPost> findByUserIdOrderBySavedAtDesc(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"socialPost", "post"})
    @Query("SELECT sp FROM SavedPost sp WHERE sp.user.id = :userId AND sp.id < :id ORDER BY sp.savedAt DESC")
    Page<SavedPost> findByUserIdAndIdLessThanOrderBySavedAtDesc(@Param("userId") Long userId, @Param("id") Long id, Pageable pageable);

    @Query("SELECT sp FROM SavedPost sp WHERE sp.user.id = :userId ORDER BY sp.savedAt DESC")
    List<SavedPost> findByUserIdOrderBySavedAtDesc(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"socialPost", "post"})
    @Query("SELECT sp FROM SavedPost sp WHERE sp.user.id = :userId AND sp.socialPost IS NOT NULL ORDER BY sp.savedAt DESC")
    Page<SavedPost> findSocialPostSavesByUserIdOrderBySavedAtDesc(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"socialPost", "post"})
    @Query("SELECT sp FROM SavedPost sp WHERE sp.user.id = :userId AND sp.post IS NOT NULL ORDER BY sp.savedAt DESC")
    Page<SavedPost> findBroadcastPostSavesByUserIdOrderBySavedAtDesc(@Param("userId") Long userId, Pageable pageable);

    // =========================================================================
    // COUNT QUERIES
    // =========================================================================

    @Query("SELECT COUNT(sp) FROM SavedPost sp WHERE sp.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    long countBySocialPost(SocialPost socialPost);

    long countByPost(Post post);

    // =========================================================================
    // BULK DELETE
    // =========================================================================

    @Modifying
    @Transactional
    void deleteAllBySocialPost(SocialPost socialPost);

    @Modifying
    @Transactional
    void deleteAllByPost(Post post);

    @Modifying
    @Transactional
    @Query("DELETE FROM SavedPost sp WHERE sp.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    // =========================================================================
    // UTILITY — admin / reconciliation
    // =========================================================================

    @Query("SELECT sp FROM SavedPost sp WHERE sp.socialPost = :socialPost")
    List<SavedPost> findAllBySocialPost(@Param("socialPost") SocialPost socialPost);

    @Query("SELECT sp FROM SavedPost sp WHERE sp.post = :post")
    List<SavedPost> findAllByPost(@Param("post") Post post);

    // =========================================================================
    // BATCH INTERACTION QUERIES — eliminates N+1 in feed
    // =========================================================================

    /**
     * Returns IDs of social posts (from given list) that the user has SAVED.
     * Used by PostInteractionService.getBatchSavedSocialPostIds()
     */
    @Query("SELECT s.socialPost.id FROM SavedPost s " +
            "WHERE s.user.id = :userId " +
            "AND s.socialPost.id IN :postIds")
    List<Long> findSavedSocialPostIdsByUser(
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds);

    /**
     * Returns IDs of regular posts (from given list) that the user has SAVED.
     */
    @Query("SELECT s.post.id FROM SavedPost s " +
            "WHERE s.user.id = :userId " +
            "AND s.post.id IN :postIds")
    List<Long> findSavedPostIdsByUser(
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds);
}
