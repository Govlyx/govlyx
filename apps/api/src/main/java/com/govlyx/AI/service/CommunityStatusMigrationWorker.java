package com.Govlyx.AI.service;

import com.Govlyx.AI.repository.SocialPostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker to asynchronously backfill `communityStatus` on existing SocialPost records
 * in small chunks to avoid long table locks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityStatusMigrationWorker {

    private final SocialPostRepo socialPostRepo;
    
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private CommunityStatusMigrationWorker self;
    
    private static final int CHUNK_SIZE = 5000;

    /**
     * Start the migration automatically when the application is ready.
     * Uses @Async so it does not block the application startup.
     */
    @Async("taskExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void startMigration() {
        log.info("[Migration] Starting background backfill for communityStatus...");
        
        Long maxId = socialPostRepo.findMaxId();
        if (maxId == null || maxId == 0) {
            log.info("[Migration] No SocialPosts found. Migration skipped.");
            return;
        }

        long currentId = 0;
        int totalUpdated = 0;

        while (currentId <= maxId) {
            long endId = currentId + CHUNK_SIZE;
            int updated = self.executeChunk(currentId, endId);
            totalUpdated += updated;
            currentId = endId;
            
            // Sleep briefly to yield database connections
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Migration] Migration thread interrupted!");
                break;
            }
        }
        
        log.info("[Migration] Completed communityStatus backfill! Total rows updated: {}", totalUpdated);
    }

    /**
     * Executes a single chunk in a separate transaction.
     * REQUIRES_NEW ensures that each chunk is committed independently,
     * avoiding huge transaction logs (WAL) and lock escalation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int executeChunk(long startId, long endId) {
        try {
            return socialPostRepo.backfillCommunityStatusChunk(startId, endId);
        } catch (Exception e) {
            log.error("[Migration] Error processing chunk {}-{} : {}", startId, endId, e.getMessage());
            return 0;
        }
    }
}
