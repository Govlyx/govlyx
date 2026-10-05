package com.Govlyx.AI.service;

import com.Govlyx.AI.event.CommunityDeletedEvent;
import com.Govlyx.AI.event.CommunityRevokedEvent;
import com.Govlyx.AI.model.Community;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.CommunityMemberRepo;
import com.Govlyx.AI.repository.CommunityRepo;
import com.Govlyx.AI.repository.SocialPostRepo;
import com.Govlyx.AI.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityAsyncEventListener {

    private final CommunityRepo communityRepo;
    private final UserRepo userRepo;
    private final SocialPostRepo socialPostRepo;
    private final CommunityMemberRepo communityMemberRepo;
    private final NotificationService notificationService;

    @Async
    @EventListener
    public void handleCommunityDeleted(CommunityDeletedEvent event) {
        log.info("Processing CommunityDeletedEvent for communityId: {}", event.getCommunityId());
        
        try {
            // 1. Remove posts from feed
            socialPostRepo.removeCommunityPostsFromFeed(event.getCommunityId());
            
            // 2. Fetch community and owner
            Community community = communityRepo.findById(event.getCommunityId()).orElse(null);
            User requester = userRepo.findById(event.getRequesterId()).orElse(null);
            
            if (community == null || requester == null) {
                log.warn("Community or Requester not found during delete event processing.");
                return;
            }

            // 3. Notify members in chunks
            int page = 0;
            int size = 500;
            Slice<Long> memberIdsSlice;
            
            do {
                memberIdsSlice = processNotificationChunk(community, requester, page, size);
                page++;
            } while (memberIdsSlice != null && memberIdsSlice.hasNext());

        } catch (Exception e) {
            log.error("Failed to process CommunityDeletedEvent for communityId: {}", event.getCommunityId(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Slice<Long> processNotificationChunk(Community community, User requester, int page, int size) {
        Slice<Long> memberIdsSlice = communityMemberRepo.findActiveMemberIdsByCommunityId(community.getId(), PageRequest.of(page, size));
        if (memberIdsSlice.hasContent()) {
            notificationService.notifyCommunityDeleted(community, requester, memberIdsSlice.getContent());
        }
        return memberIdsSlice;
    }

    @Async
    @EventListener
    public void handleCommunityRevoked(CommunityRevokedEvent event) {
        log.info("Processing CommunityRevokedEvent for communityId: {}", event.getCommunityId());
        try {
            Community community = communityRepo.findById(event.getCommunityId()).orElse(null);
            if (community != null) {
                socialPostRepo.syncCommunityDenormalizedFields(
                        community.getId(), 
                        community.getPrivacy() != null ? community.getPrivacy().name() : null, 
                        community.isFeedEligible(),
                        community.getStatus() != null ? community.getStatus().name() : null
                );
            }
        } catch (Exception e) {
            log.error("Failed to process CommunityRevokedEvent for communityId: {}", event.getCommunityId(), e);
        }
    }
}
