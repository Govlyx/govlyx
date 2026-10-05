package com.JanSahayak.AI.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Core cryptographic engine for the Zero-Knowledge Blind Shield.
 * Single source of truth for HMAC-SHA256 token derivations and blind index hashes.
 */
@Service
public class IdentityBlindService {

    private final String blindPepper;
    private final String emailPepper;
    private final SecureRandom secureRandom = new SecureRandom();

    public IdentityBlindService(
            @Value("${govlyx.security.blind-pepper:1023a41ebf7bead2c8d899dda6df33f56f3258e13a3a6b1ae4ecbac3b6b04e4e}") String blindPepper,
            @Value("${govlyx.security.email-pepper:7f2314478209f306d6f414393775d3182559e3bf3299c8ed2fca2ee13c46a7ff}") String emailPepper) {
        this.blindPepper = blindPepper;
        this.emailPepper = emailPepper;
    }

    /**
     * Polymorphic server actor token derivation for either Google or local user.
     */
    public String deriveServerActorToken(com.JanSahayak.AI.model.User user) {
        if (user == null) return null;
        if (user.getGoogleId() != null && !user.getGoogleId().isBlank()) {
            return deriveServerActorTokenFromGoogleId(user.getGoogleId(), user.getActorSalt());
        } else if (user.getId() != null && user.getActorSalt() != null) {
            return deriveServerActorTokenFromUserId(user.getId(), user.getActorSalt());
        }
        return null;
    }

    /**
     * Server-side intermediate token for Google OAuth users.
     * Input: google_id + per-user actor_salt.
     */
    public String deriveServerActorTokenFromGoogleId(String googleId, String actorSalt) {
        if (googleId == null || actorSalt == null) {
            throw new IllegalArgumentException("googleId and actorSalt must not be null");
        }
        return hmacSha256Hex(googleId + ":" + actorSalt, blindPepper);
    }

    /**
     * Server-side intermediate token for local email/password users.
     * Input: user.id + per-user actor_salt.
     */
    public String deriveServerActorTokenFromUserId(Long userId, String actorSalt) {
        if (userId == null || actorSalt == null) {
            throw new IllegalArgumentException("userId and actorSalt must not be null");
        }
        return hmacSha256Hex(userId + ":" + actorSalt, blindPepper);
    }

    /**
     * Blind index hash for email — used for login queries after encryption at rest.
     */
    public String deriveEmailHash(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalizedEmail = email.toLowerCase(Locale.ROOT).trim();
        return hmacSha256Hex(normalizedEmail, emailPepper);
    }

    /**
     * Derives final client-side actor token: act_ + HMAC(serverActorToken, seedBlindSalt).
     */
    public String deriveClientActorToken(String serverActorToken, String seedBlindSalt) {
        if (serverActorToken == null || seedBlindSalt == null) return null;
        return "act_" + hmacSha256Hex(serverActorToken, seedBlindSalt);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.JanSahayak.AI.repository.ActorProfileRepo actorProfileRepo;

    /**
     * Resolves actor token for a user if server actor token and seedBlindSalt are available,
     * or via their civic persona ActorProfile mapping if seedBlindSalt was migrated to client vault.
     */
    public String resolveActorTokenForUser(com.JanSahayak.AI.model.User user) {
        if (user == null) return null;
        if (user.getActorSalt() != null) {
            String serverToken = deriveServerActorToken(user);
            if (serverToken != null && user.getSeedBlindSalt() != null) {
                return deriveClientActorToken(serverToken, user.getSeedBlindSalt());
            }
        }
        if (actorProfileRepo != null && user.getActualUsername() != null && !user.getActualUsername().isBlank()) {
            return actorProfileRepo.findByUsername(user.getActualUsername())
                    .map(com.JanSahayak.AI.model.ActorProfile::getActorToken)
                    .orElse(null);
        }
        return null;
    }

    /**
     * Generates a cryptographically secure 32-byte (64 hex characters) salt.
     */
    public String generateActorSalt() {
        byte[] salt = new byte[32];
        secureRandom.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Computes HMAC-SHA256 of data using the provided key, returning lowercase hex.
     */
    public String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
