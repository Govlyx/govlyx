package com.JanSahayak.AI.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class CommunityRepoQueryTest {

    @Autowired
    private CommunityMemberRepo communityMemberRepo;

    @Autowired
    private CommunityJoinRequestRepo communityJoinRequestRepo;

    @Test
    void communityMemberRepo_CursorQueries_ShouldCompileAndExecuteWithoutSyntaxErrors() {
        assertDoesNotThrow(() -> {
            communityMemberRepo.findActiveMembersCursor(1L, 10L, PageRequest.of(0, 10));
        });

        assertDoesNotThrow(() -> {
            communityMemberRepo.findUserCommunitiesCursor(1L, 10L, PageRequest.of(0, 10));
        });

        assertDoesNotThrow(() -> {
            communityMemberRepo.findElevatedRolesByUserIds(1L, List.of(1L, 2L));
        });
    }

    @Test
    void communityJoinRequestRepo_CursorQueries_ShouldCompileAndExecuteWithoutSyntaxErrors() {
        assertDoesNotThrow(() -> {
            communityJoinRequestRepo.findPendingRequestsCursor(1L, 10L, PageRequest.of(0, 10));
        });
    }
}
