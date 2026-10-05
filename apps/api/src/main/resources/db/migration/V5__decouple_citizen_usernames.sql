-- ============================================================================
-- V5: DECOUPLE CITIZEN USERNAMES (Zero-Knowledge Clean Break)
-- ============================================================================

-- 1. Sanitize citizen accounts in users table to use opaque acc_<id>_<hash> handles.
-- Authority accounts (ROLE_DEPARTMENT and ROLE_ADMIN) keep their official handles.
UPDATE users 
SET username = 'acc_' || id || '_' || SUBSTRING(MD5(RANDOM()::text || id::text), 1, 8)
WHERE role_id = (SELECT id FROM roles WHERE name = 'ROLE_USER') 
  AND username NOT LIKE 'acc_%';

-- 2. Ensure all posts, social_posts, and comments reflect the civic pseudonym from actor_profiles
UPDATE posts p
SET author_username = ap.username
FROM actor_profiles ap
WHERE p.actor_token = ap.actor_token
  AND (p.author_username IS NULL OR p.author_username LIKE 'acc_%');

UPDATE social_posts sp
SET author_username = ap.username
FROM actor_profiles ap
WHERE sp.actor_token = ap.actor_token
  AND (sp.author_username IS NULL OR sp.author_username LIKE 'acc_%');

UPDATE comments c
SET author_username = ap.username
FROM actor_profiles ap
WHERE c.actor_token = ap.actor_token
  AND (c.author_username IS NULL OR c.author_username LIKE 'acc_%');
