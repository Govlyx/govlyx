package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.*;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.ResourceNotFoundException;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.model.PostShare.ShareType;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.payload.SocialPostUtility;
import com.JanSahayak.AI.repository.*;
import com.JanSahayak.AI.config.Constant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostInteractionService {

    // Self-injection to fix proxy-bypass for @Transactional and @Async on internal calls
    private PostInteractionService self;

    @Autowired
    public void setSelf(@Lazy PostInteractionService self) {
        this.self = self;
    }

    // ── Repositories ──────────────────────────────────────────────────────────
    private final PostRepo       postRepository;
    private final UserRepo       userRepository;
    private final SocialPostRepo socialPostRepository;
    private final PostViewRepo   postViewRepository;
    private final PostLikeRepo   postLikeRepository;
    private final SavedPostRepo  savedPostRepo;
    private final PostShareRepo  postShareRepo;
    private final CommentRepo    commentRepo;

    @Lazy
    @Autowired
    private CommunityService communityService;

    @Lazy
    @Autowired
    private InterestProfileService interestProfileService;

    @Lazy
    @Autowired
    private PostService postService;

    @Lazy
    @Autowired
    private SocialPostService socialPostService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    @Autowired(required = false)
    private org.springframework.messaging.simp.SimpMessagingTemplate simpMessagingTemplate;

    // =========================================================================
    // VIEWS — REGULAR POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostView recordPostView(Post post, User user) {
        try {
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);

            if (!PostUtility.isPostStatusVisible(post)) {
                log.debug("Cannot record view for non-visible post: {} (status: {})",
                        post.getId(), post.getStatus().getDisplayName());
                return null;
            }

            Date threshold = dedupeThreshold();
            Optional<PostView> recent = postViewRepository.findByPostAndUserIdAndViewedAtAfter(post, user.getId(), threshold);
            if (recent.isPresent()) {
                log.debug("Duplicate view prevented: post={} user={}", post.getId(), user.getActualUsername());
                return null;
            }

            PostView view = new PostView();
            view.setPost(post);
            view.setUser(user);
            view.setViewedAt(new Date());

            PostView saved = postViewRepository.save(view);
            self.executeIncrementPostViewAsync(post.getId());


            log.info("View recorded: post={} user={} viewCount={}", post.getId(), user.getActualUsername(), post.getViewCount());
            return saved;

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to record view: post={} user={}",
                    post != null ? post.getId() : "null",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to record post view: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // VIEWS — SOCIAL POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostView recordSocialPostView(SocialPost socialPost, User user) {
        try {
            SocialPostUtility.validateSocialPost(socialPost);
            SocialPostUtility.validateUser(user);

            if (!socialPost.isEligibleForDisplay()) {
                log.debug("Cannot record view for non-visible social post: {} (status: {})",
                        socialPost.getId(), socialPost.getStatus().getDisplayName());
                return null;
            }

            Date threshold = dedupeThreshold();
            Optional<PostView> recent = postViewRepository.findBySocialPostAndUserIdAndViewedAtAfter(socialPost, user.getId(), threshold);
            if (recent.isPresent()) {
                log.debug("Duplicate view prevented: socialPost={} user={}", socialPost.getId(), user.getActualUsername());
                return null;
            }

            PostView view = new PostView();
            view.setSocialPost(socialPost);
            view.setUser(user);
            view.setViewedAt(new Date());

            PostView saved = postViewRepository.save(view);
            self.executeIncrementSocialPostViewAsync(socialPost.getId());

            fireHligSignal(() -> interestProfileService.onView(user.getId(), socialPost.getId()),
                    "VIEW", socialPost.getId(), user.getId());

            log.info("View recorded: socialPost={} user={} viewCount={}", socialPost.getId(), user.getActualUsername(), socialPost.getViewCount());
            return saved;

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to record view: socialPost={} user={}",
                    socialPost != null ? socialPost.getId() : "null",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to record social post view: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // LIKE — REGULAR POST
    // =========================================================================

    public boolean likePost(Post post, User user) {
        PostUtility.validatePost(post);
        PostUtility.validateUser(user);
        validatePostInteractable(post);

        boolean currentlyLiked = hasUserLikedPost(post, user);
        boolean currentlyDisliked = hasUserDislikedPost(post, user);

        if (currentlyLiked) {
            post.decrementLikeCount();
        } else {
            post.incrementLikeCount();
            if (currentlyDisliked) post.decrementDislikeCount();
        }

        self.executeLikePostAsync(post.getId(), user.getId());
        // Cache is evicted asynchronously
        return !currentlyLiked;
    }

    @org.springframework.scheduling.annotation.Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeLikePostAsync(Long postId, Long userId) {
        Post post = postRepository.findById(postId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (post == null || user == null) return;
        
        try {
            Optional<PostLike> existing = postLikeRepository.findByPostAndUserId(post, userId);
            if (existing.isPresent()) {
                PostLike row = existing.get();
                if (row.isLike()) {
                    postLikeRepository.delete(row);
                    postRepository.decrementLikeCount(postId);
                    log.info("[Like] Removed (Async): post={} user={}", postId, user.getActualUsername());
                } else {
                    row.setReactionType(PostLike.ReactionType.LIKE);
                    postLikeRepository.save(row);
                    postRepository.decrementDislikeCount(postId);
                    postRepository.incrementLikeCount(postId);
                    try { notificationService.notifyPostLiked(post, user); } catch (Exception e) {}
                    log.info("[Like] Flipped DISLIKE→LIKE (Async): post={} user={}", postId, user.getActualUsername());
                }
            } else {
                PostLike newLike = buildLike(post, null, user, PostLike.ReactionType.LIKE);
                postLikeRepository.save(newLike);
                postRepository.incrementLikeCount(postId);
                try { notificationService.notifyPostLiked(post, user); } catch (Exception e) {}
                log.info("[Like] Added (Async): post={} user={}", postId, user.getActualUsername());
            }
            evictPostCountsCache(postId);
            broadcastPostStatsUpdate(postId);
        } catch (DataIntegrityViolationException e) {
            log.debug("[Like] Race condition (DB Async): post={} user={}", postId, user.getActualUsername());
        } catch (Exception e) {
            log.error("[Like] Failed Async: post={} user={}", postId, user.getActualUsername(), e);
        }
    }

    @org.springframework.scheduling.annotation.Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeIncrementPostViewAsync(Long postId) {
        try {
            postRepository.incrementViewCount(postId);
            evictPostCountsCache(postId);
            broadcastPostStatsUpdate(postId);
        } catch (Exception e) {
            log.error("[View] Failed Async: post={}", postId, e);
        }
    }

    // =========================================================================
    // DISLIKE — REGULAR POST
    // =========================================================================

    public boolean dislikePost(Post post, User user) {
        PostUtility.validatePost(post);
        PostUtility.validateUser(user);
        validatePostInteractable(post);

        boolean currentlyDisliked = hasUserDislikedPost(post, user);
        boolean currentlyLiked = hasUserLikedPost(post, user);

        if (currentlyDisliked) {
            post.decrementDislikeCount();
        } else {
            post.incrementDislikeCount();
            if (currentlyLiked) post.decrementLikeCount();
        }

        self.executeDislikePostAsync(post.getId(), user.getId());
        // Cache is evicted asynchronously
        return !currentlyDisliked;
    }

    @org.springframework.scheduling.annotation.Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeDislikePostAsync(Long postId, Long userId) {
        Post post = postRepository.findById(postId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (post == null || user == null) return;
        
        try {
            Optional<PostLike> existing = postLikeRepository.findByPostAndUserId(post, userId);
            if (existing.isPresent()) {
                PostLike row = existing.get();
                if (row.isDislike()) {
                    postLikeRepository.delete(row);
                    postRepository.decrementDislikeCount(postId);
                    log.info("[Dislike] Removed (Async): post={} user={}", postId, user.getActualUsername());
                } else {
                    row.setReactionType(PostLike.ReactionType.DISLIKE);
                    postLikeRepository.save(row);
                    postRepository.decrementLikeCount(postId);
                    postRepository.incrementDislikeCount(postId);
                    log.info("[Dislike] Flipped LIKE→DISLIKE (Async): post={} user={}", postId, user.getActualUsername());
                }
            } else {
                PostLike newDislike = buildLike(post, null, user, PostLike.ReactionType.DISLIKE);
                postLikeRepository.save(newDislike);
                postRepository.incrementDislikeCount(postId);
                log.info("[Dislike] Added (Async): post={} user={}", postId, user.getActualUsername());
            }
            evictPostCountsCache(postId);
            broadcastPostStatsUpdate(postId);
        } catch (DataIntegrityViolationException e) {
            log.debug("[Dislike] Race condition (DB Async): post={} user={}", postId, user.getActualUsername());
        } catch (Exception e) {
            log.error("[Dislike] Failed Async: post={} user={}", postId, user.getActualUsername(), e);
        }
    }

    // =========================================================================
    // LIKE — SOCIAL POST
    // =========================================================================

    public boolean likeSocialPost(SocialPost socialPost, User user) {
        SocialPostUtility.validateSocialPost(socialPost);
        SocialPostUtility.validateUser(user);
        validateSocialPostInteractable(socialPost, user);

        boolean currentlyLiked = hasUserLikedSocialPost(socialPost, user);
        boolean currentlyDisliked = hasUserDislikedSocialPost(socialPost, user);

        if (currentlyLiked) {
            socialPost.decrementLikeCount();
        } else {
            socialPost.incrementLikeCount();
            if (currentlyDisliked) socialPost.decrementDislikeCount();
        }

        self.executeLikeSocialPostAsync(socialPost.getId(), user.getId(), socialPost.getCommunity() != null ? socialPost.getCommunity().getId() : null);
        // Cache is evicted asynchronously
        return !currentlyLiked;
    }

    @org.springframework.scheduling.annotation.Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeLikeSocialPostAsync(Long socialPostId, Long userId, Long communityId) {
        SocialPost socialPost = socialPostRepository.findById(socialPostId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (socialPost == null || user == null) return;

        try {
            Optional<PostLike> existing = postLikeRepository.findBySocialPostAndUserId(socialPost, userId);
            if (existing.isPresent()) {
                PostLike row = existing.get();
                if (row.isLike()) {
                    postLikeRepository.delete(row);
                    socialPostRepository.decrementLikeCount(socialPostId);
                    fireHligSignal(() -> interestProfileService.onUnlike(userId, socialPostId), "UNLIKE", socialPostId, userId);
                    log.info("[Like] Removed (Async): socialPost={} user={}", socialPostId, user.getActualUsername());
                } else {
                    row.setReactionType(PostLike.ReactionType.LIKE);
                    postLikeRepository.save(row);
                    socialPostRepository.decrementDislikeCount(socialPostId);
                    socialPostRepository.incrementLikeCount(socialPostId);
                    fireHligSignal(() -> interestProfileService.onLike(userId, socialPostId), "LIKE", socialPostId, userId);
                    if (communityId != null) {
                        try { communityService.onLikeAdded(communityId); } catch (Exception e) {}
                    }
                    try { notificationService.notifySocialPostLiked(socialPost.getId(), user.getId()); } catch (Exception e) {}
                    log.info("[Like] Flipped DISLIKE→LIKE (Async): socialPost={} user={}", socialPostId, user.getActualUsername());
                }
            } else {
                PostLike newLike = buildLike(null, socialPost, user, PostLike.ReactionType.LIKE);
                postLikeRepository.save(newLike);
                socialPostRepository.incrementLikeCount(socialPostId);
                fireHligSignal(() -> interestProfileService.onLike(userId, socialPostId), "LIKE", socialPostId, userId);
                if (communityId != null) {
                    try { communityService.onLikeAdded(communityId); } catch (Exception e) {}
                }
                try { notificationService.notifySocialPostLiked(socialPost.getId(), user.getId()); } catch (Exception e) {}
                log.info("[Like] Added (Async): socialPost={} user={}", socialPostId, user.getActualUsername());
            }
            evictSocialPostCountsCache(socialPostId);
            broadcastSocialPostStatsUpdate(socialPostId, communityId);
        } catch (DataIntegrityViolationException e) {
            log.debug("[Like] Race condition (DB Async): socialPost={} user={}", socialPostId, user.getActualUsername());
        } catch (Exception e) {
            log.error("[Like] Failed Async: socialPost={} user={}", socialPostId, user.getActualUsername(), e);
        }
    }

    @org.springframework.scheduling.annotation.Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeIncrementSocialPostViewAsync(Long socialPostId) {
        try {
            socialPostRepository.incrementViewCount(socialPostId);
            evictSocialPostCountsCache(socialPostId);
            broadcastSocialPostStatsUpdate(socialPostId, null);
        } catch (Exception e) {
            log.error("[View] Failed Async: socialPost={}", socialPostId, e);
        }
    }

    // =========================================================================
    // DISLIKE — SOCIAL POST
    // =========================================================================

    public boolean dislikeSocialPost(SocialPost socialPost, User user) {
        SocialPostUtility.validateSocialPost(socialPost);
        SocialPostUtility.validateUser(user);
        validateSocialPostInteractable(socialPost, user);

        boolean currentlyDisliked = hasUserDislikedSocialPost(socialPost, user);
        boolean currentlyLiked = hasUserLikedSocialPost(socialPost, user);

        if (currentlyDisliked) {
            socialPost.decrementDislikeCount();
        } else {
            socialPost.incrementDislikeCount();
            if (currentlyLiked) socialPost.decrementLikeCount();
        }

        self.executeDislikeSocialPostAsync(socialPost.getId(), user.getId());
        // Cache is evicted asynchronously
        return !currentlyDisliked;
    }

    @org.springframework.scheduling.annotation.Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeDislikeSocialPostAsync(Long socialPostId, Long userId) {
        SocialPost socialPost = socialPostRepository.findById(socialPostId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (socialPost == null || user == null) return;

        try {
            Optional<PostLike> existing = postLikeRepository.findBySocialPostAndUserId(socialPost, userId);
            if (existing.isPresent()) {
                PostLike row = existing.get();
                if (row.isDislike()) {
                    postLikeRepository.delete(row);
                    socialPostRepository.decrementDislikeCount(socialPostId);
                    fireHligSignal(() -> interestProfileService.onUnlike(userId, socialPostId), "UN-DISLIKE", socialPostId, userId);
                    log.info("[Dislike] Removed (Async): socialPost={} user={}", socialPostId, user.getActualUsername());
                } else {
                    row.setReactionType(PostLike.ReactionType.DISLIKE);
                    postLikeRepository.save(row);
                    socialPostRepository.decrementLikeCount(socialPostId);
                    socialPostRepository.incrementDislikeCount(socialPostId);
                    fireHligSignal(() -> interestProfileService.onDislike(userId, socialPostId), "DISLIKE", socialPostId, userId);
                    log.info("[Dislike] Flipped LIKE→DISLIKE (Async): socialPost={} user={}", socialPostId, user.getActualUsername());
                }
            } else {
                PostLike newDislike = buildLike(null, socialPost, user, PostLike.ReactionType.DISLIKE);
                postLikeRepository.save(newDislike);
                socialPostRepository.incrementDislikeCount(socialPostId);
                fireHligSignal(() -> interestProfileService.onDislike(userId, socialPostId), "DISLIKE", socialPostId, userId);
                log.info("[Dislike] Added (Async): socialPost={} user={}", socialPostId, user.getActualUsername());
            }
            evictSocialPostCountsCache(socialPostId);
            broadcastSocialPostStatsUpdate(socialPostId, socialPost.getCommunity() != null ? socialPost.getCommunity().getId() : null);
        } catch (DataIntegrityViolationException e) {
            log.debug("[Dislike] Race condition (DB Async): socialPost={} user={}", socialPostId, user.getActualUsername());
        } catch (Exception e) {
            log.error("[Dislike] Failed Async: socialPost={} user={}", socialPostId, user.getActualUsername(), e);
        }
    }

    // =========================================================================
    // LEGACY TOGGLES (backward compatibility)
    // =========================================================================

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean togglePostLike(Post post, User user) {
        return likePost(post, user);
    }

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleSocialPostLike(SocialPost socialPost, User user) {
        return likeSocialPost(socialPost, user);
    }

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public PostView recordPostViewWithCounterUpdate(Post post, User user) {
        return recordPostView(post, user);
    }

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean togglePostLikeWithCounterUpdate(Post post, User user) {
        return togglePostLike(post, user);
    }

    // =========================================================================
    // REACTION STATUS CHECKS — single post (used for single post detail page)
    // =========================================================================

    
    // =========================================================================
    // CACHED COUNTS
    // =========================================================================

    @org.springframework.cache.annotation.Cacheable(value = "postCounts", key = "'POST_' + #postId")
    public java.util.Map<String, Object> getPostCounts(Long postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        return java.util.Map.of(
            "likeCount", post.getLikeCount(),
            "dislikeCount", post.getDislikeCount(),
            "saveCount", post.getSaveCount(),
            "viewCount", post.getViewCount(),
            "commentCount", post.getCommentCount(),
            "shareCount", post.getShareCount()
        );
    }

    @org.springframework.cache.annotation.Cacheable(value = "postCounts", key = "'SOCIAL_' + #socialPostId")
    public java.util.Map<String, Object> getSocialPostCounts(Long socialPostId) {
        SocialPost post = socialPostRepository.findById(socialPostId).orElseThrow(() -> new ResourceNotFoundException("SocialPost not found"));
        return java.util.Map.of(
            "likeCount", post.getLikeCount(),
            "dislikeCount", post.getDislikeCount(),
            "saveCount", post.getSaveCount(),
            "viewCount", post.getViewCount(),
            "commentCount", post.getCommentCount(),
            "shareCount", post.getShareCount()
        );
    }

    
    public void evictPostCountsCache(Long postId) {
        org.springframework.cache.Cache cache = cacheManager.getCache("postCounts");
        if (cache != null && postId != null) {
            cache.evict("POST_" + postId);
        }
    }

    public void evictSocialPostCountsCache(Long socialPostId) {
        org.springframework.cache.Cache cache = cacheManager.getCache("postCounts");
        if (cache != null && socialPostId != null) {
            cache.evict("SOCIAL_" + socialPostId);
        }
    }

    public void broadcastPostStatsUpdate(Long postId) {
        if (simpMessagingTemplate != null && postId != null) {
            try {
                java.util.Map<String, Object> stats = new java.util.HashMap<>();
                stats.put("postId", postId);
                stats.put("type", "STATS_UPDATE");
                java.util.Map<String, Object> counts = self.getPostCounts(postId);
                stats.putAll(counts);

                simpMessagingTemplate.convertAndSend("/topic/feed.updates", stats);
                simpMessagingTemplate.convertAndSend("/topic/post." + postId + ".updates", stats);
            } catch (Exception e) {
                log.warn("[WebSocket] Failed to broadcast post stats update: postId={}: {}", postId, e.getMessage());
            }
        }
    }

    public void broadcastSocialPostStatsUpdate(Long socialPostId, Long communityId) {
        if (simpMessagingTemplate != null && socialPostId != null) {
            try {
                java.util.Map<String, Object> stats = new java.util.HashMap<>();
                stats.put("postId", socialPostId);
                stats.put("type", "STATS_UPDATE");
                java.util.Map<String, Object> counts = self.getSocialPostCounts(socialPostId);
                stats.putAll(counts);

                simpMessagingTemplate.convertAndSend("/topic/feed.updates", stats);
                if (communityId != null) {
                    simpMessagingTemplate.convertAndSend("/topic/community." + communityId + ".updates", stats);
                }
                simpMessagingTemplate.convertAndSend("/topic/post." + socialPostId + ".updates", stats);
            } catch (Exception e) {
                log.warn("[WebSocket] Failed to broadcast social post stats update: socialPostId={}: {}", socialPostId, e.getMessage());
            }
        }
    }

    public boolean hasUserLikedPost(Post post, User user) {
        if (post == null || user == null) return false;
        return postLikeRepository.findByPostAndUserId(post, user.getId()).map(PostLike::isLike).orElse(false);
    }

    public boolean hasUserDislikedPost(Post post, User user) {
        if (post == null || user == null) return false;
        return postLikeRepository.findByPostAndUserId(post, user.getId()).map(PostLike::isDislike).orElse(false);
    }

    public boolean hasUserLikedSocialPost(SocialPost socialPost, User user) {
        if (socialPost == null || user == null) return false;
        return postLikeRepository.findBySocialPostAndUserId(socialPost, user.getId()).map(PostLike::isLike).orElse(false);
    }

    public boolean hasUserDislikedSocialPost(SocialPost socialPost, User user) {
        if (socialPost == null || user == null) return false;
        return postLikeRepository.findBySocialPostAndUserId(socialPost, user.getId()).map(PostLike::isDislike).orElse(false);
    }

    public boolean hasUserViewedPostRecently(Post post, User user) {
        if (post == null || user == null) return false;
        return postViewRepository.findByPostAndUserIdAndViewedAtAfter(post, user.getId(), dedupeThreshold()).isPresent();
    }

    public boolean hasUserViewedSocialPostRecently(SocialPost socialPost, User user) {
        if (socialPost == null || user == null) return false;
        return postViewRepository.findBySocialPostAndUserIdAndViewedAtAfter(socialPost, user.getId(), dedupeThreshold()).isPresent();
    }

    // =========================================================================
    // BATCH INTERACTION CHECKS — eliminates N+1 queries in feed (NEW)
    // =========================================================================

    /**
     * Returns IDs of social posts (from given list) that the user has LIKED.
     * Replaces N individual hasUserLikedSocialPost() calls with 1 query.
     */
    public Set<Long> getBatchLikedSocialPostIds(User user, List<Long> postIds) {
        if (user == null || postIds == null || postIds.isEmpty())
            return Collections.emptySet();
        return new HashSet<>(
                postLikeRepository.findLikedSocialPostIdsByUser(user.getId(), postIds, PostLike.ReactionType.LIKE));
    }

    /**
     * Returns IDs of social posts (from given list) that the user has DISLIKED.
     * Replaces N individual hasUserDislikedSocialPost() calls with 1 query.
     */
    public Set<Long> getBatchDislikedSocialPostIds(User user, List<Long> postIds) {
        if (user == null || postIds == null || postIds.isEmpty())
            return Collections.emptySet();
        return new HashSet<>(
                postLikeRepository.findDislikedSocialPostIdsByUser(user.getId(), postIds, PostLike.ReactionType.DISLIKE));
    }

    /**
     * Returns IDs of social posts (from given list) that the user has SAVED.
     * Replaces N individual hasSavedSocialPost() calls with 1 query.
     */
    public Set<Long> getBatchSavedSocialPostIds(User user, List<Long> postIds) {
        if (user == null || postIds == null || postIds.isEmpty())
            return Collections.emptySet();
        return new HashSet<>(
                savedPostRepo.findSavedSocialPostIdsByUser(user.getId(), postIds));
    }

    /**
     * Returns IDs of social posts (from given list) that the user has VIEWED.
     * Replaces N individual hasUserViewedSocialPostRecently() calls with 1 query.
     */
    public Set<Long> getBatchViewedSocialPostIds(User user, List<Long> postIds) {
        if (user == null || postIds == null || postIds.isEmpty())
            return Collections.emptySet();
        return new HashSet<>(
                postViewRepository.findViewedSocialPostIdsByUser(user.getId(), postIds));
    }

    /**
     * Returns IDs of regular posts (from given list) that the user has LIKED.
     */
    public Set<Long> getBatchLikedPostIds(User user, List<Long> postIds) {
        if (user == null || postIds == null || postIds.isEmpty())
            return Collections.emptySet();
        return new HashSet<>(
                postLikeRepository.findLikedPostIdsByUser(user.getId(), postIds, PostLike.ReactionType.LIKE));
    }

    /**
     * Returns IDs of regular posts (from given list) that the user has DISLIKED.
     */
    public Set<Long> getBatchDislikedPostIds(User user, List<Long> postIds) {
        if (user == null || postIds == null || postIds.isEmpty())
            return Collections.emptySet();
        return new HashSet<>(
                postLikeRepository.findDislikedPostIdsByUser(user.getId(), postIds, PostLike.ReactionType.DISLIKE));
    }

    /**
     * Returns IDs of regular posts (from given list) that the user has SAVED.
     */
    public Set<Long> getBatchSavedPostIds(User user, List<Long> postIds) {
        if (user == null || postIds == null || postIds.isEmpty())
            return Collections.emptySet();
        return new HashSet<>(
                savedPostRepo.findSavedPostIdsByUser(user.getId(), postIds));
    }

    // =========================================================================
    // SAVE — SOCIAL POST
    // =========================================================================

    public boolean toggleSocialPostSave(SocialPost socialPost, User user) {
        validateSocialPost(socialPost);
        validateUser(user);
        validateSocialPostInteractable(socialPost, user);

        boolean currentlySaved = hasSavedSocialPost(socialPost, user);

        if (currentlySaved) {
            socialPost.decrementSaveCount();
        } else {
            socialPost.incrementSaveCount();
        }

        self.executeToggleSocialPostSaveAsync(socialPost.getId(), user.getId());
        // Cache is evicted asynchronously
        return !currentlySaved;
    }

    @org.springframework.scheduling.annotation.Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeToggleSocialPostSaveAsync(Long socialPostId, Long userId) {
        SocialPost socialPost = socialPostRepository.findById(socialPostId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (socialPost == null || user == null) return;

        try {
            Optional<SavedPost> existing = savedPostRepo.findByUserIdAndSocialPost(userId, socialPost);
            if (existing.isPresent()) {
                savedPostRepo.delete(existing.get());
                socialPostRepository.decrementSaveCount(socialPostId);
                fireHligSignal(() -> interestProfileService.onUnsave(userId, socialPostId), "UNSAVE", socialPostId, userId);
                log.info("[Save] Removed (Async): socialPost={} user={}", socialPostId, user.getActualUsername());
            } else {
                SavedPost sp = SavedPost.builder().user(user).socialPost(socialPost).savedAt(new Date()).build();
                savedPostRepo.save(sp);
                socialPostRepository.incrementSaveCount(socialPostId);
                fireHligSignal(() -> interestProfileService.onSave(userId, socialPostId), "SAVE", socialPostId, userId);
                log.info("[Save] Added (Async): socialPost={} user={}", socialPostId, user.getActualUsername());
            }
            evictSocialPostCountsCache(socialPostId);
            broadcastSocialPostStatsUpdate(socialPostId, socialPost.getCommunityId());
        } catch (DataIntegrityViolationException e) {
            log.debug("[Save] Race condition (DB Async): socialPost={} user={}", socialPostId, user.getActualUsername());
        } catch (Exception e) {
            log.error("[Save] Failed Async: socialPost={} user={}", socialPostId, user.getActualUsername(), e);
        }
    }

    // =========================================================================
    // SAVE — GOVERNMENT BROADCAST POST
    // =========================================================================

    public boolean toggleBroadcastPostSave(Post post, User user) {
        validatePost(post);
        validateUser(user);

        if (!post.isEligibleForDisplay()) {
            throw new ValidationException("Cannot save a post with status: " + post.getStatus().getDisplayName());
        }
        if (!post.isGovernmentBroadcast()) {
            throw new ValidationException("Only government broadcast posts can be saved. PostId=" + post.getId() + " is a regular issue post.");
        }

        boolean currentlySaved = hasSavedBroadcastPost(post, user);

        if (currentlySaved) {
            post.decrementSaveCount();
        } else {
            post.incrementSaveCount();
        }

        self.executeToggleBroadcastPostSaveAsync(post.getId(), user.getId());
        // Cache is evicted asynchronously
        return !currentlySaved;
    }

    @org.springframework.scheduling.annotation.Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeToggleBroadcastPostSaveAsync(Long postId, Long userId) {
        Post post = postRepository.findById(postId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (post == null || user == null) return;

        try {
            Optional<SavedPost> existing = savedPostRepo.findByUserIdAndPost(userId, post);
            if (existing.isPresent()) {
                savedPostRepo.delete(existing.get());
                postRepository.decrementSaveCount(postId);
                log.info("[Save] Removed (Async): broadcastPost={} user={}", postId, user.getActualUsername());
            } else {
                SavedPost sp = SavedPost.builder().user(user).post(post).savedAt(new Date()).build();
                savedPostRepo.save(sp);
                postRepository.incrementSaveCount(postId);
                log.info("[Save] Added (Async): broadcastPost={} user={}", postId, user.getActualUsername());
            }
            evictPostCountsCache(postId);
            broadcastPostStatsUpdate(postId);
        } catch (DataIntegrityViolationException e) {
            log.debug("[Save] Race condition (DB Async): broadcastPost={} user={}", postId, user.getActualUsername());
        } catch (Exception e) {
            log.error("[Save] Failed Async: broadcastPost={} user={}", postId, user.getActualUsername(), e);
        }
    }

    // ── Save status checks ────────────────────────────────────────────────────

    public boolean hasSavedSocialPost(SocialPost socialPost, User user) {
        if (socialPost == null || user == null) return false;
        return savedPostRepo.existsByUserIdAndSocialPost(user.getId(), socialPost);
    }

    public boolean hasSavedSocialPostByIds(Long socialPostId, Long userId) {
        if (socialPostId == null || userId == null) return false;
        return savedPostRepo.existsByUser_IdAndSocialPost_Id(userId, socialPostId);
    }

    public boolean hasSavedBroadcastPost(Post post, User user) {
        if (post == null || user == null) return false;
        return savedPostRepo.existsByUserIdAndPost(user.getId(), post);
    }

    // ── Saved post listing ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SavedPostDto> getSavedPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return savedPostRepo.findByUserIdOrderBySavedAtDesc(user.getId(), pageable)
                .map(sp -> convertToDto(sp, user));
    }

    @Transactional(readOnly = true)
    public Page<SavedPostDto> getSavedSocialPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return savedPostRepo.findSocialPostSavesByUserIdOrderBySavedAtDesc(user.getId(), pageable)
                .map(sp -> convertToDto(sp, user));
    }

    @Transactional(readOnly = true)
    public Page<SavedPostDto> getSavedBroadcastPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return savedPostRepo.findBroadcastPostSavesByUserIdOrderBySavedAtDesc(user.getId(), pageable)
                .map(sp -> convertToDto(sp, user));
    }

    private SavedPostDto convertToDto(SavedPost sp, User user) {
        if (sp == null) return null;
        
        String content = "";
        String type = "unknown";
        SocialPostDto socialPostDto = null;
        PostResponse postResponse = null;
        
        if (sp.isSocialPostSave() && sp.getSocialPost() != null) {
            content = sp.getSocialPost().getContent();
            type = "social";
            socialPostDto = socialPostService.convertToDto(sp.getSocialPost(), user);
        } else if (sp.isBroadcastPostSave() && sp.getPost() != null) {
            content = sp.getPost().getContent();
            type = "issue";
            postResponse = postService.convertToPostResponse(sp.getPost(), user);
        }

        return SavedPostDto.builder()
                .id(sp.getId())
                .userId(sp.getUserId())
                .socialPostId(sp.getSocialPostId())
                .postId(sp.getPostId())
                .savedAt(sp.getSavedAt())
                .content(content)
                .type(type)
                .socialPost(socialPostDto)
                .post(postResponse)
                .build();
    }

    // ── Liked post listing ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PostInteractionDto> getLikedSocialPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return postLikeRepository.findBySocialPostNotNullAndUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(like -> convertToInteractionDto(like, "LIKE", "SOCIAL", user));
    }

    @Transactional(readOnly = true)
    public Page<PostInteractionDto> getLikedBroadcastPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return postLikeRepository.findByPostNotNullAndUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(like -> convertToInteractionDto(like, "LIKE", "ISSUE", user));
    }

    // ── Commented post listing ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PostInteractionDto> getCommentedSocialPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return commentRepo.findBySocialPostNotNullAndUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(c -> convertToInteractionDto(c, "COMMENT", "SOCIAL", user));
    }

    @Transactional(readOnly = true)
    public Page<PostInteractionDto> getCommentedBroadcastPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return commentRepo.findByPostNotNullAndUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(c -> convertToInteractionDto(c, "COMMENT", "ISSUE", user));
    }

    private PostInteractionDto convertToInteractionDto(Object entity, String interactionType, String postType, User user) {
        if (entity == null) return null;
        
        PostInteractionDto.PostInteractionDtoBuilder builder = PostInteractionDto.builder()
                .interactionType(interactionType)
                .postType(postType);

        if (entity instanceof PostLike) {
            PostLike like = (PostLike) entity;
            builder.id(like.getId())
                   .createdAt(like.getCreatedAt())
                   .userId(like.getUser().getId())
                   .interactionType(like.isDislike() ? "DISLIKE" : "LIKE");
            if (like.getSocialPost() != null) {
                builder.socialPost(socialPostService.convertToDto(like.getSocialPost(), user));
                builder.content(like.getSocialPost().getContent());
            } else if (like.getPost() != null) {
                builder.post(postService.convertToPostResponse(like.getPost(), user));
                builder.content(like.getPost().getContent());
            }
        } else if (entity instanceof Comment) {
            Comment comment = (Comment) entity;
            builder.id(comment.getId())
                   .createdAt(comment.getCreatedAt())
                   .userId(comment.getUser().getId())
                   .content(comment.getText());
            if (comment.getSocialPost() != null) {
                builder.socialPost(socialPostService.convertToDto(comment.getSocialPost(), user));
            } else if (comment.getPost() != null) {
                builder.post(postService.convertToPostResponse(comment.getPost(), user));
            }
        }

        return builder.build();
    }

    // ── Save counts ───────────────────────────────────────────────────────────

    public long getSaveCountForSocialPost(SocialPost socialPost) {
        if (socialPost == null) return 0L;
        return savedPostRepo.countBySocialPost(socialPost);
    }

    public long getSaveCountForBroadcastPost(Post post) {
        if (post == null) return 0L;
        return savedPostRepo.countByPost(post);
    }

    public long getTotalSavesByUser(User user) {
        if (user == null) return 0L;
        return savedPostRepo.countByUserId(user.getId());
    }

    // =========================================================================
    // SHARE — REGULAR POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordPostShare(Post post, User user, ShareType shareType) {
        validatePost(post);

        if (!post.isEligibleForDisplay()) {
            throw new ValidationException("Cannot share a post with status: " + post.getStatus().getDisplayName());
        }
        if (post.isResolved() || post.getStatus() == PostStatus.RESOLVED) {
            throw new ValidationException(
                    "Cannot share a resolved issue post. PostId=" + post.getId() +
                            " has been marked as resolved and is no longer shareable.");
        }

        try {
            PostShare share = PostShare.builder()
                    .post(post)
                    .user(user)
                    .shareType(shareType != null ? shareType : ShareType.LINK_COPY)
                    .sharedAt(new Date())
                    .build();

            post.incrementShareCount();
            PostShare saved = postShareRepo.save(share);
            postRepository.incrementShareCount(post.getId());
            postRepository.save(post);
            evictPostCountsCache(post.getId());
            broadcastPostStatsUpdate(post.getId());

            log.info("[Share] post={} user={} type={} shareCount={}",
                    post.getId(),
                    user != null ? user.getActualUsername() : "anonymous",
                    share.getShareType(), post.getShareCount());
            return saved;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Share] Failed: post={} user={}", post.getId(), user != null ? user.getActualUsername() : "anonymous", e);
            throw new ServiceException("Failed to record post share: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordPostShare(Post post, User user) {
        return recordPostShare(post, user, ShareType.LINK_COPY);
    }

    // =========================================================================
    // SHARE — SOCIAL POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordSocialPostShare(SocialPost socialPost, User user, ShareType shareType) {
        validateSocialPost(socialPost);

        validateSocialPostInteractable(socialPost, user);

        try {
            PostShare share = PostShare.builder()
                    .socialPost(socialPost)
                    .user(user)
                    .shareType(shareType != null ? shareType : ShareType.LINK_COPY)
                    .sharedAt(new Date())
                    .build();

            socialPost.incrementShareCount();
            PostShare saved = postShareRepo.save(share);
            socialPostRepository.incrementShareCount(socialPost.getId());
            socialPostRepository.save(socialPost);
            evictSocialPostCountsCache(socialPost.getId());
            broadcastSocialPostStatsUpdate(socialPost.getId(), socialPost.getCommunityId());

            if (user != null) {
                fireHligSignal(() -> interestProfileService.onShare(user.getId(), socialPost.getId()),
                        "SHARE", socialPost.getId(), user.getId());
            }

            log.info("[Share] socialPost={} user={} type={} shareCount={}",
                    socialPost.getId(),
                    user != null ? user.getActualUsername() : "anonymous",
                    share.getShareType(), socialPost.getShareCount());
            return saved;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Share] Failed: socialPost={} user={}", socialPost.getId(), user != null ? user.getActualUsername() : "anonymous", e);
            throw new ServiceException("Failed to record social post share: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordSocialPostShare(SocialPost socialPost, User user) {
        return recordSocialPostShare(socialPost, user, ShareType.LINK_COPY);
    }

    // ── Share counts & breakdown ──────────────────────────────────────────────

    public long getShareCountForPost(Post post) {
        if (post == null) return 0L;
        return postShareRepo.countByPost(post);
    }

    public long getShareCountForSocialPost(SocialPost socialPost) {
        if (socialPost == null) return 0L;
        return postShareRepo.countBySocialPost(socialPost);
    }

    public List<Object[]> getShareBreakdownForPost(Post post) {
        if (post == null) return List.of();
        return postShareRepo.countByPostGroupByShareType(post)
                .stream()
                .filter(row -> row[0] != null)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<Object[]> getShareBreakdownForSocialPost(SocialPost socialPost) {
        if (socialPost == null) return List.of();
        return postShareRepo.countBySocialPostGroupByShareType(socialPost)
                .stream()
                .filter(row -> row[0] != null)
                .collect(java.util.stream.Collectors.toList());
    }

    // =========================================================================
    // HLIG v2 — NOT INTERESTED & SCROLLED PAST
    // =========================================================================

    public void markNotInterested(SocialPost socialPost, User user) {
        validateSocialPost(socialPost);
        validateUser(user);
        fireHligSignal(() -> interestProfileService.onNotInterested(user.getId(), socialPost.getId()),
                "NOT_INTERESTED", socialPost.getId(), user.getId());
        log.info("[HLIG] Not-interested: socialPost={} user={}", socialPost.getId(), user.getId());
    }

    public void recordScrollPast(SocialPost socialPost, User user) {
        validateSocialPost(socialPost);
        validateUser(user);
        fireHligSignal(() -> interestProfileService.onScrolledPast(user.getId(), socialPost.getId()),
                "SCROLL_PAST", socialPost.getId(), user.getId());
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public void cleanupForSocialPostDeletion(SocialPost socialPost) {
        if (socialPost == null) return;
        savedPostRepo.deleteAllBySocialPost(socialPost);
        postShareRepo.deleteAllBySocialPost(socialPost);
        log.info("Cleaned up saves and shares for socialPost={}", socialPost.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void cleanupForPostDeletion(Post post) {
        if (post == null) return;
        postShareRepo.deleteAllByPost(post);
        savedPostRepo.deleteAllByPost(post);
        log.info("Cleaned up shares and saves for post={}", post.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void cleanupForUserDeletion(User user) {
        if (user == null) return;
        savedPostRepo.deleteAllByUserId(user.getId());
        postShareRepo.deleteAllByUserId(user.getId());
        log.info("Cleaned up saves and shares for user={}", user.getActualUsername());
    }

    // =========================================================================
    // ById ENTRY POINTS
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostView recordSocialPostViewById(Long socialPostId, User user) {
        SocialPost sp = requireSocialPost(socialPostId);
        return recordSocialPostView(sp, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean likeSocialPostById(Long socialPostId, User user) {
        SocialPost sp = requireSocialPost(socialPostId);
        return likeSocialPost(sp, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean dislikeSocialPostById(Long socialPostId, User user) {
        SocialPost sp = requireSocialPost(socialPostId);
        return dislikeSocialPost(sp, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleSocialPostSaveById(Long socialPostId, User user) {
        SocialPost sp = requireSocialPost(socialPostId);
        return toggleSocialPostSave(sp, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordSocialPostShareById(Long socialPostId, User user, ShareType shareType) {
        SocialPost sp = requireSocialPost(socialPostId);
        return recordSocialPostShare(sp, user, shareType);
    }

    @Transactional(rollbackFor = Exception.class)
    public PostView recordPostViewById(Long postId, User user) {
        Post post = requirePost(postId);
        return recordPostView(post, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean likePostById(Long postId, User user) {
        Post post = requirePost(postId);
        return likePost(post, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean dislikePostById(Long postId, User user) {
        Post post = requirePost(postId);
        return dislikePost(post, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleBroadcastPostSaveById(Long postId, User user) {
        Post post = requirePost(postId);
        return toggleBroadcastPostSave(post, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordPostShareById(Long postId, User user, ShareType shareType) {
        Post post = requirePost(postId);
        return recordPostShare(post, user, shareType);
    }

    @Transactional(readOnly = true)
    public SocialPost getSocialPostById(Long id) {
        return requireSocialPost(id);
    }

    @Transactional(readOnly = true)
    public Post getPostById(Long id) {
        return requirePost(id);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Date dedupeThreshold() {
        return new Date(System.currentTimeMillis() - Constant.VIEW_DUPLICATE_PREVENTION_MILLIS);
    }

    private PostLike buildLike(Post post, SocialPost socialPost, User user, PostLike.ReactionType type) {
        PostLike like = new PostLike();
        like.setPost(post);
        like.setSocialPost(socialPost);
        like.setUser(user);
        like.setReactionType(type);
        like.setCreatedAt(new Date());
        return like;
    }

    private void validatePostInteractable(Post post) {
        PostStatus status = post.getStatus();
        if (status == null) {
            throw new ValidationException("Post has no status — cannot interact.");
        }
        if (!status.isInteractable()) {
            throw new ValidationException("Post does not allow reactions in its current status: " + status.getDisplayName());
        }
    }

    private void validateSocialPostInteractable(SocialPost socialPost, User user) {
        if (socialPost.getCommunityStatus() != null && !"ACTIVE".equals(socialPost.getCommunityStatus())) {
            throw new ValidationException("Cannot interact with a post in an archived or deleted community.");
        }
        if (!socialPost.isEligibleForDisplay()) {
            throw new ValidationException("Cannot interact with social post in status: " + socialPost.getStatus().getDisplayName());
        }
        if (!socialPost.getStatus().allowsLikes()) {
            throw new ValidationException("Social post does not allow reactions in its current status.");
        }

        if (socialPost.getCommunityId() != null && user != null && !com.JanSahayak.AI.payload.PostUtility.isAdmin(user)) {
            String privacy = socialPost.getCommunityPrivacy();
            if ("PRIVATE".equalsIgnoreCase(privacy) || "SECRET".equalsIgnoreCase(privacy)) {
                if (!communityService.isMember(socialPost.getCommunityId(), user.getId())) {
                    throw new SecurityException("User does not have permission to interact with this private community post");
                }
            }
        }
    }

    private void validateUser(User user) {
        if (user == null)         throw new ValidationException("User cannot be null");
        if (user.getId() == null) throw new ValidationException("User must be persisted (id is null)");
    }

    private void validatePost(Post post) {
        if (post == null)         throw new ValidationException("Post cannot be null");
        if (post.getId() == null) throw new ValidationException("Post must be persisted (id is null)");
    }

    private void validateSocialPost(SocialPost sp) {
        if (sp == null)         throw new ValidationException("SocialPost cannot be null");
        if (sp.getId() == null) throw new ValidationException("SocialPost must be persisted (id is null)");
    }

    private void evictHligFeedCache(Long userId) {
        if (userId == null) return;
        try {
            org.springframework.cache.Cache cache = cacheManager.getCache("hlig_feed");
            if (cache != null) {
                cache.evict(userId + "_HOT");
                cache.evict(userId + "_NEW");
                cache.evict(userId + "_TOP");
                log.debug("[HLIG] Evicted personalised hlig_feed cache for userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("[HLIG] Failed to evict hlig_feed cache for userId={}: {}", userId, e.getMessage());
        }
    }

    private void fireHligSignal(Runnable signal, String signalType, Long postId, Long userId) {
        try {
            signal.run();
            evictHligFeedCache(userId);
        } catch (Exception e) {
            log.warn("[HLIG] {} signal dropped: postId={} userId={} reason={}",
                    signalType, postId, userId, e.getMessage());
        }
    }

    private SocialPost requireSocialPost(Long id) {
        return socialPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SocialPost not found with id: " + id));
    }

    private Post requirePost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + id));
    }
}
