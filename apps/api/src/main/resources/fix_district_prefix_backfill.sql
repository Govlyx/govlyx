-- =============================================================================
-- BACKFILL: social_posts.district_prefix
-- =============================================================================
-- WHY: Posts created before districtPrefix was reliably populated have
--      district_prefix = NULL even though they have a valid pincode.
--      The Neighborhood Q&A "Wider District" feed queries by district_prefix,
--      so these posts are invisible in the DISTRICT scope.
--
-- SAFE TO RE-RUN: The WHERE clause only touches rows that are actually broken.
-- =============================================================================

-- Step 1 — Backfill ALL social_posts that have a pincode but no district_prefix
UPDATE social_posts
SET district_prefix = SUBSTRING(pincode, 1, 3),
    state_prefix    = SUBSTRING(pincode, 1, 2)
WHERE district_prefix IS NULL
  AND pincode IS NOT NULL
  AND LENGTH(pincode) >= 3;

-- Step 2 — Report how many rows were fixed (useful for your logs)
-- SELECT COUNT(*) AS rows_fixed
-- FROM social_posts
-- WHERE district_prefix IS NOT NULL
--   AND state_prefix IS NOT NULL
--   AND pincode IS NOT NULL;

-- =============================================================================
-- OPTIONAL: Add a partial index to speed up the DISTRICT Q&A query
-- (the main fix in SocialPostRepo.java uses SUBSTRING — this index helps it)
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_social_posts_qa_district
    ON social_posts (category, status, district_prefix, id DESC)
    WHERE is_flagged = false
      AND category = 'NEIGHBORHOOD_QUESTION';

-- Fallback index for the NULL districtPrefix case (covers the OR branch)
CREATE INDEX IF NOT EXISTS idx_social_posts_qa_pincode_prefix
    ON social_posts (category, status, SUBSTRING(pincode, 1, 3), id DESC)
    WHERE is_flagged = false
      AND district_prefix IS NULL
      AND category = 'NEIGHBORHOOD_QUESTION';
