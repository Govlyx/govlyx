package com.govlyx.AI.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReactCommentRequest {
    @NotBlank(message = "Reaction code or emoji is required")
    private String reaction;
}
