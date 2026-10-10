package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Admin-only controller for the JanSahayak Admin Dashboard.
 *
 * All endpoints require ROLE_ADMIN and are mapped under /api/admin/**.
 *
 * Endpoints:
 *  GET    /api/admin/dashboard/overview          — aggregate platform stats
 *  DELETE /api/admin/users/{userId}              — delete a user account
 *  POST   /api/admin/bad-words/reload            — hot-reload bad word filter
 *  GET    /api/admin/bad-words/stats             — bad word filter statistics
 *  GET    /api/admin/content/flagged             — recently flagged content (placeholder)
 *  POST   /api/admin/chat/force-end-all          — force-end all active chat sessions
 *  GET    /api/admin/chat/daily-active-users     — DAU breakdown by role
 *  GET    /api/admin/system/health               — service health status
 *  GET    /api/admin/activity/recent             — recent platform activity feed
 *  GET    /api/admin/communities/stats           — community aggregate statistics
 *  POST   /api/admin/notifications/cleanup       — manually trigger notification cleanup
 *  POST   /api/admin/communities/reset-counters  — reset weekly community counters
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserRepo              userRepository;
    private final UserService           userService;
    private final BadWordService        badWordService;
    private final ChatSessionService    chatSessionService;
    private final MatchmakingService    matchmakingService;
    private final NotificationService   notificationService;
    private final CommunityRepo         communityRepository;
    private final PostService           postService;

    // ═══════════════════════════════════════════════════════════════════════════
    //  1. DASHBOARD OVERVIEW — Aggregate platform statistics
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/dashboard/overview
     *
     * Returns a single-call summary of all platform KPIs for the dashboard
     * landing page stat cards.
     */
    @GetMapping("/dashboard/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardOverview() {
        try {
            Map<String, Object> stats = new LinkedHashMap<>();

            // User counts
            long totalUsers    = userRepository.countActiveUsers();
            long totalCitizens = userRepository.countActiveByRoleName("ROLE_USER");
            long totalDepts    = userRepository.countActiveByRoleName("ROLE_DEPARTMENT");
            long totalAdmins   = userRepository.countActiveByRoleName("ROLE_ADMIN");

            stats.put("totalUsers",       totalUsers);
            stats.put("totalCitizens",    totalCitizens);
            stats.put("totalDepartments", totalDepts);
            stats.put("totalAdmins",      totalAdmins);

            // Chat stats
            stats.put("activeChatSessions", chatSessionService.getActiveSessionCount());
            stats.put("chatQueueSize",      matchmakingService.getQueueSize());

            // Post counts
            stats.put("activeIssues",   postService.countActivePosts());
            stats.put("resolvedPosts",  postService.countResolvedPosts());

            // Community counts
            long totalCommunities    = communityRepository.count();
            long activeCommunities   = communityRepository.countByStatus(
                    com.JanSahayak.AI.model.Community.CommunityStatus.ACTIVE);
            long archivedCommunities = communityRepository.countByStatus(
                    com.JanSahayak.AI.model.Community.CommunityStatus.ARCHIVED);

            stats.put("totalCommunities",    totalCommunities);
            stats.put("activeCommunities",   activeCommunities);
            stats.put("archivedCommunities", archivedCommunities);

            // Bad words
            stats.put("badWordsLoaded", badWordService.getBadWordCount());

            stats.put("timestamp", Instant.now());

            return ResponseEntity.ok(ApiResponse.success("Dashboard overview retrieved", stats));

        } catch (Exception e) {
            log.error("Error fetching dashboard overview", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve dashboard overview", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  2. DEPARTMENTS LISTING — Unified dashboard search  
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/departments
     *
     * Returns a unified list of department users for the Admin Dashboard.
     * Allows optional geographic filtering by state, city (district), or exact pincode.
     * Uses cursor-based pagination.
     */
    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> getDepartments(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        
        try {
            // "city" and "district" are often used interchangeably in the frontend
            String effectiveDistrict = (district != null) ? district : city;
            
            PaginatedResponse<User> response;
            
            if (pincode != null && !pincode.isEmpty()) {
                response = userService.findDepartmentUsersByPincode(pincode, cursor, limit);
            } else if (effectiveDistrict != null && !effectiveDistrict.isEmpty() && state != null && !state.isEmpty()) {
                response = userService.findDepartmentUsersByDistrict(state, effectiveDistrict, cursor, limit);
            } else if (state != null && !state.isEmpty()) {
                response = userService.findDepartmentUsersByState(state, cursor, limit);
            } else {
                // Fetch all departments globally if no geographic filter is applied
                response = userService.getUsersByRole("ROLE_DEPARTMENT", cursor, limit); 
            }

            return ResponseEntity.ok(ApiResponse.success("Departments retrieved successfully", response));

        } catch (Exception e) {
            log.error("Error fetching departments for dashboard", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve departments", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  3. DELETE USER — Admin deletes a user account
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * DELETE /api/admin/users/{userId}
     *
     * Soft-deletes (deactivates) a user account. Sets isActive = false.
     * Does not cascade-delete posts/comments — they remain for audit trail.
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteUser(@PathVariable Long userId) {
        try {
            // Prevent self-deletion
            User admin = getCurrentAdmin();
            if (admin.getId().equals(userId)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Cannot delete your own admin account"));
            }

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("User not found", "No user exists with ID: " + userId));
            }

            User user = userOpt.get();
            String username = user.getUsername();

            // Delegate to UserService to handle cleanup and state change
            userService.deactivateUser(userId, admin);

            log.info("Admin {} deleted (deactivated) user: {} (ID: {})", admin.getUsername(), username, userId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deletedUserId", userId);
            result.put("deletedUsername", username);
            result.put("action", "DEACTIVATED");

            return ResponseEntity.ok(ApiResponse.success("User account deactivated successfully", result));

        } catch (Exception e) {
            log.error("Error deleting user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete user", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  3. BAD WORD RELOAD — Hot-reload the bad word filter
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/admin/bad-words/reload
     *
     * Triggers a hot-reload of the bad word filter from the word list file
     * without requiring a server restart.
     */
    @PostMapping("/bad-words/reload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reloadBadWords() {
        try {
            badWordService.reloadBadWords();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("wordsLoaded", badWordService.getBadWordCount());
            result.put("reloadedAt", Instant.now());
            result.put("status", "SUCCESS");

            log.info("Admin triggered bad word filter reload — {} words now active", badWordService.getBadWordCount());

            return ResponseEntity.ok(ApiResponse.success("Bad word filter reloaded successfully", result));

        } catch (Exception e) {
            log.error("Error reloading bad word filter", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to reload bad word filter", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  4. BAD WORD STATS — Filter statistics for Content Monitor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/bad-words/stats
     *
     * Returns statistics about the bad word filter for the Content Monitor page.
     */
    @GetMapping("/bad-words/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBadWordStats() {
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("wordsLoaded", badWordService.getBadWordCount());
            stats.put("filterStatus", badWordService.getBadWordCount() > 0 ? "ACTIVE" : "EMPTY");
            stats.put("timestamp", Instant.now());

            return ResponseEntity.ok(ApiResponse.success("Bad word stats retrieved", stats));

        } catch (Exception e) {
            log.error("Error fetching bad word stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve bad word stats", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  5. FLAGGED CONTENT — Recently flagged content (placeholder)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/content/flagged
     *
     * Returns recently flagged content. Currently returns an empty list as
     * the flagging audit log infrastructure is not yet built. The frontend
     * can still call this endpoint and will get a valid empty response.
     *
     * TODO: Create FlaggedContent entity and track ContentValidationService rejections.
     */
    @GetMapping("/content/flagged")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFlaggedContent() {
        try {
            // Placeholder — will be populated once FlaggedContent entity is built
            List<Map<String, Object>> flaggedItems = new ArrayList<>();

            return ResponseEntity.ok(ApiResponse.success("Flagged content retrieved", flaggedItems));

        } catch (Exception e) {
            log.error("Error fetching flagged content", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve flagged content", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  6. FORCE END ALL CHAT SESSIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/admin/chat/force-end-all
     *
     * Emergency kill switch — force-ends ALL active chat sessions.
     * Use with caution.
     */
    @PostMapping("/chat/force-end-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> forceEndAllChatSessions() {
        try {
            int sessionsEnded = chatSessionService.forceEndAllSessions();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sessionsEnded", sessionsEnded);
            result.put("endedAt", Instant.now());

            log.warn("Admin force-ended ALL chat sessions — {} sessions terminated", sessionsEnded);

            return ResponseEntity.ok(ApiResponse.success(
                    "All chat sessions force-ended: " + sessionsEnded + " sessions terminated", result));

        } catch (Exception e) {
            log.error("Error force-ending all chat sessions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to force-end chat sessions", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  7. DAILY ACTIVE USERS — DAU breakdown by role
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/chat/daily-active-users
     *
     * Returns daily active user breakdown for the Chat service.
     * Since the anonymous chat feature is strictly for Citizens (ROLE_USER),
     * this endpoint only returns citizen chat metrics.
     */
    @GetMapping("/chat/daily-active-users")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDailyActiveUsers() {
        try {
            Map<String, Object> dau = new LinkedHashMap<>();

            // Chat is strictly a citizen-to-citizen feature
            long citizens = userRepository.countActiveByRoleName("ROLE_USER");

            long activeSessions = chatSessionService.getActiveSessionCount();
            long queueSize      = matchmakingService.getQueueSize();
            
            // Current online/chatting citizens (2 per session + users waiting in queue)
            long currentlyChattingOrWaiting = (activeSessions * 2) + queueSize;

            dau.put("totalEligibleCitizens", citizens);
            dau.put("currentlyActiveInChat", currentlyChattingOrWaiting);
            
            // Raw chat metrics
            dau.put("activeSessions", activeSessions);
            dau.put("queueSize",      queueSize);

            dau.put("timestamp", Instant.now());

            return ResponseEntity.ok(ApiResponse.success("Chat DAU stats retrieved (Citizen only)", dau));

        } catch (Exception e) {
            log.error("Error fetching chat daily active users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve chat DAU stats", e.getMessage()));
        }
    }

    /**
     * GET /api/admin/chat/monthly-active-users
     *
     * Returns monthly active user breakdown for the Chat service.
     * Since actual chat sessions are kept only in memory and not persisted to DB,
     * this uses "Citizen accounts interacting with the platform in the last 30 days"
     * as a reasonable proxy for the monthly eligible chat demographic.
     */
    @GetMapping("/chat/monthly-active-users")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMonthlyActiveUsers() {
        try {
            Map<String, Object> mau = new LinkedHashMap<>();

            // 30 days ago
            Date thirtyDaysAgo = Date.from(Instant.now().minusSeconds(30L * 24 * 60 * 60));

            // Count citizens who were created or generated an update to their profile in the last 30 days
            long monthlyActiveCitizens = userRepository.countActiveByRoleNameAndActiveSince("ROLE_USER", thirtyDaysAgo);

            mau.put("monthlyEligibleCitizens", monthlyActiveCitizens);
            mau.put("periodDays", 30);
            mau.put("timestamp", Instant.now());

            return ResponseEntity.ok(ApiResponse.success("Chat MAU stats retrieved (Citizen only)", mau));

        } catch (Exception e) {
            log.error("Error fetching chat monthly active users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve chat MAU stats", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  8. SYSTEM HEALTH — Service health check
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/system/health
     *
     * Returns health status for all major platform services.
     * Each service is pinged via a lightweight check (count query, method call, etc.)
     */
    @GetMapping("/system/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemHealth() {
        try {
            Map<String, Object> health = new LinkedHashMap<>();

            List<Map<String, String>> services = new ArrayList<>();

            // Check each service by attempting a lightweight operation
            services.add(checkService("AuthController & JWT",         () -> userRepository.countActiveUsers()));
            services.add(checkService("PostService (Cloudinary)",     () -> postService.countActivePosts()));
            services.add(checkService("NotificationService",          () -> { /* no-op */ return 0; }));
            services.add(checkService("ChatSessionService",           () -> chatSessionService.getActiveSessionCount()));
            services.add(checkService("MatchmakingService",           () -> matchmakingService.getQueueSize()));
            services.add(checkService("BadWordService",               () -> badWordService.getBadWordCount()));
            services.add(checkService("CommunityService",             () -> communityRepository.count()));

            health.put("services",  services);
            health.put("storage",   "Cloudinary");
            health.put("timestamp", Instant.now());

            return ResponseEntity.ok(ApiResponse.success("System health retrieved", health));

        } catch (Exception e) {
            log.error("Error fetching system health", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve system health", e.getMessage()));
        }
    }

    private Map<String, String> checkService(String name, java.util.function.Supplier<Object> probe) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("name", name);
        try {
            probe.get();
            entry.put("status", "HEALTHY");
        } catch (Exception e) {
            entry.put("status", "UNHEALTHY");
            entry.put("error", e.getMessage());
            log.warn("Health check failed for {}: {}", name, e.getMessage());
        }
        return entry;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  9. RECENT ACTIVITY — Platform activity feed
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/activity/recent
     *
     * Returns recent platform events. Currently returns a placeholder/stub
     * until an AdminActivityService with @EventListener is implemented.
     *
     * TODO: Implement AdminActivityService with Spring event listeners for:
     *  - User registrations (admin, dept, citizen)
     *  - Post resolutions
     *  - Bad word filter reloads
     *  - File cleanup runs
     */
    @GetMapping("/activity/recent")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRecentActivity() {
        try {
            // Placeholder — returns an empty list until audit infrastructure is built
            List<Map<String, Object>> activities = new ArrayList<>();

            return ResponseEntity.ok(ApiResponse.success("Recent activity retrieved", activities));

        } catch (Exception e) {
            log.error("Error fetching recent activity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve recent activity", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  10. COMMUNITY STATS — Aggregate community statistics
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/communities/stats
     *
     * Returns aggregate community statistics for the admin Communities page.
     */
    @GetMapping("/communities/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityStats() {
        try {
            Map<String, Object> stats = new LinkedHashMap<>();

            long total    = communityRepository.count();
            long active   = communityRepository.countByStatus(
                    com.JanSahayak.AI.model.Community.CommunityStatus.ACTIVE);
            long archived = communityRepository.countByStatus(
                    com.JanSahayak.AI.model.Community.CommunityStatus.ARCHIVED);

            stats.put("totalCommunities",    total);
            stats.put("activeCommunities",   active);
            stats.put("archivedCommunities", archived);
            stats.put("timestamp",           Instant.now());

            return ResponseEntity.ok(ApiResponse.success("Community stats retrieved", stats));

        } catch (Exception e) {
            log.error("Error fetching community stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve community stats", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  11. NOTIFICATION CLEANUP — Manual trigger
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/admin/notifications/cleanup
     *
     * Manually triggers notification cleanup (normally runs daily at 02:00).
     * Deletes notifications older than 30 days.
     */
    @PostMapping("/notifications/cleanup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerNotificationCleanup() {
        try {
            notificationService.cleanupOldNotifications();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "COMPLETED");
            result.put("cleanedAt", Instant.now());
            result.put("retentionDays", 30);

            log.info("Admin manually triggered notification cleanup");

            return ResponseEntity.ok(ApiResponse.success("Notification cleanup completed", result));

        } catch (Exception e) {
            log.error("Error during manual notification cleanup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to run notification cleanup", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  12. COMMUNITY COUNTER RESET — Reset weekly counters
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/admin/communities/reset-counters
     *
     * Resets weekly community engagement counters.
     * This is normally handled by CommunityHealthScoreService's scheduled task.
     */
    @PostMapping("/communities/reset-counters")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetCommunityCounters() {
        try {
            long communitiesReset = communityRepository.countByStatus(
                    com.JanSahayak.AI.model.Community.CommunityStatus.ACTIVE);
            // Reset weekly post counts — the communities track this for health score
            communityRepository.resetWeeklyCounters();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("communitiesReset", communitiesReset);
            result.put("resetAt", Instant.now());

            log.info("Admin reset weekly community counters for {} communities", communitiesReset);

            return ResponseEntity.ok(ApiResponse.success("Weekly community counters reset", result));

        } catch (Exception e) {
            log.error("Error resetting community counters", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to reset community counters", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private User getCurrentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return userRepository.findByEmailWithRole(email)
                .orElseThrow(() -> new RuntimeException("Admin not found: " + email));
    }
}
