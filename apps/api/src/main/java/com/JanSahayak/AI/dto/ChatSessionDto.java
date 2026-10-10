package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionDto {
    private String sessionId;
    private String yourAnonymousId;
    private String partnerAnonymousId;
    private String status;
    private Instant createdAt;
    private Instant lastActivityAt;
}
