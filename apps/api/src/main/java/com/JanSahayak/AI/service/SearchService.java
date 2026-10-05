package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.dto.PostResponse;
import com.JanSahayak.AI.dto.SocialPostDto;
import com.JanSahayak.AI.dto.SearchDto;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SearchService {

    private final PostRepo       postRepo;
    private final SocialPostRepo socialPostRepo;
    private final CommunityRepo  communityRepo;
    private final PostService    postService;

    // =========================================================================
    // Public API
    // =========================================================================

    public SearchDto.Response search(SearchDto.Request req) {
        String   query  = com.JanSahayak.AI.payload.PostUtility.sanitizeSqlLike(req.getQuery().trim());
        Integer  page   = req.getPage();
        int      limit  = req.safeLimit();
        Pageable probe  = PageRequest.of(page == null ? 0 : page, limit + 1);

        List<SearchDto.Result> posts       = Collections.emptyList();
        List<SearchDto.Result> socialPosts = Collections.emptyList();
        List<SearchDto.Result> communities = Collections.emptyList();
        List<SearchDto.Result> hashtags    = Collections.emptyList();

        if (req.isHashtagSearch()) {
            String tag = com.JanSahayak.AI.payload.PostUtility.sanitizeSqlLike(req.normalizedHashtag().replaceFirst("^#+", ""));
            if (req.includesType("POST"))        posts       = fetchPosts(tag, req.getPincode(), probe);
            if (req.includesType("SOCIAL_POST")) socialPosts = fetchSocialPostsByHashtag(tag, probe);
            if (req.includesType("COMMUNITY"))   communities = fetchCommunities(tag, req.getPincode(), probe);
            if (req.includesType("HASHTAG"))     hashtags    = fetchHashtagRows(tag, limit);
        } else {
            if (req.includesType("POST"))        posts       = fetchPosts(query, req.getPincode(), probe);
            if (req.includesType("SOCIAL_POST")) socialPosts = fetchSocialPosts(query, req.getPincode(), probe);
            if (req.includesType("COMMUNITY"))   communities = fetchCommunities(query, req.getPincode(), probe);
            if (req.includesType("HASHTAG"))     hashtags    = fetchHashtagRows(query, limit);
        }

        List<SearchDto.Result> flat = interleave(posts, socialPosts, communities, hashtags);

        boolean hasMore = flat.size() > limit;
        if (hasMore) flat = new ArrayList<>(flat.subList(0, limit));
        Integer nextPage = hasMore ? (page == null ? 0 : page) + 1 : null;

        Map<String, List<SearchDto.Result>> grouped = new LinkedHashMap<>();
        if (!posts.isEmpty())       grouped.put("POST",        cap(posts, limit));
        if (!socialPosts.isEmpty()) grouped.put("SOCIAL_POST", cap(socialPosts, limit));
        if (!communities.isEmpty()) grouped.put("COMMUNITY",   cap(communities, limit));
        if (!hashtags.isEmpty())    grouped.put("HASHTAG",     hashtags);

        Map<String, Long> countByType = new LinkedHashMap<>();
        countByType.put("POST",        (long) Math.min(posts.size(),       limit));
        countByType.put("SOCIAL_POST", (long) Math.min(socialPosts.size(), limit));
        countByType.put("COMMUNITY",   (long) Math.min(communities.size(), limit));
        countByType.put("HASHTAG",     (long) hashtags.size());

        return SearchDto.Response.builder()
                .query(query)
                .currentPage(page)
                .nextPage(nextPage)
                .count(flat.size())
                .limit(limit)
                .hasMore(hasMore)
                .data(flat)
                .grouped(grouped)
                .countByType(countByType)
                .build();
    }

    public PaginatedResponse<SearchDto.Result> searchByType(
            String query, String type, String pincode, Integer page, int limit) {

        int      safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable probe     = PageRequest.of(page == null ? 0 : page, safeLimit + 1);
        String   q         = com.JanSahayak.AI.payload.PostUtility.sanitizeSqlLike(query.trim());
        boolean  isHashtag = query.trim().startsWith("#");
        String   cleanTag  = com.JanSahayak.AI.payload.PostUtility.sanitizeSqlLike(query.trim().replaceFirst("^#+", ""));

        List<SearchDto.Result> results = switch (type.toUpperCase()) {
            case "POST"        -> fetchPosts(isHashtag ? cleanTag : q, pincode, probe);
            case "SOCIAL_POST" -> isHashtag ? fetchSocialPostsByHashtag(cleanTag, probe) : fetchSocialPosts(q, pincode, probe);
            case "COMMUNITY"   -> fetchCommunities(isHashtag ? cleanTag : q, pincode, probe);
            case "HASHTAG"     -> fetchHashtagRows(isHashtag ? cleanTag : q, safeLimit);
            default            -> Collections.emptyList();
        };

        boolean hasMore = results.size() > safeLimit;
        if (hasMore) results = new ArrayList<>(results.subList(0, safeLimit));
        Integer nextPage = hasMore ? (page == null ? 0 : page) + 1 : null;
        Long nextCursor = nextPage != null ? nextPage.longValue() : null;

        return PaginatedResponse.of(results, hasMore, nextCursor, safeLimit);
    }

    // =========================================================================
    // Fetchers
    // =========================================================================

    private List<SearchDto.Result> fetchPosts(String q, String pincode, Pageable probe) {
        try {
            boolean hasPincode = pincode != null && pincode.length() == 6;
            List<Post> rows;
            if (hasPincode) {
                rows = postRepo.searchPageByPincode(q, PostStatus.ACTIVE, pincode, probe);
            } else {
                rows = postRepo.searchPage(q, PostStatus.ACTIVE, probe);
            }
            return rows.stream().map(this::mapPost).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Post search error q='{}': {}", q, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchDto.Result> fetchSocialPosts(String q, String pincode, Pageable probe) {
        try {
            boolean hasPincode = pincode != null && pincode.length() == 6;
            List<SocialPost> rows;
            if (hasPincode) {
                rows = socialPostRepo.searchPageByPincode(q, PostStatus.ACTIVE, pincode, probe);
            } else {
                rows = socialPostRepo.searchPage(q, PostStatus.ACTIVE, probe);
            }
            return rows.stream().map(this::mapSocialPost).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("SocialPost search error q='{}': {}", q, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchDto.Result> fetchSocialPostsByHashtag(String hashtag, Pageable probe) {
        try {
            List<SocialPost> rows = socialPostRepo.searchByHashtagPage(hashtag, PostStatus.ACTIVE, probe);
            return rows.stream().map(this::mapSocialPost).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Hashtag post search error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchDto.Result> fetchCommunities(String q, String pincode, Pageable probe) {
        try {
            List<Community> rows = Collections.emptyList();

            // Try location-scoped search first when pincode is valid
            if (pincode != null && pincode.length() == 6) {
                String dist  = pincode.substring(0, 3);
                String state = pincode.substring(0, 2);
                rows = communityRepo.searchPageByLocation(q, pincode, dist, state, probe);
            }

            // Fall back to global search if location search returned nothing
            if (rows.isEmpty()) {
                rows = communityRepo.searchPage(q, probe);
            }

            return rows.stream().map(this::mapCommunity).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Community search error q='{}' pincode='{}': {}", q, pincode, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchDto.Result> fetchHashtagRows(String q, int limit) {
        try {
            return socialPostRepo.findTopHashtags(q.replaceFirst("^#+", ""), limit)
                    .stream().map(this::mapHashtagRow).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Hashtag aggregation error (needs MySQL 8+ JSON_TABLE): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<SearchDto.Result> interleave(List<SearchDto.Result> posts,
                                              List<SearchDto.Result> socialPosts,
                                              List<SearchDto.Result> communities,
                                              List<SearchDto.Result> hashtags) {
        List<List<SearchDto.Result>> active = new ArrayList<>();
        for (List<SearchDto.Result> src : List.of(posts, socialPosts, communities, hashtags))
            if (!src.isEmpty()) active.add(new ArrayList<>(src));

        List<SearchDto.Result> out = new ArrayList<>();
        int maxLen = active.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < maxLen; i++)
            for (List<SearchDto.Result> src : active)
                if (i < src.size()) out.add(src.get(i));
        return out;
    }

    private <T> List<T> cap(List<T> list, int limit) {
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    // =========================================================================
    // Mappers
    // =========================================================================

    private SearchDto.Result mapPost(Post p) {
        PostResponse pr = postService.convertToPostResponse(p, null);
        return SearchDto.Result.builder()
                .resultType("POST")
                .id(p.getId())
                .post(pr)
                .build();
    }

    private SearchDto.Result mapSocialPost(SocialPost sp) {
        SocialPostDto dto;
        if (sp.hasPoll()) {
            dto = SocialPostDto.fromSocialPostWithInteractions(
                    sp, 
                    false, // isLikedByCurrentUser
                    false, // isSavedByCurrentUser
                    false, // isViewedByCurrentUser
                    sp.getPoll(), // Poll entity reference
                    false, // userHasVoted
                    java.util.Collections.emptyList() // votedOptionIds
            );
        } else {
            dto = SocialPostDto.fromSocialPost(sp);
        }
        return SearchDto.Result.builder()
                .resultType("SOCIAL_POST")
                .id(sp.getId())
                .socialPost(dto)
                .build();
    }

    private SearchDto.Result mapCommunity(Community c) {
        return SearchDto.Result.builder()
                .resultType("COMMUNITY")
                .id(c.getId())
                .communityName(c.getName())
                .communitySlug(c.getSlug())
                .communityAvatarUrl(c.getAvatarUrl())
                .communityDescription(truncate(c.getDescription(), 200))
                .memberCount(c.getMemberCount())
                .privacy(c.getPrivacy() != null ? c.getPrivacy().name() : null)
                .healthScore(c.getHealthScore())
                .pincode(c.getPincode())
                .locationName(c.getLocationName())
                .build();
    }

    private SearchDto.Result mapHashtagRow(Object[] row) {
        String token   = row[0] != null ? row[0].toString() : "";
        long   postCnt = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        String display = token.startsWith("#") ? token : "#" + token;
        return SearchDto.Result.builder()
                .resultType("HASHTAG")
                .hashtag(display)
                .postCount(postCnt)
                .build();
    }

    private String truncate(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) + "..." : s);
    }
}
