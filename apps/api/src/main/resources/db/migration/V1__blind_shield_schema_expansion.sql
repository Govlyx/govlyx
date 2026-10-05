-- ============================================================================
-- ZERO-KNOWLEDGE BLIND SHIELD: STAGE 1 SCHEMA EXPANSION (Zero Downtime)
-- As specified in Zero-Knowledge Blind Shield.md
-- ============================================================================

-- 1. Expand users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS actor_salt VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS vault_blob TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS vault_salt VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS seed_blind_salt VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS migration_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_encrypted VARCHAR(512);

CREATE INDEX IF NOT EXISTS idx_user_migration_status ON users (migration_status);
CREATE INDEX IF NOT EXISTS idx_user_email_hash ON users (email_hash);
CREATE INDEX IF NOT EXISTS idx_user_google_id ON users (google_id);

-- 2. Migration progress tracking table
CREATE TABLE IF NOT EXISTS migration_progress (
    task_name VARCHAR(100) PRIMARY KEY,
    last_processed_id BIGINT DEFAULT 0,
    total_processed BIGINT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'RUNNING',
    updated_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO migration_progress (task_name, last_processed_id, total_processed, status)
VALUES ('actor_token_backfill', 0, 0, 'INITIALIZED')
ON CONFLICT (task_name) DO NOTHING;

-- 3. Create actor_profiles table (Approach A: The Civic Persona Layer)
CREATE TABLE IF NOT EXISTS actor_profiles (
    actor_token VARCHAR(70) PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    profile_image VARCHAR(255),
    bio VARCHAR(1000),
    pincode VARCHAR(6),
    home_latitude NUMERIC(10, 8),
    home_longitude NUMERIC(10, 8),
    muted_words VARCHAR(1000),
    blocked_actors TEXT,
    profanity_filter_level VARCHAR(20) DEFAULT 'STRICT',
    copyright_strikes INT NOT NULL DEFAULT 0,
    is_adult BOOLEAN DEFAULT TRUE,
    theme VARCHAR(20) DEFAULT 'light',
    interface_language VARCHAR(10) DEFAULT 'en',
    preferred_language VARCHAR(10) DEFAULT 'en',
    auto_translate BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_actor_profile_token ON actor_profiles (actor_token);
CREATE UNIQUE INDEX IF NOT EXISTS idx_actor_profile_username ON actor_profiles (username);
CREATE INDEX IF NOT EXISTS idx_actor_profile_pincode ON actor_profiles (pincode);
CREATE INDEX IF NOT EXISTS idx_actor_profile_coords ON actor_profiles (home_latitude, home_longitude);

-- 4. Create banned_actors table
CREATE TABLE IF NOT EXISTS banned_actors (
    id BIGSERIAL PRIMARY KEY,
    actor_token VARCHAR(70) NOT NULL UNIQUE,
    reason VARCHAR(500),
    banned_by_admin_id BIGINT REFERENCES users(id),
    banned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_banned_actor_token ON banned_actors (actor_token);

-- 5. Create admin_audit_logs table (CERT-In / Statutory Non-Repudiation)
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    target_actor_token VARCHAR(70),
    target_user_id BIGINT,
    details JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_admin_audit_admin ON admin_audit_logs (admin_id, created_at DESC);

-- 6. Expand posts (Author snapshots to eliminate N+1 queries)
ALTER TABLE posts ADD COLUMN IF NOT EXISTS actor_token VARCHAR(70);
ALTER TABLE posts ADD COLUMN IF NOT EXISTS author_username VARCHAR(100);
ALTER TABLE posts ADD COLUMN IF NOT EXISTS author_profile_image VARCHAR(500);
ALTER TABLE posts ADD COLUMN IF NOT EXISTS author_pincode VARCHAR(6);
CREATE INDEX IF NOT EXISTS idx_post_actor_token ON posts (actor_token);
CREATE INDEX IF NOT EXISTS idx_post_actor_status ON posts (actor_token, status, created_at);

-- 7. Expand social_posts (Author snapshots)
ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS actor_token VARCHAR(70);
ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS author_username VARCHAR(100);
ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS author_profile_image VARCHAR(500);
CREATE INDEX IF NOT EXISTS idx_social_post_actor_token ON social_posts (actor_token);
CREATE INDEX IF NOT EXISTS idx_social_post_actor_created ON social_posts (actor_token, created_at);

-- 8. Expand comments (Author snapshots)
ALTER TABLE comments ADD COLUMN IF NOT EXISTS actor_token VARCHAR(70);
ALTER TABLE comments ADD COLUMN IF NOT EXISTS author_username VARCHAR(100);
ALTER TABLE comments ADD COLUMN IF NOT EXISTS author_profile_image VARCHAR(500);
CREATE INDEX IF NOT EXISTS idx_comment_actor_token ON comments (actor_token);

-- 9. Expand post_likes
ALTER TABLE post_likes ADD COLUMN IF NOT EXISTS actor_token VARCHAR(70);
CREATE INDEX IF NOT EXISTS idx_post_like_actor_token ON post_likes (actor_token);

-- 10. Expand saved_posts
ALTER TABLE saved_posts ADD COLUMN IF NOT EXISTS actor_token VARCHAR(70);
CREATE INDEX IF NOT EXISTS idx_saved_post_actor_token ON saved_posts (actor_token);

-- 11. Expand user_tags
ALTER TABLE user_tags ADD COLUMN IF NOT EXISTS tagged_by_actor_token VARCHAR(70);
ALTER TABLE user_tags ADD COLUMN IF NOT EXISTS tagged_by_username VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_user_tag_tagged_by_actor ON user_tags (tagged_by_actor_token);

-- 12. Expand content_reports
ALTER TABLE content_reports ADD COLUMN IF NOT EXISTS reporter_actor_token VARCHAR(70);
CREATE INDEX IF NOT EXISTS idx_report_reporter_actor ON content_reports (reporter_actor_token);

-- 13. Expand notifications (Both triggered_by and recipient actor tokens)
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS triggered_by_actor_token VARCHAR(70);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS recipient_actor_token VARCHAR(70);
CREATE INDEX IF NOT EXISTS idx_notification_triggered_by_actor ON notifications (triggered_by_actor_token);
CREATE INDEX IF NOT EXISTS idx_notification_recipient_actor ON notifications (recipient_actor_token, created_at DESC);

-- 14. Expand polls & poll_votes (Bug 8 Fix)
ALTER TABLE polls ADD COLUMN IF NOT EXISTS created_by_actor_token VARCHAR(70);
ALTER TABLE polls ALTER COLUMN created_by_user_id DROP NOT NULL;
ALTER TABLE poll_votes ADD COLUMN IF NOT EXISTS actor_token VARCHAR(70);
ALTER TABLE poll_votes ALTER COLUMN user_id DROP NOT NULL;
CREATE INDEX IF NOT EXISTS idx_poll_created_by_actor ON polls (created_by_actor_token);
CREATE UNIQUE INDEX IF NOT EXISTS uq_poll_vote_actor_option
    ON poll_votes (poll_id, actor_token, poll_option_id)
    WHERE actor_token IS NOT NULL;
