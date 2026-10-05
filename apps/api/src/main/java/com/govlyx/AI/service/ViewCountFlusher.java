package com.govlyx.AI.service;

import com.govlyx.AI.repository.PostRepo;
import com.govlyx.AI.repository.SocialPostRepo;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * ViewCountFlusher — Periodically drains the ViewCountBuffer into the DB.
 *
 * Runs every 30 seconds via @Scheduled.
 * Also flushes on JVM shutdown (@PreDestroy) to preserve counts during
 * graceful restart (e.g. Render deploy / rolling update).
 *
 * DATABASE SIDE:
 *   Calls incrementViewCountBy(id, count) on PostRepo and SocialPostRepo.
 *   These are @Modifying JPQL UPDATE queries:
 *     UPDATE Post p SET p.viewCount = p.viewCount + :count WHERE p.id = :id
 *   One UPDATE per unique post ID — exactly the minimum required work.
 *
 * MONITORING:
 *   Logs total posts flushed and total view count delta each cycle for
 *   easy Kibana / Loki alerting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViewCountFlusher {

    private final ViewCountBuffer viewCountBuffer;
    private final PostRepo        postRepo;
    private final SocialPostRepo  socialPostRepo;

    // ── Scheduled flush ───────────────────────────────────────────────────────

    /**
     * Drain and persist buffered view counts every 30 seconds.
     * fixedDelay (not fixedRate) ensures the next run starts 30 s AFTER
     * the previous flush completes — no concurrent executions.
     */
    @Scheduled(fixedDelayString = "${app.view-count-flush-interval-ms:30000}")
    @Transactional
    public void flush() {
        flushPosts();
        flushSocialPosts();
    }

    // ── Shutdown hook ─────────────────────────────────────────────────────────

    /**
     * Flush remaining counts before JVM exit.
     * Protects against losing up to 30 s of view data on graceful restart.
     */
    @PreDestroy
    @Transactional
    public void flushOnShutdown() {
        log.info("[ViewCountFlusher] Shutdown flush started.");
        flushPosts();
        flushSocialPosts();
        log.info("[ViewCountFlusher] Shutdown flush complete.");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void flushPosts() {
        Map<Long, Integer> snapshot = viewCountBuffer.drainPostViews();
        if (snapshot.isEmpty()) return;

        int totalViews = 0;
        for (Map.Entry<Long, Integer> entry : snapshot.entrySet()) {
            try {
                postRepo.incrementViewCountBy(entry.getKey(), entry.getValue());
                totalViews += entry.getValue();
            } catch (Exception e) {
                log.warn("[ViewCountFlusher] Failed to flush views for postId={}: {}",
                        entry.getKey(), e.getMessage());
            }
        }
        log.debug("[ViewCountFlusher] Posts: flushed {} IDs, {} total views",
                snapshot.size(), totalViews);
    }

    private void flushSocialPosts() {
        Map<Long, Integer> snapshot = viewCountBuffer.drainSocialPostViews();
        if (snapshot.isEmpty()) return;

        int totalViews = 0;
        for (Map.Entry<Long, Integer> entry : snapshot.entrySet()) {
            try {
                socialPostRepo.incrementViewCountBy(entry.getKey(), entry.getValue());
                totalViews += entry.getValue();
            } catch (Exception e) {
                log.warn("[ViewCountFlusher] Failed to flush views for socialPostId={}: {}",
                        entry.getKey(), e.getMessage());
            }
        }
        log.debug("[ViewCountFlusher] SocialPosts: flushed {} IDs, {} total views",
                snapshot.size(), totalViews);
    }
}
