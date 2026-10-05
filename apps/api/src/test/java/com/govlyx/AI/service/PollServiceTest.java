package com.govlyx.AI.service;

import com.govlyx.AI.model.*;
import com.govlyx.AI.payload.request.CreatePollRequest;
import com.govlyx.AI.payload.request.PollResponse;
import com.govlyx.AI.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PollServiceTest {

    @Mock
    private PollRepository pollRepository;
    @Mock
    private PollOptionRepository pollOptionRepository;
    @Mock
    private PollVoteRepository pollVoteRepository;
    @Mock
    private SocialPostRepo socialPostRepository;
    @Mock
    private UserRepo userRepository;
    @Mock
    private SocialPostMediaService mediaService;
    @Mock
    private CommunityRepo communityRepository;
    @Mock
    private CommunityMemberRepo communityMemberRepository;
    @Mock
    private CommunityService communityService;

    @Mock
    private SocialPostService socialPostService;

    @InjectMocks
    private PollService pollService;

    private User testUser;
    private Community testCommunity;
    private CreatePollRequest req;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(100L);
        testUser.setUsername("testuser");

        testCommunity = new Community();
        testCommunity.setId(200L);
        testCommunity.setName("Test Community");
        testCommunity.setPrivacy(Community.CommunityPrivacy.PUBLIC);

        req = new CreatePollRequest();
        req.setQuestion("Which one is better?");
        req.setOptions(List.of("Option 1", "Option 2"));
        req.setAllowMultipleVotes(false);
        req.setShowResultsBeforeExpiry(true);

        org.springframework.test.util.ReflectionTestUtils.setField(pollService, "communityService", communityService);
        org.springframework.test.util.ReflectionTestUtils.setField(pollService, "socialPostService", socialPostService);
    }

    @Test
    void testCreatePollPost_InCommunity_Success() {
        req.setCommunityId(testCommunity.getId());

        when(communityRepository.findById(testCommunity.getId())).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepository.existsByCommunityIdAndUserIdAndIsActiveTrue(testCommunity.getId(), testUser.getId()))
                .thenReturn(true);
        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(i -> {
            SocialPost sp = i.getArgument(0);
            sp.setId(1L);
            return sp;
        });
        when(pollRepository.save(any(Poll.class))).thenAnswer(i -> {
            Poll p = i.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(pollOptionRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        PollResponse response = pollService.createPollPost(req, testUser);

        assertNotNull(response);
        assertEquals("Which one is better?", response.getQuestion());

        ArgumentCaptor<SocialPost> socialPostCaptor = ArgumentCaptor.forClass(SocialPost.class);
        verify(socialPostRepository).save(socialPostCaptor.capture());
        SocialPost savedSp = socialPostCaptor.getValue();
        assertEquals(testCommunity, savedSp.getCommunity());
        assertEquals("PUBLIC", savedSp.getCommunityPrivacy()); // Assuming syncCommunityDenormalizedFields sets this
    }

    @Test
    void testCreatePollPost_WithCategory_Success() {
        req.setCategory("NEIGHBORHOOD_QUESTION");

        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(i -> {
            SocialPost sp = i.getArgument(0);
            sp.setId(2L);
            return sp;
        });
        when(pollRepository.save(any(Poll.class))).thenAnswer(i -> {
            Poll p = i.getArgument(0);
            p.setId(20L);
            return p;
        });
        when(pollOptionRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        PollResponse response = pollService.createPollPost(req, testUser);

        assertNotNull(response);

        ArgumentCaptor<SocialPost> socialPostCaptor = ArgumentCaptor.forClass(SocialPost.class);
        verify(socialPostRepository).save(socialPostCaptor.capture());
        SocialPost savedSp = socialPostCaptor.getValue();
        
        assertEquals(com.govlyx.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION, savedSp.getCategory());
    }

    @Test
    void testCreatePollPost_InCommunity_NotMember() {
        req.setCommunityId(testCommunity.getId());

        when(communityRepository.findById(testCommunity.getId())).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepository.existsByCommunityIdAndUserIdAndIsActiveTrue(testCommunity.getId(), testUser.getId()))
                .thenReturn(false); // Not an active member

        SecurityException ex = assertThrows(SecurityException.class, () -> {
            pollService.createPollPost(req, testUser);
        });
        assertTrue(ex.getMessage().contains("active member"));

        verify(socialPostRepository, never()).save(any(SocialPost.class));
    }

    @Test
    void testCreatePollPost_InCommunity_NotFound() {
        req.setCommunityId(testCommunity.getId());

        when(communityRepository.findById(testCommunity.getId())).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            pollService.createPollPost(req, testUser);
        });
        assertTrue(ex.getMessage().contains("Community not found"));

        verify(communityMemberRepository, never()).existsByCommunityIdAndUserIdAndIsActiveTrue(anyLong(), anyLong());
        verify(socialPostRepository, never()).save(any(SocialPost.class));
    }

    @Test
    void testCreatePollPost_InCommunity_RequireApproval_NotModerator() {
        req.setCommunityId(testCommunity.getId());
        testCommunity.setRequirePostApproval(true);

        when(communityRepository.findById(testCommunity.getId())).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepository.existsByCommunityIdAndUserIdAndIsActiveTrue(testCommunity.getId(), testUser.getId()))
                .thenReturn(true);
        when(communityMemberRepository.isModeratorOrAbove(testCommunity.getId(), testUser.getId()))
                .thenReturn(false);

        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(i -> {
            SocialPost sp = i.getArgument(0);
            sp.setId(1L);
            return sp;
        });
        when(pollRepository.save(any(Poll.class))).thenAnswer(i -> {
            Poll p = i.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(pollOptionRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        PollResponse response = pollService.createPollPost(req, testUser);

        assertNotNull(response);

        ArgumentCaptor<SocialPost> socialPostCaptor = ArgumentCaptor.forClass(SocialPost.class);
        verify(socialPostRepository).save(socialPostCaptor.capture());
        SocialPost savedSp = socialPostCaptor.getValue();
        
        // Assert moderation bypass is fixed
        assertEquals(com.govlyx.AI.enums.PostStatus.PENDING_APPROVAL, savedSp.getStatus());
        
        // Assert counter corruption is fixed
        verify(communityService, never()).onPostPublished(any(SocialPost.class), anyLong());
    }

    @Test
    void testCreatePollPost_InCommunity_RequireApproval_IsModerator() {
        req.setCommunityId(testCommunity.getId());
        testCommunity.setRequirePostApproval(true);

        when(communityRepository.findById(testCommunity.getId())).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepository.existsByCommunityIdAndUserIdAndIsActiveTrue(testCommunity.getId(), testUser.getId()))
                .thenReturn(true);
        when(communityMemberRepository.isModeratorOrAbove(testCommunity.getId(), testUser.getId()))
                .thenReturn(true);

        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(i -> {
            SocialPost sp = i.getArgument(0);
            sp.setId(1L);
            return sp;
        });
        when(pollRepository.save(any(Poll.class))).thenAnswer(i -> {
            Poll p = i.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(pollOptionRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        PollResponse response = pollService.createPollPost(req, testUser);

        assertNotNull(response);

        ArgumentCaptor<SocialPost> socialPostCaptor = ArgumentCaptor.forClass(SocialPost.class);
        verify(socialPostRepository).save(socialPostCaptor.capture());
        SocialPost savedSp = socialPostCaptor.getValue();
        
        assertEquals(com.govlyx.AI.enums.PostStatus.ACTIVE, savedSp.getStatus());
        
        verify(communityService, times(1)).onPostPublished(any(SocialPost.class), anyLong());
    }

    @Test
    void testDeletePoll_Success() {
        Poll poll = new Poll();
        poll.setId(10L);
        poll.setCreatedBy(testUser);
        poll.setIsActive(true);
        SocialPost sp = new SocialPost();
        sp.setId(100L);
        poll.setSocialPost(sp);

        when(pollRepository.findById(10L)).thenReturn(Optional.of(poll));

        pollService.deletePoll(10L, testUser);

        assertFalse(poll.getIsActive());
        verify(pollRepository, times(1)).save(poll);
        verify(socialPostService, times(1)).deleteSocialPost(100L, testUser);
    }

    @Test
    void testDeletePoll_NotOwner_ThrowsException() {
        Poll poll = new Poll();
        poll.setId(10L);
        User anotherUser = new User();
        anotherUser.setId(999L);
        poll.setCreatedBy(anotherUser);
        
        when(pollRepository.findById(10L)).thenReturn(Optional.of(poll));

        assertThrows(SecurityException.class, () -> pollService.deletePoll(10L, testUser));
    }

    @Test
    void testCreatePollPost_AlwaysExpiresIn_HasNullExpiresAt() {
        req.setExpiresIn("always");

        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(i -> {
            SocialPost sp = i.getArgument(0);
            sp.setId(30L);
            return sp;
        });
        when(pollRepository.save(any(Poll.class))).thenAnswer(i -> {
            Poll p = i.getArgument(0);
            p.setId(300L);
            return p;
        });
        when(pollOptionRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        PollResponse response = pollService.createPollPost(req, testUser);

        assertNotNull(response);
        assertNull(response.getExpiresAt());
        assertFalse(response.isExpired());
        assertTrue(response.isOpenForVoting());

        ArgumentCaptor<Poll> pollCaptor = ArgumentCaptor.forClass(Poll.class);
        verify(pollRepository).save(pollCaptor.capture());
        assertNull(pollCaptor.getValue().getExpiresAt());
    }

    @Test
    void testCreatePollPost_1hExpiresIn_HasNonNullExpiresAt() {
        req.setExpiresIn("1h");

        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(i -> {
            SocialPost sp = i.getArgument(0);
            sp.setId(31L);
            return sp;
        });
        when(pollRepository.save(any(Poll.class))).thenAnswer(i -> {
            Poll p = i.getArgument(0);
            p.setId(301L);
            return p;
        });
        when(pollOptionRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        PollResponse response = pollService.createPollPost(req, testUser);

        assertNotNull(response);
        assertNotNull(response.getExpiresAt());
        assertFalse(response.isExpired());
        assertTrue(response.isOpenForVoting());

        ArgumentCaptor<Poll> pollCaptor = ArgumentCaptor.forClass(Poll.class);
        verify(pollRepository).save(pollCaptor.capture());
        assertNotNull(pollCaptor.getValue().getExpiresAt());
    }

    @Test
    void testBuildPollSummary_AlwaysPoll_HasAlwaysTimeLeft() {
        Poll poll = new Poll();
        poll.setId(50L);
        poll.setQuestion("Ongoing poll question?");
        poll.setExpiresAt(null);
        poll.setIsActive(true);
        poll.setTotalVotes(0);

        com.govlyx.AI.dto.SocialPostDto.PollSummaryDto summary =
                com.govlyx.AI.dto.SocialPostDto.buildPollSummary(poll, false, List.of());

        assertNotNull(summary);
        assertNull(summary.getExpiresAt());
        assertFalse(summary.isExpired());
        assertEquals("Always", summary.getTimeLeft());
    }
}
