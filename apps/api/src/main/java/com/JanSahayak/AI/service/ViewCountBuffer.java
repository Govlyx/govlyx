package com.JanSahayak.AI.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ViewCountBuffer — In-memory accumulator for post view counts.
 *
 * PROBLEM SOLVED:
 * ──────────────────────────────────────────────────────────────────────────────
 * Previously every single view fired:
 *   postRepository.incrementViewCount(postId);   → 1 SQL UPDATE per view
 *
 * Under viral load (10k views/min on a trending post) this creates:
 *   • 10,000 individual UPDATE statements per minute
 *   • PostgreSQL row-level locking contention on that single row
 *   • Cascading slowdown of all reads on the posts table
 *
 * SOLUTION:
 * ──────────────────────────────────────────────────────────────────────────────
 * Accumulate view counts in memory. ViewCountFlusher drains this buffer
 * every 30 seconds and issues exactly 1 UPDATE per post with the total.
 *
 *   10,000 individual UPDATEs  →  1 bulk UPDATE per post per 30 s
 *   Write amplification: -99%
 *
 * THREAD SAFETY:
 *   ConcurrentHashMap.computeIfAbsent + AtomicInteger.incrementAndGet()
 *   are both lock-free and safe under high concurrent access.
 *
 * DURABILITY:
 *   A server restart loses at most 30 s of buffered view counts.
 *   ViewCountFlusher.flushOnShutdown() is annotated with @PreDestroy to
 *   flush all pending counts before JVM exit (graceful shutdown).
 */
@Component
public class ViewCountBuffer {

    /** Buffer for regular Issue/Broadcast posts */
    private volatile ConcurrentHashMap<Long, AtomicInteger> postBuffer =
            new ConcurrentHashMap<>();

    /** Buffer for SocialPosts */
    private volatile ConcurrentHashMap<Long, AtomicInteger> socialPostBuffer =
            new ConcurrentHashMap<>();

    // ── Write side (called from PostInteractionService) ───────────────────────

    /**
     * Increment the buffered view count for a regular Post.
     * Lock-free, O(1).
     */
    public void recordPostView(Long postId) {
        if (postId == null) return;
        postBuffer.computeIfAbsent(postId, k -> new AtomicInteger(0))
                  .incrementAndGet();
    }

    /**
     * Increment the buffered view count for a SocialPost.
     * Lock-free, O(1).
     */
    public void recordSocialPostView(Long socialPostId) {
        if (socialPostId == null) return;
        socialPostBuffer.computeIfAbsent(socialPostId, k -> new AtomicInteger(0))
                        .incrementAndGet();
    }

    // ── Drain side (called from ViewCountFlusher) ─────────────────────────────

    /**
     * Atomically swap the post buffer and return the old snapshot.
     * The returned map contains each postId → count accumulated since the
     * last drain. After this call the live buffer is empty again.
     */
    public Map<Long, Integer> drainPostViews() {
        return drain(postBuffer, ref -> postBuffer = ref);
    }

    /**
     * Atomically swap the socialPost buffer and return the old snapshot.
     */
    public Map<Long, Integer> drainSocialPostViews() {
        return drain(socialPostBuffer, ref -> socialPostBuffer = ref);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Swap the live buffer with a fresh empty one, then flatten the old
     * buffer into a plain HashMap of id → count.
     *
     * @param setter  lambda that replaces the volatile field with the fresh map
     */
    private Map<Long, Integer> drain(
            ConcurrentHashMap<Long, AtomicInteger> liveBuffer,
            java.util.function.Consumer<ConcurrentHashMap<Long, AtomicInteger>> setter) {

        if (liveBuffer.isEmpty()) return Collections.emptyMap();

        // Swap: replace live reference with a fresh empty map.
        // Any views arriving after this point go into the new map.
        ConcurrentHashMap<Long, AtomicInteger> snapshot = liveBuffer;
        setter.accept(new ConcurrentHashMap<>());

        // Flatten AtomicInteger values into a plain int map
        Map<Long, Integer> result = new HashMap<>(snapshot.size() * 2);
        snapshot.forEach((id, counter) -> {
            int count = counter.get();
            if (count > 0) result.put(id, count);
        });
        return result;
    }
}
