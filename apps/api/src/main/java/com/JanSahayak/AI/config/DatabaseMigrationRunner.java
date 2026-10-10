package com.JanSahayak.AI.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DatabaseMigrationRunner implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    }
}
