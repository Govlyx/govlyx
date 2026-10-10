package com.JanSahayak.AI.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentUpdateDto {
    @NotBlank(message = "Comment text is required")
    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String text;   // The updated comment text
}

