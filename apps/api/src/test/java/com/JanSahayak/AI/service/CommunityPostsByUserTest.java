package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.CommunityDto.CommunityPostResponse;
import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.PostLikeRepo;
import com.JanSahayak.AI.repository.SavedPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@Import(CommunityPostsByUserTest.Config.class)
public class CommunityPostsByUserTest {

    @TestConfiguration
    @EnableCaching
    @Import(CommunityService.class)
    static class Config {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("communities", "community-list", "community-membership");
        }
    }

    @Autowired
    private CommunityService communityService;

    @MockBean private CommunityRepo communityRepo;
    @MockBean private CommunityMemberRepo communityMemberRepo;
    @MockBean private SocialPostRepo socialPostRepo;
    @MockBean private PostLikeRepo postLikeRepo;
    @MockBean private com.JanSahayak.AI.repository.CommentRepo commentRepo;
    @MockBean private NotificationService notificationService;
    @MockBean private UserService userService;
    @MockBean private com.JanSahayak.AI.repository.CommunityJoinRequestRepo communityJoinRequestRepo;
    @MockBean private com.JanSahayak.AI.repository.UserRepo userRepo;
    @MockBean private SavedPostRepo savedPostRepo;
    @MockBean private CommunityHealthScoreService communityHealthScoreService;
    @MockBean private HyperlocalSeedService hyperlocalSeedService;
    @MockBean private CloudinaryStorageService cloudinaryStorageService;
    @MockBean private InterestProfileService interestProfileService;
    @MockBean private com.JanSahayak.AI.repository.PollRepository pollRepository;
    @MockBean private com.JanSahayak.AI.repository.PollVoteRepository pollVoteRepository;

    @MockBean private ApplicationEventPublisher eventPublisher;

    private Community publicCommunity;
    private Community privateCommunity;
    private User targetUser;
    private SocialPost mockPost;

    @BeforeEach
    void setUp() {
        targetUser = new User();
        targetUser.setId(10L);
        targetUser.setUsername("targetUser");

        publicCommunity = new Community();
        publicCommunity.setId(100L);
        publicCommunity.setName("Public Comm");
        publicCommunity.setPrivacy(Community.CommunityPrivacy.PUBLIC);
        publicCommunity.setStatus(Community.CommunityStatus.ACTIVE);

        privateCommunity = new Community();
        privateCommunity.setId(200L);
        privateCommunity.setName("Private Comm");
        privateCommunity.setPrivacy(Community.CommunityPrivacy.PRIVATE);
        privateCommunity.setStatus(Community.CommunityStatus.ACTIVE);

        mockPost = new SocialPost();
        mockPost.setId(1L);
        mockPost.setContent("Test Post");
        mockPost.setUser(targetUser);
        mockPost.setCommunity(publicCommunity);
        mockPost.setCreatedAt(new Date());
    }

    @Test
    void testGetCommunityPostsByUser_PublicCommunity() {
        when(communityRepo.findById(100L)).thenReturn(Optional.of(publicCommunity));
        when(socialPostRepo.findCommunityPostsByUserCursorAndStatuses(eq(100L), eq(10L), anyList(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(mockPost));
        when(userRepo.existsById(10L)).thenReturn(true);

        PaginatedResponse<CommunityPostResponse> response = 
                communityService.getCommunityPostsByUser(100L, 10L, 50L, null, 10);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        assertEquals("Test Post", response.getData().get(0).getContent());
        assertEquals(1L, response.getData().get(0).getId());

        // Verify that member repo wasn't checked because it's public
        verify(communityMemberRepo, never()).existsByCommunityIdAndUserIdAndIsActiveTrue(anyLong(), anyLong());
    }

    @Test
    void testGetCommunityPostsByUser_PrivateCommunity_NonMember() {
        when(communityRepo.findById(200L)).thenReturn(Optional.of(privateCommunity));
        when(userRepo.existsById(10L)).thenReturn(true);
        
        // 50L is requester, is NOT a member
        when(communityMemberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(200L, 50L))
                .thenReturn(false);

        SecurityException ex = assertThrows(SecurityException.class, () -> 
                communityService.getCommunityPostsByUser(200L, 10L, 50L, null, 10)
        );
        assertEquals("You must be a member to view this community's posts.", ex.getMessage());
    }

    @Test
    void testGetCommunityPostsByUser_PrivateCommunity_IsMember() {
        when(communityRepo.findById(200L)).thenReturn(Optional.of(privateCommunity));
        when(userRepo.existsById(10L)).thenReturn(true);
        
        // 50L is requester, IS a member
        when(communityMemberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(200L, 50L))
                .thenReturn(true);

        when(socialPostRepo.findCommunityPostsByUserCursorAndStatuses(eq(200L), eq(10L), anyList(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(mockPost));

        PaginatedResponse<CommunityPostResponse> response = 
                communityService.getCommunityPostsByUser(200L, 10L, 50L, null, 10);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
    }
}
