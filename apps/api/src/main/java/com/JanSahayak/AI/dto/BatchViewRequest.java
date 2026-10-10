package com.JanSahayak.AI.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for batch view operations
 * Allows recording multiple post views in single request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchViewRequest {

    @NotEmpty(message = "Post IDs list cannot be empty")
    @Size(max = 50, message = "Cannot process more than 50 posts at once")
    private List<Long> postIds;

    // Optional durations by post ID (in seconds)
    private Map<Long, Integer> durations;
}
