-- ==============================================================================
-- MIGRATION SCRIPT: GPS Bounding Box & Pincode Clustering
-- ==============================================================================

-- 1. Add GPS coordinates to Users table
ALTER TABLE users 
ADD COLUMN home_latitude NUMERIC(10, 8),
ADD COLUMN home_longitude NUMERIC(10, 8);

-- 2. Add Clustering & Demographic fields to Pincode Lookup table
ALTER TABLE pincode_lookup
ADD COLUMN IF NOT EXISTS urban_cluster_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS urban_cluster_name VARCHAR(100),
ADD COLUMN IF NOT EXISTS settlement_type VARCHAR(20) NOT NULL DEFAULT 'URBAN',
ADD COLUMN IF NOT EXISTS is_cantonment BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS is_hill_state BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Add GPS and Targeting Scopes to Social Posts table
ALTER TABLE social_posts
ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 8),
ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 8),
ADD COLUMN IF NOT EXISTS qa_poster_scope VARCHAR(20),
ADD COLUMN IF NOT EXISTS target_urban_cluster_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS target_district VARCHAR(100);

-- ==============================================================================
-- CLUSTERING LOGIC: Building Urban Clusters
-- ==============================================================================

-- 4A. Group contiguous city pincodes into a single Urban Cluster ID
UPDATE pincode_lookup
SET urban_cluster_id = LOWER(REPLACE(city, ' ', '_')) || '_' || LOWER(REPLACE(district, ' ', '_')),
    urban_cluster_name = city
WHERE city IS NOT NULL AND city != '';

-- 4B. Group remaining (rural/village) pincodes into District-level rural clusters
UPDATE pincode_lookup
SET urban_cluster_id = 'rural_' || LOWER(REPLACE(district, ' ', '_')),
    urban_cluster_name = district,
    settlement_type = 'RURAL'
WHERE (city IS NULL OR city = '') AND district IS NOT NULL;

-- 5. Create index for fast clustering lookups
CREATE INDEX IF NOT EXISTS idx_pincode_lookup_cluster ON pincode_lookup(urban_cluster_id);

-- 6. Create indexes for social_posts geo-scoping (required for performant QA feed queries)
-- Bounding-box pre-filter before Haversine (composite lat/lon range scan)
CREATE INDEX IF NOT EXISTS idx_sp_coords ON social_posts(latitude, longitude);
-- Fast lookup by urban cluster ID for CITY-scope feed
CREATE INDEX IF NOT EXISTS idx_sp_target_cluster ON social_posts(target_urban_cluster_id);
-- Fast lookup by district string for DISTRICT-scope feed
CREATE INDEX IF NOT EXISTS idx_sp_target_district ON social_posts(target_district);
