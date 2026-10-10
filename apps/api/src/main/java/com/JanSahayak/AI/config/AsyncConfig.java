package com.JanSahayak.AI.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AsyncConfig — Bounded thread-pool executors for all @Async methods.
 *
 * WHY THIS MATTERS:
 * ─────────────────────────────────────────────────────────────────────────────
 * Without this, every @Async call uses Spring's SimpleAsyncTaskExecutor which
 * spawns a NEW OS thread per invocation — NO pooling, NO limit, NO reuse.
 * Cloudinary uploads, notification dispatch, HLIG scoring, email sends all
 * fire unbounded threads under load → thread explosion → JVM OOM crash.
 *
 * THREE POOLS:
 * ─────────────────────────────────────────────────────────────────────────────
 *   "taskExecutor"          General-purpose async tasks (default @Async pool)
 *   "mediaExecutor"         Cloudinary uploads / media processing (I/O heavy)
 *   "notificationExecutor"  Push notifications + email + WebSocket dispatch
 *
 * USAGE:
 *   @Async                          → uses "taskExecutor" (default)
 *   @Async("mediaExecutor")         → Cloudinary, video processing
 *   @Async("notificationExecutor")  → FCM, email, WebSocket push
 *
 * CallerRunsPolicy — when the queue is full the CALLING thread executes the
 * task itself instead of dropping it. This provides natural back-pressure
 * without data loss.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    // ── Default executor (general-purpose async) ──────────────────────────────

    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);           // threads kept alive always
        executor.setMaxPoolSize(50);            // burst capacity under load
        executor.setQueueCapacity(500);         // backpressure buffer
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    // ── Cloudinary / media I/O pool ───────────────────────────────────────────
    // Isolated so heavy upload I/O never starves regular app threads.

    @Bean(name = "mediaExecutor")
    public Executor mediaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("media-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    // ── Notification / email dispatch pool ────────────────────────────────────
    // High queue depth — notifications are fire-and-forget, latency-tolerant.

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(1_000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("notif-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
