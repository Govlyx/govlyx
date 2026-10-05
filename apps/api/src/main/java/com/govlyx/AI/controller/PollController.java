package com.govlyx.AI.controller;

import com.govlyx.AI.model.User;

import com.govlyx.AI.payload.request.CreatePollRequest;
import com.govlyx.AI.payload.request.PollResponse;
import com.govlyx.AI.service.PollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import com.govlyx.AI.payload.request.CreatePollRequest;  // specific
import com.govlyx.AI.payload.request.PollResponse;      // correct package
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    /**
     * CREATE POLL
     * Frontend sends ONE request here.
     * Internally: SocialPost is auto-created → Poll is attached to it.
     *
     * POST /api/polls/create
     * Body: CreatePollRequest
     */
    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<PollResponse> createPoll(
            @Valid @RequestBody CreatePollRequest request,
            @AuthenticationPrincipal User currentUser) {

        PollResponse response = pollService.createPollPost(request, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * CREATE POLL WITH MULTIMEDIA
     * POST /api/polls/create-with-media
     * Consumes: multipart/form-data
     */
    @PostMapping(value = "/create-with-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<PollResponse> createPollWithMedia(
            @RequestPart("poll") @Valid CreatePollRequest request,
            @RequestPart(value = "media", required = false) List<MultipartFile> mediaFiles,
            @AuthenticationPrincipal User currentUser) {
        PollResponse response = pollService.createPollPostWithMedia(request, mediaFiles, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * GET POLL by poll ID
     * GET /api/polls/{pollId}
     */
    @GetMapping("/{pollId}")
    public ResponseEntity<PollResponse> getPoll(
            @PathVariable Long pollId,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(pollService.getPollResponse(pollId, currentUser));
    }

    /**
     * GET POLL by SocialPost ID  (used when rendering a post in the feed)
     * GET /api/polls/by-post/{socialPostId}
     */
    @GetMapping("/by-post/{socialPostId}")
    public ResponseEntity<PollResponse> getPollBySocialPost(
            @PathVariable Long socialPostId,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(pollService.getPollBySocialPostId(socialPostId, currentUser));
    }

    /**
     * VOTE
     * Body: list of option IDs e.g. [42], [42, 45], or {"optionIds": [42]}
     * POST /api/polls/{pollId}/vote
     */
    @PostMapping("/{pollId}/vote")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<PollResponse> vote(
            @PathVariable Long pollId,
            @RequestBody com.fasterxml.jackson.databind.JsonNode body,
            @AuthenticationPrincipal User currentUser) {

        List<Long> optionIds = new java.util.ArrayList<>();
        if (body.isArray()) {
            body.forEach(node -> optionIds.add(node.asLong()));
        } else if (body.isObject() && body.has("optionIds") && body.get("optionIds").isArray()) {
            body.get("optionIds").forEach(node -> optionIds.add(node.asLong()));
        }
        return ResponseEntity.ok(pollService.vote(pollId, optionIds, currentUser));
    }

    /**
     * CLOSE POLL (creator or admin only)
     * PATCH /api/polls/{pollId}/close
     */
    @PatchMapping("/{pollId}/close")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<Void> closePoll(
            @PathVariable Long pollId,
            @AuthenticationPrincipal User currentUser) {

        pollService.closePoll(pollId, currentUser);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE POLL (creator or admin only)
     * DELETE /api/polls/{pollId}
     */
    @DeleteMapping("/{pollId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<Void> deletePoll(
            @PathVariable Long pollId,
            @AuthenticationPrincipal User currentUser) {

        pollService.deletePoll(pollId, currentUser);
        return ResponseEntity.ok().build();
    }
}
