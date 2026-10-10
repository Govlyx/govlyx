package com.JanSahayak.AI.model;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocialPostTest {

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setIsActive(true);
    }

    @Test
    void testIsEligibleForRecommendation_NullQualityScore_DefaultsTo100() {
        // Arrange
        SocialPost post = new SocialPost();
        post.setUser(activeUser);
        post.setStatus(PostStatus.ACTIVE);
        post.setIsFlagged(false);
        
        // Explicitly set to null to test the fallback logic
        try {
            java.lang.reflect.Field qualityScoreField = SocialPost.class.getDeclaredField("qualityScore");
            qualityScoreField.setAccessible(true);
            qualityScoreField.set(post, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Act & Assert
        // Should use 100.0 as fallback, which is >= Constant.HLIG_MIN_QUALITY (40.0)
        assertTrue(post.isEligibleForRecommendation(), "Post with null qualityScore should fall back to 100.0 and be eligible");
    }

    @Test
    void testIsEligibleForRecommendation_QualityScoreAtThreshold() {
        // Arrange
        SocialPost post = new SocialPost();
        post.setUser(activeUser);
        post.setStatus(PostStatus.ACTIVE);
        post.setIsFlagged(false);
        
        try {
            java.lang.reflect.Field qualityScoreField = SocialPost.class.getDeclaredField("qualityScore");
            qualityScoreField.setAccessible(true);
            qualityScoreField.set(post, Constant.HLIG_MIN_QUALITY); // Exactly 40.0
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Act & Assert
        assertTrue(post.isEligibleForRecommendation(), "Post exactly at threshold should be eligible");
    }

    @Test
    void testIsEligibleForRecommendation_QualityScoreBelowThreshold() {
        // Arrange
        SocialPost post = new SocialPost();
        post.setUser(activeUser);
        post.setStatus(PostStatus.ACTIVE);
        post.setIsFlagged(false);
        
        try {
            java.lang.reflect.Field qualityScoreField = SocialPost.class.getDeclaredField("qualityScore");
            qualityScoreField.setAccessible(true);
            qualityScoreField.set(post, Constant.HLIG_MIN_QUALITY - 1.0); // 39.0
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Act & Assert
        assertFalse(post.isEligibleForRecommendation(), "Post below threshold should not be eligible");
    }
}
