package com.JanSahayak.AI.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PostContentUpdateDto {
    @NotBlank(message = "Post content is required")
    @Size(max = 2000, message = "Content must not exceed 2000 characters") // Fixed: was 500, now matches service validation
    private String content;
}

