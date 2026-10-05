package com.govlyx.AI.service;

import com.govlyx.AI.dto.CommentReactionSummaryDto;
import com.govlyx.AI.dto.ReactCommentRequest;
import com.govlyx.AI.model.Comment;
import com.govlyx.AI.model.CommentInteraction;
import com.govlyx.AI.model.InteractionType;
import com.govlyx.AI.model.User;
import com.govlyx.AI.repository.CommentInteractionRepository;
import com.govlyx.AI.repository.CommentRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommentInteractionServiceTest {

    @Mock
    private CommentInteractionRepository interactionRepo;

    @Mock
    private CommentRepo commentRepo;

    @InjectMocks
    private CommentInteractionService interactionService;

    private Comment comment;
    private User user;
    private ReactCommentRequest request;

    @BeforeEach
    void setUp() {
        comment = new Comment();
        comment.setId(1L);
        comment.setLikeCount(0);
        comment.setDislikeCount(0);

        user = new User();
        user.setId(10L);

        request = new ReactCommentRequest();
        request.setReaction("LIKE");
    }

    @Test
    void toggleVote_firstTimeLike_increasesLikeCountAndCalculatesWilsonScore() {
        when(commentRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(comment));
        when(interactionRepo.findByCommentIdAndUserId(1L, 10L)).thenReturn(Optional.empty());

        CommentReactionSummaryDto summary = interactionService.toggleVote(1L, request, user);

        assertEquals(1, comment.getLikeCount());
        assertEquals(0, comment.getDislikeCount());
        assertTrue(comment.getRankingScore() > 0.0);
        
        verify(interactionRepo, times(1)).save(any(CommentInteraction.class));
        verify(commentRepo, times(1)).save(comment);
    }

    @Test
    void toggleVote_sameVote_togglesOff() {
        comment.setLikeCount(1);
        
        CommentInteraction existingInteraction = new CommentInteraction();
        existingInteraction.setInteractionType(InteractionType.LIKE);
        existingInteraction.setUser(user);
        existingInteraction.setComment(comment);

        when(commentRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(comment));
        when(interactionRepo.findByCommentIdAndUserId(1L, 10L)).thenReturn(Optional.of(existingInteraction));

        interactionService.toggleVote(1L, request, user);

        assertEquals(0, comment.getLikeCount());
        verify(interactionRepo, times(1)).delete(existingInteraction);
    }

    @Test
    void toggleVote_differentVote_switchesVote() {
        comment.setLikeCount(1);
        comment.setDislikeCount(0);
        
        CommentInteraction existingInteraction = new CommentInteraction();
        existingInteraction.setInteractionType(InteractionType.LIKE);
        existingInteraction.setUser(user);
        existingInteraction.setComment(comment);

        request.setReaction("DISLIKE");

        when(commentRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(comment));
        when(interactionRepo.findByCommentIdAndUserId(1L, 10L)).thenReturn(Optional.of(existingInteraction));

        interactionService.toggleVote(1L, request, user);

        assertEquals(0, comment.getLikeCount());
        assertEquals(1, comment.getDislikeCount());
        assertEquals(InteractionType.DISLIKE, existingInteraction.getInteractionType());
        verify(interactionRepo, times(1)).save(existingInteraction);
    }
}
