package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.ChatSession;
import com.JanSahayak.AI.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the matchmaking queue for anonymous 1-vs-1 chats.
 *
 * ── Reconnect awareness ───────────────────────────────────────────────────────
 * Before adding a user to the queue, findMatch() first checks whether they have
 * a disconnected-but-still-valid session (e.g. they just refreshed the page).
 * If so, reconnectToSession() is called and the existing sessionId is returned
 * immediately — no new match is needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchmakingService {

    private final ChatSessionService chatSessionService;
    private final PinCodeLookupService pinCodeLookupService;
    private final PlanEnforcementService planEnforcementService;

    // Use PriorityBlockingQueue for VIP priority matchmaking
    private final java.util.concurrent.PriorityBlockingQueue<QueueEntry> waitingQueue = new java.util.concurrent.PriorityBlockingQueue<>();
    private final Map<Long, QueueEntry>  searchingUsers = new ConcurrentHashMap<>();
    private final ReentrantLock          matchmakingLock = new ReentrantLock();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Entry point called when a user wants to start / resume a chat.
     *
     * Priority order:
     *  1. User has a disconnected session within its grace period → reconnect.
     *  2. User is already queued                                  → no-op (return null).
     *  3. Another user is waiting                                 → pair them.
     *  4. No one waiting                                          → add to queue.
     *
     * @return sessionId if matched/reconnected, null if added to the waiting queue.
     */
    public String findMatch(User user) {
        Long userId = user.getId();
        log.info("User {} entering matchmaking", userId);

        // ── 1. Reconnect to an existing disconnected session ──────────────────
        if (chatSessionService.isUserDisconnectedWithActiveSession(userId)) {
            ChatSession resumed = chatSessionService.reconnectToSession(userId);
            if (resumed != null) {
                log.info("User {} reconnected to existing session {}", userId, resumed.getSessionId());
                return resumed.getSessionId();
            }
            // Grace period expired between the check and the call — fall through to normal matchmaking
        }

        // ── 2. Already queued ─────────────────────────────────────────────────
        if (isUserSearching(userId)) {
            log.warn("User {} is already in matchmaking queue", userId);
            return null;
        }

        // ── Enforce Matchmaking Limits ────────────────────────────────────────
        planEnforcementService.enforceDailyMatchmakingLimit(userId);

        // ── 3 & 4. Normal matchmaking ─────────────────────────────────────────
        LocalityMetadata requesterLocality = extractLocality(user);

        matchmakingLock.lock();
        try {
            QueueEntry partner = findBestPartner(userId, requesterLocality);

            if (partner != null) {
                log.info("Match found (Tier {}): user {} paired with user {} | partnerPincode={}", 
                        partner.lastMatchTier, userId, partner.getUserId(), partner.pincode);
                // MEMORY LEAK FIX: wrap createSession() in try/finally so BOTH users are
                // always removed from searchingUsers even when createSession() throws
                // (e.g. race condition where one user is already in a session).
                // Without this, a RuntimeException leaves both entries in the map
                // permanently — isUserSearching() returns true forever, blocking
                // both users from ever matching again until a server restart.
                try {
                    ChatSession session = chatSessionService.createSession(userId, partner.getUserId());
                    return session.getSessionId();
                } finally {
                    removeFromSearch(userId);
                    removeFromSearch(partner.getUserId());
                }
            } else {
                addToQueue(userId, requesterLocality);
                log.info("User {} added to queue — waiting for partner | pincode={}", userId, user.getPincode());
                return null;
            }
        } finally {
            matchmakingLock.unlock();
        }
    }

    public void cancelSearch(Long userId) {
        log.info("User {} cancelling matchmaking search", userId);
        matchmakingLock.lock();
        try {
            removeFromQueue(userId);
            removeFromSearch(userId);
        } finally {
            matchmakingLock.unlock();
        }
    }

    public boolean isUserSearching(Long userId) {
        QueueEntry entry = searchingUsers.get(userId);
        if (entry == null) return false;
        if (entry.hasExpired()) {
            removeFromSearch(userId);
            return false;
        }
        return true;
    }

    public long getQueueSize() {
        return waitingQueue.size();
    }

    public void cleanupExpiredSearches() {
        List<Long> expiredUsers = new ArrayList<>();
        for (Map.Entry<Long, QueueEntry> entry : searchingUsers.entrySet()) {
            if (entry.getValue().hasExpired()) expiredUsers.add(entry.getKey());
        }

        matchmakingLock.lock();
        try {
            for (Long userId : expiredUsers) {
                removeFromQueue(userId);
                removeFromSearch(userId);
            }
        } finally {
            matchmakingLock.unlock();
        }

        if (!expiredUsers.isEmpty()) {
            log.info("Cleaned up {} expired matchmaking entries", expiredUsers.size());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Tiered Search: Pincode -> Nearby -> District -> Nearby District -> State -> Nearby State -> Global.
     * Iterates the queue once and picks the candidate with the highest matching tier.
     */
    private QueueEntry findBestPartner(Long userId, LocalityMetadata a) {
        QueueEntry bestCandidate = null;
        int bestTier = 8; // Start below the lowest priority (Global is 7)

        for (QueueEntry b : waitingQueue) {
            if (b.getUserId().equals(userId)) continue; // Self-match check
            if (b.hasExpired()) continue; // Skip expired (cleaned up elsewhere)

            int tier = calculateMatchTier(a, b);
            
            // Tier 1 is highest priority.
            if (tier < bestTier) {
                bestTier = tier;
                bestCandidate = b;
                // Optimization: Short-circuit if we find an exact Pincode match (Tier 1)
                if (bestTier == 1) break;
            }
        }

        if (bestCandidate != null) {
            bestCandidate.lastMatchTier = bestTier;
            waitingQueue.remove(bestCandidate);
            return bestCandidate;
        }

        return null;
    }

    private int calculateMatchTier(LocalityMetadata a, QueueEntry b) {
        // Tier 1: Same Pincode
        if (Objects.equals(a.pincode, b.pincode)) return 1;

        // Tier 2: Nearby Pincode (radius 20km)
        if (a.nearbyPincodes.contains(b.pincode)) return 2;

        // Tier 3: Same District (3-digit prefix)
        if (Objects.equals(a.districtPrefix, b.districtPrefix)) return 3;

        // Tier 4: Nearby District (Same State prefix but different district)
        if (Objects.equals(a.statePrefix, b.statePrefix)) return 4;

        // Tier 5: Same State (Should be caught by Tier 4, but added for safety if prefix logic varies)
        // (In India, 2-digit prefix is Circle which corresponds to State)
        if (Objects.equals(a.statePrefix, b.statePrefix)) return 5;

        // Tier 6: Nearby State (Same Region prefix - 1 digit)
        if (Objects.equals(a.regionPrefix, b.regionPrefix)) return 6;

        // Tier 7: Global Fallback
        return 7;
    }

    private void addToQueue(Long userId, LocalityMetadata locality) {
        if (searchingUsers.containsKey(userId)) {
            log.debug("addToQueue: userId={} already in searchingUsers — skipping duplicate add", userId);
            return;
        }
        com.JanSahayak.AI.enums.PassTier userTier = planEnforcementService.getUserTier(userId);
        QueueEntry entry = new QueueEntry(userId, locality, userTier);
        waitingQueue.offer(entry);
        searchingUsers.put(userId, entry);
    }

    private LocalityMetadata extractLocality(User user) {
        String pc = user.getPincode();
        return new LocalityMetadata(
                pc,
                pc != null && pc.length() >= 3 ? pc.substring(0, 3) : null,
                pc != null && pc.length() >= 2 ? pc.substring(0, 2) : null,
                pc != null && pc.length() >= 1 ? pc.substring(0, 1) : null,
                pinCodeLookupService.getNearbyPincodeStrings(pc)
        );
    }

    private record LocalityMetadata(
            String pincode,
            String districtPrefix,
            String statePrefix,
            String regionPrefix,
            Set<String> nearbyPincodes
    ) {}

    private void removeFromQueue(Long userId) {
        waitingQueue.removeIf(e -> e.getUserId().equals(userId));
    }

    private void removeFromSearch(Long userId) {
        searchingUsers.remove(userId);
    }

    // ── Inner class ───────────────────────────────────────────────────────────

    private static class QueueEntry implements Comparable<QueueEntry> {
        private final Long    userId;
        private final Instant joinedAt;
        private final com.JanSahayak.AI.enums.PassTier tier;

        // Locality metadata
        private final String  pincode;
        private final String  districtPrefix;
        private final String  statePrefix;
        private final String  regionPrefix;
        private final Set<String> nearbyPincodes;

        // Transient match tracking
        private int lastMatchTier = 7;

        QueueEntry(Long userId, LocalityMetadata meta, com.JanSahayak.AI.enums.PassTier tier) {
            this.userId         = userId;
            this.joinedAt       = Instant.now();
            this.tier           = tier != null ? tier : com.JanSahayak.AI.enums.PassTier.GOVLYX_FREE;
            this.pincode        = meta.pincode();
            this.districtPrefix = meta.districtPrefix();
            this.statePrefix    = meta.statePrefix();
            this.regionPrefix   = meta.regionPrefix();
            this.nearbyPincodes = meta.nearbyPincodes();
        }

        Long getUserId() { return userId; }

        boolean hasExpired() {
            return Instant.now().isAfter(
                    joinedAt.plusSeconds(Constant.MATCHMAKING_SEARCH_TIMEOUT_SECONDS));
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return Objects.equals(userId, ((QueueEntry) o).userId);
        }

        @Override public int hashCode() { return Objects.hash(userId); }

        @Override
        public int compareTo(QueueEntry other) {
            // Higher tier = priority (GOVLYX_VIP > GOVLYX_PRO > GOVLYX_FREE)
            // Enums are ordinal 0=FREE, 1=PRO, 2=VIP, so we compare other.ordinal to this.ordinal
            int tierCompare = Integer.compare(other.tier.ordinal(), this.tier.ordinal());
            if (tierCompare != 0) {
                return tierCompare; // Higher tier comes first
            }
            // Same tier, older timestamp comes first (FIFO)
            return this.joinedAt.compareTo(other.joinedAt);
        }
    }
}
