package com.Govlyx.AI.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Executes critical database data cleanups BEFORE Hibernate's SchemaUpdate runs.
 *
 * This prevents Hibernate DDL update failures caused by orphaned rows
 * (e.g. post_likes or post_views referencing deleted post_ids) violating
 * foreign key constraints when Hibernate executes ALTER TABLE ADD CONSTRAINT.
 */
@Component("databasePreMigrationCleanup")
@Slf4j
public class DatabasePreMigrationCleanup {

    public DatabasePreMigrationCleanup(DataSource dataSource) {
        log.info("[DatabasePreMigrationCleanup] Running pre-Hibernate schema data sanity check...");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Clean up orphaned post_likes pointing to non-existent posts
            executeCleanup(stmt,
                "DELETE FROM post_likes WHERE post_id IS NOT NULL AND post_id NOT IN (SELECT id FROM posts)",
                "post_likes");

            // Clean up orphaned post_views pointing to non-existent posts
            executeCleanup(stmt,
                "DELETE FROM post_views WHERE post_id IS NOT NULL AND post_id NOT IN (SELECT id FROM posts)",
                "post_views");

            // Clean up orphaned comments pointing to non-existent posts
            executeCleanup(stmt,
                "DELETE FROM comments WHERE post_id IS NOT NULL AND post_id NOT IN (SELECT id FROM posts)",
                "comments");

            // Clean up orphaned saved_posts pointing to non-existent posts
            executeCleanup(stmt,
                "DELETE FROM saved_posts WHERE post_id IS NOT NULL AND post_id NOT IN (SELECT id FROM posts)",
                "saved_posts");

        } catch (Exception e) {
            log.warn("[DatabasePreMigrationCleanup] Notice during pre-migration check: {}", e.getMessage());
        }
    }

    private void executeCleanup(Statement stmt, String sql, String tableName) {
        try {
            int cleaned = stmt.executeUpdate(sql);
            if (cleaned > 0) {
                log.info("[DatabasePreMigrationCleanup] Successfully purged {} orphaned records from '{}'", cleaned, tableName);
            }
        } catch (Exception e) {
            log.debug("[DatabasePreMigrationCleanup] Notice for '{}': {}", tableName, e.getMessage());
        }
    }

    @Configuration
    public static class DependsOnConfig {
        /**
         * Ensures that any EntityManagerFactory bean waits for databasePreMigrationCleanup
         * to complete before Hibernate inspects the database and applies DDL schema updates.
         */
        @Bean
        public static EntityManagerFactoryDependsOnPostProcessor databasePreMigrationDependsOnPostProcessor() {
            return new EntityManagerFactoryDependsOnPostProcessor("databasePreMigrationCleanup");
        }
    }
}
