package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for batch view operations
 * Contains success/failure statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchViewResponse {
    private Integer totalRequested;
    private Integer successCount;
    private Integer skippedCount;
    private Integer failedCount;
}
