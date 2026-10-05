package com.govlyx.AI.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test") // Assuming there's a test profile, or default
public class RepositoryValidationTest {

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private SocialPostRepo socialPostRepo;

    @Test
    void contextLoadsAndQueriesAreValid() {
        assertNotNull(postRepo);
        assertNotNull(socialPostRepo);
    }
}
