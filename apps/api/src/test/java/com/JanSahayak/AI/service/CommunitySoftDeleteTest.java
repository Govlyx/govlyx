package com.JanSahayak.AI.service;

import com.JanSahayak.AI.event.CommunityDeletedEvent;
import com.JanSahayak.AI.event.CommunityRevokedEvent;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommunityRepo;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@Import(CommunitySoftDeleteTest.Config.class)
public class CommunitySoftDeleteTest {

    @TestConfiguration
    @EnableCaching
    @Import(CommunityService.class)
    static class Config {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("communities", "community-list");
        }
    }

    @Autowired
    private CommunityService communityService;

    @MockBean private CommunityRepo communityRepo;
    @MockBean private com.JanSahayak.AI.repository.CommunityMemberRepo communityMemberRepo;
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
    @MockBean private InterestProfileService interestProfileService;
    @MockBean private ApplicationEventPublisher eventPublisher;
    @MockBean private com.JanSahayak.AI.repository.PollRepository pollRepository;
    @MockBean private com.JanSahayak.AI.repository.PollVoteRepository pollVoteRepository;

    @Test
    void deleteCommunity_ShouldSetStatusToDeleteAndScheduleDeletion() {
        // Arrange
        Long communityId = 1L;
        Long ownerId = 100L;
        Community community = new Community();
        community.setId(communityId);
        User owner = new User();
        owner.setId(ownerId);
        community.setOwner(owner);
        community.setStatus(Community.CommunityStatus.ACTIVE);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community));

        // Act
        communityService.deleteCommunity(communityId, ownerId);

        // Assert
        assertEquals(Community.CommunityStatus.DELETED, community.getStatus());
        assertNotNull(community.getScheduledDeletionAt());

        // scheduledDeletionAt must be ~1 day in the future (within a 5-second tolerance)
        long expectedMs = System.currentTimeMillis() + 1L * 24 * 60 * 60 * 1000;
        long actualMs   = community.getScheduledDeletionAt().getTime();
        assertTrue(community.getScheduledDeletionAt().after(new Date()), "scheduledDeletionAt must be in the future");
        assertTrue(Math.abs(actualMs - expectedMs) < 5_000,
                "scheduledDeletionAt should be ~1 day from now, but was: " + community.getScheduledDeletionAt());

        // Verify that the entity was saved
        verify(communityRepo, times(1)).save(community);
    }

    @Test
    void revokeDeletion_ShouldRestoreStatusAndClearDeletionSchedule() {
        // Arrange
        Long communityId = 1L;
        Long ownerId = 100L;
        Community community = new Community();
        community.setId(communityId);
        User owner = new User();
        owner.setId(ownerId);
        community.setOwner(owner);
        community.setStatus(Community.CommunityStatus.DELETED);
        community.setScheduledDeletionAt(new Date(System.currentTimeMillis() + 100000)); // Future date

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community));

        // Act
        communityService.revokeDeletion(communityId, ownerId);

        // Assert
        assertEquals(Community.CommunityStatus.ACTIVE, community.getStatus());
        assertNull(community.getScheduledDeletionAt());

        // Verify that the entity was saved
        verify(communityRepo, times(1)).save(community);
    }
    
    @Test
    void deleteCommunity_ShouldThrowSecurityExceptionIfNotOwner() {
        // Arrange
        Long communityId = 1L;
        Long ownerId = 100L;
        Long nonOwnerId = 999L;
        
        Community community = new Community();
        community.setId(communityId);
        User owner = new User();
        owner.setId(ownerId);
        community.setOwner(owner);
        
        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community));
        
        // Act & Assert
        assertThrows(SecurityException.class, () -> {
            communityService.deleteCommunity(communityId, nonOwnerId);
        });
        
        // Verify no save occurred
        verify(communityRepo, never()).save(any());
    }

    @Test
    void getOwnedCommunities_ShouldIncludeDeletedButExcludePermanentlyDeleted() {
        // Arrange
        Long ownerId = 100L;
        Community activeCommunity = new Community();
        activeCommunity.setId(1L);
        activeCommunity.setStatus(Community.CommunityStatus.ACTIVE);
        
        Community deletedCommunity = new Community();
        deletedCommunity.setId(2L);
        deletedCommunity.setStatus(Community.CommunityStatus.DELETED);
        
        // This simulates the repo returning ACTIVE and DELETED communities.
        // PERMANENTLY_DELETED communities would be excluded by the repository query.
        when(communityRepo.findByOwnerIdAndStatusNot(ownerId, Community.CommunityStatus.PERMANENTLY_DELETED))
                .thenReturn(java.util.Arrays.asList(activeCommunity, deletedCommunity));
                
        // Act
        var result = communityService.getOwnedCommunities(ownerId);
        
        // Assert
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("ACTIVE", result.get(0).getStatus());
        assertEquals(2L, result.get(1).getId());
        assertEquals("DELETED", result.get(1).getStatus());
        
        // Verify the exact status passed to the repository method
        verify(communityRepo).findByOwnerIdAndStatusNot(ownerId, Community.CommunityStatus.PERMANENTLY_DELETED);
    }

    @Test
    void revokeDeletion_ShouldThrowIfCommunityIsPermanentlyDeleted() {
        // Arrange — grace period has already expired
        Long communityId = 1L;
        Long ownerId = 100L;
        Community community = new Community();
        community.setId(communityId);
        User owner = new User();
        owner.setId(ownerId);
        community.setOwner(owner);
        community.setStatus(Community.CommunityStatus.PERMANENTLY_DELETED);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community));

        // Act & Assert — must NOT silently resurrect the community
        assertThrows(com.JanSahayak.AI.exception.ValidationException.class, () ->
                communityService.revokeDeletion(communityId, ownerId));

        verify(communityRepo, never()).save(any());
    }

    @Test
    void revokeDeletion_ShouldThrowIfCommunityIsNotPendingDeletion() {
        // Arrange — community is ACTIVE, not in a DELETED grace-period state
        Long communityId = 1L;
        Long ownerId = 100L;
        Community community = new Community();
        community.setId(communityId);
        User owner = new User();
        owner.setId(ownerId);
        community.setOwner(owner);
        community.setStatus(Community.CommunityStatus.ACTIVE);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community));

        // Act & Assert
        assertThrows(com.JanSahayak.AI.exception.ValidationException.class, () ->
                communityService.revokeDeletion(communityId, ownerId));

        verify(communityRepo, never()).save(any());
    }
}
