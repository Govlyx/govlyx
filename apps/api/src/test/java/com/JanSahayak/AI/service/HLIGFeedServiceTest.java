package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.FeedScope;
import com.JanSahayak.AI.enums.FeedSort;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HLIGFeedServiceTest {

    @Mock
    private SocialPostRepo postRepo;

    @Mock
    private InterestProfileService interestService;

    @Mock
    private HLIGScorer scorer;

    @Mock
    private TopicExtractor topicExtractor;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private HLIGFeedService hligFeedService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
    }

    @Test
    void testGetBrowseFeed_Following_Pagination_NoAbsoluteFallback() {
        // Arrange
        // Simulate fetchBrowsePool returning empty for FOLLOWING
        when(postRepo.findPostsFromUserCommunities(eq(user.getId()), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        
        // Cold fallback in fetchBrowsePool
        when(postRepo.findAllActivePostsForFeed(any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        
        // Act
        // Pass lastPostId = 100L (pagination request)
        List<SocialPost> result = hligFeedService.getBrowseFeed(user, FeedScope.FOLLOWING, FeedSort.HOT, 100L, 20);

        // Assert
        assertTrue(result.isEmpty());
        // Verify absolute fallback was NOT triggered. absoluteFallback calls findHotActivePostsForFeed.
        verify(postRepo, never()).findHotActivePostsForFeed(any(), any(Pageable.class));
    }
    @Test
    void testGetBrowseFeed_FiltersOutNeighborhoodQuestions() {
        // Arrange
        SocialPost validPost = new SocialPost();
        validPost.setId(1L);
        validPost.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);
        validPost.setCategory(com.JanSahayak.AI.enums.SocialPostCategory.GENERAL);

        SocialPost qaPost = new SocialPost();
        qaPost.setId(2L);
        qaPost.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);
        qaPost.setCategory(com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION);

        // When finding posts for FOLLOWING, return both valid and QA post
        when(postRepo.findPostsFromUserCommunities(eq(user.getId()), any(Pageable.class)))
                .thenReturn(List.of(validPost, qaPost));

        // Act
        List<SocialPost> result = hligFeedService.getBrowseFeed(user, FeedScope.FOLLOWING, FeedSort.NEW, null, 20);

        // Assert
        // The QA post should be filtered out in the memory pool
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(1L, result.get(0).getId());
    }

    @Test
    void testGetBrowseFeed_HotSort_SparsePlatform_UltimateFallback() {
        // Arrange
        SocialPost oldPost = new SocialPost();
        oldPost.setId(10L);
        oldPost.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);
        // Set creation date to 10 days ago (older than the 7-day window)
        oldPost.setCreatedAt(new java.util.Date(System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000));
        
        // Mock findHotActivePostsForFeed (platform widening) to return this old post
        when(postRepo.findHotActivePostsForFeed(any(), any(Pageable.class)))
                .thenReturn(List.of(oldPost));

        // Act
        // Because findHotActivePostsForFeed returns oldPost, fetchBrowsePool includes it.
        // Then applyHotSort is called. The 72h and 7d windows will be empty.
        // Due to the sparse-platform fix, it will fall back to returning ALL active posts (oldPost).
        List<SocialPost> result = hligFeedService.getBrowseFeed(user, FeedScope.LOCATION, FeedSort.HOT, null, 20);

        // Assert
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size(), "Post should be returned due to ultimate fallback");
        org.junit.jupiter.api.Assertions.assertEquals(10L, result.get(0).getId());
    }

    @Test
    void testGetBrowseFeed_ForYou_SparsePlatform_AlwaysRelaxesToActive() {
        // Arrange
        when(interestService.getUserPhase(user.getId())).thenReturn(InterestProfileService.Phase.COLD);
        
        SocialPost ineligiblePost = new SocialPost();
        ineligiblePost.setId(20L);
        ineligiblePost.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);
        // Make it ineligible for recommendation by setting report count high
        ineligiblePost.setReportCount(10); 
        
        // Mock findNationalViralPosts to return this ineligible post so fetchCandidates picks it up
        when(postRepo.findNationalViralPosts(any(Pageable.class)))
                .thenReturn(List.of(ineligiblePost));
        
        // Act
        List<SocialPost> result = hligFeedService.getBrowseFeed(user, FeedScope.FOR_YOU, FeedSort.HOT, null, 20);
        
        // Assert
        // Before the fix, eligible.size() (0) < Math.min(20, 50) was the threshold.
        // Wait, Math.min(200, 50) = 50. So it would actually relax if < 50.
        // But with our change, it ALWAYS relaxes if < size (20).
        // It should relax to activeOnly and return the post.
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size(), "Should relax to ACTIVE-only and return the post");
        org.junit.jupiter.api.Assertions.assertEquals(20L, result.get(0).getId());
    }
}
