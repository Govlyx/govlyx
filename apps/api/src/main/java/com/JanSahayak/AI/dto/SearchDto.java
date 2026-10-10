package com.JanSahayak.AI.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SearchDto -- single file, three static inner classes.
 *
 *   SearchDto.Request   inbound query + cursor params
 *   SearchDto.Result    unified result item (POST / SOCIAL_POST / COMMUNITY / HASHTAG)
 *                         POST        -> result.post        = full PostResponse
 *                         SOCIAL_POST -> result.socialPost  = full SocialPostDto
 *                         COMMUNITY   -> community fields inline
 *                         HASHTAG     -> hashtag + postCount only
 *   SearchDto.Response  paginated cursor-based response
 */
public final class SearchDto {

    private SearchDto() {} // namespace class - no instantiation

    // =========================================================================
    // 1.  REQUEST
    // =========================================================================

    /**
     * Inbound search request with cursor-based infinite scroll.
     *
     * Cursor pagination (Twitter / Instagram style):
     *   First call : cursor = null  -> returns first N items
     *   Next calls : cursor = nextCursor from previous response
     *   End        : response.hasMore = false, response.nextCursor = null
     */
    @Data
    public static class Request {

        @NotBlank(message = "Search query must not be blank")
        @Size(min = 1, max = 200, message = "Query must be between 1 and 200 characters")
        private String query;

        /** POST | SOCIAL_POST | COMMUNITY | HASHTAG  -- null/empty = all types. */
        private Set<String> types;

        /** 6-digit pincode for hyperlocal results; auto-filled from user profile if absent. */
        private String pincode;

        /**
         * Page number for offset pagination.
         * Default is 0 (first page).
         */
        private Integer page = 0;

        /** Batch size. Default 20, max 50. */
        private int limit = 20;

        public boolean includesType(String type) {
            return types == null || types.isEmpty() || types.contains(type.toUpperCase());
        }

        public boolean isHashtagSearch() {
            return query != null && query.trim().startsWith("#");
        }

        /** Returns normalised "#tag" string for DB LIKE queries. */
        public String normalizedHashtag() {
            if (!isHashtagSearch()) return null;
            return "#" + query.trim().replaceFirst("^#+", "");
        }

        public int safeLimit() {
            return Math.min(Math.max(limit, 1), 50);
        }

        public boolean isFirstPage() {
            return page == null || page == 0;
        }

        /** District prefix derived from pincode (first 3 digits). */
        public String districtPrefix() {
            return (pincode != null && pincode.length() == 6) ? pincode.substring(0, 3) : null;
        }

        /** State prefix derived from pincode (first 2 digits). */
        public String statePrefix() {
            return (pincode != null && pincode.length() == 6) ? pincode.substring(0, 2) : null;
        }
    }

    // =========================================================================
    // 2.  RESULT
    // =========================================================================

    /**
     * Unified result item. Wraps existing DTOs so no fields are duplicated.
     *
     * resultType discriminator:
     *   POST        -> post field populated        (PostResponse)
     *   SOCIAL_POST -> socialPost field populated  (SocialPostDto)
     *   COMMUNITY   -> community* fields populated
     *   HASHTAG     -> hashtag + postCount populated
     *
     * id is always set (used as cursor value by the frontend).
     * Null fields are omitted from JSON.
     */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Result {

        /** POST | SOCIAL_POST | COMMUNITY | HASHTAG */
        private String resultType;

        /** Always set -- used as the cursor value by the frontend. */
        private Long id;

        // POST -- full PostResponse object
        private PostResponse post;

        // SOCIAL_POST -- full SocialPostDto object
        private SocialPostDto socialPost;

        // COMMUNITY fields
        private String  communityName;
        private String  communitySlug;
        private String  communityAvatarUrl;
        private String  communityDescription;
        private Integer memberCount;
        private String  privacy;
        private Double  healthScore;
        private String  pincode;
        private String  locationName;

        // HASHTAG fields
        private String hashtag;   // e.g. "#water"
        private Long   postCount; // posts using this hashtag

        // Reserved for future ML ranking
        private Double relevanceScore;
    }

    // =========================================================================
    // 3.  RESPONSE
    // =========================================================================

    /**
     * Paginated search response - cursor-based infinite scroll.
     *
     * Frontend pattern:
     *   1. GET /api/search?q=water
     *      -> { data:[...20], hasMore:true,  nextPage:1, countByType:{POST:8,...} }
     *   2. GET /api/search?q=water&page=1
     *      -> { data:[...20], hasMore:true,  nextPage:2 }
     *   3. GET /api/search?q=water&page=2
     *      -> { data:[...5],  hasMore:false, nextPage:null }  -- stop
     *
     * data    = flat interleaved list  (powers the "All" tab)
     * grouped = per-type map           (powers tabbed UI)
     */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {

        private String query;
        private Integer currentPage;    // page used for THIS batch (default 0)
        private Integer nextPage;       // pass as page on next request; null = end of results
        private int    count;          // items in this batch
        private int    limit;          // requested batch size
        private boolean hasMore;

        /** Flat interleaved list -- powers the \"All\" tab. */
        private List<Result> data;

        /** Per-type grouping -- powers tabbed UI. */
        private Map<String, List<Result>> grouped;

        /** Tab badge counts -- e.g. { \"POST\": 8, \"SOCIAL_POST\": 5 }. */
        private Map<String, Long> countByType;
    }
}
