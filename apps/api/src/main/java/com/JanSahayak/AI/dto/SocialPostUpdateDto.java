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
public class SocialPostUpdateDto {

    @NotBlank(message = "Social post content is required")
    @Size(max = 3000, message = "Content must not exceed 3000 characters")
    private String content;

    // Optional: Update hashtags
    private List<String> hashtags;

    // Optional: Update comment settings
    private Boolean allowComments;
}
