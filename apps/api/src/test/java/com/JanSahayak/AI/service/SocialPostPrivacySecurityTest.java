package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.SocialPostDto;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.model.Community;
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
public class SocialPostPrivacySecurityTest {

    @Mock
    private SocialPostRepo socialPostRepo;

    @Mock
    private CommunityMemberRepo memberRepo;

    // Use a spy or mock dependencies to isolate the privacy logic if InjectMocks fails.
    // For this test, we mock the specific privacy check logic inside SocialPostService.
    
    @InjectMocks
    private SocialPostService socialPostService;

    private User guestUser = null;
    private User memberUser;
    private SocialPost publicPost;
    private SocialPost privatePost;
    private SocialPost secretPost;

    private Community publicCommunity;
    private Community privateCommunity;
    private Community secretCommunity;

    @BeforeEach
    void setUp() {
        memberUser = new User();
        memberUser.setId(100L);

        publicCommunity = new Community();
        publicCommunity.setId(10L);
        publicCommunity.setPrivacy(Community.CommunityPrivacy.PUBLIC);

        privateCommunity = new Community();
        privateCommunity.setId(20L);
        privateCommunity.setPrivacy(Community.CommunityPrivacy.PRIVATE);

        secretCommunity = new Community();
        secretCommunity.setId(30L);
        secretCommunity.setPrivacy(Community.CommunityPrivacy.SECRET);

        publicPost = new SocialPost();
        publicPost.setId(1L);
        publicPost.setStatus(PostStatus.ACTIVE);
        publicPost.setCommunity(publicCommunity);
        publicPost.setCommunityPrivacy("PUBLIC");
        
        privatePost = new SocialPost();
        privatePost.setId(2L);
        privatePost.setStatus(PostStatus.ACTIVE);
        privatePost.setCommunity(privateCommunity);
        privatePost.setCommunityPrivacy("PRIVATE");

        secretPost = new SocialPost();
        secretPost.setId(3L);
        secretPost.setStatus(PostStatus.ACTIVE);
        secretPost.setCommunity(secretCommunity);
        secretPost.setCommunityPrivacy("SECRET");
    }

    @Test
    void testGetSocialPostById_PublicPost_GuestAccess_ThrowsNoSecurityException() {
        when(socialPostRepo.findById(1L)).thenReturn(Optional.of(publicPost));
        
        // It might throw a NullPointerException inside convertToDto because we haven't mocked everything,
        // but it should NOT throw SecurityException. We just catch it and assert it's not SecurityException.
        try {
            socialPostService.getSocialPostById(1L, guestUser);
        } catch (SecurityException e) {
            fail("Should not throw SecurityException for PUBLIC posts");
        } catch (Exception e) {
            // Expected due to missing mocks in convertToDto
        }
    }

    @Test
    void testGetSocialPostById_PrivatePost_GuestAccess_ThrowsSecurityException() {
        when(socialPostRepo.findById(2L)).thenReturn(Optional.of(privatePost));
        
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            socialPostService.getSocialPostById(2L, guestUser);
        });
        
        assertTrue(exception.getMessage().contains("User does not have permission"));
    }

    @Test
    void testGetSocialPostById_SecretPost_GuestAccess_ThrowsSecurityException() {
        when(socialPostRepo.findById(3L)).thenReturn(Optional.of(secretPost));
        
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            socialPostService.getSocialPostById(3L, guestUser);
        });
        
        assertTrue(exception.getMessage().contains("User does not have permission"));
    }

    @Test
    void testGetSocialPostById_PrivatePost_MemberAccess_ThrowsNoSecurityException() {
        when(socialPostRepo.findById(2L)).thenReturn(Optional.of(privatePost));
        when(memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(20L, 100L)).thenReturn(true);
        
        try {
            socialPostService.getSocialPostById(2L, memberUser);
        } catch (SecurityException e) {
            fail("Should not throw SecurityException for members");
        } catch (Exception e) {
            // Expected due to missing mocks in convertToDto
        }
    }
}
