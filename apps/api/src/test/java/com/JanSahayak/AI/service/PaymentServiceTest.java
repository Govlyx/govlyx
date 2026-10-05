package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.BillingCycle;
import com.JanSahayak.AI.enums.PassTier;
import com.JanSahayak.AI.enums.UserPassStatus;
import com.JanSahayak.AI.model.UserPass;
import com.JanSahayak.AI.repository.TransactionHistoryRepository;
import com.JanSahayak.AI.repository.UserPassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private UserPassRepository userPassRepository;

    @Mock
    private TransactionHistoryRepository transactionHistoryRepository;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // Not initializing razorpayClient as we only test activatePass via reflection to bypass webhook constraints
    }

    @Test
    void testActivatePass_NewMonthlyPass_GrantsOneMonth() throws Exception {
        UserPass pendingPass = new UserPass();
        pendingPass.setUserId(1L);
        pendingPass.setTier(PassTier.GOVLYX_PRO);
        pendingPass.setBillingCycle(BillingCycle.MONTHLY);
        pendingPass.setPrivateCommunityQuota(0);

        when(userPassRepository.findActivePassByUserId(1L)).thenReturn(Optional.empty());

        // Use reflection to call private method activatePass
        ReflectionTestUtils.invokeMethod(paymentService, "activatePass", pendingPass);

        assertEquals(UserPassStatus.ACTIVE, pendingPass.getStatus());
        assertTrue(pendingPass.getValidUntil().isAfter(LocalDateTime.now().plusDays(25)));
        assertTrue(pendingPass.getValidUntil().isBefore(LocalDateTime.now().plusDays(35)));
        assertEquals(3, pendingPass.getPrivateCommunityQuota());
        
        verify(userPassRepository, times(1)).save(pendingPass);
    }

    @Test
    void testActivatePass_NewYearlyPass_GrantsOneYearAnd12xQuota() throws Exception {
        UserPass pendingPass = new UserPass();
        pendingPass.setUserId(2L);
        pendingPass.setTier(PassTier.GOVLYX_VIP);
        pendingPass.setBillingCycle(BillingCycle.YEARLY);
        pendingPass.setPrivateCommunityQuota(0);

        when(userPassRepository.findActivePassByUserId(2L)).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(paymentService, "activatePass", pendingPass);

        assertEquals(UserPassStatus.ACTIVE, pendingPass.getStatus());
        assertTrue(pendingPass.getValidUntil().isAfter(LocalDateTime.now().plusMonths(11)));
        assertEquals(60, pendingPass.getPrivateCommunityQuota()); // VIP gives 5, yearly is 12x = 60
    }

    @Test
    void testActivatePass_Renewal_CarriesOverValidityAndQuota() throws Exception {
        // Existing pass expires in 15 days, has 2 quota left
        UserPass existingPass = new UserPass();
        existingPass.setValidUntil(LocalDateTime.now().plusDays(15));
        existingPass.setPrivateCommunityQuota(2);
        
        // New monthly PRO pass
        UserPass pendingPass = new UserPass();
        pendingPass.setUserId(3L);
        pendingPass.setTier(PassTier.GOVLYX_PRO);
        pendingPass.setBillingCycle(BillingCycle.MONTHLY);
        pendingPass.setPrivateCommunityQuota(0);

        when(userPassRepository.findActivePassByUserId(3L)).thenReturn(Optional.of(existingPass));

        ReflectionTestUtils.invokeMethod(paymentService, "activatePass", pendingPass);

        assertEquals(UserPassStatus.EXPIRED, existingPass.getStatus());
        verify(userPassRepository).save(existingPass);

        assertEquals(UserPassStatus.ACTIVE, pendingPass.getStatus());
        // Should have 15 days + 1 month = ~45 days
        assertTrue(pendingPass.getValidUntil().isAfter(LocalDateTime.now().plusDays(40)));
        // Should have 2 carried over + 3 new = 5
        assertEquals(5, pendingPass.getPrivateCommunityQuota());
    }
}
