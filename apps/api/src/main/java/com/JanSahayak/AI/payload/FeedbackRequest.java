package com.JanSahayak.AI.payload;

import com.JanSahayak.AI.enums.FeedbackCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @NotNull(message = "Category is required")
    private FeedbackCategory category;

    private String message;
    private String appVersion;
    private String deviceInfo;
}
