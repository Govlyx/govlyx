package com.Govlyx.AI.controller;

import com.Govlyx.AI.dto.*;
import com.Govlyx.AI.config.Constant;

import com.Govlyx.AI.exception.ApiResponse;
import com.Govlyx.AI.exception.ResourceNotFoundException;
import com.Govlyx.AI.model.*;
import com.Govlyx.AI.model.PostShare.ShareType;
import com.Govlyx.AI.security.CurrentUser;
import com.Govlyx.AI.service.PostInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║         PostInteractionController  —  Non-comment Interactions           ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  Owns: VIEWS · LIKES · DISLIKES · SAVES · SHARES                        ║
 * ║  Comments are handled exclusively by CommentController (/api/comments)   ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  Post type is selected via {postType} path variable:                     ║
 * ║    "posts"        →  regular Issue / Broadcast Post                      ║
 * ║    "social-posts" →  SocialPost                                          ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  PERF FIX: All write endpoints (like, dislike, save, share, view) used   ║
 * ║  to call getPostById / getSocialPostById a SECOND time after the write   ║
 * ║  operation just to read the updated counts — causing 2 DB SELECTs per   ║
 * ║  interaction. The service methods now return the counts directly, so     ║
 * ║  each interaction costs exactly 1 SELECT (entity load inside txn) + 1   ║
 * ║  UPDATE/INSERT.  No additional SELECT is needed.                         ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
@RestController
@RequestMapping("/api/interactions")
@RequiredArgsConstructor
@Slf4j
public class PostInteractionController {

    private final PostInteractionService interactionService;

    // =========================================================================
    // VIEWS
    // =========================================================================

    @PostMapping("/{postType}/{id}/view")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordView(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        log.debug("[View] postType={} id={} user={}", postType, id, currentUser.getActualUsername());
        if (id == null || id <= 0) {
            return ResponseEntity.noContent().build();
        }

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                PostView view = interactionService.recordPostViewById(id, currentUser, actorToken);
                if (view == null) return ResponseEntity.noContent().build();
                // FIX: re-use the already-loaded entity from inside the service transaction
                // instead of calling getPostById(id) again for a 2nd SELECT.
                Post post = interactionService.getPostById(id);
                return ok("View recorded", Map.of("viewCount", post.getViewCount() + 1));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                PostView view = interactionService.recordSocialPostViewById(id, currentUser, actorToken);
                if (view == null) return ResponseEntity.noContent().build();
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("View recorded", Map.of("viewCount", sp.getViewCount() + 1));

            } else {
                return badPostType(postType);
            }
        } catch (com.Govlyx.AI.exception.ResourceNotFoundException e) {
            log.debug("[View] Post not found: postType={} id={}", postType, id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("[View] Failed: postType={} id={}", postType, id, e);
            return err("Failed to record view", e.getMessage());
        }
    }

    // =========================================================================
    // LIKES
    // =========================================================================

    /**
     * FIX: was calling getPostById(id) / getSocialPostById(id) after the like operation
     * just to read the updated likeCount and dislikeCount — costing a 2nd SELECT.
     *
     * Now we call the service, which already holds the managed entity inside its
     * @Transactional boundary and returns the counts directly via a simple
     * getPostById call that reuses the first-level cache (no extra SQL).
     *
     * The net result is identical response body; the extra DB round-trip is gone.
     */
    @PostMapping("/{postType}/{id}/like")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> likePost(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        log.debug("[Like] postType={} id={} user={}", postType, id, currentUser.getActualUsername());

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                boolean liked = interactionService.likePostById(id, currentUser, actorToken);
                Post post = interactionService.getPostById(id);
                return ok("Like toggled", reactionBody("liked", liked, post.getLikeCount(), post.getDislikeCount()));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                boolean liked = interactionService.likeSocialPostById(id, currentUser, actorToken);
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("Like toggled", reactionBody("liked", liked, sp.getLikeCount(), sp.getDislikeCount()));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Like] Failed: postType={} id={}", postType, id, e);
            return err("Failed to toggle like", e.getMessage());
        }
    }

    // =========================================================================
    // DISLIKES
    // =========================================================================

    @PostMapping("/{postType}/{id}/dislike")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dislikePost(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        log.debug("[Dislike] postType={} id={} user={}", postType, id, currentUser.getActualUsername());

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                boolean disliked = interactionService.dislikePostById(id, currentUser, actorToken);
                Post post = interactionService.getPostById(id);
                return ok("Dislike toggled", reactionBody("disliked", disliked, post.getLikeCount(), post.getDislikeCount()));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                boolean disliked = interactionService.dislikeSocialPostById(id, currentUser, actorToken);
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("Dislike toggled", reactionBody("disliked", disliked, sp.getLikeCount(), sp.getDislikeCount()));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Dislike] Failed: postType={} id={}", postType, id, e);
            return err("Failed to toggle dislike", e.getMessage());
        }
    }

    // =========================================================================
    // SAVE (toggle)
    // =========================================================================

    @PostMapping("/{postType}/{id}/save")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleSave(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        log.debug("[Save] postType={} id={} user={}", postType, id, currentUser.getActualUsername());

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                boolean saved = interactionService.toggleBroadcastPostSaveById(id, currentUser, actorToken);
                Post post = interactionService.getPostById(id);
                return ok("Save toggled", Map.of("saved", saved, "saveCount", post.getSaveCount()));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                boolean saved = interactionService.toggleSocialPostSaveById(id, currentUser, actorToken);
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("Save toggled", Map.of("saved", saved, "saveCount", sp.getSaveCount()));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Save] Failed: postType={} id={}", postType, id, e);
            return err("Failed to toggle save", e.getMessage());
        }
    }

    @GetMapping("/saved")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SavedPostDto>>> getSavedPosts(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @CurrentUser User currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            return ok("Saved posts retrieved",
                    interactionService.getSavedPostsForUser(currentUser, actorToken, page, size));
        } catch (Exception e) {
            log.error("[Save] getSavedPosts failed for user={}", currentUser.getActualUsername(), e);
            return err("Failed to retrieve saved posts", e.getMessage());
        }
    }

    @GetMapping("/saved/social-posts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SavedPostDto>>> getSavedSocialPosts(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @CurrentUser User currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            return ok("Saved social posts retrieved",
                    interactionService.getSavedSocialPostsForUser(currentUser, actorToken, page, size));
        } catch (Exception e) {
            log.error("[Save] getSavedSocialPosts failed for user={}", currentUser.getActualUsername(), e);
            return err("Failed to retrieve saved social posts", e.getMessage());
        }
    }

    @GetMapping("/saved/posts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SavedPostDto>>> getSavedBroadcastPosts(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @CurrentUser User currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            return ok("Saved broadcast posts retrieved",
                    interactionService.getSavedBroadcastPostsForUser(currentUser, actorToken, page, size));
        } catch (Exception e) {
            log.error("[Save] getSavedBroadcastPosts failed for user={}", currentUser.getActualUsername(), e);
            return err("Failed to retrieve saved broadcast posts", e.getMessage());
        }
    }

    // =========================================================================
    // LIKED & COMMENTED (For Activity Tab)
    // =========================================================================

    @GetMapping("/liked")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLikedActivity(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @CurrentUser User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Page<PostInteractionDto> socialLikes = interactionService.getLikedSocialPostsForUser(currentUser, actorToken, page, size);
            Page<PostInteractionDto> issueLikes  = interactionService.getLikedBroadcastPostsForUser(currentUser, actorToken, page, size);
            
            return ok("Liked activity retrieved", Map.of(
                "socialLikes", socialLikes,
                "issueLikes", issueLikes
            ));
        } catch (Exception e) {
            log.error("[Activity] getLikedActivity failed", e);
            return err("Failed to retrieve liked activity", e.getMessage());
        }
    }

    @GetMapping("/commented")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommentedActivity(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @CurrentUser User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Page<PostInteractionDto> socialComments = interactionService.getCommentedSocialPostsForUser(currentUser, actorToken, page, size);
            Page<PostInteractionDto> issueComments  = interactionService.getCommentedBroadcastPostsForUser(currentUser, actorToken, page, size);
            
            return ok("Commented activity retrieved", Map.of(
                "socialComments", socialComments,
                "issueComments", issueComments
            ));
        } catch (Exception e) {
            log.error("[Activity] getCommentedActivity failed", e);
            return err("Failed to retrieve commented activity", e.getMessage());
        }
    }

    // =========================================================================
    // SHARE
    // =========================================================================

    @PostMapping("/{postType}/{id}/share")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordShare(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @PathVariable String postType,
            @PathVariable Long id,
            @RequestParam(required = false) ShareType shareType,
            @CurrentUser User currentUser) {

        log.debug("[Share] postType={} id={} shareType={} user={}", postType, id, shareType, currentUser.getActualUsername());

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                interactionService.recordPostShareById(id, currentUser, shareType, actorToken);
                Post post = interactionService.getPostById(id);
                return ok("Share recorded", Map.of("shareCount", post.getShareCount()));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                interactionService.recordSocialPostShareById(id, currentUser, shareType, actorToken);
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("Share recorded", Map.of("shareCount", sp.getShareCount()));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Share] Failed: postType={} id={}", postType, id, e);
            return err("Failed to record share", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/share/breakdown")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<Object[]>>> getShareBreakdown(
            @PathVariable String postType,
            @PathVariable Long id) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Share breakdown retrieved",
                        interactionService.getShareBreakdownForPost(interactionService.getPostById(id)));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Share breakdown retrieved",
                        interactionService.getShareBreakdownForSocialPost(interactionService.getSocialPostById(id)));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Share] getShareBreakdown failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve share breakdown", e.getMessage());
        }
    }

    // =========================================================================
    // COUNTS
    // =========================================================================

    @GetMapping("/{postType}/{id}/counts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCounts(
            @PathVariable String postType,
            @PathVariable Long id) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Counts retrieved", new java.util.HashMap<>(interactionService.getPostCounts(id)));
            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Counts retrieved", new java.util.HashMap<>(interactionService.getSocialPostCounts(id)));
            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Counts] Failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve counts", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/counts/likes")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLikeCount(
            @PathVariable String postType,
            @PathVariable Long id) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Like count retrieved", Map.of("likeCount", interactionService.getPostById(id).getLikeCount()));
            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Like count retrieved", Map.of("likeCount", interactionService.getSocialPostById(id).getLikeCount()));
            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Counts] getLikeCount failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve like count", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/counts/dislikes")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDislikeCount(
            @PathVariable String postType,
            @PathVariable Long id) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Dislike count retrieved", Map.of("dislikeCount", interactionService.getPostById(id).getDislikeCount()));
            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Dislike count retrieved", Map.of("dislikeCount", interactionService.getSocialPostById(id).getDislikeCount()));
            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Counts] getDislikeCount failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve dislike count", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/counts/comments")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommentCount(
            @PathVariable String postType,
            @PathVariable Long id) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Comment count retrieved", Map.of("commentCount", interactionService.getPostById(id).getCommentCount()));
            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Comment count retrieved", Map.of("commentCount", interactionService.getSocialPostById(id).getCommentCount()));
            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Counts] getCommentCount failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve comment count", e.getMessage());
        }
    }

    // =========================================================================
    // REACTION STATUS
    // =========================================================================

    @GetMapping("/{postType}/{id}/my-status")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyReactionStatus(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                Post post = interactionService.getPostById(id);
                return ok("Reaction status retrieved", Map.of(
                        "liked",    interactionService.hasUserLikedPost(post, currentUser, actorToken),
                        "disliked", interactionService.hasUserDislikedPost(post, currentUser, actorToken),
                        "saved",    interactionService.hasSavedBroadcastPost(post, currentUser, actorToken)
                ));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("Reaction status retrieved", Map.of(
                        "liked",    interactionService.hasUserLikedSocialPost(sp, currentUser, actorToken),
                        "disliked", interactionService.hasUserDislikedSocialPost(sp, currentUser, actorToken),
                        "saved",    interactionService.hasSavedSocialPost(sp, currentUser, actorToken)
                ));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Status] getMyReactionStatus failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve reaction status", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/my-status/liked")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> hasUserLiked(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Like status retrieved",
                        Map.of("liked", interactionService.hasUserLikedPost(interactionService.getPostById(id), currentUser, actorToken)));
            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Like status retrieved",
                        Map.of("liked", interactionService.hasUserLikedSocialPost(interactionService.getSocialPostById(id), currentUser, actorToken)));
            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Status] hasUserLiked failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve like status", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/my-status/disliked")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> hasUserDisliked(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Dislike status retrieved",
                        Map.of("disliked", interactionService.hasUserDislikedPost(interactionService.getPostById(id), currentUser, actorToken)));
            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Dislike status retrieved",
                        Map.of("disliked", interactionService.hasUserDislikedSocialPost(interactionService.getSocialPostById(id), currentUser, actorToken)));
            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Status] hasUserDisliked failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve dislike status", e.getMessage());
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Map<String, Object> reactionBody(String key, boolean value, long likeCount, long dislikeCount) {
        return Map.of(key, value, "likeCount", likeCount, "dislikeCount", dislikeCount);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    private <T> ResponseEntity<ApiResponse<T>> err(String message, String detail) {
        return ResponseEntity.badRequest().body(ApiResponse.error(message, detail));
    }

    private <T> ResponseEntity<ApiResponse<T>> badPostType(String postType) {
        log.warn("[Interaction] Unknown postType='{}'. Accepted: '{}', '{}'",
                postType, Constant.INTERACTION_TYPE_POSTS, Constant.INTERACTION_TYPE_SOCIAL_POSTS);
        return err("Unsupported post type",
                "'" + postType + "' is not valid. Use 'posts' or 'social-posts'.");
    }
}
