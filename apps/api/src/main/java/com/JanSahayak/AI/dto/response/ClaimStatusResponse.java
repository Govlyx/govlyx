package com.JanSahayak.AI.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.Date;

@Data
@Builder
public class ClaimStatusResponse {
    private String referenceId;
    private String status;
    private Date submittedAt;
    private Date updatedOn;
}
