package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepo extends JpaRepository<Comment, Long> {

    Optional<Comment> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Comment c WHERE c.id = :id")
    Optional<Comment> findByIdForUpdate(@Param("id") Long id);

    // Count comments for a post
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.post = :post")
    Long countByPost(@Param("post") Post post);

    /**
     * Recursively counts all nested replies (descendants) under a given comment ID.
     * Works with H2, PostgreSQL, MySQL 8.0+, and SQL Server.
     */
    @Query(value = 
        "WITH RECURSIVE CommentHierarchy AS (" +
        "    SELECT id FROM comments WHERE parent_comment_id = :commentId " +
        "    UNION ALL " +
        "    SELECT c.id FROM comments c " +
        "    INNER JOIN CommentHierarchy ch ON c.parent_comment_id = ch.id" +
        ") SELECT COUNT(*) FROM CommentHierarchy", 
        nativeQuery = true)
    int countRecursiveReplies(@Param("commentId") Long commentId);

    // ── SocialPost comment queries ────────────────────────────────────────────

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.socialPost = :socialPost")
    Long countBySocialPost(@Param("socialPost") SocialPost socialPost);

    // ── NEW: "Most Recent" — cursor-based, sorted purely by createdAt DESC ──
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.socialPost.id = :socialPostId AND c.parentComment IS NULL ORDER BY c.createdAt DESC")
    List<Comment> findTopLevelCommentsBySocialPostId(
            @Param("socialPostId") Long socialPostId, Pageable pageable);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.socialPost.id = :socialPostId AND c.parentComment IS NULL AND c.id < :id ORDER BY c.createdAt DESC")
    List<Comment> findTopLevelCommentsBySocialPostIdAndIdLessThan(
            @Param("socialPostId") Long socialPostId,
            @Param("id") Long id,
            Pageable pageable);

    // ── NEW: "Top Rated" top-level — offset-based, sorted by likeCount DESC ──
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.socialPost.id = :socialPostId AND c.parentComment IS NULL ORDER BY c.likeCount DESC, c.createdAt DESC")
    List<Comment> findTopRatedTopLevelCommentsBySocialPostId(
            @Param("socialPostId") Long socialPostId, Pageable pageable);

    // ── All comments for SocialPost (Fixed for performance & pagination) ──

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.socialPost.id = :socialPostId ORDER BY c.createdAt DESC")
    List<Comment> findBySocialPostIdOrderByCreatedAtDesc(@Param("socialPostId") Long socialPostId, Pageable pageable);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.socialPost.id = :socialPostId AND c.id < :beforeId ORDER BY c.createdAt DESC")
    List<Comment> findBySocialPostIdAndIdLessThanOrderByCreatedAtDesc(@Param("socialPostId") Long socialPostId, @Param("beforeId") Long beforeId, Pageable pageable);

    // ── NEW: "Top Rated" all-comments SocialPost — offset-based ──
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.socialPost.id = :socialPostId ORDER BY c.likeCount DESC, c.createdAt DESC")
    List<Comment> findTopRatedBySocialPostId(@Param("socialPostId") Long socialPostId, Pageable pageable);


    // ── Top-level comment queries (with JOIN FETCH) ───────────────────────────

    // ── "Most Recent" top-level Post — cursor-based, sorted purely by createdAt DESC ──
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post AND c.parentComment IS NULL ORDER BY c.createdAt DESC")
    List<Comment> findTopLevelCommentsByPost(@Param("post") Post post);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post AND c.parentComment IS NULL ORDER BY c.createdAt DESC")
    List<Comment> findTopLevelCommentsByPost(@Param("post") Post post, Pageable pageable);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post AND c.parentComment IS NULL AND c.id < :beforeId ORDER BY c.createdAt DESC")
    List<Comment> findTopLevelCommentsByPostAndIdLessThan(@Param("post") Post post, @Param("beforeId") Long beforeId, Pageable pageable);

    // ── NEW: "Top Rated" top-level Post — offset-based, sorted by likeCount DESC ──
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post AND c.parentComment IS NULL ORDER BY c.likeCount DESC, c.createdAt DESC")
    List<Comment> findTopRatedTopLevelCommentsByPost(@Param("post") Post post, Pageable pageable);

    // ── Paginated post comment queries ────────────────────────────────────────

    /**
     * FIX: Added JOIN FETCH c.user to eliminate N lazy-load queries.
     * Sorted purely by createdAt DESC for correct cursor semantics ("Most Recent").
     */
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post ORDER BY c.createdAt DESC")
    List<Comment> findByPostOrderByCreatedAtDesc(@Param("post") Post post, Pageable pageable);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post AND c.id < :beforeId ORDER BY c.createdAt DESC")
    List<Comment> findByPostAndIdLessThanOrderByCreatedAtDesc(@Param("post") Post post, @Param("beforeId") Long beforeId, Pageable pageable);

    // ── NEW: "Top Rated" all-comments Post — offset-based, sorted by likeCount DESC ──
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post ORDER BY c.likeCount DESC, c.createdAt DESC")
    List<Comment> findTopRatedByPost(@Param("post") Post post, Pageable pageable);

    @Query("SELECT c FROM Comment c " +
            "JOIN FETCH c.post p " +
            "JOIN FETCH c.user u " +
            "WHERE c.user = :user " +
            "AND p.status IN (com.JanSahayak.AI.enums.PostStatus.ACTIVE, com.JanSahayak.AI.enums.PostStatus.RESOLVED) " +
            "AND p.user.isActive = true " +
            "ORDER BY c.rankingScore DESC, c.createdAt DESC")
    List<Comment> findByUserWithVisiblePostsOrderByCreatedAtDesc(@Param("user") User user, Pageable pageable);

    @Query("SELECT c FROM Comment c " +
            "JOIN FETCH c.post p " +
            "JOIN FETCH c.user u " +
            "WHERE c.user = :user " +
            "AND p.status IN (com.JanSahayak.AI.enums.PostStatus.ACTIVE, com.JanSahayak.AI.enums.PostStatus.RESOLVED) " +
            "AND p.user.isActive = true " +
            "AND c.id < :beforeId " +
            "ORDER BY c.rankingScore DESC, c.createdAt DESC")
    List<Comment> findByUserWithVisiblePostsAndIdLessThanOrderByCreatedAtDesc(
            @Param("user") User user,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    /**
     * FIX: Added JOIN FETCH c.user to eliminate N lazy-load queries when iterating replies.
     */
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.parentComment = :parentComment ORDER BY c.rankingScore DESC, c.createdAt DESC")
    List<Comment> findByParentCommentOrderByCreatedAtDesc(@Param("parentComment") Comment parentComment, Pageable pageable);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.parentComment = :parentComment AND c.id < :beforeId ORDER BY c.rankingScore DESC, c.createdAt DESC")
    List<Comment> findByParentCommentAndIdLessThanOrderByCreatedAtDesc(@Param("parentComment") Comment parentComment, @Param("beforeId") Long beforeId, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    List<Comment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c WHERE c.id < :beforeId ORDER BY c.rankingScore DESC, c.createdAt DESC")
    List<Comment> findByIdLessThanOrderByCreatedAtDesc(@Param("beforeId") Long beforeId, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    List<Comment> findByCreatedAtAfterOrderByCreatedAtDesc(Date fromDate, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c WHERE c.createdAt > :fromDate AND c.id < :beforeId ORDER BY c.rankingScore DESC, c.createdAt DESC")
    List<Comment> findByCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(@Param("fromDate") Date fromDate, @Param("beforeId") Long beforeId, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    List<Comment> findByTextContainingIgnoreCaseOrderByCreatedAtDesc(String searchTerm, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c WHERE LOWER(c.text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND c.id < :beforeId ORDER BY c.rankingScore DESC, c.createdAt DESC")
    List<Comment> findByTextContainingIgnoreCaseAndIdLessThanOrderByCreatedAtDesc(@Param("searchTerm") String searchTerm, @Param("beforeId") Long beforeId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "socialPost"})
    @Query("SELECT c FROM Comment c WHERE c.socialPost IS NOT NULL AND c.user.id = :userId AND (c.isDeleted IS NULL OR c.isDeleted = false) ORDER BY c.createdAt DESC")
    org.springframework.data.domain.Page<Comment> findBySocialPostNotNullAndUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT c FROM Comment c WHERE c.post IS NOT NULL AND c.user.id = :userId AND (c.isDeleted IS NULL OR c.isDeleted = false) ORDER BY c.createdAt DESC")
    org.springframework.data.domain.Page<Comment> findByPostNotNullAndUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);
}
