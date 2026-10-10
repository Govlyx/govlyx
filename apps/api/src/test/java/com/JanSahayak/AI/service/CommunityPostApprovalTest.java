package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommunityPostApprovalTest {

    @Mock
    private CommunityMemberRepo memberRepo;

    @Mock
    private CommunityRepo communityRepo;

    @Mock
    private SocialPostRepo socialPostRepo;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CommunityService communityService;

    private User moderator;
    private User normalUser;
    private User author;
    private Community community;
    private SocialPost post;

    private final Long COMMUNITY_ID = 100L;
    private final Long POST_ID = 500L;
    private final Long MODERATOR_ID = 1L;
    private final Long AUTHOR_ID = 2L;

    @BeforeEach
    void setUp() {
        // Field injection for NotificationService since it's @Lazy @Autowired
        ReflectionTestUtils.setField(communityService, "notificationService", notificationService);

        moderator = new User();
        moderator.setId(MODERATOR_ID);
        moderator.setUsername("moderator");

        normalUser = new User();
        normalUser.setId(3L);
        normalUser.setUsername("normalUser");

        author = new User();
        author.setId(AUTHOR_ID);
        author.setUsername("author");

        community = new Community();
        community.setId(COMMUNITY_ID);
        community.setName("Test Community");
        community.setRequirePostApproval(true);

        post = new SocialPost();
        post.setId(POST_ID);
        post.setCommunity(community);
        post.setUser(author);
        post.setStatus(PostStatus.PENDING_APPROVAL);
    }

    @Test
    void testApproveCommunityPost_Success() {
        when(memberRepo.isModeratorOrAbove(COMMUNITY_ID, MODERATOR_ID)).thenReturn(true);
        when(socialPostRepo.findById(POST_ID)).thenReturn(Optional.of(post));
        when(communityRepo.existsById(COMMUNITY_ID)).thenReturn(true);

        communityService.approveCommunityPost(COMMUNITY_ID, POST_ID, moderator);

        assertEquals(PostStatus.ACTIVE, post.getStatus());
        verify(socialPostRepo).save(post);
        verify(notificationService).notifyPostApproved(post, "Test Community", moderator);
    }

    @Test
    void testRejectCommunityPost_Success() {
        when(memberRepo.isModeratorOrAbove(COMMUNITY_ID, MODERATOR_ID)).thenReturn(true);
        when(socialPostRepo.findById(POST_ID)).thenReturn(Optional.of(post));

        communityService.rejectCommunityPost(COMMUNITY_ID, POST_ID, moderator);

        assertEquals(PostStatus.REJECTED, post.getStatus());
        verify(socialPostRepo).save(post);
        verify(notificationService).notifyPostRejected(post, "Test Community", moderator);
    }

    @Test
    void testApproveCommunityPost_NotModerator_ThrowsException() {
        when(memberRepo.isModeratorOrAbove(COMMUNITY_ID, normalUser.getId())).thenReturn(false);

        SecurityException ex = assertThrows(SecurityException.class, () -> {
            communityService.approveCommunityPost(COMMUNITY_ID, POST_ID, normalUser);
        });
        
        assertEquals("Only community moderators can approve posts", ex.getMessage());
        verify(socialPostRepo, never()).save(any());
        verify(notificationService, never()).notifyPostApproved(any(), any(), any());
    }

    @Test
    void testApproveCommunityPost_PostNotPending_ThrowsException() {
        post.setStatus(PostStatus.ACTIVE);
        when(memberRepo.isModeratorOrAbove(COMMUNITY_ID, MODERATOR_ID)).thenReturn(true);
        when(socialPostRepo.findById(POST_ID)).thenReturn(Optional.of(post));

        ValidationException ex = assertThrows(ValidationException.class, () -> {
            communityService.approveCommunityPost(COMMUNITY_ID, POST_ID, moderator);
        });

        assertEquals("Post is not pending approval", ex.getMessage());
        verify(socialPostRepo, never()).save(any());
    }

    @Test
    void testRejectCommunityPost_PostDoesNotBelongToCommunity_ThrowsException() {
        Community otherCommunity = new Community();
        otherCommunity.setId(999L);
        post.setCommunity(otherCommunity); // Different community ID
        when(memberRepo.isModeratorOrAbove(COMMUNITY_ID, MODERATOR_ID)).thenReturn(true);
        when(socialPostRepo.findById(POST_ID)).thenReturn(Optional.of(post));

        ValidationException ex = assertThrows(ValidationException.class, () -> {
            communityService.rejectCommunityPost(COMMUNITY_ID, POST_ID, moderator);
        });

        assertEquals("Post does not belong to this community", ex.getMessage());
        verify(socialPostRepo, never()).save(any());
    }
}
