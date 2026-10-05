package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.CommunityMember;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@Import(CommunityServiceMembershipTest.Config.class)
public class CommunityServiceMembershipTest {

    @TestConfiguration
    @EnableCaching
    @Import(CommunityService.class)
    static class Config {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("community-membership");
        }
    }

    @Autowired
    private CommunityService communityService;

    @MockBean
    private CommunityMemberRepo communityMemberRepo;
    
    // Mocking other dependencies of CommunityService to satisfy the application context
    @MockBean private com.JanSahayak.AI.repository.CommunityRepo communityRepo;
    @MockBean private com.JanSahayak.AI.repository.SocialPostRepo socialPostRepo;
    @MockBean private com.JanSahayak.AI.repository.PostLikeRepo postLikeRepo;
    @MockBean private com.JanSahayak.AI.repository.CommentRepo commentRepo;
    @MockBean private NotificationService notificationService;
    @MockBean private UserService userService;
    @MockBean private com.JanSahayak.AI.repository.CommunityJoinRequestRepo communityJoinRequestRepo;
    @MockBean private com.JanSahayak.AI.repository.UserRepo userRepo;
    @MockBean private com.JanSahayak.AI.repository.SavedPostRepo savedPostRepo;
    @MockBean private CommunityHealthScoreService communityHealthScoreService;
    @MockBean private HyperlocalSeedService hyperlocalSeedService;
    @MockBean private CloudinaryStorageService cloudinaryStorageService;
    @MockBean private com.JanSahayak.AI.repository.PollRepository pollRepository;
    @MockBean private com.JanSahayak.AI.repository.PollVoteRepository pollVoteRepository;

    @Test
    void isMember_ShouldUseCacheAndPreventDatabaseHits() {
        // Arrange
        Long communityId = 100L;
        Long userId = 1L;
        CommunityMember member = new CommunityMember();
        member.setMemberRole(CommunityMember.MemberRole.MEMBER);

        when(communityMemberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(communityId, userId))
                .thenReturn(true);

        // Act 1: First call should hit the database
        boolean result1 = communityService.isMember(communityId, userId);
        
        // Act 2: Second call should hit the cache
        boolean result2 = communityService.isMember(communityId, userId);

        // Assert
        assertTrue(result1);
        assertTrue(result2);

        // Verify that the repository was only called ONCE, proving the cache intercepted the second call
        verify(communityMemberRepo, times(1)).existsByCommunityIdAndUserIdAndIsActiveTrue(communityId, userId);
    }

    @Test
    void isModeratorOrAbove_ShouldUseCacheAndPreventDatabaseHits() {
        // Arrange
        Long communityId = 200L;
        Long userId = 2L;
        CommunityMember mod = new CommunityMember();
        mod.setMemberRole(CommunityMember.MemberRole.MODERATOR);

        when(communityMemberRepo.isModeratorOrAbove(communityId, userId))
                .thenReturn(true);

        // Act 1: First call should hit the database
        boolean result1 = communityService.isModeratorOrAbove(communityId, userId);
        
        // Act 2: Second call should hit the cache
        boolean result2 = communityService.isModeratorOrAbove(communityId, userId);

        // Assert
        assertTrue(result1);
        assertTrue(result2);

        // Verify that the repository was only called ONCE
        verify(communityMemberRepo, times(1)).isModeratorOrAbove(communityId, userId);
    }
}
