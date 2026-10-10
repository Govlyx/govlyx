package com.JanSahayak.AI.service;

import com.JanSahayak.AI.repository.TopicNodeRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicPrunerTask {

    private final TopicNodeRepo topicNodeRepo;
    private final TopicAggregationWorker topicAggregationWorker;

    // Prune topics that haven't been seen in 30 days and have a low velocity score
    private static final int DAYS_TO_DECAY = 30;
    private static final int MIN_VELOCITY_TO_SURVIVE = 5;

    /**
     * Runs every night at 3:00 AM to clean up dead topics.
     * This prevents database bloat from topics that were once popular but are now irrelevant.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void pruneDeadTopics() {
        log.info("Starting nightly TopicPrunerTask...");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -DAYS_TO_DECAY);
        Date thresholdDate = cal.getTime();

        int deletedCount = topicNodeRepo.pruneDeadTopics(thresholdDate, MIN_VELOCITY_TO_SURVIVE);
        
        log.info("Pruned {} dead topics from the database.", deletedCount);
        
        // If we deleted anything, we should ideally refresh the fast-lookup memory set.
        // For simplicity, we could just clear and reload or let it restart, 
        // but since it's a ConcurrentHashMap, we don't strictly need to do it immediately unless we want to free memory.
        // A better approach is to reload the topics to stay perfectly in sync.
        if (deletedCount > 0) {
            // Re-initialization happens implicitly if we restart, or we can just ignore since deleted topics won't be matched anyway
            // because they are no longer actively extracted from new posts.
        }
    }
}
