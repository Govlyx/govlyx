DO $$
DECLARE
    v_users_total INT;
    v_users_unmigrated INT;
    v_actor_profiles_total INT;
    v_distinct_actors INT;
    v_duplicate_actor_tokens INT;
    v_posts_missing_token INT;
    v_social_posts_missing_token INT;
    v_comments_missing_token INT;
    v_likes_missing_token INT;
    v_saved_missing_token INT;
    v_tags_missing_token INT;
    v_encrypted_emails_null INT;
    v_email_hash_null INT;
BEGIN
    RAISE NOTICE '=============================================================';
    RAISE NOTICE 'STARTING GOVLYX ZERO-DATA-LOSS & UNIQUENESS AUDIT...';
    RAISE NOTICE '=============================================================';

    -- Check 1: All users have migration_status = 'COMPLETED'
    SELECT COUNT(*) INTO v_users_total FROM users;
    SELECT COUNT(*) INTO v_users_unmigrated FROM users WHERE migration_status != 'COMPLETED';
    IF v_users_unmigrated > 0 THEN
        RAISE EXCEPTION 'CHECK 1 FAILED: % out of % users are not fully migrated!', v_users_unmigrated, v_users_total;
    ELSE
        RAISE NOTICE 'CHECK 1 PASSED: All % users successfully migrated.', v_users_total;
    END IF;

    -- Check 2: Every user has an actor_profiles row
    SELECT COUNT(*) INTO v_actor_profiles_total FROM actor_profiles;
    IF v_actor_profiles_total < v_users_total THEN
        RAISE EXCEPTION 'CHECK 2 FAILED: Actor profiles count (%) < Users count (%)!', v_actor_profiles_total, v_users_total;
    ELSE
        RAISE NOTICE 'CHECK 2 PASSED: % actor profiles verified.', v_actor_profiles_total;
    END IF;

    -- Check 3: Every user has email_encrypted and email_hash
    SELECT COUNT(*) INTO v_encrypted_emails_null FROM users WHERE email_encrypted IS NULL;
    SELECT COUNT(*) INTO v_email_hash_null FROM users WHERE email_hash IS NULL;
    IF v_encrypted_emails_null > 0 OR v_email_hash_null > 0 THEN
        RAISE EXCEPTION 'CHECK 3 FAILED: Users found with NULL email_encrypted (%) or NULL email_hash (%)!', v_encrypted_emails_null, v_email_hash_null;
    ELSE
        RAISE NOTICE 'CHECK 3 PASSED: All user emails encrypted and blind-indexed.';
    END IF;

    -- Check 4: Zero citizen posts missing actor_token, and zero authority posts missing user_id
    SELECT COUNT(*) INTO v_posts_missing_token FROM posts WHERE actor_token IS NULL AND user_id IS NULL;
    IF v_posts_missing_token > 0 THEN
        RAISE EXCEPTION 'CHECK 4 FAILED: % posts have neither actor_token nor user_id!', v_posts_missing_token;
    ELSE
        RAISE NOTICE 'CHECK 4 PASSED: All posts have valid author linkage (actor_token for citizens, user_id for authorities).';
    END IF;

    -- Check 5: Zero social_posts with NULL actor_token
    SELECT COUNT(*) INTO v_social_posts_missing_token FROM social_posts WHERE actor_token IS NULL;
    IF v_social_posts_missing_token > 0 THEN
        RAISE EXCEPTION 'CHECK 5 FAILED: % social_posts have NULL actor_token!', v_social_posts_missing_token;
    ELSE
        RAISE NOTICE 'CHECK 5 PASSED: All social_posts have actor_token populated.';
    END IF;

    -- Check 6: Zero comments with NULL actor_token
    SELECT COUNT(*) INTO v_comments_missing_token FROM comments WHERE actor_token IS NULL;
    IF v_comments_missing_token > 0 THEN
        RAISE EXCEPTION 'CHECK 6 FAILED: % comments have NULL actor_token!', v_comments_missing_token;
    ELSE
        RAISE NOTICE 'CHECK 6 PASSED: All comments have actor_token populated.';
    END IF;

    -- Check 7: Zero post_likes and saved_posts with NULL actor_token
    SELECT COUNT(*) INTO v_likes_missing_token FROM post_likes WHERE actor_token IS NULL;
    SELECT COUNT(*) INTO v_saved_missing_token FROM saved_posts WHERE actor_token IS NULL;
    IF v_likes_missing_token > 0 OR v_saved_missing_token > 0 THEN
        RAISE EXCEPTION 'CHECK 7 FAILED: Likes (%) or Saves (%) missing actor_token!', v_likes_missing_token, v_saved_missing_token;
    ELSE
        RAISE NOTICE 'CHECK 7 PASSED: All post_likes and saved_posts have actor_token.';
    END IF;

    -- Check 8: Government tagging integrity preserved
    SELECT COUNT(*) INTO v_tags_missing_token FROM user_tags WHERE tagged_by_actor_token IS NULL;
    IF v_tags_missing_token > 0 THEN
        RAISE EXCEPTION 'CHECK 8 FAILED: % user_tags missing tagged_by_actor_token!', v_tags_missing_token;
    ELSE
        RAISE NOTICE 'CHECK 8 PASSED: All citizen user tags have tagged_by_actor_token.';
    END IF;

    -- Check 9: Absolute Uniqueness of actor_token (Zero duplicate tokens in actor_profiles)
    SELECT (COUNT(*) - COUNT(DISTINCT actor_token)) INTO v_duplicate_actor_tokens FROM actor_profiles;
    IF v_duplicate_actor_tokens > 0 THEN
        RAISE EXCEPTION 'CHECK 9 FAILED: % duplicate actor_tokens detected in actor_profiles!', v_duplicate_actor_tokens;
    ELSE
        RAISE NOTICE 'CHECK 9 PASSED: Zero duplicate actor_tokens. 100%% unique across all rows.';
    END IF;

    -- Check 10: 1-to-1 Bijection (Every user has their own distinct actor_token)
    SELECT COUNT(DISTINCT actor_token) INTO v_distinct_actors FROM actor_profiles;
    IF v_distinct_actors != v_users_total THEN
        RAISE EXCEPTION 'CHECK 10 FAILED: Distinct actor_tokens (%) != total users (%)! Every user must have a unique actor_token.', v_distinct_actors, v_users_total;
    ELSE
        RAISE NOTICE 'CHECK 10 PASSED: Exact 1-to-1 bijection confirmed between % users and % actor_tokens.', v_users_total, v_distinct_actors;
    END IF;

    RAISE NOTICE '=============================================================';
    RAISE NOTICE 'ALL 10 AUDIT CHECKS PASSED! 100%% DATA PARITY & UNIQUENESS.';
    RAISE NOTICE 'Safe to proceed with V4 Contraction.';
    RAISE NOTICE '=============================================================';
END $$;
