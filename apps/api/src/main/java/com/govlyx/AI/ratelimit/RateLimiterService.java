package com.Govlyx.AI.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RateLimiterService
 * ══════════════════════════════════════════════════════════════════════════════
 * Sliding-window rate limiter backed by an in-process Caffeine cache.
 *
 * WHY SLIDING WINDOW?
 *   A fixed window allows a "boundary burst": a client can fire MAX_REQUESTS
 *   at second :59 and another MAX_REQUESTS at second 1:01 — 2× the limit in
 *   two seconds with no violation detected.  The sliding window eliminates
 *   this by measuring any rolling 60-second interval.
 *
 * WHY DIRECT CAFFEINE (not Spring CacheManager)?
 *   Spring's CacheManager abstracts away the raw Cache<K,V> interface, which
 *   prevents atomic get-or-create semantics we need for RequestWindow. We use
 *   the same Caffeine library already declared in CacheConfig — zero new deps.
 *
 * THREAD SAFETY
 *   - Caffeine's Cache.get(key, mappingFn) is thread-safe for key creation.
 *   - RequestWindow.tryAcquire() is synchronized on the instance, making the
 *     evict → check → add triplet atomic per key.
 *   - Contention is per-user/IP key, NOT global — no shared lock bottleneck.
 *
 * MEMORY BUDGET (worst case)
 *   100 timestamps × 8 B (Long) = 800 B per active key.
 *   200 000 keys × 800 B ≈ 160 MB — acceptable on a standard JVM heap.
 *   Caffeine evicts idle keys after IDLE_EXPIRE_MINUTES automatically.
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Service
@Slf4j
public class RateLimiterService {

    // ── Tunables (change here; nowhere else) ───────────────────────────────────

    /** Maximum requests allowed per key within WINDOW_SIZE_MS. */
    public static final int  MAX_REQUESTS            = 1000;

    /** Rolling window duration in milliseconds (60 000 = 1 minute). */
    public static final long WINDOW_SIZE_MS          = 60_000L;

    /** Nanosecond equivalent — used with System.nanoTime() for monotonic timing. */
    private static final long WINDOW_SIZE_NS         = WINDOW_SIZE_MS * 1_000_000L;

    /**
     * A key that receives zero traffic for this many minutes is evicted.
     * Prevents unbounded heap growth for transient/bursty anonymous clients.
     */
    private static final long IDLE_EXPIRE_MINUTES    = 10L;

    /** Hard cap on distinct rate-limit keys held in memory simultaneously. */
    private static final long MAX_KEYS               = 200_000L;

    // ── State ──────────────────────────────────────────────────────────────────

    /**
     * Per-key sliding-window registry.
     *
     * We do NOT use Spring's CacheManager here — see class Javadoc for reason.
     * The Caffeine library is already on the classpath via CacheConfig.
     */
    private Cache<String, RequestWindow> windowCache;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @PostConstruct
    void init() {
        windowCache = Caffeine.newBuilder()
                .maximumSize(MAX_KEYS)
                .expireAfterAccess(IDLE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats()   // hit/miss ratios visible via Spring Actuator /actuator/metrics
                .build();

        log.info("[RateLimiter] Initialized: limit={} req / {} ms, maxKeys={}, idleEvict={}min",
                MAX_REQUESTS, WINDOW_SIZE_MS, MAX_KEYS, IDLE_EXPIRE_MINUTES);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Attempts to consume one request token for the given key.
     *
     * @param key  Unique bucket identifier — "user:<id>" or "ip:<address>"
     * @return     {@code true}  → request is within limit, allow it
     *             {@code false} → rate limit exceeded, return HTTP 429
     */
    public boolean tryConsume(String key) {
        // get() is atomic: creates the window on first access
        RequestWindow window = windowCache.get(key, k -> new RequestWindow());
        // window is never null because the mapping function always returns a new instance
        return window.tryAcquire();
    }

    /**
     * Returns how many requests this key has made in the current rolling window.
     * Used to populate the {@code X-RateLimit-Remaining} response header.
     *
     * Returns 0 for unknown keys (no requests yet).
     */
    public int currentCount(String key) {
        RequestWindow window = windowCache.getIfPresent(key);
        if (window == null) return 0;
        return window.currentCount();
    }

    // ── Inner class — per-key sliding window ───────────────────────────────────

    /**
     * RequestWindow holds the monotonic nanosecond timestamps of all requests
     * that fall inside the current rolling window for one key.
     *
     * DATA STRUCTURES
     *   ConcurrentLinkedDeque  — timestamps added to tail, expired ones polled
     *                            from head (FIFO order by time).
     *   AtomicInteger count    — separate size counter so we avoid O(n)
     *                            deque.size() traversal on every request.
     *
     * SYNCHRONIZATION
     *   The synchronized(this) guard on tryAcquire() keeps the
     *   "evict old → check size → add new" sequence atomic per instance.
     *   Without it, two threads on the same key could both read count < MAX,
     *   both pass the check, and both add — letting count reach MAX+1 briefly.
     *   Since lock scope is per-window (per-user), this does not create a
     *   global bottleneck.
     */
    static final class RequestWindow {

        /** Nanosecond timestamps of requests within the active window. */
        private final ConcurrentLinkedDeque<Long> timestamps = new ConcurrentLinkedDeque<>();

        /** Independent size tracker — avoids O(n) deque.size() calls. */
        private final AtomicInteger count = new AtomicInteger(0);

        /**
         * Atomically: evict stale entries → check limit → conditionally record.
         *
         * @return true if the request is allowed, false if the limit is exceeded
         */
        synchronized boolean tryAcquire() {
            evictExpired();

            if (count.get() >= MAX_REQUESTS) {
                return false; // 429
            }

            timestamps.addLast(System.nanoTime());
            count.incrementAndGet();
            return true;
        }

        /** Returns the number of requests in the current rolling window. */
        synchronized int currentCount() {
            evictExpired();
            return count.get();
        }

        /** Removes timestamps that have fallen outside the rolling window. */
        private void evictExpired() {
            long cutoff = System.nanoTime() - WINDOW_SIZE_NS;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst();
                count.decrementAndGet();
            }
        }
    }
}
