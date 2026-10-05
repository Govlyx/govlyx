package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.CommunityMessageDto;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@Import(CommunityChatServiceTest.Config.class)
@DisplayName("CommunityChatService — processNewMessage")
public class CommunityChatServiceTest {

    @TestConfiguration
    @Import(CommunityChatService.class)
    static class Config {
    }

    @Autowired
    private CommunityChatService communityChatService;

    @MockBean private CommunityMessageRepo     communityMessageRepo;
    @MockBean private CommunityRepo            communityRepo;
    @MockBean private CommunityMemberRepo      communityMemberRepo;
    @MockBean private SocialPostRepo           socialPostRepo;
    @MockBean private UserRepo                 userRepo;
    @MockBean private SimpMessagingTemplate    messagingTemplate;
    @MockBean private ContentReportRepository  contentReportRepository;
    @MockBean private CommunityChatModerator   chatModerator;
    @MockBean private PlanEnforcementService   planEnforcementService;
    @MockBean private NotificationService      notificationService;
    @MockBean private CommunitySharedPostSnapshotRepo communitySharedPostSnapshotRepo;

    // ── Fixture IDs ───────────────────────────────────────────────────────────

    private static final Long COMMUNITY_ID       = 100L;
    private static final Long OTHER_COMMUNITY_ID = 999L;
    private static final Long SENDER_ID          = 10L;
    private static final Long AUTHOR_ID          = 11L;
    private static final Long POST_ID            = 50L;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private Community      community;
    private User           sender;
    private User           postAuthor;
    private CommunityMember activeMember;

    private SocialPost postInSameCommunity;
    private SocialPost postInOtherCommunity;
    private SocialPost postFromGlobalFeed;

    @BeforeEach
    void setUp() {
        sender = new User();
        sender.setId(SENDER_ID);
        sender.setUsername("sender_user");
        sender.setUsername("sender_user");

        postAuthor = new User();
        postAuthor.setId(AUTHOR_ID);
        postAuthor.setUsername("AuthorUser");

        community = new Community();
        community.setId(COMMUNITY_ID);
        community.setName("Test Mohalla");
        community.setIsGroupChatEnabled(true);
        community.setOwner(postAuthor); // sender is NOT the owner to avoid bypass

        activeMember = new CommunityMember();
        activeMember.setUser(sender);
        activeMember.setCommunity(community);
        activeMember.setMemberRole(CommunityMember.MemberRole.MEMBER);
        activeMember.setIsActive(true);
        activeMember.setIsMuted(false);
        activeMember.setIsBanned(false);

        // Post originally posted INSIDE this community
        postInSameCommunity = new SocialPost();
        postInSameCommunity.setId(POST_ID);
        postInSameCommunity.setContent("Post inside same community");
        postInSameCommunity.setCommunity(community);
        postInSameCommunity.setUser(postAuthor);

        // Post originally posted in a DIFFERENT community
        Community otherCommunity = new Community();
        otherCommunity.setId(OTHER_COMMUNITY_ID);
        postInOtherCommunity = new SocialPost();
        postInOtherCommunity.setId(501L);
        postInOtherCommunity.setContent("Post from another community");
        postInOtherCommunity.setCommunity(otherCommunity);
        postInOtherCommunity.setUser(postAuthor);

        // Post from the global feed (no community)
        postFromGlobalFeed = new SocialPost();
        postFromGlobalFeed.setId(502L);
        postFromGlobalFeed.setContent("Global feed post, no community");
        postFromGlobalFeed.setCommunity(null);
        postFromGlobalFeed.setUser(postAuthor);
    }

    @AfterEach
    void tearDown() {
        reset(communityMessageRepo, communityRepo, communityMemberRepo, socialPostRepo, userRepo, messagingTemplate, contentReportRepository, chatModerator, planEnforcementService);
    }

    // ── Helper: happy-path wiring ──────────────────────────────────────────────

    private void givenHappyPath() {
        when(communityRepo.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(communityMemberRepo.findByCommunityIdAndUserId(COMMUNITY_ID, SENDER_ID))
                .thenReturn(Optional.of(activeMember));
        when(chatModerator.scanMessage(anyString())).thenReturn(false);
        when(communityMessageRepo.save(any(CommunityMessage.class))).thenAnswer(inv -> {
            CommunityMessage m = inv.getArgument(0);
            if (m != null) m.setId(999L);
            return m;
        });
    }

    // =========================================================================
    // ── Nested: Original test — snapshot creation ─────────────────────────────
    // =========================================================================

    @Test
    @DisplayName("✅ Sharing a same-community post creates a proper snapshot (original test)")
    void testProcessNewMessage_SharePost_CreatesSnapshot() {
        when(communityRepo.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(communityMemberRepo.findByCommunityIdAndUserId(COMMUNITY_ID, SENDER_ID))
                .thenReturn(Optional.of(activeMember));
        when(chatModerator.scanMessage(anyString())).thenReturn(false);
        when(socialPostRepo.findById(POST_ID)).thenReturn(Optional.of(postInSameCommunity));
        when(communityMessageRepo.save(any(CommunityMessage.class))).thenAnswer(inv -> {
            CommunityMessage m = inv.getArgument(0);
            if (m != null) m.setId(999L);
            return m;
        });

        CommunityMessageDto result = communityChatService.processNewMessage(
                COMMUNITY_ID, SENDER_ID, "Optional comment", POST_ID, null);

        assertNotNull(result);
        assertEquals("SHARE_POST", result.getMessageType());

        ArgumentCaptor<CommunityMessage> captor = ArgumentCaptor.forClass(CommunityMessage.class);
        verify(communityMessageRepo).save(captor.capture());
        CommunityMessage saved = captor.getValue();

        assertEquals(CommunityMessage.MessageType.SHARE_POST, saved.getMessageType());
        assertNotNull(saved.getSharedPostSnapshot(), "Snapshot must be created");
        assertEquals(POST_ID, saved.getSharedPostSnapshot().getPostId());
        assertEquals("Post inside same community", saved.getSharedPostSnapshot().getContent());
        assertEquals("AuthorUser", saved.getSharedPostSnapshot().getAuthorUsername());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/community." + COMMUNITY_ID + ".messages"),
                any(CommunityMessageDto.class));
    }

    // =========================================================================
    // ── Nested: Post sharing — THE BUG FIX ───────────────────────────────────
    // =========================================================================

    @Nested
    @DisplayName("Sharing posts to chat — bug fix coverage")
    class PostSharingBugFix {

        @Test
        @DisplayName("✅ [Regression] Can share a post from the SAME community")
        void sharePost_sameCommunity_success() {
            givenHappyPath();
            when(socialPostRepo.findById(POST_ID)).thenReturn(Optional.of(postInSameCommunity));

            assertDoesNotThrow(() ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Check this out!", POST_ID, null));

            verify(communityMessageRepo).save(argThat(m ->
                    m.getMessageType() == CommunityMessage.MessageType.SHARE_POST));
        }

        @Test
        @DisplayName("✅ [Fixed Bug] Can share a post from a DIFFERENT community")
        void sharePost_differentCommunity_success() {
            givenHappyPath();
            when(socialPostRepo.findById(501L)).thenReturn(Optional.of(postInOtherCommunity));

            // Before the fix this threw:
            // ValidationException("Forbidden: You can only share posts belonging to this community.")
            assertDoesNotThrow(() ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Look at this!", 501L, null));

            verify(communityMessageRepo).save(argThat(m ->
                    m.getMessageType() == CommunityMessage.MessageType.SHARE_POST));
        }

        @Test
        @DisplayName("✅ [Fixed Bug] Can share a GLOBAL FEED post (community == null)")
        void sharePost_globalFeedPost_success() {
            givenHappyPath();
            when(socialPostRepo.findById(502L)).thenReturn(Optional.of(postFromGlobalFeed));

            // Before the fix: community == null triggered the bad check → exception
            assertDoesNotThrow(() ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Sharing from feed", 502L, null));

            verify(communityMessageRepo).save(argThat(m ->
                    m.getMessageType() == CommunityMessage.MessageType.SHARE_POST));
        }

        @Test
        @DisplayName("❌ Sharing a non-existent post still throws NoSuchElementException")
        void sharePost_postNotFound_throws() {
            givenHappyPath();
            when(socialPostRepo.findById(9999L)).thenReturn(Optional.empty());

            assertThrows(java.util.NoSuchElementException.class, () ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Share it", 9999L, null));

            verify(communityMessageRepo, never()).save(any());
        }

        @Test
        @DisplayName("✅ Snapshot captures cross-community author username correctly")
        void sharePost_crossCommunity_snapshotHasCorrectAuthor() {
            givenHappyPath();
            when(socialPostRepo.findById(501L)).thenReturn(Optional.of(postInOtherCommunity));

            communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "👀", 501L, null);

            ArgumentCaptor<CommunityMessage> captor = ArgumentCaptor.forClass(CommunityMessage.class);
            verify(communityMessageRepo).save(captor.capture());
            CommunityMessage saved = captor.getValue();

            assertNotNull(saved.getSharedPostSnapshot());
            assertEquals("AuthorUser", saved.getSharedPostSnapshot().getAuthorUsername());
            assertEquals("Post from another community", saved.getSharedPostSnapshot().getContent());
        }
    }

    // =========================================================================
    // ── Nested: Text messages ─────────────────────────────────────────────────
    // =========================================================================

    @Nested
    @DisplayName("Text messages")
    class TextMessages {

        @Test
        @DisplayName("✅ Active member can send a plain text message")
        void sendText_success() {
            givenHappyPath();

            assertDoesNotThrow(() ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Hello community!", null, null));

            verify(communityMessageRepo).save(argThat(m ->
                    m.getMessageType() == CommunityMessage.MessageType.TEXT
                    && "Hello community!".equals(m.getContent())));
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/community." + COMMUNITY_ID + ".messages"), any(CommunityMessageDto.class));
        }

        @Test
        @DisplayName("❌ Empty text (no post) is rejected")
        void sendText_emptyContent_throwsValidation() {
            givenHappyPath();

            assertThrows(ValidationException.class, () ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "   ", null, null));
            verify(communityMessageRepo, never()).save(any());
        }

        @Test
        @DisplayName("❌ Null text (no post) is rejected")
        void sendText_nullContent_throwsValidation() {
            givenHappyPath();

            assertThrows(ValidationException.class, () ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, null, null, null));
            verify(communityMessageRepo, never()).save(any());
        }
    }

    // =========================================================================
    // ── Nested: Authorization ─────────────────────────────────────────────────
    // =========================================================================

    @Nested
    @DisplayName("Authorization — membership & permissions")
    class Authorization {

        @Test
        @DisplayName("❌ Non-member cannot send")
        void nonMember_throws() {
            when(communityRepo.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
            when(communityMemberRepo.findByCommunityIdAndUserId(COMMUNITY_ID, SENDER_ID))
                    .thenReturn(Optional.empty());

            assertThrows(SecurityException.class, () ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Hello!", null, null));
            verify(communityMessageRepo, never()).save(any());
        }

        @Test
        @DisplayName("❌ Inactive member cannot send")
        void inactiveMember_throws() {
            activeMember.setIsActive(false);
            when(communityRepo.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
            when(communityMemberRepo.findByCommunityIdAndUserId(COMMUNITY_ID, SENDER_ID))
                    .thenReturn(Optional.of(activeMember));

            assertThrows(SecurityException.class, () ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Hello!", null, null));
        }

        @Test
        @DisplayName("❌ Banned member cannot send")
        void bannedMember_throws() {
            activeMember.setIsBanned(true);
            when(communityRepo.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
            when(communityMemberRepo.findByCommunityIdAndUserId(COMMUNITY_ID, SENDER_ID))
                    .thenReturn(Optional.of(activeMember));

            assertThrows(SecurityException.class, () ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Hello!", null, null));
        }

        @Test
        @DisplayName("❌ Muted member cannot send")
        void mutedMember_throws() {
            activeMember.setIsMuted(true);
            when(communityRepo.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
            when(communityMemberRepo.findByCommunityIdAndUserId(COMMUNITY_ID, SENDER_ID))
                    .thenReturn(Optional.of(activeMember));

            assertThrows(SecurityException.class, () ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Hello!", null, null));
        }

        @Test
        @DisplayName("❌ MEMBER blocked when group chat is disabled")
        void chatDisabled_memberBlocked() {
            community.setIsGroupChatEnabled(false);
            when(communityRepo.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
            when(communityMemberRepo.findByCommunityIdAndUserId(COMMUNITY_ID, SENDER_ID))
                    .thenReturn(Optional.of(activeMember));

            SecurityException ex = assertThrows(SecurityException.class, () ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Hello!", null, null));
            assertTrue(ex.getMessage().contains("disabled by the administrator"));
        }

        @Test
        @DisplayName("✅ ADMIN can post when group chat is disabled")
        void chatDisabled_adminCanPost() {
            community.setIsGroupChatEnabled(false);
            activeMember.setMemberRole(CommunityMember.MemberRole.ADMIN);
            when(communityRepo.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
            when(communityMemberRepo.findByCommunityIdAndUserId(COMMUNITY_ID, SENDER_ID))
                    .thenReturn(Optional.of(activeMember));
            when(chatModerator.scanMessage(anyString())).thenReturn(false);
            when(communityMessageRepo.save(any())).thenAnswer(inv -> {
                CommunityMessage m = inv.getArgument(0);
                if (m != null) m.setId(999L);
                return m;
            });

            assertDoesNotThrow(() ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Admin notice", null, null));
        }

        @Test
        @DisplayName("❌ Community not found throws NoSuchElementException")
        void communityNotFound_throws() {
            when(communityRepo.findById(COMMUNITY_ID)).thenReturn(Optional.empty());

            assertThrows(java.util.NoSuchElementException.class, () ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Hello!", null, null));
        }
    }

    // =========================================================================
    // ── Nested: Content moderation ────────────────────────────────────────────
    // =========================================================================

    @Nested
    @DisplayName("Content moderation")
    class ContentModeration {

        @Test
        @DisplayName("❌ Flagged message is persisted for audit but NOT broadcast")
        void flaggedMessage_savedButNotBroadcast() {
            when(communityRepo.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
            when(communityMemberRepo.findByCommunityIdAndUserId(COMMUNITY_ID, SENDER_ID))
                    .thenReturn(Optional.of(activeMember));
            when(chatModerator.scanMessage(anyString())).thenReturn(true);
            when(communityMessageRepo.save(any(CommunityMessage.class))).thenAnswer(inv -> {
                CommunityMessage m = inv.getArgument(0);
                if (m != null) m.setId(999L);
                return m;
            });

            assertThrows(ValidationException.class, () ->
                    communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "bad content", null, null));

            verify(communityMessageRepo).save(any());                            // saved for audit
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class)); // NOT broadcast
        }

        @Test
        @DisplayName("✅ Clean message is saved and broadcast over WebSocket")
        void cleanMessage_broadcastSent() {
            givenHappyPath();

            communityChatService.processNewMessage(COMMUNITY_ID, SENDER_ID, "Hello!", null, null);

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/community." + COMMUNITY_ID + ".messages"), any(CommunityMessageDto.class));
        }
    }
}
