package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSummaryDto {
    private long all;
    private long unread;
    private long invites;
    private long interactions;

    /**
     * Constructor for JPQL aggregate projection.
     * Handles nullable Long values from SUM(...) expressions safely.
     */
    public NotificationSummaryDto(long all, Long unread, Long invites, Long interactions) {
        this.all = all;
        this.unread = unread != null ? unread : 0L;
        this.invites = invites != null ? invites : 0L;
        this.interactions = interactions != null ? interactions : 0L;
    }
}
