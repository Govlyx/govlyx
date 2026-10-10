package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {
    private Long id;
    private String text;
    private Date createdAt;

    // Post relationship
    private Long postId;

    // User relationship
    private Long userId;
    private String username;

    // Parent comment relationship (for replies)
    private Long parentCommentId;

    // Reply count (derived from replies list)
    private int replyCount;

    // Nested replies for tree structure (optional)
    private List<CommentResponse> replies;
}
