package com.govlyx.AI.service;

import com.govlyx.AI.dto.PaginatedResponse;
import com.govlyx.AI.dto.PostContentUpdateDto;
import com.govlyx.AI.dto.PostCreateDto;
import com.govlyx.AI.dto.PostResponse;
import com.govlyx.AI.config.Constant;
import com.govlyx.AI.enums.PostStatus;
import com.govlyx.AI.enums.BroadcastScope;
import com.govlyx.AI.enums.FeedSort;
import com.govlyx.AI.exception.*;
import com.govlyx.AI.model.*;
import com.govlyx.AI.model.PostShare.ShareType;
import com.govlyx.AI.payload.PostUtility;
import com.govlyx.AI.payload.PaginationUtils;
import com.govlyx.AI.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepo                 postRepository;
    private final UserTaggingService       userTaggingService;
    private final UserRepo                 userRepository;
    private final CommentRepo              commentRepository;
    private final PinCodeLookupService     pinCodeLookupService;
    private final UserTagRepo              userTagRepository;
    private final ContentValidationService contentValidationService;
    private final NotificationService      notificationService;
    private final PostInteractionService   postInteractionService;
    private final TranslationService       translationService;
    private final TextSimilarityService    textSimilarityService;

    // ── Cloudinary media adapter (replaces DrivePostMediaAdapter / local disk) ─
    // Bean name kept as drivePostMedia so no controller or other caller needs updating.
    private final DrivePostMediaAdapter    drivePostMedia;
    private final com.govlyx.AI.security.IdentityBlindService identityBlindService;
    private final com.govlyx.AI.repository.ActorProfileRepo actorProfileRepo;

    // uploadDir kept only so PostUtility helper methods that read the value compile.
    // NOT used for actual file storage — Cloudinary handles all uploads.
    @Value("${app.upload.dir:${user.home}/uploads/posts}")
    private String uploadDir;

    @Value("${app.upload.max-image-size:5242880}")
    private long maxImageSize;

    @Value("${app.upload.max-video-size:536870912}")
    private long maxVideoSize;

    // Cloudinary URL cleanup retry queue
    // FIX 1 — THREAD SAFETY: CompletableFuture.exceptionally() callbacks write to this
    // queue from ForkJoin worker threads, while processFileCleanupQueue() reads from it
    // on the scheduler thread.  LinkedList is NOT thread-safe; concurrent offer()+poll()
    // can corrupt the internal node links, causing infinite loops or lost entries.
    // Replaced with ConcurrentLinkedQueue which is lock-free and thread-safe.
    //
    // FIX 2 — UNBOUNDED GROWTH: processFileCleanupQueue() was never @Scheduled, so the
    // queue was filled by every failed async delete but NEVER drained, leaking one String
    // entry per failure indefinitely.  The @Scheduled annotation is added below.
    private final Queue<String> fileCleanupQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

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
    // CREATE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public Post createPost(PostCreateDto postDto, User user, List<MultipartFile> mediaFile) {
        log.info("Creating post: user={} (id={})", user.getActualUsername(), user.getId());
        try {
            PostUtility.validateUser(user);
            String safeContent = contentValidationService.sanitizeAndValidateContent(postDto.getContent());
            postDto.setContent(safeContent);
            PostUtility.validatePostContent(safeContent);

            if (postDto.getTargetPincode() == null || postDto.getTargetPincode().trim().isEmpty()) {
                throw new ValidationException("Target pincode is required for creating posts");
            }
            PostUtility.validateTargetPincodeForUser(postDto.getTargetPincode());
            pinCodeLookupService.populateUserLocationData(user);

            // CLOUDINARY: upload returns secure URL; null when no file provided
            String fileName = null;
            if (mediaFile != null && !mediaFile.isEmpty()) {
                if (mediaFile.size() > 2) {
                    throw new MediaValidationException("Maximum 2 media files are allowed");
                }
                List<String> fileNames = new java.util.ArrayList<>();
                for (MultipartFile mf : mediaFile) {
                    if (mf != null && !mf.isEmpty()) {
                        fileNames.add(drivePostMedia.upload(mf, user.getId()));
                    }
                }
                if (!fileNames.isEmpty()) {
                    fileName = String.join(",", fileNames);
                }
            }

            String idempotencyKey = com.govlyx.AI.util.IdempotencyContext.getKey();
            if (idempotencyKey != null) {
                java.util.Optional<Post> existingPost = postRepository.findByIdempotencyKey(idempotencyKey);
                if (existingPost.isPresent()) {
                    log.info("Idempotency hit: Returning existing Post for key {}", idempotencyKey);
                    return existingPost.get();
                }
            }

            Post post = new Post();
            post.setIdempotencyKey(idempotencyKey);
            post.setContent(postDto.getContent().trim());
            if (user != null) {
                String authorUsername = user.getActualUsername();
                String authorProfileImage = user.getProfileImage();
                String authorPincode = user.getPincode();
                String actorToken = resolveActorToken(user);
                if (actorToken != null && !actorToken.isBlank() && PostUtility.isCitizen(user)) {
                    post.setActorToken(actorToken);
                    post.setUser(null);
                    if (actorProfileRepo != null) {
                        ActorProfile ap = actorProfileRepo.findByActorToken(actorToken).orElse(null);
                        if (ap != null) {
                            authorUsername = ap.getUsername();
                            if (ap.getProfileImage() != null) authorProfileImage = ap.getProfileImage();
                            if (ap.getPincode() != null) authorPincode = ap.getPincode();
                        }
                    }
                } else {
                    post.setUser(user);
                    post.setActorToken(null);
                }
                post.setAuthorUsername(authorUsername);
                post.setAuthorProfileImage(authorProfileImage);
                post.setAuthorPincode(authorPincode);
            }
            post.setImageName(fileName);   // stores Cloudinary URL or null
            post.setStatus(PostStatus.ACTIVE);
            post.setCreatedAt(new Date());
            post.setIpAddress(com.govlyx.AI.util.IpUtils.getClientIpFromContext());

            if (PostUtility.isNormalUser(user) || PostUtility.isDepartment(user) || PostUtility.isAdmin(user)) {
                post.setBroadcastScope(BroadcastScope.AREA);
                String targetPincode = postDto.getTargetPincode().trim();
                if (!pinCodeLookupService.isValidPincode(targetPincode)) {
                    throw new ValidationException("Target pincode not found in system: " + targetPincode);
                }
                if (!Constant.isValidIndianPincode(targetPincode)) {
                    throw new ValidationException("Invalid Indian pincode format: " + targetPincode);
                }
                // Extract tags if not provided in DTO
                List<String> tagsForDuplicateCheck = postDto.getTaggedUsernames();
                if (tagsForDuplicateCheck == null || tagsForDuplicateCheck.isEmpty()) {
                    tagsForDuplicateCheck = PostUtility.extractUserTags(safeContent);
                }
                
                // Duplicate Issue Check
                if (!postDto.isForceSubmit()) {
                    Post duplicate = checkDuplicatePosts(targetPincode, safeContent, tagsForDuplicateCheck);
                    if (duplicate != null) {
                        PostResponse duplicateResponse = convertToPostResponse(duplicate, user);
                        throw new DuplicatePostException("Duplicate issue detected.", duplicateResponse);
                    }
                }
                
                post.setTargetPincodes(targetPincode);
                log.info("Post created with target pincode: {}", targetPincode);
            }

            post.setTargetCountry(Constant.DEFAULT_TARGET_COUNTRY);
            post = postRepository.save(post);

            try {
                userTaggingService.processUserTags(post);
            } catch (Exception e) {
                log.warn("Failed to process user tags for post: {}", post.getId(), e);
            }

            log.info("Post created: id={} status={} scope={} pincode={}",
                    post.getId(), post.getStatus().getDisplayName(),
                    post.getBroadcastScope() != null ? post.getBroadcastScope().getDescription() : "None",
                    post.getTargetPincodes());
            return post;
        } catch (ValidationException | MediaValidationException | DuplicatePostException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create post: user={} (id={})", user.getActualUsername(), user.getId(), e);
            throw new ServiceException("Failed to create post: " + e.getMessage(), e);
        }
    }

    public Post checkDuplicatePosts(String pincode, String content, List<String> taggedUsernames) {
        if (pincode == null || pincode.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            return null;
        }

        // If it's not tagged to anyone, it's not a report/complaint, so bypass duplicate checker
        if (taggedUsernames == null || taggedUsernames.isEmpty()) {
            return null;
        }

        // Only consider it an issue post if at least one tagged user is a department
        List<User> taggedUsers = userRepository.findByUsernameInAndIsActiveTrue(taggedUsernames);
        boolean hasDepartmentTag = false;
        if (taggedUsers != null) {
            hasDepartmentTag = taggedUsers.stream().anyMatch(User::isDepartment);
        }
        
        if (!hasDepartmentTag) {
            return null;
        }

        // Fetch ALL active posts in this specific pincode. 
        // We cap it at 20 to ensure the DB query and memory loop stay under ~15ms for lightning-fast replies.
        List<Post> activePosts = postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByIdDesc(
                BroadcastScope.AREA, PostStatus.ACTIVE, pincode, PageRequest.of(0, 20));

        for (Post post : activePosts) {
            // Only compare against old posts that tag at least one of the same departments
            boolean hasOverlappingTag = false;
            if (post.getUserTags() != null) {
                hasOverlappingTag = post.getUserTags().stream()
                        .filter(tag -> tag.getTaggedUser() != null)
                        .map(tag -> tag.getTaggedUser().getActualUsername())
                        .anyMatch(taggedUsernames::contains);
            }
            if (!hasOverlappingTag) {
                continue;
            }

            double similarity = textSimilarityService.calculateSimilarity(content, post.getContent());
            if (similarity >= 0.60) {
                return post;
            }
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public Post createPost(PostCreateDto postDto, User user) {
        return createPost(postDto, user, null);
    }


    @Transactional(rollbackFor = Exception.class)
    public Post createBroadcastPost(PostCreateDto postDto, User user, BroadcastScope broadcastScope,
                                    String targetCountry, List<String> targetStates,
                                    List<String> targetDistricts, List<String> targetPincodes,
                                    List<MultipartFile> mediaFile) {
        log.info("Creating broadcast post: user={} (id={}) scope={}", user.getActualUsername(), user.getId(), broadcastScope);
        try {
            PostUtility.validateUser(user);
            PostUtility.validateBroadcastPermission(user);
            PostUtility.validatePostContent(postDto.getContent());
            PostUtility.validateBroadcastScope(broadcastScope, targetCountry, targetStates, targetDistricts, targetPincodes);
            pinCodeLookupService.populateUserLocationData(user);

            // CLOUDINARY: upload returns secure URL
            String fileName = null;
            if (mediaFile != null && !mediaFile.isEmpty()) {
                if (mediaFile.size() > 2) {
                    throw new MediaValidationException("Maximum 2 media files are allowed");
                }
                List<String> fileNames = new java.util.ArrayList<>();
                for (MultipartFile mf : mediaFile) {
                    if (mf != null && !mf.isEmpty()) {
                        fileNames.add(drivePostMedia.upload(mf, user.getId()));
                    }
                }
                if (!fileNames.isEmpty()) {
                    fileName = String.join(",", fileNames);
                }
            }

            String idempotencyKey = com.govlyx.AI.util.IdempotencyContext.getKey();
            if (idempotencyKey != null) {
                java.util.Optional<Post> existingPost = postRepository.findByIdempotencyKey(idempotencyKey);
                if (existingPost.isPresent()) {
                    log.info("Idempotency hit: Returning existing Broadcast Post for key {}", idempotencyKey);
                    return existingPost.get();
                }
            }

            Post post = new Post();
            post.setIdempotencyKey(idempotencyKey);
            post.setContent(postDto.getContent().trim());
            if (user != null) {
                String authorUsername = user.getActualUsername();
                String authorProfileImage = user.getProfileImage();
                String authorPincode = user.getPincode();
                String actorToken = resolveActorToken(user);
                if (actorToken != null && !actorToken.isBlank() && PostUtility.isCitizen(user)) {
                    post.setActorToken(actorToken);
                    post.setUser(null);
                    if (actorProfileRepo != null) {
                        ActorProfile ap = actorProfileRepo.findByActorToken(actorToken).orElse(null);
                        if (ap != null) {
                            authorUsername = ap.getUsername();
                            if (ap.getProfileImage() != null) authorProfileImage = ap.getProfileImage();
                            if (ap.getPincode() != null) authorPincode = ap.getPincode();
                        }
                    }
                } else {
                    post.setUser(user);
                    post.setActorToken(null);
                }
                post.setAuthorUsername(authorUsername);
                post.setAuthorProfileImage(authorProfileImage);
                post.setAuthorPincode(authorPincode);
            }
            post.setImageName(fileName);
            post.setStatus(PostStatus.ACTIVE);
            post.setCreatedAt(new Date());
            post.setBroadcastScope(broadcastScope);
            post.setIpAddress(com.govlyx.AI.util.IpUtils.getClientIpFromContext());

            // targetCountry is no longer used for geographic targeting - relying strictly on broadcastScope
            post.setTargetCountry(null);

            // Fallback geographic targeting from PostCreateDto if explicit lists are empty
            List<String> states = targetStates;
            if ((states == null || states.isEmpty()) && postDto.getTargetStates() != null && !postDto.getTargetStates().isBlank()) {
                states = java.util.Arrays.asList(postDto.getTargetStates().split(","));
            }

            List<String> districts = targetDistricts;
            if ((districts == null || districts.isEmpty()) && postDto.getTargetDistricts() != null && !postDto.getTargetDistricts().isBlank()) {
                districts = java.util.Arrays.asList(postDto.getTargetDistricts().split(","));
            }

            List<String> pincodes = targetPincodes;
            if (pincodes == null || pincodes.isEmpty()) {
                if (postDto.getTargetPincodes() != null && !postDto.getTargetPincodes().isBlank()) {
                    pincodes = java.util.Arrays.asList(postDto.getTargetPincodes().split(","));
                } else if (postDto.getTargetPincode() != null && !postDto.getTargetPincode().isBlank()) {
                    pincodes = java.util.Collections.singletonList(postDto.getTargetPincode());
                }
            }

            if (states != null && !states.isEmpty()) {
                post.setTargetStates(PostUtility.convertStatesToTargetString(states, pinCodeLookupService));
            }
            if (districts != null && !districts.isEmpty()) {
                post.setTargetDistricts(PostUtility.convertDistrictsToTargetString(districts, pinCodeLookupService));
            }
            if (pincodes != null && !pincodes.isEmpty()) {
                post.setTargetPincodes(PostUtility.convertPincodesToTargetString(pincodes));
            }

            // Ensure targetCountry is 'IN' only for COUNTRY-wide broadcasts.
            // For specific geographic scopes, set to NULL to ensure the Waterfall Strategy filters them correctly.
            if (broadcastScope == BroadcastScope.COUNTRY) {
                post.setTargetCountry("IN");
            } else {
                post.setTargetCountry(null);
            }

            post = postRepository.save(post);

            try {
                userTaggingService.processUserTags(post);
            } catch (Exception e) {
                log.warn("Failed to process user tags for broadcast post: {}", post.getId(), e);
            }

            if (post.isCountryWideGovernmentBroadcast()) {
                PostUtility.logCountryBroadcast(post, user);
            }

            log.info("Broadcast post created: id={} scope={}", post.getId(), broadcastScope.getDescription());
            return post;
        } catch (ValidationException | MediaValidationException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create broadcast post: user={} (id={})", user.getActualUsername(), user.getId(), e);
            throw new ServiceException("Failed to create broadcast post: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post createCountryWideBroadcast(PostCreateDto postDto, User user, List<MultipartFile> mediaFile) {
        PostUtility.validateBroadcastPermission(user);
        Post post = createBroadcastPost(postDto, user, BroadcastScope.COUNTRY, Constant.DEFAULT_TARGET_COUNTRY,
                null, null, null, mediaFile);
        log.info("COUNTRY-WIDE BROADCAST CREATED: id={} user={} ({})",
                post.getId(), user.getActualUsername(), user.getRole().getName());
        return post;
    }

    @Transactional(rollbackFor = Exception.class)
    public Post createStateLevelBroadcast(PostCreateDto postDto, User user,
                                          List<String> targetStates, List<MultipartFile> mediaFile) {
        PostUtility.validateBroadcastPermission(user);
        PostUtility.validateTargetStates(targetStates);
        return createBroadcastPost(postDto, user, BroadcastScope.STATE, null,
                targetStates, null, null, mediaFile);
    }

    @Transactional(rollbackFor = Exception.class)
    public Post createDistrictLevelBroadcast(PostCreateDto postDto, User user,
                                             List<String> targetStates, List<String> targetDistricts,
                                             List<MultipartFile> mediaFile) {
        PostUtility.validateBroadcastPermission(user);
        PostUtility.validateTargetDistricts(targetDistricts);
        return createBroadcastPost(postDto, user, BroadcastScope.DISTRICT, null,
                targetStates, targetDistricts, null, mediaFile);
    }

    @Transactional(rollbackFor = Exception.class)
    public Post createAreaLevelBroadcast(PostCreateDto postDto, User user,
                                         List<String> targetPincodes, List<MultipartFile> mediaFile) {
        PostUtility.validateBroadcastPermission(user);
        PostUtility.validateTargetPincodesWithLookup(targetPincodes, pinCodeLookupService);
        return createBroadcastPost(postDto, user, BroadcastScope.AREA, null,
                null, null, targetPincodes, mediaFile);
    }

    // =========================================================================
    // BROADCAST QUERIES
    // =========================================================================

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getPostsByUser(Long userId, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getPostsByUser", beforeId, limit);
            List<PostStatus> visibleStatuses = Arrays.asList(PostStatus.ACTIVE, PostStatus.RESOLVED);
            List<Post> posts;
            
            Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
            if (setup.hasCursor()) {
                posts = postRepository.findByUserIdWithUserAndStatusInAndIdLessThanOrderByCreatedAtDesc(
                        userId, visibleStatuses, setup.getSanitizedCursor(), pageable);
            } else {
                posts = postRepository.findByUserIdWithUserAndStatusInOrderByCreatedAtDesc(userId, visibleStatuses);
                posts = posts.stream().limit(setup.getValidatedLimit() + 1).collect(Collectors.toList());
            }
            return PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
        } catch (Exception e) {
            log.error("Failed to get posts for user: {}", userId, e);
            return PaginationUtils.handlePaginationError("getPostsByUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "broadcast-feeds", key = "'all:' + (#beforeId != null ? #beforeId : 'first') + ':' + #limit")
    public PaginatedResponse<Post> getAllBroadcastPosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllBroadcastPosts", beforeId, limit);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByBroadcastScopeIsNotNullAndIdLessThanOrderByCreatedAtDesc(setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByBroadcastScopeIsNotNullOrderByCreatedAtDesc(setup.toPageable());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAllBroadcastPosts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get all broadcast posts", e);
            return PaginationUtils.handlePaginationError("getAllBroadcastPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "broadcast-feeds", key = "'active:' + (#beforeId != null ? #beforeId : 'first') + ':' + #limit")
    public PaginatedResponse<Post> getActiveBroadcastPosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getActiveBroadcastPosts", beforeId, limit);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByBroadcastScopeIsNotNullAndStatusAndIdLessThanOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByBroadcastScopeIsNotNullAndStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.toPageable());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getActiveBroadcastPosts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get active broadcast posts", e);
            return PaginationUtils.handlePaginationError("getActiveBroadcastPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "broadcast-feeds", key = "'scope:' + #scope.name() + ':' + (#beforeId != null ? #beforeId : 'first') + ':' + #limit")
    public PaginatedResponse<Post> getBroadcastPostsByScope(BroadcastScope scope, Long beforeId, Integer limit) {
        try {
            PostUtility.validateBroadcastScope(scope);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getBroadcastPostsByScope", beforeId, limit);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByBroadcastScopeAndStatusAndIdLessThanOrderByCreatedAtDesc(scope, PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(scope, PostStatus.ACTIVE, setup.toPageable());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getBroadcastPostsByScope", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get broadcast posts by scope: {}", scope, e);
            return PaginationUtils.handlePaginationError("getBroadcastPostsByScope", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getVisiblePostsForUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getVisiblePostsForUser", beforeId, limit);
            List<Post> allPosts = setup.hasCursor()
                    ? postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.toPageable());
            List<Post> visiblePosts = allPosts.stream()
                    .filter(post -> PostUtility.isPostVisibleToUser(post, user))
                    .collect(Collectors.toList());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(visiblePosts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getVisiblePostsForUser", visiblePosts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get visible posts for user: {}", user.getActualUsername(), e);
            return PaginationUtils.handlePaginationError("getVisiblePostsForUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getBroadcastPostsVisibleToUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getBroadcastPostsVisibleToUser", beforeId, limit);
            PaginatedResponse<Post> broadcastPosts = getActiveBroadcastPosts(beforeId, setup.getValidatedLimit());
            List<Post> visiblePosts = broadcastPosts.getData().stream()
                    .filter(post -> PostUtility.isPostVisibleToUser(post, user))
                    .collect(Collectors.toList());
            PaginatedResponse<Post> response = PaginatedResponse.of(visiblePosts, broadcastPosts.isHasMore(), broadcastPosts.getNextCursor(), setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getBroadcastPostsVisibleToUser", visiblePosts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get broadcast posts visible to user: {}", user.getActualUsername(), e);
            return PaginationUtils.handlePaginationError("getBroadcastPostsVisibleToUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "broadcast-feeds", key = "'countryAll:' + (#beforeId != null ? #beforeId : 'first') + ':' + #limit")
    public PaginatedResponse<Post> getAllCountryWideBroadcasts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllCountryWideBroadcasts", beforeId, limit);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByBroadcastScopeAndStatusAndTargetCountryAndIdLessThanOrderByCreatedAtDesc(BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByBroadcastScopeAndStatusAndTargetCountryOrderByCreatedAtDesc(BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY, setup.toPageable());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAllCountryWideBroadcasts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get all country-wide broadcasts", e);
            return PaginationUtils.handlePaginationError("getAllCountryWideBroadcasts", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "broadcast-feeds", key = "'countryActive:' + (#beforeId != null ? #beforeId : 'first') + ':' + #limit")
    public PaginatedResponse<Post> getActiveCountryWideBroadcasts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getActiveCountryWideBroadcasts", beforeId, limit);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByBroadcastScopeAndStatusAndTargetCountryAndIdLessThanOrderByCreatedAtDesc(BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByBroadcastScopeAndStatusAndTargetCountryOrderByCreatedAtDesc(BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY, setup.toPageable());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getActiveCountryWideBroadcasts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get active country-wide broadcasts", e);
            return PaginationUtils.handlePaginationError("getActiveCountryWideBroadcasts", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getCountryWideBroadcasts(Long beforeId, Integer limit) {
        return getBroadcastPostsByScope(BroadcastScope.COUNTRY, beforeId, limit);
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "broadcast-feeds", key = "'state:' + #state + ':' + (#beforeId != null ? #beforeId : 'first') + ':' + #limit", sync = true, condition = "#beforeId == null")
    public PaginatedResponse<Post> getStateLevelBroadcasts(String state, Long beforeId, Integer limit) {
        try {
            if (state == null || state.trim().isEmpty()) throw new ValidationException("State cannot be empty");
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getStateLevelBroadcasts", beforeId, limit);
            List<String> statePrefixes = PostUtility.convertStatesToPincodePrefixes(Arrays.asList(state.trim()), pinCodeLookupService);
            if (statePrefixes.isEmpty()) return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            String statePrefix = statePrefixes.get(0);
            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingAndIdLessThanOrderByCreatedAtDesc(BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix, setup.toPageable());
            }
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getStateLevelBroadcasts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get state level broadcasts for state: {}", state, e);
            return PaginationUtils.handlePaginationError("getStateLevelBroadcasts", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "broadcast-feeds", key = "'district:' + #district + ':' + (#beforeId != null ? #beforeId : 'first') + ':' + #limit", sync = true, condition = "#beforeId == null")
    public PaginatedResponse<Post> getDistrictLevelBroadcasts(String district, Long beforeId, Integer limit) {
        try {
            if (district == null || district.trim().isEmpty()) throw new ValidationException("District cannot be empty");
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getDistrictLevelBroadcasts", beforeId, limit);
            List<String> districtPrefixes = PostUtility.convertDistrictsToPincodePrefixes(Arrays.asList(district.trim()), pinCodeLookupService);
            if (districtPrefixes.isEmpty()) return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            List<Post> allDistrictPosts = new ArrayList<>();
            for (String prefix : districtPrefixes) {
                List<Post> posts = setup.hasCursor()
                        ? postRepository.findByBroadcastScopeAndStatusAndTargetDistrictsContainingAndIdLessThanOrderByCreatedAtDesc(BroadcastScope.DISTRICT, PostStatus.ACTIVE, prefix, setup.getSanitizedCursor(), setup.toPageable())
                        : postRepository.findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(BroadcastScope.DISTRICT, PostStatus.ACTIVE, prefix, setup.toPageable());
                if (posts != null) allDistrictPosts.addAll(posts);
            }
            List<Post> distinctPosts = allDistrictPosts.stream().distinct().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(distinctPosts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getDistrictLevelBroadcasts", distinctPosts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get district level broadcasts for district: {}", district, e);
            return PaginationUtils.handlePaginationError("getDistrictLevelBroadcasts", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "broadcast-feeds", key = "'area:' + #pincode + ':' + (#beforeId != null ? #beforeId : 'first') + ':' + #limit", sync = true, condition = "#beforeId == null")
    public PaginatedResponse<Post> getAreaLevelBroadcasts(String pincode, Long beforeId, Integer limit) {
        try {
            if (!Constant.isValidIndianPincode(pincode)) throw new ValidationException("Invalid Indian pincode format");
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAreaLevelBroadcasts", beforeId, limit);
            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingAndIdLessThanOrderByCreatedAtDesc(BroadcastScope.AREA, PostStatus.ACTIVE, pincode, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(BroadcastScope.AREA, PostStatus.ACTIVE, pincode, setup.toPageable());
            }
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAreaLevelBroadcasts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get area level broadcasts for pincode: {}", pincode, e);
            return PaginationUtils.handlePaginationError("getAreaLevelBroadcasts", e, PaginationUtils.validateLimit(limit));
        }
    }

    public Map<String, Long> getBroadcastStatistics() {
        try {
            Map<String, Long> stats = new HashMap<>();
            stats.put("totalBroadcasts", postRepository.countByBroadcastScopeIsNotNullAndStatusNot(PostStatus.DELETED));
            stats.put("activeBroadcasts", postRepository.countByBroadcastScopeIsNotNullAndStatus(PostStatus.ACTIVE));
            Long countryBroadcasts = postRepository.countByBroadcastScopeAndTargetCountryAndStatusNot(BroadcastScope.COUNTRY, Constant.DEFAULT_TARGET_COUNTRY, PostStatus.DELETED);
            stats.put("countryWideBroadcasts", countryBroadcasts != null ? countryBroadcasts : 0L);
            for (BroadcastScope scope : BroadcastScope.values()) {
                Long count = postRepository.countByBroadcastScopeAndStatusNot(scope, PostStatus.DELETED);
                stats.put("broadcasts" + scope.name(), count != null ? count : 0L);
                Long activeCount = postRepository.countByBroadcastScopeAndStatus(scope, PostStatus.ACTIVE);
                stats.put("activeBroadcasts" + scope.name(), activeCount != null ? activeCount : 0L);
            }
            return stats;
        } catch (Exception e) {
            log.error("Failed to get broadcast statistics", e);
            throw new ServiceException("Failed to get broadcast statistics: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getBroadcastAnalytics(User user, int days) {
        try {
            PostUtility.validateUser(user);
            PostUtility.validateBroadcastPermission(user);
            if (days <= 0) throw new ValidationException("Days must be positive");
            LocalDateTime startDate = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
            Timestamp timestamp = Timestamp.valueOf(startDate);
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("totalBroadcastsCreated", postRepository.countByUserIdAndBroadcastScopeIsNotNullAndStatusNot(user.getId(), PostStatus.DELETED));
            analytics.put("recentBroadcasts", postRepository.countByUserIdAndBroadcastScopeIsNotNullAndCreatedAtAfterAndStatusNot(user.getId(), timestamp, PostStatus.DELETED));
            if (PostUtility.canCreateBroadcast(user)) {
                Long cb = postRepository.countByUserIdAndBroadcastScopeAndTargetCountryAndStatusNot(user.getId(), BroadcastScope.COUNTRY, Constant.DEFAULT_TARGET_COUNTRY, PostStatus.DELETED);
                analytics.put("countryWideBroadcasts", cb != null ? cb : 0L);
            }
            Map<String, Long> scopeBreakdown = new HashMap<>();
            for (BroadcastScope scope : BroadcastScope.values()) {
                Long count = postRepository.countByUserIdAndBroadcastScopeAndStatusNot(user.getId(), scope, PostStatus.DELETED);
                scopeBreakdown.put(scope.name(), count != null ? count : 0L);
            }
            analytics.put("scopeBreakdown", scopeBreakdown);
            List<Post> userBroadcasts = postRepository.findByUserIdAndBroadcastScopeIsNotNullAndStatusNot(user.getId(), PostStatus.DELETED);
            if (userBroadcasts != null && !userBroadcasts.isEmpty()) {
                analytics.put("averageLikes",    Math.round(userBroadcasts.stream().mapToInt(Post::getLikeCount).average().orElse(0.0)    * 100.0) / 100.0);
                analytics.put("averageComments", Math.round(userBroadcasts.stream().mapToInt(Post::getCommentCount).average().orElse(0.0) * 100.0) / 100.0);
                analytics.put("averageViews",    Math.round(userBroadcasts.stream().mapToInt(Post::getViewCount).average().orElse(0.0)    * 100.0) / 100.0);
                analytics.put("averageShares",   Math.round(userBroadcasts.stream().mapToInt(Post::getShareCount).average().orElse(0.0)   * 100.0) / 100.0);
            }
            return analytics;
        } catch (Exception e) {
            log.error("Failed to get broadcast analytics for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get broadcast analytics: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post updateBroadcastTargets(Long postId, BroadcastScope newScope,
                                       List<String> targetStates, List<String> targetDistricts,
                                       List<String> targetPincodes, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            PostUtility.validateBroadcastPermission(currentUser);
            Post post = findById(postId);
            if (!post.isBroadcastPost()) throw new ValidationException("Post is not a broadcast post");
            if (!PostUtility.isPostOwner(post, currentUser) && !PostUtility.isAdmin(currentUser)) {
                throw new SecurityException("Only post creator or admin can update broadcast targets");
            }
            if (!PostUtility.postAllowsUpdates(post)) {
                throw new SecurityException("Cannot update broadcast targets for posts with status: " + post.getStatus().getDisplayName());
            }
            PostUtility.validateBroadcastScope(newScope, Constant.DEFAULT_TARGET_COUNTRY, targetStates, targetDistricts, targetPincodes);
            post.setBroadcastScope(newScope);
            post.setTargetStates(PostUtility.convertStatesToTargetString(targetStates, pinCodeLookupService));
            post.setTargetDistricts(PostUtility.convertDistrictsToTargetString(targetDistricts, pinCodeLookupService));
            post.setTargetPincodes(PostUtility.convertPincodesToTargetString(targetPincodes));
            
            // Set targetCountry based on scope
            if (newScope == BroadcastScope.COUNTRY) {
                post.setTargetCountry(Constant.DEFAULT_TARGET_COUNTRY);
            } else {
                post.setTargetCountry(null);
            }
            
            post.setUpdatedAt(new Date());
            Post updatedPost = postRepository.save(post);
            if (PostUtility.isAllIndiaGovernmentBroadcast(updatedPost)) {
                log.info("CRITICAL: Government country-wide broadcast updated: id={} user={} ({})",
                        postId, currentUser.getActualUsername(), currentUser.getRole().getName());
            }
            log.info("Broadcast targets updated: id={} scope={}", postId, newScope.getDescription());
            return updatedPost;
        } catch (ValidationException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update broadcast targets for post: {}", postId, e);
            throw new ServiceException("Failed to update broadcast targets: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // SHARE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordShare(Long postId, User user, ShareType shareType) {
        PostUtility.validatePostId(postId);
        Post post = findById(postId);
        PostShare share = postInteractionService.recordPostShare(post, user, shareType);
        log.info("Share recorded: postId={} userId={} type={}",
                postId, user != null ? user.getId() : "anon", shareType);
        return share;
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordShare(Long postId, User user) {
        return recordShare(postId, user, ShareType.LINK_COPY);
    }

    @Transactional(readOnly = true)
    public long getShareCount(Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) return 0L;
        return postInteractionService.getShareCountForPost(post);
    }

    @Transactional(readOnly = true)
    public List<Object[]> getShareBreakdown(Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) return List.of();
        return postInteractionService.getShareBreakdownForPost(post);
    }

    // =========================================================================
    // MEDIA UPDATE / REMOVE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public Post updatePostMedia(Long postId, List<MultipartFile> mediaFile, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            Post post = findById(postId);
            if (!PostUtility.isPostOwner(post, currentUser)) throw new SecurityException("Only post creator can update post media");
            if (!PostUtility.postAllowsUpdates(post)) throw new SecurityException("Cannot update media for posts with status: " + post.getStatus().getDisplayName());


            // CLOUDINARY: upload new file, delete old one asynchronously
            String fileName = null;
            if (mediaFile != null && !mediaFile.isEmpty()) {
                if (mediaFile.size() > 2) {
                    throw new MediaValidationException("Maximum 2 media files are allowed");
                }
                List<String> fileNames = new java.util.ArrayList<>();
                for (MultipartFile mf : mediaFile) {
                    if (mf != null && !mf.isEmpty()) {
                        fileNames.add(drivePostMedia.upload(mf, currentUser.getId()));
                    }
                }
                if (!fileNames.isEmpty()) {
                    fileName = String.join(",", fileNames);
                }
            }
            String oldFileName = post.getImageName();
            post.setImageName(fileName);
            post.setUpdatedAt(new Date());
            Post updatedPost = postRepository.save(post);
            if (oldFileName != null && !oldFileName.trim().isEmpty()) {
                // FIX THREAD LEAK: runAsync with no timeout holds a ForkJoin thread
                // indefinitely if Cloudinary hangs — risks pool exhaustion under load.
                // orTimeout(10s) interrupts and falls back to the retry cleanup queue.
                String[] urlsToDelete = oldFileName.split(",");
                for (String url : urlsToDelete) {
                    final String urlToDelete = url.trim();
                    if (!urlToDelete.isEmpty()) {
                        CompletableFuture
                                .runAsync(() -> drivePostMedia.delete(urlToDelete))
                                .orTimeout(10, TimeUnit.SECONDS)
                                .exceptionally(ex -> {
                                    log.warn("[Cloudinary] Async delete timed out/failed url={}: {}", urlToDelete, ex.getMessage());
                                    fileCleanupQueue.offer(urlToDelete);
                                    return null;
                                });
                    }
                }
            }
            log.info("Media updated: postId={} newMedia={}", post.getId(), fileName != null ? fileName : "removed");
            return updatedPost;
        } catch (SecurityException | MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post media: id={} user={}", postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post media: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post removePostMedia(Long postId, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            Post post = findById(postId);
            if (!PostUtility.isPostOwner(post, currentUser)) throw new SecurityException("Only post creator can remove post media");
            if (!PostUtility.postAllowsUpdates(post)) throw new SecurityException("Cannot remove media for posts with status: " + post.getStatus().getDisplayName());
            String oldFileName = post.getImageName();
            if (post.hasImage()) {
                post.setImageName(null);
                post.setUpdatedAt(new Date());
            }
            Post updatedPost = postRepository.save(post);
            if (oldFileName != null && !oldFileName.trim().isEmpty()) {
                // FIX THREAD LEAK: same timeout guard as updatePostMedia (see above)
                String[] urlsToDelete = oldFileName.split(",");
                for (String url : urlsToDelete) {
                    final String urlToDelete = url.trim();
                    if (!urlToDelete.isEmpty()) {
                        CompletableFuture
                                .runAsync(() -> drivePostMedia.delete(urlToDelete))
                                .orTimeout(10, TimeUnit.SECONDS)
                                .exceptionally(ex -> {
                                    log.warn("[Cloudinary] Async delete timed out/failed url={}: {}", urlToDelete, ex.getMessage());
                                    fileCleanupQueue.offer(urlToDelete);
                                    return null;
                                });
                    }
                }
            }
            log.info("Media removed from post: id={}", post.getId());
            return updatedPost;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to remove post media: id={} user={}", postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to remove post media: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // CONTENT UPDATE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public Post updatePostContent(Long postId, String newContent, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            String safeContent = contentValidationService.sanitizeAndValidateContent(newContent);
            PostUtility.validatePostContent(safeContent);
            Post post = findById(postId);
            if (!PostUtility.isPostOwner(post, currentUser)) throw new SecurityException("Only post creator can update post content");
            if (!PostUtility.postAllowsUpdates(post)) throw new SecurityException("Cannot update content for posts with status: " + post.getStatus().getDisplayName());
            String oldContent = post.getContent();
            post.setContent(safeContent.trim());
            post.setUpdatedAt(new Date());
            try {
                userTaggingService.updatePostTags(post, safeContent.trim());
            } catch (Exception e) {
                log.warn("Failed to update tags for post: {}", postId, e);
            }
            Post updatedPost = postRepository.save(post);
            if (PostUtility.isAllIndiaGovernmentBroadcast(updatedPost)) {
                log.info("CRITICAL: Government country-wide broadcast content updated: id={} user={} ({})",
                        postId, currentUser.getActualUsername(), currentUser.getRole().getName());
            }
            log.info("Post content updated: id={} changed={}", post.getId(), !Objects.equals(oldContent, newContent));
            return updatedPost;
        } catch (PostNotFoundException | SecurityException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post content: id={} user={}", postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post content: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post updatePostContent(Long postId, PostContentUpdateDto contentUpdateDto, User currentUser) {
        if (contentUpdateDto == null) throw new ValidationException("Content update data cannot be null");
        return updatePostContent(postId, contentUpdateDto.getContent(), currentUser);
    }

    @Transactional(rollbackFor = Exception.class)
    public Post updatePostResolution(Long postId, Boolean isResolved, User user, String updateMessage) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(user);
            Post post = findById(postId);
            boolean canUpdate = userTaggingService.isUserTaggedInPost(post, user)
                    || PostUtility.isDepartment(user) || PostUtility.isAdmin(user);
            if (!canUpdate) {
                throw new SecurityException("Only tagged users, department users, or admin can update post resolution status");
            }
            if (post.getStatus() == null) throw new ServiceException("Post status is invalid");
            PostStatus newStatus = isResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
            if (!post.getStatus().canTransitionTo(newStatus)) {
                throw new SecurityException("Cannot transition from " + post.getStatus().getDisplayName() + " to " + newStatus.getDisplayName());
            }
            if (isResolved) {
                post.markAsResolved(updateMessage != null ? updateMessage.trim() : null);
                log.info("Post id={} marked RESOLVED by user={} (id={})", postId, user.getActualUsername(), user.getId());
            } else {
                post.markAsUnresolved();
                log.info("Post id={} marked ACTIVE by user={} (id={})", postId, user.getActualUsername(), user.getId());
            }
            if (updateMessage != null && !updateMessage.trim().isEmpty()) {
                try {
                    Comment statusComment = new Comment();
                    statusComment.setText("Status Update (" + post.getStatus().getDisplayName() + "): " + updateMessage.trim());
                    String token = resolveActorToken(user);
                    if (token != null && !token.isBlank() && PostUtility.isCitizen(user)) {
                        statusComment.setActorToken(token);
                        statusComment.setUser(null);
                    } else {
                        statusComment.setUser(user);
                        statusComment.setActorToken(null);
                    }
                    statusComment.setPost(post);
                    statusComment.setCreatedAt(new Date());
                    commentRepository.save(statusComment);
                } catch (Exception e) {
                    log.warn("Failed to create status update comment for post: {}", postId, e);
                }
            }
            return postRepository.save(post);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post resolution: id={} user={}", postId, user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post resolution: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post reopenPost(Long postId, User user, String reason) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(user);
            Post post = findById(postId);

            if (!PostUtility.isPostOwner(post, user)) {
                throw new SecurityException("Only the creator of the post can reopen it");
            }
            if (post.getStatus() != PostStatus.RESOLVED) {
                throw new ValidationException("Only resolved posts can be reopened");
            }

            post.markAsUnresolved();
            log.info("Post id={} REOPENED by user={} (id={})", postId, user.getActualUsername(), user.getId());

            String safeReason = reason != null && !reason.trim().isEmpty() ? reason.trim() : "No reason provided";
            try {
                Comment statusComment = new Comment();
                statusComment.setText("Issue reopened by creator. Reason: " + safeReason);
                String token = resolveActorToken(user);
                if (token != null && !token.isBlank() && PostUtility.isCitizen(user)) {
                    statusComment.setActorToken(token);
                    statusComment.setUser(null);
                } else {
                    statusComment.setUser(user);
                    statusComment.setActorToken(null);
                }
                statusComment.setPost(post);
                statusComment.setCreatedAt(new Date());
                commentRepository.save(statusComment);
            } catch (Exception e) {
                log.warn("Failed to create reopen comment for post: {}", postId, e);
            }

            Post updatedPost = postRepository.save(post);
            try {
                notificationService.notifyPostReopened(updatedPost, user, safeReason);
            } catch (Exception e) {
                log.warn("Failed to trigger post reopen notification for post: {}", postId, e);
            }
            return updatedPost;
        } catch (SecurityException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to reopen post: id={} user={}", postId, user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to reopen post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // USER TAGGING
    // =========================================================================

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getPostsTaggedWithUser(Long userId, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUserId(userId);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getPostsTaggedWithUser", beforeId, limit);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findPostsTaggedWithUserIdAndIdLessThan(user.getId(), setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findPostsTaggedWithUserId(user.getId());
                posts = posts.stream().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            }
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getPostsTaggedWithUser", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get posts tagged with user: {}", userId, e);
            return PaginationUtils.handlePaginationError("getPostsTaggedWithUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    // =========================================================================
    // FIND / PAGING
    // =========================================================================

    @Transactional(readOnly = true)
    public Post findById(Long postId) {
        try {
            PostUtility.validatePostId(postId);
            return postRepository.findById(postId)
                    .orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        } catch (PostNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find post by ID: {}", postId, e);
            throw new ServiceException("Failed to find post: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public PostResponse getPostByIdForUser(Long postId, User currentUser) {
        try {
            Post post = findById(postId);
            if (post.getStatus() == PostStatus.DELETED || post.getStatus() == PostStatus.FLAGGED) {
                log.debug("[Access] Deleted/Flagged post={} denied for user={}",
                        postId, currentUser != null ? currentUser.getActualUsername() : "anonymous");
                throw new PostNotFoundException("Post not found with ID: " + postId);
            }
            if (post.getStatus() == PostStatus.TAKEN_DOWN) {
                log.debug("[Access] Taken down post={} denied for user={}",
                        postId, currentUser != null ? currentUser.getActualUsername() : "anonymous");
                throw new com.govlyx.AI.exception.ContentTakenDownException("This content has been removed due to a legal or copyright claim.");
            }
            if (post.getStatus() == PostStatus.RESOLVED && !canViewResolvedPost(post, currentUser)) {
                log.debug("[Access] Resolved post={} denied for user={}",
                        postId, currentUser != null ? currentUser.getActualUsername() : "anonymous");
                throw new PostNotFoundException("Post not found with ID: " + postId);
            }
            return convertToPostResponse(post, currentUser);
        } catch (PostNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed getPostByIdForUser: id={}", postId, e);
            throw new ServiceException("Failed to get post: " + e.getMessage(), e);
        }
    }

    private boolean canViewResolvedPost(Post post, User viewer) {
        if (viewer == null) return false;
        if (PostUtility.isAdmin(viewer)) return true;
        if (post.getUser() != null && post.getUser().getId().equals(viewer.getId())) return true;
        if (PostUtility.isDepartment(viewer)) return userTaggingService.isUserTaggedInPost(post, viewer);
        return false;
    }

    public void assertPostAcceptsInteractions(Post post) {
        if (post == null) {
            throw new ValidationException("Post not found.");
        }
        if (post.getStatus() == PostStatus.RESOLVED) {
            throw new ValidationException("This issue has been resolved and no longer accepts likes or comments.");
        }
        if (post.getStatus() == PostStatus.DELETED || post.getStatus() == PostStatus.FLAGGED) {
            throw new ValidationException("This post is not available for interactions.");
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getAllPosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllPosts", beforeId, limit);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.toPageable());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAllPosts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get all posts", e);
            return PaginationUtils.handlePaginationError("getAllPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getAllActivePosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllActivePosts", beforeId, limit);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.toPageable());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAllActivePosts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get all active posts", e);
            return PaginationUtils.handlePaginationError("getAllActivePosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getAllResolvedPosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllResolvedPosts", beforeId, limit);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(PostStatus.RESOLVED, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.RESOLVED, setup.toPageable());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAllResolvedPosts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get all resolved posts", e);
            return PaginationUtils.handlePaginationError("getAllResolvedPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getPostsByUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getPostsByUser", beforeId, limit);
            List<PostStatus> visibleStatuses = Arrays.asList(PostStatus.ACTIVE, PostStatus.RESOLVED);
            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByUserWithUserAndStatusInAndIdLessThanOrderByCreatedAtDesc(
                        user, visibleStatuses, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByUserWithUserAndStatusInOrderByCreatedAtDesc(user, visibleStatuses);
                posts = posts.stream().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            }
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getPostsByUser", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get posts by user: {}", user != null ? user.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("getPostsByUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getPostsByActorToken(String actorToken, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getPostsByActorToken", beforeId, limit);
            List<PostStatus> visibleStatuses = Arrays.asList(PostStatus.ACTIVE, PostStatus.RESOLVED);
            org.springframework.data.domain.Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByActorTokenAndStatusInAndIdLessThanOrderByCreatedAtDesc(
                        actorToken, visibleStatuses, setup.getSanitizedCursor(), pageable);
            } else {
                posts = postRepository.findByActorTokenAndStatusInOrderByCreatedAtDesc(
                        actorToken, visibleStatuses, pageable);
            }
            return PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
        } catch (Exception e) {
            log.error("Failed to get posts by actorToken: {}", actorToken, e);
            return PaginationUtils.handlePaginationError("getPostsByActorToken", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    public Long countPostsByActorToken(String actorToken) {
        if (actorToken == null || actorToken.isBlank()) return 0L;
        return postRepository.countByActorToken(actorToken);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getActivePostsByUser(Long userId, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getActivePostsByUser", beforeId, limit);
            List<Post> posts;
            Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
            if (setup.hasCursor()) {
                posts = postRepository.findByUserIdWithUserAndStatusAndIdLessThanOrderByCreatedAtDesc(
                        userId, PostStatus.ACTIVE, setup.getSanitizedCursor(), pageable);
            } else {
                posts = postRepository.findByUserIdWithUserAndStatusOrderByCreatedAtDesc(userId, PostStatus.ACTIVE);
                posts = posts.stream().limit(setup.getValidatedLimit() + 1).collect(Collectors.toList());
            }
            return PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
        } catch (Exception e) {
            log.error("Failed to get active posts for user: {}", userId, e);
            return PaginationUtils.handlePaginationError("getActivePostsByUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getResolvedPostsByUser(User requestingUser, Long userId, Long beforeId, Integer limit) {
        try {
            if (requestingUser == null) throw new SecurityException("Authentication required.");
            boolean isSelf = requestingUser.getId().equals(userId);
            boolean isAdmin = PostUtility.isAdmin(requestingUser);
            if (!isSelf && !isAdmin) throw new SecurityException("You can only view your own resolved posts.");
            
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getResolvedPostsByUser", beforeId, limit);
            List<Post> posts;
            Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
            if (setup.hasCursor()) {
                posts = postRepository.findByUserIdWithUserAndStatusAndIdLessThanOrderByCreatedAtDesc(
                        userId, PostStatus.RESOLVED, setup.getSanitizedCursor(), pageable);
            } else {
                posts = postRepository.findByUserIdWithUserAndStatusOrderByCreatedAtDesc(userId, PostStatus.RESOLVED);
                posts = posts.stream().limit(setup.getValidatedLimit() + 1).collect(Collectors.toList());
            }
            return PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get resolved posts for user: {}", userId, e);
            return PaginationUtils.handlePaginationError("getResolvedPostsByUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    public Long countActivePosts() {
        try {
            Long count = postRepository.countByStatus(PostStatus.ACTIVE);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Failed to count active posts", e);
            throw new ServiceException("Failed to count active posts: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Long countResolvedPosts() {
        try {
            Long count = postRepository.countByStatus(PostStatus.RESOLVED);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Failed to count resolved posts", e);
            throw new ServiceException("Failed to count resolved posts: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getPostsWithMultipleUserTags(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getPostsWithMultipleUserTags", beforeId, limit);
            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findPostsWithMultipleUserTagsAndIdLessThan(PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findPostsWithMultipleUserTags(PostStatus.ACTIVE);
                posts = posts.stream().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            }
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getPostsWithMultipleUserTags", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get posts with multiple user tags", e);
            return PaginationUtils.handlePaginationError("getPostsWithMultipleUserTags", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Post> getTrendingPosts(int days, Long beforeId, Integer limit) {
        try {
            if (days <= 0) throw new ValidationException("Days must be positive");
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getTrendingPosts", beforeId, limit, Constant.DEFAULT_FEED_LIMIT, 1000);
            LocalDateTime startDate = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findTrendingPostsWithCursor(Timestamp.valueOf(startDate), PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findTrendingPosts(Timestamp.valueOf(startDate), PostStatus.ACTIVE, setup.toPageable());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getTrendingPosts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get trending posts: days={} beforeId={} limit={}", days, beforeId, limit, e);
            return PaginationUtils.handlePaginationError("getTrendingPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post tagUsersToPost(Long postId, List<Long> userIds, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            if (userIds == null || userIds.isEmpty()) throw new ValidationException("User IDs list cannot be empty");
            for (Long userId : userIds) PostUtility.validateUserId(userId);
            Post post = findById(postId);
            if (!PostUtility.postAllowsUpdates(post)) throw new SecurityException("Cannot add tags to posts with status: " + post.getStatus().getDisplayName());
            if (!PostUtility.canUserModifyPostTags(post, currentUser)) throw new SecurityException("Only post creator, department users, or admin can add user tags");
            List<User> usersToTag = userRepository.findAllById(userIds);
            if (usersToTag.size() != userIds.size()) {
                List<Long> foundIds   = usersToTag.stream().map(User::getId).collect(Collectors.toList());
                List<Long> missingIds = userIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
                throw new ValidationException("Users not found with IDs: " + missingIds);
            }
            int successCount = 0;
            for (User userToTag : usersToTag) {
                try {
                    userTaggingService.addUserTag(post, userToTag);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Failed to tag user: {} to post: {}", userToTag.getActualUsername(), postId, e);
                }
            }
            log.info("Added {} user tags to post id={} (attempted: {})", successCount, post.getId(), usersToTag.size());
            for (User taggedUser : usersToTag) {
                try {
                    notificationService.notifyUserTagged(post, taggedUser, currentUser);
                } catch (Exception e) {
                    log.warn("Failed to send tag notification to user={}: {}", taggedUser.getActualUsername(), e.getMessage());
                }
            }
            return post;
        } catch (PostNotFoundException | SecurityException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to tag users to post: {} by user: {}", postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to tag users to post: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post removeUserTagFromPost(Long postId, Long userId, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUserId(userId);
            PostUtility.validateUser(currentUser);
            Post post = findById(postId);
            User userToRemove = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
            if (!PostUtility.postAllowsUpdates(post)) throw new SecurityException("Cannot remove tags from posts with status: " + post.getStatus().getDisplayName());
            if (!PostUtility.canUserRemovePostTag(post, currentUser, userId)) throw new SecurityException("Insufficient permissions to remove user tag");
            userTaggingService.removeUserTag(post, userToRemove);
            log.info("Removed user tag: userId={} from post id={} by user={}", userId, post.getId(), currentUser.getActualUsername());
            return post;
        } catch (PostNotFoundException | UserNotFoundException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to remove user tag: postId={} userId={} by user={}", postId, userId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to remove user tag from post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // MEDIA HELPERS
    // =========================================================================

    /**
     * Returns the Cloudinary URL as-is (it IS the public CDN path).
     */
    public String getMediaFilePath(String fileName) {
        return drivePostMedia.getPath(fileName);
    }

    public boolean isImageFile(String fileName)      { return PostUtility.isImageFile(fileName); }
    public boolean isVideoFile(String fileName)      { return PostUtility.isVideoFile(fileName); }
    public String getMediaType(String fileName)      { return PostUtility.getMediaType(fileName); }
    public Map<String, Object> getMediaConstraints() { return PostUtility.createMediaConstraints(maxImageSize, maxVideoSize); }
    
    public Long countPostsByUser(User user) {
        if (user == null) return 0L;
        java.util.List<PostStatus> visibleStatuses = java.util.Arrays.asList(PostStatus.ACTIVE, PostStatus.RESOLVED);
        String actorToken = resolveActorToken(user);
        if (actorToken != null && !actorToken.isBlank()) {
            return postRepository.countByActorTokenAndStatusIn(actorToken, visibleStatuses);
        }
        return postRepository.countByUserIdAndStatusIn(user.getId(), visibleStatuses);
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public void softDeletePost(Long postId, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            Post post = findById(postId);
            String actorToken = resolveActorToken(currentUser);
            if (!PostUtility.isPostOwner(post, currentUser, actorToken) && !PostUtility.isAdmin(currentUser)) {
                throw new SecurityException("Only the post creator or an admin can delete this post.");
            }
            if (!PostUtility.postAllowsUpdates(post)) {
                throw new SecurityException("Cannot delete posts with status: " + post.getStatus().getDisplayName());
            }
            try {
                postInteractionService.cleanupForPostDeletion(post);
            } catch (Exception e) {
                log.warn("Failed to clean up interactions for post={}: {}", postId, e.getMessage());
            }
            try {
                notificationService.deleteNotificationsForPost(postId, false);
            } catch (Exception e) {
                log.warn("Failed to clean up notifications for post={}: {}", postId, e.getMessage());
            }
            post.setStatus(PostStatus.DELETED);
            post.setUpdatedAt(new Date());
            postRepository.save(post);
            log.info("Post soft-deleted: id={} by user={}", postId, currentUser.getActualUsername());
        } catch (SecurityException | PostNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to soft-delete post: id={} user={}", postId,
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to delete post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // FILE CLEANUP QUEUE  (deletes from Cloudinary)
    // =========================================================================

    // FIX 2 — drain the retry queue every 5 minutes (previously never called).
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300_000)
    public void processFileCleanupQueue() {
        int processedCount = 0;
        while (!fileCleanupQueue.isEmpty() && processedCount < 10) {
            String urlOrId = fileCleanupQueue.poll();
            if (urlOrId != null) {
                try {
                    drivePostMedia.delete(urlOrId);
                    log.info("Cleaned up Cloudinary file from retry queue: {}", urlOrId);
                    processedCount++;
                } catch (Exception e) {
                    log.warn("Failed to cleanup Cloudinary file: {}", urlOrId, e);
                }
            }
        }
        if (processedCount > 0) {
            log.info("Processed {} files from cleanup queue, {} remaining", processedCount, fileCleanupQueue.size());
        }
    }

    @SuppressWarnings("unused")
    private void scheduleFileCleanupRetry(String urlOrId) {
        if (urlOrId != null && !urlOrId.trim().isEmpty()) {
            fileCleanupQueue.offer(urlOrId);
            if (fileCleanupQueue.size() > 1000) {
                log.warn("Cleanup queue full, dropping oldest entry: {}", fileCleanupQueue.poll());
            }
        }
    }

    // =========================================================================
    // DTO CONVERSION
    // =========================================================================

    public PostResponse convertToPostResponse(Post post, User currentUser) {
        if (post == null) return null;
        boolean isLiked    = currentUser != null && postInteractionService.hasUserLikedPost(post, currentUser);
        boolean isDisliked = currentUser != null && postInteractionService.hasUserDislikedPost(post, currentUser);
        boolean isSaved    = currentUser != null && postInteractionService.hasSavedBroadcastPost(post, currentUser);
        return convertToPostResponseWithInteractions(post, currentUser, isLiked, isDisliked, isSaved);
    }

    public PostResponse convertToPostResponseWithInteractions(
            Post post, User currentUser, boolean isLiked, boolean isDisliked, boolean isSaved) {
        return convertToPostResponseWithInteractions(post, currentUser, isLiked, isDisliked, isSaved, null);
    }

    public PostResponse convertToPostResponseWithInteractions(
            Post post, User currentUser, boolean isLiked, boolean isDisliked, boolean isSaved, List<UserTag> preloadedTags) {
        try {
            if (post == null) return null;

            int shareCount = post.getShareCount();

            String resolvedAuthorUsername = "Anonymous";
            if (post.getAuthorUsername() != null && !post.getAuthorUsername().isBlank()) {
                resolvedAuthorUsername = post.getAuthorUsername();
            } else if (post.getUser() != null && post.getUser().getActualUsername() != null && !post.getUser().getActualUsername().isBlank()) {
                resolvedAuthorUsername = post.getUser().getActualUsername();
            }

            String actorToken = resolveActorToken(currentUser);
            PostResponse.PostResponseBuilder builder = PostResponse.builder()
                    .id(post.getId())
                    .content(post.getContent())
                    .status(post.getStatus())
                    .createdAt(post.getCreatedAt())
                    .updatedAt(post.getUpdatedAt())

                    // Cloudinary secure URL stored here (or null)
                    .imageName(post.getImageName())
                    .hasImage(post.hasImage())
                    .mediaType(post.hasImage() ? PostUtility.getMediaType(post.getImageName()) : null)

                    .isResolved(post.isResolved())
                    .resolvedAt(post.getResolvedAt())

                    .userId(post.getUser() != null ? post.getUser().getId() : null)
                    .username(resolvedAuthorUsername)
                    .userDisplayName(resolvedAuthorUsername)
                    .userProfileImage(post.getAuthorProfileImage() != null ? post.getAuthorProfileImage() : (post.getUser() != null ? post.getUser().getProfileImage() : null))
                    .userPincode(post.getAuthorPincode() != null ? post.getAuthorPincode() : (post.getUser() != null ? post.getUser().getPincode() : null))

                    .broadcastScope(post.getBroadcastScope())
                    .broadcastScopeDescription(post.getBroadcastScopeDescription())
                    .isBroadcastPost(post.isBroadcastPost())
                    .isGovernmentBroadcast(post.isGovernmentBroadcast())
                    .countryWideBroadcast(post.isCountryWideBroadcast())
                    .targetCountry(post.getTargetCountry())
                    .targetStates(PostUtility.resolvePrefixesToStateNames(post.getTargetStates()))
                    .targetDistricts(PostUtility.resolvePrefixesToDistrictNames(post.getTargetDistricts(), pinCodeLookupService))
                    .targetPincodes(post.getTargetPincodesList())

                    .likeCount(post.getLikeCount())
                    .dislikeCount(post.getDislikeCount())
                    .commentCount(post.getCommentCount())
                    .viewCount(post.getViewCount())
                    .shareCount(shareCount)
                    .saveCount(post.getSaveCount())
                    .taggedUserCount(post.getTaggedUserCount())

                    .isLikedByCurrentUser(isLiked)
                    .isDislikedByCurrentUser(isDisliked)
                    .isSavedByCurrentUser(isSaved)
                    .isViewedByCurrentUser(false)

                    .statusDisplayName(post.getStatus() != null ? post.getStatus().getDisplayName() : null)
                    .canBeResolved(post.getStatus() == PostStatus.ACTIVE)
                    .allowsUpdates(PostUtility.postAllowsUpdates(post))
                    .isEligibleForDisplay(PostUtility.isPostEligibleForDisplay(post))

                    .canLike(post.getStatus() == PostStatus.ACTIVE)
                    .canComment(post.getStatus() == PostStatus.ACTIVE)
                    .canShare(post.getStatus() == PostStatus.ACTIVE)
                    .canSave(post.isGovernmentBroadcast() && post.getStatus() == PostStatus.ACTIVE)
                    .canDelete(currentUser != null && (PostUtility.isPostOwner(post, currentUser, actorToken) || PostUtility.isAdmin(currentUser)))

                    .timeAgo(PostUtility.calculateTimeAgo(post.getCreatedAt()))
                    .isVisibleToCurrentUser(currentUser != null);

            List<String> taggedUsernames = Collections.emptyList();
            List<PostResponse.TaggedUserInfo> taggedUsers = Collections.emptyList();
            boolean isDepartmentTagged = false;
            
            if (preloadedTags != null && !preloadedTags.isEmpty()) {
                List<User> data = preloadedTags.stream()
                        .filter(Objects::nonNull)
                        .map(UserTag::getTaggedUser)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                taggedUsernames = data.stream().map(User::getActualUsername).collect(Collectors.toList());
                isDepartmentTagged = data.stream().anyMatch(User::isDepartment);
                taggedUsers = data.stream()
                        .map(u -> PostResponse.TaggedUserInfo.builder()
                                .userId(u.getId())
                                .username(u.getActualUsername())
                                .profileImage(u.getProfileImage())
                                .isDepartment(u.isDepartment())
                                .build())
                        .collect(Collectors.toList());
            } else if (preloadedTags == null && post.getContent() != null && post.getContent().contains("@")) {
                try {
                    PaginatedResponse<User> taggedUsersResponse = userTaggingService.getTaggedUsersInPost(post, null, Constant.MAX_TAGS_PER_POST * 10);
                    if (taggedUsersResponse != null && taggedUsersResponse.getData() != null) {
                        List<User> data = taggedUsersResponse.getData();
                        taggedUsernames = data.stream().map(User::getActualUsername).collect(Collectors.toList());
                        
                        // Check if any tagged user is a department
                        isDepartmentTagged = data.stream().anyMatch(User::isDepartment);
                        
                        taggedUsers = data.stream()
                                .map(u -> PostResponse.TaggedUserInfo.builder()
                                        .userId(u.getId())
                                        .username(u.getActualUsername())
                                        .profileImage(u.getProfileImage())
                                        .isDepartment(u.isDepartment())
                                        .build())
                                .collect(Collectors.toList());
                    }
                } catch (Exception e) {
                    log.warn("Failed to get tagged users for post: {}", post.getId(), e);
                }
            }
            builder.taggedUsernames(taggedUsernames);
            builder.taggedUsers(taggedUsers);
            builder.isDepartmentTagged(isDepartmentTagged);

            // =========================================================================
            // FEED FILTERING & MODERATION CHECK
            // =========================================================================
            boolean hidden = false;
            String reason = null;

            if (currentUser != null) {
                // 1. Muted Words Check
                if (currentUser.getMutedWords() != null && !currentUser.getMutedWords().trim().isEmpty()) {
                    String[] mutedWords = currentUser.getMutedWords().split(",");
                    String contentLower = post.getContent().toLowerCase();
                    for (String word : mutedWords) {
                        if (contentLower.contains(word.trim().toLowerCase())) {
                            hidden = true;
                            reason = "Contains a muted word: " + word.trim();
                            break;
                        }
                    }
                }

                // 2. Profanity Filter Check
                if (!hidden && ("STRICT".equalsIgnoreCase(currentUser.getProfanityFilterLevel()) ||
                                "BLUR".equalsIgnoreCase(currentUser.getProfanityFilterLevel()))) {
                    BadWordService.BadWordCheckResult profanityResult = contentValidationService.checkContent(post.getContent());
                    if (!profanityResult.isAllowed()) {
                        hidden = true;
                        reason = "Contains potentially sensitive or profane language";
                    }
                }
            }

            builder.contentHidden(hidden)
                   .hiddenReason(reason);

            return builder.build();

        } catch (Exception e) {
            log.error("Failed to convert post {} to response", post != null ? post.getId() : "null", e);
            return null;
        }
    }

    public List<PostResponse> convertToPostResponseBatch(List<Post> posts, User currentUser) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = posts.stream()
                .filter(Objects::nonNull)
                .map(Post::getId)
                .collect(Collectors.toList());

        Set<Long> likedPostIds = currentUser != null ? postInteractionService.getBatchLikedPostIds(currentUser, postIds) : Collections.emptySet();
        Set<Long> dislikedPostIds = currentUser != null ? postInteractionService.getBatchDislikedPostIds(currentUser, postIds) : Collections.emptySet();
        Set<Long> savedPostIds = currentUser != null ? postInteractionService.getBatchSavedPostIds(currentUser, postIds) : Collections.emptySet();

        Map<Long, List<UserTag>> tagsByPostId = Collections.emptyMap();
        List<Long> taggedPostIds = posts.stream()
                .filter(p -> p != null && p.getContent() != null && p.getContent().contains("@"))
                .map(Post::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!taggedPostIds.isEmpty()) {
            try {
                List<UserTag> tags = userTagRepository.findByPostIdsAndIsActiveTrueFetch(taggedPostIds);
                if (tags != null) {
                    tagsByPostId = tags.stream()
                            .filter(t -> t.getPost() != null && t.getPost().getId() != null && t.getTaggedUser() != null)
                            .collect(Collectors.groupingBy(t -> t.getPost().getId()));
                }
            } catch (Exception e) {
                log.warn("Failed to batch fetch tags for posts: {}", e.getMessage());
            }
        }
        final Map<Long, List<UserTag>> fTagsByPostId = tagsByPostId;

        return posts.stream()
                .filter(Objects::nonNull)
                .map(p -> {
                    boolean isLiked = likedPostIds.contains(p.getId());
                    boolean isDisliked = dislikedPostIds.contains(p.getId());
                    boolean isSaved = savedPostIds.contains(p.getId());
                    List<UserTag> preloadedTags = fTagsByPostId.get(p.getId());
                    return convertToPostResponseWithInteractions(p, currentUser, isLiked, isDisliked, isSaved, preloadedTags);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PostResponse> convertToPostResponses(List<Post> posts, User currentUser, int limit) {
        if (posts == null || posts.isEmpty()) return PaginationUtils.createEmptyResponse(limit);
        List<PostResponse> postResponses = convertToPostResponseBatch(posts, currentUser);
                
        // ── Batch Auto-Translate ──
        if (currentUser != null && Boolean.TRUE.equals(currentUser.getAutoTranslate()) && currentUser.getPreferredLanguage() != null) {
            try {
                translationService.translatePosts(postResponses, currentUser.getPreferredLanguage());
            } catch (Exception e) {
                log.warn("Failed to batch translate posts: {}", e.getMessage());
            }
        }
                
        return PaginationUtils.createIdBasedResponse(postResponses, limit, PostResponse::getId);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PostResponse> convertPaginatedPostsToResponses(PaginatedResponse<Post> paginatedPosts, User currentUser) {
        if (paginatedPosts == null || paginatedPosts.getData() == null) return PaginationUtils.createEmptyResponse(0);
        List<PostResponse> postResponses = convertToPostResponseBatch(paginatedPosts.getData(), currentUser);
                
        // ── Batch Auto-Translate ──
        if (currentUser != null && Boolean.TRUE.equals(currentUser.getAutoTranslate()) && currentUser.getPreferredLanguage() != null) {
            try {
                translationService.translatePosts(postResponses, currentUser.getPreferredLanguage());
            } catch (Exception e) {
                log.warn("Failed to batch translate posts: {}", e.getMessage());
            }
        }
                
        return PaginatedResponse.of(postResponses, paginatedPosts.isHasMore(), paginatedPosts.getNextCursor(), paginatedPosts.getLimit());
    }

    // =========================================================================
    // LOCAL FEED / RECOMMENDATION
    // =========================================================================

    @Transactional(readOnly = true)
    public PaginatedResponse<PostResponse> getLocalFeed(User user, FeedSort sort, Long beforeId, int limit, String requestPincode, String targetPincode) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);

            int validLimit = Math.max(1, Math.min(limit, 50));
            int candidatePool = validLimit * 2; // fetch extra for scoring headroom

            String effectivePincode = null;
            if (requestPincode != null && !requestPincode.trim().isEmpty()) {
                effectivePincode = requestPincode.trim();
            } else if (targetPincode != null && !targetPincode.trim().isEmpty()) {
                effectivePincode = targetPincode.trim();
            } else if (user.hasPincode()) {
                effectivePincode = user.getPincode();
            }

            Map<Long, Post> merged = new LinkedHashMap<>();

            if (effectivePincode != null) {
                String districtPrefix = effectivePincode.length() >= 3 ? effectivePincode.substring(0, 3) : effectivePincode;
                String statePrefix    = effectivePincode.length() >= 2 ? effectivePincode.substring(0, 2) : effectivePincode;

                java.util.Set<String> nearbyPincodes = pinCodeLookupService.getNearbyPincodeStrings(effectivePincode, 50.0, 100);
                if (nearbyPincodes == null || nearbyPincodes.isEmpty()) {
                    nearbyPincodes = new java.util.HashSet<>();
                    nearbyPincodes.add(effectivePincode);
                }

                // ─── Single optimized query handles Exact + Nearby + Escalated Posts ───
                postRepository.findLocationFeedPosts(
                        effectivePincode, nearbyPincodes, districtPrefix, statePrefix, PageRequest.of(0, candidatePool)
                ).forEach(p -> merged.put(p.getId(), p));
            }

            // ─── Inject user's own posts so they always see their content ───
            if (user != null) {
                try {
                    postRepository.findByUserWithUserAndStatusOrderByCreatedAtDesc(user, PostStatus.ACTIVE)
                            .forEach(p -> merged.putIfAbsent(p.getId(), p));
                } catch (Exception e) {
                    log.warn("[LocalFeed] Own-post injection failed for userId={}: {}", user != null ? user.getId() : null, e.getMessage());
                }
            }

            // ─── Fallback: If no location-specific issue posts exist, fetch active global issue posts ───
            if (merged.isEmpty()) {
                try {
                    postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, PageRequest.of(0, candidatePool))
                            .forEach(p -> merged.put(p.getId(), p));
                } catch (Exception e) {
                    log.warn("[LocalFeed] Global issue fallback failed: {}", e.getMessage());
                }
            }

            if (merged.isEmpty()) {
                log.info("[LocalFeed] Feed empty for userId={}", user != null ? user.getId() : null);
                return PaginationUtils.createEmptyResponse(validLimit);
            }

            String districtPrefix = (effectivePincode != null && effectivePincode.length() >= 3) ? effectivePincode.substring(0, 3) : null;
            String statePrefix    = (effectivePincode != null && effectivePincode.length() >= 2) ? effectivePincode.substring(0, 2) : null;

            return buildCitizenFeedResponse(
                    new java.util.ArrayList<>(merged.values()), user, sort,
                    effectivePincode, districtPrefix, statePrefix,
                    validLimit, "LOCAL_AREA");

        } catch (Exception e) {
            log.error("[LocalFeed] Failed for user={}", user != null ? user.getActualUsername() : "null", e);
            int safe = Math.max(1, Math.min(limit, 50));
            return PaginationUtils.createEmptyResponse(safe);
        }
    }

    /**
     * Scores, ranks, and paginates a pool of citizen issue posts for the Location tab.
     */
    private PaginatedResponse<PostResponse> buildCitizenFeedResponse(
            List<Post> pool, User user, FeedSort sort,
            String userPincode, String districtPrefix, String statePrefix,
            int validLimit, String scopeLabel) {

        List<Post> ranked;
        if (sort == FeedSort.NEW) {
            ranked = pool.stream()
                    .filter(p -> p.getStatus() == PostStatus.ACTIVE)
                    .sorted(Comparator.comparingLong((Post p) -> p.getCreatedAt() != null ? -p.getCreatedAt().getTime() : 0L))
                    .limit(validLimit)
                    .collect(Collectors.toList());
        } else if (sort == FeedSort.TOP) {
            ranked = pool.stream()
                    .filter(p -> p.getStatus() == PostStatus.ACTIVE)
                    .sorted(Comparator.comparingDouble((Post p) -> {
                        int likes = p.getLikeCount();
                        int comments = p.getCommentCount();
                        int shares = p.getShareCount();
                        return -(likes * Constant.POST_WEIGHT_LIKE + comments * Constant.POST_WEIGHT_COMMENT + shares * 3.0);
                    }))
                    .limit(validLimit)
                    .collect(Collectors.toList());
        } else {
            long now72hAgo = System.currentTimeMillis() - 72L * 60 * 60 * 1000;
            long now7dAgo  = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
            List<Post> active = pool.stream()
                    .filter(p -> p.getStatus() == PostStatus.ACTIVE)
                    .collect(Collectors.toList());
            List<Post> hotPool = active.stream()
                    .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().getTime() >= now72hAgo)
                    .collect(Collectors.toList());
            if (hotPool.size() < 5) {
                hotPool = active.stream()
                        .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().getTime() >= now7dAgo)
                        .collect(Collectors.toList());
            }
            if (hotPool.isEmpty()) {
                hotPool = active;
            }
            ranked = hotPool.stream()
                    .sorted(Comparator.comparingDouble((Post p) -> computeIssueScore(p, userPincode, districtPrefix, statePrefix)).reversed())
                    .limit(validLimit)
                    .collect(Collectors.toList());
        }

        log.debug("[LocalFeed] userId={} pincode={} scope={} pool={} returned={}",
                user.getId(), userPincode, scopeLabel, pool.size(), ranked.size());

        List<PostResponse> responses = convertToPostResponseBatch(ranked, user);

        // ── Batch Auto-Translate ──
        if (user != null && Boolean.TRUE.equals(user.getAutoTranslate()) && user.getPreferredLanguage() != null) {
            try {
                translationService.translatePosts(responses, user.getPreferredLanguage());
            } catch (Exception e) {
                log.warn("Failed to batch translate posts: {}", e.getMessage());
            }
        }

        boolean hasMore    = responses.size() == validLimit;
        Long    nextCursor = hasMore && !responses.isEmpty() ? responses.get(responses.size() - 1).getId() : null;
        return PaginatedResponse.of(responses, hasMore, nextCursor, validLimit);
    }

    /**
     * Official government feed — exclusively departments/admins, strictly geo-targeted.
     * Waterfall: User's Pincode -> User's District -> User's State -> National.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<PostResponse> getOfficialFeed(User user, FeedSort sort, Long beforeId, int limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);

            int validLimit = Math.max(1, Math.min(limit, 50));

            // Resolve user's pincode
            String userPincode = user.getPincode();

            // Resolved prefixes (for matching against target_districts and target_states)
            String districtPrefix = (userPincode != null && userPincode.length() >= 3)
                    ? userPincode.substring(0, 3) : null;
            String statePrefix    = (userPincode != null && userPincode.length() >= 2)
                    ? userPincode.substring(0, 2) : null;

            log.debug("[OfficialFeed] userId={} pincode={} districtPrefix={} statePrefix={}",
                    user.getId(), userPincode, districtPrefix, statePrefix);

            Map<Long, Post> merged = new LinkedHashMap<>();

            // 1. Area Level (Pincode match) — Only if user has pincode
            if (userPincode != null) {
                List<Post> areaGov = postRepository.findOfficialAreaBroadcasts(
                        BroadcastScope.AREA, PostStatus.ACTIVE, userPincode);
                areaGov.forEach(p -> merged.put(p.getId(), p));
            }

            // 2. District Level — match by 3-digit district prefix (e.g. "411")
            if (merged.size() < validLimit && districtPrefix != null) {
                List<Post> districtGov = postRepository.findOfficialDistrictBroadcasts(
                        BroadcastScope.DISTRICT, PostStatus.ACTIVE, districtPrefix);
                districtGov.forEach(p -> merged.putIfAbsent(p.getId(), p));
            }

            // 3. State Level — match by 2-digit state prefix (e.g. "40")
            if (merged.size() < validLimit && statePrefix != null) {
                List<Post> stateGov = postRepository.findOfficialStateBroadcasts(
                        BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix);
                stateGov.forEach(p -> merged.putIfAbsent(p.getId(), p));
            }

            // 4. National Level
            if (merged.size() < validLimit) {
                List<Post> nationalGov = postRepository.findOfficialCountryBroadcasts(
                        BroadcastScope.COUNTRY, PostStatus.ACTIVE);
                nationalGov.forEach(p -> merged.putIfAbsent(p.getId(), p));
            }

            // 5. Absolute fallback — ALL department/admin broadcasts (catches posts with
            //    null targetCountry or non-standard broadcastScope configurations)
            if (merged.isEmpty()) {
                log.debug("[OfficialFeed] All geo-tiers empty — loading all official broadcasts for userId={}", user.getId());
                postRepository.findAllOfficialBroadcasts(
                        PostStatus.ACTIVE, PageRequest.of(0, validLimit * 2)
                ).forEach(p -> merged.putIfAbsent(p.getId(), p));
            }

            // Deduplicate and filter by cursor (beforeId)
            List<Post> allOfficial = merged.values().stream()
                    .filter(p -> beforeId == null || p.getId() < beforeId)
                    .collect(Collectors.toList());
            List<Post> sortedOfficial;
            if (sort == FeedSort.NEW) {
                // --- NEW (Chronological) ---
                sortedOfficial = allOfficial.stream()
                        .sorted(Comparator.comparingLong((Post p) -> p.getCreatedAt() != null ? -p.getCreatedAt().getTime() : -p.getId()))
                        .collect(Collectors.toList());
            } else if (sort == FeedSort.TOP) {
                // --- TOP (All-Time Engagement Score) ---
                sortedOfficial = allOfficial.stream()
                        .sorted(Comparator.comparingDouble((Post p) -> {
                            int likes = p.getLikeCount();
                            int comments = p.getCommentCount();
                            int shares = p.getShareCount();
                            return -(likes * Constant.POST_WEIGHT_LIKE + comments * Constant.POST_WEIGHT_COMMENT + shares * 3.0);
                        }))
                        .collect(Collectors.toList());
            } else { 
                // --- HOT (Trending Official Updates) ---
                long now72hAgo = System.currentTimeMillis() - 72L * 60 * 60 * 1000;
                long now7dAgo  = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
                // Attempt to filter for posts in the last 72 hours
                List<Post> hotPool = allOfficial.stream()
                        .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().getTime() >= now72hAgo)
                        .collect(Collectors.toList());
                // Fallback to last 7 days
                if (hotPool.size() < 5) {
                    hotPool = allOfficial.stream()
                            .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().getTime() >= now7dAgo)
                            .collect(Collectors.toList());
                }
                // Final fallback: all posts
                if (hotPool.isEmpty()) {
                    hotPool = allOfficial;
                }
                // Sort the trending updates by engagement
                sortedOfficial = hotPool.stream()
                        .sorted(Comparator.comparingDouble((Post p) -> {
                            int likes = p.getLikeCount();
                            int comments = p.getCommentCount();
                            int shares = p.getShareCount();
                            return -(likes * Constant.POST_WEIGHT_LIKE + comments * Constant.POST_WEIGHT_COMMENT + shares * 3.0);
                        }))
                        .collect(Collectors.toList());
            }
            // Paginate the sorted list
            List<Post> finalPosts = sortedOfficial.stream()
                    .limit(validLimit)
                    .collect(Collectors.toList());

            List<PostResponse> responses = convertToPostResponseBatch(finalPosts, user);

            // ── Batch Auto-Translate ──
            if (user != null && Boolean.TRUE.equals(user.getAutoTranslate()) && user.getPreferredLanguage() != null) {
                try {
                    translationService.translatePosts(responses, user.getPreferredLanguage());
                } catch (Exception e) {
                    log.warn("Failed to batch translate posts: {}", e.getMessage());
                }
            }

            boolean hasMore = responses.size() == validLimit;
            Long nextCursor = hasMore && !responses.isEmpty() ? responses.get(responses.size() - 1).getId() : null;
            return PaginatedResponse.of(responses, hasMore, nextCursor, validLimit);

        } catch (Exception e) {
            log.error("[OfficialFeed] Failed for user={}", user != null ? user.getActualUsername() : "null", e);
            return PaginationUtils.createEmptyResponse(Math.max(1, Math.min(limit, 50)));
        }
    }


    @Transactional(readOnly = true)
    public PaginatedResponse<PostResponse> getIssuePostFeed(User user, Integer limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);

            int validLimit = (limit == null || limit <= 0) ? Constant.DEFAULT_FEED_LIMIT : Math.min(limit, 100);

            String userPincode    = user.getPincode();
            String districtPrefix = (userPincode != null && userPincode.length() >= 3) ? userPincode.substring(0, 3) : null;
            String statePrefix    = (userPincode != null && userPincode.length() >= 2) ? userPincode.substring(0, 2) : null;

            List<Post> candidates = new ArrayList<>();

            if (userPincode != null && !userPincode.isBlank()) {
                candidates.addAll(postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(
                        BroadcastScope.AREA, PostStatus.ACTIVE, userPincode));
                if (districtPrefix != null) {
                    candidates.addAll(postRepository.findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(
                            BroadcastScope.DISTRICT, PostStatus.ACTIVE, districtPrefix));
                }
                if (statePrefix != null) {
                    candidates.addAll(postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(
                            BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix));
                }
            } else {
                log.debug("[IssueFeed] userId={} has no pincode — skipping geo tiers", user.getId());
            }

            candidates.addAll(postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(
                    BroadcastScope.COUNTRY, PostStatus.ACTIVE, PageRequest.of(0, 50)));

            Map<Long, Post> deduped = candidates.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(Post::getId, p -> p, (a, b) -> a, LinkedHashMap::new),
                            map -> map));

            try {
                List<Post> ownPosts = postRepository.findByUserWithUserAndStatusOrderByCreatedAtDesc(user, PostStatus.ACTIVE);
                ownPosts.forEach(p -> deduped.putIfAbsent(p.getId(), p));
                if (!ownPosts.isEmpty()) {
                    log.debug("[IssueFeed] Injected {} own posts for userId={}", ownPosts.size(), user.getId());
                }
            } catch (Exception e) {
                log.warn("[IssueFeed] Own-post injection failed for userId={}: {}", user.getId(), e.getMessage());
            }

            if (deduped.size() < validLimit) {
                log.info("[IssueFeed] Thin pool ({}) for userId={} — loading all active posts (sparse platform)",
                        deduped.size(), user.getId());
                try {
                    PaginatedResponse<Post> allActive = getAllActivePosts(null, validLimit * 2);
                    if (allActive != null && allActive.getData() != null) {
                        allActive.getData().forEach(p -> deduped.putIfAbsent(p.getId(), p));
                    }
                } catch (Exception e) {
                    log.warn("[IssueFeed] Sparse fallback failed for userId={}: {}", user.getId(), e.getMessage());
                }
            }

            final String fp = userPincode, fd = districtPrefix, fs = statePrefix;
            List<Post> ranked = deduped.values().stream()
                    .sorted(Comparator.comparingDouble((Post p) -> computeIssueScore(p, fp, fd, fs)).reversed())
                    .limit(validLimit)
                    .collect(Collectors.toList());

            List<PostResponse> responses = convertToPostResponseBatch(ranked, user);

            // ── Batch Auto-Translate ──
            if (user != null && Boolean.TRUE.equals(user.getAutoTranslate()) && user.getPreferredLanguage() != null) {
                try {
                    translationService.translatePosts(responses, user.getPreferredLanguage());
                } catch (Exception e) {
                    log.warn("Failed to batch translate posts: {}", e.getMessage());
                }
            }

            log.info("[IssueFeed] user={} pincode={} candidates={} deduped={} returned={}",
                    user.getActualUsername(), userPincode != null ? userPincode : "none",
                    candidates.size(), deduped.size(), responses.size());

            return PaginatedResponse.of(responses, false, null, validLimit);

        } catch (Exception e) {
            log.error("[IssueFeed] Failed for user={}", user != null ? user.getActualUsername() : "null", e);
            try {
                int fallback = (limit == null || limit <= 0) ? Constant.DEFAULT_FEED_LIMIT : Math.min(limit, 100);
                PaginatedResponse<Post> allActive = getAllActivePosts(null, fallback);
                return convertPaginatedPostsToResponses(allActive, user);
            } catch (Exception ex) {
                int fallback = (limit == null || limit <= 0) ? Constant.DEFAULT_FEED_LIMIT : Math.min(limit, 100);
                return PaginationUtils.createEmptyResponse(fallback);
            }
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PostResponse> getIssueRecommendationFeed(User user, Integer limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);

            int validLimit = (limit == null || limit <= 0)
                    ? Constant.ISSUE_RECOMMENDATION_DEFAULT_LIMIT
                    : Math.min(limit, Constant.ISSUE_RECOMMENDATION_MAX_LIMIT);

            String userPincode    = user.getPincode();
            String districtPrefix = (userPincode != null && userPincode.length() >= 3) ? userPincode.substring(0, 3) : null;
            String statePrefix    = (userPincode != null && userPincode.length() >= 2) ? userPincode.substring(0, 2) : null;

            Map<Long, Post> merged = new LinkedHashMap<>();

            if (userPincode != null && !userPincode.isBlank()) {
                List<Post> tier1ExactArea = postRepository
                        .findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(
                                BroadcastScope.AREA, PostStatus.ACTIVE, userPincode);
                tier1ExactArea.forEach(p -> merged.put(p.getId(), p));

                boolean nearbyNeeded = tier1ExactArea.size() < Constant.ISSUE_NEARBY_EXPANSION_THRESHOLD;
                if (districtPrefix != null) {
                    List<Post> districtAreaCandidates = postRepository
                            .findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(
                                    BroadcastScope.AREA, PostStatus.ACTIVE, districtPrefix);
                    List<Post> tier2NearbyArea = districtAreaCandidates.stream()
                            .filter(p -> {
                                if (merged.containsKey(p.getId())) return false;
                                String tp = p.getTargetPincodes();
                                if (tp == null) return false;
                                for (String pc : tp.split(",")) {
                                    String pc2 = pc.trim();
                                    if (pc2.length() >= 3 && pc2.substring(0, 3).equals(districtPrefix)
                                            && !pc2.equals(userPincode)) {
                                        return true;
                                    }
                                }
                                return false;
                            })
                            .limit(nearbyNeeded
                                    ? Constant.ISSUE_NEARBY_BLEND_LIMIT
                                    : Constant.ISSUE_NEARBY_BLEND_LIMIT / 2)
                            .collect(Collectors.toList());
                    tier2NearbyArea.forEach(p -> merged.putIfAbsent(p.getId(), p));
                }

                if (districtPrefix != null) {
                    postRepository.findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(
                                    BroadcastScope.DISTRICT, PostStatus.ACTIVE, districtPrefix)
                            .forEach(p -> merged.putIfAbsent(p.getId(), p));
                }

                if (statePrefix != null) {
                    postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(
                                    BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix)
                            .forEach(p -> merged.putIfAbsent(p.getId(), p));
                }
            } else {
                log.debug("[IssueRec] userId={} has no pincode — skipping geo tiers, going straight to national", user.getId());
            }

            int nationalCap = validLimit * Constant.ISSUE_RECOMMENDATION_CANDIDATE_MULTIPLIER;
            postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(
                            BroadcastScope.COUNTRY, PostStatus.ACTIVE,
                            PageRequest.of(0, Math.min(nationalCap, 50)))
                    .forEach(p -> merged.putIfAbsent(p.getId(), p));

            try {
                List<Post> ownPosts = postRepository.findByUserWithUserAndStatusOrderByCreatedAtDesc(user, PostStatus.ACTIVE);
                ownPosts.forEach(p -> merged.putIfAbsent(p.getId(), p));
                if (!ownPosts.isEmpty()) {
                    log.debug("[IssueRec] Injected {} own posts for userId={}", ownPosts.size(), user.getId());
                }
            } catch (Exception e) {
                log.warn("[IssueRec] Own-post injection failed for userId={}: {}", user.getId(), e.getMessage());
            }

            if (merged.size() < validLimit) {
                log.info("[IssueRec] Thin pool ({} posts) for userId={} — loading all active posts (sparse platform)",
                        merged.size(), user.getId());
                try {
                    PaginatedResponse<Post> allActive = getAllActivePosts(null, validLimit * 2);
                    if (allActive != null && allActive.getData() != null) {
                        allActive.getData().forEach(p -> merged.putIfAbsent(p.getId(), p));
                    }
                } catch (Exception e) {
                    log.warn("[IssueRec] Sparse-platform fallback failed for userId={}: {}", user.getId(), e.getMessage());
                }
            }

            final String fp = userPincode, fd = districtPrefix, fs = statePrefix;
            List<Post> ranked = merged.values().stream()
                    .filter(p -> p.getStatus() == PostStatus.ACTIVE)
                    .sorted(Comparator
                            .comparingDouble((Post p) -> computeIssueRecommendationScore(p, fp, fd, fs))
                            .reversed())
                    .limit(validLimit)
                    .collect(Collectors.toList());

            List<PostResponse> responses = convertToPostResponseBatch(ranked, user);

            // ── Batch Auto-Translate ──
            if (user != null && Boolean.TRUE.equals(user.getAutoTranslate()) && user.getPreferredLanguage() != null) {
                try {
                    translationService.translatePosts(responses, user.getPreferredLanguage());
                } catch (Exception e) {
                    log.warn("Failed to batch translate posts: {}", e.getMessage());
                }
            }

            log.info("[IssueRec] user={} pincode={} merged={} returned={}",
                    user.getActualUsername(), userPincode != null ? userPincode : "none",
                    merged.size(), responses.size());

            return PaginatedResponse.of(responses, false, null, validLimit);

        } catch (Exception e) {
            log.error("[IssueRec] Failed for user={}", user != null ? user.getActualUsername() : "null", e);
            try {
                int fallback = (limit == null || limit <= 0)
                        ? Constant.ISSUE_RECOMMENDATION_DEFAULT_LIMIT
                        : Math.min(limit, Constant.ISSUE_RECOMMENDATION_MAX_LIMIT);
                PaginatedResponse<Post> allActive = getAllActivePosts(null, fallback);
                return convertPaginatedPostsToResponses(allActive, user);
            } catch (Exception ex) {
                int fallback = (limit == null || limit <= 0)
                        ? Constant.ISSUE_RECOMMENDATION_DEFAULT_LIMIT
                        : Math.min(limit, Constant.ISSUE_RECOMMENDATION_MAX_LIMIT);
                return PaginationUtils.createEmptyResponse(fallback);
            }
        }
    }

    // =========================================================================
    // SCORING
    // =========================================================================

    private double computeIssueRecommendationScore(
            Post post, String userPincode, String districtPrefix, String statePrefix) {
        // FIX: Post.likeCount / commentCount / viewCount are primitive int — comparing to
        // null is a compile error ("bad operand types for binary operator '!='").
        // Primitives are always initialised (default 0), so the null-safe ternary is wrong.
        int    likes    = post.getLikeCount();
        int    comments = post.getCommentCount();
        int    views    = post.getViewCount();
        double engagement = (likes    * Constant.POST_WEIGHT_LIKE)
                + (comments * Constant.POST_WEIGHT_COMMENT)
                + (views    * Constant.POST_WEIGHT_VIEW);
        long   ageHours  = post.getCreatedAt() != null
                ? TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - post.getCreatedAt().getTime()) : 0L;
        double freshness = 1.0 / (1.0 + ageHours * Constant.POST_DECAY_RATE);
        double geoBoost  = resolveIssueGeoBoost(post, userPincode, districtPrefix, statePrefix);
        return engagement * freshness * geoBoost;
    }

    private double resolveIssueGeoBoost(
            Post post, String userPincode, String districtPrefix, String statePrefix) {
        BroadcastScope scope = post.getBroadcastScope();
        if (scope == null) return Constant.ISSUE_GEO_BOOST_NATIONAL;
        switch (scope) {
            case AREA: {
                String targetPincodes = post.getTargetPincodes();
                if (targetPincodes == null) return Constant.ISSUE_GEO_BOOST_NATIONAL;
                if (targetPincodes.contains(userPincode)) return Constant.ISSUE_GEO_BOOST_SAME_PINCODE;
                if (districtPrefix != null) {
                    for (String pc : targetPincodes.split(",")) {
                        String pc2 = pc.trim();
                        if (pc2.length() >= 3 && pc2.substring(0, 3).equals(districtPrefix)) {
                            return Constant.ISSUE_GEO_BOOST_NEARBY;
                        }
                    }
                }
                return Constant.ISSUE_GEO_BOOST_NATIONAL;
            }
            case DISTRICT: {
                String targetDistricts = post.getTargetDistricts();
                boolean inDistrict = districtPrefix != null && targetDistricts != null && targetDistricts.contains(districtPrefix);
                return inDistrict ? Constant.ISSUE_GEO_BOOST_DISTRICT : Constant.ISSUE_GEO_BOOST_STATE;
            }
            case STATE: {
                String targetStates = post.getTargetStates();
                boolean inState = statePrefix != null && targetStates != null && targetStates.contains(statePrefix);
                return inState ? Constant.ISSUE_GEO_BOOST_STATE : Constant.ISSUE_GEO_BOOST_NATIONAL;
            }
            case COUNTRY: return Constant.ISSUE_GEO_BOOST_NATIONAL;
            default:      return Constant.ISSUE_GEO_BOOST_NATIONAL;
        }
    }

    private double computeIssueScore(Post post, String userPincode, String districtPrefix, String statePrefix) {
        // FIX: same primitive int issue as computeIssueRecommendationScore above.
        int    likes    = post.getLikeCount();
        int    comments = post.getCommentCount();
        int    views    = post.getViewCount();
        double engagement = (likes * Constant.POST_WEIGHT_LIKE) + (comments * Constant.POST_WEIGHT_COMMENT) + (views * Constant.POST_WEIGHT_VIEW);
        long ageHours = post.getCreatedAt() != null
                ? TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - post.getCreatedAt().getTime()) : 0L;
        double freshness = 1.0 / (1.0 + ageHours * Constant.POST_DECAY_RATE);
        return engagement * freshness * resolveGeoBoost(post, userPincode, districtPrefix, statePrefix);
    }

    private double resolveGeoBoost(Post post, String userPincode, String districtPrefix, String statePrefix) {
        BroadcastScope scope = post.getBroadcastScope();
        if (scope == null) return Constant.POST_BOOST_NATIONAL;
        switch (scope) {
            case AREA: {
                String p = post.getTargetPincodes();
                return (p != null && p.contains(userPincode)) ? Constant.POST_BOOST_AREA : Constant.POST_BOOST_DISTRICT;
            }
            case DISTRICT: {
                String d = post.getTargetDistricts();
                return (districtPrefix != null && d != null && d.contains(districtPrefix)) ? Constant.POST_BOOST_DISTRICT : Constant.POST_BOOST_STATE;
            }
            case STATE: {
                String s = post.getTargetStates();
                return (statePrefix != null && s != null && s.contains(statePrefix)) ? Constant.POST_BOOST_STATE : Constant.POST_BOOST_NATIONAL;
            }
            case COUNTRY: return Constant.POST_BOOST_NATIONAL;
            default:      return Constant.POST_BOOST_NATIONAL;
        }
    }

    // =========================================================================
    // PROMOTION / DEMOTION
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public void checkAndPromoteIssuePost(Long postId) {
        try {
            Post post = postRepository.findById(postId).orElse(null);
            if (post == null || !isUserIssuePost(post) || post.getStatus() != PostStatus.ACTIVE) return;

            BroadcastScope current = post.getBroadcastScope();
            if (current == null) return;

            long   ageHours = getAgeInHours(post);
            // FIX: primitive int — null check is illegal, use directly.
            int    likes    = post.getLikeCount();
            int    comments = post.getCommentCount();
            double velocity = ageHours > 0 ? (double)(likes + comments) / ageHours : (likes + comments);

            switch (current) {
                case AREA:
                    if (ageHours <= Constant.ISSUE_DISTRICT_PROMOTE_MAX_AGE_HOURS
                            && (likes >= Constant.ISSUE_DISTRICT_PROMOTE_LIKES
                            || comments >= Constant.ISSUE_DISTRICT_PROMOTE_COMMENTS)) {
                        String pincode = getFirstPincode(post);
                        if (pincode != null && pincode.length() >= 3) {
                            post.setBroadcastScope(BroadcastScope.DISTRICT);
                            post.setTargetDistricts(pincode.substring(0, 3));
                            post.setUpdatedAt(new Date());
                            postRepository.save(post);
                            log.info("[Promotion] Post={} AREA→DISTRICT likes={} comments={} vel={}/hr",
                                    postId, likes, comments, String.format("%.2f", velocity));
                        }
                    }
                    break;
                case DISTRICT:
                    if (ageHours <= Constant.ISSUE_STATE_PROMOTE_MAX_AGE_HOURS
                            && (likes >= Constant.ISSUE_STATE_PROMOTE_LIKES
                            || comments >= Constant.ISSUE_STATE_PROMOTE_COMMENTS)) {
                        String pincode = getFirstPincode(post);
                        if (pincode != null && pincode.length() >= 2) {
                            post.setBroadcastScope(BroadcastScope.STATE);
                            post.setTargetStates(pincode.substring(0, 2));
                            post.setUpdatedAt(new Date());
                            postRepository.save(post);
                            log.info("[Promotion] Post={} DISTRICT→STATE likes={} comments={} vel={}/hr",
                                    postId, likes, comments, String.format("%.2f", velocity));
                        }
                    }
                    break;
                case STATE:
                    if (ageHours <= Constant.ISSUE_NATIONAL_PROMOTE_MAX_AGE_HOURS
                            && (likes >= Constant.ISSUE_NATIONAL_PROMOTE_LIKES
                            || comments >= Constant.ISSUE_NATIONAL_PROMOTE_COMMENTS)) {
                        post.setBroadcastScope(BroadcastScope.COUNTRY);
                        post.setTargetCountry(Constant.DEFAULT_TARGET_COUNTRY);
                        post.setUpdatedAt(new Date());
                        postRepository.save(post);
                        log.info("[Promotion] Post={} STATE→NATIONAL likes={} comments={} vel={}/hr",
                                postId, likes, comments, String.format("%.2f", velocity));
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("[Promotion] Failed for post={}: {}", postId, e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void demoteStaleIssuePosts() {
        log.info("[Demotion] Starting stale issue post demotion...");
        int demoted = 0;
        try {
            for (Post post : postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(BroadcastScope.DISTRICT, PostStatus.ACTIVE, PageRequest.of(0, 500))) {
                if (!isUserIssuePost(post) || !isStale(post)) continue;
                post.setBroadcastScope(BroadcastScope.AREA);
                post.setTargetDistricts(null);
                post.setUpdatedAt(new Date());
                postRepository.save(post);
                log.info("[Demotion] Post={} DISTRICT→AREA", post.getId());
                demoted++;
            }
            for (Post post : postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(BroadcastScope.STATE, PostStatus.ACTIVE, PageRequest.of(0, 300))) {
                if (!isUserIssuePost(post) || !isStale(post)) continue;
                String pincode = getFirstPincode(post);
                if (pincode != null && pincode.length() >= 3) {
                    post.setBroadcastScope(BroadcastScope.DISTRICT);
                    post.setTargetStates(null);
                    post.setTargetDistricts(pincode.substring(0, 3));
                    post.setUpdatedAt(new Date());
                    postRepository.save(post);
                    log.info("[Demotion] Post={} STATE→DISTRICT", post.getId());
                    demoted++;
                }
            }
            for (Post post : postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(BroadcastScope.COUNTRY, PostStatus.ACTIVE, PageRequest.of(0, 100))) {
                if (!isUserIssuePost(post) || !isStale(post)) continue;
                String pincode = getFirstPincode(post);
                if (pincode != null && pincode.length() >= 2) {
                    post.setBroadcastScope(BroadcastScope.STATE);
                    post.setTargetStates(pincode.substring(0, 2));
                    post.setUpdatedAt(new Date());
                    postRepository.save(post);
                    log.info("[Demotion] Post={} NATIONAL→STATE", post.getId());
                    demoted++;
                }
            }
            log.info("[Demotion] Complete — demoted={}", demoted);
        } catch (Exception e) {
            log.error("[Demotion] Job failed", e);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private boolean isUserIssuePost(Post post) {
        String p = post.getTargetPincodes();
        return p != null && !p.isBlank();
    }

    private String getFirstPincode(Post post) {
        String raw = post.getTargetPincodes();
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(",");
        return parts.length > 0 ? parts[0].trim() : null;
    }

    private long getAgeInHours(Post post) {
        return post.getCreatedAt() == null ? 0
                : TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - post.getCreatedAt().getTime());
    }

    private boolean isStale(Post post) {
        Date last = post.getUpdatedAt() != null ? post.getUpdatedAt() : post.getCreatedAt();
        return last != null && TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - last.getTime()) >= Constant.POST_DEMOTION_INACTIVE_DAYS;
    }
}
