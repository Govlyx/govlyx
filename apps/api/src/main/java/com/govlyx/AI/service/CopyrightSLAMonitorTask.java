package com.Govlyx.AI.service;

import com.Govlyx.AI.model.ExternalCopyrightClaim;
import com.Govlyx.AI.repository.ExternalCopyrightClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CopyrightSLAMonitorTask {

    private final ExternalCopyrightClaimRepository claimRepository;
    private final EmailService emailService;

    // Runs every 6 hours
    @Scheduled(cron = "0 0 */6 * * *")
    public void monitorCopyrightSLA() {
        log.info("Running Copyright SLA Monitor Task...");

        Calendar cal = Calendar.getInstance();

        // 1. Check for PENDING claims nearing 24-hour acknowledgment SLA
        cal.add(Calendar.HOUR_OF_DAY, -18);
        Date eighteenHoursAgo = cal.getTime();
        
        // Find claims that are PENDING and older than 18 hours
        List<ExternalCopyrightClaim> pendingClaims = claimRepository.findByStatusAndCreatedAtBefore("PENDING", eighteenHoursAgo);
            
        if (!pendingClaims.isEmpty()) {
            log.warn("SLA ALERT: {} copyright claims are nearing the 24-hour acknowledgment deadline.", pendingClaims.size());
            // TODO: In production, send a batched email alert to the Grievance Officer
        }

        // 2. Check for ACKNOWLEDGED claims nearing 15-day resolution SLA
        cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -13);
        Date thirteenDaysAgo = cal.getTime();
        
        List<ExternalCopyrightClaim> unresolvedClaims = claimRepository.findByStatusAndCreatedAtBefore("ACKNOWLEDGED", thirteenDaysAgo);

        if (!unresolvedClaims.isEmpty()) {
            log.warn("SLA ALERT: {} copyright claims are nearing the 15-day resolution deadline.", unresolvedClaims.size());
            // TODO: Send alert to Grievance Officer
        }
        
        log.info("Copyright SLA Monitor Task completed.");
    }
}
