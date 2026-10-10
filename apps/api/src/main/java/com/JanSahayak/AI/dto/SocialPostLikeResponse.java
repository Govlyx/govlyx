package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPostLikeResponse {

    private Long socialPostId;
    private Long userId;
    private String username;

    private Boolean isLiked;
    private Integer newLikeCount;
    private String action; // "LIKED" or "UNLIKED"

    // Additional metrics
    private Integer totalEngagementCount;
    private Double newEngagementScore;
}
