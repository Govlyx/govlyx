package com.JanSahayak.AI.dto;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.payload.PaginationUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchRequest {
    private String query;
    private String role;
    private Boolean activeOnly;
    private Long beforeId;
    private Integer limit;

    // Validation methods
    public String getCleanQuery() {
        if (query == null) return null;
        String cleaned = query.startsWith("@") ? query.substring(1) : query;
        return cleaned.trim();
    }

    public Boolean getActiveOnly() {
        return activeOnly != null ? activeOnly : true;
    }

    public Integer getValidatedLimit() {
        if (query != null && !query.trim().isEmpty()) {
            // For search queries, use tagging limits
            return PaginationUtils.validateTaggingLimit(limit);
        } else {
            // For general listing, use user search limits
            return PaginationUtils.validateUserSearchLimit(limit);
        }
    }

    public Long getSanitizedCursor() {
        return PaginationUtils.sanitizeCursor(beforeId);
    }

    public boolean isValidSearchQuery() {
        String cleaned = getCleanQuery();
        return cleaned != null && cleaned.length() >= 2;
    }
}
