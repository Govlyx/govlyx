package com.govlyx.AI.service;

import com.govlyx.AI.model.Community;
import com.govlyx.AI.repository.CommunityInviteRepo;
import com.govlyx.AI.repository.CommunityJoinRequestRepo;
import com.govlyx.AI.repository.CommunityMemberRepo;
import com.govlyx.AI.repository.CommunityMessageRepo;
import com.govlyx.AI.repository.CommunityRepo;
import com.govlyx.AI.repository.CommunitySharedPostSnapshotRepo;
import com.govlyx.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityDeletionScheduledTaskTest {

    @Mock
    private CommunityRepo communityRepo;

    @Mock
    private CommunityMemberRepo communityMemberRepo;

    @Mock
    private CommunityJoinRequestRepo communityJoinRequestRepo;

    @Mock
    private CommunityInviteRepo communityInviteRepo;

    @Mock
    private CommunityMessageRepo communityMessageRepo;

    @Mock
    private CommunitySharedPostSnapshotRepo communitySharedPostSnapshotRepo;

    @Mock
    private SocialPostRepo socialPostRepo;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private CommunityDeletionScheduledTask scheduledTask;

    private Community community1;
    private Community community2;

    @BeforeEach
    void setUp() {
        community1 = new Community();
        community1.setId(100L);
        community1.setName("Test Community 1");
        community1.setSlug("test-community-1");
        community1.setStatus(Community.CommunityStatus.DELETED);

        community2 = new Community();
        community2.setId(200L);
        community2.setName("Test Community 2");
        community2.setSlug("test-community-2");
        community2.setStatus(Community.CommunityStatus.DELETED);
        org.springframework.test.util.ReflectionTestUtils.setField(scheduledTask, "self", scheduledTask);
    }

    @Test
    void testProcessScheduledDeletions_Success() {
        when(communityRepo.findByStatusAndScheduledDeletionAtBefore(eq(Community.CommunityStatus.DELETED), any(Date.class)))
                .thenReturn(Arrays.asList(community1, community2));

        when(cacheManager.getCache("communities")).thenReturn(cache);

        scheduledTask.processScheduledDeletions();

        // Verify status changed
        assertEquals(Community.CommunityStatus.PERMANENTLY_DELETED, community1.getStatus());
        assertEquals(Community.CommunityStatus.PERMANENTLY_DELETED, community2.getStatus());

        // Verify save called for both
        verify(communityRepo, times(1)).save(community1);
        verify(communityRepo, times(1)).save(community2);

        // Verify cache eviction
        verify(cache, times(1)).evict(100L);
        verify(cache, times(1)).evict("test-community-1");
        verify(cache, times(1)).evict(200L);
        verify(cache, times(1)).evict("test-community-2");
    }

    @Test
    void testProcessScheduledDeletions_NoCommunities() {
        when(communityRepo.findByStatusAndScheduledDeletionAtBefore(eq(Community.CommunityStatus.DELETED), any(Date.class)))
                .thenReturn(Collections.emptyList());

        scheduledTask.processScheduledDeletions();

        verify(communityRepo, never()).save(any());
    }

    @Test
    void testProcessCompliancePurge_PhysicalDeletionSuccess() {
        community1.setStatus(Community.CommunityStatus.PERMANENTLY_DELETED);
        
        when(communityRepo.findByStatus(Community.CommunityStatus.PERMANENTLY_DELETED))
                .thenReturn(Collections.singletonList(community1));

        when(cacheManager.getCache("communities")).thenReturn(cache);

        scheduledTask.processCompliancePurge();

        // Verify all database repositories were called to purge data
        verify(communityMemberRepo, times(1)).deleteByCommunityId(100L);
        verify(communityJoinRequestRepo, times(1)).deleteByCommunityId(100L);
        verify(communityInviteRepo, times(1)).deleteByCommunityId(100L);
        verify(communitySharedPostSnapshotRepo, times(1)).deleteSnapshotsByCommunityId(100L);
        verify(communityMessageRepo, times(1)).deleteByCommunityId(100L);
        verify(socialPostRepo, times(1)).detachFromCommunity(100L);
        verify(communityRepo, times(1)).delete(community1);

        // Verify cache eviction
        verify(cache, times(1)).evict(100L);
        verify(cache, times(1)).evict("test-community-1");
    }

    @Test
    void testProcessCompliancePurge_OneFailureDoesNotStopOthers() {
        community1.setStatus(Community.CommunityStatus.PERMANENTLY_DELETED);
        community2.setStatus(Community.CommunityStatus.PERMANENTLY_DELETED);

        when(communityRepo.findByStatus(Community.CommunityStatus.PERMANENTLY_DELETED))
                .thenReturn(Arrays.asList(community1, community2));

        // Simulate an exception throwing when purging community 1
        doThrow(new RuntimeException("Database error")).when(communityMemberRepo).deleteByCommunityId(100L);

        when(cacheManager.getCache("communities")).thenReturn(cache);

        scheduledTask.processCompliancePurge();

        // Community 1 fails at member deletion step
        verify(communityMemberRepo, times(1)).deleteByCommunityId(100L);
        verify(communityJoinRequestRepo, never()).deleteByCommunityId(100L); // shouldn't reach here
        verify(communityRepo, never()).delete(community1); // shouldn't be deleted

        // BUT Community 2 should still process fully
        verify(communityMemberRepo, times(1)).deleteByCommunityId(200L);
        verify(communityJoinRequestRepo, times(1)).deleteByCommunityId(200L);
        verify(communityInviteRepo, times(1)).deleteByCommunityId(200L);
        verify(communitySharedPostSnapshotRepo, times(1)).deleteSnapshotsByCommunityId(200L);
        verify(communityMessageRepo, times(1)).deleteByCommunityId(200L);
        verify(socialPostRepo, times(1)).detachFromCommunity(200L);
        verify(communityRepo, times(1)).delete(community2);
    }
}
