package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user interaction status with a social post
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPostInteractionStatusDto {

    private Long socialPostId;
    private Long userId;
    private String username;

    // Interaction Status
    private Boolean isLiked;
    private Boolean isSaved;
    private Boolean hasViewed;

    // Current Metrics
    private Integer currentLikeCount;
    private Integer currentCommentCount;
    private Integer currentShareCount;
    private Integer currentSaveCount;
    private Integer currentViewCount;

    // Permissions
    private Boolean canLike;
    private Boolean canComment;
    private Boolean canShare;
    private Boolean canSave;
}
