package com.Govlyx.AI.service;

import com.Govlyx.AI.dto.CommentReactionSummaryDto;
import com.Govlyx.AI.dto.ReactCommentRequest;
import com.Govlyx.AI.model.Comment;
import com.Govlyx.AI.model.CommentInteraction;
import com.Govlyx.AI.model.InteractionType;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.CommentInteractionRepository;
import com.Govlyx.AI.repository.CommentRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentInteractionService {

    private final CommentInteractionRepository interactionRepo;
    private final CommentRepo commentRepo;

    @Transactional
    public CommentReactionSummaryDto toggleVote(Long commentId, ReactCommentRequest request, User currentUser) {
        // Use pessimistic write lock to prevent race conditions during high-frequency voting
        Comment comment = commentRepo.findByIdForUpdate(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found: " + commentId));

        InteractionType targetInteraction = InteractionType.valueOf(request.getReaction().toUpperCase());
        Optional<CommentInteraction> existing = interactionRepo.findByCommentIdAndUserId(commentId, currentUser.getId());

        if (existing.isPresent()) {
            CommentInteraction interaction = existing.get();
            if (interaction.getInteractionType() == targetInteraction) {
                // Same action clicked -> Toggle OFF
                interactionRepo.delete(interaction);
                decrementCounter(comment, targetInteraction);
            } else {
                // Different action clicked -> Switch vote
                decrementCounter(comment, interaction.getInteractionType());
                interaction.setInteractionType(targetInteraction);
                interactionRepo.save(interaction);
                incrementCounter(comment, targetInteraction);
            }
        } else {
            // First time voting
            CommentInteraction newInteraction = CommentInteraction.builder()
                    .comment(comment)
                    .user(currentUser)
                    .interactionType(targetInteraction)
                    .build();
            interactionRepo.save(newInteraction);
            incrementCounter(comment, targetInteraction);
        }

        // Calculate and update Wilson Score
        comment.setRankingScore(calculateWilsonScore(comment.getLikeCount(), comment.getDislikeCount()));
        commentRepo.save(comment);

        return getReactionSummary(commentId, currentUser.getId());
    }

    private void incrementCounter(Comment comment, InteractionType type) {
        if (type == InteractionType.LIKE) {
            comment.setLikeCount(comment.getLikeCount() + 1);
        } else if (type == InteractionType.DISLIKE) {
            comment.setDislikeCount(comment.getDislikeCount() + 1);
        }
    }

    private void decrementCounter(Comment comment, InteractionType type) {
        if (type == InteractionType.LIKE) {
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
        } else if (type == InteractionType.DISLIKE) {
            comment.setDislikeCount(Math.max(0, comment.getDislikeCount() - 1));
        }
    }

    /**
     * Wilson Score Confidence Interval for a Bernoulli parameter.
     * Used for ranking comments intelligently.
     */
    private double calculateWilsonScore(int positive, int negative) {
        int total = positive + negative;
        if (total == 0) return 0.0;
        
        // 1.96 corresponds to a 95% confidence interval
        double z = 1.96;
        double z_squared = z * z;
        double p = (double) positive / total;

        double left = p + (z_squared / (2 * total));
        double right = z * Math.sqrt((p * (1 - p) / total) + (z_squared / (4 * total * total)));
        double under = 1 + (z_squared / total);

        return (left - right) / under;
    }

    @Transactional(readOnly = true)
    public CommentReactionSummaryDto getReactionSummary(Long commentId, Long currentUserId) {
        List<Object[]> countsData = interactionRepo.countInteractionsGroupedByCommentId(commentId);
        Map<String, Long> counts = new HashMap<>();

        for (Object[] row : countsData) {
            InteractionType type = (InteractionType) row[0];
            Long count = (Long) row[1];
            counts.put(type.name(), count);
        }

        String userReaction = null;
        if (currentUserId != null) {
            userReaction = interactionRepo.findByCommentIdAndUserId(commentId, currentUserId)
                    .map(i -> i.getInteractionType().name())
                    .orElse(null);
        }

        return CommentReactionSummaryDto.builder()
                .counts(counts)
                .userReaction(userReaction)
                .build();
    }
}
