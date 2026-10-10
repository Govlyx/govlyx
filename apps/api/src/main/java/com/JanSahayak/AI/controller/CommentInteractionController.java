package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.dto.CommentReactionSummaryDto;
import com.JanSahayak.AI.dto.ReactCommentRequest;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.CommentInteractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentInteractionController {

    private final CommentInteractionService interactionService;

    @PostMapping("/{commentId}/interactions")
    public ResponseEntity<ApiResponse<CommentReactionSummaryDto>> interactWithComment(
            @PathVariable Long commentId,
            @Valid @RequestBody ReactCommentRequest request,
            @CurrentUser User currentUser) {
        
        CommentReactionSummaryDto summary = interactionService.toggleVote(commentId, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Interaction updated", summary));
    }

    @GetMapping("/{commentId}/interactions")
    public ResponseEntity<ApiResponse<CommentReactionSummaryDto>> getInteractions(
            @PathVariable Long commentId,
            @CurrentUser(required = false) User currentUser) {
        
        Long userId = currentUser != null ? currentUser.getId() : null;
        CommentReactionSummaryDto summary = interactionService.getReactionSummary(commentId, userId);
        return ResponseEntity.ok(ApiResponse.success("Interactions retrieved", summary));
    }
}
