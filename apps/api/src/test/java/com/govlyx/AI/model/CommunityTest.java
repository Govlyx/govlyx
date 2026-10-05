package com.Govlyx.AI.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommunityTest {

    @Test
    public void testDefaultHealthScoreIsZero() {
        Community community = new Community();
        assertEquals(0.0, community.getHealthScore(), "Default health score should be 0.0");
    }

    @Test
    public void testBuilderDefaultHealthScoreIsZero() {
        Community community = Community.builder()
                .name("Test Community")
                .slug("test-community")
                .build();
        assertEquals(0.0, community.getHealthScore(), "Builder default health score should be 0.0");
    }
}
