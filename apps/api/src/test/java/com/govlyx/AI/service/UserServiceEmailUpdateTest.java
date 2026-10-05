package com.Govlyx.AI.service;

import com.Govlyx.AI.exception.ValidationException;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Date;
import java.util.Optional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceEmailUpdateTest {

    @Mock
    private UserRepo userRepo;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = new User();
        com.Govlyx.AI.model.Role role = new com.Govlyx.AI.model.Role();
        role.setId(1L);
        role.setName("ROLE_USER");
        existingUser.setRole(role);
        existingUser.setId(1L);
        existingUser.setEmail("old@example.com");
    }

    @Test
    void updateUser_WithNewEmail_SetsPendingEmailAndToken() {
        User updateRequest = new User();
        updateRequest.setId(1L);
        updateRequest.setEmail("new@example.com");

        when(userRepo.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepo.save(any(User.class))).thenReturn(existingUser);

        User result = userService.updateUser(updateRequest);

        assertEquals("old@example.com", result.getEmail(), "Primary email should not change immediately");
        assertEquals("new@example.com", result.getPendingEmail(), "Pending email should be set");
        assertNotNull(result.getEmailUpdateToken(), "Update token should be generated");
        assertNotNull(result.getEmailUpdateTokenExpiry(), "Token expiry should be set");
        assertTrue(result.getEmailUpdateTokenExpiry().after(new Date()), "Token expiry should be in the future");
    }

    @Test
    void updateUser_WithExistingEmail_ThrowsValidationException() {
        User updateRequest = new User();
        updateRequest.setId(1L);
        updateRequest.setEmail("taken@example.com");

        when(userRepo.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.findByEmail("taken@example.com")).thenReturn(Optional.of(new User()));

        assertThrows(ValidationException.class, () -> userService.updateUser(updateRequest));
    }

    @Test
    void verifyEmailUpdate_WithValidToken_UpdatesEmail() {
        String token = "valid-token";
        existingUser.setPendingEmail("new@example.com");
        existingUser.setEmailUpdateToken(token);
        existingUser.setEmailUpdateTokenExpiry(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

        when(userRepo.findByEmailUpdateToken(token)).thenReturn(Optional.of(existingUser));
        when(userRepo.save(any(User.class))).thenReturn(existingUser);

        User result = userService.verifyEmailUpdate(token);

        assertEquals("new@example.com", result.getEmail());
        assertNull(result.getPendingEmail());
        assertNull(result.getEmailUpdateToken());
        assertNull(result.getEmailUpdateTokenExpiry());
        assertTrue(result.getIsEmailVerified());
        
        verify(userRepo).save(existingUser);
    }

    @Test
    void verifyEmailUpdate_WithExpiredToken_ThrowsValidationException() {
        String token = "expired-token";
        existingUser.setPendingEmail("new@example.com");
        existingUser.setEmailUpdateToken(token);
        existingUser.setEmailUpdateTokenExpiry(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));

        when(userRepo.findByEmailUpdateToken(token)).thenReturn(Optional.of(existingUser));

        ValidationException exception = assertThrows(ValidationException.class, () -> userService.verifyEmailUpdate(token));
        assertTrue(exception.getMessage().contains("expired"));
        verify(userRepo, never()).save(any());
    }
}
