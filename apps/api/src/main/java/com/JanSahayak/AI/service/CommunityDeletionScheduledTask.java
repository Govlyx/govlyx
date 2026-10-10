package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.CommunityJoinRequestRepo;
import com.JanSahayak.AI.repository.CommunityInviteRepo;
import com.JanSahayak.AI.repository.CommunityMessageRepo;
import com.JanSahayak.AI.repository.CommunitySharedPostSnapshotRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityDeletionScheduledTask {

    private final CommunityRepo communityRepo;
    private final CommunityMemberRepo communityMemberRepo;
    private final CommunityJoinRequestRepo communityJoinRequestRepo;
    private final CommunityInviteRepo communityInviteRepo;
    private final CommunityMessageRepo communityMessageRepo;
    private final CommunitySharedPostSnapshotRepo communitySharedPostSnapshotRepo;
    private final SocialPostRepo socialPostRepo;
    private final org.springframework.cache.CacheManager cacheManager;

    // Self-reference to invoke purgeSingleCommunity through the Spring AOP proxy,
    // which is required for @Transactional(REQUIRES_NEW) to take effect.
    @Autowired @Lazy
    private CommunityDeletionScheduledTask self;

    /**
     * Step 1: Move all DELETED communities whose grace period has expired
     * to PERMANENTLY_DELETED status, preventing any further restore attempts.
     *
     * Runs every 1 minute for faster testing and cleanup.
     */
    @Scheduled(fixedDelay = 60 * 1000)   // every 1 minute
    public void processScheduledDeletions() {
        Date now = new Date();
        List<Community> toDelete = communityRepo.findByStatusAndScheduledDeletionAtBefore(
                Community.CommunityStatus.DELETED, now);

        if (toDelete.isEmpty()) {
            return;   // nothing to do — skip noisy log at high frequency
        }

        log.info("[CommunityDeletion] Step 1: {} communities past grace period — moving to PERMANENTLY_DELETED.", toDelete.size());

        for (Community community : toDelete) {
            try {
                log.info("[CommunityDeletion] Locking community id={} name='{}' (scheduledAt={})",
                        community.getId(), community.getName(), community.getScheduledDeletionAt());
                community.setStatus(Community.CommunityStatus.PERMANENTLY_DELETED);
                communityRepo.save(community);

                evictFromCache(community);
            } catch (Exception e) {
                // Log but continue — one bad community must not block the rest
                log.error("[CommunityDeletion] Failed to lock community id={}: {}",
                        community.getId(), e.getMessage(), e);
            }
        }

        log.info("[CommunityDeletion] Step 1 complete. Locked {} communities.", toDelete.size());
    }

    /**
     * Step 2: For every PERMANENTLY_DELETED community, physically remove
     * all CommunityMember rows and detach any SocialPosts that reference it.
     *
     * Runs every 1 minute.
     */
    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 30000)   // every 1 min, offset by 30s
    public void processCompliancePurge() {
        List<Community> toPurge = communityRepo.findByStatus(Community.CommunityStatus.PERMANENTLY_DELETED);

        if (toPurge.isEmpty()) {
            return;   // nothing to do — skip noisy log
        }

        log.info("[CommunityDeletion] Step 2: Purging {} PERMANENTLY_DELETED communities.", toPurge.size());

        int purged = 0;
        for (Community community : toPurge) {
            try {
                self.purgeSingleCommunity(community);
                purged++;
            } catch (Exception e) {
                log.error("[CommunityDeletion] Failed to purge community id={}: {}",
                        community.getId(), e.getMessage(), e);
            }
        }

        log.info("[CommunityDeletion] Step 2 complete. Purged {}/{} communities.", purged, toPurge.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void purgeSingleCommunity(Community community) {
        Long communityId = community.getId();
        log.info("[CommunityDeletion] Purging community id={} name='{}'", communityId, community.getName());

        // 1. Remove all member associations
        communityMemberRepo.deleteByCommunityId(communityId);
        log.debug("[CommunityDeletion] Members removed for community {}", communityId);

        // 2. Remove all join requests and invites to prevent FK constraint violations
        communityJoinRequestRepo.deleteByCommunityId(communityId);
        communityInviteRepo.deleteByCommunityId(communityId);
        communitySharedPostSnapshotRepo.deleteSnapshotsByCommunityId(communityId);
        communityMessageRepo.deleteByCommunityId(communityId);
        log.debug("[CommunityDeletion] Join requests, invites, and messages removed for community {}", communityId);

        // 3. Detach posts from the deleted community (nullify the FK rather than
        //    deleting posts, so posts remain in the author's profile history)
        socialPostRepo.detachFromCommunity(communityId);
        log.debug("[CommunityDeletion] Posts detached from community {}", communityId);

        // 4. Evict from cache BEFORE deleting
        evictFromCache(community);

        // 5. Physically delete the community from the database
        communityRepo.delete(community);

        log.info("[CommunityDeletion] Purge and physical deletion complete for community {}", communityId);
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    private void evictFromCache(Community community) {
        if (cacheManager == null) return;
        try {
            org.springframework.cache.Cache cache = cacheManager.getCache("communities");
            if (cache != null) {
                cache.evict(community.getId());
                if (community.getSlug() != null) cache.evict(community.getSlug());
            }
        } catch (Exception e) {
            log.warn("[CommunityDeletion] Cache eviction failed for community {}: {}", community.getId(), e.getMessage());
        }
    }
}
