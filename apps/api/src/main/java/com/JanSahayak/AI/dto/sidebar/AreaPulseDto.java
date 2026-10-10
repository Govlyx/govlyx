package com.JanSahayak.AI.dto.sidebar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AreaPulseDto {
    private long totalIssuesThisWeek;
    private long unansweredQuestions;
    private long resolvedIssuesThisWeek;
}
