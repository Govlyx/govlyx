package com.govlyx.AI.service;

import com.govlyx.AI.dto.PaginatedResponse;
import com.govlyx.AI.dto.SocialPostDto;
import com.govlyx.AI.enums.FeedSort;
import com.govlyx.AI.enums.PostStatus;
import com.govlyx.AI.enums.SocialPostCategory;
import com.govlyx.AI.model.PincodeLookup;
import com.govlyx.AI.model.SocialPost;
import com.govlyx.AI.model.User;
import com.govlyx.AI.repository.PincodeLookupRepo;
import com.govlyx.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SocialPostNeighborhoodQATest {

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private PincodeLookupRepo pincodeLookupRepo;

    @Mock
    private com.govlyx.AI.repository.PollRepository pollRepository;

    @Mock
    private com.govlyx.AI.service.PostInteractionService postInteractionService;

    @Mock
    private com.govlyx.AI.repository.CommunityMemberRepo communityMemberRepo;

    @InjectMocks
    private SocialPostService socialPostService;

    private User testUser;
    private SocialPost testPost;
    private PincodeLookup testPincode;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(100L);
        testUser.setPincode("411001");
        testUser.setHomeLatitude(new BigDecimal("18.5204"));
        testUser.setHomeLongitude(new BigDecimal("73.8567"));

        testPost = new SocialPost();
        testPost.setId(1L);
        testPost.setCategory(SocialPostCategory.NEIGHBORHOOD_QUESTION);
        testPost.setStatus(PostStatus.ACTIVE);
        testPost.setContent("Who is the best plumber here?");
        testPost.setUser(testUser);
        
        testPincode = new PincodeLookup();
        testPincode.setPincode("411001");
        testPincode.setCity("Pune");
        testPincode.setUrbanClusterId("pune_pune");
        testPincode.setDistrict("Pune District");
        testPincode.setLatitude(new BigDecimal("18.5204"));
        testPincode.setLongitude(new BigDecimal("73.8567"));
    }

    @Test
    void testNeighborhoodQAAreaScope() {
        when(pincodeLookupRepo.findById("411001")).thenReturn(Optional.of(testPincode));
        when(socialPostRepository.findQAPostsByCity(
                eq(SocialPostCategory.NEIGHBORHOOD_QUESTION),
                eq("Pune"),
                eq(PostStatus.ACTIVE),
                any(Pageable.class)
        )).thenReturn(List.of(testPost));

        PaginatedResponse<SocialPostDto> response = socialPostService.getNeighborhoodQAFeed(testUser, "AREA", null, 20);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        verify(socialPostRepository, times(1)).findQAPostsByCity(any(), any(), any(), any());
    }

    @Test
    void testNeighborhoodQACityScope() {
        when(pincodeLookupRepo.findById("411001")).thenReturn(Optional.of(testPincode));
        when(socialPostRepository.findQAPostsByUrbanCluster(
                eq(SocialPostCategory.NEIGHBORHOOD_QUESTION),
                eq("pune_pune"),
                eq(PostStatus.ACTIVE),
                any(Pageable.class)
        )).thenReturn(List.of(testPost));

        PaginatedResponse<SocialPostDto> response = socialPostService.getNeighborhoodQAFeed(testUser, "CITY", null, 20);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        verify(socialPostRepository, times(1)).findQAPostsByUrbanCluster(any(), any(), any(), any());
    }

    @Test
    void testNeighborhoodQADistrictScope() {
        when(pincodeLookupRepo.findById("411001")).thenReturn(Optional.of(testPincode));
        when(socialPostRepository.findQAPostsByRealDistrict(
                eq(SocialPostCategory.NEIGHBORHOOD_QUESTION),
                eq("Pune District"),
                eq(PostStatus.ACTIVE),
                any(Pageable.class)
        )).thenReturn(List.of(testPost));

        PaginatedResponse<SocialPostDto> response = socialPostService.getNeighborhoodQAFeed(testUser, "DISTRICT", null, 20);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        verify(socialPostRepository, times(1)).findQAPostsByRealDistrict(any(), any(), any(), any());
    }

    @Test
    void testNeighborhoodQANearbyScopeSuccess() {
        when(pincodeLookupRepo.findById("411001")).thenReturn(Optional.of(testPincode));
        when(socialPostRepository.findNearbyPostsUsingGPS(
                any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()
        )).thenReturn(List.of(testPost));

        PaginatedResponse<SocialPostDto> response = socialPostService.getNeighborhoodQAFeed(testUser, "NEARBY", FeedSort.NEW, null, 20);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        verify(socialPostRepository, times(1)).findNearbyPostsUsingGPS(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any());
    }

    @Test
    void testNeighborhoodQANearbyScopeColdStartFallback() {
        when(pincodeLookupRepo.findById("411001")).thenReturn(Optional.of(testPincode));
        
        // 5km radius returns empty, 15km returns post
        when(socialPostRepository.findNearbyPostsUsingGPS(
                any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()
        )).thenReturn(Collections.emptyList()).thenReturn(List.of(testPost));

        PaginatedResponse<SocialPostDto> response = socialPostService.getNeighborhoodQAFeed(testUser, "NEARBY", FeedSort.HOT, null, 20);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        
        // Should be called twice due to the fallback mechanism
        verify(socialPostRepository, times(2)).findNearbyPostsUsingGPS(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any());
    }

    @Test
    void testSyncGPSFromPincode() {
        User targetUser = new User();
        targetUser.setId(200L);
        targetUser.setPincode("411001");
        // GPS not set yet
        
        when(pincodeLookupRepo.findById("411001")).thenReturn(Optional.of(testPincode));
        
        socialPostService.syncGPSFromPincode(targetUser);
        
        assertEquals(new BigDecimal("18.5204"), targetUser.getHomeLatitude());
        assertEquals(new BigDecimal("73.8567"), targetUser.getHomeLongitude());
    }

    @Test
    void testNeighborhoodQANearbyScopePincodeFallback() {
        // User with pincode but NO GPS
        User noGpsUser = new User();
        noGpsUser.setId(300L);
        noGpsUser.setPincode("411001");
        
        when(pincodeLookupRepo.findById("411001")).thenReturn(Optional.of(testPincode));
        
        // Mock the fallback to city search
        when(socialPostRepository.findQAPostsByCity(
                eq(SocialPostCategory.NEIGHBORHOOD_QUESTION),
                eq("Pune"),
                eq(PostStatus.ACTIVE),
                any(Pageable.class)
        )).thenReturn(List.of(testPost));

        PaginatedResponse<SocialPostDto> response = socialPostService.getNeighborhoodQAFeed(noGpsUser, "NEARBY", null, 20);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        assertTrue(response.isFallback(), "Fallback flag should be true");
        verify(socialPostRepository, times(1)).findQAPostsByCity(any(), any(), any(), any());
        verify(socialPostRepository, never()).findNearbyPostsUsingGPS(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any());
    }

    @Test
    void testNeighborhoodQASortHotAndTop() {
        SocialPost postViral = new SocialPost();
        postViral.setId(10L);
        postViral.setCategory(SocialPostCategory.NEIGHBORHOOD_QUESTION);
        postViral.setStatus(PostStatus.ACTIVE);
        postViral.setContent("Viral question");
        postViral.setUser(testUser);
        postViral.setCreatedAt(new java.util.Date());
        postViral.setViralityScore(95.0);
        postViral.setEngagementScore(10.0);

        SocialPost postTop = new SocialPost();
        postTop.setId(20L);
        postTop.setCategory(SocialPostCategory.NEIGHBORHOOD_QUESTION);
        postTop.setStatus(PostStatus.ACTIVE);
        postTop.setContent("Top engaged question");
        postTop.setUser(testUser);
        postTop.setCreatedAt(new java.util.Date(System.currentTimeMillis() - 100000));
        postTop.setViralityScore(5.0);
        postTop.setEngagementScore(99.0);

        when(pincodeLookupRepo.findById("411001")).thenReturn(Optional.of(testPincode));
        when(socialPostRepository.findQAPostsByCity(
                eq(SocialPostCategory.NEIGHBORHOOD_QUESTION),
                eq("Pune"),
                eq(PostStatus.ACTIVE),
                any(Pageable.class)
        )).thenReturn(List.of(postTop, postViral));

        // Test HOT sort: viral post should be first
        PaginatedResponse<SocialPostDto> hotResp = socialPostService.getNeighborhoodQAFeed(testUser, "AREA", FeedSort.HOT, null, 20);
        assertNotNull(hotResp);
        assertEquals(2, hotResp.getData().size());
        assertEquals(10L, hotResp.getData().get(0).getId());

        // Test TOP sort: top engagement post should be first
        PaginatedResponse<SocialPostDto> topResp = socialPostService.getNeighborhoodQAFeed(testUser, "AREA", FeedSort.TOP, null, 20);
        assertNotNull(topResp);
        assertEquals(2, topResp.getData().size());
        assertEquals(20L, topResp.getData().get(0).getId());

        // Test NEW sort: postViral has newer createdAt so it should be first
        PaginatedResponse<SocialPostDto> newResp = socialPostService.getNeighborhoodQAFeed(testUser, "AREA", FeedSort.NEW, null, 20);
        assertNotNull(newResp);
        assertEquals(2, newResp.getData().size());
        assertEquals(10L, newResp.getData().get(0).getId());
    }
}
