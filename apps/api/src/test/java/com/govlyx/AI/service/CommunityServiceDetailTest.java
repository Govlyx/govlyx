package com.Govlyx.AI.service;

import com.Govlyx.AI.dto.CommunityDto.CommunityDetailResponse;
import com.Govlyx.AI.model.Community;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.CommunityRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommunityServiceDetailTest {

    @Mock
    private CommunityRepo communityRepo;

    // Using spy or partial mock of service if needed for toDetailResponse, but typically
    // we should just inject mocks. However, toDetailResponse and isMember are private.
    // For a pure unit test of the fallback logic, we can mock the repository.
    
    // In this codebase, the detail response is complex. We'll just verify repository interactions 
    // and exception throwing for the fallback.

    @InjectMocks
    private CommunityService communityService;

    private Community mockCommunity;
    
    @BeforeEach
    void setUp() {
        mockCommunity = new Community();
        mockCommunity.setId(101L);
        mockCommunity.setSlug("my-awesome-community");
        mockCommunity.setStatus(Community.CommunityStatus.ACTIVE);
        mockCommunity.setName("Test Community");
    }

    @Test
    void getCommunityDetail_withNonNumericSlug_findsBySlug() {
        when(communityRepo.findBySlug("my-awesome-community")).thenReturn(Optional.of(mockCommunity));
        
        try {
            communityService.getCommunityDetail("my-awesome-community", 1L);
        } catch (Exception e) {
            // expected due to missing deep mocks
        }
        
        verify(communityRepo).findBySlug("my-awesome-community");
        verify(communityRepo, never()).findById(anyLong());
    }

    @Test
    void getCommunityDetail_withNumericString_findsById() {
        when(communityRepo.findById(101L)).thenReturn(Optional.of(mockCommunity));

        try {
            communityService.getCommunityDetail("101", 1L);
        } catch (Exception e) {
            // expected due to missing deep mocks
        }

        verify(communityRepo).findById(101L);
        verify(communityRepo, never()).findBySlug(anyString());
    }

    @Test
    void getCommunityDetail_withNonNumericSlugNotFound_throwsNoSuchElementException() {
        when(communityRepo.findBySlug("not-found")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> {
            communityService.getCommunityDetail("not-found", 1L);
        });

        verify(communityRepo).findBySlug("not-found");
        verify(communityRepo, never()).findById(anyLong());
    }
}
