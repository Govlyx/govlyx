package com.govlyx.AI.controller;

import com.govlyx.AI.exception.ApiResponse;
import com.govlyx.AI.dto.CommentReactionSummaryDto;
import com.govlyx.AI.dto.ReactCommentRequest;
import com.govlyx.AI.model.User;
import com.govlyx.AI.security.CurrentUser;
import com.govlyx.AI.service.CommentInteractionService;
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
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CommentReactionSummaryDto>> interactWithComment(
            @PathVariable Long commentId,
            @Valid @RequestBody ReactCommentRequest request,
            @CurrentUser User currentUser) {
        
        if (currentUser == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required to rate comments"));
        }

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
