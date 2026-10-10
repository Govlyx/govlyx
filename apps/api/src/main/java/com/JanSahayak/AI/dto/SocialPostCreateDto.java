package com.JanSahayak.AI.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPostCreateDto {

    @NotBlank(message = "Social post content is required")
    @Size(max = 3000, message = "Content must not exceed 3000 characters")
    private String content;

    // Optional: Media URLs (comma-separated or list)
    private List<String> mediaUrls;

    // Optional: Hashtags
    private List<String> hashtags;

    // Optional: Mentioned user IDs
    private List<Long> mentionedUserIds;

    // Optional: Location settings
    private Boolean showLocation;

    // Optional: Comment settings
    private Boolean allowComments;

    // Optional: The category of the post (defaults to GENERAL)
    private com.JanSahayak.AI.enums.SocialPostCategory category;

    // Optional: Community this post belongs to (null = regular social post)
    private Long communityId;

    // Optional: Target Pincode for Ask Anywhere functionality
    private String targetPincode;
}
