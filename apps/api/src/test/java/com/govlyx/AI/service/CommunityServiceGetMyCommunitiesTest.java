package com.Govlyx.AI.service;

import com.Govlyx.AI.config.Constant;
import com.Govlyx.AI.dto.CommunityDto;
import com.Govlyx.AI.dto.PaginatedResponse;
import com.Govlyx.AI.model.Community;
import com.Govlyx.AI.model.CommunityMember;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.CommunityMemberRepo;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@Import(CommunityServiceGetMyCommunitiesTest.Config.class)
public class CommunityServiceGetMyCommunitiesTest {

    @TestConfiguration
    @EnableCaching
    @Import(CommunityService.class)
    static class Config {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(Constant.CACHE_COMMUNITY_LIST, "community-membership", "communities");
        }
    }

    @Autowired
    private CommunityService communityService;

    @MockBean
    private CommunityMemberRepo communityMemberRepo;
    
    // Mocking other dependencies of CommunityService to satisfy the application context
    @MockBean private com.Govlyx.AI.repository.CommunityRepo communityRepo;
    @MockBean private com.Govlyx.AI.repository.SocialPostRepo socialPostRepo;
    @MockBean private com.Govlyx.AI.repository.PostLikeRepo postLikeRepo;
    @MockBean private com.Govlyx.AI.repository.CommentRepo commentRepo;
    @MockBean private NotificationService notificationService;
    @MockBean private UserService userService;
    @MockBean private com.Govlyx.AI.repository.CommunityJoinRequestRepo communityJoinRequestRepo;
    @MockBean private com.Govlyx.AI.repository.UserRepo userRepo;
    @MockBean private com.Govlyx.AI.repository.SavedPostRepo savedPostRepo;
    @MockBean private CommunityHealthScoreService communityHealthScoreService;
    @MockBean private HyperlocalSeedService hyperlocalSeedService;
    @MockBean private CloudinaryStorageService cloudinaryStorageService;
    @MockBean private com.Govlyx.AI.repository.PollRepository pollRepository;
    @MockBean private com.Govlyx.AI.repository.PollVoteRepository pollVoteRepository;

    @Test
    void getMyCommunities_ShouldCorrectlyMapIsModerator() {
        // Arrange
        Long userId = 1L;
        User user = new User();
        user.setId(userId);

        Community community1 = new Community();
        community1.setId(101L);
        community1.setName("Community 1");
        community1.setCreatedAt(new Date());

        Community community2 = new Community();
        community2.setId(102L);
        community2.setName("Community 2");
        community2.setCreatedAt(new Date());

        CommunityMember member1 = new CommunityMember();
        member1.setCommunity(community1);
        member1.setUser(user);
        member1.setMemberRole(CommunityMember.MemberRole.MODERATOR); // isModerator should be true

        CommunityMember member2 = new CommunityMember();
        member2.setCommunity(community2);
        member2.setUser(user);
        member2.setMemberRole(CommunityMember.MemberRole.MEMBER); // isModerator should be false

        List<CommunityMember> mockMembers = Arrays.asList(member1, member2);

        when(communityMemberRepo.findUserCommunitiesCursor(anyLong(), any(), any(Pageable.class)))
                .thenReturn(mockMembers);

        // Act
        PaginatedResponse<CommunityDto.CommunitySummaryResponse> response = 
                communityService.getMyCommunities(userId, null, 10);

        // Assert
        assertEquals(2, response.getData().size());
        
        CommunityDto.CommunitySummaryResponse res1 = response.getData().stream()
                .filter(c -> c.getId().equals(101L))
                .findFirst().orElseThrow();
        assertTrue(res1.isModerator(), "Expected isModerator to be true for MODERATOR role");

        CommunityDto.CommunitySummaryResponse res2 = response.getData().stream()
                .filter(c -> c.getId().equals(102L))
                .findFirst().orElseThrow();
        assertFalse(res2.isModerator(), "Expected isModerator to be false for MEMBER role");
    }
}
