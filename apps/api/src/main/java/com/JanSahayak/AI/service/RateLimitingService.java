package com.JanSahayak.AI.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@Slf4j
public class RateLimitingService {

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int MAX_REGISTRATION_ATTEMPTS = 3;
    private static final long BLOCK_DURATION_MS = 15 * 60 * 1000; // 15 minutes

    private static class RateLimitData {
        int attempts;
        long expirationTime;

        RateLimitData(long duration) {
            this.attempts = 1;
            this.expirationTime = Instant.now().toEpochMilli() + duration;
        }
    }

    private final Map<String, RateLimitData> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, RateLimitData> registrationAttempts = new ConcurrentHashMap<>();
    private final Map<String, RateLimitData> passwordChangeAttempts = new ConcurrentHashMap<>();

    // ===== Password Change Rate Limiting =====
    private static final int MAX_PASSWORD_CHANGE_ATTEMPTS = 5;

    public boolean isPasswordChangeBlocked(String email) {
        if (email == null) return false;
        String key = email.toLowerCase();
        RateLimitData data = passwordChangeAttempts.get(key);
        if (data == null) return false;

        if (Instant.now().toEpochMilli() > data.expirationTime) {
            passwordChangeAttempts.remove(key);
            return false;
        }

        return data.attempts >= MAX_PASSWORD_CHANGE_ATTEMPTS;
    }

    public void recordFailedPasswordChange(String email) {
        if (email == null) return;
        String key = email.toLowerCase();
        passwordChangeAttempts.compute(key, (k, data) -> {
            if (data == null || Instant.now().toEpochMilli() > data.expirationTime) {
                return new RateLimitData(BLOCK_DURATION_MS);
            }
            data.attempts++;
            if (data.attempts >= MAX_PASSWORD_CHANGE_ATTEMPTS) {
                data.expirationTime = Instant.now().toEpochMilli() + BLOCK_DURATION_MS;
            }
            return data;
        });
    }

    public void clearPasswordChangeAttempts(String email) {
        if (email == null) return;
        passwordChangeAttempts.remove(email.toLowerCase());
    }

    // ===== Login Rate Limiting =====

    public boolean isLoginBlocked(String email) {
        if (email == null) return false;
        String key = email.toLowerCase();
        RateLimitData data = loginAttempts.get(key);
        if (data == null) return false;

        if (Instant.now().toEpochMilli() > data.expirationTime) {
            loginAttempts.remove(key);
            return false;
        }

        return data.attempts >= MAX_LOGIN_ATTEMPTS;
    }

    public void recordFailedLogin(String email) {
        if (email == null) return;
        String key = email.toLowerCase();
        loginAttempts.compute(key, (k, data) -> {
            if (data == null || Instant.now().toEpochMilli() > data.expirationTime) {
                return new RateLimitData(BLOCK_DURATION_MS);
            }
            data.attempts++;
            // Optional: Extend block time on repeated failures
            if (data.attempts >= MAX_LOGIN_ATTEMPTS) {
                data.expirationTime = Instant.now().toEpochMilli() + BLOCK_DURATION_MS;
            }
            return data;
        });
    }

    public void clearLoginAttempts(String email) {
        if (email == null) return;
        loginAttempts.remove(email.toLowerCase());
    }

    // ===== Registration / Resend Rate Limiting =====

    public boolean isRegistrationBlocked(String email) {
        if (email == null) return false;
        String key = email.toLowerCase();
        RateLimitData data = registrationAttempts.get(key);
        if (data == null) return false;

        if (Instant.now().toEpochMilli() > data.expirationTime) {
            registrationAttempts.remove(key);
            return false;
        }

        return data.attempts >= MAX_REGISTRATION_ATTEMPTS;
    }

    public void recordRegistrationAttempt(String email) {
        if (email == null) return;
        String key = email.toLowerCase();
        registrationAttempts.compute(key, (k, data) -> {
            if (data == null || Instant.now().toEpochMilli() > data.expirationTime) {
                return new RateLimitData(BLOCK_DURATION_MS);
            }
            data.attempts++;
            if (data.attempts >= MAX_REGISTRATION_ATTEMPTS) {
                data.expirationTime = Instant.now().toEpochMilli() + BLOCK_DURATION_MS;
            }
            return data;
        });
    }

    // ===== Cleanup Scheduled Task =====
    
    @Scheduled(fixedRate = 3600000) // Run every 1 hour
    public void cleanupExpiredEntries() {
        long now = Instant.now().toEpochMilli();
        int loginCleaned = 0;
        int regCleaned = 0;
        int pwCleaned = 0;

        for (Map.Entry<String, RateLimitData> entry : loginAttempts.entrySet()) {
            if (now > entry.getValue().expirationTime) {
                loginAttempts.remove(entry.getKey());
                loginCleaned++;
            }
        }

        for (Map.Entry<String, RateLimitData> entry : registrationAttempts.entrySet()) {
            if (now > entry.getValue().expirationTime) {
                registrationAttempts.remove(entry.getKey());
                regCleaned++;
            }
        }
        
        for (Map.Entry<String, RateLimitData> entry : passwordChangeAttempts.entrySet()) {
            if (now > entry.getValue().expirationTime) {
                passwordChangeAttempts.remove(entry.getKey());
                pwCleaned++;
            }
        }
        
        if (loginCleaned > 0 || regCleaned > 0 || pwCleaned > 0) {
            log.info("RateLimitingService cleanup: Removed {} login entries, {} registration entries, {} password change entries", loginCleaned, regCleaned, pwCleaned);
        }
    }
}
