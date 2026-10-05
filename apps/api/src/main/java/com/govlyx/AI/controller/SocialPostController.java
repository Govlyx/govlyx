package com.Govlyx.AI.controller;

import com.Govlyx.AI.dto.*;
import com.Govlyx.AI.enums.FeedSort;
import com.Govlyx.AI.exception.ApiResponse;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.security.CurrentUser;
import com.Govlyx.AI.service.SocialPostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST Controller for Social Post operations
 * Handles creation, retrieval, updates, and deletion of social posts
 */
@RestController
@RequestMapping("/api/social-posts")
@RequiredArgsConstructor
@Slf4j
public class SocialPostController {

    private final SocialPostService socialPostService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.Govlyx.AI.security.IdentityBlindService identityBlindService;

    // ===== CREATE ENDPOINTS =====

    /**
     * Create a text-only social post (no media)
     * POST /api/social-posts/text
     */
    @PostMapping("/text")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<SocialPostDto>> createTextPost(
            @RequestBody @Valid SocialPostCreateDto createDto,
            @CurrentUser User currentUser) {

        try {
            log.info("Creating text post for user: {}", currentUser.getActualUsername());

            SocialPostDto createdPost = socialPostService.createTextPost(createDto, currentUser);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Text post created successfully", createdPost));

        } catch (Exception e) {
            log.error("Failed to create text post for user: {}", currentUser.getActualUsername(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to create text post", e.getMessage()));
        }
    }

    /**
     * Create a social post with media files
     * POST /api/social-posts/with-media
     */
    @PostMapping(value = "/with-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<SocialPostDto>> createPostWithMedia(
            @RequestPart("post") @Valid SocialPostCreateDto createDto,
            @RequestPart(value = "media", required = false) List<MultipartFile> mediaFiles,
            @CurrentUser User currentUser) {

        try {
            log.info("Creating social post with {} media files for user: {}",
                    mediaFiles != null ? mediaFiles.size() : 0, currentUser.getActualUsername());

            SocialPostDto createdPost = socialPostService.createSocialPost(
                    createDto, mediaFiles, currentUser);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Social post created successfully", createdPost));

        } catch (Exception e) {
            log.error("Failed to create social post with media for user: {}",
                    currentUser.getActualUsername(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to create social post", e.getMessage()));
        }
    }

    // ===== READ ENDPOINTS =====

    /**
     * Get a specific social post by ID
     * GET /api/social-posts/{postId}
     */
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<SocialPostDto>> getSocialPostById(
            @PathVariable @NotNull Long postId,
            @CurrentUser(required = false) User currentUser) {

        try {
            log.debug("Fetching social post: {}", postId);

            SocialPostDto post = socialPostService.getSocialPostById(postId, currentUser);

            return ResponseEntity.ok(ApiResponse.success("Social post retrieved", post));

        } catch (com.Govlyx.AI.exception.ResourceNotFoundException | com.Govlyx.AI.exception.PostNotFoundException e) {
            log.warn("Social post not found: {}", postId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get social post: {}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve social post", e.getMessage()));
        }
    }

    /**
     * Get home feed (personalized recommendations)
     * GET /api/social-posts/feed/home
     */
    @GetMapping("/feed/home")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<SocialPostDto>>> getHomeFeed(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User currentUser) {

        try {
            log.debug("Fetching home feed for user: {}", currentUser.getActualUsername());

            PaginatedResponse<SocialPostDto> feed = socialPostService.getHomeFeed(
                    currentUser, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Home feed retrieved", feed));

        } catch (Exception e) {
            log.error("Failed to get home feed for user: {}", currentUser.getActualUsername(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve home feed", e.getMessage()));
        }
    }

    /**
     * Get trending posts
     * GET /api/social-posts/feed/trending
     */
    @GetMapping("/feed/trending")
    public ResponseEntity<ApiResponse<PaginatedResponse<SocialPostDto>>> getTrendingPosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser(required = false) User currentUser) {

        try {
            log.debug("Fetching trending posts");

            PaginatedResponse<SocialPostDto> trending = socialPostService.getTrendingPosts(
                    currentUser, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Trending posts retrieved", trending));

        } catch (Exception e) {
            log.error("Failed to get trending posts", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve trending posts", e.getMessage()));
        }
    }

    /**
     * Get local posts (based on user's location)
     * GET /api/social-posts/feed/local
     */
    @GetMapping("/feed/local")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<SocialPostDto>>> getLocalPosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User currentUser) {

        try {
            log.debug("Fetching local posts for user: {}", currentUser.getActualUsername());

            PaginatedResponse<SocialPostDto> localPosts = socialPostService.getLocalPosts(
                    currentUser, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Local posts retrieved", localPosts));

        } catch (Exception e) {
            log.error("Failed to get local posts for user: {}", currentUser.getActualUsername(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve local posts", e.getMessage()));
        }
    }

    /**
     * Get the Neighborhood Q&A feed filtered by geo-scope.
     * GET /api/social-posts/feed/neighborhood
     *
     * @param scope     One of: NEARBY (5km GPS radius), AREA (city), CITY (urban cluster), DISTRICT
     *                  Defaults to NEARBY if omitted.
     * @param lastPostId Cursor ID for pagination.
     * @param size      Page size (default 20).
     */
    @GetMapping("/feed/neighborhood")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<SocialPostDto>>> getNeighborhoodFeed(
            @RequestParam(required = false, defaultValue = "NEARBY") String scope,
            @RequestParam(required = false, defaultValue = "HOT") FeedSort sort,
            @RequestParam(required = false) Long lastPostId,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @CurrentUser User currentUser) {

        try {
            log.debug("Fetching neighborhood QA feed: scope={} sort={} user={}", scope, sort, currentUser.getActualUsername());
            int validSize = (size != null && size > 0 && size <= 50) ? size : 20;
            PaginatedResponse<SocialPostDto> feed = socialPostService.getNeighborhoodQAFeed(
                    currentUser, scope.toUpperCase(), sort, lastPostId, validSize);
            return ResponseEntity.ok(ApiResponse.success("Neighborhood feed retrieved", feed));

        } catch (Exception e) {
            log.error("Failed to get neighborhood feed for user: {}", currentUser.getActualUsername(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve neighborhood feed", e.getMessage()));
        }
    }

    /**
     * Get posts by a specific user
     * GET /api/social-posts/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<PaginatedResponse<SocialPostDto>>> getUserPosts(
            @PathVariable @NotNull Long userId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser(required = false) User currentUser) {

        try {
            log.debug("Fetching posts for user: {}", userId);

            PaginatedResponse<SocialPostDto> userPosts = socialPostService.getUserPosts(
                    userId, currentUser, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("User posts retrieved", userPosts));

        } catch (Exception e) {
            log.error("Failed to get posts for user: {}", userId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve user posts", e.getMessage()));
        }
    }

    /**
     * Get current user's posts
     * GET /api/social-posts/my-posts
     */
    @GetMapping("/my-posts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<SocialPostDto>>> getMyPosts(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User currentUser) {

        try {
            log.debug("Fetching posts for current user: {}", currentUser.getActualUsername());

            String effectiveToken = (actorToken != null && !actorToken.isBlank())
                    ? actorToken
                    : (identityBlindService != null ? identityBlindService.resolveActorTokenForUser(currentUser) : null);

            PaginatedResponse<SocialPostDto> myPosts = (effectiveToken != null && !effectiveToken.isBlank())
                    ? socialPostService.getUserPostsByActorToken(effectiveToken, currentUser, beforeId, limit)
                    : socialPostService.getUserPosts(currentUser.getId(), currentUser, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Your posts retrieved", myPosts));

        } catch (Exception e) {
            log.error("Failed to get posts for current user: {}", currentUser.getActualUsername(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve your posts", e.getMessage()));
        }
    }

    /**
     * Search posts by hashtag
     * GET /api/social-posts/search/hashtag
     */
    @GetMapping("/search/hashtag")
    public ResponseEntity<ApiResponse<PaginatedResponse<SocialPostDto>>> searchByHashtag(
            @RequestParam @NotNull String hashtag,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser(required = false) User currentUser) {

        try {
            log.debug("Searching posts by hashtag: {}", hashtag);

            PaginatedResponse<SocialPostDto> posts = socialPostService.searchByHashtag(
                    hashtag, currentUser, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Posts retrieved for hashtag: " + hashtag, posts));

        } catch (Exception e) {
            log.error("Failed to search posts by hashtag: {}", hashtag, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to search posts", e.getMessage()));
        }
    }

    // ===== UPDATE ENDPOINTS =====

    /**
     * Update a social post
     * PUT /api/social-posts/{postId}
     */
    @PutMapping("/{postId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<SocialPostDto>> updateSocialPost(
            @PathVariable @NotNull Long postId,
            @RequestBody @Valid SocialPostUpdateDto updateDto,
            @CurrentUser User currentUser) {

        try {
            log.info("Updating social post: {} by user: {}", postId, currentUser.getActualUsername());

            SocialPostDto updatedPost = socialPostService.updateSocialPost(
                    postId, updateDto, currentUser);

            return ResponseEntity.ok(ApiResponse.success("Social post updated successfully", updatedPost));

        } catch (Exception e) {
            log.error("Failed to update social post: {}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to update social post", e.getMessage()));
        }
    }

    // ===== DELETE ENDPOINTS =====

    /**
     * Delete a social post (soft delete)
     * DELETE /api/social-posts/{postId}
     */
    @DeleteMapping("/{postId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSocialPost(
            @PathVariable @NotNull Long postId,
            @CurrentUser User currentUser) {

        try {
            log.info("Deleting social post: {} by user: {}", postId, currentUser.getActualUsername());

            socialPostService.deleteSocialPost(postId, currentUser);

            return ResponseEntity.ok(ApiResponse.success("Social post deleted successfully", null));

        } catch (Exception e) {
            log.error("Failed to delete social post: {}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to delete social post", e.getMessage()));
        }
    }

    /**
     * Get total count of social posts by a specific user
     * GET /api/social-posts/count/user/{userId}
     */
    @GetMapping("/count/user/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> getSocialPostsCountByUser(@PathVariable Long userId) {
        try {
            Long count = socialPostService.countSocialPostsByUserId(userId);
            return ResponseEntity.ok(ApiResponse.success("User social posts count retrieved", count));
        } catch (Exception e) {
            log.error("Failed to count social posts for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to count social posts", e.getMessage()));
        }
    }

    /**
     * Get total count of social posts by the current user
     * GET /api/social-posts/count/my-posts
     */
    @GetMapping("/count/my-posts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> getMySocialPostsCount(
            @RequestHeader(value = "X-Actor-Token", required = false) String actorToken,
            @CurrentUser User currentUser) {
        try {
            String effectiveToken = (actorToken != null && !actorToken.isBlank())
                    ? actorToken
                    : (identityBlindService != null ? identityBlindService.resolveActorTokenForUser(currentUser) : null);

            Long count = 0L;
            if (effectiveToken != null && !effectiveToken.isBlank()) {
                count = socialPostService.countSocialPostsByActorToken(effectiveToken);
            }
            if (currentUser != null && currentUser.getId() != null) {
                Long userCount = socialPostService.countSocialPostsByUserId(currentUser.getId());
                if (effectiveToken == null || effectiveToken.isBlank()) {
                    count = userCount;
                } else if (userCount != null && userCount > 0) {
                    count = Math.max(count, userCount);
                }
            }
            return ResponseEntity.ok(ApiResponse.success("Your social posts count retrieved", count));
        } catch (Exception e) {
            log.error("Failed to count social posts for current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to count your social posts", e.getMessage()));
        }
    }
}
