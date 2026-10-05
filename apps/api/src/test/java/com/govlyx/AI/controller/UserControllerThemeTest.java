package com.govlyx.AI.controller;

import com.govlyx.AI.exception.ApiResponse;
import com.govlyx.AI.exception.ServiceException;
import com.govlyx.AI.model.User;
import com.govlyx.AI.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserControllerThemeTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private User testUser;
    private Validator validator;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");

        // Mock Security Context
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(testUser.getEmail(), null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testUpdateTheme_Success() {
        // Arrange
        when(userService.getUserFromAuthentication(any())).thenReturn(testUser);
        doNothing().when(userService).updateTheme(1L, "dark");

        UserController.ThemeUpdateRequest request = new UserController.ThemeUpdateRequest();
        request.setTheme("dark");

        // Act
        ResponseEntity<ApiResponse<Void>> response = userController.updateTheme(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Theme updated successfully", response.getBody().getMessage());
        verify(userService, times(1)).updateTheme(1L, "dark");
    }

    @Test
    void testUpdateTheme_Light() {
        when(userService.getUserFromAuthentication(any())).thenReturn(testUser);
        doNothing().when(userService).updateTheme(1L, "light");

        UserController.ThemeUpdateRequest request = new UserController.ThemeUpdateRequest();
        request.setTheme("light");

        ResponseEntity<ApiResponse<Void>> response = userController.updateTheme(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userService, times(1)).updateTheme(1L, "light");
    }

    @Test
    void testUpdateTheme_CaseInsensitiveNormalization() {
        // Arrange: Pass uppercase DARK
        when(userService.getUserFromAuthentication(any())).thenReturn(testUser);
        doNothing().when(userService).updateTheme(1L, "dark");

        UserController.ThemeUpdateRequest request = new UserController.ThemeUpdateRequest();
        request.setTheme("DARK");

        // Act
        ResponseEntity<ApiResponse<Void>> response = userController.updateTheme(request);

        // Assert: normalized to lowercase "dark"
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userService, times(1)).updateTheme(1L, "dark");
    }

    @Test
    void testUpdateTheme_ServiceException() {
        // Arrange
        when(userService.getUserFromAuthentication(any())).thenReturn(testUser);
        doThrow(new ServiceException("Database error")).when(userService).updateTheme(1L, "dark");

        UserController.ThemeUpdateRequest request = new UserController.ThemeUpdateRequest();
        request.setTheme("dark");

        // Act
        ResponseEntity<ApiResponse<Void>> response = userController.updateTheme(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Service error", response.getBody().getMessage());
        verify(userService, times(1)).updateTheme(1L, "dark");
    }

    @Test
    void testUpdateTheme_UnexpectedException() {
        // Arrange
        when(userService.getUserFromAuthentication(any())).thenThrow(new RuntimeException("Unexpected failure"));

        UserController.ThemeUpdateRequest request = new UserController.ThemeUpdateRequest();
        request.setTheme("dark");

        // Act
        ResponseEntity<ApiResponse<Void>> response = userController.updateTheme(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("An unexpected error occurred"));
    }

    @Test
    void testThemeUpdateRequest_Validation_ValidValues() {
        String[] validThemes = {"light", "dark", "LIGHT", "DARK", "Light", "Dark"};
        for (String theme : validThemes) {
            UserController.ThemeUpdateRequest request = new UserController.ThemeUpdateRequest();
            request.setTheme(theme);
            Set<ConstraintViolation<UserController.ThemeUpdateRequest>> violations = validator.validate(request);
            assertTrue(violations.isEmpty(), "Expected no violations for valid theme: " + theme);
        }
    }

    @Test
    void testThemeUpdateRequest_Validation_InvalidValues() {
        String[] invalidThemes = {"blue", "system", "auto", "neon", "123", "", "   ", null};
        for (String theme : invalidThemes) {
            UserController.ThemeUpdateRequest request = new UserController.ThemeUpdateRequest();
            request.setTheme(theme);
            Set<ConstraintViolation<UserController.ThemeUpdateRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty(), "Expected validation violations for invalid theme: " + theme);
        }
    }
}
