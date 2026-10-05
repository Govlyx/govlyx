package com.JanSahayak.AI.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for recording view duration
 * Duration should be in seconds
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewDurationRequest {

    @Min(value = 1, message = "Duration must be at least 1 second")
    @Max(value = 3600, message = "Duration cannot exceed 1 hour")
    private Integer duration; // Duration in seconds
}
