package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.PostShare;
import com.JanSahayak.AI.model.PostShare.ShareType;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Repository
public interface PostShareRepo extends JpaRepository<PostShare, Long> {

    // =========================================================================
    // REGULAR POST QUERIES
    // =========================================================================

    /** Total share count for a regular Post (all platforms combined). */
    long countByPost(Post post);

    /** Per-platform share breakdown for a regular Post — returns [ShareType, count] rows. */
    @Query("SELECT ps.shareType, COUNT(ps) FROM PostShare ps " +
            "WHERE ps.post = :post GROUP BY ps.shareType ORDER BY COUNT(ps) DESC")
    List<Object[]> countByPostGroupByShareType(@Param("post") Post post);

    /** All share records for a regular Post by a specific user (for analytics). */
    @Query("SELECT ps FROM PostShare ps WHERE ps.post = :post AND ps.user.id = :userId")
    List<PostShare> findByPostAndUserId(@Param("post") Post post, @Param("userId") Long userId);

    /** Share count for a specific platform on a regular Post. */
    long countByPostAndShareType(Post post, ShareType shareType);

    /**
     * Bulk-delete all share records for a regular Post.
     * Call before a hard-delete (rare; soft-delete is the norm).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PostShare ps WHERE ps.post = :post")
    void deleteAllByPost(@Param("post") Post post);

    // =========================================================================
    // SOCIAL POST QUERIES
    // =========================================================================

    /** Total share count for a SocialPost (all platforms combined). */
    long countBySocialPost(SocialPost socialPost);

    /** Per-platform share breakdown for a SocialPost — returns [ShareType, count] rows. */
    @Query("SELECT ps.shareType, COUNT(ps) FROM PostShare ps " +
            "WHERE ps.socialPost = :socialPost GROUP BY ps.shareType ORDER BY COUNT(ps) DESC")
    List<Object[]> countBySocialPostGroupByShareType(@Param("socialPost") SocialPost socialPost);

    /** All share records for a SocialPost by a specific user (for analytics). */
    @Query("SELECT ps FROM PostShare ps WHERE ps.socialPost = :socialPost AND ps.user.id = :userId")
    List<PostShare> findBySocialPostAndUserId(@Param("socialPost") SocialPost socialPost, @Param("userId") Long userId);

    /** Share count for a specific platform on a SocialPost. */
    long countBySocialPostAndShareType(SocialPost socialPost, ShareType shareType);

    /**
     * Bulk-delete all share records for a SocialPost.
     * Call before a hard-delete (rare; soft-delete is the norm).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PostShare ps WHERE ps.socialPost = :socialPost")
    void deleteAllBySocialPost(@Param("socialPost") SocialPost socialPost);

    // =========================================================================
    // USER-LEVEL QUERIES
    // =========================================================================

    /**
     * Paginated share history for a user across both post types, newest first.
     * Useful for a "my activity" / analytics screen.
     */
    @Query("SELECT ps FROM PostShare ps WHERE ps.user.id = :userId ORDER BY ps.sharedAt DESC")
    Page<PostShare> findByUserIdOrderBySharedAtDesc(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(ps) FROM PostShare ps WHERE ps.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);


    @Modifying
    @Transactional
    @Query("DELETE FROM PostShare ps WHERE ps.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    // =========================================================================
    // ANALYTICS / ADMIN QUERIES
    // =========================================================================

    /**
     * Global per-platform share counts within a date range.
     * Returns [ShareType, count] rows — used for admin dashboard charts.
     */
    @Query("SELECT ps.shareType, COUNT(ps) FROM PostShare ps " +
            "WHERE ps.sharedAt BETWEEN :from AND :to GROUP BY ps.shareType ORDER BY COUNT(ps) DESC")
    List<Object[]> countByShareTypeBetween(@Param("from") Date from, @Param("to") Date to);

    /**
     * Total shares across ALL content within a date range — headline metric.
     */
    @Query("SELECT COUNT(ps) FROM PostShare ps WHERE ps.sharedAt BETWEEN :from AND :to")
    long countAllBetween(@Param("from") Date from, @Param("to") Date to);
}
