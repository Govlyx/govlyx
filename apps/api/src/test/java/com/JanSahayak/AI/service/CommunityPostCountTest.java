package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.SocialPostCreateDto;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class CommunityPostCountTest {

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private CommunityService communityService;
    
    @Mock
    private InterestProfileService interestProfileService;

    @Mock
    private NotificationService notificationService;
    
    @Mock
    private TopicAggregationWorker topicAggregationWorker;
    
    @Mock
    private ContentValidationService contentValidationService;

    @Mock
    private com.JanSahayak.AI.repository.PollRepository pollRepository;
    
    @Mock
    private PostInteractionService postInteractionService;
    
    @Mock
    private com.JanSahayak.AI.repository.CommunityMemberRepo communityMemberRepo;

    @InjectMocks
    private SocialPostService socialPostService;

    private User testUser;
    private SocialPost testPost;
    private final Long COMMUNITY_ID = 100L;
    private final Long POST_ID = 500L;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testPost = new SocialPost();
        testPost.setId(POST_ID);
        testPost.setUser(testUser);
        com.JanSahayak.AI.model.Community community = new com.JanSahayak.AI.model.Community();
        community.setId(COMMUNITY_ID);
        testPost.setCommunity(community);
        
        org.mockito.Mockito.lenient().when(contentValidationService.sanitizeAndValidateContent(any())).thenReturn("Test content");
        ReflectionTestUtils.setField(socialPostService, "communityService", communityService);
        ReflectionTestUtils.setField(socialPostService, "contentValidationService", contentValidationService);
        ReflectionTestUtils.setField(socialPostService, "topicAggregationWorker", topicAggregationWorker);
        ReflectionTestUtils.setField(socialPostService, "pollRepository", pollRepository);
        ReflectionTestUtils.setField(socialPostService, "postInteractionService", postInteractionService);
        ReflectionTestUtils.setField(socialPostService, "communityMemberRepo", communityMemberRepo);
    }

    @Test
    void testCreateSocialPost_PendingApproval_DoesNotIncrementCount() {
        SocialPostCreateDto dto = new SocialPostCreateDto();
        dto.setContent("Test content");
        dto.setCommunityId(COMMUNITY_ID);
        
        testPost.setStatus(PostStatus.PENDING_APPROVAL);
        when(socialPostRepository.save(any(SocialPost.class))).thenReturn(testPost);

        socialPostService.createSocialPost(dto, java.util.Collections.emptyList(), testUser);

        // Verify that onPostPublished is NOT called because status is not ACTIVE
        verify(communityService, never()).onPostPublished(any(), anyLong());
    }

    @Test
    void testCreateSocialPost_Active_IncrementsCount() {
        SocialPostCreateDto dto = new SocialPostCreateDto();
        dto.setContent("Test content");
        dto.setCommunityId(COMMUNITY_ID);
        
        testPost.setStatus(PostStatus.ACTIVE);
        when(socialPostRepository.save(any(SocialPost.class))).thenReturn(testPost);

        socialPostService.createSocialPost(dto, java.util.Collections.emptyList(), testUser);

        // Verify that onPostPublished IS called because status is ACTIVE
        verify(communityService).onPostPublished(testPost, COMMUNITY_ID);
    }

    @Test
    void testDeleteSocialPost_PendingApproval_DoesNotDecrementCount() {
        testPost.setStatus(PostStatus.PENDING_APPROVAL);
        when(socialPostRepository.findById(POST_ID)).thenReturn(Optional.of(testPost));

        socialPostService.deleteSocialPost(POST_ID, testUser);

        // Verify that onPostDeleted is NOT called because status was not ACTIVE
        verify(communityService, never()).onPostDeleted(COMMUNITY_ID);
    }

    @Test
    void testDeleteSocialPost_Active_DecrementsCount() {
        testPost.setStatus(PostStatus.ACTIVE);
        when(socialPostRepository.findById(POST_ID)).thenReturn(Optional.of(testPost));

        socialPostService.deleteSocialPost(POST_ID, testUser);

        // Verify that onPostDeleted IS called because status was ACTIVE
        verify(communityService).onPostDeleted(COMMUNITY_ID);
    }
}
