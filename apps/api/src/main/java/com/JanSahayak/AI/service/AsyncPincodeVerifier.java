package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class AsyncPincodeVerifier {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PincodeValidationService pincodeValidationService;

    // Run every 1 hour (3600000 ms)
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void verifyPendingPincodes() {
        log.info("Starting scheduled verification of pending pincodes.");

        // Fetch up to 100 users at a time to avoid memory overload
        List<User> usersToVerify = userRepo.findUsersWithUnverifiedPincode(PageRequest.of(0, 100));
        
        if (usersToVerify.isEmpty()) {
            log.info("No unverified pincodes found.");
            return;
        }

        for (User user : usersToVerify) {
            try {
                boolean isValid = pincodeValidationService.isValidIndianPincode(user.getPincode());
                // If the API call completes (no exception), we have a definitive answer.
                user.setHasInvalidPincode(!isValid);
                userRepo.save(user);
                log.info("Verified pincode for user {}: isValid={}", user.getId(), isValid);
            } catch (PincodeValidationService.ApiUnavailableException e) {
                log.warn("API still down while verifying user {}. Skipping...", user.getId());
                // Stop the batch if API is down so we don't spam it.
                break;
            } catch (Exception e) {
                log.error("Unexpected error verifying user {}: {}", user.getId(), e.getMessage());
            }
        }
        
        log.info("Completed scheduled verification of pending pincodes.");
    }
}
