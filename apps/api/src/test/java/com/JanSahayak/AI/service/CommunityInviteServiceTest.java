package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.CommunityDto.CommunityInviteDto.InviteResponse;
import com.JanSahayak.AI.dto.CommunityDto.CommunityInviteDto.SendInviteRequest;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.CommunityInvite;
import com.JanSahayak.AI.model.CommunityMember;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommunityInviteRepo;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommunityInviteServiceTest {

    @Mock
    private CommunityInviteRepo inviteRepo;
    @Mock
    private CommunityRepo communityRepo;
    @Mock
    private CommunityMemberRepo memberRepo;
    @Mock
    private UserRepo userRepo;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CommunityInviteService inviteService;

    private User adminUser;
    private User inviteeUser;
    private Community publicCommunity;
    private CommunityMember adminMember;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inviteService, "frontendBaseUrl", "http://localhost:3000");

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");

        inviteeUser = new User();
        inviteeUser.setId(2L);
        inviteeUser.setUsername("guest");

        publicCommunity = new Community();
        publicCommunity.setId(10L);
        publicCommunity.setPrivacy(Community.CommunityPrivacy.PUBLIC);

        adminMember = new CommunityMember();
        adminMember.setUser(adminUser);
        adminMember.setCommunity(publicCommunity);
        adminMember.setMemberRole(CommunityMember.MemberRole.ADMIN);
        adminMember.setIsActive(true);
    }

    @Test
    void testSendInvite_PublicCommunity_Success() {
        // Arrange
        Long communityId = publicCommunity.getId();
        Long requesterId = adminUser.getId();
        
        SendInviteRequest req = new SendInviteRequest();
        req.setInviteeId(inviteeUser.getId());

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(publicCommunity));
        when(memberRepo.findByCommunityIdAndUserId(communityId, requesterId)).thenReturn(Optional.of(adminMember));
        when(inviteRepo.findPendingByCommunityIdCursor(eq(communityId), eq(null), any())).thenReturn(Collections.emptyList());
        when(userRepo.findById(inviteeUser.getId())).thenReturn(Optional.of(inviteeUser));
        when(memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(communityId, inviteeUser.getId())).thenReturn(false);
        when(inviteRepo.existsPendingInviteForUser(communityId, inviteeUser.getId())).thenReturn(false);
        when(userRepo.findById(requesterId)).thenReturn(Optional.of(adminUser));
        
        // Mock the save to just return the passed invite
        when(inviteRepo.save(any(CommunityInvite.class))).thenAnswer(invocation -> {
            CommunityInvite invite = invocation.getArgument(0);
            invite.setId(100L);
            invite.setToken("mock-token-123");
            return invite;
        });

        // Act
        InviteResponse response = inviteService.sendInvite(communityId, requesterId, req);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("mock-token-123", response.getToken());
        assertTrue(response.isSingleUse());
        
        // Verify notification is sent for a public community invite
        verify(notificationService, times(1)).notifyCommunityInvite(any(CommunityInvite.class));
    }
    @Test
    void testSendInvite_Moderator_Success() {
        // Arrange
        Long communityId = publicCommunity.getId();
        Long requesterId = adminUser.getId();
        
        SendInviteRequest req = new SendInviteRequest();
        req.setInviteeId(inviteeUser.getId());

        CommunityMember moderatorMember = new CommunityMember();
        moderatorMember.setUser(adminUser);
        moderatorMember.setCommunity(publicCommunity);
        moderatorMember.setMemberRole(CommunityMember.MemberRole.MODERATOR);
        moderatorMember.setIsActive(true);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(publicCommunity));
        when(memberRepo.findByCommunityIdAndUserId(communityId, requesterId)).thenReturn(Optional.of(moderatorMember));
        when(inviteRepo.findPendingByCommunityIdCursor(eq(communityId), eq(null), any())).thenReturn(Collections.emptyList());
        when(userRepo.findById(inviteeUser.getId())).thenReturn(Optional.of(inviteeUser));
        when(memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(communityId, inviteeUser.getId())).thenReturn(false);
        when(inviteRepo.existsPendingInviteForUser(communityId, inviteeUser.getId())).thenReturn(false);
        when(userRepo.findById(requesterId)).thenReturn(Optional.of(adminUser));
        
        when(inviteRepo.save(any(CommunityInvite.class))).thenAnswer(invocation -> {
            CommunityInvite invite = invocation.getArgument(0);
            invite.setId(101L);
            invite.setToken("mock-token-456");
            return invite;
        });

        // Act
        InviteResponse response = inviteService.sendInvite(communityId, requesterId, req);

        // Assert
        assertNotNull(response);
        assertEquals(101L, response.getId());
    }

    @Test
    void testSendInvite_Member_AccessDenied() {
        // Arrange
        Long communityId = publicCommunity.getId();
        Long requesterId = adminUser.getId();
        
        SendInviteRequest req = new SendInviteRequest();
        req.setInviteeId(inviteeUser.getId());

        CommunityMember normalMember = new CommunityMember();
        normalMember.setUser(adminUser);
        normalMember.setCommunity(publicCommunity);
        normalMember.setMemberRole(CommunityMember.MemberRole.MEMBER);
        normalMember.setIsActive(true);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(publicCommunity));
        when(memberRepo.findByCommunityIdAndUserId(communityId, requesterId)).thenReturn(Optional.of(normalMember));

        // Act & Assert
        Exception exception = assertThrows(SecurityException.class, () -> {
            inviteService.sendInvite(communityId, requesterId, req);
        });
        
        assertTrue(exception.getMessage().contains("Only community staff (admins, owners, or moderators) can manage invites."));
    }
}
