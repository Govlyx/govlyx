-- ============================================================================
-- V4: CONTRACTION & ZERO-KNOWLEDGE SEAL (Irreversible Forensic Severing)
-- ============================================================================

-- 1. Make user_id / reporter_id nullable across civic & interaction tables
ALTER TABLE posts ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE social_posts ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE comments ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE post_likes ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE saved_posts ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE content_reports ALTER COLUMN reporter_id DROP NOT NULL;

-- 2. Sever forensic relational links ONLY for regular citizens (ROLE_USER):
-- Authority accounts (ROLE_DEPARTMENT and ROLE_ADMIN) keep user_id for verified broadcasts and official responses!
UPDATE posts 
SET user_id = NULL 
WHERE user_id IN (
    SELECT u.id FROM users u 
    JOIN roles r ON u.role_id = r.id 
    WHERE r.name = 'ROLE_USER'
);

UPDATE social_posts 
SET user_id = NULL 
WHERE user_id IN (
    SELECT u.id FROM users u 
    JOIN roles r ON u.role_id = r.id 
    WHERE r.name = 'ROLE_USER'
);

UPDATE comments 
SET user_id = NULL 
WHERE user_id IN (
    SELECT u.id FROM users u 
    JOIN roles r ON u.role_id = r.id 
    WHERE r.name = 'ROLE_USER'
);

-- Personal interactions (likes, saves, reports, polls, poll_votes, tags, notifications)
UPDATE post_likes   SET user_id = NULL WHERE actor_token IS NOT NULL;
UPDATE saved_posts  SET user_id = NULL WHERE actor_token IS NOT NULL;
UPDATE user_tags    SET tagged_by_user_id = NULL WHERE tagged_by_actor_token IS NOT NULL;
UPDATE content_reports SET reporter_id = NULL WHERE reporter_actor_token IS NOT NULL;
UPDATE polls        SET created_by_user_id = NULL WHERE created_by_actor_token IS NOT NULL;
UPDATE poll_votes   SET user_id = NULL WHERE actor_token IS NOT NULL;
UPDATE notifications SET triggered_by_user_id = NULL WHERE triggered_by_actor_token IS NOT NULL;

-- 3. Enforce Author Integrity Constraints across civic tables:
-- Citizens have actor_token (user_id IS NULL); Authorities (Dept/Admin) have user_id (actor_token IS NULL):
ALTER TABLE posts DROP CONSTRAINT IF EXISTS chk_post_author;
ALTER TABLE posts ADD CONSTRAINT chk_post_author 
    CHECK ((user_id IS NULL AND actor_token IS NOT NULL) OR (user_id IS NOT NULL AND actor_token IS NULL));

ALTER TABLE social_posts DROP CONSTRAINT IF EXISTS chk_social_post_author;
ALTER TABLE social_posts ADD CONSTRAINT chk_social_post_author 
    CHECK ((user_id IS NULL AND actor_token IS NOT NULL) OR (user_id IS NOT NULL AND actor_token IS NULL));

ALTER TABLE comments DROP CONSTRAINT IF EXISTS chk_comment_author;
ALTER TABLE comments ADD CONSTRAINT chk_comment_author 
    CHECK ((user_id IS NULL AND actor_token IS NOT NULL) OR (user_id IS NOT NULL AND actor_token IS NULL));

-- Personal interactions (likes, saves, reports) are 100% actor_token driven:
ALTER TABLE post_likes   ALTER COLUMN actor_token SET NOT NULL;
ALTER TABLE saved_posts  ALTER COLUMN actor_token SET NOT NULL;
ALTER TABLE content_reports ALTER COLUMN reporter_actor_token SET NOT NULL;

-- 4. Recreate unique constraints on post_likes using actor_token
DROP INDEX IF EXISTS uq_post_like_post_user;
DROP INDEX IF EXISTS uq_post_like_social_post_user;
DROP INDEX IF EXISTS uq_post_like_post_actor;
DROP INDEX IF EXISTS uq_post_like_social_post_actor;
CREATE UNIQUE INDEX uq_post_like_post_actor
    ON post_likes (post_id, actor_token) WHERE post_id IS NOT NULL;
CREATE UNIQUE INDEX uq_post_like_social_post_actor
    ON post_likes (social_post_id, actor_token) WHERE social_post_id IS NOT NULL;

-- 5. Recreate unique constraints on saved_posts using actor_token
DROP INDEX IF EXISTS uk_saved_post_user_social_post;
DROP INDEX IF EXISTS uk_saved_post_user_post;
DROP INDEX IF EXISTS uk_saved_post_user_social_post_actor;
DROP INDEX IF EXISTS uk_saved_post_user_post_actor;
CREATE UNIQUE INDEX uk_saved_post_user_social_post_actor
    ON saved_posts (actor_token, social_post_id) WHERE social_post_id IS NOT NULL;
CREATE UNIQUE INDEX uk_saved_post_user_post_actor
    ON saved_posts (actor_token, post_id) WHERE post_id IS NOT NULL;

-- 6. Enforce unique constraint on email_hash in users table
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_user_email_hash;
ALTER TABLE users ADD CONSTRAINT uk_user_email_hash UNIQUE (email_hash);

-- 7. Foreign key adjustments:
ALTER TABLE polls DROP CONSTRAINT IF EXISTS fk_poll_created_by;
ALTER TABLE poll_votes DROP CONSTRAINT IF EXISTS fk_poll_vote_user;
ALTER TABLE poll_votes DROP CONSTRAINT IF EXISTS uk_poll_vote_user_option;

ALTER TABLE posts DROP CONSTRAINT IF EXISTS fk_post_user;
ALTER TABLE posts ADD CONSTRAINT fk_post_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE social_posts DROP CONSTRAINT IF EXISTS fk_social_post_user;
ALTER TABLE social_posts ADD CONSTRAINT fk_social_post_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE comments DROP CONSTRAINT IF EXISTS fk_comment_user;
ALTER TABLE comments ADD CONSTRAINT fk_comment_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE post_likes   DROP CONSTRAINT IF EXISTS fk_post_like_user;
ALTER TABLE saved_posts  DROP CONSTRAINT IF EXISTS fk_saved_post_user;
ALTER TABLE user_tags    DROP CONSTRAINT IF EXISTS fk_user_tag_tagged_by;
ALTER TABLE content_reports DROP CONSTRAINT IF EXISTS fk_content_report_reporter;

-- 8. Drop legacy plaintext email constraints, indexes, and column
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_user_email;
DROP INDEX IF EXISTS idx_user_email;
DROP INDEX IF EXISTS idx_users_email;
ALTER TABLE users DROP COLUMN IF EXISTS email;
ALTER TABLE users DROP COLUMN IF EXISTS migration_status;
ALTER TABLE users DROP COLUMN IF EXISTS display_name;
ALTER TABLE actor_profiles DROP COLUMN IF EXISTS display_name;
ALTER TABLE posts DROP COLUMN IF EXISTS author_display_name;
ALTER TABLE social_posts DROP COLUMN IF EXISTS author_display_name;
DROP TABLE IF EXISTS migration_progress;
