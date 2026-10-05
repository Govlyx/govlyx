package com.JanSahayak.AI.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class ChatScheduledTasks {

    private final ChatSessionService chatSessionService;
    private final MatchmakingService matchmakingService;

    /** Clean up expired (inactive too long) sessions every 5 minutes. */
    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredSessions() {
        log.debug("Running scheduled cleanup of expired chat sessions");
        try {
            chatSessionService.cleanupExpiredSessions();
        } catch (Exception e) {
            log.error("Error during scheduled session cleanup", e);
        }
    }

    /** Remove ended sessions from memory every 2 minutes. */
    @Scheduled(fixedRate = 120_000)
    public void cleanupEndedSessions() {
        log.debug("Running scheduled cleanup of ended chat sessions");
        try {
            chatSessionService.cleanupEndedSessions();
        } catch (Exception e) {
            log.error("Error during scheduled ended session cleanup", e);
        }
    }

    /**
     * Finalize sessions whose disconnected user never reconnected within the
     * grace period.  Runs every 15 seconds so the partner isn't left hanging
     * for more than one grace-period length + 15 s.
     */
    @Scheduled(fixedRate = 15_000)
    public void cleanupDisconnectedSessions() {
        log.debug("Running scheduled cleanup of grace-period-expired disconnected sessions");
        try {
            chatSessionService.cleanupDisconnectedSessions();
        } catch (Exception e) {
            log.error("Error during scheduled disconnected-session cleanup", e);
        }
    }

    /** Remove expired matchmaking queue entries every 2 minutes. */
    @Scheduled(fixedRate = 120_000)
    public void cleanupExpiredMatchmaking() {
        log.debug("Running scheduled cleanup of expired matchmaking entries");
        try {
            matchmakingService.cleanupExpiredSearches();
        } catch (Exception e) {
            log.error("Error during scheduled matchmaking cleanup", e);
        }
    }

    /** Log statistics every 10 minutes. */
    @Scheduled(fixedRate = 600_000)
    public void logStatistics() {
        try {
            int  activeSessions = chatSessionService.getActiveSessionCount();
            long queueSize      = matchmakingService.getQueueSize();
            log.info("Chat Statistics — Active Sessions: {}, Waiting in Queue: {}",
                    activeSessions, queueSize);
        } catch (Exception e) {
            log.error("Error logging chat statistics", e);
        }
    }
}
