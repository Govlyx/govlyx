package com.govlyx.AI.controller;

import com.govlyx.AI.dto.CommunityDto.CommunityDetailResponse;
import com.govlyx.AI.exception.ApiResponse;
import com.govlyx.AI.model.User;
import com.govlyx.AI.service.CommunityInviteService;
import com.govlyx.AI.service.CommunityService;
import com.govlyx.AI.service.SocialPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommunityControllerTest {

    @Mock
    private CommunityService communityService;

    @Mock
    private CommunityInviteService inviteService;

    @Mock
    private SocialPostService socialPostService;

    @InjectMocks
    private CommunityController communityController;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(10L);
    }

    @Test
    void getCommunity_withNumericString_passesIdOrSlugToService() {
        // Arrange
        String idOrSlug = "101";
        CommunityDetailResponse responseDto = new CommunityDetailResponse();
        responseDto.setId(101L);
        when(communityService.getCommunityDetail(idOrSlug, testUser.getId())).thenReturn(responseDto);

        // Act
        ResponseEntity<ApiResponse<CommunityDetailResponse>> response = communityController.getCommunity(idOrSlug, testUser);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertEquals(101L, response.getBody().getData().getId());
        verify(communityService).getCommunityDetail("101", 10L);
    }

    @Test
    void getCommunity_withStringSlug_passesIdOrSlugToService() {
        // Arrange
        String idOrSlug = "gaming-innovators";
        CommunityDetailResponse responseDto = new CommunityDetailResponse();
        responseDto.setId(9L);
        responseDto.setSlug("gaming-innovators");
        when(communityService.getCommunityDetail(idOrSlug, testUser.getId())).thenReturn(responseDto);

        // Act
        ResponseEntity<ApiResponse<CommunityDetailResponse>> response = communityController.getCommunity(idOrSlug, testUser);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertEquals("gaming-innovators", response.getBody().getData().getSlug());
        verify(communityService).getCommunityDetail("gaming-innovators", 10L);
    }

    @Test
    void refreshMyCommunitiesCache_shouldCallServiceAndReturnOk() {
        // Arrange
        doNothing().when(communityService).evictMyCommunityListCache(testUser.getId());

        // Act
        ResponseEntity<ApiResponse<Void>> response = communityController.refreshMyCommunitiesCache(testUser);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Cache cleared successfully", response.getBody().getMessage());
        verify(communityService, times(1)).evictMyCommunityListCache(10L);
    }
}
