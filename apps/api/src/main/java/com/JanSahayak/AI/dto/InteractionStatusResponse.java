package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user interaction status with a post
 * Contains current interaction state and post metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionStatusResponse {
    private Long postId;
    private Long userId;
    private String username;
    private Boolean isLiked;
    private Boolean hasViewed;
    private Integer currentLikeCount;
    private Integer currentViewCount;
    private Integer currentCommentCount;
    private Boolean canInteract;
}
