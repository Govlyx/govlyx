package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.CommunityDto.CommunityDetailResponse;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommunityJoinRequestRepo;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import com.JanSahayak.AI.repository.CommunityRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommunityPrivacySecurityTest {

    @Mock
    private CommunityRepo communityRepo;

    @Mock
    private CommunityMemberRepo memberRepo;

    @Mock
    private CommunityJoinRequestRepo joinRequestRepo;

    @InjectMocks
    private CommunityService communityService;

    private User memberUser;
    
    private Community publicCommunity;
    private Community privateCommunity;
    private Community secretCommunity;

    @BeforeEach
    void setUp() {
        memberUser = new User();
        memberUser.setId(1L);

        publicCommunity = new Community();
        publicCommunity.setId(10L);
        publicCommunity.setPrivacy(Community.CommunityPrivacy.PUBLIC);
        publicCommunity.setSlug("public-comm");

        privateCommunity = new Community();
        privateCommunity.setId(20L);
        privateCommunity.setPrivacy(Community.CommunityPrivacy.PRIVATE);
        privateCommunity.setSlug("private-comm");

        secretCommunity = new Community();
        secretCommunity.setId(30L);
        secretCommunity.setPrivacy(Community.CommunityPrivacy.SECRET);
        secretCommunity.setSlug("secret-comm");
    }

    @Test
    void testGetCommunityDetail_PublicCommunity_GuestAccess_Success() {
        when(communityRepo.findBySlug("public-comm")).thenReturn(Optional.of(publicCommunity));
        
        CommunityDetailResponse response = communityService.getCommunityDetail("public-comm", null);
        
        assertNotNull(response);
        assertEquals("PUBLIC", response.getPrivacy());
    }

    @Test
    void testGetCommunityDetail_PrivateCommunity_GuestAccess_Success() {
        // Guests CAN view metadata of PRIVATE communities
        when(communityRepo.findBySlug("private-comm")).thenReturn(Optional.of(privateCommunity));
        
        CommunityDetailResponse response = communityService.getCommunityDetail("private-comm", null);
        
        assertNotNull(response);
        assertEquals("PRIVATE", response.getPrivacy());
    }

    @Test
    void testGetCommunityDetail_SecretCommunity_GuestAccess_ThrowsSecurityException() {
        // Guests CANNOT view metadata of SECRET communities
        when(communityRepo.findBySlug("secret-comm")).thenReturn(Optional.of(secretCommunity));
        
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            communityService.getCommunityDetail("secret-comm", null);
        });
        
        assertTrue(exception.getMessage().contains("secret community"));
    }

    @Test
    void testGetCommunityDetail_SecretCommunity_MemberAccess_Success() {
        when(communityRepo.findBySlug("secret-comm")).thenReturn(Optional.of(secretCommunity));
        when(memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(secretCommunity.getId(), memberUser.getId())).thenReturn(true);
        
        CommunityDetailResponse response = communityService.getCommunityDetail("secret-comm", memberUser.getId());
        
        assertNotNull(response);
        assertEquals("SECRET", response.getPrivacy());
    }
}
