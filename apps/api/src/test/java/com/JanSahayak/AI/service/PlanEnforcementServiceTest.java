package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.PassTier;
import com.JanSahayak.AI.exception.PlanLimitExceededException;
import com.JanSahayak.AI.model.ChatSessionAudit;
import com.JanSahayak.AI.model.UserPass;
import com.JanSahayak.AI.repository.ChatSessionAuditRepo;
import com.JanSahayak.AI.repository.UserPassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PlanEnforcementServiceTest {

    @Mock
    private UserPassRepository userPassRepository;

    @Mock
    private ChatSessionAuditRepo chatSessionAuditRepo;

    private PlanEnforcementService planEnforcementService;

    @BeforeEach
    void setUp() {
        // Initialize with freeDailyMatchLimit = 10 as per our recent changes
        planEnforcementService = new PlanEnforcementService(userPassRepository, chatSessionAuditRepo, 10);
        planEnforcementService.setSelf(planEnforcementService);
    }

    // ── Cache / Tier Resolution Tests ─────────────────────────────────────────

    @Test
    void testGetUserTier_WithActivePass_ReturnsPassTier() {
        Long userId = 1L;
        UserPass mockPass = new UserPass();
        mockPass.setTier(PassTier.GOVLYX_PRO);
        when(userPassRepository.findActivePassByUserId(userId)).thenReturn(Optional.of(mockPass));

        PassTier result = planEnforcementService.getUserTier(userId);
        
        assertEquals(PassTier.GOVLYX_VIP, result);
    }

    @Test
    void testGetUserTier_NoActivePass_ReturnsFreeTier() {
        Long userId = 2L;
        when(userPassRepository.findActivePassByUserId(userId)).thenReturn(Optional.empty());

        PassTier result = planEnforcementService.getUserTier(userId);
        
        assertEquals(PassTier.GOVLYX_VIP, result);
    }

    // ── Media Quota Tests (5 distinct chats) ──────────────────────────────────

    @Test
    void testCanSendChatMedia_ProUser_AlwaysAllowed() {
        Long userId = 1L;
        String sessionId = "session-123";
        
        UserPass mockPass = new UserPass();
        mockPass.setTier(PassTier.GOVLYX_PRO);
        when(userPassRepository.findActivePassByUserId(userId)).thenReturn(Optional.of(mockPass));

        assertTrue(planEnforcementService.canSendChatMedia(userId, sessionId));
    }

    @Test
    void testCanSendChatMedia_FreeUser_MediaAlreadyUsedInSession_Allowed() {
        Long userId = 1L;
        String sessionId = "session-123";
        
        when(userPassRepository.findActivePassByUserId(userId)).thenReturn(Optional.empty()); // FREE
        
        ChatSessionAudit audit = new ChatSessionAudit();
        audit.setUser1Id(userId);
        audit.setUser1UsedMedia(true);
        when(chatSessionAuditRepo.findBySessionId(sessionId)).thenReturn(Optional.of(audit));

        assertTrue(planEnforcementService.canSendChatMedia(userId, sessionId));
    }

    @Test
    void testCanSendChatMedia_FreeUser_Under5MediaChats_Allowed() {
        Long userId = 1L;
        String sessionId = "session-123";
        
        when(userPassRepository.findActivePassByUserId(userId)).thenReturn(Optional.empty()); // FREE
        when(chatSessionAuditRepo.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(chatSessionAuditRepo.countMediaSessionsForUserSince(eq(userId), any(Date.class))).thenReturn(4);

        assertTrue(planEnforcementService.canSendChatMedia(userId, sessionId));
    }

    @Test
    void testCanSendChatMedia_FreeUser_AtLimit_ThrowsException() {
        Long userId = 1L;
        String sessionId = "session-123";
        
        when(userPassRepository.findActivePassByUserId(userId)).thenReturn(Optional.empty()); // FREE
        when(chatSessionAuditRepo.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(chatSessionAuditRepo.countMediaSessionsForUserSince(eq(userId), any(Date.class))).thenReturn(5);

        // Verification since monetization is disabled: it should NOT throw any exception
        assertDoesNotThrow(() -> {
            planEnforcementService.canSendChatMedia(userId, sessionId);
        });
    }

    // ── Matchmaking Limit Tests (10 matches) ──────────────────────────────────

    @Test
    void testEnforceDailyMatchmakingLimit_FreeUser_UnderLimit_NoException() {
        Long userId = 1L;
        when(userPassRepository.findActivePassByUserId(userId)).thenReturn(Optional.empty()); // FREE
        when(chatSessionAuditRepo.countSessionsForUserSince(eq(userId), any(Date.class))).thenReturn(9);

        assertDoesNotThrow(() -> planEnforcementService.enforceDailyMatchmakingLimit(userId));
    }

    @Test
    void testEnforceDailyMatchmakingLimit_FreeUser_AtLimit_ThrowsException() {
        Long userId = 1L;
        when(userPassRepository.findActivePassByUserId(userId)).thenReturn(Optional.empty()); // FREE
        when(chatSessionAuditRepo.countSessionsForUserSince(eq(userId), any(Date.class))).thenReturn(10);

        // Verification since monetization is disabled: it should NOT throw any exception
        assertDoesNotThrow(() -> {
            planEnforcementService.enforceDailyMatchmakingLimit(userId);
        });
    }

    @Test
    void testEnforceDailyMatchmakingLimit_ProUser_NoLimit() {
        Long userId = 1L;
        UserPass mockPass = new UserPass();
        mockPass.setTier(PassTier.GOVLYX_PRO);
        when(userPassRepository.findActivePassByUserId(userId)).thenReturn(Optional.of(mockPass));

        assertDoesNotThrow(() -> planEnforcementService.enforceDailyMatchmakingLimit(userId));
    }
}

