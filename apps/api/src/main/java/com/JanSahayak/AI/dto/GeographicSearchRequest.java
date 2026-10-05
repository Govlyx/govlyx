package com.JanSahayak.AI.dto;

import com.JanSahayak.AI.payload.PaginationUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeographicSearchRequest {
    private String pincode;
    private String state;
    private String district;
    private Long beforeId;
    private Integer limit;

    // Validation and helper methods
    public Integer getValidatedLimit() {
        String searchType = determineSearchType();
        return PaginationUtils.validateGeographicSearchLimit(limit, searchType);
    }

    public Long getSanitizedCursor() {
        return PaginationUtils.sanitizeCursor(beforeId);
    }

    public String determineSearchType() {
        if (pincode != null && !pincode.trim().isEmpty()) {
            return "pincode";
        } else if (state != null && district != null &&
                !state.trim().isEmpty() && !district.trim().isEmpty()) {
            return "district";
        } else if (state != null && !state.trim().isEmpty()) {
            return "state";
        }
        return "unknown";
    }

    public boolean isValid() {
        return !determineSearchType().equals("unknown");
    }

    public String getSearchValue() {
        switch (determineSearchType()) {
            case "pincode":
                return pincode.trim();
            case "district":
                return district.trim() + ", " + state.trim();
            case "state":
                return state.trim();
            default:
                return "";
        }
    }
}
