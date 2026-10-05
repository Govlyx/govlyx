package com.Govlyx.AI.service;

import com.Govlyx.AI.enums.PassTier;
import com.Govlyx.AI.exception.PlanLimitExceededException;
import com.Govlyx.AI.model.UserPass;
import com.Govlyx.AI.repository.ChatSessionAuditRepo;
import com.Govlyx.AI.repository.UserPassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;

@Service
public class PlanEnforcementService {

    private PlanEnforcementService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy PlanEnforcementService self) {
        this.self = self;
    }

    private final UserPassRepository userPassRepository;
    private final ChatSessionAuditRepo chatSessionAuditRepo;
    private final int freeDailyMatchLimit;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanEnforcementService(
            UserPassRepository userPassRepository,
            ChatSessionAuditRepo chatSessionAuditRepo,
            @org.springframework.beans.factory.annotation.Value("${govlyx.monetization.free-daily-match-limit:5}") int freeDailyMatchLimit) {
        this.userPassRepository = userPassRepository;
        this.chatSessionAuditRepo = chatSessionAuditRepo;
        this.freeDailyMatchLimit = freeDailyMatchLimit;
    }

    @Cacheable(value = "userTiers", key = "#userId")
    public PassTier getUserTier(Long userId) {
        /* --- MONETIZATION DISABLED ---
        return userPassRepository.findActivePassByUserId(userId)
                .map(UserPass::getTier)
                .orElse(PassTier.GOVLYX_FREE);
        */
        return PassTier.GOVLYX_VIP;
    }

    public boolean canCreateExclusiveCommunity(Long userId) {
        /* --- MONETIZATION DISABLED ---
        Optional<UserPass> passOpt = userPassRepository.findActivePassByUserId(userId);
        return passOpt.isPresent() && passOpt.get().getPrivateCommunityQuota() > 0;
        */
        return true;
    }

    public boolean canSendChatMedia(Long userId, String sessionId) {
        /* --- MONETIZATION DISABLED ---
        PassTier tier = self.getUserTier(userId);
        if (tier == PassTier.GOVLYX_PRO || tier == PassTier.GOVLYX_VIP) {
            return true;
        }

        // For FREE tier:
        Optional<com.Govlyx.AI.model.ChatSessionAudit> auditOpt = chatSessionAuditRepo.findBySessionId(sessionId);
        if (auditOpt.isPresent()) {
            com.Govlyx.AI.model.ChatSessionAudit audit = auditOpt.get();
            // If they already used media in this session, allow
            if (userId.equals(audit.getUser1Id()) && audit.isUser1UsedMedia()) return true;
            if (userId.equals(audit.getUser2Id()) && audit.isUser2UsedMedia()) return true;
        }

        // Otherwise, check if they have used media in < 5 sessions in the last 24h
        Date since = Date.from(Instant.now().minus(24, ChronoUnit.HOURS));
        int mediaCount = chatSessionAuditRepo.countMediaSessionsForUserSince(userId, since);
        if (mediaCount >= 5) {
            throw new PlanLimitExceededException("You have reached your free daily limit of 5 chats with media. Upgrade to Govlyx Pro for unlimited media in all chats.");
        }
        */
        return true;
    }

    public boolean canUseMatchFilters(Long userId) {
        /* --- MONETIZATION DISABLED ---
        PassTier tier = self.getUserTier(userId);
        return tier == PassTier.GOVLYX_PRO || tier == PassTier.GOVLYX_VIP;
        */
        return true;
    }

    public boolean canSetDisappearingMessages(Long userId) {
        /* --- MONETIZATION DISABLED ---
        return self.getUserTier(userId) == PassTier.GOVLYX_VIP;
        */
        return true;
    }

    public boolean canPinMessages(Long userId) {
        /* --- MONETIZATION DISABLED ---
        return self.getUserTier(userId) == PassTier.GOVLYX_VIP;
        */
        return true;
    }

    public void enforceDailyMatchmakingLimit(Long userId) {
        /* --- MONETIZATION DISABLED ---
        PassTier tier = self.getUserTier(userId);
        if (tier == PassTier.GOVLYX_FREE) {
            Date since = Date.from(Instant.now().minus(24, ChronoUnit.HOURS));
            int matchCount = chatSessionAuditRepo.countSessionsForUserSince(userId, since);
            if (matchCount >= freeDailyMatchLimit) {
                throw new PlanLimitExceededException("You have reached your daily matchmaking limit of " + freeDailyMatchLimit + ". Upgrade to Govlyx Pro for unlimited matches.");
            }
        }
        */
    }

    // A secret community is frozen if the owner doesn't have an active pass
    public boolean isCommunityFrozen(Long ownerId) {
        /* --- MONETIZATION DISABLED ---
        PassTier tier = self.getUserTier(ownerId);
        return tier == PassTier.GOVLYX_FREE; // For now, if owner drops to FREE, frozen
        */
        return false;
    }
}
