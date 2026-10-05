package com.Govlyx.AI.service;

import com.Govlyx.AI.model.ExternalCopyrightClaim;
import com.Govlyx.AI.repository.ExternalCopyrightClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CopyrightSLAMonitorTaskTest {

    @Mock
    private ExternalCopyrightClaimRepository claimRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private CopyrightSLAMonitorTask copyrightSLAMonitorTask;

    @BeforeEach
    void setUp() {
        // Initialization if needed
    }

    @Test
    void testMonitorCopyrightSLA_WithViolations() {
        // Arrange
        ExternalCopyrightClaim pendingClaim = new ExternalCopyrightClaim();
        pendingClaim.setStatus("PENDING");
        pendingClaim.setCreatedAt(new Date(System.currentTimeMillis() - 20 * 3600 * 1000L)); // 20 hours ago

        ExternalCopyrightClaim unresolvedClaim = new ExternalCopyrightClaim();
        unresolvedClaim.setStatus("ACKNOWLEDGED");
        unresolvedClaim.setCreatedAt(new Date(System.currentTimeMillis() - 14 * 24 * 3600 * 1000L)); // 14 days ago

        List<ExternalCopyrightClaim> pendingClaims = Arrays.asList(pendingClaim);
        List<ExternalCopyrightClaim> unresolvedClaims = Arrays.asList(unresolvedClaim);

        when(claimRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(Date.class)))
                .thenReturn(pendingClaims);
        
        when(claimRepository.findByStatusAndCreatedAtBefore(eq("ACKNOWLEDGED"), any(Date.class)))
                .thenReturn(unresolvedClaims);

        // Act
        copyrightSLAMonitorTask.monitorCopyrightSLA();

        // Assert
        verify(claimRepository, times(1)).findByStatusAndCreatedAtBefore(eq("PENDING"), any(Date.class));
        verify(claimRepository, times(1)).findByStatusAndCreatedAtBefore(eq("ACKNOWLEDGED"), any(Date.class));
    }

    @Test
    void testMonitorCopyrightSLA_NoViolations() {
        // Arrange
        when(claimRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(Date.class)))
                .thenReturn(Collections.emptyList());
        
        when(claimRepository.findByStatusAndCreatedAtBefore(eq("ACKNOWLEDGED"), any(Date.class)))
                .thenReturn(Collections.emptyList());

        // Act
        copyrightSLAMonitorTask.monitorCopyrightSLA();

        // Assert
        verify(claimRepository, times(1)).findByStatusAndCreatedAtBefore(eq("PENDING"), any(Date.class));
        verify(claimRepository, times(1)).findByStatusAndCreatedAtBefore(eq("ACKNOWLEDGED"), any(Date.class));
    }
}
