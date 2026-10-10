package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.RegisterRequest;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.RoleRepo;
import com.JanSahayak.AI.service.EmailService;
import com.JanSahayak.AI.service.RateLimitingService;
import com.JanSahayak.AI.service.UserService;
import com.JanSahayak.AI.service.PincodeValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private RoleRepo roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PincodeValidationService pincodeValidationService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthController authController;

    @Test
    void testRegisterCitizen_Success_SessionTokenGenerated() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setPincode("110001");
        request.setIsAdult(true);

        when(rateLimitingService.isRegistrationBlocked(anyString())).thenReturn(false);
        when(userService.existsByEmail(anyString())).thenReturn(false);
        
        when(pincodeValidationService.isValidIndianPincode(anyString())).thenReturn(true);

        Role role = new Role();
        role.setName("ROLE_USER");
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));

        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        when(userService.existsByUsername(anyString())).thenReturn(false);

        when(userService.saveUser(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            return savedUser;
        });

        ResponseEntity<ApiResponse<String>> response = authController.registerUser(request);

        assertEquals(200, response.getStatusCode().value(), "Registration should return 200 OK");
        
        verify(userService, times(1)).saveUser(argThat(user -> 
            user.getSessionToken() != null && !user.getSessionToken().isEmpty()
        ));
    }
}
