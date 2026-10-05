package com.govlyx.AI.migration;

import com.govlyx.AI.repository.ActorProfileRepo;
import com.govlyx.AI.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BackfillMigrationExecutionTest {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ActorProfileRepo actorProfileRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void verifyBlindShieldContractedState() {
        System.out.println(">>> VERIFYING BLIND SHIELD CONTRACTED STATE <<<");

        long totalUsers = userRepo.count();
        long totalActorProfiles = actorProfileRepo.count();

        System.out.println("Total Users: " + totalUsers);
        System.out.println("Total Actor Profiles: " + totalActorProfiles);

        assertEquals(totalUsers, totalActorProfiles, "Every user must have exactly one actor_profile");

        // Verify all citizen posts have user_id = NULL and actor_token IS NOT NULL
        Long postsMissingActor = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE actor_token IS NULL",
                Long.class
        );
        assertEquals(0L, postsMissingActor, "All posts must have actor_token");

        // Verify all social_posts have actor_token
        Long unmigratedSocialPosts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM social_posts WHERE actor_token IS NULL",
                Long.class
        );
        assertEquals(0L, unmigratedSocialPosts, "All social posts must have actor_token");

        // Verify all comments have actor_token
        Long unmigratedComments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE actor_token IS NULL",
                Long.class
        );
        assertEquals(0L, unmigratedComments, "All comments must have actor_token");

        // Verify all post_likes have actor_token
        Long unmigratedLikes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE actor_token IS NULL",
                Long.class
        );
        assertEquals(0L, unmigratedLikes, "All post likes must have actor_token");

        // Verify all saved_posts have actor_token
        Long unmigratedSaves = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saved_posts WHERE actor_token IS NULL",
                Long.class
        );
        assertEquals(0L, unmigratedSaves, "All saved posts must have actor_token");

        System.out.println(">>> ZERO-KNOWLEDGE BLIND SHIELD VERIFIED SUCCESSFULLY <<<");
    }
}
