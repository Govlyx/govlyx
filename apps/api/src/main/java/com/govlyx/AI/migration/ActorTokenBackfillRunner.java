package com.Govlyx.AI.migration;

import com.Govlyx.AI.model.ActorProfile;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.ActorProfileRepo;
import com.Govlyx.AI.repository.UserRepo;
import com.Govlyx.AI.security.AesGcmEmailConverter;
import com.Govlyx.AI.security.IdentityBlindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class ActorTokenBackfillRunner {

    private final UserRepo userRepo;
    private final ActorProfileRepo actorProfileRepo;
    private final IdentityBlindService identityBlindService;
    private final AesGcmEmailConverter emailConverter;
    private final JdbcTemplate jdbcTemplate;
    private final com.Govlyx.AI.service.CivicPseudonymService civicPseudonymService;

    private static final int BATCH_SIZE = 250;
    private static final String TASK_NAME = "actor_token_backfill";

    @EventListener(ApplicationReadyEvent.class)
    public void runMigration() {
        try {
            Integer progressTableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'migration_progress'",
                Integer.class
            );
            if (progressTableExists == null || progressTableExists == 0) {
                log.info("[MIGRATION] Database has already undergone Stage 5 Contraction. Backfill skipped.");
                return;
            }

            ensureMigrationTable();

            String status = getMigrationStatus();
            if ("COMPLETED".equalsIgnoreCase(status)) {
                log.info("[MIGRATION] ActorToken backfill already completed. Skipping.");
                return;
            }

            log.info("[MIGRATION] Starting Zero-Data-Loss ActorToken backfill...");
            long lastId = getLastProcessedId();

            while (true) {
                List<User> batch = userRepo.findByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, BATCH_SIZE));
                if (batch.isEmpty()) {
                    log.info("[MIGRATION] All users processed successfully!");
                    markMigrationCompleted();
                    break;
                }

                for (User user : batch) {
                    processSingleUser(user);
                    lastId = user.getId();
                }

                updateCheckpoint(lastId, batch.size());
                log.info("[MIGRATION] Checkpoint: Processed up to User ID {}", lastId);
            }
        } catch (Exception e) {
            log.error("[MIGRATION] Error during backfill migration: {}", e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleUser(User user) {
        try {
            // 1. Ensure actor_salt exists
            String actorSalt = user.getActorSalt();
            if (actorSalt == null || actorSalt.isBlank()) {
                actorSalt = identityBlindService.generateActorSalt();
                user.setActorSalt(actorSalt);
            }

            // 2. Compute email_hash and email_encrypted
            String rawEmail = user.getEmail();
            if (rawEmail != null && !rawEmail.isBlank()) {
                user.setEmailHash(identityBlindService.deriveEmailHash(rawEmail));
                user.setEmailEncrypted(emailConverter.convertToDatabaseColumn(rawEmail));
            }

            // 3. Derive deterministic server_actor_token & seed_blind_salt
            String seedBlindSalt = user.getSeedBlindSalt();
            if (seedBlindSalt == null || seedBlindSalt.isBlank()) {
                seedBlindSalt = identityBlindService.generateActorSalt();
                user.setSeedBlindSalt(seedBlindSalt);
            }

            String serverActorToken;
            String actorToken;
            while (true) {
                if (user.getGoogleId() != null && !user.getGoogleId().isBlank()) {
                    serverActorToken = identityBlindService.deriveServerActorTokenFromGoogleId(user.getGoogleId(), actorSalt);
                } else {
                    serverActorToken = identityBlindService.deriveServerActorTokenFromUserId(user.getId(), actorSalt);
                }

                actorToken = identityBlindService.deriveClientActorToken(serverActorToken, seedBlindSalt);

                // UNIQUENESS COLLISION GUARD:
                Optional<ActorProfile> existing = actorProfileRepo.findByActorToken(actorToken);
                if (existing.isPresent() && !existing.get().getUsername().equals(user.getActualUsername())) {
                    log.warn("[COLLISION_GUARD] Collision detected for user {}. Regenerating salts...", user.getId());
                    actorSalt = identityBlindService.generateActorSalt();
                    seedBlindSalt = identityBlindService.generateActorSalt();
                    user.setActorSalt(actorSalt);
                    user.setSeedBlindSalt(seedBlindSalt);
                    continue;
                }
                break;
            }

            // 4. Create or update ActorProfile (The Civic Persona Layer)
            String authorUsername;
            if (user.getRole() != null && !"ROLE_USER".equals(user.getRole().getName())) {
                authorUsername = user.getActualUsername();
            } else if (civicPseudonymService != null) {
                authorUsername = civicPseudonymService.generateUniquePseudonym();
            } else {
                authorUsername = "Citizen_" + user.getId();
            }
            String authorProfileImage = user.getProfileImage();
            String authorPincode = user.getPincode();

            if (!actorProfileRepo.existsById(actorToken)) {
                ActorProfile profile = ActorProfile.builder()
                        .actorToken(actorToken)
                        .username(authorUsername)
                        .profileImage(authorProfileImage)
                        .bio(user.getBio())
                        .pincode(authorPincode)
                        .homeLatitude(user.getHomeLatitude())
                        .homeLongitude(user.getHomeLongitude())
                        .mutedWords(user.getMutedWords())
                        .profanityFilterLevel(user.getProfanityFilterLevel() != null ? user.getProfanityFilterLevel() : "STRICT")
                        .copyrightStrikes(user.getCopyrightStrikes() != null ? user.getCopyrightStrikes() : 0)
                        .isAdult(user.getIsAdult() != null ? user.getIsAdult() : true)
                        .theme(user.getTheme() != null ? user.getTheme() : "light")
                        .interfaceLanguage(user.getInterfaceLanguage() != null ? user.getInterfaceLanguage() : "en")
                        .preferredLanguage(user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "en")
                        .autoTranslate(user.getAutoTranslate() != null ? user.getAutoTranslate() : false)
                        .createdAt(user.getCreatedAt() != null ? user.getCreatedAt() : new Date())
                        .build();
                actorProfileRepo.save(profile);
            }

            // 5. Backfill actor_token and author snapshot fields across all civic tables
            Long uid = user.getId();

            jdbcTemplate.update(
                "UPDATE posts SET actor_token = ?, author_username = COALESCE(author_username, ?), " +
                "author_profile_image = COALESCE(author_profile_image, ?), " +
                "author_pincode = COALESCE(author_pincode, ?) WHERE user_id = ? AND actor_token IS NULL",
                actorToken, authorUsername, authorProfileImage, authorPincode, uid
            );

            jdbcTemplate.update(
                "UPDATE social_posts SET actor_token = ?, author_username = COALESCE(author_username, ?), " +
                "author_profile_image = COALESCE(author_profile_image, ?) " +
                "WHERE user_id = ? AND actor_token IS NULL",
                actorToken, authorUsername, authorProfileImage, uid
            );

            jdbcTemplate.update(
                "UPDATE comments SET actor_token = ?, author_username = COALESCE(author_username, ?), " +
                "author_profile_image = COALESCE(author_profile_image, ?) WHERE user_id = ? AND actor_token IS NULL",
                actorToken, authorUsername, authorProfileImage, uid
            );

            jdbcTemplate.update("UPDATE post_likes SET actor_token = ? WHERE user_id = ? AND actor_token IS NULL", actorToken, uid);
            jdbcTemplate.update("UPDATE saved_posts SET actor_token = ? WHERE user_id = ? AND actor_token IS NULL", actorToken, uid);
            jdbcTemplate.update("UPDATE content_reports SET reporter_actor_token = ? WHERE reporter_id = ? AND reporter_actor_token IS NULL", actorToken, uid);
            jdbcTemplate.update("UPDATE notifications SET triggered_by_actor_token = ? WHERE triggered_by_user_id = ? AND triggered_by_actor_token IS NULL", actorToken, uid);
            jdbcTemplate.update("UPDATE notifications SET recipient_actor_token = ? WHERE user_id = ? AND recipient_actor_token IS NULL", actorToken, uid);

            jdbcTemplate.update(
                "UPDATE user_tags SET tagged_by_actor_token = ?, tagged_by_username = COALESCE(tagged_by_username, ?) " +
                "WHERE tagged_by_user_id = ? AND tagged_by_actor_token IS NULL",
                actorToken, authorUsername, uid
            );

            jdbcTemplate.update(
                "UPDATE polls SET created_by_actor_token = ? WHERE created_by_user_id = ? AND created_by_actor_token IS NULL",
                actorToken, uid
            );

            jdbcTemplate.update(
                "UPDATE poll_votes SET actor_token = ? WHERE user_id = ? AND actor_token IS NULL",
                actorToken, uid
            );

            // 6. Mark user as migrated
            user.setMigrationStatus("COMPLETED");
            userRepo.save(user);

        } catch (Exception e) {
            log.error("[MIGRATION] Failed to backfill User ID {}: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("Migration halted on User ID " + user.getId(), e);
        }
    }

    private void ensureMigrationTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS migration_progress (
                task_name VARCHAR(100) PRIMARY KEY,
                last_processed_id BIGINT DEFAULT 0,
                total_processed BIGINT DEFAULT 0,
                status VARCHAR(20) DEFAULT 'RUNNING',
                updated_at TIMESTAMP DEFAULT NOW()
            );
        """);

        jdbcTemplate.update("""
            INSERT INTO migration_progress (task_name, last_processed_id, total_processed, status)
            VALUES (?, 0, 0, 'INITIALIZED')
            ON CONFLICT (task_name) DO NOTHING;
        """, TASK_NAME);
    }

    private String getMigrationStatus() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT status FROM migration_progress WHERE task_name = ?",
                String.class, TASK_NAME
            );
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private long getLastProcessedId() {
        try {
            Long id = jdbcTemplate.queryForObject(
                "SELECT last_processed_id FROM migration_progress WHERE task_name = ?",
                Long.class, TASK_NAME
            );
            return id != null ? id : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private void updateCheckpoint(long lastId, int batchCount) {
        jdbcTemplate.update(
            "UPDATE migration_progress SET last_processed_id = ?, total_processed = total_processed + ?, updated_at = NOW() WHERE task_name = ?",
            lastId, batchCount, TASK_NAME
        );
    }

    private void markMigrationCompleted() {
        jdbcTemplate.update(
            "UPDATE migration_progress SET status = 'COMPLETED', updated_at = NOW() WHERE task_name = ?",
            TASK_NAME
        );
    }
}
