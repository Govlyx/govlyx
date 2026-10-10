package com.JanSahayak.AI.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveRequest {

    @NotBlank(message = "Resolution action is required")
    private String action; // "RESOLVED_REMOVED" or "RESOLVED_DISMISSED"

    private String resolutionNotes;
}
