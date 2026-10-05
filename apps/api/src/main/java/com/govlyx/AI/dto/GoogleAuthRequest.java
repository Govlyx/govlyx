package com.Govlyx.AI.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Unified request DTO for POST /api/auth/google.
 *
 * Phase 1 (check if new or existing user):
 *   { "token": "google_id_token" }
 *
 * Phase 2 (create new account atomically):
 *   { "token": "google_id_token", "pincode": "110001", "isAdult": true, "acceptedPolicy": true }
 *
 * isRegistrationRequest() returns true only when all Phase 2 fields are populated and valid.
 */
@Data
public class GoogleAuthRequest {

    @NotBlank(message = "Google token is required")
    private String token;

    // Phase 2 fields — null in Phase 1, populated in Phase 2
    private String pincode;
    private Boolean isAdult;
    private Boolean acceptedPolicy;

    /**
     * Returns true if this is a Phase 2 (account creation) request.
     * All three consent fields must be present and affirmative.
     */
    public boolean isRegistrationRequest() {
        return pincode != null
                && !pincode.isBlank()
                && Boolean.TRUE.equals(isAdult)
                && Boolean.TRUE.equals(acceptedPolicy);
    }
}
