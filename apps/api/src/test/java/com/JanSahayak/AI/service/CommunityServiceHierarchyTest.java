package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.CommunityMember;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import com.JanSahayak.AI.repository.CommunityRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(SpringExtension.class)
@Import(CommunityServiceHierarchyTest.Config.class)
public class CommunityServiceHierarchyTest {

    @TestConfiguration
    @Import(CommunityService.class)
    static class Config {
    }

    @Autowired
    private CommunityService communityService;

    @MockBean private CommunityMemberRepo communityMemberRepo;
    @MockBean private CommunityRepo communityRepo;
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
    @MockBean private org.springframework.cache.CacheManager cacheManager;
    @MockBean private com.JanSahayak.AI.repository.PollVoteRepository pollVoteRepository;

    @Test
    void testBanMember_ModeratorCannotBanAdmin() {
        // Arrange
        Long communityId = 1L;
        Long requesterId = 2L; // Moderator
        Long targetId = 3L;    // Admin

        Community community = new Community();
        community.setId(communityId);
        
        User owner = new User();
        owner.setId(99L);
        community.setOwner(owner);

        User requesterUser = new User();
        requesterUser.setId(requesterId);
        
        User targetUser = new User();
        targetUser.setId(targetId);

        CommunityMember requester = new CommunityMember();
        requester.setUser(requesterUser);
        requester.setMemberRole(CommunityMember.MemberRole.MODERATOR);
        requester.setIsActive(true);

        CommunityMember target = new CommunityMember();
        target.setUser(targetUser);
        target.setMemberRole(CommunityMember.MemberRole.ADMIN);
        target.setIsActive(true);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community));
        when(communityMemberRepo.findByCommunityIdAndUserId(communityId, requesterId))
                .thenReturn(Optional.of(requester));
        when(communityMemberRepo.findByCommunityIdAndUserId(communityId, targetId))
                .thenReturn(Optional.of(target));

        // Act & Assert
        Exception exception = assertThrows(SecurityException.class, () -> {
            communityService.banMember(communityId, targetId, requesterId, null);
        });

        assert(exception.getMessage().contains("You do not have permission to modify a member with an equal or higher role."));
    }
    
    @Test
    void testBanMember_ModeratorCannotBanModerator() {
        // Arrange
        Long communityId = 1L;
        Long requesterId = 2L; // Moderator
        Long targetId = 3L;    // Moderator

        Community community = new Community();
        community.setId(communityId);
        
        User owner = new User();
        owner.setId(99L);
        community.setOwner(owner);

        User requesterUser = new User();
        requesterUser.setId(requesterId);
        
        User targetUser = new User();
        targetUser.setId(targetId);

        CommunityMember requester = new CommunityMember();
        requester.setUser(requesterUser);
        requester.setMemberRole(CommunityMember.MemberRole.MODERATOR);
        requester.setIsActive(true);

        CommunityMember target = new CommunityMember();
        target.setUser(targetUser);
        target.setMemberRole(CommunityMember.MemberRole.MODERATOR);
        target.setIsActive(true);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community));
        when(communityMemberRepo.findByCommunityIdAndUserId(communityId, requesterId))
                .thenReturn(Optional.of(requester));
        when(communityMemberRepo.findByCommunityIdAndUserId(communityId, targetId))
                .thenReturn(Optional.of(target));

        // Act & Assert
        Exception exception = assertThrows(SecurityException.class, () -> {
            communityService.banMember(communityId, targetId, requesterId, null);
        });

        assert(exception.getMessage().contains("You do not have permission to modify a member with an equal or higher role."));
    }

    @Test
    void testBanMember_ModeratorCanBanMember() {
        // Arrange
        Long communityId = 1L;
        Long requesterId = 2L; // Moderator
        Long targetId = 3L;    // Member

        Community community = new Community();
        community.setId(communityId);
        
        User owner = new User();
        owner.setId(99L);
        community.setOwner(owner);

        User requesterUser = new User();
        requesterUser.setId(requesterId);
        
        User targetUser = new User();
        targetUser.setId(targetId);

        CommunityMember requester = new CommunityMember();
        requester.setUser(requesterUser);
        requester.setMemberRole(CommunityMember.MemberRole.MODERATOR);
        requester.setIsActive(true);

        CommunityMember target = new CommunityMember();
        target.setUser(targetUser);
        target.setMemberRole(CommunityMember.MemberRole.MEMBER);
        target.setIsActive(true);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community));
        when(communityMemberRepo.findByCommunityIdAndUserId(communityId, requesterId))
                .thenReturn(Optional.of(requester));
        when(communityMemberRepo.findByCommunityIdAndUserId(communityId, targetId))
                .thenReturn(Optional.of(target));

        // Act
        communityService.banMember(communityId, targetId, requesterId, null);

        // Assert
        verify(communityMemberRepo, times(1)).save(target);
        assert(target.getIsBanned() == true);
    }

    @Test
    void updateMemberRole_Success_TriggersNotification() {
        Long communityId = 1L;
        Long requesterId = 100L;
        Long targetId = 200L;

        Community community = new Community();
        community.setId(communityId);
        community.setName("Test Community");

        User requesterUser = new User();
        requesterUser.setId(requesterId);
        
        User targetUser = new User();
        targetUser.setId(targetId);

        CommunityMember requester = new CommunityMember();
        requester.setUser(requesterUser);
        requester.setMemberRole(CommunityMember.MemberRole.ADMIN);
        requester.setIsActive(true);

        CommunityMember target = new CommunityMember();
        target.setUser(targetUser);
        target.setMemberRole(CommunityMember.MemberRole.MEMBER);
        target.setIsActive(true);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community));
        when(communityMemberRepo.findByCommunityIdAndUserId(communityId, requesterId))
                .thenReturn(Optional.of(requester));
        when(communityMemberRepo.findByCommunityIdAndUserId(communityId, targetId))
                .thenReturn(Optional.of(target));
        when(userRepo.findById(requesterId)).thenReturn(Optional.of(requesterUser));

        com.JanSahayak.AI.dto.CommunityDto.UpdateMemberRoleRequest req = new com.JanSahayak.AI.dto.CommunityDto.UpdateMemberRoleRequest();
        req.setNewRole(CommunityMember.MemberRole.MODERATOR);

        // Act
        communityService.updateMemberRole(communityId, targetId, requesterId, req);

        // Assert
        verify(communityMemberRepo, times(1)).save(target);
        assert(target.getMemberRole() == CommunityMember.MemberRole.MODERATOR);
        
        verify(notificationService, times(1)).notifyCommunityRoleChanged(
                targetUser, community, CommunityMember.MemberRole.MEMBER, CommunityMember.MemberRole.MODERATOR, requesterUser
        );
    }
}