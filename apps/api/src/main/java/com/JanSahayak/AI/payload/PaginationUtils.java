package com.JanSahayak.AI.payload;

import com.JanSahayak.AI.dto.CommentDto;
import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.dto.SocialPostDto;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.Comment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.sql.Timestamp;
import java.util.List;
import java.util.Collections;
import java.util.function.Function;

/**
 * Utility class for handling cursor-based pagination operations
 * Used across all service classes for consistent pagination implementation
 *
 * IMPORTANT — how hasMore detection works (BUG FIX #6):
 * The correct pattern for cursor-based pagination is to ask the DB for (limit + 1) rows.
 * If the DB returns more than `limit` items we know there is a next page (hasMore = true),
 * and we trim the extra item before returning.  The old code used `size == limit` which
 * falsely signals hasMore=true whenever the last page happens to have exactly `limit` rows,
 * causing the frontend to fire a request that returns an empty page.
 *
 * These utility methods receive whatever list the calling service/repository passes in.
 * To use them correctly, repository queries must fetch (limit + 1) rows, e.g.:
 *
 *   Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
 *   List<SocialPost> raw = repo.findByPincode(..., pageable);
 *   PaginationUtils.createSocialPostResponse(raw, setup.getValidatedLimit());
 *
 * The response builder then trims the extra item and sets hasMore accordingly.
 */
@Slf4j
public class PaginationUtils {

    // ===== Core Pagination Response Creation =====

    /**
     * Create paginated response using ID-based cursor.
     *
     * BUG FIX #6: original used `data.size() == requestedLimit` to decide hasMore.
     * This is wrong: if the last real page coincidentally has exactly `requestedLimit`
     * items, hasMore is set to true and the next request returns nothing — a wasted
     * round trip and a broken UX "Load more" button.
     *
     * The correct pattern: callers must fetch (requestedLimit + 1) rows from the DB.
     * This method checks if the list is LARGER than requestedLimit (i.e. the +1 sentinel
     * row came back), sets hasMore = true, then trims the list to requestedLimit before
     * returning so the extra row is never exposed to the client.
     */
    public static <T> PaginatedResponse<T> createIdBasedResponse(
            List<T> data,
            int requestedLimit,
            Function<T, Long> idExtractor) {

        if (data == null) {
            return PaginatedResponse.of(Collections.emptyList(), false, null, requestedLimit);
        }

        // hasMore is true only when the DB returned the sentinel extra row
        boolean hasMore = data.size() > requestedLimit;

        // Trim the sentinel row so the client only sees requestedLimit items
        List<T> trimmed = hasMore ? data.subList(0, requestedLimit) : data;

        Long nextCursor = hasMore && !trimmed.isEmpty()
                ? idExtractor.apply(trimmed.get(trimmed.size() - 1))
                : null;

        return PaginatedResponse.of(trimmed, hasMore, nextCursor, requestedLimit);
    }

    /**
     * Create paginated response using timestamp-based cursor.
     * Same fix as createIdBasedResponse — callers must fetch (limit + 1) rows.
     */
    public static <T> PaginatedResponse<T> createTimestampBasedResponse(
            List<T> data,
            int requestedLimit,
            Function<T, Long> timestampExtractor) {

        if (data == null) {
            return PaginatedResponse.of(Collections.emptyList(), false, null, requestedLimit);
        }

        boolean hasMore = data.size() > requestedLimit;
        List<T> trimmed = hasMore ? data.subList(0, requestedLimit) : data;

        Long nextCursor = hasMore && !trimmed.isEmpty()
                ? timestampExtractor.apply(trimmed.get(trimmed.size() - 1))
                : null;

        return PaginatedResponse.of(trimmed, hasMore, nextCursor, requestedLimit);
    }

    /**
     * Create paginated response with manual hasMore determination.
     * Used when you need custom logic to determine if more data exists.
     */
    public static <T> PaginatedResponse<T> createCustomResponse(
            List<T> data,
            boolean hasMore,
            Long nextCursor,
            int requestedLimit) {

        if (data == null) {
            return PaginatedResponse.of(Collections.emptyList(), false, null, requestedLimit);
        }

        return PaginatedResponse.of(data, hasMore, nextCursor, requestedLimit);
    }

    // ===== Standard Pagination Setup Methods =====

    /**
     * Standard pagination setup that's repeated across services.
     * Consolidates validation, sanitization, and logging.
     */
    public static PaginationSetup setupPagination(String methodName, Long beforeId, Integer limit) {
        int validatedLimit = validateLimit(limit);
        Long sanitizedCursor = sanitizeCursor(beforeId);
        logPaginationParams(methodName, sanitizedCursor, validatedLimit);

        return new PaginationSetup(validatedLimit, sanitizedCursor);
    }

    /**
     * Standard pagination setup with custom limit validation.
     */
    public static PaginationSetup setupPagination(String methodName, Long beforeId, Integer limit,
                                                  int defaultLimit, int maxLimit) {
        int validatedLimit = validateLimit(limit, defaultLimit, maxLimit);
        Long sanitizedCursor = sanitizeCursor(beforeId);
        logPaginationParams(methodName, sanitizedCursor, validatedLimit);

        return new PaginationSetup(validatedLimit, sanitizedCursor);
    }

    /**
     * Setup pagination for user tagging operations.
     */
    public static PaginationSetup setupTaggingPagination(String methodName, Long beforeId, Integer limit) {
        int validatedLimit = validateTaggingLimit(limit);
        Long sanitizedCursor = sanitizeCursor(beforeId);
        logPaginationParams(methodName, sanitizedCursor, validatedLimit);

        return new PaginationSetup(validatedLimit, sanitizedCursor);
    }

    /**
     * Setup pagination for geographic searches.
     */
    public static PaginationSetup setupGeographicPagination(String methodName, Long beforeId,
                                                            Integer limit, String searchType) {
        int validatedLimit = validateGeographicSearchLimit(limit, searchType);
        Long sanitizedCursor = sanitizeCursor(beforeId);
        logPaginationParams(methodName, sanitizedCursor, validatedLimit);

        return new PaginationSetup(validatedLimit, sanitizedCursor);
    }

    // ===== Limit Validation Methods =====

    /**
     * Validate and normalize limit parameter with default values.
     */
    public static int validateLimit(Integer limit) {
        return validateLimit(limit, Constant.DEFAULT_FEED_LIMIT, Constant.MAX_FEED_LIMIT);
    }

    /**
     * Validate and normalize limit parameter with custom defaults.
     */
    public static int validateLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        if (limit > maxLimit) {
            log.debug("Requested limit {} exceeds maximum {}, using maximum", limit, maxLimit);
            return maxLimit;
        }
        return limit;
    }

    /**
     * Validate limit for user tagging suggestions.
     */
    public static int validateTaggingLimit(Integer limit) {
        return validateLimit(limit, Constant.DEFAULT_TAGGING_SUGGESTIONS_LIMIT, Constant.MAX_TAGGING_SUGGESTIONS_LIMIT);
    }

    /**
     * Validate limit for user search operations.
     */
    public static int validateUserSearchLimit(Integer limit) {
        return validateLimit(limit, Constant.DEFAULT_USER_SEARCH_LIMIT, Constant.MAX_USER_SEARCH_LIMIT);
    }

    /**
     * Validate limit for department user searches.
     */
    public static int validateDepartmentSearchLimit(Integer limit) {
        return validateLimit(limit, Constant.DEFAULT_DEPARTMENT_SEARCH_LIMIT, Constant.MAX_DEPARTMENT_SEARCH_LIMIT);
    }

    /**
     * Validate limit for geographic searches (pincode, state, district).
     */
    public static int validateGeographicSearchLimit(Integer limit, String searchType) {
        if (searchType == null) {
            return validateLimit(limit);
        }

        switch (searchType.toLowerCase()) {
            case "pincode":
                return validateLimit(limit, Constant.DEFAULT_FEED_LIMIT, Constant.MAX_PINCODE_SEARCH_RESULTS);
            case "state":
                return validateLimit(limit, Constant.DEFAULT_FEED_LIMIT, Constant.MAX_STATE_SEARCH_RESULTS);
            case "district":
                return validateLimit(limit, Constant.DEFAULT_FEED_LIMIT, Constant.MAX_DISTRICT_SEARCH_RESULTS);
            default:
                return validateLimit(limit);
        }
    }

    /**
     * Validate limit specifically for comment pagination.
     */
    public static int validateCommentLimit(Integer limit) {
        return validateLimit(limit, Constant.DEFAULT_FEED_LIMIT, 200); // Comments typically need smaller limits
    }

    // ===== Cursor Extraction Methods =====

    /**
     * Extract ID cursor from User entity.
     */
    public static Long extractUserIdCursor(User user) {
        return user != null ? user.getId() : null;
    }

    /**
     * Extract ID cursor from Post entity.
     */
    public static Long extractPostIdCursor(Post post) {
        return post != null ? post.getId() : null;
    }

    /**
     * Extract ID cursor from Comment entity.
     */
    public static Long extractCommentIdCursor(Comment comment) {
        return comment != null ? comment.getId() : null;
    }

    /**
     * Extract timestamp cursor from Post entity (createdAt).
     */
    public static Long extractPostTimestampCursor(Post post) {
        if (post == null || post.getCreatedAt() == null) {
            return null;
        }
        return post.getCreatedAt().getTime();
    }

    /**
     * Extract timestamp cursor from Comment entity (createdAt).
     */
    public static Long extractCommentTimestampCursor(Comment comment) {
        if (comment == null || comment.getCreatedAt() == null) {
            return null;
        }
        return comment.getCreatedAt().getTime();
    }

    /**
     * Generic cursor extraction with null safety.
     */
    public static <T> Long extractCursor(T entity, Function<T, Long> extractor) {
        if (entity == null) {
            return null;
        }
        try {
            return extractor.apply(entity);
        } catch (Exception e) {
            log.warn("Failed to extract cursor from entity: {}",
                    entity.getClass().getSimpleName(), e);
            return null;
        }
    }

    // ===== Specialized Response Creators =====

    /**
     * Create paginated response for User entities using ID cursor.
     */
    public static PaginatedResponse<User> createUserResponse(List<User> users, int requestedLimit) {
        return createIdBasedResponse(users, requestedLimit, PaginationUtils::extractUserIdCursor);
    }

    /**
     * Create paginated response for Post entities using ID cursor.
     */
    public static PaginatedResponse<Post> createPostResponse(List<Post> posts, int requestedLimit) {
        return createIdBasedResponse(posts, requestedLimit, PaginationUtils::extractPostIdCursor);
    }

    /**
     * Create paginated response for Comment entities using ID cursor.
     */
    public static PaginatedResponse<Comment> createCommentResponse(List<Comment> comments, int requestedLimit) {
        return createIdBasedResponse(comments, requestedLimit, PaginationUtils::extractCommentIdCursor);
    }

    /**
     * Create empty paginated response.
     */
    public static <T> PaginatedResponse<T> createEmptyResponse(int requestedLimit) {
        return PaginatedResponse.of(Collections.emptyList(), false, null, requestedLimit);
    }

    // ===== Validation and Safety Methods =====

    /**
     * Check if cursor value is valid (not null and positive).
     */
    public static boolean isValidCursor(Long cursor) {
        return cursor != null && cursor >= Constant.MIN_VALID_CURSOR && cursor <= Constant.MAX_VALID_CURSOR;
    }

    /**
     * Check if timestamp cursor is within reasonable bounds.
     */
    public static boolean isValidTimestampCursor(Long timestampCursor) {
        if (!isValidCursor(timestampCursor)) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long maxAgeAgo = currentTime - Constant.MAX_TIMESTAMP_AGE_MILLIS;

        // Timestamp should be within the max age and not in the future
        return timestampCursor >= maxAgeAgo && timestampCursor <= currentTime;
    }

    /**
     * Sanitize cursor value to prevent injection attacks.
     */
    public static Long sanitizeCursor(Long cursor) {
        if (!isValidCursor(cursor)) {
            return null;
        }
        // Ensure cursor is within reasonable bounds
        return Math.max(Constant.MIN_VALID_CURSOR, Math.min(cursor, Constant.MAX_VALID_CURSOR));
    }

    /**
     * Validate and sanitize multiple cursors at once.
     */
    public static Long[] sanitizeCursors(Long... cursors) {
        if (cursors == null) {
            return new Long[0];
        }

        Long[] sanitized = new Long[cursors.length];
        for (int i = 0; i < cursors.length; i++) {
            sanitized[i] = sanitizeCursor(cursors[i]);
        }
        return sanitized;
    }

    // ===== Pageable Creation Methods =====

    /**
     * Create Pageable object for repository queries.
     * NOTE: to leverage the hasMore fix (BUG FIX #6), pass (limit + 1) here so the DB
     * returns the sentinel extra row.  Example:
     *   Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
     */
    public static Pageable createPageable(int limit) {
        return PageRequest.of(0, Math.max(1, limit));
    }

    /**
     * Create Pageable object with validated limit.
     */
    public static Pageable createPageable(Integer limit) {
        int validatedLimit = validateLimit(limit);
        return PageRequest.of(0, validatedLimit);
    }

    /**
     * Create Pageable object for specific pagination type.
     */
    public static Pageable createPageable(PaginationSetup setup) {
        return PageRequest.of(0, setup.getValidatedLimit());
    }

    // ===== Helper Methods for Complex Pagination Scenarios =====

    /**
     * Merge multiple paginated lists while maintaining order.
     * Used when combining results from multiple repository calls.
     */
    public static <T> List<T> mergeAndLimitResults(List<List<T>> resultLists, int limit) {
        if (resultLists == null || resultLists.isEmpty()) {
            return Collections.emptyList();
        }

        return resultLists.stream()
                .filter(list -> list != null)
                .flatMap(List::stream)
                .filter(item -> item != null)
                .distinct()
                .limit(Math.max(1, limit))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Calculate offset for traditional pagination (when needed for fallback).
     */
    public static int calculateOffset(int page, int size) {
        if (page < 1 || size < 1) {
            return 0;
        }
        return Math.max(0, (page - 1) * size);
    }

    /**
     * Convert timestamp to Timestamp object for database queries.
     */
    public static Timestamp convertToTimestamp(Long timestampCursor) {
        return isValidTimestampCursor(timestampCursor)
                ? new Timestamp(timestampCursor)
                : null;
    }

    /**
     * Check if pagination has reached the end.
     */
    public static boolean isLastPage(List<?> results, int requestedLimit) {
        return results == null || results.size() < requestedLimit;
    }

    /**
     * Calculate total estimated items (for progress indicators).
     */
    public static long estimateTotalItems(List<?> currentPage, boolean hasMore, long currentOffset) {
        if (currentPage == null || currentPage.isEmpty()) {
            return 0;
        }

        if (!hasMore) {
            return currentOffset + currentPage.size();
        }

        // Rough estimate: assume current page size continues
        return Math.max(currentOffset + currentPage.size() * 2, currentPage.size());
    }

    // ===== Query Construction Helpers =====

    /**
     * Build cursor condition for ID-based pagination.
     */
    public static String buildIdCursorCondition(Long beforeId, String entityAlias) {
        if (beforeId == null || !isValidCursor(beforeId)) {
            return "";
        }
        return String.format(" AND %s.id < %d",
                entityAlias != null ? entityAlias : "e", beforeId);
    }

    /**
     * Build cursor condition for timestamp-based pagination.
     */
    public static String buildTimestampCursorCondition(Long beforeTimestamp, String entityAlias, String timestampField) {
        if (beforeTimestamp == null || !isValidTimestampCursor(beforeTimestamp)) {
            return "";
        }
        return String.format(" AND %s.%s < %d",
                entityAlias != null ? entityAlias : "e",
                timestampField != null ? timestampField : "createdAt",
                beforeTimestamp);
    }

    // ===== Debug and Logging Methods =====

    /**
     * Log pagination parameters for debugging.
     */
    public static void logPaginationParams(String methodName, Long cursor, Integer limit) {
        if (Constant.ENABLE_PAGINATION_LOGGING && log.isDebugEnabled()) {
            log.debug("Pagination - Method: {}, Cursor: {}, Limit: {}",
                    methodName, cursor, limit);
        }
    }

    /**
     * Log pagination results for debugging.
     */
    public static <T> void logPaginationResults(String methodName, List<T> results, boolean hasMore, Long nextCursor) {
        if (Constant.ENABLE_PAGINATION_LOGGING && log.isDebugEnabled()) {
            log.debug("Pagination Result - Method: {}, Count: {}, HasMore: {}, NextCursor: {}",
                    methodName, results != null ? results.size() : 0, hasMore, nextCursor);
        }
    }

    /**
     * Log pagination setup for debugging.
     */
    public static void logPaginationSetup(String methodName, PaginationSetup setup) {
        if (Constant.ENABLE_PAGINATION_LOGGING && log.isDebugEnabled()) {
            log.debug("Pagination Setup - Method: {}, ValidatedLimit: {}, SanitizedCursor: {}",
                    methodName, setup.getValidatedLimit(), setup.getSanitizedCursor());
        }
    }

    // ===== Error Handling Methods =====

    /**
     * Handle pagination errors gracefully.
     */
    public static <T> PaginatedResponse<T> handlePaginationError(String methodName, Exception e, int requestedLimit) {
        log.error("Pagination error in method: {}", methodName, e);
        return createEmptyResponse(requestedLimit);
    }

    /**
     * Validate pagination response integrity.
     */
    public static <T> boolean validatePaginationResponse(PaginatedResponse<T> response) {
        if (response == null) {
            return false;
        }

        List<T> data = response.getData();
        if (data == null) {
            return false;
        }

        // If hasMore is true, nextCursor should not be null
        if (response.isHasMore() && response.getNextCursor() == null && !data.isEmpty()) {
            log.warn("Invalid pagination response: hasMore=true but nextCursor=null");
            return false;
        }

        return true;
    }

    // ===== Constants and Configuration =====

    /**
     * Default limit for user-related pagination.
     */
    public static final int DEFAULT_USER_LIMIT = Constant.DEFAULT_ACTIVE_USER_LIMIT;

    /**
     * Maximum limit for user-related pagination.
     */
    public static final int MAX_USER_LIMIT = Constant.MAX_ACTIVE_USER_LIMIT;

    /**
     * Default limit for search operations.
     */
    public static final int DEFAULT_SEARCH_LIMIT = Constant.DEFAULT_USER_SEARCH_LIMIT;

    /**
     * Maximum results for department user searches.
     */
    public static final int MAX_DEPARTMENT_SEARCH_LIMIT = Constant.MAX_DEPARTMENT_SEARCH_LIMIT;

    // ===== Inner Classes =====

    /**
     * Holds validated pagination parameters.
     */
    public static class PaginationSetup {
        private final int validatedLimit;
        private final Long sanitizedCursor;

        public PaginationSetup(int validatedLimit, Long sanitizedCursor) {
            this.validatedLimit = validatedLimit;
            this.sanitizedCursor = sanitizedCursor;
        }

        public int getValidatedLimit() {
            return validatedLimit;
        }

        public Long getSanitizedCursor() {
            return sanitizedCursor;
        }

        public Pageable toPageable() {
            return PageRequest.of(0, validatedLimit);
        }

        public boolean hasCursor() {
            return sanitizedCursor != null;
        }

        @Override
        public String toString() {
            return String.format("PaginationSetup{limit=%d, cursor=%s}",
                    validatedLimit, sanitizedCursor);
        }
    }

    /**
     * Builder for complex pagination scenarios.
     */
    public static class PaginationBuilder {
        private String methodName;
        private Long beforeId;
        private Integer limit;
        private Integer defaultLimit;
        private Integer maxLimit;
        private String searchType;

        public static PaginationBuilder create() {
            return new PaginationBuilder();
        }

        public PaginationBuilder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        public PaginationBuilder beforeId(Long beforeId) {
            this.beforeId = beforeId;
            return this;
        }

        public PaginationBuilder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public PaginationBuilder defaultLimit(Integer defaultLimit) {
            this.defaultLimit = defaultLimit;
            return this;
        }

        public PaginationBuilder maxLimit(Integer maxLimit) {
            this.maxLimit = maxLimit;
            return this;
        }

        public PaginationBuilder searchType(String searchType) {
            this.searchType = searchType;
            return this;
        }

        public PaginationSetup build() {
            int validatedLimit;

            if (searchType != null) {
                validatedLimit = validateGeographicSearchLimit(limit, searchType);
            } else if (defaultLimit != null && maxLimit != null) {
                validatedLimit = validateLimit(limit, defaultLimit, maxLimit);
            } else {
                validatedLimit = validateLimit(limit);
            }

            Long sanitizedCursor = sanitizeCursor(beforeId);

            if (methodName != null) {
                logPaginationParams(methodName, sanitizedCursor, validatedLimit);
            }

            return new PaginationSetup(validatedLimit, sanitizedCursor);
        }
    }

    public static PaginatedResponse<CommentDto> createCommentDtoResponse(List<CommentDto> commentDtos, int requestedLimit) {
        return createIdBasedResponse(commentDtos, requestedLimit, CommentDto::getId);
    }

    /**
     * Create empty paginated response for CommentDto.
     */
    public static PaginatedResponse<CommentDto> createEmptyCommentDtoResponse(int requestedLimit) {
        return PaginatedResponse.of(Collections.emptyList(), false, null, requestedLimit);
    }

    /**
     * Extract ID cursor from CommentDto.
     */
    public static Long extractCommentDtoIdCursor(CommentDto commentDto) {
        return commentDto != null ? commentDto.getId() : null;
    }

    /**
     * Extract ID cursor from SocialPost entity.
     */
    public static Long extractSocialPostIdCursor(SocialPost socialPost) {
        return socialPost != null ? socialPost.getId() : null;
    }

    /**
     * Extract timestamp cursor from SocialPost entity (createdAt).
     */
    public static Long extractSocialPostTimestampCursor(SocialPost socialPost) {
        if (socialPost == null || socialPost.getCreatedAt() == null) {
            return null;
        }
        return socialPost.getCreatedAt().getTime();
    }

    /**
     * Extract ID cursor from SocialPostDto.
     */
    public static Long extractSocialPostDtoIdCursor(SocialPostDto socialPostDto) {
        return socialPostDto != null ? socialPostDto.getId() : null;
    }

    /**
     * Create paginated response for SocialPost entities using ID cursor.
     */
    public static PaginatedResponse<SocialPost> createSocialPostResponse(
            List<SocialPost> socialPosts,
            int requestedLimit) {
        return createIdBasedResponse(socialPosts, requestedLimit,
                PaginationUtils::extractSocialPostIdCursor);
    }

    /**
     * Create paginated response for SocialPostDto using ID cursor.
     */
    public static PaginatedResponse<SocialPostDto> createSocialPostDtoResponse(
            List<SocialPostDto> socialPostDtos,
            int requestedLimit) {
        return createIdBasedResponse(socialPostDtos, requestedLimit,
                PaginationUtils::extractSocialPostDtoIdCursor);
    }

    /**
     * Create empty paginated response for SocialPost.
     */
    public static PaginatedResponse<SocialPost> createEmptySocialPostResponse(int requestedLimit) {
        return PaginatedResponse.of(Collections.emptyList(), false, null, requestedLimit);
    }

    /**
     * Create empty paginated response for SocialPostDto.
     */
    public static PaginatedResponse<SocialPostDto> createEmptySocialPostDtoResponse(int requestedLimit) {
        return PaginatedResponse.of(Collections.emptyList(), false, null, requestedLimit);
    }

    /**
     * Create paginated response for SocialPost using timestamp cursor.
     * Used for trending/recent feeds.
     */
    public static PaginatedResponse<SocialPost> createSocialPostTimestampResponse(
            List<SocialPost> socialPosts,
            int requestedLimit) {
        return createTimestampBasedResponse(socialPosts, requestedLimit,
                PaginationUtils::extractSocialPostTimestampCursor);
    }

    /**
     * Validate limit for social post feed operations.
     */
    public static int validateSocialPostFeedLimit(Integer limit) {
        return validateLimit(limit, Constant.DEFAULT_FEED_LIMIT, Constant.MAX_FEED_LIMIT);
    }

    /**
     * Validate limit for trending social posts.
     */
    public static int validateTrendingPostsLimit(Integer limit) {
        return validateLimit(limit, 20, 100); // Default 20, max 100 for trending
    }

    /**
     * Setup pagination for social post feeds.
     */
    public static PaginationSetup setupSocialPostFeedPagination(
            String methodName,
            Long beforeId,
            Integer limit) {
        int validatedLimit = validateSocialPostFeedLimit(limit);
        Long sanitizedCursor = sanitizeCursor(beforeId);
        logPaginationParams(methodName, sanitizedCursor, validatedLimit);

        return new PaginationSetup(validatedLimit, sanitizedCursor);
    }

    /**
     * Setup pagination for trending social posts.
     */
    public static PaginationSetup setupTrendingPostsPagination(
            String methodName,
            Long beforeId,
            Integer limit) {
        int validatedLimit = validateTrendingPostsLimit(limit);
        Long sanitizedCursor = sanitizeCursor(beforeId);
        logPaginationParams(methodName, sanitizedCursor, validatedLimit);

        return new PaginationSetup(validatedLimit, sanitizedCursor);
    }

    /**
     * Calculate relevance-based score cursor for recommendation feeds.
     * Used for sorting by engagement/virality scores.
     */
    public static Double extractRelevanceScoreCursor(SocialPost socialPost) {
        if (socialPost == null) {
            return null;
        }
        // Combine engagement and virality scores for cursor
        double engagement = socialPost.getEngagementScore() != null ? socialPost.getEngagementScore() : 0.0;
        double virality   = socialPost.getViralityScore()   != null ? socialPost.getViralityScore()   : 0.0;
        return (engagement * 0.6) + (virality * 0.4);
    }

    /**
     * Validate limit for saved social posts.
     */
    public static int validateSavedPostsLimit(Integer limit) {
        return validateLimit(limit, Constant.DEFAULT_FEED_LIMIT, 200);
    }

    /**
     * Setup pagination for user's saved posts.
     */
    public static PaginationSetup setupSavedPostsPagination(
            String methodName,
            Long beforeId,
            Integer limit) {
        int validatedLimit = validateSavedPostsLimit(limit);
        Long sanitizedCursor = sanitizeCursor(beforeId);
        logPaginationParams(methodName, sanitizedCursor, validatedLimit);

        return new PaginationSetup(validatedLimit, sanitizedCursor);
    }

    /**
     * Validate limit for hashtag-based searches.
     */
    public static int validateHashtagSearchLimit(Integer limit) {
        return validateLimit(limit, 30, 100); // Default 30, max 100
    }

    /**
     * Setup pagination for hashtag searches.
     */
    public static PaginationSetup setupHashtagSearchPagination(
            String methodName,
            Long beforeId,
            Integer limit) {
        int validatedLimit = validateHashtagSearchLimit(limit);
        Long sanitizedCursor = sanitizeCursor(beforeId);
        logPaginationParams(methodName, sanitizedCursor, validatedLimit);

        return new PaginationSetup(validatedLimit, sanitizedCursor);
    }

    /**
     * Create paginated response with relevance scoring.
     * Used for recommendation feeds where posts are sorted by multiple factors.
     */
    public static <T> PaginatedResponse<T> createRelevanceScoredResponse(
            List<T> data,
            int requestedLimit,
            Function<T, Long> idExtractor,
            Function<T, Double> scoreExtractor) {

        if (data == null || data.isEmpty()) {
            return PaginatedResponse.of(Collections.emptyList(), false, null, requestedLimit);
        }

        // BUG FIX #6 applied here too: use > not ==
        boolean hasMore = data.size() > requestedLimit;
        List<T> trimmed = hasMore ? data.subList(0, requestedLimit) : data;

        Long nextCursor = hasMore ? idExtractor.apply(trimmed.get(trimmed.size() - 1)) : null;

        return PaginatedResponse.of(trimmed, hasMore, nextCursor, requestedLimit);
    }

    /**
     * Merge social posts from multiple sources (local, viral, trending)
     * with intelligent deduplication and ranking.
     */
    public static List<SocialPost> mergeAndRankSocialPosts(
            List<List<SocialPost>> resultLists,
            int limit) {
        if (resultLists == null || resultLists.isEmpty()) {
            return Collections.emptyList();
        }

        return resultLists.stream()
                .filter(list -> list != null)
                .flatMap(List::stream)
                .filter(post -> post != null && post.isEligibleForDisplay())
                .distinct() // Remove duplicates by object equality
                .sorted((p1, p2) -> {
                    // Sort by engagement score descending
                    double e1 = p1.getEngagementScore() != null ? p1.getEngagementScore() : 0.0;
                    double v1 = p1.getViralityScore()   != null ? p1.getViralityScore()   : 0.0;
                    double e2 = p2.getEngagementScore() != null ? p2.getEngagementScore() : 0.0;
                    double v2 = p2.getViralityScore()   != null ? p2.getViralityScore()   : 0.0;

                    double score1 = (e1 * 0.5) + (v1 * 0.5);
                    double score2 = (e2 * 0.5) + (v2 * 0.5);
                    int scoreCompare = Double.compare(score2, score1);

                    // If scores are equal, sort by created date (newer first)
                    if (scoreCompare == 0) {
                        return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                    }
                    return scoreCompare;
                })
                .limit(Math.max(1, limit))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Calculate optimal feed size based on user engagement patterns.
     * Returns adjusted limit for personalized feed size.
     */
    public static int calculateOptimalFeedSize(Integer requestedLimit, boolean isHighlyEngaged) {
        int validatedLimit = validateSocialPostFeedLimit(requestedLimit);

        // Highly engaged users get larger feeds
        if (isHighlyEngaged && validatedLimit < 50) {
            return Math.min(validatedLimit + 10, 50);
        }

        return validatedLimit;
    }

}
