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
public class SavedPostDto {
    private Long id;
    private Long userId;
    private Long socialPostId;
    private Long postId;
    private Date savedAt;
    
    // Preview fields for Activity Tab
    private String content; 
    private String type; // "social" or "issue"
    
    // Existing entities passed if needed, but usually we just want the ID and text
    private SocialPostDto socialPost;
    private PostResponse post;
}
