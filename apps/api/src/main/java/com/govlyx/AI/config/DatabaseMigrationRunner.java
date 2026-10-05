package com.govlyx.AI.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DatabaseMigrationRunner implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Override
    public void run(String... args) throws Exception {
        try {
            log.info("Running database migration check for email verification...");
            // Set existing users' email verification status to true to avoid locking out existing users
            int updatedRows = jdbcTemplate.update(
                "UPDATE users SET is_email_verified = true WHERE is_email_verified IS NULL"
            );
            if (updatedRows > 0) {
                log.info("Database migration complete: updated {} existing user accounts to be verified", updatedRows);
            } else {
                log.info("Database migration check: no uninitialized user verification statuses found");
            }
        } catch (Exception e) {
            log.error("Failed to run database migration update for existing users", e);
        }

        try {
            log.info("Running database migration check for user themes...");
            int themeRows = jdbcTemplate.update(
                "UPDATE users SET theme = 'light' WHERE theme IS NULL OR TRIM(theme) = ''"
            );
            if (themeRows > 0) {
                log.info("Database migration complete: updated {} existing user accounts with null/empty theme to 'light'", themeRows);
            } else {
                log.info("Database migration check: all user accounts have valid theme values");
            }
        } catch (Exception e) {
            log.error("Failed to run database migration update for user themes", e);
        }

        try {
            log.info("Running database migration check for Model 1 citizen account anonymization...");
            // Sanitize citizen accounts in users table to use opaque acc_<uuid> handles,
            // permanently severing correlation between users table and actor_profiles / civic posts.
            int sanitizedRows = jdbcTemplate.update(
                "UPDATE users SET username = 'acc_' || id || '_' || SUBSTRING(MD5(RANDOM()::text || id::text), 1, 8) " +
                "WHERE role_id = (SELECT id FROM roles WHERE name = 'ROLE_USER') AND username NOT LIKE 'acc_%'"
            );
            if (sanitizedRows > 0) {
                log.info("Database migration complete: decoupled {} citizen accounts into opaque handles", sanitizedRows);
            } else {
                log.info("Database migration check: all citizen accounts already use opaque handles");
            }
        } catch (Exception e) {
            log.error("Failed to run database migration update for citizen account anonymity", e);
        }

        try {
            log.info("Running database migration check to relax user_id NOT NULL constraints for Blind Shield...");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS notifications ALTER COLUMN user_id DROP NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS comments ALTER COLUMN user_id DROP NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS social_posts ALTER COLUMN user_id DROP NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS post_likes ALTER COLUMN user_id DROP NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS post_shares ALTER COLUMN user_id DROP NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS post_views ALTER COLUMN user_id DROP NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS saved_posts ALTER COLUMN user_id DROP NOT NULL;");
            log.info("Database migration complete: user_id NOT NULL constraints relaxed for Blind Shield.");
        } catch (Exception e) {
            log.warn("Notice: schema relaxation check encountered non-fatal exception: {}", e.getMessage());
        }

        try {
            log.info("Running database migration check to synchronize post & social post comment counts...");
            int spCommentsUpdated = jdbcTemplate.update(
                "UPDATE social_posts sp " +
                "SET comment_count = COALESCE((SELECT COUNT(*) FROM comments c WHERE c.social_post_id = sp.id AND (c.is_deleted IS NULL OR c.is_deleted = false)), 0) " +
                "WHERE sp.comment_count IS NULL OR sp.comment_count != COALESCE((SELECT COUNT(*) FROM comments c WHERE c.social_post_id = sp.id AND (c.is_deleted IS NULL OR c.is_deleted = false)), 0)"
            );
            int pCommentsUpdated = jdbcTemplate.update(
                "UPDATE posts p " +
                "SET comment_count = COALESCE((SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id AND (c.is_deleted IS NULL OR c.is_deleted = false)), 0) " +
                "WHERE p.comment_count IS NULL OR p.comment_count != COALESCE((SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id AND (c.is_deleted IS NULL OR c.is_deleted = false)), 0)"
            );
            log.info("Database migration complete: synchronized comment counts for {} social posts and {} posts",
                    spCommentsUpdated, pCommentsUpdated);

            if (cacheManager != null) {
                org.springframework.cache.Cache hligCache = cacheManager.getCache("hlig_feed");
                if (hligCache != null) {
                    hligCache.clear();
                    log.info("Cleared 'hlig_feed' cache on startup to ensure updated comment counts are served.");
                }
            }
        } catch (Exception e) {
            log.error("Failed to run database migration update for comment counts", e);
        }
    }
}
