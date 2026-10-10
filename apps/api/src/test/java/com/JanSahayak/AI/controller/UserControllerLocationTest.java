package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.UserMeResponse;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.PincodeLookup;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.service.PinCodeLookupService;
import com.JanSahayak.AI.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserControllerLocationTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepo userRepository;

    @Mock
    private PinCodeLookupService pinCodeLookupService;

    @InjectMocks
    private UserController userController;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setPincode("110001");

        // Mock Security Context
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(testUser.getEmail(), null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void testUpdateHomeLocation_WithFrontendPincode() {
        // Arrange
        when(userService.getUserFromAuthentication(any())).thenReturn(testUser);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserController.LocationUpdateRequest request = new UserController.LocationUpdateRequest();
        request.setLatitude(new BigDecimal("18.5204"));
        request.setLongitude(new BigDecimal("73.8567"));
        request.setPincode("411001");

        // Act
        ResponseEntity<ApiResponse<UserMeResponse>> response = userController.updateHomeLocation(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Home location saved", response.getBody().getMessage());
        
        // Pincode should be updated directly from request
        assertEquals("411001", testUser.getPincode());
        assertEquals(new BigDecimal("18.5204"), testUser.getHomeLatitude());
        assertEquals(new BigDecimal("73.8567"), testUser.getHomeLongitude());
        
        // Backend Geocoding should NOT be called
        verify(pinCodeLookupService, never()).findClosestPincode(anyDouble(), anyDouble());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void testUpdateHomeLocation_WithoutFrontendPincode_UsesBackendGeocoding() {
        // Arrange
        when(userService.getUserFromAuthentication(any())).thenReturn(testUser);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        PincodeLookup mockClosestPincode = new PincodeLookup();
        mockClosestPincode.setPincode("400001");
        
        when(pinCodeLookupService.findClosestPincode(19.0760, 72.8777)).thenReturn(Optional.of(mockClosestPincode));

        UserController.LocationUpdateRequest request = new UserController.LocationUpdateRequest();
        request.setLatitude(new BigDecimal("19.0760"));
        request.setLongitude(new BigDecimal("72.8777"));
        // pincode not set in request

        // Act
        ResponseEntity<ApiResponse<UserMeResponse>> response = userController.updateHomeLocation(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // Pincode should be updated from backend geocoding service
        assertEquals("400001", testUser.getPincode());
        assertEquals(new BigDecimal("19.0760"), testUser.getHomeLatitude());
        assertEquals(new BigDecimal("72.8777"), testUser.getHomeLongitude());
        
        // Backend Geocoding SHOULD be called
        verify(pinCodeLookupService, times(1)).findClosestPincode(19.0760, 72.8777);
        verify(userRepository, times(1)).save(testUser);
    }
}
