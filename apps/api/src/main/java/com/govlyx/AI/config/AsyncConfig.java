package com.govlyx.AI.config;

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

    // ── Dedicated @Scheduled thread pool ─────────────────────────────────────
    //
    // WHY THIS MATTERS (Scalability audit M-3):
    // Spring Boot's default scheduler is a single-threaded executor shared by
    // every @Scheduled method in the application. There are 15+ scheduled tasks:
    //   • chatCleanup        — every 15 s
    //   • communityDeletion  — every 5 min
    //   • viewCountFlush     — every 30 s
    //   • interestDecay      — nightly cron
    //   • topicPruner        — nightly cron
    //   • mediRetention      — nightly cron
    //   • notifCleanup       — every 6 h
    //   • ... and more
    //
    // If any one task is slow (e.g. a community purge iterating thousands of
    // rows), all other tasks queue behind it. The 15-second chat-session cleanup
    // could be delayed by minutes during a peak purge window.
    //
    // This bean replaces the default single-thread scheduler with a pool of 5.
    // Spring Boot detects TaskScheduler beans automatically and uses this instead.

    @Bean
    public org.springframework.scheduling.TaskScheduler taskScheduler() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("sched-");
        // On JVM shutdown, wait up to 30 s for in-progress scheduled tasks to finish
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setErrorHandler(t ->
                org.slf4j.LoggerFactory.getLogger(AsyncConfig.class)
                        .error("[Scheduler] Unhandled exception in @Scheduled task", t));
        scheduler.initialize();
        return scheduler;
    }
}
