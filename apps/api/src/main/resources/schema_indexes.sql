-- =============================================================================
-- JanSahayak-AI — Performance Indexes
-- =============================================================================
-- Run these on your PostgreSQL DB once (safe to re-run — all use IF NOT EXISTS).
-- Run without CONCURRENTLY so they can execute inside transaction blocks.
-- =============================================================================

-- =============================================================================
-- SOCIAL POSTS FEED — hottest query path
-- =============================================================================

-- HOT tab (virality score): most-read query in the entire system
CREATE INDEX IF NOT EXISTS idx_social_post_virality
    ON social_posts (status, virality_score DESC, created_at DESC)
    WHERE is_flagged = false;

-- TOP tab (engagement score)
CREATE INDEX IF NOT EXISTS idx_social_post_engagement
    ON social_posts (status, engagement_score DESC, created_at DESC)
    WHERE is_flagged = false;

-- NEW tab (chronological)
CREATE INDEX IF NOT EXISTS idx_social_post_status_created
    ON social_posts (status, created_at DESC);

-- Cursor-based pagination (id DESC fallback)
CREATE INDEX IF NOT EXISTS idx_social_post_status_id_desc
    ON social_posts (status, id DESC);

-- =============================================================================
-- GEO WATERFALL — local / area feed (pincode → district → state)
-- =============================================================================

-- Pincode-level feed (most specific, highest relevance)
CREATE INDEX IF NOT EXISTS idx_social_post_pincode
    ON social_posts (pincode, status, created_at DESC)
    WHERE pincode IS NOT NULL;

-- District-prefix feed (3-digit prefix covers all pincodes in district)
CREATE INDEX IF NOT EXISTS idx_social_post_district_prefix
    ON social_posts (district_prefix, status, created_at DESC)
    WHERE district_prefix IS NOT NULL;

-- State-prefix feed (2-digit prefix covers all pincodes in state)
CREATE INDEX IF NOT EXISTS idx_social_post_state_prefix
    ON social_posts (state_prefix, status, created_at DESC)
    WHERE state_prefix IS NOT NULL;

-- =============================================================================
-- COMMUNITY FEED — /api/communities and community post tab
-- =============================================================================

-- Community posts (following tab, community detail page)
CREATE INDEX IF NOT EXISTS idx_social_post_community_id
    ON social_posts (community_id, status, created_at DESC)
    WHERE community_id IS NOT NULL;

-- Community list browse (GET /api/communities)
CREATE INDEX IF NOT EXISTS idx_communities_created_at
    ON communities (created_at DESC);

-- =============================================================================
-- ISSUE / BROADCAST POSTS — official feed
-- =============================================================================

-- Official feed: status + created_at
CREATE INDEX IF NOT EXISTS idx_posts_status_created
    ON posts (status, created_at DESC);

-- Cursor pagination
CREATE INDEX IF NOT EXISTS idx_posts_status_id_desc
    ON posts (status, id DESC);

-- User's own posts (profile page)
CREATE INDEX IF NOT EXISTS idx_posts_user_id_created
    ON posts (user_id, created_at DESC);

-- =============================================================================
-- NOTIFICATIONS — polled every 60 s by frontend
-- =============================================================================

-- Unread count query: SELECT COUNT(*) WHERE user_id=? AND is_read=false
-- This is the single most-polled query in the entire API surface.
CREATE INDEX IF NOT EXISTS idx_notifications_user_read
    ON notifications (user_id, is_read, created_at DESC);

-- Notification list cursor pagination
CREATE INDEX IF NOT EXISTS idx_notifications_user_id_desc
    ON notifications (user_id, id DESC);

-- Category tab cursor pagination (invites, interactions)
CREATE INDEX IF NOT EXISTS idx_notifications_user_type_id
    ON notifications (user_id, notification_type, id DESC);

-- =============================================================================
-- USERS — search and profile lookups
-- =============================================================================

-- Username search (admin dashboard, user search, @mentions)
-- text_pattern_ops enables prefix LIKE 'abc%' to use the index
CREATE INDEX IF NOT EXISTS idx_users_username_lower
    ON users (LOWER(username) text_pattern_ops);

-- Email lookup (login)
CREATE INDEX IF NOT EXISTS idx_users_email
    ON users (LOWER(email));

-- =============================================================================
-- INTERACTIONS — like/save status checks (batch queries)
-- =============================================================================

-- PostLike lookup by social post + user (batch interaction status)
CREATE INDEX IF NOT EXISTS idx_post_like_social_post_user
    ON post_likes (social_post_id, user_id, reaction_type)
    WHERE social_post_id IS NOT NULL;

-- PostLike lookup by post + user (broadcast post interactions)
CREATE INDEX IF NOT EXISTS idx_post_like_post_user
    ON post_likes (post_id, user_id, reaction_type)
    WHERE post_id IS NOT NULL;

-- SavedPost lookup by user + social post
CREATE INDEX IF NOT EXISTS idx_saved_post_user_social
    ON saved_posts (user_id, social_post_id)
    WHERE social_post_id IS NOT NULL;

-- SavedPost lookup by user + broadcast post
CREATE INDEX IF NOT EXISTS idx_saved_post_user_post
    ON saved_posts (user_id, post_id)
    WHERE post_id IS NOT NULL;

-- =============================================================================
-- POST VIEWS — deduplication window check
-- =============================================================================

-- View deduplication: "has user viewed this post in last 1h?"
CREATE INDEX IF NOT EXISTS idx_post_view_social_post_user_time
    ON post_views (social_post_id, user_id, viewed_at DESC)
    WHERE social_post_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_post_view_post_user_time
    ON post_views (post_id, user_id, viewed_at DESC)
    WHERE post_id IS NOT NULL;

-- =============================================================================
-- COMMENTS — comment list + count queries
-- =============================================================================

-- Comments for a social post (paginated)
CREATE INDEX IF NOT EXISTS idx_comments_social_post_created
    ON comments (social_post_id, created_at DESC)
    WHERE social_post_id IS NOT NULL;

-- Comments for a broadcast post (paginated)
CREATE INDEX IF NOT EXISTS idx_comments_post_created
    ON comments (post_id, created_at DESC)
    WHERE post_id IS NOT NULL;

-- Comments by user (activity tab)
CREATE INDEX IF NOT EXISTS idx_comments_user_created
    ON comments (user_id, created_at DESC);

-- =============================================================================
-- COMMUNITY CHAT MESSAGES
-- =============================================================================

-- Cursor pagination (hottest query path for chat history)
CREATE INDEX IF NOT EXISTS idx_community_messages_query
    ON community_messages (community_id, created_at DESC, id DESC);

-- Pinned messages lookup
CREATE INDEX IF NOT EXISTS idx_community_messages_pinned
    ON community_messages (community_id)
    WHERE is_pinned = true;

-- Expiry pruner query
CREATE INDEX IF NOT EXISTS idx_community_messages_expiry
    ON community_messages (expires_at)
    WHERE expires_at IS NOT NULL;

-- =============================================================================
-- FULL-TEXT SEARCH (pg_trgm) — Required for blazing fast LIKE '%query%' searches
-- Note: Run these in Supabase SQL Editor.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Index Community fields
CREATE INDEX IF NOT EXISTS idx_community_name_trgm ON communities USING gin (lower(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_community_desc_trgm ON communities USING gin (lower(description) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_community_tags_trgm ON communities USING gin (lower(tags) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_community_category_trgm ON communities USING gin (lower(category) gin_trgm_ops);

-- Index SocialPost fields
CREATE INDEX IF NOT EXISTS idx_socialpost_content_trgm ON social_posts USING gin (lower(content) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_socialpost_hashtags_trgm ON social_posts USING gin (lower(hashtags) gin_trgm_ops);

-- Index Post fields
CREATE INDEX IF NOT EXISTS idx_post_content_trgm ON posts USING gin (lower(content) gin_trgm_ops);

