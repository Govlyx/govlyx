package com.Govlyx.AI.controller;

import com.Govlyx.AI.enums.BillingCycle;
import com.Govlyx.AI.enums.PassTier;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.model.UserPass;
import com.Govlyx.AI.repository.UserPassRepository;
import com.Govlyx.AI.repository.UserRepo;
import com.Govlyx.AI.service.PaymentService;
import com.Govlyx.AI.service.PlanEnforcementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaymentControllerTest {

    /* --- MONETIZATION DISABLED ---
    @Mock
    private PaymentService paymentService;
    @Mock
    private PlanEnforcementService planEnforcementService;
    @Mock
    private UserPassRepository userPassRepository;
    @Mock
    private UserRepo userRepository;
    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private PaymentController paymentController;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = mock(User.class);
        when(testUser.getId()).thenReturn(1L);
    }

    @Test
    void testGetMyBilling_ReturnsBillingCycle() {
        // Arrange
        when(planEnforcementService.getUserTier(1L)).thenReturn(PassTier.GOVLYX_VIP);

        UserPass activePass = new UserPass();
        activePass.setBillingCycle(BillingCycle.YEARLY);
        activePass.setValidUntil(LocalDateTime.now().plusMonths(12));
        activePass.setPrivateCommunityQuota(5);

        when(userPassRepository.findActivePassByUserId(1L)).thenReturn(Optional.of(activePass));

        // Act
        ResponseEntity<?> responseEntity = paymentController.getMyBilling(testUser);

        // Assert
        assertEquals(200, responseEntity.getStatusCodeValue());
        Map<String, Object> responseBody = (Map<String, Object>) responseEntity.getBody();
        assertEquals("GOVLYX_VIP", responseBody.get("currentTier"));
        assertEquals("YEARLY", responseBody.get("billingCycle"), "Billing cycle should be included in the response to fix the UI active plan bug");
    }

    @Test
    void testGetMyBilling_ReturnsDefaultMonthlyWhenNull() {
        // Arrange
        when(planEnforcementService.getUserTier(1L)).thenReturn(PassTier.GOVLYX_PRO);

        UserPass activePass = new UserPass();
        // Billing cycle is null for old legacy passes
        activePass.setBillingCycle(null);
        activePass.setValidUntil(LocalDateTime.now().plusMonths(1));
        activePass.setPrivateCommunityQuota(3);

        when(userPassRepository.findActivePassByUserId(1L)).thenReturn(Optional.of(activePass));

        // Act
        ResponseEntity<?> responseEntity = paymentController.getMyBilling(testUser);

        // Assert
        assertEquals(200, responseEntity.getStatusCodeValue());
        Map<String, Object> responseBody = (Map<String, Object>) responseEntity.getBody();
        assertEquals("GOVLYX_PRO", responseBody.get("currentTier"));
        assertEquals("MONTHLY", responseBody.get("billingCycle"), "Should fallback to MONTHLY when billing cycle is null");
    }
    --- END MONETIZATION DISABLED --- */
}
