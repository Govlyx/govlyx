package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostInteractionDto {
    private Long id;
    private Long userId;
    private String interactionType; // "LIKE", "COMMENT", "SAVE"
    private String postType;        // "SOCIAL", "ISSUE"
    private Date createdAt;
    
    // Preview content
    private String content;
    
    // Actual post data
    private SocialPostDto socialPost;
    private PostResponse post;
}
