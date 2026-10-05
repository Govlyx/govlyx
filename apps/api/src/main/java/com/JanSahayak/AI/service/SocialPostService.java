package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.dto.SocialPostCreateDto;
import com.JanSahayak.AI.dto.SocialPostDto;
import com.JanSahayak.AI.dto.SocialPostUpdateDto;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.FeedScope;
import com.JanSahayak.AI.enums.FeedSort;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.PostNotFoundException;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.model.PostShare.ShareType;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.payload.SocialPostUtility;
import com.JanSahayak.AI.repository.PollRepository;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.PollVoteRepository;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialPostService {


    private final SocialPostRepo           socialPostRepository;
    private final UserRepo                 userRepository;
    private final PollRepository           pollRepository;
    private final PollVoteRepository       pollVoteRepository;
    private final CommunityMemberRepo      communityMemberRepository;
    private final CommunityRepo            communityRepository;


    private final ContentValidationService contentValidationService;
    private final SocialPostMediaService   mediaService;
    private final PostInteractionService   postInteractionService;
    private final NotificationService      notificationService;
    private final CommunityMemberRepo communityMemberRepo;
    private final TranslationService       translationService;
    private final TopicAggregationWorker   topicAggregationWorker;
    private final com.JanSahayak.AI.repository.PincodeLookupRepo pincodeLookupRepo;

    @Lazy
    @Autowired
    private CommunityService communityService;


    @Lazy
    @Autowired
    private InterestProfileService interestProfileService;

    @Lazy
    @Autowired
    private HLIGFeedService hligFeedService;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private com.JanSahayak.AI.repository.PostRepo postRepo;

    @Lazy
    @Autowired
    private com.JanSahayak.AI.security.IdentityBlindService identityBlindService;

    @Lazy
    @Autowired
    private com.JanSahayak.AI.repository.ActorProfileRepo actorProfileRepo;


    public String resolveActorToken(User user) {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs != null && attrs.getRequest() != null) {
                String headerToken = attrs.getRequest().getHeader("X-Actor-Token");
                if (headerToken != null && !headerToken.isBlank()) {
                    return headerToken.trim();
                }
            }
        } catch (Exception ignored) {}

        if (identityBlindService != null && user != null) {
            return identityBlindService.resolveActorTokenForUser(user);
        }
        return null;
    }

    // =========================================================================
    // COUNT
    // =========================================================================

    private final java.util.Map<String, List<String>> nearbyPincodesCache = new java.util.concurrent.ConcurrentHashMap<>();

    public Long countSocialPostsByUserId(Long userId) {
        return socialPostRepository.countByUserIdAndStatus(userId, PostStatus.ACTIVE);
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * Create a text-only social post (no media).
     */
    @Transactional(rollbackFor = Exception.class)
    public SocialPostDto createTextPost(SocialPostCreateDto createDto, User user) {
        return createSocialPost(createDto, Collections.emptyList(), user);
    }

    /**
     * Create a new social post with optional media files and user tagging.
     */
    @Transactional(rollbackFor = Exception.class)
    public SocialPostDto createSocialPost(
            SocialPostCreateDto createDto,
            List<MultipartFile> mediaFiles,
            User user) {
        try {
            PostUtility.validateUser(user);
            SocialPostUtility.validateSocialPostContent(createDto.getContent());
            String safeContent = contentValidationService.sanitizeAndValidateContent(createDto.getContent());
            createDto.setContent(safeContent);

            List<String> extractedHashtags = extractAndValidateHashtags(
                    createDto.getContent(), createDto.getHashtags());
            List<Long> mentionedUserIds = extractAndValidateMentions(
                    createDto.getContent(), createDto.getMentionedUserIds());

            List<String> uploadedMediaUrls = new ArrayList<>();
            if (mediaFiles != null && !mediaFiles.isEmpty()) {
                uploadedMediaUrls = uploadMediaFilesWithValidation(mediaFiles, user.getId());
            }

            String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();
            if (idempotencyKey != null) {
                java.util.Optional<SocialPost> existingPost = socialPostRepository.findByIdempotencyKey(idempotencyKey);
                if (existingPost.isPresent()) {
                    log.info("Idempotency hit: Returning existing SocialPost for key {}", idempotencyKey);
                    return convertToDto(existingPost.get(), user);
                }
            }

            SocialPost socialPost = buildSocialPost(
                    createDto, user, extractedHashtags, mentionedUserIds, uploadedMediaUrls);
            socialPost.setIdempotencyKey(idempotencyKey);
            socialPost.setIpAddress(com.JanSahayak.AI.util.IpUtils.getClientIpFromContext());
            SocialPost savedPost = socialPostRepository.save(socialPost);

            // HLIG v2: post creation is the strongest interest signal (+5.0 weight).
            try {
                if (user != null && user.getId() != null) {
                    interestProfileService.onPostCreated(user.getId(), savedPost.getId());
                }
                // HLIG v4: trigger asynchronous folksonomy candidate extraction
                topicAggregationWorker.processPostAsync(savedPost);
            } catch (Exception e) {
                log.warn("[HLIG] onPostCreated failed: postId={} reason={}", savedPost.getId(), e.getMessage());
            }

            if (savedPost.getCommunityId() != null && savedPost.getStatus() == PostStatus.ACTIVE) {
                try {
                    communityService.onPostPublished(savedPost, savedPost.getCommunityId());
                    
                    try {
                        String communityName = communityService.findCommunityForPost(savedPost.getCommunityId(), user)
                                .map(com.JanSahayak.AI.model.Community::getName)
                                .orElse("your community");
                                
                        List<Long> memberUserIds = communityMemberRepo.findActiveMemberUserIds(savedPost.getCommunityId());
                        List<Long> targetUserIds = memberUserIds.stream()
                                .filter(id -> !id.equals(user.getId()))
                                .collect(Collectors.toList());
                                
                        if (!targetUserIds.isEmpty()) {
                            notificationService.notifyCommunityNewPost(savedPost, targetUserIds, communityName);
                        }
                    } catch (Exception ne) {
                        log.warn("Failed to schedule community new post notifications for post={}: {}", savedPost.getId(), ne.getMessage());
                    }
                    
                } catch (Exception e) {
                    log.warn("[Community] onPostPublished failed for post={} community={}: {}",
                            savedPost.getId(), savedPost.getCommunityId(), e.getMessage());
                }
            }

            // --- Feed Refresh Push (fire-and-forget WebSocket signal) ---
            // isEligibleForDisplay() checks: status==ACTIVE, !isFlagged, user active.
            // This covers both plain posts and community posts so any live client
            // watching the global feed topic will get notified.
            try {
                // Removed global feed.new-post to prevent phantom banners on unrelated tabs
                if (savedPost.isEligibleForDisplay()) {
                    simpMessagingTemplate.convertAndSend("/topic/feed.new-post",
                            java.util.Map.of("postId", savedPost.getId(), "scope", "ALL"));
                }
                if (savedPost.getCommunityId() != null) {
                    simpMessagingTemplate.convertAndSend("/topic/community." + savedPost.getCommunityId() + ".new-post",
                            java.util.Map.of("postId", savedPost.getId()));
                }
            } catch (Exception e) {
                log.warn("Failed to push feed refresh signal for post {}: {}", savedPost.getId(), e.getMessage());
            }

            log.info("Social post created: ID={} User={} Media={} Hashtags={} Mentions={}",
                    savedPost.getId(), user.getActualUsername(), savedPost.getMediaCount(),
                    savedPost.getHashtagCount(), savedPost.getMentionCount());

            return convertToDto(savedPost, user);

        } catch (ValidationException | IllegalArgumentException | SecurityException e) {
            // Propagate input / permission errors directly so the client sees the
            // real reason (e.g. "You must be a member of this community to post in it.")
            cleanupMediaFiles(null);
            throw e;
        } catch (Exception e) {
            log.error("Failed to create social post for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to create social post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public SocialPostDto updateSocialPost(Long postId, SocialPostUpdateDto updateDto, User user) {
        try {
            SocialPostUtility.validateSocialPostId(postId);
            PostUtility.validateUser(user);
            SocialPostUtility.validateSocialPostContent(updateDto.getContent());

            SocialPost socialPost = findById(postId);

            String actorToken = resolveActorToken(user);
            if (!SocialPostUtility.canUserModifySocialPost(socialPost, user, actorToken)) {
                throw new SecurityException(
                        "User does not have permission to modify this social post");
            }

            String safeContent = contentValidationService.sanitizeAndValidateContent(updateDto.getContent());
            updateDto.setContent(safeContent);
            socialPost.setContent(updateDto.getContent().trim());

            if (updateDto.getHashtags() != null) {
                List<String> processedHashtags = extractAndValidateHashtags(
                        updateDto.getContent(), updateDto.getHashtags());
                socialPost.setHashtagsList(processedHashtags);
            }

            if (updateDto.getAllowComments() != null) {
                socialPost.setAllowComments(updateDto.getAllowComments());
            }

            SocialPost updatedPost = socialPostRepository.save(socialPost);
            log.info("Social post updated: ID={} User={}", postId, user.getActualUsername());
            return convertToDto(updatedPost, user);

        } catch (ValidationException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update social post {} by user: {}", postId, user.getActualUsername(), e);
            throw new ServiceException("Failed to update social post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public void deleteSocialPost(Long postId, User user) {
        try {
            SocialPostUtility.validateSocialPostId(postId);
            PostUtility.validateUser(user);

            SocialPost socialPost = findById(postId);

            String actorToken = resolveActorToken(user);
            if (!SocialPostUtility.canUserDeleteSocialPost(socialPost, user, actorToken)) {
                throw new SecurityException(
                        "User does not have permission to delete this social post");
            }

            if (socialPost.hasMedia()) {
                cleanupMediaFiles(socialPost.getMediaUrlsList());
            }

            try {
                postInteractionService.cleanupForSocialPostDeletion(socialPost);
            } catch (Exception e) {
                log.warn("Failed to clean up interactions for socialPost={}: {}",
                        postId, e.getMessage());
            }

            try {
                notificationService.deleteNotificationsForPost(postId, true);
            } catch (Exception e) {
                log.warn("Failed to clean up notifications for socialPost={}: {}", postId, e.getMessage());
            }

            Long communityId = socialPost.getCommunityId();
            boolean wasActive = (socialPost.getStatus() == PostStatus.ACTIVE);
            socialPost.softDelete();
            socialPostRepository.save(socialPost);

            if (communityId != null && wasActive) {
                try {
                    communityService.onPostDeleted(communityId);
                } catch (Exception e) {
                    log.warn("[Community] onPostDeleted failed for post={} community={}: {}",
                            postId, communityId, e.getMessage());
                }
            }

            log.info("Social post soft-deleted: ID={} Media={} User={}",
                    postId, socialPost.getMediaCount(), user.getActualUsername());

        } catch (ValidationException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete social post {} by user: {}", postId, user.getActualUsername(), e);
            throw new ServiceException("Failed to delete social post: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCommunityPost(Long communityId, Long postId, User user) {
        try {
            SocialPostUtility.validateSocialPostId(postId);
            PostUtility.validateUser(user);
            if (communityId == null || communityId <= 0) {
                throw new ValidationException("Invalid community ID");
            }

            SocialPost socialPost = findById(postId);

            if (socialPost.getStatus() == PostStatus.DELETED || socialPost.getStatus() == PostStatus.FLAGGED) {
                throw new PostNotFoundException("Social post not found with ID: " + postId);
            }

            if (socialPost.getCommunityId() == null || !socialPost.getCommunityId().equals(communityId)) {
                throw new ValidationException("Post does not belong to this community");
            }

            String actorToken = resolveActorToken(user);
            boolean isAuthorOrAdmin = SocialPostUtility.canUserDeleteSocialPost(socialPost, user, actorToken);
            boolean isCommunityMod = communityService.isModeratorOrAbove(communityId, user.getId());

            if (!isAuthorOrAdmin && !isCommunityMod) {
                throw new SecurityException("User does not have permission to delete this community post");
            }

            if (!isAuthorOrAdmin && isCommunityMod) {
                log.info("Community moderator/admin (userId={}) deleting post (postId={}) by author (userId={})",
                        user.getId(), postId, socialPost.getUser() != null ? socialPost.getUser().getId() : "null");
            }

            if (socialPost.hasMedia()) {
                cleanupMediaFiles(socialPost.getMediaUrlsList());
            }

            try {
                postInteractionService.cleanupForSocialPostDeletion(socialPost);
            } catch (Exception e) {
                log.warn("Failed to clean up interactions for socialPost={}: {}", postId, e.getMessage());
            }

            try {
                notificationService.deleteNotificationsForPost(postId, true);
            } catch (Exception e) {
                log.warn("Failed to clean up notifications for socialPost={}: {}", postId, e.getMessage());
            }

            boolean wasActive = (socialPost.getStatus() == PostStatus.ACTIVE);
            socialPost.softDelete();
            socialPostRepository.save(socialPost);

            try {
                if (wasActive) {
                    communityService.onPostDeleted(communityId);
                }
            } catch (Exception e) {
                log.warn("[Community] onPostDeleted failed for post={} community={}: {}", postId, communityId, e.getMessage());
            }

            log.info("Community post soft-deleted: ID={} Media={} User={}",
                    postId, socialPost.getMediaCount(), user.getActualUsername());

        } catch (ValidationException | SecurityException | PostNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete community post {} by user: {}", postId, user.getActualUsername(), e);
            throw new ServiceException("Failed to delete community post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // GET SINGLE POST
    // =========================================================================

    @Transactional(readOnly = true)
    public SocialPostDto getSocialPostById(Long postId, User user) {
        try {
            SocialPostUtility.validateSocialPostId(postId);
            SocialPost socialPost = findById(postId);
            if (socialPost.getStatus() == PostStatus.DELETED || socialPost.getStatus() == PostStatus.FLAGGED) {
                log.debug("[Access] Social post={} is {} — access denied", postId, socialPost.getStatus());
                throw new PostNotFoundException("Social post not found with ID: " + postId);
            }
            if (socialPost.getStatus() == PostStatus.TAKEN_DOWN) {
                log.debug("[Access] Taken down social post={} denied", postId);
                throw new com.JanSahayak.AI.exception.ContentTakenDownException("This content has been removed due to a legal or copyright claim.");
            }


            if (socialPost.getCommunityId() != null && (user == null || !PostUtility.isAdmin(user))) {
                String privacy = socialPost.getCommunityPrivacy();
                if (privacy == null && socialPost.getCommunity() != null && socialPost.getCommunity().getPrivacy() != null) {
                    privacy = socialPost.getCommunity().getPrivacy().name();
                }
                if ("PRIVATE".equalsIgnoreCase(privacy) || "SECRET".equalsIgnoreCase(privacy)) {
                    if (user == null || !communityMemberRepository.existsByCommunityIdAndUserIdAndIsActiveTrue(
                            socialPost.getCommunityId(), user.getId())) {
                        throw new SecurityException("User does not have permission to view this private community post");
                    }
                }
            }

            return convertToDto(socialPost, user);

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get social post {}", postId, e);
            throw new ServiceException("Failed to retrieve social post: " + e.getMessage(), e);
        }
    }

    public SocialPost findById(Long postId) {
        return socialPostRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(
                        "Social post not found with ID: " + postId));
    }

    // =========================================================================
    // SAVE FEATURE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleSave(Long postId, User user) {
        PostUtility.validateUser(user);
        SocialPostUtility.validateSocialPostId(postId);
        SocialPost socialPost = findById(postId);
        boolean isSaved = postInteractionService.toggleSocialPostSave(socialPost, user);
        log.info("Toggle save: postId={} userId={} isSaved={}", postId, user.getId(), isSaved);
        return isSaved;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getSavedPosts(User user, Long beforeId, Integer limit) {
        PostUtility.validateUser(user);
        int validLimit = PaginationUtils.validateLimit(limit);

        try {
            // PostInteractionService.getSavedSocialPostsForUser returns Page<SavedPostDto>
            Page<com.JanSahayak.AI.dto.SavedPostDto> page =
                    postInteractionService.getSavedSocialPostsForUser(user, resolveActorToken(user), 0, validLimit);

            // Collect social post IDs from the DTO page
            List<Long> socialPostIds = page.getContent().stream()
                    .filter(dto -> dto.getSocialPostId() != null)
                    .map(com.JanSahayak.AI.dto.SavedPostDto::getSocialPostId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // Batch-fetch the actual SocialPost entities and convert to DTOs
            List<SocialPost> activeSavedPosts = socialPostIds.isEmpty()
                    ? Collections.emptyList()
                    : socialPostRepository.findAllById(socialPostIds).stream()
                            .filter(sp -> sp.getStatus() == PostStatus.ACTIVE)
                            .collect(Collectors.toList());
            List<SocialPostDto> dtos = convertToDtoBatch(activeSavedPosts, user);

            boolean hasMore    = page.hasNext();
            Long    nextCursor = hasMore && !dtos.isEmpty()
                    ? dtos.get(dtos.size() - 1).getId()
                    : null;

            return PaginatedResponse.of(dtos, hasMore, nextCursor, validLimit);

        } catch (Exception e) {
            log.error("Failed to get saved posts for user {}", user.getActualUsername(), e);
            return PaginationUtils.createEmptyResponse(validLimit);
        }
    }

    public boolean isSavedByUser(Long postId, User user) {
        if (user == null || postId == null) return false;
        return postInteractionService.hasSavedSocialPostByIds(postId, user.getId(), resolveActorToken(user));
    }

    // =========================================================================
    // SHARE FEATURE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordShare(Long postId, User user, ShareType shareType) {
        SocialPostUtility.validateSocialPostId(postId);
        SocialPost socialPost = findById(postId);
        PostShare share = postInteractionService.recordSocialPostShare(socialPost, user, shareType);
        log.info("Share recorded: socialPostId={} userId={} type={}",
                postId, user != null ? user.getId() : "anon", shareType);
        return share;
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordShare(Long postId, User user) {
        return recordShare(postId, user, ShareType.LINK_COPY);
    }

    public long getShareCount(Long postId) {
        if (postId == null) return 0L;
        Integer shareCount = socialPostRepository.findShareCountById(postId);
        return shareCount != null ? shareCount : 0L;
    }

    // =========================================================================
    // LEGACY FEED / LISTING (pre-HLIG, kept for SocialPostController)
    // =========================================================================

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getHomeFeed(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupSocialPostFeedPagination(
                    "getHomeFeed", beforeId, limit);

            String userStatePrefix    = user.hasPincode() ? user.getStatePrefix()    : null;
            String userDistrictPrefix = user.hasPincode() ? user.getDistrictPrefix() : null;

            List<SocialPost> posts = fetchRecommendedPosts(
                    userStatePrefix, userDistrictPrefix, setup);
            List<SocialPostDto> postDtos = convertToDtoBatch(posts, user);

            PaginatedResponse<SocialPostDto> response = PaginationUtils
                    .createSocialPostDtoResponse(postDtos, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getHomeFeed", postDtos,
                    response.isHasMore(), response.getNextCursor());
            return response;

        } catch (DataAccessException e) {
            log.error("Database error in getHomeFeed for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to retrieve home feed due to database error", e);
        } catch (Exception e) {
            log.error("Error getting home feed for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to retrieve home feed", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getTrendingPosts(
            User user, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupTrendingPostsPagination(
                    "getTrendingPosts", beforeId, limit);

            List<SocialPost> posts = fetchTrendingPosts(setup);

            List<SocialPostDto> postDtos = user != null
                    ? convertToDtoBatch(posts, user)
                    : convertToDtoSimple(posts);

            PaginatedResponse<SocialPostDto> response = PaginationUtils
                    .createSocialPostDtoResponse(postDtos, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getTrendingPosts", postDtos,
                    response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception e) {
            log.error("Error getting trending posts", e);
            throw new ServiceException("Failed to retrieve trending posts", e);
        }
    }

    /**
     * Local posts feed — geographic pool sorted by engagement, scoped to the
     * user's pincode / district / state waterfall.
     *
     * Called by SocialPostController GET /api/social-posts/feed/local.
     * Delegates to getBrowseFeed(LOCATION, NEW) so it inherits the full
     * geo waterfall, own-post injection, sparse widening, and never-empty guarantees.
     *
     * Uses LOCATION + NEW (chronological) as the default sort for local posts
     * because locality is the primary filter — users browsing "local" care most
     * about what is happening right now near them, not what is trending nationally.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getLocalPosts(
            User user, Long beforeId, Integer limit) {
        int size = (limit != null && limit > 0 && limit <= Constant.MAX_PAGE_SIZE)
                ? limit : Constant.DEFAULT_PAGE_SIZE;
        return getBrowseFeed(user, FeedScope.LOCATION, FeedSort.NEW, beforeId, size);
    }

    // =========================================================================
    // NEIGHBORHOOD Q&A FEED
    // =========================================================================

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getNeighborhoodQAFeed(User user, String scope, Long lastPostId, int size) {
        return getNeighborhoodQAFeed(user, scope, FeedSort.NEW, lastPostId, size);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getNeighborhoodQAFeed(User user, String scope, FeedSort sort, Long lastPostId, int size) {
        try {
            if (sort == null) {
                sort = FeedSort.HOT;
            }
            int validSize = Math.min(Math.max(size, 1), 50);
            int candidateLimit = Math.max(100, validSize * 4);
            org.springframework.data.domain.Pageable candidatePage = org.springframework.data.domain.PageRequest.of(
                    0, candidateLimit, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
            List<SocialPost> posts = new ArrayList<>();
            com.JanSahayak.AI.enums.SocialPostCategory category = com.JanSahayak.AI.enums.SocialPostCategory.NEIGHBORHOOD_QUESTION;

            String userPincode = user.getPincode();
            PincodeLookup pinData = null;
            if (userPincode != null) {
                pinData = pincodeLookupRepo.findById(userPincode).orElse(null);
            }

            boolean isFallbackResponse = false;

            if ("AREA".equalsIgnoreCase(scope)) {
                if (pinData != null && pinData.getCity() != null && !pinData.getCity().isBlank()) {
                    posts = socialPostRepository.findQAPostsByCity(category, pinData.getCity(), PostStatus.ACTIVE, candidatePage);
                }
                if (posts.isEmpty() && pinData != null && pinData.getDistrict() != null) {
                    log.debug("[QA-Feed] AREA scope: falling back to DISTRICT '{}'", pinData.getDistrict());
                    posts = socialPostRepository.findQAPostsByRealDistrict(category, pinData.getDistrict(), PostStatus.ACTIVE, candidatePage);
                }
            } else if ("CITY".equalsIgnoreCase(scope)) {
                if (pinData != null && pinData.getUrbanClusterId() != null) {
                    posts = socialPostRepository.findQAPostsByUrbanCluster(category, pinData.getUrbanClusterId(), PostStatus.ACTIVE, candidatePage);
                }
                if (posts.isEmpty() && pinData != null && pinData.getCity() != null && !pinData.getCity().isBlank()) {
                    log.debug("[QA-Feed] CITY scope: falling back to city '{}'", pinData.getCity());
                    posts = socialPostRepository.findQAPostsByCity(category, pinData.getCity(), PostStatus.ACTIVE, candidatePage);
                }
                if (posts.isEmpty() && pinData != null && pinData.getDistrict() != null) {
                    log.debug("[QA-Feed] CITY scope: falling back to district '{}'", pinData.getDistrict());
                    posts = socialPostRepository.findQAPostsByRealDistrict(category, pinData.getDistrict(), PostStatus.ACTIVE, candidatePage);
                }
            } else if ("DISTRICT".equalsIgnoreCase(scope)) {
                if (pinData != null && pinData.getDistrict() != null) {
                    posts = socialPostRepository.findQAPostsByRealDistrict(category, pinData.getDistrict(), PostStatus.ACTIVE, candidatePage);
                } else if (user.getDistrictPrefix() != null) {
                    posts = socialPostRepository.findQAPostsByRealDistrict(category, user.getDistrictPrefix(), PostStatus.ACTIVE, candidatePage);
                }
            } else {
                // Default to NEARBY
                java.math.BigDecimal lat = user.getHomeLatitude();
                java.math.BigDecimal lon = user.getHomeLongitude();
                
                boolean foundNearbyPosts = false;
                if (lat != null && lon != null) {
                    double[] radii = {5.0, 15.0, 30.0};
                    for (double radius : radii) {
                        double latOffset = radius / 111.0;
                        double lonOffset = radius / (111.0 * Math.cos(Math.toRadians(lat.doubleValue())));
                        
                        java.math.BigDecimal minLat = java.math.BigDecimal.valueOf(lat.doubleValue() - latOffset);
                        java.math.BigDecimal maxLat = java.math.BigDecimal.valueOf(lat.doubleValue() + latOffset);
                        java.math.BigDecimal minLng = java.math.BigDecimal.valueOf(lon.doubleValue() - lonOffset);
                        java.math.BigDecimal maxLng = java.math.BigDecimal.valueOf(lon.doubleValue() + lonOffset);
                        
                        posts = socialPostRepository.findNearbyPostsUsingGPS(
                            lat, lon, minLat, maxLat, minLng, maxLng, category.name(), radius, userPincode
                        );
                        
                        if (!posts.isEmpty()) {
                            log.debug("[QA-Feed] Found {} posts at radius {}km", posts.size(), radius);
                            foundNearbyPosts = true;
                            break;
                        }
                    }
                }

                if (!foundNearbyPosts && pinData != null) {
                    if (pinData.getCity() != null && !pinData.getCity().isBlank()) {
                        log.debug("[QA-Feed] NEARBY fallback: using city '{}'", pinData.getCity());
                        posts = socialPostRepository.findQAPostsByCity(category, pinData.getCity(), PostStatus.ACTIVE, candidatePage);
                    } else if (pinData.getDistrict() != null) {
                        log.debug("[QA-Feed] NEARBY fallback: using district '{}'", pinData.getDistrict());
                        posts = socialPostRepository.findQAPostsByRealDistrict(category, pinData.getDistrict(), PostStatus.ACTIVE, candidatePage);
                    }
                    isFallbackResponse = true;
                }
            }

            // Deduplicate and filter active
            List<SocialPost> activePosts = posts.stream()
                    .filter(p -> p != null && p.getStatus() == PostStatus.ACTIVE && !Boolean.TRUE.equals(p.getIsFlagged()))
                    .distinct()
                    .collect(Collectors.toList());

            // Apply unified sorting across all scopes
            List<SocialPost> sortedPosts = applyQASort(activePosts, sort);

            // Apply cursor-based pagination
            List<SocialPost> pagedPosts = applyCursorWindow(sortedPosts, lastPostId, validSize);

            boolean hasMore = false;
            if (!sortedPosts.isEmpty()) {
                if (lastPostId == null) {
                    hasMore = sortedPosts.size() > validSize;
                } else {
                    int cursorIdx = -1;
                    for (int i = 0; i < sortedPosts.size(); i++) {
                        if (Objects.equals(sortedPosts.get(i).getId(), lastPostId)) {
                            cursorIdx = i;
                            break;
                        }
                    }
                    if (cursorIdx != -1) {
                        hasMore = (cursorIdx + 1 + validSize) < sortedPosts.size();
                    }
                }
            }

            Long nextCursor = pagedPosts.isEmpty() ? null : pagedPosts.get(pagedPosts.size() - 1).getId();
            List<SocialPostDto> postDtos = convertToDtoBatch(pagedPosts, user);

            return PaginatedResponse.of(postDtos, hasMore, nextCursor, validSize, isFallbackResponse);
        } catch (Exception e) {
            log.error("Error getting Neighborhood QA feed", e);
            throw new com.JanSahayak.AI.exception.ServiceException("Failed to retrieve QA feed", e);
        }
    }

    private List<SocialPost> applyQASort(List<SocialPost> candidates, FeedSort sort) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        if (sort == null || sort == FeedSort.NEW) {
            return candidates.stream()
                    .sorted(Comparator.comparingLong((SocialPost p) -> p.getCreatedAt() != null ? -p.getCreatedAt().getTime() : 0L))
                    .collect(Collectors.toList());
        } else if (sort == FeedSort.TOP) {
            return candidates.stream()
                    .sorted(Comparator
                            .comparingDouble((SocialPost p) -> {
                                if (p.getEngagementScore() != null && p.getEngagementScore() > 0.0) {
                                    return -p.getEngagementScore();
                                }
                                int likes = p.getLikeCount() != null ? p.getLikeCount() : 0;
                                int comments = p.getCommentCount() != null ? p.getCommentCount() : 0;
                                int shares = p.getShareCount() != null ? p.getShareCount() : 0;
                                return -(likes * 2.0 + comments * 3.0 + shares * 1.5);
                            })
                            .thenComparingLong(p -> -(p.getCreatedAt() != null ? p.getCreatedAt().getTime() : 0L)))
                    .collect(Collectors.toList());
        } else {
            // FeedSort.HOT: viralityScore DESC within 72h -> 7d -> all
            long now72hAgo = System.currentTimeMillis() - (72L * 60 * 60 * 1000);
            long now7dAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            Comparator<SocialPost> hotComp = Comparator
                    .comparingDouble((SocialPost p) -> -(p.getViralityScore() != null ? p.getViralityScore() : 0.0))
                    .thenComparingLong(p -> -(p.getCreatedAt() != null ? p.getCreatedAt().getTime() : 0L));

            List<SocialPost> window72h = candidates.stream()
                    .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().getTime() >= now72hAgo)
                    .sorted(hotComp)
                    .collect(Collectors.toList());
            if (window72h.size() >= 5) {
                return window72h;
            }

            List<SocialPost> window7d = candidates.stream()
                    .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().getTime() >= now7dAgo)
                    .sorted(hotComp)
                    .collect(Collectors.toList());
            if (window7d.size() >= 5) {
                return window7d;
            }

            return candidates.stream().sorted(hotComp).collect(Collectors.toList());
        }
    }

    private List<SocialPost> applyCursorWindow(List<SocialPost> sorted, Long lastPostId, int size) {
        if (sorted == null || sorted.isEmpty()) return Collections.emptyList();
        if (lastPostId == null) return sorted.stream().limit(size).collect(Collectors.toList());

        int cursorIdx = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (Objects.equals(sorted.get(i).getId(), lastPostId)) {
                cursorIdx = i;
                break;
            }
        }

        if (cursorIdx == -1) {
            return sorted.stream().limit(size).collect(Collectors.toList());
        }

        int fromIdx = Math.min(cursorIdx + 1, sorted.size());
        return sorted.subList(fromIdx, sorted.size()).stream().limit(size).collect(Collectors.toList());
    }

    public void syncGPSFromPincode(User user) {
        if (user.getPincode() != null) {
            pincodeLookupRepo.findById(user.getPincode()).ifPresent(pin -> {
                if (pin.getLatitude() != null && pin.getLongitude() != null) {
                    user.setHomeLatitude(pin.getLatitude());
                    user.setHomeLongitude(pin.getLongitude());
                    log.info("Synced GPS coordinates for user {} from pincode {}", user.getId(), user.getPincode());
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getUserPosts(
            Long userId, User currentUser, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUserId(userId);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupSocialPostFeedPagination(
                    "getUserPosts", beforeId, limit);

            List<SocialPost> posts = fetchUserPosts(userId, setup);

            List<SocialPost> activeUserPosts = posts.stream()
                    .filter(post -> post.getStatus() == PostStatus.ACTIVE)
                    .collect(Collectors.toList());

            List<SocialPostDto> postDtos = currentUser != null
                    ? convertToDtoBatch(activeUserPosts, currentUser)
                    : convertToDtoSimple(activeUserPosts);

            PaginatedResponse<SocialPostDto> response = PaginationUtils
                    .createSocialPostDtoResponse(postDtos, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getUserPosts", postDtos,
                    response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception e) {
            log.error("Error getting posts for user: {}", userId, e);
            throw new ServiceException("Failed to retrieve user posts", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getUserPostsByActorToken(
            String actorToken, User currentUser, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupSocialPostFeedPagination(
                    "getUserPostsByActorToken", beforeId, limit);

            org.springframework.data.domain.Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
            List<SocialPost> posts = setup.hasCursor()
                    ? socialPostRepository.findByActorTokenAndStatusAndIdLessThanOrderByCreatedAtDesc(
                    actorToken, PostStatus.ACTIVE, setup.getSanitizedCursor(), pageable)
                    : socialPostRepository.findByActorTokenAndStatusOrderByCreatedAtDesc(
                    actorToken, PostStatus.ACTIVE, pageable);

            List<SocialPostDto> postDtos = currentUser != null
                    ? convertToDtoBatch(posts, currentUser)
                    : convertToDtoSimple(posts);

            PaginatedResponse<SocialPostDto> response = PaginationUtils
                    .createSocialPostDtoResponse(postDtos, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getUserPostsByActorToken", postDtos,
                    response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception e) {
            log.error("Error getting posts for actorToken: {}", actorToken, e);
            throw new ServiceException("Failed to retrieve user posts by actor token", e);
        }
    }

    @Transactional(readOnly = true)
    public Long countSocialPostsByActorToken(String actorToken) {
        if (actorToken == null || actorToken.isBlank()) return 0L;
        return socialPostRepository.countByActorToken(actorToken);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> searchByHashtag(
            String hashtag, User user, Long beforeId, Integer limit) {
        try {
            if (hashtag == null || hashtag.trim().isEmpty()) {
                throw new ValidationException("Hashtag cannot be empty");
            }

            String cleanHashtag = com.JanSahayak.AI.payload.PostUtility.sanitizeSqlLike(normalizeHashtag(hashtag));
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupHashtagSearchPagination(
                    "searchByHashtag", beforeId, limit);

            List<SocialPost> posts = fetchPostsByHashtag(cleanHashtag, setup);

            List<SocialPostDto> postDtos = user != null
                    ? convertToDtoBatch(posts, user)
                    : convertToDtoSimple(posts);

            PaginatedResponse<SocialPostDto> response = PaginationUtils
                    .createSocialPostDtoResponse(postDtos, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("searchByHashtag", postDtos,
                    response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception e) {
            log.error("Error searching posts by hashtag: {}", hashtag, e);
            throw new ServiceException("Failed to search posts by hashtag", e);
        }
    }

    // =========================================================================
    // FEED PEEK (NEW POST COUNTS)
    // =========================================================================

    /**
     * Counts new posts since a given post ID for the three main feed scopes.
     *
     * FOR_YOU / LOCATION — global count: any ACTIVE, non-flagged post that
     *   is either a plain post OR a PUBLIC community post with feedEligible=true.
     * FOLLOWING — counts posts from communities the user has joined.
     *
     * Returns 0 for invalid afterId or null user.
     */
    @Transactional(readOnly = true)
    public long peekNewPostCount(User user, com.JanSahayak.AI.enums.FeedScope scope, Long afterId) {
        if (afterId == null || afterId <= 0) return 0;

        switch (scope) {
            case FOR_YOU:
                return socialPostRepository.countNewGlobalPostsAfter(afterId);
            case LOCATION:
                if (user == null || user.getPincode() == null) return socialPostRepository.countNewGlobalPostsAfter(afterId);
                return socialPostRepository.countNewLocationPostsAfter(afterId, user.getPincode());
            case FOLLOWING:
                if (user == null || user.getId() == null) {
                    log.warn("[FeedPeek] FOLLOWING peek called with null user — returning 0");
                    return 0;
                }
                return socialPostRepository.countNewFollowingPostsAfter(user.getId(), afterId);
            case NEIGHBORHOOD_QA:
                if (user == null || user.getPincode() == null) return 0;
                return socialPostRepository.countNewNeighborhoodQAPostsAfter(afterId, user.getPincode());
            case OFFICIAL:
                if (user == null || user.getPincode() == null) return 0;
                return postRepo.countNewOfficialPostsAfter(afterId);
            default:
                return 0;
        }
    }

    /**
     * Counts new posts in a specific community since a given post ID.
     * Used by the Communities tab to power the per-community "New Posts" banner.
     *
     * This wires in the countNewCommunityPostsAfter() repo query that is
     * already written and tested but was previously not called anywhere.
     *
     * Returns 0 for invalid communityId or afterId.
     */
    @Transactional(readOnly = true)
    public long peekCommunityPostCount(Long communityId, Long afterId) {
        if (communityId == null || communityId <= 0) return 0;
        if (afterId == null || afterId <= 0) return 0;
        return socialPostRepository.countNewCommunityPostsAfter(communityId, afterId);
    }

    // =========================================================================

    // HLIG v2 — BROWSE FEED (3 tabs × 3 sort modes)
    // =========================================================================
    //
    // Every tab endpoint in FeedController calls one of the 5 convenience methods
    // below. All 5 delegate to getBrowseFeed() which delegates to HLIGFeedService.
    //
    // Tab → scope + sort mapping:
    //
    //   /for-you   → FOR_YOU  + HOT   personalised pool, trending within interests
    //   /hot       → LOCATION + HOT   geo pool, 72h window, viralityScore DESC
    //   /new       → LOCATION + NEW   geo pool, createdAt DESC
    //   /top       → LOCATION + TOP   geo pool, all-time engagementScore DESC
    //   /following → FOLLOWING + HOT  community pool, trending sort
    //
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Core delegate for all 9 scope × sort combinations.
     * Called by every HLIG tab method. Passes size+1 so buildPagedResponse()
     * can detect hasMore without a separate COUNT query.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getBrowseFeed(
            User user, FeedScope scope, FeedSort sort, Long lastPostId, int size) {
        PostUtility.validateUser(user);
        try {
            List<SocialPost> posts = hligFeedService.getBrowseFeed(
                    user, scope, sort, lastPostId, size + 1);

            if (posts == null || posts.isEmpty()) {
                if (lastPostId != null) {
                    log.debug("[HLIG] getBrowseFeed empty on page 2+ scope={} sort={} userId={} — returning empty",
                            scope, sort, user.getId());
                    return PaginatedResponse.of(Collections.emptyList(), false, null, size);
                }
                log.debug("[HLIG] getBrowseFeed empty scope={} sort={} userId={} — homeFeed fallback",
                        scope, sort, user.getId());
                return getHomeFeed(user, lastPostId, size);
            }

            return buildPagedResponse(posts, user, size);

        } catch (Exception e) {
            log.error("[HLIG] getBrowseFeed failed scope={} sort={} user={} — trending fallback",
                    scope, sort, user.getActualUsername(), e);
            return getTrendingPostsFallback(user, lastPostId, size);
        }
    }

    /**
     * FOR YOU tab — HLIG personalised pool, hot sort.
     * Cold users (no interactions) receive a geographic fallback inside HLIGFeedService.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getPersonalisedFeed(
            User user, Long lastPostId, int size) {
        return getBrowseFeed(user, FeedScope.FOR_YOU, FeedSort.HOT, lastPostId, size);
    }

    /**
     * HOT tab — geographic pool (pincode→district→state waterfall), trending in 72 hours.
     * viralityScore DESC within the 72h window; extends to 7-day on sparse areas.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getHotFeed(
            User user, Long lastPostId, int size) {
        return getBrowseFeed(user, FeedScope.LOCATION, FeedSort.HOT, lastPostId, size);
    }

    /**
     * NEW tab — geographic pool, pure chronological (createdAt DESC).
     * No time window; all active posts in the user's state + national.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getNewFeed(
            User user, Long lastPostId, int size) {
        return getBrowseFeed(user, FeedScope.LOCATION, FeedSort.NEW, lastPostId, size);
    }

    /**
     * TOP tab — geographic pool, all-time highest engagement.
     * engagementScore DESC, no time window; surfaces the best posts ever in the region.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getTopFeed(
            User user, Long lastPostId, int size) {
        return getBrowseFeed(user, FeedScope.LOCATION, FeedSort.TOP, lastPostId, size);
    }

    /**
     * FOLLOWING tab — posts from communities the user has joined, hot sort.
     * Falls back to cold-feed candidates inside HLIGFeedService when the user
     * has not joined any community yet.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getFollowingFeed(
            User user, Long lastPostId, int size) {
        return getBrowseFeed(user, FeedScope.FOLLOWING, FeedSort.HOT, lastPostId, size);
    }

    // =========================================================================
    // HLIG v2 — NEGATIVE SIGNALS
    // =========================================================================

    public void recordScrolledPast(Long postId, User user) {
        if (postId == null || user == null) return;
        try {
            boolean exists = socialPostRepository.existsById(postId);
            if (exists) interestProfileService.onScrolledPast(user.getId(), postId);
        } catch (Exception e) {
            log.warn("[HLIG] onScrolledPast failed: postId={} userId={} reason={}",
                    postId, user.getId(), e.getMessage());
        }
    }

    public void recordNotInterested(Long postId, User user) {
        if (postId == null || user == null) return;
        try {
            boolean exists = socialPostRepository.existsById(postId);
            if (exists) {
                interestProfileService.onNotInterested(user.getId(), postId);
                log.info("[HLIG] onNotInterested: postId={} userId={}", postId, user.getId());
            }
        } catch (Exception e) {
            log.warn("[HLIG] onNotInterested failed: postId={} userId={} reason={}",
                    postId, user.getId(), e.getMessage());
        }
    }

    // =========================================================================
    // PRIVATE — DTO CONVERSION
    // =========================================================================

    public SocialPostDto convertToDto(SocialPost post, User user) {
        if (post == null) return null;
        List<SocialPostDto> result = convertToDtoBatch(List.of(post), user);
        return result.isEmpty() ? null : result.get(0);
    }

    private Map<Long, Poll> loadPollMap(List<SocialPost> posts) {
        if (posts == null || posts.isEmpty()) return Collections.emptyMap();
        List<Long> postIds = posts.stream().map(SocialPost::getId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        List<Poll> polls = pollRepository.findBySocialPostIdIn(postIds);
        Map<Long, Poll> map = new HashMap<>();
        for (Poll poll : polls) {
            if (poll.getSocialPost() != null)
                map.put(poll.getSocialPost().getId(), poll);
        }
        return map;
    }

    private List<SocialPostDto> convertToDtoBatch(List<SocialPost> posts, User user) {
        if (posts == null || posts.isEmpty()) return Collections.emptyList();

        // Collect all post IDs upfront
        List<Long> postIds = posts.stream()
                .map(SocialPost::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Re-fetch posts eagerly with their user and community associations
        // to avoid LazyInitializationException when accessing lazy proxies on detached entities (e.g. cached posts).
        List<SocialPost> attachedPosts;
        try {
            List<SocialPost> fetched = socialPostRepository.findAllByIdsWithUserAndCommunity(postIds);
            if (fetched != null && !fetched.isEmpty()) {
                Map<Long, SocialPost> fetchedMap = fetched.stream()
                        .collect(Collectors.toMap(SocialPost::getId, p -> p, (p1, p2) -> p1));
                attachedPosts = postIds.stream()
                        .map(fetchedMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (attachedPosts.isEmpty()) {
                    attachedPosts = posts;
                }
            } else {
                attachedPosts = posts;
            }
        } catch (Exception e) {
            log.warn("Failed to eagerly fetch posts with user/community: {}. Falling back to input posts.", e.getMessage());
            attachedPosts = posts;
        }

        // ── Load polls (already batched correctly) ────────────────────────────
        Map<Long, Poll> pollMap = loadPollMap(attachedPosts);

        // ── BATCH load interactions (was: 3 queries × N posts = N*3 queries) ──
        // Now: exactly 3 queries regardless of feed size
        Set<Long> likedPostIds   = Collections.emptySet();
        Set<Long> dislikedPostIds = Collections.emptySet();
        Set<Long> savedPostIds   = Collections.emptySet();
        Set<Long> viewedPostIds  = Collections.emptySet();

        if (user != null) {
            likedPostIds    = postInteractionService.getBatchLikedSocialPostIds(user, postIds);
            dislikedPostIds = postInteractionService.getBatchDislikedSocialPostIds(user, postIds);
            savedPostIds    = postInteractionService.getBatchSavedSocialPostIds(user, postIds);
            viewedPostIds   = postInteractionService.getBatchViewedSocialPostIds(user, postIds);
        }

        // ── Batch load poll votes (already done correctly) ────────────────────
        Set<Long>             votedPollIds    = Collections.emptySet();
        Map<Long, List<Long>> votedOptionsMap = Collections.emptyMap();

        if (user != null && !pollMap.isEmpty()) {
            List<Long> pollIds = pollMap.values().stream()
                    .map(Poll::getId)
                    .collect(Collectors.toList());
            votedPollIds = new HashSet<>(
                    pollVoteRepository.findVotedPollIdsByUserAndPollIds(user.getId(), pollIds));
            List<Object[]> rows =
                    pollVoteRepository.findVotedOptionsByUserAndPollIds(user.getId(), pollIds);
            votedOptionsMap = new HashMap<>();
            for (Object[] row : rows) {
                Long pollId   = ((Number) row[0]).longValue();
                Long optionId = ((Number) row[1]).longValue();
                votedOptionsMap.computeIfAbsent(pollId, k -> new ArrayList<>()).add(optionId);
            }
        }

        // Capture finals for lambda
        final Set<Long>             fLiked    = likedPostIds;
        final Set<Long>             fDisliked = dislikedPostIds;
        final Set<Long>             fSaved    = savedPostIds;
        final Set<Long>             fViewed   = viewedPostIds;
        final Map<Long, Poll>       fp        = pollMap;
        final Set<Long>             fvp       = votedPollIds;
        final Map<Long, List<Long>> fvo       = votedOptionsMap;

        // Batch load author roles to avoid LazyInitializationException outside transactional boundaries
        Map<Long, String> userRoleMap = new HashMap<>();
        try {
            List<Long> userIds = attachedPosts.stream()
                    .filter(p -> p != null && p.getUser() != null)
                    .map(p -> p.getUser().getId())
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!userIds.isEmpty()) {
                List<Object[]> rolesData = userRepository.findUserRolesByUserIds(userIds);
                for (Object[] row : rolesData) {
                    if (row != null && row.length >= 2) {
                        Long uid = ((Number) row[0]).longValue();
                        String rName = (String) row[1];
                        userRoleMap.put(uid, rName);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to batch load user roles in convertToDtoBatch: {}", e.getMessage());
        }

        final Map<Long, String> fUserRoles = userRoleMap;

        // Batch load communities to avoid LazyInitializationException on detached entities
        Map<Long, com.JanSahayak.AI.model.Community> communityMap = new HashMap<>();
        try {
            List<Long> communityIds = attachedPosts.stream()
                    .filter(p -> p != null && p.getCommunityId() != null)
                    .map(SocialPost::getCommunityId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!communityIds.isEmpty()) {
                List<com.JanSahayak.AI.model.Community> communities = communityRepository.findAllById(communityIds);
                for (com.JanSahayak.AI.model.Community c : communities) {
                    if (c != null) {
                        communityMap.put(c.getId(), c);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to batch load communities in convertToDtoBatch: {}", e.getMessage());
        }

        final Map<Long, com.JanSahayak.AI.model.Community> fCommunities = communityMap;
        final String fActorToken = resolveActorToken(user);

        List<SocialPostDto> dtos = attachedPosts.stream()
                .map(post -> {
                    try {
                        if (post == null) return null;
                        boolean isLiked    = fLiked.contains(post.getId());
                        boolean isSaved    = fSaved.contains(post.getId());
                        boolean isViewed   = fViewed.contains(post.getId());
                        Poll poll          = fp.get(post.getId());
                        boolean hasVoted   = poll != null && fvp.contains(poll.getId());
                        List<Long> votedIds = hasVoted
                                ? fvo.getOrDefault(poll.getId(), List.of())
                                : List.of();
                        String roleName = (post.getUser() != null && post.getUser().getId() != null)
                                ? fUserRoles.getOrDefault(post.getUser().getId(), "ROLE_USER")
                                : "ROLE_USER";
                        SocialPostDto dto = SocialPostDto.fromSocialPostWithInteractions(
                                post, isLiked, isSaved, isViewed, poll, hasVoted, votedIds, roleName);
                        if (dto != null) {
                            dto.setCanDelete(SocialPostUtility.canUserDeleteSocialPost(post, user, fActorToken));
                            if (post.getCommunityId() != null) {
                                com.JanSahayak.AI.model.Community c = fCommunities.get(post.getCommunityId());
                                if (c != null) {
                                    dto.setCommunityName(c.getName());
                                    dto.setCommunitySlug(c.getSlug());
                                    dto.setCommunityAvatar(c.getAvatarUrl());
                                    dto.setCommunityMemberCount(c.getMemberCount());
                                }
                            }
                        }
                        return dto;
                    } catch (Exception e) {
                        log.warn("Failed to convert post {} to DTO: {}", post.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // ── Batch-enrich community posts with isMember flag (1 extra query for the whole page) ─
        if (user != null) {
            List<Long> communityPostIds = dtos.stream()
                    .filter(d -> d.getCommunityId() != null)
                    .map(SocialPostDto::getCommunityId)
                    .distinct()
                    .collect(Collectors.toList());

            if (!communityPostIds.isEmpty()) {
                try {
                    Set<Long> memberCommunityIds = communityMemberRepository
                            .findActiveByUserIdAndCommunityIdIn(user.getId(), communityPostIds)
                            .stream()
                            .map(cm -> cm.getCommunity().getId())
                            .collect(Collectors.toSet());

                    // ═════════════════════════════════════════════════════════════════════════
                    // BATCH ENRICHMENT: Author roles in communities
                    // ═════════════════════════════════════════════════════════════════════════
                    List<Long> authorIds = attachedPosts.stream()
                            .filter(p -> p.getUser() != null)
                            .map(p -> p.getUser().getId())
                            .distinct()
                            .toList();
                    List<CommunityMember> authorMemberships = authorIds.isEmpty() ? List.of() :
                            communityMemberRepo.findActiveByUserIdInAndCommunityIdIn(authorIds, communityPostIds);

                    // Map key: "userId_communityId" -> role
                    Map<String, String> authorRoleMap = authorMemberships.stream()
                            .filter(cm -> cm.getUser() != null && cm.getCommunity() != null)
                            .collect(java.util.stream.Collectors.toMap(
                                    cm -> cm.getUser().getId() + "_" + cm.getCommunity().getId(),
                                    cm -> cm.getMemberRole() != null ? cm.getMemberRole().name() : null,
                                    (r1, r2) -> r1)); // Should be unique per UK

                    dtos.forEach(dto -> {
                        if (dto.getCommunityId() != null) {
                            dto.setIsMember(memberCommunityIds.contains(dto.getCommunityId()));

                            Long authorId = dto.getAuthor() != null ? dto.getAuthor().getId() : null;
                            if (authorId != null) {
                                String roleKey = authorId + "_" + dto.getCommunityId();
                                dto.setAuthorRole(authorRoleMap.getOrDefault(roleKey, null));
                            }
                        }
                    });
                } catch (Exception e) {
                    log.warn("[Community] batch membership enrichment failed: {}", e.getMessage());
                }
            }
        }

        // ── Batch Auto-Translate ──
        if (user != null && Boolean.TRUE.equals(user.getAutoTranslate()) && user.getPreferredLanguage() != null) {
            try {
                translationService.translateSocialPosts(dtos, user.getPreferredLanguage());
            } catch (Exception e) {
                log.warn("Failed to batch translate social posts: {}", e.getMessage());
            }
        }

        return dtos;
    }

    private List<SocialPostDto> convertToDtoSimple(List<SocialPost> posts) {
        return convertToDtoBatch(posts, null);
    }

    private SocialPostDto convertToDtoFromMaps(
            SocialPost post, User user,
            Map<Long, Poll> pollMap,
            Set<Long> votedPollIds,
            Map<Long, List<Long>> votedOptionsMap) {
        try {
            if (post == null) return null;
            boolean isLiked  = user != null && postInteractionService.hasUserLikedSocialPost(post, user);
            boolean isSaved  = user != null && postInteractionService.hasSavedSocialPost(post, user);
            boolean isViewed = user != null && postInteractionService.hasUserViewedSocialPostRecently(post, user);
            Poll       poll         = pollMap.get(post.getId());
            boolean    userHasVoted = false;
            List<Long> votedIds     = List.of();
            if (poll != null && user != null) {
                userHasVoted = votedPollIds.contains(poll.getId());
                if (userHasVoted) votedIds = votedOptionsMap.getOrDefault(poll.getId(), List.of());
            }
            return SocialPostDto.fromSocialPostWithInteractions(
                    post, isLiked, isSaved, isViewed, poll, userHasVoted, votedIds);
        } catch (Exception e) {
            log.warn("Failed to convert social post {} to DTO: {}", post.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Shared cursor-page builder used by getBrowseFeed and all legacy feed methods.
     * Caller passes size+1 posts; this method trims and computes hasMore + nextCursor.
     */
    private PaginatedResponse<SocialPostDto> buildPagedResponse(
            List<SocialPost> posts, User user, int size) {
        List<SocialPostDto> dtos = convertToDtoBatch(
                posts != null ? posts : Collections.emptyList(), user);
        boolean hasMore = dtos.size() > size;
        if (hasMore) dtos = new ArrayList<>(dtos.subList(0, size));
        Long nextCursor = hasMore && !dtos.isEmpty()
                ? dtos.get(dtos.size() - 1).getId()
                : null;
        return PaginatedResponse.of(dtos, hasMore, nextCursor, size);
    }

    // =========================================================================
    // PRIVATE — FETCH HELPERS
    // =========================================================================

    private List<SocialPost> fetchRecommendedPosts(
            String statePrefix, String districtPrefix, PaginationUtils.PaginationSetup setup) {

        List<SocialPost> posts;
        if (setup.hasCursor()) {
            posts = socialPostRepository.findRecommendedPostsForUserWithCursor(
                    statePrefix, districtPrefix,
                    setup.getSanitizedCursor(), setup.toPageable());
        } else {
            posts = socialPostRepository.findRecommendedPostsForUser(
                    statePrefix, districtPrefix, setup.toPageable());
        }

        if (posts == null || posts.size() < Math.max(1, setup.getValidatedLimit() / 2)) {
            log.debug("[Feed] fetchRecommendedPosts: only {} — falling back to all active",
                    posts == null ? 0 : posts.size());
            List<SocialPost> fallback = setup.hasCursor()
                    ? socialPostRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(
                    PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : socialPostRepository.findByStatusOrderByCreatedAtDesc(
                    PostStatus.ACTIVE, setup.toPageable());

            if (fallback != null && !fallback.isEmpty()) {
                Map<Long, SocialPost> merged = new LinkedHashMap<>();
                if (posts != null) posts.forEach(p -> merged.put(p.getId(), p));
                fallback.forEach(p -> merged.putIfAbsent(p.getId(), p));
                posts = new ArrayList<>(merged.values());
            }
        }

        return posts != null ? posts : Collections.emptyList();
    }

    private List<SocialPost> fetchTrendingPosts(PaginationUtils.PaginationSetup setup) {
        List<SocialPost> posts;
        if (setup.hasCursor()) {
            posts = socialPostRepository
                    .findByIsViralTrueAndStatusAndIdLessThanOrderByViralityScoreDesc(
                            PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
        } else {
            posts = socialPostRepository.findByIsViralTrueAndStatusOrderByViralityScoreDesc(
                    PostStatus.ACTIVE, setup.toPageable());
        }

        if (posts == null || posts.size() < Math.max(1, setup.getValidatedLimit() / 2)) {
            log.debug("[Feed] fetchTrendingPosts: only {} viral — fallback to active",
                    posts == null ? 0 : posts.size());
            List<SocialPost> fallback = setup.hasCursor()
                    ? socialPostRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(
                    PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : socialPostRepository.findByStatusOrderByCreatedAtDesc(
                    PostStatus.ACTIVE, setup.toPageable());

            if (fallback != null && !fallback.isEmpty()) {
                Map<Long, SocialPost> merged = new LinkedHashMap<>();
                if (posts != null) posts.forEach(p -> merged.put(p.getId(), p));
                fallback.forEach(p -> merged.putIfAbsent(p.getId(), p));
                posts = new ArrayList<>(merged.values());
            }
        }

        return posts != null ? posts : Collections.emptyList();
    }

    private List<SocialPost> fetchUserPosts(Long userId, PaginationUtils.PaginationSetup setup) {
        List<PostStatus> visibleStatuses = List.of(PostStatus.ACTIVE);
        org.springframework.data.domain.Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
        return setup.hasCursor()
                ? socialPostRepository.findByUserIdAndStatusInAndIdLessThanOrderByCreatedAtDesc(
                userId, visibleStatuses, setup.getSanitizedCursor(), pageable)
                : socialPostRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(
                userId, visibleStatuses, pageable);
    }

    private List<SocialPost> fetchPostsByHashtag(
            String hashtag, PaginationUtils.PaginationSetup setup) {
        return setup.hasCursor()
                ? socialPostRepository.findByHashtagContainingAndIdLessThan(
                hashtag, PostStatus.ACTIVE,
                setup.getSanitizedCursor(), setup.toPageable())
                : socialPostRepository.findByHashtagContaining(
                hashtag, PostStatus.ACTIVE, setup.toPageable());
    }

    /**
     * Emergency last-resort when getBrowseFeed() itself throws.
     * Degrades to trending (viral) posts so the tab is never a 500.
     */
    private PaginatedResponse<SocialPostDto> getTrendingPostsFallback(
            User user, Long lastPostId, int size) {
        try {
            return getTrendingPosts(user, lastPostId, size);
        } catch (Exception e) {
            log.error("[HLIG] getTrendingPostsFallback also failed userId={}",
                    user != null ? user.getId() : "null", e);
            return PaginatedResponse.of(Collections.emptyList(), false, null, size);
        }
    }

    // =========================================================================
    // PRIVATE — BUILD / VALIDATE HELPERS
    // =========================================================================

    private List<String> extractAndValidateHashtags(
            String content, List<String> providedHashtags) {

        List<String> extractedFromContent =
                SocialPostUtility.extractHashtagsFromContent(content);
        Set<String> allHashtags = new HashSet<>(extractedFromContent);

        if (providedHashtags != null && !providedHashtags.isEmpty()) {
            allHashtags.addAll(providedHashtags.stream()
                    .map(this::normalizeHashtag)
                    .collect(Collectors.toList()));
        }

        List<String> finalHashtags = new ArrayList<>(allHashtags);
        SocialPostUtility.validateHashtags(finalHashtags);
        return finalHashtags;
    }

    private List<Long> extractAndValidateMentions(
            String content, List<Long> providedMentions) {

        List<String> extractedUsernames =
                SocialPostUtility.extractMentionsFromContent(content);
        Set<Long> allMentionedUserIds = new HashSet<>();

        if (!extractedUsernames.isEmpty()) {
            List<User> mentionedUsers =
                    userRepository.findByUsernameIn(extractedUsernames);
            allMentionedUserIds.addAll(mentionedUsers.stream()
                    .map(User::getId)
                    .collect(Collectors.toList()));
        }

        if (providedMentions != null && !providedMentions.isEmpty()) {
            allMentionedUserIds.addAll(providedMentions);
        }

        List<Long> finalMentions = new ArrayList<>(allMentionedUserIds);
        if (finalMentions.size() > Constant.MAX_MENTIONS_PER_POST) {
            throw new ValidationException("Cannot mention more than " +
                    Constant.MAX_MENTIONS_PER_POST + " users");
        }
        return finalMentions;
    }

    private List<String> uploadMediaFilesWithValidation(
            List<MultipartFile> files, Long userId) {
        try {
            SocialPostUtility.validateMediaFiles(files);
            return mediaService.uploadMediaFiles(files, userId);
        } catch (Exception e) {
            log.error("Failed to upload media files for user: {}", userId, e);
            throw new ServiceException(
                    "Failed to upload media files: " + e.getMessage(), e);
        }
    }

    private SocialPost buildSocialPost(
            SocialPostCreateDto createDto,
            User user,
            List<String> hashtags,
            List<Long> mentionedUserIds,
            List<String> mediaUrls) {

        SocialPost socialPost = SocialPost.builder()
                .content(createDto.getContent().trim())
                .user(user)
                .category(createDto.getCategory() != null 
                        ? createDto.getCategory() : com.JanSahayak.AI.enums.SocialPostCategory.GENERAL)
                .status(PostStatus.ACTIVE)
                .allowComments(createDto.getAllowComments() != null
                        ? createDto.getAllowComments() : true)
                .showLocation(createDto.getShowLocation() != null
                        ? createDto.getShowLocation() : false)
                .createdAt(new Date())
                .build();

        if (user != null) {
            String authorUsername = user.getActualUsername();
            String authorProfileImage = user.getProfileImage();
            String token = resolveActorToken(user);
            if (token != null && !token.isBlank() && com.JanSahayak.AI.payload.PostUtility.isCitizen(user)) {
                socialPost.setActorToken(token);
                socialPost.setUser(null);
                if (actorProfileRepo != null) {
                    ActorProfile ap = actorProfileRepo.findByActorToken(token).orElse(null);
                    if (ap != null) {
                        authorUsername = ap.getUsername();
                        if (ap.getProfileImage() != null) authorProfileImage = ap.getProfileImage();
                    }
                }
            } else {
                socialPost.setUser(user);
                socialPost.setActorToken(null);
            }
            socialPost.setAuthorUsername(authorUsername);
            socialPost.setAuthorProfileImage(authorProfileImage);
        }

        if (createDto.getTargetPincode() != null && !createDto.getTargetPincode().trim().isEmpty()) {
            String targetPin = createDto.getTargetPincode().trim();
            PincodeLookup targetLookup = pincodeLookupRepo.findById(targetPin).orElse(null);
            if (targetLookup != null) {
                socialPost.setPincode(targetPin);
                socialPost.setLatitude(targetLookup.getLatitude());
                socialPost.setLongitude(targetLookup.getLongitude());
                socialPost.setTargetUrbanClusterId(targetLookup.getUrbanClusterId());
                socialPost.setTargetDistrict(targetLookup.getDistrict());
                socialPost.setQaPosterScope("ASK_ANYWHERE");
            } else {
                socialPost.inheritLocationFromUser(user);
                socialPost.setQaPosterScope("HOME");
            }
        } else {
            socialPost.inheritLocationFromUser(user);
            socialPost.setQaPosterScope("HOME");
        }

        if (socialPost.getLatitude() == null && socialPost.getPincode() != null) {
            pincodeLookupRepo.findById(socialPost.getPincode()).ifPresent(pin -> {
                if (pin.getLatitude() != null && pin.getLongitude() != null) {
                    socialPost.setLatitude(pin.getLatitude());
                    socialPost.setLongitude(pin.getLongitude());
                    if (socialPost.getTargetUrbanClusterId() == null) {
                        socialPost.setTargetUrbanClusterId(pin.getUrbanClusterId());
                    }
                    if (socialPost.getTargetDistrict() == null) {
                        socialPost.setTargetDistrict(pin.getDistrict());
                    }
                }
            });
        }

        if (createDto.getCommunityId() != null) {
            communityService.findCommunityForPost(createDto.getCommunityId(), user)
                    .ifPresent(community -> {
                        socialPost.setCommunity(community);
                        socialPost.syncCommunityDenormalizedFields(community);
                        
                        if (Boolean.TRUE.equals(community.getRequirePostApproval()) &&
                                !com.JanSahayak.AI.payload.PostUtility.isAdmin(user) &&
                                !communityMemberRepository.isModeratorOrAbove(community.getId(), user.getId())) {
                            socialPost.setStatus(PostStatus.PENDING_APPROVAL);
                        }
                    });
        }
        // ── End community wiring ──────────────────────────────────────────────

        if (!mediaUrls.isEmpty())        socialPost.setMediaUrlsList(mediaUrls);
        if (!hashtags.isEmpty())         socialPost.setHashtagsList(hashtags);
        if (!mentionedUserIds.isEmpty()) socialPost.setMentionedUserIdsList(mentionedUserIds);

        return socialPost;
    }

    private String normalizeHashtag(String hashtag) {
        String cleaned = hashtag.trim().toLowerCase();
        return cleaned.startsWith("#") ? cleaned : "#" + cleaned;
    }

    private void cleanupMediaFiles(List<String> mediaUrls) {
        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            try {
                mediaService.deleteMediaFiles(mediaUrls);
            } catch (Exception e) {
                log.warn("Failed to clean up media files, will be handled by cleanup job", e);
            }
        }
    }
}
