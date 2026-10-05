package com.govlyx.AI.service;

import com.govlyx.AI.model.ChatSession;
import com.govlyx.AI.model.ChatSessionAudit;
import com.govlyx.AI.model.User;
import com.govlyx.AI.repository.ChatSessionAuditRepo;
import com.govlyx.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatSessionService — Audit Exclusion for user<number>@gmail.com")
class ChatSessionAuditExclusionTest {

    @Mock
    private UserRepo userRepository;

    @Mock
    private ChatSessionAuditRepo chatSessionAuditRepo;

    @Mock
    private ChatMessagingService chatMessagingService;

    private ChatSessionService chatSessionService;

    @BeforeEach
    void setUp() {
        chatSessionService = new ChatSessionService(
                userRepository,
                chatSessionAuditRepo,
                chatMessagingService,
                "^(user\\d+|usersomenumer)@gmail\\.com$"
        );
    }

    private User buildUser(Long id, String email) {
        return User.builder()
                .id(id)
                .username("user_" + id)
                .email(email)
                .build();
    }

    @Test
    @DisplayName("✅ Normal users: audit IS saved")
    void normalUsers_auditSaved() {
        User user1 = buildUser(1L, "alice@example.com");
        User user2 = buildUser(2L, "bob@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));

        ChatSession session = chatSessionService.createSession(1L, 2L);

        assertNotNull(session);
        verify(chatSessionAuditRepo, times(1)).save(any(ChatSessionAudit.class));
    }

    @Test
    @DisplayName("❌ User 1 matches user<number>@gmail.com: audit is SKIPPED")
    void user1MatchesExclusion_auditSkipped() {
        User user1 = buildUser(10L, "user123@gmail.com");
        User user2 = buildUser(20L, "charlie@example.com");

        when(userRepository.findById(10L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user2));

        ChatSession session = chatSessionService.createSession(10L, 20L);

        assertNotNull(session);
        verify(chatSessionAuditRepo, never()).save(any(ChatSessionAudit.class));
    }

    @Test
    @DisplayName("❌ User 2 matches user<number>@gmail.com: audit is SKIPPED")
    void user2MatchesExclusion_auditSkipped() {
        User user1 = buildUser(11L, "david@example.com");
        User user2 = buildUser(21L, "user99@gmail.com");

        when(userRepository.findById(11L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(21L)).thenReturn(Optional.of(user2));

        ChatSession session = chatSessionService.createSession(11L, 21L);

        assertNotNull(session);
        verify(chatSessionAuditRepo, never()).save(any(ChatSessionAudit.class));
    }

    @Test
    @DisplayName("❌ User matches usersomenumer@gmail.com: audit is SKIPPED")
    void usersomenumerLiteral_auditSkipped() {
        User user1 = buildUser(12L, "usersomenumer@gmail.com");
        User user2 = buildUser(22L, "eve@example.com");

        when(userRepository.findById(12L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(22L)).thenReturn(Optional.of(user2));

        ChatSession session = chatSessionService.createSession(12L, 22L);

        assertNotNull(session);
        verify(chatSessionAuditRepo, never()).save(any(ChatSessionAudit.class));
    }

    @Test
    @DisplayName("❌ Case-insensitive matching (User777@GMAIL.COM): audit is SKIPPED")
    void caseInsensitiveMatch_auditSkipped() {
        User user1 = buildUser(13L, "User777@GMAIL.COM");
        User user2 = buildUser(23L, "frank@example.com");

        when(userRepository.findById(13L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(23L)).thenReturn(Optional.of(user2));

        ChatSession session = chatSessionService.createSession(13L, 23L);

        assertNotNull(session);
        verify(chatSessionAuditRepo, never()).save(any(ChatSessionAudit.class));
    }

    @Test
    @DisplayName("Direct helper isExcludedFromAudit tests")
    void testIsExcludedFromAuditPattern() {
        assertTrue(chatSessionService.isExcludedFromAudit("user1@gmail.com"));
        assertTrue(chatSessionService.isExcludedFromAudit("user99999@gmail.com"));
        assertTrue(chatSessionService.isExcludedFromAudit("USER01@GMAIL.COM"));
        assertTrue(chatSessionService.isExcludedFromAudit("usersomenumer@gmail.com"));

        assertFalse(chatSessionService.isExcludedFromAudit("user@gmail.com"));
        assertFalse(chatSessionService.isExcludedFromAudit("user123@yahoo.com"));
        assertFalse(chatSessionService.isExcludedFromAudit("myuser1@gmail.com"));
        assertFalse(chatSessionService.isExcludedFromAudit("alice@example.com"));
        assertFalse(chatSessionService.isExcludedFromAudit(null));
        assertFalse(chatSessionService.isExcludedFromAudit("   "));
    }
}
