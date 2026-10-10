package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.CommentCreateDto;
import com.JanSahayak.AI.dto.CommentDto;
import com.JanSahayak.AI.dto.CommentUpdateDto;
import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.exception.CommentNotFoundException;
import com.JanSahayak.AI.exception.ResourceNotFoundException;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.repository.CommentRepo;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.CommentInteractionRepository;
import com.JanSahayak.AI.model.CommentInteraction;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Unified CommentService that handles comments for both regular Posts and SocialPosts
 * Eliminates code duplication between comment handling logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepo commentRepository;
    private final PostRepo postRepository;
    private final SocialPostRepo socialPostRepository;
    private final ContentValidationService contentValidationService;
    private final CommentInteractionRepository commentInteractionRepository;


    @Lazy
    @Autowired
    private PostService postService;

    private final NotificationService notificationService;

    // FIX #5: CommunityService was not injected — totalCommentCount always stayed 0.
    // @Lazy breaks the circular dependency: CommunityService → SocialPostRepo,
    // CommentService → CommunityService.
    @Lazy
    @Autowired
    private CommunityService communityService;

    @Lazy
    @Autowired
    private PostInteractionService postInteractionService;

    @Autowired(required = false)
    private org.springframework.messaging.simp.SimpMessagingTemplate simpMessagingTemplate;

    private void broadcastCommentStatsUpdate(Long postId, String postType, Long communityId, Long commentCount) {
        if (postId == null) return;
        try {
            if ("posts".equalsIgnoreCase(postType) || "post".equalsIgnoreCase(postType)) {
                if (postInteractionService != null) {
                    postInteractionService.evictPostCountsCache(postId);
                    postInteractionService.broadcastPostStatsUpdate(postId);
                }
            } else {
                if (postInteractionService != null) {
                    postInteractionService.evictSocialPostCountsCache(postId);
                    postInteractionService.broadcastSocialPostStatsUpdate(postId, communityId);
                }
            }
        } catch (Exception e) {
            log.warn("[CommentService] Failed to broadcast full stats update: {}", e.getMessage());
        }

        if (simpMessagingTemplate != null) {
            try {
                java.util.Map<String, Object> stats = new java.util.HashMap<>();
                stats.put("postId", postId);
                stats.put("type", "STATS_UPDATE");
                stats.put("commentCount", commentCount);
                
                simpMessagingTemplate.convertAndSend("/topic/feed.updates", stats);
                if (communityId != null) {
                    simpMessagingTemplate.convertAndSend("/topic/community." + communityId + ".updates", stats);
                }
                simpMessagingTemplate.convertAndSend("/topic/post." + postId + ".updates", stats);
            } catch (Exception e) {
                log.warn("[CommentService] Basic WebSocket broadcast failed: {}", e.getMessage());
            }
        }
    }


    @Transactional(rollbackFor = Exception.class)
    public CommentDto createCommentOnPost(@Valid CommentCreateDto commentDto, @NotNull User user, @NotNull Post post) {
        try {
            validateCommentDto(commentDto);
            PostUtility.validateUser(user);
            PostUtility.validatePost(post);


            if (post.getStatus() == null || !post.getStatus().isInteractable()) {
                throw new ServiceException("Cannot add comments to posts with status: " + post.getStatus().getDisplayName());
            }

            String safeContent = contentValidationService.sanitizeAndValidateContent(commentDto.getText());
            commentDto.setText(safeContent);

            String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();
            if (idempotencyKey != null) {
                java.util.Optional<Comment> existingComment = commentRepository.findByIdempotencyKey(idempotencyKey);
                if (existingComment.isPresent()) {
                    log.info("Idempotency hit: Returning existing Comment for key {}", idempotencyKey);
                    return CommentDto.fromComment(existingComment.get());
                }
            }

            Comment comment = new Comment();
            comment.setIdempotencyKey(idempotencyKey);
            comment.setText(commentDto.getText().trim());
            comment.setUser(user);
            comment.setPost(post);
            comment.setCreatedAt(new Date());
            comment.setIpAddress(com.JanSahayak.AI.util.IpUtils.getClientIpFromContext());

            if (commentDto.getParentCommentId() != null) {
                Comment parentComment = findById(commentDto.getParentCommentId());
                validateParentCommentForPost(parentComment, post);
                comment.setParentComment(parentComment);
            }

            Comment savedComment = commentRepository.save(comment);
            post.incrementCommentCount();
            postRepository.incrementCommentCount(post.getId());
            postRepository.save(post);
            
            broadcastCommentStatsUpdate(post.getId(), "posts", null, (long) post.getCommentCount());

            // ── Comment notification ─────────────────────────────────────────
            try {
                notificationService.notifyPostCommented(post, savedComment, user);
                if (savedComment.getParentComment() != null) {
                    notificationService.notifyCommentReplied(savedComment.getParentComment(), savedComment, user);
                }
            } catch (Exception e) {
                log.warn("[Notification] Failed to notify post comment: post={}: {}", post.getId(), e.getMessage());
            }
            // ────────────────────────────────────────────────────────────────

            // ── Issue post promotion check ───────────────────────────────────
            // After a successful comment, check geographic promotion thresholds.
            // Swallows exceptions so it never rolls back the comment above.
            try {
                postService.checkAndPromoteIssuePost(post.getId());
            } catch (Exception e) {
                log.warn("[Promotion] Failed after comment on post={}: {}", post.getId(), e.getMessage());
            }
            // ────────────────────────────────────────────────────────────────

            log.info("Comment created by user: {} on post: {}", user.getActualUsername(), post.getId());
            return CommentDto.fromComment(savedComment);

        } catch (DataAccessException ex) {
            log.error("Database error while creating comment for user: {} on post: {}", user.getActualUsername(), post.getId(), ex);
            throw new ServiceException("Failed to create comment due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while creating comment for user: {} on post: {}", user.getActualUsername(), post.getId(), ex);
            throw new ServiceException("Failed to create comment", ex);
        }
    }

    // ===== CREATE COMMENT - SOCIAL POST =====

    /**
     * Create a comment on a social post.
     * SocialPosts have no resolved status so no resolved-post guard is needed here.
     */
    @Transactional(rollbackFor = Exception.class)
    public CommentDto createCommentOnSocialPost(@Valid CommentCreateDto commentDto, @NotNull User user, @NotNull SocialPost socialPost) {
        try {
            validateCommentDto(commentDto);
            PostUtility.validateUser(user);
            validateSocialPost(socialPost, user);

            if (!socialPost.getStatus().allowsComments()) {
                throw new ServiceException("Cannot add comments to social posts with status: " +
                        socialPost.getStatus().getDisplayName());
            }

            if (!socialPost.getAllowComments()) {
                throw new ServiceException("Comments are disabled for this social post");
            }

            String safeContent = contentValidationService.sanitizeAndValidateContent(commentDto.getText());
            commentDto.setText(safeContent);

            String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();
            if (idempotencyKey != null) {
                java.util.Optional<Comment> existingComment = commentRepository.findByIdempotencyKey(idempotencyKey);
                if (existingComment.isPresent()) {
                    log.info("Idempotency hit: Returning existing Comment for SocialPost for key {}", idempotencyKey);
                    return CommentDto.fromComment(existingComment.get());
                }
            }

            Comment comment = new Comment();
            comment.setIdempotencyKey(idempotencyKey);
            comment.setText(commentDto.getText().trim());
            comment.setUser(user);
            comment.setSocialPost(socialPost);
            comment.setCreatedAt(new Date());
            comment.setIpAddress(com.JanSahayak.AI.util.IpUtils.getClientIpFromContext());

            if (commentDto.getParentCommentId() != null) {
                Comment parentComment = findById(commentDto.getParentCommentId());
                SocialPost parentSocialPost = parentComment.getSocialPost();
                validateSocialPost(parentSocialPost, user);
                validateParentCommentForSocialPost(parentComment, socialPost);
                comment.setParentComment(parentComment);
            }

            Comment savedComment = commentRepository.save(comment);
            socialPost.incrementCommentCount();
            socialPostRepository.incrementCommentCount(socialPost.getId());
            socialPostRepository.save(socialPost);

            broadcastCommentStatsUpdate(socialPost.getId(), "social-posts", socialPost.getCommunityId(), (long) socialPost.getCommentCount());

            // ── Comment notification ─────────────────────────────────────────
            try {
                notificationService.notifySocialPostCommented(socialPost.getId(), savedComment.getId(), user.getId());
                if (savedComment.getParentComment() != null) {
                    notificationService.notifyCommentReplied(savedComment.getParentComment(), savedComment, user);
                }
            } catch (Exception e) {
                log.warn("[Notification] Failed to notify social post comment: post={}: {}", socialPost.getId(), e.getMessage());
            }
            // ────────────────────────────────────────────────────────────────

            // FIX #5: Wire onCommentAdded so community totalCommentCount is incremented.
            if (socialPost.getCommunityId() != null) {
                try {
                    communityService.onCommentAdded(socialPost.getCommunityId());
                } catch (Exception e) {
                    log.warn("[Community] onCommentAdded failed for post={} community={}: {}",
                            socialPost.getId(), socialPost.getCommunityId(), e.getMessage());
                }
            }

            log.info("Comment created by user: {} on social post: {}", user.getActualUsername(), socialPost.getId());
            return CommentDto.fromComment(savedComment);

        } catch (Exception ex) {
            log.error("Error creating comment for user: {} on social post: {}", user.getActualUsername(), socialPost.getId(), ex);
            throw new ServiceException("Failed to create comment on social post", ex);
        }
    }

    // ===== UPDATE COMMENT (WORKS FOR BOTH POST TYPES) =====

    @Transactional(rollbackFor = Exception.class)
    public CommentDto updateComment(@NotNull Long commentId, @Valid CommentUpdateDto commentDto, @NotNull User user) {
        try {
            validateCommentId(commentId);
            validateCommentDto(commentDto);
            PostUtility.validateUser(user);

            Comment comment = findById(commentId);
            if (comment.getSocialPost() != null) {
                validateSocialPost(comment.getSocialPost(), user);
            }

            // Check if post allows updates
            if (comment.getPost() != null) {
                Post post = comment.getPost();
                if (post == null || post.getStatus() == null || !post.getStatus().allowsUpdates()) {
                    throw new SecurityException("Cannot update comments on this post.");
                }
            } else if (comment.getSocialPost() != null) {
                SocialPost socialPost = comment.getSocialPost();
                if (socialPost == null || !socialPost.getStatus().allowsComments()) {
                    throw new SecurityException("Cannot update comments on this social post.");
                }
            }

            if (comment.getUser() == null || !comment.getUser().getId().equals(user.getId())) {
                throw new SecurityException("Only the comment owner can update the comment.");
            }

            String safeContent = contentValidationService.sanitizeAndValidateContent(commentDto.getText());
            commentDto.setText(safeContent);

            comment.setText(commentDto.getText().trim());
            comment.setUpdatedAt(new Date());
            Comment updatedComment = commentRepository.save(comment);

            log.info("Comment updated by user: {}", user.getActualUsername());
            return CommentDto.fromComment(updatedComment);

        } catch (Exception ex) {
            log.error("Error updating comment {}: {}", commentId, ex.getMessage());
            throw new ServiceException("Failed to update comment", ex);
        }
    }

    // ===== GET COMMENTS BY POST =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentsByPost(@NotNull Post post, Long beforeId, Integer limit, String sort, User currentUser) {
        try {
            PostUtility.validatePost(post);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getCommentsByPost", beforeId, limit);

            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                log.warn("Attempted to retrieve comments for non-visible post: {} with status: {}",
                        post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null");
                return PaginationUtils.createEmptyCommentDtoResponse(setup.getValidatedLimit());
            }

            List<Comment> comments;
            if ("TOP".equalsIgnoreCase(sort)) {
                // Offset-based pagination: reuse beforeId as offset value
                int offset = (beforeId != null && beforeId > 0) ? beforeId.intValue() : 0;
                Pageable pageable = PageRequest.of(0, setup.getValidatedLimit() + 1).withPage(0);
                pageable = PageRequest.of(offset / Math.max(1, setup.getValidatedLimit()), setup.getValidatedLimit() + 1);
                comments = commentRepository.findTopRatedByPost(post, pageable);
                List<CommentDto> commentDtos = convertCommentsToDto(comments, currentUser);
                boolean hasMore = commentDtos.size() > setup.getValidatedLimit();
                if (hasMore) commentDtos = commentDtos.subList(0, setup.getValidatedLimit());
                long nextOffset = offset + setup.getValidatedLimit();
                Long nextCursor = hasMore ? nextOffset : null;
                return PaginationUtils.createCustomResponse(commentDtos, hasMore, nextCursor, setup.getValidatedLimit());
            } else {
                if (setup.hasCursor()) {
                    comments = commentRepository.findByPostAndIdLessThanOrderByCreatedAtDesc(
                            post, setup.getSanitizedCursor(), setup.toPageable());
                } else {
                    comments = commentRepository.findByPostOrderByCreatedAtDesc(post, setup.toPageable());
                }
                List<CommentDto> commentDtos = convertCommentsToDto(comments, currentUser);
                PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(commentDtos, setup.getValidatedLimit());
                PaginationUtils.logPaginationResults("getCommentsByPost", comments, response.isHasMore(), response.getNextCursor());
                return response;
            }

        } catch (DataAccessException ex) {
            log.error("Database error while retrieving comments for post: {}", post.getId(), ex);
            throw new ServiceException("Failed to retrieve comments due to database error", ex);
        }
    }

    // ===== GET COMMENTS BY SOCIAL POST =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentsBySocialPost(@NotNull SocialPost socialPost, Long beforeId, Integer limit, String sort, User user) {
        try {
            validateSocialPost(socialPost, user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getCommentsBySocialPost", beforeId, limit);

            if (!socialPost.isEligibleForDisplay()) {
                log.warn("Attempted to retrieve comments for non-visible social post: {}", socialPost.getId());
                return PaginationUtils.createEmptyCommentDtoResponse(setup.getValidatedLimit());
            }

            List<Comment> comments;
            if ("TOP".equalsIgnoreCase(sort)) {
                int offset = (beforeId != null && beforeId > 0) ? beforeId.intValue() : 0;
                Pageable pageable = PageRequest.of(offset / Math.max(1, setup.getValidatedLimit()), setup.getValidatedLimit() + 1);
                comments = commentRepository.findTopRatedBySocialPostId(socialPost.getId(), pageable);
                List<CommentDto> commentDtos = convertCommentsToDto(comments, user);
                boolean hasMore = commentDtos.size() > setup.getValidatedLimit();
                if (hasMore) commentDtos = commentDtos.subList(0, setup.getValidatedLimit());
                long nextOffset = offset + setup.getValidatedLimit();
                Long nextCursor = hasMore ? nextOffset : null;
                return PaginationUtils.createCustomResponse(commentDtos, hasMore, nextCursor, setup.getValidatedLimit());
            } else {
                if (setup.hasCursor()) {
                    comments = commentRepository.findBySocialPostIdAndIdLessThanOrderByCreatedAtDesc(
                            socialPost.getId(), setup.getSanitizedCursor(), setup.toPageable());
                } else {
                    comments = commentRepository.findBySocialPostIdOrderByCreatedAtDesc(socialPost.getId(), setup.toPageable());
                }
                List<CommentDto> commentDtos = convertCommentsToDto(comments, user);
                PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(commentDtos, setup.getValidatedLimit());
                PaginationUtils.logPaginationResults("getCommentsBySocialPost", comments, response.isHasMore(), response.getNextCursor());
                return response;
            }

        } catch (DataAccessException ex) {
            log.error("Database error while retrieving comments for social post: {}", socialPost.getId(), ex);
            throw new ServiceException("Failed to retrieve comments due to database error", ex);
        }
    }

    // ===== GET TOP LEVEL COMMENTS - POST =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getTopLevelCommentsByPost(@NotNull Post post, Long beforeId, Integer limit, String sort, User currentUser) {
        try {
            PostUtility.validatePost(post);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getTopLevelCommentsByPost", beforeId, limit);

            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                return PaginationUtils.createEmptyCommentDtoResponse(setup.getValidatedLimit());
            }

            List<Comment> comments;
            if ("TOP".equalsIgnoreCase(sort)) {
                int offset = (beforeId != null && beforeId > 0) ? beforeId.intValue() : 0;
                Pageable pageable = PageRequest.of(offset / Math.max(1, setup.getValidatedLimit()), setup.getValidatedLimit() + 1);
                comments = commentRepository.findTopRatedTopLevelCommentsByPost(post, pageable);
                List<CommentDto> commentDtos = convertCommentsToDto(comments, currentUser);
                boolean hasMore = commentDtos.size() > setup.getValidatedLimit();
                if (hasMore) commentDtos = commentDtos.subList(0, setup.getValidatedLimit());
                long nextOffset = offset + setup.getValidatedLimit();
                Long nextCursor = hasMore ? nextOffset : null;
                return PaginationUtils.createCustomResponse(commentDtos, hasMore, nextCursor, setup.getValidatedLimit());
            } else {
                if (setup.hasCursor()) {
                    comments = commentRepository.findTopLevelCommentsByPostAndIdLessThan(
                            post, setup.getSanitizedCursor(), setup.toPageable());
                } else {
                    comments = commentRepository.findTopLevelCommentsByPost(post, setup.toPageable());
                }
                List<CommentDto> commentDtos = convertCommentsToDto(comments, currentUser);
                PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(commentDtos, setup.getValidatedLimit());
                PaginationUtils.logPaginationResults("getTopLevelCommentsByPost", comments, response.isHasMore(), response.getNextCursor());
                return response;
            }

        } catch (Exception ex) {
            log.error("Error retrieving top-level comments for post: {}", post.getId(), ex);
            throw new ServiceException("Failed to retrieve top-level comments", ex);
        }
    }

    // ===== GET TOP LEVEL COMMENTS - SOCIAL POST =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getTopLevelCommentsBySocialPost(@NotNull SocialPost socialPost, Long beforeId, Integer limit, String sort, User user) {
        try {
            validateSocialPost(socialPost, user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getTopLevelCommentsBySocialPost", beforeId, limit);

            if (!socialPost.isEligibleForDisplay()) {
                return PaginationUtils.createEmptyCommentDtoResponse(setup.getValidatedLimit());
            }

            List<Comment> comments;
            if ("TOP".equalsIgnoreCase(sort)) {
                int offset = (beforeId != null && beforeId > 0) ? beforeId.intValue() : 0;
                Pageable pageable = PageRequest.of(offset / Math.max(1, setup.getValidatedLimit()), setup.getValidatedLimit() + 1);
                comments = commentRepository.findTopRatedTopLevelCommentsBySocialPostId(socialPost.getId(), pageable);
                List<CommentDto> commentDtos = convertCommentsToDto(comments, user);
                boolean hasMore = commentDtos.size() > setup.getValidatedLimit();
                if (hasMore) commentDtos = commentDtos.subList(0, setup.getValidatedLimit());
                long nextOffset = offset + setup.getValidatedLimit();
                Long nextCursor = hasMore ? nextOffset : null;
                return PaginationUtils.createCustomResponse(commentDtos, hasMore, nextCursor, setup.getValidatedLimit());
            } else {
                if (setup.hasCursor()) {
                    comments = commentRepository.findTopLevelCommentsBySocialPostIdAndIdLessThan(
                            socialPost.getId(), setup.getSanitizedCursor(), setup.toPageable());
                } else {
                    comments = commentRepository.findTopLevelCommentsBySocialPostId(socialPost.getId(), setup.toPageable());
                }
                List<CommentDto> commentDtos = convertCommentsToDto(comments, user);
                PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(commentDtos, setup.getValidatedLimit());
                PaginationUtils.logPaginationResults("getTopLevelCommentsBySocialPost", comments, response.isHasMore(), response.getNextCursor());
                return response;
            }

        } catch (Exception ex) {
            log.error("Error retrieving top-level comments for social post: {}", socialPost.getId(), ex);
            throw new ServiceException("Failed to retrieve top-level comments", ex);
        }
    }

    // ===== COUNT COMMENTS =====

    public Long countCommentsByPost(@NotNull Post post) {
        try {
            PostUtility.validatePost(post);
            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                return 0L;
            }
            Long count = commentRepository.countByPost(post);
            return count != null ? count : 0L;
        } catch (Exception ex) {
            log.error("Error counting comments for post: {}", post.getId(), ex);
            throw new ServiceException("Failed to count comments", ex);
        }
    }

    public Long countCommentsBySocialPost(@NotNull SocialPost socialPost, User user) {
        try {
            validateSocialPost(socialPost, user);
            if (!socialPost.isEligibleForDisplay()) {
                return 0L;
            }
            Long count = commentRepository.countBySocialPost(socialPost);
            return count != null ? count : 0L;
        } catch (Exception ex) {
            log.error("Error counting comments for social post: {}", socialPost.getId(), ex);
            throw new ServiceException("Failed to count comments", ex);
        }
    }

    // ===== GET COMMENT REPLIES =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentReplies(@NotNull Long commentId, Long beforeId, Integer limit, User currentUser) {
        try {
            validateCommentId(commentId);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getCommentReplies", beforeId, limit);

            Comment parentComment = findById(commentId);

            // Check if parent comment belongs to visible post
            boolean isVisible = false;
            if (parentComment.getPost() != null) {
                Post post = parentComment.getPost();
                isVisible = post.getStatus() != null && post.getStatus().isVisible();
            } else if (parentComment.getSocialPost() != null) {
                SocialPost socialPost = parentComment.getSocialPost();
                isVisible = socialPost.isEligibleForDisplay();
            }

            if (!isVisible) {
                return PaginationUtils.createEmptyCommentDtoResponse(setup.getValidatedLimit());
            }

            List<Comment> replies;
            if (setup.hasCursor()) {
                replies = commentRepository.findByParentCommentAndIdLessThanOrderByCreatedAtDesc(
                        parentComment, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                replies = commentRepository.findByParentCommentOrderByCreatedAtDesc(parentComment, setup.toPageable());
            }

            List<CommentDto> replyDtos = convertCommentsToDto(replies, currentUser);
            PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(replyDtos, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getCommentReplies", replies, response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception ex) {
            log.error("Error retrieving replies for comment: {}", commentId, ex);
            throw new ServiceException("Failed to retrieve comment replies", ex);
        }
    }

    // ===== GET COMMENTS BY USER =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentsByUser(@NotNull User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getCommentsByUser", beforeId, limit);

            List<Comment> userComments;
            if (setup.hasCursor()) {
                userComments = commentRepository.findByUserWithVisiblePostsAndIdLessThanOrderByCreatedAtDesc(
                        user, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                userComments = commentRepository.findByUserWithVisiblePostsOrderByCreatedAtDesc(user, setup.toPageable());
            }

            List<CommentDto> commentDtos = convertCommentsToDto(userComments, user);
            PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(commentDtos, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getCommentsByUser", userComments, response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception ex) {
            log.error("Error retrieving comments for user: {}", user.getActualUsername(), ex);
            throw new ServiceException("Failed to retrieve user comments", ex);
        }
    }

    // ===== HELPER METHODS =====

    public Comment findById(@NotNull Long commentId) {
        try {
            validateCommentId(commentId);
            return commentRepository.findById(commentId)
                    .orElseThrow(() -> new CommentNotFoundException("Comment not found with ID: " + commentId));
        } catch (DataAccessException ex) {
            log.error("Database error while finding comment with ID: {}", commentId, ex);
            throw new ServiceException("Failed to retrieve comment due to database error", ex);
        }
    }

    private List<CommentDto> convertCommentsToDto(List<Comment> comments, User currentUser) {
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> userVotes = new HashMap<>();
        if (currentUser != null) {
            List<Long> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toList());
            if (!commentIds.isEmpty()) {
                List<CommentInteraction> interactions = commentInteractionRepository.findByUserIdAndCommentIdIn(currentUser.getId(), commentIds);
                for (CommentInteraction interaction : interactions) {
                    userVotes.put(interaction.getComment().getId(), interaction.getInteractionType().name());
                }
            }
        }

        return comments.stream()
                .map(comment -> {
                    try {
                        int recursiveCount = commentRepository.countRecursiveReplies(comment.getId());
                        CommentDto dto = CommentDto.fromComment(comment, recursiveCount);
                        if (dto != null) {
                            String vote = userVotes.get(comment.getId());
                            dto.setUserVote(vote);
                            if ("LIKE".equals(vote)) {
                                dto.setLikedByCurrentUser(true);
                                dto.setDislikedByCurrentUser(false);
                            } else if ("DISLIKE".equals(vote)) {
                                dto.setLikedByCurrentUser(false);
                                dto.setDislikedByCurrentUser(true);
                            }
                        }
                        return dto;
                    } catch (Exception e) {
                        log.warn("Failed to convert comment {} to DTO: {}", comment.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    // ===== VALIDATION METHODS =====

    private void validateCommentId(Long commentId) {
        if (commentId == null || commentId <= 0) {
            throw new IllegalArgumentException("Comment ID must be a positive number");
        }
    }

    private void validateCommentDto(Object commentDto) {
        if (commentDto == null) {
            throw new IllegalArgumentException("Comment data cannot be null");
        }

        if (commentDto instanceof CommentCreateDto) {
            CommentCreateDto dto = (CommentCreateDto) commentDto;
            validateCommentText(dto.getText());
        }

        if (commentDto instanceof CommentUpdateDto) {
            CommentUpdateDto dto = (CommentUpdateDto) commentDto;
            validateCommentText(dto.getText());
        }
    }

    private void validateCommentText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Comment text cannot be empty");
        }
        if (text.length() > Constant.MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment text exceeds maximum length of " +
                    Constant.MAX_COMMENT_LENGTH + " characters");
        }
    }

    private void validateSocialPost(SocialPost socialPost, User user) {
        if (socialPost == null) {
            throw new IllegalArgumentException("Social post cannot be null");
        }
        if (socialPost.getId() == null) {
            throw new IllegalArgumentException("Social post ID cannot be null");
        }
        if (!socialPost.isEligibleForDisplay()) {
            throw new SecurityException("Social post is not eligible for display");
        }
        if (socialPost.getCommunityStatus() != null && !"ACTIVE".equals(socialPost.getCommunityStatus())) {
            throw new SecurityException("Cannot view or comment on a post in an archived or deleted community.");
        }
        if (socialPost.getCommunityId() != null && (user == null || !com.JanSahayak.AI.payload.PostUtility.isAdmin(user))) {
            String privacy = socialPost.getCommunityPrivacy();
            if ("PRIVATE".equalsIgnoreCase(privacy) || "SECRET".equalsIgnoreCase(privacy)) {
                if (user == null || !communityService.isMember(socialPost.getCommunityId(), user.getId())) {
                    throw new SecurityException("User does not have permission to view or comment on this private community post");
                }
            }
        }
    }

    private void validateParentCommentForPost(Comment parentComment, Post post) {
        if (parentComment == null) {
            throw new CommentNotFoundException("Parent comment not found");
        }

        if (parentComment.getPost() == null) {
            throw new IllegalArgumentException("Parent comment has no associated post");
        }

        if (!parentComment.getPost().getId().equals(post.getId())) {
            throw new IllegalArgumentException("Parent comment must belong to the same post");
        }

        if (parentComment.getPost().getStatus() == null || !parentComment.getPost().getStatus().isInteractable()) {
            throw new IllegalArgumentException("Cannot reply to comments on posts with status: " +
                    parentComment.getPost().getStatus().getDisplayName());
        }
    }

    private void validateParentCommentForSocialPost(Comment parentComment, SocialPost socialPost) {
        if (parentComment == null) {
            throw new CommentNotFoundException("Parent comment not found");
        }

        if (parentComment.getSocialPost() == null) {
            throw new IllegalArgumentException("Parent comment is not associated with a social post");
        }

        if (!parentComment.getSocialPost().getId().equals(socialPost.getId())) {
            throw new IllegalArgumentException("Parent comment must belong to the same social post");
        }

        if (!parentComment.getSocialPost().getStatus().allowsComments()) {
            throw new IllegalArgumentException("Cannot reply to comments on social posts with status: " +
                    parentComment.getSocialPost().getStatus().getDisplayName());
        }
    }
    /**
     * Delete a comment by id.
     *
     * Rules enforced:
     *   • Only the comment owner OR a user with ROLE_ADMIN may delete.
     *   • Decrements the parent post's / social-post's commentCount atomically.
     *   • Throws CommentNotFoundException (404) if the id is unknown.
     *   • Throws SecurityException (403) if the caller is not the owner/admin.
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(@NotNull Long commentId, @NotNull User currentUser) {
        try {
            validateCommentId(commentId);
            PostUtility.validateUser(currentUser);

            Comment comment = findById(commentId);

            // ── Ownership / admin guard ───────────────────────────────────────
            boolean isOwner = comment.getUser() != null &&
                    comment.getUser().getId().equals(currentUser.getId());
            boolean isAdmin = currentUser.getAuthorities() != null &&
                    currentUser.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isOwner && !isAdmin) {
                throw new SecurityException("You are not authorised to delete this comment");
            }
            // ─────────────────────────────────────────────────────────────────

            int totalCommentsToDelete = 1 + countDescendants(comment);

            if (comment.getPost() != null) {
                Post post = comment.getPost();
                post.setCommentCount(Math.max(0, post.getCommentCount() - totalCommentsToDelete));
                postRepository.decrementCommentCountBy(post.getId(), totalCommentsToDelete);
                postRepository.save(post);
                broadcastCommentStatsUpdate(post.getId(), "posts", null, (long) post.getCommentCount());
            } else if (comment.getSocialPost() != null) {
                SocialPost socialPost = comment.getSocialPost();
                socialPost.setCommentCount(Math.max(0, socialPost.getCommentCount() - totalCommentsToDelete));
                socialPost.recalculateEngagementScore();
                socialPostRepository.decrementCommentCountBy(socialPost.getId(), totalCommentsToDelete);
                socialPostRepository.save(socialPost);
                broadcastCommentStatsUpdate(socialPost.getId(), "social-posts", socialPost.getCommunityId(), (long) socialPost.getCommentCount());
            }
            // ─────────────────────────────────────────────────────────────────

            if (comment.getReplyCount() > 0) {
                // Soft Delete
                comment.setIsDeleted(true);
                comment.setDeletedByType(isAdmin ? "ADMINISTRATOR" : "USER");
                comment.setText("[Deleted]");
                commentRepository.save(comment);
                log.info("Comment {} soft-deleted by user: {}", commentId, currentUser.getActualUsername());
            } else {
                // Hard Delete
                commentInteractionRepository.deleteByCommentId(comment.getId());
                commentRepository.delete(comment);
                log.info("Comment {} hard-deleted by user: {}", commentId, currentUser.getActualUsername());
            }

        } catch (SecurityException | CommentNotFoundException e) {
            throw e;
        } catch (DataAccessException ex) {
            log.error("Database error while deleting comment: {}", commentId, ex);
            throw new ServiceException("Failed to delete comment due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while deleting comment: {}", commentId, ex);
            throw new ServiceException("Failed to delete comment", ex);
        }
    }

    private int countDescendants(Comment comment) {
        if (comment.getReplies() == null || comment.getReplies().isEmpty()) {
            return 0;
        }
        int count = comment.getReplies().size();
        for (Comment reply : comment.getReplies()) {
            count += countDescendants(reply);
        }
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentDto pinComment(@NotNull Long commentId, @NotNull User currentUser) {
        try {
            validateCommentId(commentId);
            PostUtility.validateUser(currentUser);

            Comment comment = findById(commentId);

            boolean isPostOwner = false;
            boolean isAdmin = currentUser.getAuthorities() != null &&
                    currentUser.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (comment.getPost() != null) {
                isPostOwner = comment.getPost().getUser().getId().equals(currentUser.getId());
            } else if (comment.getSocialPost() != null) {
                isPostOwner = comment.getSocialPost().getUser().getId().equals(currentUser.getId());
            }

            if (!isPostOwner && !isAdmin) {
                throw new SecurityException("Only the post author or an administrator can pin comments.");
            }

            comment.setIsPinned(!comment.getIsPinned());
            commentRepository.save(comment);

            log.info("Comment {} pinning toggled to {} by user: {}", commentId, comment.getIsPinned(), currentUser.getActualUsername());
            return CommentDto.fromComment(comment);
        } catch (Exception ex) {
            log.error("Failed to pin comment {}: {}", commentId, ex.getMessage());
            throw new ServiceException("Failed to pin comment", ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentDto createCommentOnPostById(Long postId, CommentCreateDto dto, User user) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        return createCommentOnPost(dto, user, post);
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentDto createCommentOnSocialPostById(Long socialPostId, CommentCreateDto dto, User user) {
        SocialPost sp = socialPostRepository.findById(socialPostId)
                .orElseThrow(() -> new ResourceNotFoundException("SocialPost not found: " + socialPostId));
        return createCommentOnSocialPost(dto, user, sp);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentsByPostId(Long postId, Long beforeId, Integer limit, String sort, User currentUser) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        return getCommentsByPost(post, beforeId, limit, sort, currentUser);
    }

    /**
     * All comments for a SocialPost, cursor-paginated.
     * Controller calls this instead of loading SocialPost then calling getCommentsBySocialPost(sp, ...).
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentsBySocialPostId(Long postId, Long beforeId, Integer limit, String sort, User user) {
        SocialPost sp = socialPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("SocialPost not found: " + postId));
        return getCommentsBySocialPost(sp, beforeId, limit, sort, user);
    }

    // ── Read: top-level only ─────────────────────────────────────────────────

    /**
     * Top-level comments only for a Post, cursor-paginated.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getTopLevelCommentsByPostId(Long postId, Long beforeId, Integer limit, String sort, User currentUser) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        return getTopLevelCommentsByPost(post, beforeId, limit, sort, currentUser);
    }

    /**
     * Top-level comments only for a SocialPost, cursor-paginated.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getTopLevelCommentsBySocialPostId(Long postId, Long beforeId, Integer limit, String sort, User user) {
        SocialPost sp = socialPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("SocialPost not found: " + postId));
        return getTopLevelCommentsBySocialPost(sp, beforeId, limit, sort, user);
    }

    // ── Read: counts ─────────────────────────────────────────────────────────

    /**
     * Total comment count for a Post.
     */
    @Transactional(readOnly = true)
    public Long countCommentsByPostId(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        return countCommentsByPost(post);
    }

    /**
     * Total comment count for a SocialPost.
     */
    @Transactional(readOnly = true)
    public Long countCommentsBySocialPostId(Long postId, User user) {
        SocialPost sp = socialPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("SocialPost not found: " + postId));
        return countCommentsBySocialPost(sp, user);
    }

}
