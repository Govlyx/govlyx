package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Response DTO for post view operations
 * Contains view details and updated metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostViewResponse {
    private Long viewId;
    private Long postId;
    private Long userId;
    private String username;
    private Date viewedAt;
    private Integer viewDuration;
    private Integer newViewCount;
    private Boolean isLongView;
    private Boolean isQuickView;
}
