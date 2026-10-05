package com.Govlyx.AI.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;


@Data
public class CreatePollRequest {

    @NotBlank(message = "Poll question is required")
    @Size(max = 500, message = "Poll question cannot exceed 500 characters")
    private String question;

    @Size(min = 2, max = 4, message = "A poll must have between 2 and 4 options")
    private List<@NotBlank String> options;

    /** "1h", "1d", "3d", "7d", "always", "never" — converted to Date inside PollService */
    private String expiresIn = "1d";

    private Boolean allowMultipleVotes = false;

    private Boolean showResultsBeforeExpiry = true;

    private String category;

    private Long communityId;
}
