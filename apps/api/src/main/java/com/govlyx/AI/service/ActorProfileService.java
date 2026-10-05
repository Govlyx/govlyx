package com.Govlyx.AI.service;

import com.Govlyx.AI.model.ActorProfile;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.ActorProfileRepo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActorProfileService {

    private final ActorProfileRepo actorProfileRepo;
    private final EntityManager entityManager;
    private final CivicPseudonymService civicPseudonymService;
    private final com.Govlyx.AI.repository.UserRepo userRepo;

    public Optional<ActorProfile> findByActorToken(String actorToken) {
        if (actorToken == null || actorToken.isBlank()) return Optional.empty();
        return actorProfileRepo.findByActorToken(actorToken);
    }

    public Optional<ActorProfile> findByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return actorProfileRepo.findByUsername(username);
    }

    @Transactional
    public ActorProfile getOrCreateProfile(String actorToken, String username, String displayName, String profileImage, String pincode) {
        return getOrCreateProfile(actorToken, username, profileImage, pincode);
    }

    public ActorProfile getOrCreateProfile(String actorToken, String username, String profileImage, String pincode) {
        return actorProfileRepo.findByActorToken(actorToken).orElseGet(() -> {
            String handle = (username != null && !username.isBlank())
                    ? resolveSanitizedUsername(username, actorToken)
                    : civicPseudonymService.generateUniquePseudonym();

            ActorProfile profile = ActorProfile.builder()
                    .actorToken(actorToken)
                    .username(handle)
                    .profileImage(profileImage)
                    .pincode(pincode)
                    .createdAt(new Date())
                    .build();
            return actorProfileRepo.save(profile);
        });
    }

    /**
     * Model 1: True Zero-Knowledge Civic Persona Engine.
     * Whenever a user provides or rotates an author token:
     * 1. If this exact actorToken already exists, return it (preserving existing username).
     * 2. If this actorToken is NEW, mint a brand-new unique pseudonym specifically for this token!
     *    Under Model 1, a clean break is enforced — no linkage to users.username and no migration of old posts.
     */
    @Transactional
    public ActorProfile createOrCopyFromUser(String actorToken, User user) {
        return createOrCopyFromUser(actorToken, user, null);
    }

    @Transactional
    public ActorProfile createOrCopyFromUser(String actorToken, User user, String requestedUsername) {
        if (actorToken == null || actorToken.isBlank()) {
            throw new IllegalArgumentException("Actor token cannot be null or blank");
        }
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        // 1. If profile already exists for this exact actorToken, update preferences and username if requested
        Optional<ActorProfile> profileByToken = actorProfileRepo.findByActorToken(actorToken);
        if (profileByToken.isPresent()) {
            ActorProfile profile = profileByToken.get();
            if (requestedUsername != null && !requestedUsername.isBlank()) {
                String cleanHandle = resolveSanitizedUsername(requestedUsername.trim(), actorToken);
                profile.setUsername(cleanHandle);
            }
            copyUserPreferencesToProfile(user, profile);
            return actorProfileRepo.save(profile);
        }

        // 2. Token is NEW. Under Model 1, mint a fresh, independent civic pseudonym or use requested username!
        String freshPseudonym = (requestedUsername != null && !requestedUsername.isBlank())
                ? resolveSanitizedUsername(requestedUsername.trim(), actorToken)
                : civicPseudonymService.generateUniquePseudonym();

        // 3. Create fresh ActorProfile for this new actorToken
        ActorProfile newProfile = ActorProfile.builder()
                .actorToken(actorToken)
                .username(freshPseudonym)
                .createdAt(new Date())
                .build();
        copyUserPreferencesToProfile(user, newProfile);
        return actorProfileRepo.save(newProfile);
    }

    private String resolveSanitizedUsername(String rawUsername, String currentActorToken) {
        String clean = rawUsername.trim().replace("@", "").replaceAll("[^a-zA-Z0-9_]", "");
        if (clean.length() < 3) {
            return civicPseudonymService.generateUniquePseudonym();
        }
        if (clean.length() > 30) {
            clean = clean.substring(0, 30);
        }

        // Check if this username is already taken by another actor profile or user record
        boolean takenInProfiles = actorProfileRepo.findByUsername(clean)
                .map(p -> !p.getActorToken().equals(currentActorToken))
                .orElse(false);

        boolean takenInUsers = (userRepo != null && userRepo.findByUsername(clean).isPresent());

        if (takenInProfiles || takenInUsers) {
            // Collision: retry with unique random suffixes, fallback to civicPseudonymService
            for (int i = 0; i < 10; i++) {
                int randSuffix = 1000 + (int) (Math.random() * 9000);
                String prefix = (clean.length() > 25) ? clean.substring(0, 25) : clean;
                String candidate = prefix + randSuffix;
                if (!actorProfileRepo.findByUsername(candidate).isPresent() &&
                    (userRepo == null || !userRepo.findByUsername(candidate).isPresent())) {
                    return candidate;
                }
            }
            return civicPseudonymService.generateUniquePseudonym();
        }

        return clean;
    }

    /**
     * Copies non-identifying preference, location, and safety fields from User to ActorProfile.
     * Preserves the minted pseudonym on the ActorProfile to avoid forensic correlation.
     */
    public void copyUserPreferencesToProfile(User user, ActorProfile profile) {
        if (user == null || profile == null) return;

        if (profile.getUsername() == null || profile.getUsername().isBlank()) {
            profile.setUsername(civicPseudonymService.generateUniquePseudonym());
        }
        if (user.getProfileImage() != null) {
            profile.setProfileImage(user.getProfileImage());
        }
        if (user.getBio() != null) {
            profile.setBio(user.getBio());
        }
        if (user.getPincode() != null) {
            profile.setPincode(user.getPincode());
        }
        if (user.getHomeLatitude() != null) {
            profile.setHomeLatitude(user.getHomeLatitude());
        }
        if (user.getHomeLongitude() != null) {
            profile.setHomeLongitude(user.getHomeLongitude());
        }
        if (user.getMutedWords() != null) {
            profile.setMutedWords(user.getMutedWords());
        }
        if (user.getBlockedActors() != null) {
            profile.setBlockedActors(user.getBlockedActors());
        }
        profile.setProfanityFilterLevel(user.getProfanityFilterLevel() != null ? user.getProfanityFilterLevel() : "STRICT");
        profile.setCopyrightStrikes(user.getCopyrightStrikes() != null ? user.getCopyrightStrikes() : 0);
        profile.setIsAdult(user.getIsAdult() != null ? user.getIsAdult() : true);
        profile.setTheme(user.getTheme() != null ? user.getTheme() : "light");
        profile.setInterfaceLanguage(user.getInterfaceLanguage() != null ? user.getInterfaceLanguage() : "en");
        profile.setPreferredLanguage(user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "en");
        profile.setAutoTranslate(user.getAutoTranslate() != null ? user.getAutoTranslate() : false);
        profile.setUpdatedAt(new Date());
    }

    public void copyUserFieldsToProfile(User user, ActorProfile profile) {
        copyUserPreferencesToProfile(user, profile);
    }

    /**
     * Seamlessly cascades actor token rotation across all civic interaction tables.
     */
    @Transactional
    public void migrateActorTokenReferences(String oldToken, String newToken) {
        if (oldToken == null || newToken == null || oldToken.equals(newToken) || entityManager == null) {
            return;
        }
        try {
            entityManager.createQuery("UPDATE Post p SET p.actorToken = :newToken WHERE p.actorToken = :oldToken")
                    .setParameter("newToken", newToken).setParameter("oldToken", oldToken).executeUpdate();

            entityManager.createQuery("UPDATE SocialPost sp SET sp.actorToken = :newToken WHERE sp.actorToken = :oldToken")
                    .setParameter("newToken", newToken).setParameter("oldToken", oldToken).executeUpdate();

            entityManager.createQuery("UPDATE Comment c SET c.actorToken = :newToken WHERE c.actorToken = :oldToken")
                    .setParameter("newToken", newToken).setParameter("oldToken", oldToken).executeUpdate();

            entityManager.createQuery("UPDATE PostLike pl SET pl.actorToken = :newToken WHERE pl.actorToken = :oldToken")
                    .setParameter("newToken", newToken).setParameter("oldToken", oldToken).executeUpdate();

            entityManager.createQuery("UPDATE SavedPost sp SET sp.actorToken = :newToken WHERE sp.actorToken = :oldToken")
                    .setParameter("newToken", newToken).setParameter("oldToken", oldToken).executeUpdate();

            entityManager.createQuery("UPDATE PollVote pv SET pv.actorToken = :newToken WHERE pv.actorToken = :oldToken")
                    .setParameter("newToken", newToken).setParameter("oldToken", oldToken).executeUpdate();

            entityManager.createQuery("UPDATE Poll pol SET pol.createdByActorToken = :newToken WHERE pol.createdByActorToken = :oldToken")
                    .setParameter("newToken", newToken).setParameter("oldToken", oldToken).executeUpdate();

            entityManager.createQuery("UPDATE UserTag ut SET ut.taggedByActorToken = :newToken WHERE ut.taggedByActorToken = :oldToken")
                    .setParameter("newToken", newToken).setParameter("oldToken", oldToken).executeUpdate();

            entityManager.createQuery("UPDATE Notification n SET n.recipientActorToken = :newToken WHERE n.recipientActorToken = :oldToken")
                    .setParameter("newToken", newToken).setParameter("oldToken", oldToken).executeUpdate();

            entityManager.createQuery("UPDATE Notification n SET n.triggeredByActorToken = :newToken WHERE n.triggeredByActorToken = :oldToken")
                    .setParameter("newToken", newToken).setParameter("oldToken", oldToken).executeUpdate();

            log.info("[TOKEN_MIGRATION] Successfully cascaded references from old actor token {} to new actor token {}", oldToken, newToken);
        } catch (Exception e) {
            log.warn("[TOKEN_MIGRATION] Non-critical warning migrating token references: {}", e.getMessage());
        }
    }

    @Transactional
    public void updateProfile(String actorToken, String newDisplayName, String newProfileImage, String newBio) {
        actorProfileRepo.findByActorToken(actorToken).ifPresent(profile -> {
            if (newProfileImage != null) profile.setProfileImage(newProfileImage);
            if (newBio != null) profile.setBio(newBio);
            profile.setUpdatedAt(new Date());
            actorProfileRepo.save(profile);
        });
    }
}
