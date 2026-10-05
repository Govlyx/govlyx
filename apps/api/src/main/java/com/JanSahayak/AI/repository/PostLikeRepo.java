package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.PostLike;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostLikeRepo extends JpaRepository<PostLike, Long> {

    // =========================================================================
    // SINGLE-RECORD LOOKUPS
    // =========================================================================

    @Query("SELECT pl FROM PostLike pl WHERE pl.post = :post AND pl.user.id = :userId")
    Optional<PostLike> findByPostAndUserId(@Param("post") Post post, @Param("userId") Long userId);

    @Query("SELECT pl FROM PostLike pl WHERE pl.socialPost = :socialPost AND pl.user.id = :userId")
    Optional<PostLike> findBySocialPostAndUserId(@Param("socialPost") SocialPost socialPost, @Param("userId") Long userId);

    @Query("SELECT pl FROM PostLike pl WHERE pl.post = :post AND pl.actorToken = :actorToken")
    Optional<PostLike> findByPostAndActorToken(@Param("post") Post post, @Param("actorToken") String actorToken);

    @Query("SELECT pl FROM PostLike pl WHERE pl.socialPost = :socialPost AND pl.actorToken = :actorToken")
    Optional<PostLike> findBySocialPostAndActorToken(@Param("socialPost") SocialPost socialPost, @Param("actorToken") String actorToken);

    @Query("SELECT COUNT(pl) > 0 FROM PostLike pl WHERE pl.post = :post AND pl.user.id = :userId")
    boolean existsByPostAndUserId(@Param("post") Post post, @Param("userId") Long userId);

    @Query("SELECT COUNT(pl) > 0 FROM PostLike pl WHERE pl.post = :post AND pl.actorToken = :actorToken")
    boolean existsByPostAndActorToken(@Param("post") Post post, @Param("actorToken") String actorToken);

    @Query("SELECT COUNT(pl) > 0 FROM PostLike pl WHERE pl.socialPost = :socialPost AND pl.actorToken = :actorToken")
    boolean existsBySocialPostAndActorToken(@Param("socialPost") SocialPost socialPost, @Param("actorToken") String actorToken);

    List<PostLike> findByActorToken(String actorToken, Pageable pageable);


    // =========================================================================
    // COUNT QUERIES
    // =========================================================================

    @Query("SELECT COUNT(pl) FROM PostLike pl WHERE pl.post = :post")
    Long countByPost(@Param("post") Post post);

    Long countBySocialPost(SocialPost socialPost);

    Long countByPostAndCreatedAtAfter(Post post, Date date);

    // =========================================================================
    // LIST QUERIES
    // =========================================================================

    List<PostLike> findBySocialPost(SocialPost socialPost);

    List<PostLike> findByPostOrderByCreatedAtDesc(Post post, Pageable pageable);

    @Query("SELECT pl.user FROM PostLike pl WHERE pl.post = :post ORDER BY pl.id DESC")
    List<User> findUsersWhoLikedPost(@Param("post") Post post, Pageable pageable);

    @Query("SELECT pl.user FROM PostLike pl WHERE pl.post = :post AND pl.id < :beforeId ORDER BY pl.id DESC")
    List<User> findUsersWhoLikedPostWithCursor(@Param("post") Post post,
                                               @Param("beforeId") Long beforeId,
                                               Pageable pageable);

    // =========================================================================
    // BATCH INTERACTION QUERIES — eliminates N+1 in feed
    // =========================================================================

    /**
     * Returns IDs of social posts (from given list) that the user has LIKED.
     * Used by PostInteractionService.getBatchLikedSocialPostIds()
     */
    @Query("SELECT p.socialPost.id FROM PostLike p " +
            "WHERE p.user.id = :userId " +
            "AND p.socialPost.id IN :postIds " +
            "AND p.reactionType = :reactionType")
    List<Long> findLikedSocialPostIdsByUser(
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds,
            @Param("reactionType") PostLike.ReactionType reactionType);

    @Query("SELECT p.socialPost.id FROM PostLike p " +
            "WHERE (p.actorToken = :actorToken OR (:userId IS NOT NULL AND p.user.id = :userId)) " +
            "AND p.socialPost.id IN :postIds " +
            "AND p.reactionType = :reactionType")
    List<Long> findLikedSocialPostIdsByActorOrUser(
            @Param("actorToken") String actorToken,
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds,
            @Param("reactionType") PostLike.ReactionType reactionType);

    /**
     * Returns IDs of social posts (from given list) that the user has DISLIKED.
     * Used by PostInteractionService.getBatchDislikedSocialPostIds()
     */
    @Query("SELECT p.socialPost.id FROM PostLike p " +
            "WHERE p.user.id = :userId " +
            "AND p.socialPost.id IN :postIds " +
            "AND p.reactionType = :reactionType")
    List<Long> findDislikedSocialPostIdsByUser(
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds,
            @Param("reactionType") PostLike.ReactionType reactionType);

    @Query("SELECT p.socialPost.id FROM PostLike p " +
            "WHERE (p.actorToken = :actorToken OR (:userId IS NOT NULL AND p.user.id = :userId)) " +
            "AND p.socialPost.id IN :postIds " +
            "AND p.reactionType = :reactionType")
    List<Long> findDislikedSocialPostIdsByActorOrUser(
            @Param("actorToken") String actorToken,
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds,
            @Param("reactionType") PostLike.ReactionType reactionType);

    @Query("SELECT p.post.id FROM PostLike p " +
            "WHERE (p.actorToken = :actorToken OR (:userId IS NOT NULL AND p.user.id = :userId)) " +
            "AND p.post.id IN :postIds " +
            "AND p.reactionType = :reactionType")
    List<Long> findLikedPostIdsByActorOrUser(
            @Param("actorToken") String actorToken,
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds,
            @Param("reactionType") PostLike.ReactionType reactionType);

    @Query("SELECT p.post.id FROM PostLike p " +
            "WHERE (p.actorToken = :actorToken OR (:userId IS NOT NULL AND p.user.id = :userId)) " +
            "AND p.post.id IN :postIds " +
            "AND p.reactionType = :reactionType")
    List<Long> findDislikedPostIdsByActorOrUser(
            @Param("actorToken") String actorToken,
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds,
            @Param("reactionType") PostLike.ReactionType reactionType);

    // =========================================================================
    // USER ACTIVITY LOOKUPS
    // =========================================================================

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "socialPost"})
    @Query("SELECT pl FROM PostLike pl WHERE pl.socialPost IS NOT NULL AND (pl.user.id = :userId OR (pl.actorToken IS NOT NULL AND pl.actorToken = :actorToken)) ORDER BY pl.createdAt DESC")
    org.springframework.data.domain.Page<PostLike> findBySocialPostNotNullAndUserIdOrActorTokenOrderByCreatedAtDesc(@Param("userId") Long userId, @Param("actorToken") String actorToken, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT pl FROM PostLike pl WHERE pl.post IS NOT NULL AND (pl.user.id = :userId OR (pl.actorToken IS NOT NULL AND pl.actorToken = :actorToken)) ORDER BY pl.createdAt DESC")
    org.springframework.data.domain.Page<PostLike> findByPostNotNullAndUserIdOrActorTokenOrderByCreatedAtDesc(@Param("userId") Long userId, @Param("actorToken") String actorToken, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "socialPost"})
    @Query("SELECT pl FROM PostLike pl WHERE pl.socialPost IS NOT NULL AND pl.user.id = :userId ORDER BY pl.createdAt DESC")
    org.springframework.data.domain.Page<PostLike> findBySocialPostNotNullAndUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT pl FROM PostLike pl WHERE pl.post IS NOT NULL AND pl.user.id = :userId ORDER BY pl.createdAt DESC")
    org.springframework.data.domain.Page<PostLike> findByPostNotNullAndUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);
}
