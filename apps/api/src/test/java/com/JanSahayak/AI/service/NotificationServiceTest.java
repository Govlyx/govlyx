package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Notification;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import java.util.List;
import com.JanSahayak.AI.repository.CommentRepo;
import com.JanSahayak.AI.repository.NotificationRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Collections;
import java.util.Optional;
import com.JanSahayak.AI.dto.NotificationSummaryDto;
import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.dto.NotificationDto;
import com.JanSahayak.AI.enums.NotificationType;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepo notificationRepository;

    @Mock
    private UserRepo userRepository;

    @Mock
    private SocialPostRepo socialPostRepo;

    @Mock
    private CommentRepo commentRepo;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private WebPushService webPushService;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private NotificationService notificationService;

    private User targetUser;
    private User actionUser;
    private SocialPost socialPost;
    private Comment comment;

    @BeforeEach
    void setUp() {
        targetUser = new User();
        targetUser.setId(1L);
        targetUser.setUsername("targetUser");

        actionUser = new User();
        actionUser.setId(2L);
        actionUser.setUsername("actionUser");

        socialPost = new SocialPost();
        socialPost.setId(100L);
        socialPost.setUser(targetUser);

        comment = new Comment();
        comment.setId(200L);
        comment.setText("Test Comment");
        comment.setUser(actionUser);
        comment.setSocialPost(socialPost);
    }

    @Test
    void testNotifySocialPostLiked_ReFetchesEntities() {
        // Arrange
        when(socialPostRepo.findById(100L)).thenReturn(Optional.of(socialPost));
        when(userRepository.findById(2L)).thenReturn(Optional.of(actionUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(targetUser));
        when(notificationRepository.findByUserAndReferenceIdAndReferenceType(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        notificationService.notifySocialPostLiked(100L, 2L);

        // Assert
        verify(socialPostRepo, times(1)).findById(100L);
        verify(userRepository, times(2)).findById(2L);
        verify(userRepository, times(1)).findById(1L);
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void testNotifySocialPostCommented_ReFetchesEntities() {
        // Arrange
        when(socialPostRepo.findById(100L)).thenReturn(Optional.of(socialPost));
        when(commentRepo.findById(200L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(2L)).thenReturn(Optional.of(actionUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(targetUser));
        when(notificationRepository.findByUserAndReferenceIdAndReferenceType(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        notificationService.notifySocialPostCommented(100L, 200L, 2L);

        // Assert
        verify(socialPostRepo, times(1)).findById(100L);
        verify(commentRepo, times(1)).findById(200L);
        verify(userRepository, times(2)).findById(2L);
        verify(userRepository, times(1)).findById(1L);
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void testNotifyCommunityRoleChanged_CreatesNotificationAndSendsPush() throws Exception {
        // Arrange
        com.JanSahayak.AI.model.Community community = new com.JanSahayak.AI.model.Community();
        community.setId(10L);
        community.setName("Test Community");
        community.setSlug("test-community");

        when(userRepository.findById(1L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(actionUser));
        
        Notification savedNotification = new Notification();
        savedNotification.setId(1000L);
        savedNotification.setUser(targetUser);
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

        // Act
        notificationService.notifyCommunityRoleChanged(
                targetUser, community, 
                com.JanSahayak.AI.model.CommunityMember.MemberRole.MEMBER, 
                com.JanSahayak.AI.model.CommunityMember.MemberRole.MODERATOR, 
                actionUser
        );

        // Assert
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(webPushService, times(1)).sendPushNotification(eq(targetUser), any());
    }

    @Test
    void testDeleteNotificationsForPost_SocialPost() {
        // Act
        notificationService.deleteNotificationsForPost(100L, true);

        // Assert
        verify(notificationRepository, times(1)).deleteByReferenceIdAndTypes(eq(100L), eq(List.of("SOCIAL_POST", "COMMENT")));
    }

    @Test
    void testDeleteNotificationsForPost_StandardPost() {
        // Act
        notificationService.deleteNotificationsForPost(200L, false);

        // Assert
        verify(notificationRepository, times(1)).deleteByReferenceIdAndTypes(eq(200L), eq(List.of("POST", "BROADCAST_POST", "COMMENT")));
    }

    @Test
    void testNotifyCommentReplied_SetsCorrectReferenceType_ForPost() {
        // Arrange
        Post post = new Post();
        post.setId(300L);
        Comment originalComment = new Comment();
        originalComment.setId(400L);
        originalComment.setPost(post);
        originalComment.setUser(targetUser);

        Comment reply = new Comment();
        reply.setId(401L);
        reply.setText("Reply");

        when(userRepository.findById(1L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(actionUser));
        
        // Act
        notificationService.notifyCommentReplied(originalComment, reply, actionUser);

        // Assert
        verify(notificationRepository, times(1)).save(argThat(n -> 
            n.getReferenceType().equals("POST") && n.getReferenceId().equals(300L)
        ));
    }

    @Test
    void testNotifyCommentReplied_SetsCorrectReferenceType_ForSocialPost() {
        // Arrange
        Comment originalComment = new Comment();
        originalComment.setId(500L);
        originalComment.setSocialPost(socialPost);
        originalComment.setUser(targetUser);

        Comment reply = new Comment();
        reply.setId(501L);
        reply.setText("Reply");

        when(userRepository.findById(1L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(actionUser));
        
        // Act
        notificationService.notifyCommentReplied(originalComment, reply, actionUser);

        // Assert
        verify(notificationRepository, times(1)).save(argThat(n -> 
            n.getReferenceType().equals("SOCIAL_POST") && n.getReferenceId().equals(100L)
        ));
    }

    @Test
    void testGetUserNotifications_TabInvites_QueriesInviteTypes() {
        Notification notif = new Notification();
        notif.setId(1L);
        notif.setUser(targetUser);
        notif.setNotificationType(NotificationType.COMMUNITY_INVITE);
        notif.setTitle("Invite");
        notif.setMessage("You were invited");

        when(notificationRepository.findByUserAndNotificationTypeInOrderByCreatedAtDesc(
                eq(targetUser), eq(NotificationService.INVITE_TYPES), any()))
                .thenReturn(List.of(notif));

        PaginatedResponse<NotificationDto> res = notificationService.getUserNotifications(targetUser, null, 20, "invites");

        assertNotNull(res);
        assertEquals(1, res.getData().size());
        assertEquals(NotificationType.COMMUNITY_INVITE, res.getData().get(0).getNotificationType());
        verify(notificationRepository, times(1)).findByUserAndNotificationTypeInOrderByCreatedAtDesc(
                eq(targetUser), eq(NotificationService.INVITE_TYPES), any());
    }

    @Test
    void testGetUserNotifications_TabInteractions_QueriesInteractionTypes() {
        Notification notif = new Notification();
        notif.setId(2L);
        notif.setUser(targetUser);
        notif.setNotificationType(NotificationType.POST_LIKE);
        notif.setTitle("Like");
        notif.setMessage("Someone liked your post");

        when(notificationRepository.findByUserAndNotificationTypeInOrderByCreatedAtDesc(
                eq(targetUser), eq(NotificationService.INTERACTION_TYPES), any()))
                .thenReturn(List.of(notif));

        PaginatedResponse<NotificationDto> res = notificationService.getUserNotifications(targetUser, null, 20, "interactions");

        assertNotNull(res);
        assertEquals(1, res.getData().size());
        assertEquals(NotificationType.POST_LIKE, res.getData().get(0).getNotificationType());
        verify(notificationRepository, times(1)).findByUserAndNotificationTypeInOrderByCreatedAtDesc(
                eq(targetUser), eq(NotificationService.INTERACTION_TYPES), any());
    }

    @Test
    void testGetUserNotifications_TabUnread_QueriesUnread() {
        Notification notif = new Notification();
        notif.setId(3L);
        notif.setUser(targetUser);
        notif.setNotificationType(NotificationType.SYSTEM_ANNOUNCEMENT);
        notif.setIsRead(false);
        notif.setTitle("Announcement");
        notif.setMessage("System message");

        when(notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(eq(targetUser), any()))
                .thenReturn(List.of(notif));

        PaginatedResponse<NotificationDto> res = notificationService.getUserNotifications(targetUser, null, 20, "unread");

        assertNotNull(res);
        assertEquals(1, res.getData().size());
        verify(notificationRepository, times(1)).findByUserAndIsReadFalseOrderByCreatedAtDesc(eq(targetUser), any());
    }

    @Test
    void testGetNotificationSummaryCounts_ReturnsAggregatedCounts() {
        NotificationSummaryDto expected = new NotificationSummaryDto(50L, 10L, 5L, 15L);
        when(notificationRepository.getNotificationSummaryCounts(
                eq(targetUser), eq(NotificationService.INVITE_TYPES), eq(NotificationService.INTERACTION_TYPES)))
                .thenReturn(expected);

        NotificationSummaryDto result = notificationService.getNotificationSummaryCounts(targetUser);

        assertNotNull(result);
        assertEquals(50L, result.getAll());
        assertEquals(10L, result.getUnread());
        assertEquals(5L, result.getInvites());
        assertEquals(15L, result.getInteractions());
    }

    @Test
    void testGetNotificationSummaryCounts_NullUser_ReturnsZeros() {
        NotificationSummaryDto result = notificationService.getNotificationSummaryCounts(null);

        assertNotNull(result);
        assertEquals(0L, result.getAll());
        assertEquals(0L, result.getUnread());
        assertEquals(0L, result.getInvites());
        assertEquals(0L, result.getInteractions());
        verifyNoInteractions(notificationRepository);
    }
}