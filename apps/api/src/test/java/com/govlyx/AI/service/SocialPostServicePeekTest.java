package com.Govlyx.AI.service;

import com.Govlyx.AI.enums.FeedScope;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.PostRepo;
import com.Govlyx.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SocialPostServicePeekTest {

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private PostRepo postRepo;

    @InjectMocks
    private SocialPostService socialPostService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(100L);
        testUser.setPincode("110001");
        org.springframework.test.util.ReflectionTestUtils.setField(socialPostService, "postRepo", postRepo);
    }

    @Test
    void testPeekForYouFeed() {
        when(socialPostRepository.countNewGlobalPostsAfter(10L)).thenReturn(5L);
        long count = socialPostService.peekNewPostCount(testUser, FeedScope.FOR_YOU, 10L);
        assertEquals(5L, count);
        verify(socialPostRepository).countNewGlobalPostsAfter(10L);
    }

    @Test
    void testPeekLocationFeed_WithPincode() {
        when(socialPostRepository.countNewLocationPostsAfter(10L, "110001")).thenReturn(3L);
        long count = socialPostService.peekNewPostCount(testUser, FeedScope.LOCATION, 10L);
        assertEquals(3L, count);
        verify(socialPostRepository).countNewLocationPostsAfter(10L, "110001");
    }

    @Test
    void testPeekFollowingFeed() {
        when(socialPostRepository.countNewFollowingPostsAfter(100L, 10L)).thenReturn(2L);
        long count = socialPostService.peekNewPostCount(testUser, FeedScope.FOLLOWING, 10L);
        assertEquals(2L, count);
        verify(socialPostRepository).countNewFollowingPostsAfter(100L, 10L);
    }

    @Test
    void testPeekOfficialFeed() {
        when(postRepo.countNewOfficialPostsAfter(10L)).thenReturn(4L);
        long count = socialPostService.peekNewPostCount(testUser, FeedScope.OFFICIAL, 10L);
        assertEquals(4L, count);
        verify(postRepo).countNewOfficialPostsAfter(10L);
    }

    @Test
    void testPeekNeighborhoodQAFeed() {
        when(socialPostRepository.countNewNeighborhoodQAPostsAfter(10L, "110001")).thenReturn(7L);
        long count = socialPostService.peekNewPostCount(testUser, FeedScope.NEIGHBORHOOD_QA, 10L);
        assertEquals(7L, count);
        verify(socialPostRepository).countNewNeighborhoodQAPostsAfter(10L, "110001");
    }
}
