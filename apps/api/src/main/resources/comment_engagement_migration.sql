-- 1. Ensure existing columns have default values if they were added by Hibernate previously with NULLs
UPDATE comments SET like_count = 0 WHERE like_count IS NULL;
UPDATE comments SET dislike_count = 0 WHERE dislike_count IS NULL;
UPDATE comments SET is_pinned = FALSE WHERE is_pinned IS NULL;
UPDATE comments SET is_deleted = FALSE WHERE is_deleted IS NULL;
UPDATE comments SET ranking_score = 0.0 WHERE ranking_score IS NULL;

-- 2. Add new columns or alter existing ones to be NOT NULL
ALTER TABLE comments 
    ADD COLUMN IF NOT EXISTS like_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS dislike_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_by_type VARCHAR(32) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS ranking_score DOUBLE PRECISION NOT NULL DEFAULT 0.0;

-- 3. Safely enforce NOT NULL on columns (in case Hibernate created them as nullable previously)
ALTER TABLE comments 
    ALTER COLUMN like_count SET DEFAULT 0,
    ALTER COLUMN like_count SET NOT NULL,
    ALTER COLUMN dislike_count SET DEFAULT 0,
    ALTER COLUMN dislike_count SET NOT NULL,
    ALTER COLUMN is_pinned SET DEFAULT FALSE,
    ALTER COLUMN is_pinned SET NOT NULL,
    ALTER COLUMN is_deleted SET DEFAULT FALSE,
    ALTER COLUMN is_deleted SET NOT NULL,
    ALTER COLUMN ranking_score SET DEFAULT 0.0,
    ALTER COLUMN ranking_score SET NOT NULL;

-- 4. Create the new comment_interactions table
CREATE TABLE IF NOT EXISTS comment_interactions (
    id BIGSERIAL PRIMARY KEY,
    comment_id BIGINT NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    interaction_type VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT unique_user_comment_interaction UNIQUE (comment_id, user_id)
);

-- 5. Data Migration and Drop (Wrapped in a DO block to prevent errors if table is already gone)
DO $$ 
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'comment_reactions') THEN
        INSERT INTO comment_interactions (comment_id, user_id, interaction_type, created_at)
        SELECT comment_id, user_id, 'LIKE', created_at 
        FROM comment_reactions 
        WHERE reaction_type IN ('LIKE', 'LOVE', 'WOW', 'FIRE', 'CLAP');

        UPDATE comments c
        SET like_count = (
            SELECT COUNT(*) FROM comment_interactions ci 
            WHERE ci.comment_id = c.id AND ci.interaction_type = 'LIKE'
        );

        DROP TABLE comment_reactions;
    END IF;
END $$;

-- 6. Create Composite Indexes for Performance
CREATE INDEX IF NOT EXISTS idx_comments_post_ranking ON comments (post_id, ranking_score DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_comments_social_ranking ON comments (social_post_id, ranking_score DESC, created_at DESC);
