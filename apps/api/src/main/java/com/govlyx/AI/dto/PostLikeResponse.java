// File: src/main/java/com/Govlyx/AI/DTO/PostLikeResponse.java
package com.govlyx.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for post like operations
 * Contains like status and updated metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostLikeResponse {
    private Long postId;
    private Long userId;
    private String username;
    private Boolean isLiked;
    private Integer newLikeCount;
    private String action; // "LIKED" or "UNLIKED"
}
