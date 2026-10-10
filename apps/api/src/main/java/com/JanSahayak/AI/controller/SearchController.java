package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.dto.SearchDto;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.service.SearchService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * Search controller -- three endpoints.
 *
 *   GET /api/search          Unified infinite-scroll search (all types mixed)
 *   GET /api/search/type     Per-type scroll (individual tab -- POST / SOCIAL_POST / etc.)
 *   GET /api/search/quick    Typeahead autocomplete (5 results, no cursor)
 *
 * Result shape (SearchDto.Result):
 *   POST type        -> result.post        is a full PostResponse
 *   SOCIAL_POST type -> result.socialPost  is a full SocialPostDto
 *   COMMUNITY type   -> result.community*  fields populated
 *   HASHTAG type     -> result.hashtag + result.postCount populated
 *
 * Infinite-scroll flow:
 *   1. GET /api/search?q=water
 *      <- { data:[20], hasMore:true, nextCursor:88 }
 *   2. GET /api/search?q=water&cursor=88
 *      <- { data:[20], hasMore:true, nextCursor:41 }
 *   3. GET /api/search?q=water&cursor=41
 *      <- { data:[5],  hasMore:false, nextCursor:null }  // stop
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final SearchService searchService;

    // -------------------------------------------------------------------------
    // 1.  Unified search (all types, infinite scroll)
    // -------------------------------------------------------------------------

    /**
     * @param q       Search term. Prefix with # for hashtag mode.
     * @param types   Optional CSV filter: POST, SOCIAL_POST, COMMUNITY, HASHTAG
     * @param pincode Optional 6-digit pincode; falls back to user's own pincode.
     * @param cursor  ID of last seen item (null on first call).
     * @param limit   Batch size 1-50, default 20.
     */
    @GetMapping
    public ResponseEntity<SearchDto.Response> search(
            @RequestParam("q")                              @NotBlank @Size(min = 1, max = 200) String q,
            @RequestParam(value = "types",   required = false)                                  Set<String> types,
            @RequestParam(value = "pincode", required = false)                                  String pincode,
            @RequestParam(value = "page",    required = false)                                  Integer page,
            @RequestParam(value = "limit",   defaultValue = "20")                               int limit,
            @AuthenticationPrincipal User currentUser
    ) {
        SearchDto.Request req = new SearchDto.Request();
        req.setQuery(q);
        req.setTypes(types);
        req.setPincode(resolvePincode(pincode, currentUser));
        req.setPage(page);
        req.setLimit(limit);

        log.info("Search user={} q='{}' page={} types={}", uid(currentUser), q, page, types);
        return ResponseEntity.ok(searchService.search(req));
    }

    // -------------------------------------------------------------------------
    // 2.  Per-type scroll (one entity type at a time)
    // -------------------------------------------------------------------------

    /**
     * Scroll a single type independently.
     * Used when the user is on a specific tab (e.g. "Communities").
     * Returns PaginatedResponse<SearchDto.Result> -- same contract as PostService throughout.
     *
     * Examples:
     *   GET /api/search/type?q=water&type=COMMUNITY
     *   GET /api/search/type?q=water&type=POST&cursor=55
     */
    @GetMapping("/type")
    public ResponseEntity<PaginatedResponse<SearchDto.Result>> searchByType(
            @RequestParam("q")                             @NotBlank @Size(min = 1, max = 200) String q,
            @RequestParam("type")                                                               String type,
            @RequestParam(value = "pincode", required = false)                                  String pincode,
            @RequestParam(value = "page",    required = false)                                  Integer page,
            @RequestParam(value = "limit",   defaultValue = "20")                               int limit,
            @AuthenticationPrincipal User currentUser
    ) {
        log.info("Search/type user={} q='{}' type={} page={}", uid(currentUser), q, type, page);
        return ResponseEntity.ok(
                searchService.searchByType(q, type, resolvePincode(pincode, currentUser), page, limit));
    }

    // -------------------------------------------------------------------------
    // 3.  Quick / typeahead
    // -------------------------------------------------------------------------

    /**
     * Fast autocomplete -- always first page, 5 results per type, no cursor.
     * Call on every keystroke after a debounce of ~300 ms.
     *
     * Example: GET /api/search/quick?q=wat
     */
    @GetMapping("/quick")
    public ResponseEntity<SearchDto.Response> quickSearch(
            @RequestParam("q") @NotBlank @Size(min = 1, max = 100) String q,
            @AuthenticationPrincipal User currentUser
    ) {
        SearchDto.Request req = new SearchDto.Request();
        req.setQuery(q);
        req.setPincode(currentUser != null ? currentUser.getPincode() : null);
        req.setPage(null);  // always first page
        req.setLimit(5);      // small batch for typeahead speed
        return ResponseEntity.ok(searchService.search(req));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private String resolvePincode(String fromRequest, User user) {
        if (fromRequest != null && !fromRequest.isBlank()) return fromRequest;
        return user != null ? user.getPincode() : null;
    }

    private String uid(User user) {
        return user != null ? user.getId().toString() : "anon";
    }
}
