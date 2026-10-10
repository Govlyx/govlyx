package com.JanSahayak.AI.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class CommentReactionSummaryDto {
    private Map<String, Long> counts;
    private String userReaction;
}
