package com.JanSahayak.AI.payload.request;

import com.JanSahayak.AI.model.Poll;
import lombok.Builder;
import lombok.Data;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class PollResponse {

    private Long   pollId;
    private Long   socialPostId;   // so frontend knows which post this belongs to
    private String question;
    private boolean expired;
    private boolean openForVoting;
    private boolean allowMultipleVotes;
    private Date   expiresAt;
    private int    totalVotes;
    private boolean userHasVoted;
    private List<Long> userVotedOptionIds;
    private List<PollOptionResponse> options;

    @Data
    @Builder
    public static class PollOptionResponse {
        private Long   optionId;
        private String optionText;
        private int    optionOrder;
        private Integer voteCount;       // null when results are hidden
        private Double  votePercentage;  // null when results are hidden
    }

    public static PollResponse from(Poll poll, boolean userHasVoted,
                                    boolean showResults, List<Long> votedOptionIds) {

        List<PollOptionResponse> opts = poll.getOptions().stream()
                .map(o -> PollOptionResponse.builder()
                        .optionId(o.getId())
                        .optionText(o.getOptionText())
                        .optionOrder(o.getOptionOrder())
                        .voteCount(showResults ? o.getVoteCount() : null)
                        .votePercentage(showResults ? o.getVotePercentage(poll.getTotalVotes()) : null)
                        .build())
                .sorted((a, b) -> Integer.compare(a.getOptionOrder(), b.getOptionOrder()))
                .collect(Collectors.toList());

        return PollResponse.builder()
                .pollId(poll.getId())
                .socialPostId(poll.getSocialPost() != null ? poll.getSocialPost().getId() : null)
                .question(poll.getQuestion())
                .expired(poll.isExpired())
                .openForVoting(poll.isOpenForVoting())
                .allowMultipleVotes(Boolean.TRUE.equals(poll.getAllowMultipleVotes()))
                .expiresAt(poll.getExpiresAt())
                .totalVotes(showResults ? poll.getTotalVotes() : 0)
                .userHasVoted(userHasVoted)
                .userVotedOptionIds(votedOptionIds)
                .options(opts)
                .build();
    }
}
