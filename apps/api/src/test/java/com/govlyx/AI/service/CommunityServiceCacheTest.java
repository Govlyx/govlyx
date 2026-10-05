package com.Govlyx.AI.service;

import com.Govlyx.AI.model.Community;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.CommunityRepo;
import com.Govlyx.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommunityServiceCacheTest {

    @Mock
    private CommunityRepo communityRepo;

    @Mock
    private SocialPostRepo socialPostRepo;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache communitiesCache;

    @Mock
    private Cache listCache;

    @InjectMocks
    private CommunityService communityService;

    private Community community;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);

        community = new Community();
        community.setId(100L);
        community.setSlug("test-community-slug");
        community.setOwner(owner);

        ReflectionTestUtils.setField(communityService, "cacheManager", cacheManager);
    }

    @Test
    void archiveCommunity_shouldEvictCacheByIdAndSlug() {
        // Arrange
        when(communityRepo.findById(100L)).thenReturn(Optional.of(community));
        when(cacheManager.getCache("communities")).thenReturn(communitiesCache);

        // Act
        communityService.archiveCommunity(100L, 1L);

        // Assert
        verify(communitiesCache, times(1)).evict(100L);
        verify(communitiesCache, times(1)).evict("test-community-slug");
        verify(communityRepo, times(1)).save(community);
    }

    @Test
    void evictMyCommunityListCache_shouldEvictByUserId() {
        // Arrange
        when(cacheManager.getCache(com.Govlyx.AI.config.Constant.CACHE_COMMUNITY_LIST)).thenReturn(listCache);

        // Act
        communityService.evictMyCommunityListCache(1L);

        // Assert
        verify(listCache, times(1)).evict(1L);
    }
}
