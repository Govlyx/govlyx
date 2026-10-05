-- =============================================================================
-- RECONCILE POST & SOCIAL POST ENGAGEMENT COUNTERS
-- Synchronizes denormalized comment_count and share_count columns
-- with the actual records present in the comments and post_shares tables.
-- =============================================================================

-- 1. Synchronize comment counts for Posts (Issues & Government Broadcasts)
UPDATE posts p
SET comment_count = COALESCE((
    SELECT COUNT(*) 
    FROM comments c 
    WHERE c.post_id = p.id 
      AND (c.is_deleted = FALSE OR c.is_deleted IS NULL)
), 0);

-- 2. Synchronize share counts for Posts (Issues & Government Broadcasts)
UPDATE posts p
SET share_count = COALESCE((
    SELECT COUNT(*) 
    FROM post_shares ps 
    WHERE ps.post_id = p.id
), 0);

-- 3. Synchronize comment counts for SocialPosts (Community & Q&A Posts)
UPDATE social_posts sp
SET comment_count = COALESCE((
    SELECT COUNT(*) 
    FROM comments c 
    WHERE c.social_post_id = sp.id 
      AND (c.is_deleted = FALSE OR c.is_deleted IS NULL)
), 0);

-- 4. Synchronize share counts for SocialPosts (Community & Q&A Posts)
UPDATE social_posts sp
SET share_count = COALESCE((
    SELECT COUNT(*) 
    FROM post_shares ps 
    WHERE ps.social_post_id = sp.id
), 0);
