package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.enums.PostStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import com.JanSahayak.AI.model.Post;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class RepositoryEntityGraphTest {

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private SocialPostRepo socialPostRepo;

    @Test
    void postRepo_EntityGraphQueries_ShouldCompileAndExecuteWithoutSyntaxErrors() {
        // Objective: Verify that the HQL queries with @EntityGraph in PostRepo are valid
        // and do not throw syntax exceptions when parsed by Hibernate.

        assertDoesNotThrow(() -> {
            postRepo.findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(
                    BroadcastScope.DISTRICT, PostStatus.ACTIVE, "PUNE", PageRequest.of(0, 10));
        });

        assertDoesNotThrow(() -> {
            List<Post> posts = postRepo.findByBroadcastScopeAndStatusAndTargetDistrictsContainingAndIdLessThanOrderByCreatedAtDesc(
                    BroadcastScope.DISTRICT, PostStatus.ACTIVE, "PUNE", 999999L, PageRequest.of(0, 10));
        });
    }

    @Test
    void socialPostRepo_EntityGraphQueries_ShouldCompileAndExecuteWithoutSyntaxErrors() {
        // Objective: Verify that the global @EntityGraph injection across SocialPostRepo
        // did not break query compilation.

        assertDoesNotThrow(() -> {
            socialPostRepo.findRecommendedPostsForUser("MH", "PUNE", PageRequest.of(0, 10));
        });

        assertDoesNotThrow(() -> {
            socialPostRepo.findRecommendedPostsForUserWithCursor("MH", "PUNE", 999999L, PageRequest.of(0, 10));
        });
    }
}
