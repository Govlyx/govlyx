package com.Govlyx.AI.controller;

import com.Govlyx.AI.dto.*;
import com.Govlyx.AI.exception.ApiResponse;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.security.CurrentUser;
import com.Govlyx.AI.service.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                     CommentController  /api/comments                     ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  Owns ALL comment operations for both Post and SocialPost.               ║
 * ║  Delegates 100% of business logic to CommentService.                     ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  PERF FIX (read endpoints — detached entity risk + extra SELECT):        ║
 * ║                                                                          ║
 * ║  The original read endpoints (getCommentsByPost, getTopLevelComments,    ║
 * ║  countComments) loaded the Post/SocialPost entity in the controller      ║
 * ║  layer via postService.findById() / socialPostService.findById(), then   ║
 * ║  passed the detached entity to CommentService. This caused:              ║
 * ║    1. An extra SELECT (controller's implicit txn closes immediately,     ║
 * ║       entity detaches, service has to re-attach it internally).          ║
 * ║    2. Potential LazyInitializationException if any lazy collection on    ║
 * ║       the entity was accessed after detachment.                          ║
 * ║                                                                          ║
 * ║  Fix: All read endpoints now pass only the ID to new *ById service       ║
 * ║  variants (getCommentsByPostId, getTopLevelCommentsByPostId, etc.).      ║
 * ║  The service loads the entity INSIDE its own @Transactional boundary,   ║
 * ║  keeping it managed for the full operation — same pattern already used   ║
 * ║  for the write (create comment) endpoints.                               ║
 * ║                                                                          ║
 * ║  PostService and SocialPostService are no longer injected here.          ║
 * ╠═══════════════════════╦══════════╦═══════════════════════════════════════╣
 * ║ Group                 ║ Method   ║ Path                                  ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ CREATE                ║ POST     ║ /post/{postId}                        ║
 * ║                       ║ POST     ║ /social-posts/{postId}                ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ READ — all comments   ║ GET      ║ /post/{postId}                        ║
 * ║                       ║ GET      ║ /social-posts/{postId}                ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ READ — top-level only ║ GET      ║ /post/{postId}/top-level              ║
 * ║                       ║ GET      ║ /social-posts/{postId}/top-level      ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ READ — replies        ║ GET      ║ /{commentId}/replies                  ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ READ — counts         ║ GET      ║ /post/{postId}/count                  ║
 * ║                       ║ GET      ║ /social-posts/{postId}/count          ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ READ — current user   ║ GET      ║ /my                                   ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ UPDATE                ║ PUT      ║ /{commentId}                          ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ DELETE                ║ DELETE   ║ /{commentId}                          ║
 * ╚═══════════════════════╩══════════╩═══════════════════════════════════════╝
 */
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    // FIX: PostService and SocialPostService removed — no entity loading in controller.
    // All methods delegate by ID only; the service loads entities inside @Transactional.
    private final CommentService commentService;

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * Create a comment (or reply) on a regular Post.
     *
     * POST /api/comments/post/{postId}
     *
     * Passes only postId — CommentService.createCommentOnPostById() loads the
     * Post inside its own @Transactional, keeping the entity managed for the
     * full INSERT + commentCount increment + promotion check.
     */
    @PostMapping("/post/{postId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CommentDto>> createCommentOnPost(
            @PathVariable @NotNull Long postId,
            @RequestBody @Valid CommentCreateDto commentDto,
            @CurrentUser User currentUser) {

        try {
            log.info("[Comment] CREATE on post={} user={}", postId, currentUser.getActualUsername());
            CommentDto created = commentService.createCommentOnPostById(postId, commentDto, currentUser);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Comment created successfully", created));

        } catch (Exception e) {
            log.error("[Comment] CREATE failed on post={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to create comment", e.getMessage()));
        }
    }

    /**
     * Create a comment (or reply) on a SocialPost.
     *
     * POST /api/comments/social-posts/{postId}
     *
     * Passes only postId — CommentService.createCommentOnSocialPostById() loads
     * the SocialPost inside its own @Transactional boundary.
     */
    @PostMapping("/social-posts/{postId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CommentDto>> createCommentOnSocialPost(
            @PathVariable @NotNull Long postId,
            @RequestBody @Valid CommentCreateDto commentDto,
            @CurrentUser User currentUser) {

        try {
            log.info("[Comment] CREATE on socialPost={} user={}", postId, currentUser.getActualUsername());
            CommentDto created = commentService.createCommentOnSocialPostById(postId, commentDto, currentUser);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Comment created successfully", created));

        } catch (Exception e) {
            log.error("[Comment] CREATE failed on socialPost={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to create comment", e.getMessage()));
        }
    }

    // =========================================================================
    // READ — ALL COMMENTS (cursor-paginated)
    // =========================================================================

    /**
     * All comments for a regular Post, newest first.
     *
     * GET /api/comments/post/{postId}?beforeId=N&limit=20
     *
     * FIX: was → Post post = postService.findById(postId); (extra SELECT + detached entity)
     *      now → commentService.getCommentsByPostId(postId, ...) loads Post inside @Transactional
     */
    @GetMapping("/post/{postId}")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getCommentsByPost(
            @PathVariable @NotNull Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "NEW", required = false) String sort,
            @CurrentUser(required = false) User currentUser) {

        try {
            log.debug("[Comment] LIST all for post={} sort={}", postId, sort);
            PaginatedResponse<CommentDto> comments = commentService.getCommentsByPostId(postId, beforeId, limit, sort, currentUser);
            return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));

        } catch (com.Govlyx.AI.exception.ResourceNotFoundException e) {
            log.warn("[Comment] Post not found: id={}", postId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (Exception e) {
            log.error("[Comment] LIST failed for post={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve comments", e.getMessage()));
        }
    }

    /**
     * All comments for a SocialPost, newest first.
     *
     * GET /api/comments/social-posts/{postId}?beforeId=N&limit=20
     *
     * FIX: was → SocialPost sp = socialPostService.findById(postId); (extra SELECT + detached)
     *      now → commentService.getCommentsBySocialPostId(postId, ...) loads inside @Transactional
     */
    @GetMapping("/social-posts/{postId}")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getCommentsBySocialPost(
            @PathVariable @NotNull Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "NEW", required = false) String sort,
            @CurrentUser(required = false) User currentUser) {

        try {
            log.debug("[Comment] LIST all for socialPost={} sort={}", postId, sort);
            PaginatedResponse<CommentDto> comments = commentService.getCommentsBySocialPostId(postId, beforeId, limit, sort, currentUser);
            return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));

        } catch (com.Govlyx.AI.exception.ResourceNotFoundException e) {
            log.warn("[Comment] SocialPost not found: id={}", postId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("SocialPost not found", e.getMessage()));
        } catch (Exception e) {
            log.error("[Comment] LIST failed for socialPost={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve comments", e.getMessage()));
        }
    }

    // =========================================================================
    // READ — TOP-LEVEL ONLY (no replies, for threaded UI first load)
    // =========================================================================

    /**
     * Top-level comments only (no replies) for a regular Post.
     *
     * GET /api/comments/post/{postId}/top-level?beforeId=N&limit=20
     *
     * FIX: was loading Post in controller; now delegates ID-only to service.
     */
    @GetMapping("/post/{postId}/top-level")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getTopLevelCommentsByPost(
            @PathVariable @NotNull Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "NEW", required = false) String sort,
            @CurrentUser(required = false) User currentUser) {

        try {
            log.debug("[Comment] LIST top-level for post={} sort={}", postId, sort);
            PaginatedResponse<CommentDto> comments = commentService.getTopLevelCommentsByPostId(postId, beforeId, limit, sort, currentUser);
            return ResponseEntity.ok(ApiResponse.success("Top-level comments retrieved", comments));

        } catch (com.Govlyx.AI.exception.ResourceNotFoundException e) {
            log.warn("[Comment] Post not found for top-level comments: id={}", postId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (Exception e) {
            log.error("[Comment] LIST top-level failed for post={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve comments", e.getMessage()));
        }
    }

    /**
     * Top-level comments only for a SocialPost.
     *
     * GET /api/comments/social-posts/{postId}/top-level?beforeId=N&limit=20
     *
     * FIX: was loading SocialPost in controller; now delegates ID-only to service.
     */
    @GetMapping("/social-posts/{postId}/top-level")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getTopLevelCommentsBySocialPost(
            @PathVariable @NotNull Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "NEW", required = false) String sort,
            @CurrentUser(required = false) User currentUser) {

        try {
            log.debug("[Comment] LIST top-level for socialPost={} sort={}", postId, sort);
            PaginatedResponse<CommentDto> comments = commentService.getTopLevelCommentsBySocialPostId(postId, beforeId, limit, sort, currentUser);
            return ResponseEntity.ok(ApiResponse.success("Top-level comments retrieved", comments));

        } catch (com.Govlyx.AI.exception.ResourceNotFoundException e) {
            log.warn("[Comment] SocialPost not found for top-level comments: id={}", postId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("SocialPost not found", e.getMessage()));
        } catch (Exception e) {
            log.error("[Comment] LIST top-level failed for socialPost={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve comments", e.getMessage()));
        }
    }

    // =========================================================================
    // READ — REPLIES
    // =========================================================================

    /**
     * Paginated replies for a specific comment (works for both post types).
     *
     * GET /api/comments/{commentId}/replies?beforeId=N&limit=20
     *
     * No change needed here — replies are keyed on commentId, no Post entity load.
     */
    @GetMapping("/{commentId}/replies")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getCommentReplies(
            @PathVariable @NotNull Long commentId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser(required = false) User currentUser) {

        try {
            log.debug("[Comment] LIST replies for comment={}", commentId);
            PaginatedResponse<CommentDto> replies = commentService.getCommentReplies(commentId, beforeId, limit, currentUser);
            return ResponseEntity.ok(ApiResponse.success("Replies retrieved", replies));

        } catch (Exception e) {
            log.error("[Comment] LIST replies failed for comment={}", commentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve replies", e.getMessage()));
        }
    }

    // =========================================================================
    // READ — COUNT
    // =========================================================================

    /**
     * Total comment count for a regular Post.
     *
     * GET /api/comments/post/{postId}/count
     *
     * FIX: was loading Post in controller; now delegates ID-only to service.
     */
    @GetMapping("/post/{postId}/count")
    public ResponseEntity<ApiResponse<Long>> getCommentCountByPost(
            @PathVariable @NotNull Long postId) {

        try {
            Long count = commentService.countCommentsByPostId(postId);
            return ResponseEntity.ok(ApiResponse.success("Comment count retrieved", count));

        } catch (com.Govlyx.AI.exception.ResourceNotFoundException e) {
            log.warn("[Comment] Post not found for count: id={}", postId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (Exception e) {
            log.error("[Comment] COUNT failed for post={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get comment count", e.getMessage()));
        }
    }

    /**
     * Total comment count for a SocialPost.
     *
     * GET /api/comments/social-posts/{postId}/count
     *
     * FIX: was loading SocialPost in controller; now delegates ID-only to service.
     */
    @GetMapping("/social-posts/{postId}/count")
    public ResponseEntity<ApiResponse<Long>> getCommentCountBySocialPost(
            @PathVariable @NotNull Long postId,
            @CurrentUser(required = false) User currentUser) {

        try {
            Long count = commentService.countCommentsBySocialPostId(postId, currentUser);
            return ResponseEntity.ok(ApiResponse.success("Comment count retrieved", count));

        } catch (com.Govlyx.AI.exception.ResourceNotFoundException e) {
            log.warn("[Comment] SocialPost not found for count: id={}", postId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("SocialPost not found", e.getMessage()));
        } catch (Exception e) {
            log.error("[Comment] COUNT failed for socialPost={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get comment count", e.getMessage()));
        }
    }

    // =========================================================================
    // READ — CURRENT USER'S COMMENT HISTORY
    // =========================================================================

    /**
     * All comments made by the authenticated user, newest first.
     *
     * GET /api/comments/my?beforeId=N&limit=20
     *
     * No change — this was already correct (no Post entity loaded in controller).
     */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getMyComments(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {

        try {
            log.debug("[Comment] LIST my comments for user={}", currentUser.getActualUsername());
            PaginatedResponse<CommentDto> comments = commentService.getCommentsByUser(currentUser, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));

        } catch (Exception e) {
            log.error("[Comment] LIST my comments failed for user={}", currentUser.getActualUsername(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve your comments", e.getMessage()));
        }
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * Edit a comment (owner only — enforced in CommentService).
     *
     * PUT /api/comments/{commentId}
     *
     * Works for comments on both Post and SocialPost.
     * No change — this was already correct.
     */
    @PutMapping("/{commentId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CommentDto>> updateComment(
            @PathVariable @NotNull Long commentId,
            @RequestBody @Valid CommentUpdateDto commentDto,
            @CurrentUser User currentUser) {

        try {
            log.info("[Comment] UPDATE commentId={} user={}", commentId, currentUser.getActualUsername());
            CommentDto updated = commentService.updateComment(commentId, commentDto, currentUser);
            return ResponseEntity.ok(ApiResponse.success("Comment updated successfully", updated));

        } catch (Exception e) {
            log.error("[Comment] UPDATE failed for commentId={}", commentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to update comment", e.getMessage()));
        }
    }

    /**
     * Pin or unpin a comment (owner of post or ADMIN).
     *
     * PUT /api/comments/{commentId}/pin
     */
    @PutMapping("/{commentId}/pin")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CommentDto>> pinComment(
            @PathVariable @NotNull Long commentId,
            @CurrentUser User currentUser) {

        try {
            log.info("[Comment] PIN TOGGLE commentId={} user={}", commentId, currentUser.getActualUsername());
            CommentDto updated = commentService.pinComment(commentId, currentUser);
            return ResponseEntity.ok(ApiResponse.success("Comment pinning toggled successfully", updated));

        } catch (Exception e) {
            log.error("[Comment] PIN TOGGLE failed for commentId={}", commentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to pin comment", e.getMessage()));
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    /**
     * Delete a comment (owner or ADMIN — enforced in CommentService).
     *
     * DELETE /api/comments/{commentId}
     *
     * Works for comments on both Post and SocialPost.
     * No change — this was already correct.
     */
    @DeleteMapping("/{commentId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable @NotNull Long commentId,
            @CurrentUser User currentUser) {

        try {
            log.info("[Comment] DELETE commentId={} user={}", commentId, currentUser.getActualUsername());
            commentService.deleteComment(commentId, currentUser);
            return ResponseEntity.status(HttpStatus.NO_CONTENT)
                    .body(ApiResponse.success("Comment deleted successfully", null));

        } catch (Exception e) {
            log.error("[Comment] DELETE failed for commentId={}", commentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to delete comment", e.getMessage()));
        }
    }
}
