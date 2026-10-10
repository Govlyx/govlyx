package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.dto.UserTagSuggestionDto;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.PostService;
import com.JanSahayak.AI.service.UserService;
import com.JanSahayak.AI.service.UserTaggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/user-tagging")
@RequiredArgsConstructor
@Slf4j
public class UserTaggingController {

    private final UserTaggingService userTaggingService;
    private final PostService postService;

    // ===== Tag Processing Methods =====
    @PostMapping("/posts/{postId}/process")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_DEPARTMENT')")
    public ResponseEntity<ApiResponse<String>> processUserTags(@PathVariable Long postId) {
        try {
            Post post = postService.findById(postId);

            userTaggingService.processUserTags(post);

            return ResponseEntity.ok(ApiResponse.success("User tags processed successfully for post"));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error processing user tags for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to process user tags"));
        }
    }

    /**
     * Update post tags when content changes (Internal use - Admin/Department only)
     */
    @PutMapping("/posts/{postId}/update-tags")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_DEPARTMENT')")
    public ResponseEntity<ApiResponse<String>> updatePostTags(
            @PathVariable Long postId,
            @RequestBody Map<String, String> request) {
        try {
            Post post = postService.findById(postId);
            String newContent = request.get("content");

            if (newContent == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Validation failed", "Content is required"));
            }

            userTaggingService.updatePostTags(post, newContent);

            return ResponseEntity.ok(ApiResponse.success("Post tags updated successfully"));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error updating post tags for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to update post tags"));
        }
    }
    /**
     * Get user tag suggestions with cursor-based pagination
     */
    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<UserTagSuggestionDto>>> getUserTagSuggestions(
            @RequestParam String query,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        try {
            PaginatedResponse<UserTagSuggestionDto> suggestions = userTaggingService.getUserTagSuggestions(
                    query, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("User tag suggestions retrieved successfully", suggestions));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting user tag suggestions for query: {}", query, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get user tag suggestions"));
        }
    }
    @GetMapping("/users/{username}/tagged-posts/active")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActiveTaggedPostsByUsername(
            @PathVariable String username,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "desc") String sort,
            @CurrentUser User currentUser) {
        try {
            Map<String, Object> result = userTaggingService.getActivePostsByUsername(username, beforeId, limit, sort);
            return ResponseEntity.ok(ApiResponse.success("Active tagged posts retrieved successfully", result));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get active tagged posts for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get active tagged posts", "Internal server error"));
        }
    }

    @GetMapping("/users/{username}/tagged-posts/resolved")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getResolvedTaggedPostsByUsername(
            @PathVariable String username,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "desc") String sort,
            @CurrentUser User currentUser) {
        try {
            Map<String, Object> result = userTaggingService.getResolvedPostsByUsername(username, beforeId, limit, sort);
            return ResponseEntity.ok(ApiResponse.success("Resolved tagged posts retrieved successfully", result));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get resolved tagged posts for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get resolved tagged posts", "Internal server error"));
        }
    }

    @GetMapping("/users/{username}/analytics")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getComprehensiveAnalytics(
            @PathVariable String username,
            @CurrentUser User currentUser) {
        try {
            Map<String, Object> result = userTaggingService.getComprehensiveAnalyticsByUsername(username);
            return ResponseEntity.ok(ApiResponse.success("Tagging analytics retrieved successfully", result));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get tagging analytics for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get tagging analytics", "Internal server error"));
        }
    }
}
