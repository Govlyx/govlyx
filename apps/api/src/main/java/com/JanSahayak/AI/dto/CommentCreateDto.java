package com.JanSahayak.AI.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreateDto {

    @NotBlank(message = "Comment text is required")
    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String text;

    private Long postId; // For issue posts
    private Long socialPostId; // For social posts
    private Long parentCommentId;

    // Helper methods
    public boolean isForPost() {
        return postId != null;
    }

    public boolean isForSocialPost() {
        return socialPostId != null;
    }

    public boolean isReply() {
        return parentCommentId != null;
    }
}
