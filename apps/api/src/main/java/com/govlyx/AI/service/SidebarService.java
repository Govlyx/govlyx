package com.govlyx.AI.service;

import com.govlyx.AI.dto.PostResponse;
import com.govlyx.AI.dto.SocialPostDto;
import com.govlyx.AI.dto.sidebar.AreaPulseDto;
import com.govlyx.AI.dto.sidebar.SidebarResponseDto;
import com.govlyx.AI.enums.BroadcastScope;
import com.govlyx.AI.enums.PostStatus;
import com.govlyx.AI.enums.SocialPostCategory;
import com.govlyx.AI.model.Post;
import com.govlyx.AI.model.SocialPost;
import com.govlyx.AI.model.User;
import com.govlyx.AI.repository.PostRepo;
import com.govlyx.AI.repository.SocialPostRepo;
import com.govlyx.AI.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SidebarService {

    private final PostRepo postRepo;
    private final SocialPostRepo socialPostRepo;
    private final PostService postService;
    private final SocialPostService socialPostService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    /**
     * Normalize frontend tab keys to consistent internal format.
     * Frontend sends: all, location, following, official, neighborhood_qa
     * These get uppercased and underscore-normalized for the switch statement.
     */
    private String normalizeTab(String tab) {
        if (tab == null) return "ALL";
        // Replace hyphens with underscores, then uppercase for consistent matching
        String normalized = tab.toLowerCase().replace("-", "_");
        switch (normalized) {
            case "for_you":       return "ALL";             // legacy alias
            case "all":           return "ALL";
            case "location":      return "LOCATION";
            case "official":      return "OFFICIAL";
            case "following":     return "FOLLOWING";
            case "neighborhood_qa": return "NEIGHBORHOOD_QA";
            default:              return "ALL";
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "sidebarData", key = "#token.concat('-').concat(#tabName)", cacheManager = "cacheManager")
    public SidebarResponseDto getSidebarData(String token, String tabName) {
        // Bug Fix #1: Gracefully handle expired/invalid JWT instead of crashing with 500
        String username;
        try {
            username = jwtUtil.getUsernameFromToken(token);
        } catch (Exception e) {
            log.warn("[SidebarService] Invalid or expired JWT token in sidebar request: {}", e.getMessage());
            return SidebarResponseDto.builder().activeTab(tabName).build();
        }

        User user = userService.findByUsername(username);
        if (user == null) {
            log.warn("[SidebarService] User not found for username extracted from token");
            return SidebarResponseDto.builder().activeTab(tabName).build();
        }

        // Bug Fix #2: Normalize the tab name so frontend's 'all'/'neighborhood_qa' map correctly
        String normalizedTab = normalizeTab(tabName);
        log.debug("[SidebarService] tab='{}' → normalized='{}' for user={}", tabName, normalizedTab, username);

        SidebarResponseDto.SidebarResponseDtoBuilder response = SidebarResponseDto.builder()
                .activeTab(tabName);

        // Section 1: Always visible
        response.areaPulse(getAreaPulse(user));

        // Active tab conditional sections — uses normalized tab key
        switch (normalizedTab) {
            case "ALL":
                response.topUnresolvedIssue(getTopUnresolvedIssue(user));
                response.unansweredQuestions(getTopNeighborhoodQuestions(user));
                break;
            case "LOCATION":
                response.latestOfficialAlert(getLatestOfficialAlert(user));
                response.unansweredQuestions(getTopNeighborhoodQuestions(user));
                break;
            case "OFFICIAL":
                response.topUnresolvedIssue(getTopUnresolvedIssue(user));
                break;
            case "FOLLOWING":
                response.unansweredQuestions(getTopNeighborhoodQuestions(user));
                response.latestOfficialAlert(getLatestOfficialAlert(user));
                break;
            case "NEIGHBORHOOD_QA":
                response.topUnresolvedIssue(getTopUnresolvedIssue(user));
                response.latestOfficialAlert(getLatestOfficialAlert(user));
                response.unansweredQuestions(getTopNeighborhoodQuestions(user));
                break;
            default:
                // Safety fallback — return For You widgets
                response.topUnresolvedIssue(getTopUnresolvedIssue(user));
                response.unansweredQuestions(getTopNeighborhoodQuestions(user));
                break;
        }

        return response.build();
    }

    private AreaPulseDto getAreaPulse(User user) {
        String pincode = user.getPincode();
        if (pincode == null || pincode.isEmpty()) {
            return new AreaPulseDto(0, 0, 0);
        }

        Date startOfWeek = Date.from(LocalDate.now().minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant());

        long totalIssuesThisWeek = postRepo.countIssuesByDepartmentTagAndPincode(pincode, startOfWeek);

        long resolvedIssuesThisWeek = postRepo.countResolvedIssuesByDepartmentTagAndPincode(pincode, true, startOfWeek);

        List<String> localPincodes = List.of(pincode);

        long unansweredQuestions = socialPostRepo.countByCategoryAndPincodeInAndCommentCountAndStatus(
                SocialPostCategory.NEIGHBORHOOD_QUESTION, localPincodes, 0, PostStatus.ACTIVE);

        return AreaPulseDto.builder()
                .totalIssuesThisWeek(totalIssuesThisWeek)
                .resolvedIssuesThisWeek(resolvedIssuesThisWeek)
                .unansweredQuestions(unansweredQuestions)
                .build();
    }

    private List<SocialPostDto> getTopNeighborhoodQuestions(User user) {
        String pincode = user.getPincode();
        if (pincode == null || pincode.isEmpty()) {
            return List.of();
        }

        List<String> localPincodes = List.of(pincode);

        List<SocialPost> posts = socialPostRepo.findTopQAPostsByPincodes(
                SocialPostCategory.NEIGHBORHOOD_QUESTION,
                localPincodes,
                PostStatus.ACTIVE,
                PageRequest.of(0, 4)
        );

        return posts.stream()
                .map(p -> socialPostService.convertToDto(p, user))
                .collect(Collectors.toList());
    }

    private PostResponse getTopUnresolvedIssue(User user) {
        String pincode = user.getPincode();
        if (pincode == null || pincode.isEmpty()) {
            return null;
        }

        List<Post> posts = postRepo.findTopUnresolvedIssueByDepartmentTagAndPincode(
                pincode,
                false,
                PostStatus.ACTIVE,
                PageRequest.of(0, 1)
        );

        if (posts.isEmpty()) {
            return null;
        }

        return postService.convertToPostResponse(posts.get(0), user);
    }

    private PostResponse getLatestOfficialAlert(User user) {
        // Reuse official broadcasts fetch, limit 1
        List<Post> officialBroadcasts = postRepo.findAllOfficialBroadcasts(PostStatus.ACTIVE, PageRequest.of(0, 1));
        if (officialBroadcasts.isEmpty()) {
            return null;
        }
        return postService.convertToPostResponse(officialBroadcasts.get(0), user);
    }
}
