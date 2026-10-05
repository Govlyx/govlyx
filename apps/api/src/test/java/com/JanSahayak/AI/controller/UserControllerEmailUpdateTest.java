package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.UserMeResponse;
import com.JanSahayak.AI.controller.UserController.UserUpdateRequest;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.service.EmailService;
import com.JanSahayak.AI.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerEmailUpdateTest {

    @Mock
    private UserService userService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserController userController;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("old@example.com");

        Authentication auth = new UsernamePasswordAuthenticationToken(currentUser, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void updateUserProfile_EmailChanged_SendsVerificationEmail() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("new@example.com");

        User updatedUser = new User();
        updatedUser.setId(1L);
        updatedUser.setEmail("old@example.com");
        updatedUser.setPendingEmail("new@example.com");
        updatedUser.setEmailUpdateToken(UUID.randomUUID().toString());

        when(userService.getUserFromAuthentication(any())).thenReturn(currentUser);
        when(userService.updateUser(any(User.class))).thenReturn(updatedUser);

        ResponseEntity<ApiResponse<UserMeResponse>> response = userController.updateUserProfile(request, null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().getMessage().contains("Verification link sent"));
        
        verify(emailService).sendEmailUpdateAlertEmail(updatedUser);
        verify(emailService).sendEmailUpdateVerificationEmail(eq(updatedUser), anyString(), eq("new@example.com"));
    }

    @Test
    void updateUserProfile_NoEmailChange_UpdatesProfileNormally() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setBio("New bio");

        User updatedUser = new User();
        updatedUser.setId(1L);
        updatedUser.setEmail("old@example.com");
        updatedUser.setBio("New bio");
        // No pending email

        when(userService.getUserFromAuthentication(any())).thenReturn(currentUser);
        when(userService.updateUser(any(User.class))).thenReturn(updatedUser);

        ResponseEntity<ApiResponse<UserMeResponse>> response = userController.updateUserProfile(request, null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("User profile updated successfully", response.getBody().getMessage());
        
        verify(emailService, never()).sendEmailUpdateAlertEmail(any());
        verify(emailService, never()).sendEmailUpdateVerificationEmail(any(), anyString(), anyString());
    }
}
