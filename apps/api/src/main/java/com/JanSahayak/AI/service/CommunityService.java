package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;

import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.dto.CommunityDto.*;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.payload.CommunityValidationUtil;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PaginationUtils.PaginationSetup;
import com.JanSahayak.AI.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CommunityService {

    private final CommunityRepo                 communityRepo;
    private final CommunityMemberRepo           memberRepo;
    private final CommunityJoinRequestRepo      joinRequestRepo;
    private final UserRepo                      userRepo;
    private final SocialPostRepo                socialPostRepo;
    private final PostLikeRepo                  postLikeRepo;            // Added
    private final SavedPostRepo                 savedPostRepo;
    private final PollRepository                pollRepository;
    private final PollVoteRepository            pollVoteRepository;
    private final CommunityHealthScoreService   healthScoreService;
    private final HyperlocalSeedService         hyperlocalSeedService;   // â† LOCATION PATCH
    private final CloudinaryStorageService      cloudinaryStorageService;


    // â”€â”€ HLIG v2: Interest profile service â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // @Lazy breaks circular dependency:
    //   CommunityService â†’ InterestProfileService â†’ UserInterestProfileRepo
    //   InterestProfileService does NOT inject CommunityService, so this is safe.
    @Lazy
    @Autowired
    private InterestProfileService interestProfileService;
    
    @Lazy
    @Autowired
    private NotificationService notificationService;

    @Lazy
    @Autowired(required = false)
    private com.JanSahayak.AI.security.IdentityBlindService identityBlindService;

    public String resolveActorToken(Long userId, String explicitToken) {
        if (explicitToken != null && !explicitToken.isBlank()) {
            return explicitToken.trim();
        }
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

        if (identityBlindService != null && userId != null) {
            User user = userRepo.findById(userId).orElse(null);
            if (user != null) {
                return identityBlindService.resolveActorTokenForUser(user);
            }
        }
        return null;
    }

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    private void evictCommunityCache(Community community) {
        if (cacheManager != null && community != null) {
            org.springframework.cache.Cache cache = cacheManager.getCache("communities");
            if (cache != null) {
                if (community.getId() != null) cache.evict(community.getId());
                if (community.getSlug() != null) cache.evict(community.getSlug());
            }
        }
    }

    public void evictUserMembershipState(Long communityId, Long userId) {
        if (cacheManager != null && userId != null) {
            org.springframework.cache.Cache membershipCache = cacheManager.getCache("community-membership");
            if (membershipCache != null && communityId != null) {
                membershipCache.evict(communityId + "_" + userId);
                membershipCache.evict("MOD_" + communityId + "_" + userId);
            }
            org.springframework.cache.Cache listCache = cacheManager.getCache(Constant.CACHE_COMMUNITY_LIST);
            if (listCache != null) {
                listCache.evict(userId);
            }
        }
    }

    // â”€â”€ Feed surfacing thresholds (used by feed query callers) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    // =========================================================================
    // 1. CREATE / UPDATE / ARCHIVE
    // =========================================================================

    @CacheEvict(value = Constant.CACHE_COMMUNITY_LIST, key = "#creatorId")
    public CommunityDetailResponse createCommunity(Long creatorId, CreateCommunityRequest req) {
        CommunityValidationUtil.validateUserId(creatorId);
        CommunityValidationUtil.validateCommunityName(req.getName());
        CommunityValidationUtil.validateCommunityDescription(req.getDescription());

        User creator = findUserOrThrow(creatorId);
        CommunityValidationUtil.validateUser(creator);

        if (communityRepo.existsByName(req.getName())) {
            throw new IllegalArgumentException("A community named '" + req.getName() + "' already exists.");
        }

        Community.CommunityPrivacy privacy =
                req.getPrivacy() != null ? req.getPrivacy() : Community.CommunityPrivacy.PUBLIC;

        boolean defaultFeedEligible = Community.CommunityPrivacy.PUBLIC.equals(privacy);
        boolean feedEligible;
        if (req.getFeedEligible() != null) {
            if (Boolean.TRUE.equals(req.getFeedEligible()) && !Community.CommunityPrivacy.PUBLIC.equals(privacy)) {
                throw new ValidationException("Only PUBLIC communities can have feed surfacing enabled.");
            }
            feedEligible = req.getFeedEligible();
        } else {
            feedEligible = defaultFeedEligible;
        }

        Community community = Community.builder()
                .name(org.springframework.web.util.HtmlUtils.htmlEscape(req.getName()))
                .description(req.getDescription() != null ? org.springframework.web.util.HtmlUtils.htmlEscape(req.getDescription()) : null)
                .category(req.getCategory())
                .tags(CommunityValidationUtil.normalizeTags(req.getTags()))
                .privacy(privacy)
                .feedEligible(feedEligible)
                .locationRestricted(Boolean.TRUE.equals(req.getLocationRestricted()))
                .allowMemberPosts(req.getAllowMemberPosts() == null || req.getAllowMemberPosts())
                .requirePostApproval(Boolean.TRUE.equals(req.getRequirePostApproval()))
                .allowAnonymousPosts(true)
                .owner(creator)
                .memberCount(1)
                .ipAddress(com.JanSahayak.AI.util.IpUtils.getClientIpFromContext())
                .build();

        // â”€â”€ LOCATION PATCH â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (Boolean.TRUE.equals(req.getLocationRestricted()) && creator.hasPincode()) {
            community.inheritLocationFromUser(creator);
            HyperlocalSeedService.LocationData loc =
                    hyperlocalSeedService.resolveLocationData(creator.getPincode());
            if (loc != null) {
                community.setLocationName(loc.shortLabel());
                log.info("Community '{}' location enriched from pincode_lookup: {}",
                        community.getName(), loc.displayLocation());
            } else {
                log.warn("Pincode {} not found in pincode_lookup â€” locationName not set for community",
                        creator.getPincode());
            }
        }
        // â”€â”€ END LOCATION PATCH â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        communityRepo.save(community);

        CommunityMember newMember = CommunityMember.builder()
                .community(community).user(creator)
                .memberRole(CommunityMember.MemberRole.ADMIN)
                .build();

        if (community.getMembers() == null) {
            community.setMembers(new ArrayList<>());
        }
        community.getMembers().add(newMember);

        memberRepo.save(newMember);

        evictUserMembershipState(community.getId(), creatorId);

        log.info("Community '{}' (id={}) created by user {} â€” feedEligible={}",
                community.getName(), community.getId(), creatorId, community.isFeedEligible());

        return toDetailResponse(community, creatorId);
    }

    @Caching(evict = {
        @CacheEvict(value = "communities", key = "#communityId"),
        @CacheEvict(value = "communities", key = "#result.slug", condition = "#result != null"),
        @CacheEvict(value = Constant.CACHE_COMMUNITY_LIST, key = "#requesterId")
    })
    public CommunityDetailResponse updateCommunity(Long communityId, Long requesterId,
                                                   UpdateCommunityRequest req) {
        CommunityValidationUtil.validateCommunityId(communityId);
        CommunityValidationUtil.validateUserId(requesterId);

        Community community = findCommunityOrThrow(communityId);
        assertAdminOrOwner(community, requesterId);

        Community.CommunityPrivacy oldPrivacy     = community.getPrivacy();
        boolean                    oldFeedEligible = Boolean.TRUE.equals(community.getFeedEligible());

        // F7: Handle community name update â€” guard against duplicate names
        if (req.getName() != null && !req.getName().equals(community.getName())) {
            if (communityRepo.existsByName(req.getName())) {
                throw new IllegalArgumentException("A community named '" + req.getName() + "' already exists.");
            }
            community.setName(org.springframework.web.util.HtmlUtils.htmlEscape(req.getName()));
        }

        if (req.getDescription()         != null) community.setDescription(org.springframework.web.util.HtmlUtils.htmlEscape(req.getDescription()));
        if (req.getCategory()            != null) community.setCategory(req.getCategory());
        if (req.getTags()                != null) community.setTags(CommunityValidationUtil.normalizeTags(req.getTags()));
        if (req.getCoverImageUrl()       != null) community.setCoverImageUrl(req.getCoverImageUrl());
        if (req.getAvatarUrl()           != null) community.setAvatarUrl(req.getAvatarUrl());
        if (req.getPrivacy()             != null) community.setPrivacy(req.getPrivacy());
        if (req.getRequirePostApproval() != null) community.setRequirePostApproval(req.getRequirePostApproval());

        // â”€â”€ LOCATION PATCH (Step 3) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (req.getLocationRestricted() != null) {
            community.setLocationRestricted(req.getLocationRestricted());
            if (Boolean.TRUE.equals(req.getLocationRestricted()) && community.getLocationName() == null) {
                User owner = community.getOwner();
                if (owner != null && owner.hasPincode()) {
                    HyperlocalSeedService.LocationData loc =
                            hyperlocalSeedService.resolveLocationData(owner.getPincode());
                    if (loc != null) {
                        community.setLocationName(loc.shortLabel());
                        log.info("Community '{}' locationName enriched on update: {}",
                                community.getName(), loc.displayLocation());
                    }
                }
            }
        }
        // â”€â”€ END LOCATION PATCH â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        if (req.getFeedEligible() != null) {
            if (!community.isPublic() && Boolean.TRUE.equals(req.getFeedEligible())) {
                throw new ValidationException("Only PUBLIC communities can have feed surfacing enabled.");
            }
            community.setFeedEligible(req.getFeedEligible());
        }

        community = communityRepo.save(community);

        boolean privacyChanged     = !community.getPrivacy().equals(oldPrivacy);
        boolean eligibilityChanged = Boolean.TRUE.equals(community.getFeedEligible()) != oldFeedEligible;
        if (privacyChanged || eligibilityChanged) {
            syncPostDenormalizedFields(communityId, community);
        }

        return toDetailResponse(community, requesterId);
    }

    @Caching(evict = {
        @CacheEvict(value = "communities", key = "#communityId"),
        @CacheEvict(value = "communities", key = "#result.slug", condition = "#result != null"),
        @CacheEvict(value = Constant.CACHE_COMMUNITY_LIST, key = "#requesterId")
    })
    public CommunityDetailResponse uploadCommunityImage(Long communityId, Long requesterId, MultipartFile file, String imageType) {
        CommunityValidationUtil.validateCommunityId(communityId);
        CommunityValidationUtil.validateUserId(requesterId);

        Community community = findCommunityOrThrow(communityId);
        assertAdminOrOwner(community, requesterId);

        String imageUrl = cloudinaryStorageService.uploadFile(file, requesterId, "communities");

        if ("cover".equalsIgnoreCase(imageType)) {
            community.setCoverImageUrl(imageUrl);
        } else {
            community.setAvatarUrl(imageUrl);
        }

        community = communityRepo.save(community);
        return toDetailResponse(community, requesterId);
    }

    @Caching(evict = {
        @CacheEvict(value = "communities", key = "#communityId"),
        @CacheEvict(value = Constant.CACHE_COMMUNITY_LIST, key = "#requesterId")
    })
    public void archiveCommunity(Long communityId, Long requesterId) {
        Community community = findCommunityOrThrow(communityId);
        if (!community.isOwnedBy(requesterId)) {
            throw new SecurityException("Only the community owner can archive it.");
        }
        community.setStatus(Community.CommunityStatus.ARCHIVED);
        communityRepo.save(community);
        syncPostDenormalizedFields(communityId, community);
        evictCommunityCache(community);
        log.info("Community {} archived â€” posts removed from main feed", communityId);
    }

    @Caching(evict = {
        @CacheEvict(value = "communities", key = "#communityId"),
        @CacheEvict(value = Constant.CACHE_COMMUNITY_LIST, key = "#requesterId")
    })
    public void deleteCommunity(Long communityId, Long requesterId) {
        Community community = findCommunityOrThrow(communityId);
        if (!community.isOwnedBy(requesterId)) {
            throw new SecurityException("Only the community owner can delete it.");
        }
        community.setStatus(Community.CommunityStatus.DELETED);
        // Set scheduled deletion to Now + 1 Day
        community.setScheduledDeletionAt(new java.util.Date(System.currentTimeMillis() + 1L * 24 * 60 * 60 * 1000));
        communityRepo.save(community);
        syncPostDenormalizedFields(communityId, community);
        
        eventPublisher.publishEvent(new com.JanSahayak.AI.event.CommunityDeletedEvent(this, communityId, requesterId));
        evictCommunityCache(community);
        log.info("Community {} scheduled for deletion", communityId);
    }

    @Caching(evict = {
        @CacheEvict(value = "communities", key = "#communityId"),
        @CacheEvict(value = Constant.CACHE_COMMUNITY_LIST, key = "#requesterId")
    })
    public void revokeDeletion(Long communityId, Long requesterId) {
        Community community = findCommunityOrThrow(communityId);
        if (!community.isOwnedBy(requesterId)) {
            throw new SecurityException("Only the community owner can revoke deletion.");
        }
        // Guard: once permanently deleted the grace period is over and the community cannot be restored
        if (community.getStatus() == Community.CommunityStatus.PERMANENTLY_DELETED) {
            throw new ValidationException("This community has already been permanently deleted and cannot be restored.");
        }
        if (community.getStatus() != Community.CommunityStatus.DELETED) {
            throw new ValidationException("Community is not pending deletion.");
        }
        community.setStatus(Community.CommunityStatus.ACTIVE);
        community.setScheduledDeletionAt(null);
        communityRepo.save(community);
        syncPostDenormalizedFields(communityId, community);
        
        eventPublisher.publishEvent(new com.JanSahayak.AI.event.CommunityRevokedEvent(this, communityId, requesterId));
        evictCommunityCache(community);
        log.info("Community {} deletion revoked", communityId);
    }


    // =========================================================================
    // 2. JOIN / LEAVE
    // =========================================================================

    @CacheEvict(value = Constant.CACHE_COMMUNITY_LIST, key = "#userId")
    public Map<String, Object> joinCommunity(Long communityId, Long userId, JoinCommunityRequest req) {
        CommunityValidationUtil.validateCommunityId(communityId);
        CommunityValidationUtil.validateUserId(userId);

        Community community = findCommunityOrThrow(communityId);
        CommunityValidationUtil.assertCommunityActive(community);

        if (community.isSecret()) {
            throw new ValidationException("SECRET communities require an invitation to join.");
        }
        if (memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(communityId, userId)) {
            throw new ValidationException("You are already a member of this community.");
        }
        if (joinRequestRepo.existsByCommunityIdAndUserIdAndStatus(
                communityId, userId, CommunityJoinRequest.RequestStatus.PENDING)) {
            throw new ValidationException("You already have a pending join request.");
        }

        User user = findUserOrThrow(userId);
        CommunityValidationUtil.validateUser(user);

        if (community.isPublic()) {
            memberRepo.findByCommunityIdAndUserId(communityId, userId)
                .ifPresentOrElse(
                    m -> {
                        if (Boolean.TRUE.equals(m.getIsBanned())) {
                            throw new ValidationException("You have been banned from this community.");
                        }
                        // User was once a member, reactivate/reset them
                        m.setIsActive(true);
                        m.setMemberRole(CommunityMember.MemberRole.MEMBER);
                        m.setJoinedAt(new Date());
                        memberRepo.save(m);
                    },
                    () -> {
                        // New member, insert fresh record
                        memberRepo.save(CommunityMember.builder()
                                .community(community).user(user)
                                .memberRole(CommunityMember.MemberRole.MEMBER).build());
                    }
                );

            communityRepo.incrementMemberCount(communityId);
            communityRepo.incrementNewMembersLast7d(communityId);
            
            // â”€â”€ HLIG v2: Seed interest profile from community category â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (community.getCategory() != null) {
                try {
                    interestProfileService.seedFromOnboarding(userId, List.of(community.getCategory()));
                } catch (Exception e) {
                    log.warn("[HLIG] seedFromOnboarding failed: userId={} category={} reason={}",
                            userId, community.getCategory(), e.getMessage());
                }
            }

            evictUserMembershipState(communityId, userId);
            return Map.of("joined", true, "message", "Joined successfully.");
        } else {
            // PRIVATE community â†’ create join request.
            memberRepo.findByCommunityIdAndUserId(communityId, userId).ifPresent(m -> {
                if (Boolean.TRUE.equals(m.getIsBanned())) {
                    throw new ValidationException("You have been banned from this community.");
                }
            });

            // uk_join_request is a unique constraint on (community_id, user_id) with no
            // status column. Delete any stale row (APPROVED/REJECTED/CANCELLED) first so
            // a returning member (leave â†’ rejoin) does not crash on duplicate key insert.
            joinRequestRepo.findByCommunityIdAndUserId(communityId, userId)
                    .ifPresent(joinRequestRepo::delete);
            joinRequestRepo.flush();

            CommunityJoinRequest jr = CommunityJoinRequest.builder()
                    .community(community).user(user)
                    .message(req != null ? req.getMessage() : null).build();
            joinRequestRepo.save(jr);

            // Notify managers (admins & moderators) of the new join request
            try {
                notificationService.notifyCommunityJoinRequest(jr);
            } catch (Exception e) {
                log.error("Failed to trigger notifyCommunityJoinRequest: ", e);
            }

            return Map.of("joined", false,
                    "message", "Join request submitted. Awaiting moderator approval.");
        }
    }

    @CacheEvict(value = Constant.CACHE_COMMUNITY_LIST, key = "#userId")
    public void leaveCommunity(Long communityId, Long userId) {
        Community community = findCommunityOrThrow(communityId);
        if (community.isOwnedBy(userId)) {
            throw new ValidationException("Owner cannot leave. Archive the community first.");
        }
        CommunityMember m = memberRepo.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ValidationException("You are not a member of this community."));
        if (!Boolean.TRUE.equals(m.getIsActive())) {
            throw new ValidationException("You have already left this community.");
        }
        memberRepo.deactivateMember(communityId, userId);
        communityRepo.decrementMemberCount(communityId);
        // Remove the join request so uk_join_request does not block a future rejoin.
        joinRequestRepo.findByCommunityIdAndUserId(communityId, userId)
                .ifPresent(joinRequestRepo::delete);
        evictUserMembershipState(communityId, userId);
    }

    // =========================================================================
    // 3. JOIN REQUEST MANAGEMENT
    // =========================================================================

    public JoinRequestResponse reviewJoinRequest(Long communityId, Long requestId,
                                                 Long reviewerId, ReviewJoinRequest req) {
        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, reviewerId);

        CommunityJoinRequest jr = joinRequestRepo.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Join request not found: " + requestId));
        if (!jr.getCommunity().getId().equals(communityId)) {
            throw new IllegalArgumentException("Request does not belong to this community.");
        }
        if (!jr.isPending()) throw new ValidationException("This request has already been reviewed.");

        if (req.isApprove()) {
            jr.approve(reviewerId);
            joinRequestRepo.save(jr);
            
            // Check if user is already in the community_members table (even if inactive)
            memberRepo.findByCommunityIdAndUserId(communityId, jr.getUser().getId())
                .ifPresentOrElse(
                    m -> {
                        // User was once a member, reactivate/reset them
                        m.setIsActive(true);
                        m.setMemberRole(CommunityMember.MemberRole.MEMBER);
                        m.setJoinedAt(new Date()); // Optional: update join date to now
                        memberRepo.save(m);
                    },
                    () -> {
                        // New member, insert fresh record
                        memberRepo.save(CommunityMember.builder()
                                .community(community).user(jr.getUser())
                                .memberRole(CommunityMember.MemberRole.MEMBER).build());
                    }
                );

            communityRepo.incrementMemberCount(communityId);
            communityRepo.incrementNewMembersLast7d(communityId);

            // Notify the user that their join request was approved (role changed to MEMBER)
            try {
                notificationService.notifyCommunityRoleChanged(
                        jr.getUser(), community, null,
                        CommunityMember.MemberRole.MEMBER, findUserOrThrow(reviewerId));
            } catch (Exception e) {
                log.error("Failed to notify user of join request approval: ", e);
            }

            // HLIG v2: also seed when a join REQUEST is approved (PRIVATE community path)
            if (community.getCategory() != null && jr.getUser() != null) {
                try {
                    interestProfileService.seedFromOnboarding(
                            jr.getUser().getId(), List.of(community.getCategory()));
                } catch (Exception e) {
                    log.warn("[HLIG] seedFromOnboarding (approved) failed: userId={} reason={}",
                            jr.getUser().getId(), e.getMessage());
                }
            }
        } else {
            jr.reject(reviewerId, req.getRejectionReason());
            joinRequestRepo.save(jr);

            // Notify the user that their join request was rejected
            try {
                notificationService.notifyCommunityJoinRejected(jr, findUserOrThrow(reviewerId));
            } catch (Exception e) {
                log.error("Failed to notify user of join request rejection: ", e);
            }
        }
        evictUserMembershipState(communityId, jr.getUser().getId());
        return toJoinRequestResponse(jr);
    }

    public void cancelJoinRequest(Long communityId, Long userId) {
        CommunityJoinRequest jr = joinRequestRepo.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new NoSuchElementException("No join request found for this community."));
        if (!jr.isPending()) throw new ValidationException("Only PENDING requests can be cancelled.");
        jr.cancel();
        joinRequestRepo.save(jr);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<JoinRequestResponse> getPendingJoinRequests(
            Long communityId, Long requesterId, Long cursor, Integer limit) {
        assertModeratorOrAbove(findCommunityOrThrow(communityId), requesterId);

        PaginationSetup setup = PaginationUtils.setupPagination("getPendingJoinRequests", cursor, limit,
                Constant.DEFAULT_JOIN_REQUEST_LIMIT,
                Constant.MAX_JOIN_REQUEST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        List<CommunityJoinRequest> raw = joinRequestRepo.findPendingRequestsCursor(
                communityId, setup.getSanitizedCursor(), pageable);
        List<JoinRequestResponse> mapped = raw.stream()
                .map(this::toJoinRequestResponse).collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                jr -> raw.get(mapped.indexOf(jr)).getId());
    }

    // =========================================================================
    // 4. MEMBER MANAGEMENT
    // =========================================================================

    public CommunityMemberResponse updateMemberRole(Long communityId, Long targetUserId,
                                                    Long requesterId, UpdateMemberRoleRequest req) {
        Community community = findCommunityOrThrow(communityId);
        assertAdminOrOwner(community, requesterId);
        CommunityMember m = findMemberOrThrow(communityId, targetUserId);
        assertHierarchy(community, requesterId, m);

        CommunityMember.MemberRole oldRole = m.getMemberRole(); // capture BEFORE change

        m.setMemberRole(req.getNewRole());
        m.setRoleUpdatedAt(new Date());
        memberRepo.save(m);
        
        org.springframework.cache.Cache listCache = cacheManager.getCache(Constant.CACHE_COMMUNITY_LIST);
        if (listCache != null) {
            listCache.evict(targetUserId);
        }
        
        evictUserMembershipState(communityId, targetUserId);

        // ── Async role-change notification (non-blocking, never breaks the update) ──
        try {
            User requesterUser = findUserOrThrow(requesterId);
            notificationService.notifyCommunityRoleChanged(
                    m.getUser(), community, oldRole, req.getNewRole(), requesterUser);
        } catch (Exception e) {
            log.warn("Failed to dispatch role-change notification: {}", e.getMessage());
        }

        return toMemberResponse(m);
    }

    public void muteMember(Long communityId, Long targetUserId, Long requesterId) {
        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, requesterId);
        CommunityMember m = findMemberOrThrow(communityId, targetUserId);
        assertHierarchy(community, requesterId, m);
        m.setIsMuted(true); memberRepo.save(m);
    }

    public void unmuteMember(Long communityId, Long targetUserId, Long requesterId) {
        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, requesterId);
        CommunityMember m = findMemberOrThrow(communityId, targetUserId);
        assertHierarchy(community, requesterId, m);
        m.setIsMuted(false); memberRepo.save(m);
    }

    public void banMember(Long communityId, Long targetUserId, Long requesterId, BanMemberRequest req) {
        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, requesterId);
        CommunityMember m = findMemberOrThrow(communityId, targetUserId);
        assertHierarchy(community, requesterId, m);
        boolean wasActive = Boolean.TRUE.equals(m.getIsActive());
        m.ban(req != null ? req.getReason() : null);
        memberRepo.save(m);
        if (wasActive) {
            communityRepo.decrementMemberCount(communityId);
        }
        evictUserMembershipState(communityId, targetUserId);
    }

    public void unbanMember(Long communityId, Long targetUserId, Long requesterId) {
        assertAdminOrOwner(findCommunityOrThrow(communityId), requesterId);
        CommunityMember m = findMemberOrThrow(communityId, targetUserId);
        m.unban(); memberRepo.save(m);
        communityRepo.incrementMemberCount(communityId);
        evictUserMembershipState(communityId, targetUserId);
    }

    public void removeMember(Long communityId, Long targetUserId, Long requesterId) {
        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, requesterId);
        CommunityMember m = findMemberOrThrow(communityId, targetUserId);
        assertHierarchy(community, requesterId, m);
        if (Boolean.TRUE.equals(m.getIsActive())) {
            memberRepo.deactivateMember(communityId, targetUserId);
            communityRepo.decrementMemberCount(communityId);
        }
        evictUserMembershipState(communityId, targetUserId);
    }
    
    public void evictMyCommunityListCache(Long userId) {
        org.springframework.cache.Cache listCache = 
            cacheManager.getCache(Constant.CACHE_COMMUNITY_LIST);
        if (listCache != null) {
            listCache.evict(userId);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityMemberResponse> getMembers(
            Long communityId, Long requesterId, Long cursor, Integer limit) {
        Community community = findCommunityOrThrow(communityId);
        if (!community.isPublic() && !isMember(communityId, requesterId)) {
            throw new SecurityException("You must be a member to view the member list.");
        }
        PaginationSetup setup = PaginationUtils.setupPagination("getMembers", cursor, limit,
                Constant.DEFAULT_MEMBER_LIST_LIMIT,
                Constant.MAX_MEMBER_LIST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        List<CommunityMember> raw = memberRepo.findActiveMembersCursor(
                communityId, setup.getSanitizedCursor(), pageable);
        List<CommunityMemberResponse> mapped = raw.stream()
                .map(this::toMemberResponse).collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                mr -> raw.get(mapped.indexOf(mr)).getId());
    }

    // =========================================================================
    // 5. COMMUNITY POST FEEDS
    // =========================================================================

    /**
     * Returns the newest-first post feed for a community (the default "New" tab).
     *
     * <p>Access rules:
     * <ul>
     *   <li>PUBLIC  â†’ open to everyone, including unauthenticated visitors</li>
     *   <li>PRIVATE â†’ members only (non-members get 403)</li>
     *   <li>SECRET  â†’ members only (non-members get 403; community existence is
     *                 already hidden from discovery, so 403 is safe here)</li>
     * </ul>
     * </p>
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityPostsBySlug(
            String slug, String sort, Long requesterId, Long cursor, Double cursorScore, Integer limit) {
        return getCommunityPostsBySlug(slug, sort, requesterId, null, cursor, cursorScore, limit);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityPostsBySlug(
            String slug, String sort, Long requesterId, String actorToken, Long cursor, Double cursorScore, Integer limit) {

        Community community = communityRepo.findBySlug(slug)
                .or(() -> communityRepo.findByName(slug)) // Fallback to name if slug doesn't match (handles human-readable URLs)
                .orElseThrow(() -> new ValidationException("Community not found with slug: " + slug));

        return getCommunityPostsById(community.getId(), sort, requesterId, actorToken, cursor, cursorScore, limit);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityPostsById(
            Long id, String sort, Long requesterId, Long cursor, Double cursorScore, Integer limit) {
        return getCommunityPostsById(id, sort, requesterId, null, cursor, cursorScore, limit);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityPostsById(
            Long id, String sort, Long requesterId, String actorToken, Long cursor, Double cursorScore, Integer limit) {

        if ("TOP".equalsIgnoreCase(sort)) {
            return getCommunityTopPosts(id, requesterId, actorToken, cursor, cursorScore, limit);
        } else {
            return getCommunityPosts(id, requesterId, actorToken, cursor, limit);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityPostsByUser(
            Long communityId, Long targetUserId, Long requesterId, Long cursor, Integer limit) {
        return getCommunityPostsByUser(communityId, targetUserId, requesterId, null, cursor, limit);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityPostsByUser(
            Long communityId, Long targetUserId, Long requesterId, String actorToken, Long cursor, Integer limit) {

        CommunityValidationUtil.validateUserId(targetUserId);
        Community community = findCommunityOrThrow(communityId);
        assertPostReadAccess(community, requesterId);

        PaginationSetup setup = PaginationUtils.setupSocialPostFeedPagination(
                "getCommunityPostsByUser", cursor, limit);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        List<PostStatus> visibleStatuses = new java.util.ArrayList<>();
        visibleStatuses.add(PostStatus.ACTIVE);
        
        if (requesterId != null && (requesterId.equals(targetUserId) || isModeratorOrAbove(communityId, requesterId))) {
            visibleStatuses.add(PostStatus.PENDING_APPROVAL);
            visibleStatuses.add(PostStatus.REJECTED);
            visibleStatuses.add(PostStatus.FLAGGED);
            visibleStatuses.add(PostStatus.HIDDEN);
            visibleStatuses.add(PostStatus.TAKEN_DOWN);
        }

        String effectiveRequesterActorToken = resolveActorToken(requesterId, actorToken);
        String targetActorToken = (requesterId != null && requesterId.equals(targetUserId) && effectiveRequesterActorToken != null && !effectiveRequesterActorToken.isBlank())
                ? effectiveRequesterActorToken
                : resolveActorToken(targetUserId, null);

        List<SocialPost> raw = socialPostRepo.findCommunityPostsByUserOrActorTokenCursorAndStatuses(
                communityId, targetUserId, targetActorToken, visibleStatuses, setup.getSanitizedCursor(), pageable);

        List<Long> postIds = raw.stream().map(SocialPost::getId).collect(Collectors.toList());
        Set<Long> likedPostIds = ((effectiveRequesterActorToken != null && !effectiveRequesterActorToken.isBlank()) || requesterId != null) && !postIds.isEmpty()
                ? new java.util.HashSet<>(postLikeRepo.findLikedSocialPostIdsByActorOrUser(effectiveRequesterActorToken, requesterId, postIds, com.JanSahayak.AI.model.PostLike.ReactionType.LIKE))
                : java.util.Collections.emptySet();

        Set<Long> savedPostIds = ((effectiveRequesterActorToken != null && !effectiveRequesterActorToken.isBlank()) || requesterId != null) && !postIds.isEmpty()
                ? new java.util.HashSet<>(savedPostRepo.findSavedSocialPostIdsByActorOrUser(effectiveRequesterActorToken, requesterId, postIds))
                : java.util.Collections.emptySet();

        // ── Batch-fetch elevated roles for all post authors (1 query per page, zero N+1) ──
        List<Long> authorIds = raw.stream()
                .filter(p -> p.getUser() != null)
                .map(p -> p.getUser().getId())
                .distinct()
                .collect(Collectors.toList());
        final Map<Long, String> authorRoleMap;
        if (!authorIds.isEmpty()) {
            java.util.List<Object[]> roleRows = memberRepo.findElevatedRolesByUserIds(communityId, authorIds);
            authorRoleMap = roleRows.stream().collect(Collectors.toMap(
                    r -> (Long) r[0],
                    r -> r[1].toString()
            ));
        } else {
            authorRoleMap = java.util.Collections.emptyMap();
        }
        List<CommunityPostResponse> mapped = raw.stream()
                .map(p -> toPostResponse(p, requesterId, effectiveRequesterActorToken, likedPostIds.contains(p.getId()), savedPostIds.contains(p.getId()), authorRoleMap))
                .collect(Collectors.toList());

        enrichWithPolls(mapped, postIds, requesterId);

        return PaginationUtils.createIdBasedResponse(
                mapped, setup.getValidatedLimit(),
                dto -> raw.get(mapped.indexOf(dto)).getId());
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityPosts(
            Long communityId, Long requesterId, Long cursor, Integer limit) {
        return getCommunityPosts(communityId, requesterId, null, cursor, limit);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityPosts(
            Long communityId, Long requesterId, String actorToken, Long cursor, Integer limit) {

        Community community = findCommunityOrThrow(communityId);
        assertPostReadAccess(community, requesterId);

        PaginationSetup setup = PaginationUtils.setupSocialPostFeedPagination(
                "getCommunityPosts", cursor, limit);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        List<SocialPost> raw = socialPostRepo.findCommunityPostsCursor(
                communityId, setup.getSanitizedCursor(), pageable);

        List<Long> postIds = raw.stream().map(SocialPost::getId).collect(Collectors.toList());
        String effectiveActorToken = resolveActorToken(requesterId, actorToken);

        Set<Long> likedPostIds = ((effectiveActorToken != null && !effectiveActorToken.isBlank()) || requesterId != null) && !postIds.isEmpty()
                ? new java.util.HashSet<>(postLikeRepo.findLikedSocialPostIdsByActorOrUser(effectiveActorToken, requesterId, postIds, com.JanSahayak.AI.model.PostLike.ReactionType.LIKE))
                : java.util.Collections.emptySet();

        Set<Long> savedPostIds = ((effectiveActorToken != null && !effectiveActorToken.isBlank()) || requesterId != null) && !postIds.isEmpty()
                ? new java.util.HashSet<>(savedPostRepo.findSavedSocialPostIdsByActorOrUser(effectiveActorToken, requesterId, postIds))
                : java.util.Collections.emptySet();

        // ── Batch-fetch elevated roles for all post authors (1 query per page, zero N+1) ──
        List<Long> authorIds = raw.stream()
                .filter(p -> p.getUser() != null)
                .map(p -> p.getUser().getId())
                .distinct()
                .collect(Collectors.toList());
        final Map<Long, String> authorRoleMap;
        if (!authorIds.isEmpty()) {
            java.util.List<Object[]> roleRows = memberRepo.findElevatedRolesByUserIds(communityId, authorIds);
            authorRoleMap = roleRows.stream().collect(Collectors.toMap(
                    r -> (Long) r[0],
                    r -> r[1].toString()
            ));
        } else {
            authorRoleMap = java.util.Collections.emptyMap();
        }
        List<CommunityPostResponse> mapped = raw.stream()
                .map(p -> toPostResponse(p, requesterId, effectiveActorToken, likedPostIds.contains(p.getId()), savedPostIds.contains(p.getId()), authorRoleMap))
                .collect(Collectors.toList());

        enrichWithPolls(mapped, postIds, requesterId);

        return PaginationUtils.createIdBasedResponse(
                mapped, setup.getValidatedLimit(),
                dto -> raw.get(mapped.indexOf(dto)).getId());
    }

    /**
     * Returns community posts sorted by engagement score — the "Top / Hot" sort.
     *
     * <p>Uses a composite cursor of {@code (id, engagementScore)} so that
     * pages are stable even as scores change between requests.</p>
     *
     * <p>Same access rules as {@link #getCommunityPosts}.</p>
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityTopPosts(
            Long communityId, Long requesterId, Long cursor, Double cursorScore, Integer limit) {
        return getCommunityTopPosts(communityId, requesterId, null, cursor, cursorScore, limit);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityTopPosts(
            Long communityId, Long requesterId, String actorToken, Long cursor, Double cursorScore, Integer limit) {

        Community community = findCommunityOrThrow(communityId);
        assertPostReadAccess(community, requesterId);

        PaginationSetup setup = PaginationUtils.setupSocialPostFeedPagination(
                "getCommunityTopPosts", cursor, limit);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        List<SocialPost> raw = socialPostRepo.findCommunityPostsByEngagement(
                communityId,
                setup.getSanitizedCursor(),
                cursorScore != null ? cursorScore : Double.MAX_VALUE,
                pageable);

        List<Long> postIds = raw.stream().map(SocialPost::getId).collect(Collectors.toList());
        String effectiveActorToken = resolveActorToken(requesterId, actorToken);

        Set<Long> likedPostIds = ((effectiveActorToken != null && !effectiveActorToken.isBlank()) || requesterId != null) && !postIds.isEmpty()
                ? new java.util.HashSet<>(postLikeRepo.findLikedSocialPostIdsByActorOrUser(effectiveActorToken, requesterId, postIds, com.JanSahayak.AI.model.PostLike.ReactionType.LIKE))
                : java.util.Collections.emptySet();

        Set<Long> savedPostIds = ((effectiveActorToken != null && !effectiveActorToken.isBlank()) || requesterId != null) && !postIds.isEmpty()
                ? new java.util.HashSet<>(savedPostRepo.findSavedSocialPostIdsByActorOrUser(effectiveActorToken, requesterId, postIds))
                : java.util.Collections.emptySet();

        // ── Batch-fetch elevated roles for all post authors (1 query per page, zero N+1) ──
        List<Long> authorIds = raw.stream()
                .filter(p -> p.getUser() != null)
                .map(p -> p.getUser().getId())
                .distinct()
                .collect(Collectors.toList());
        final Map<Long, String> authorRoleMap;
        if (!authorIds.isEmpty()) {
            java.util.List<Object[]> roleRows = memberRepo.findElevatedRolesByUserIds(communityId, authorIds);
            authorRoleMap = roleRows.stream().collect(Collectors.toMap(
                    r -> (Long) r[0],
                    r -> r[1].toString()
            ));
        } else {
            authorRoleMap = java.util.Collections.emptyMap();
        }
        List<CommunityPostResponse> mapped = raw.stream()
                .map(p -> toPostResponse(p, requesterId, effectiveActorToken, likedPostIds.contains(p.getId()), savedPostIds.contains(p.getId()), authorRoleMap))
                .collect(Collectors.toList());

        enrichWithPolls(mapped, postIds, requesterId);

        return PaginationUtils.createIdBasedResponse(
                mapped, setup.getValidatedLimit(),
                dto -> raw.get(mapped.indexOf(dto)).getId());
    }

    // =========================================================================
    // 5.b PENDING POSTS & APPROVALS
    // =========================================================================

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getPendingCommunityPosts(
            Long communityId, Long requesterId, Long cursor, Integer limit) {

        Community community = findCommunityOrThrow(communityId);
        
        if (requesterId == null || !this.isModeratorOrAbove(communityId, requesterId)) {
            throw new SecurityException("Only community moderators can view pending posts");
        }

        PaginationSetup setup = PaginationUtils.setupSocialPostFeedPagination(
                "getPendingCommunityPosts", cursor, limit);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        List<SocialPost> raw = socialPostRepo.findPendingCommunityPostsCursor(
                communityId, setup.getSanitizedCursor(), pageable);

        List<Long> postIds = raw.stream().map(SocialPost::getId).collect(Collectors.toList());

        List<CommunityPostResponse> mapped = raw.stream()
                .map(p -> toPostResponse(p, requesterId, false, false, java.util.Collections.emptyMap()))
                .collect(Collectors.toList());

        enrichWithPolls(mapped, postIds, requesterId);

        return PaginationUtils.createIdBasedResponse(
                mapped, setup.getValidatedLimit(),
                dto -> raw.get(mapped.indexOf(dto)).getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveCommunityPost(Long communityId, Long postId, User user) {
        CommunityValidationUtil.validateUser(user);
        if (!this.isModeratorOrAbove(communityId, user.getId())) {
            throw new SecurityException("Only community moderators can approve posts");
        }

        SocialPost post = socialPostRepo.findById(postId)
                .orElseThrow(() -> new com.JanSahayak.AI.exception.PostNotFoundException("Post not found"));
        
        if (!communityId.equals(post.getCommunityId())) {
            throw new ValidationException("Post does not belong to this community");
        }
        
        if (post.getStatus() != com.JanSahayak.AI.enums.PostStatus.PENDING_APPROVAL) {
            throw new ValidationException("Post is not pending approval");
        }
        
        post.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);
        socialPostRepo.save(post);
        
        try {
            onPostPublished(post, communityId);
            notificationService.notifyPostApproved(post, post.getCommunity().getName(), user);
        } catch(Exception e) {
            log.warn("Failed to trigger post publish hooks for approved post: {}", e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectCommunityPost(Long communityId, Long postId, User user) {
        CommunityValidationUtil.validateUser(user);
        if (!this.isModeratorOrAbove(communityId, user.getId())) {
            throw new SecurityException("Only community moderators can reject posts");
        }

        SocialPost post = socialPostRepo.findById(postId)
                .orElseThrow(() -> new com.JanSahayak.AI.exception.PostNotFoundException("Post not found"));
                
        if (!communityId.equals(post.getCommunityId())) {
            throw new ValidationException("Post does not belong to this community");
        }
        
        if (post.getStatus() != com.JanSahayak.AI.enums.PostStatus.PENDING_APPROVAL) {
            throw new ValidationException("Post is not pending approval");
        }
        
        post.setStatus(com.JanSahayak.AI.enums.PostStatus.REJECTED);
        socialPostRepo.save(post);
        
        try {
            notificationService.notifyPostRejected(post, post.getCommunity().getName(), user);
        } catch(Exception e) {
            log.warn("Failed to trigger post rejection notification: {}", e.getMessage());
        }
    }

    // =========================================================================
    // 6. DISCOVERY & SEARCH
    // =========================================================================

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunitySummaryResponse> discoverCommunities(
            Long requesterId, Long cursor, Integer limit) {
        PaginationSetup setup = PaginationUtils.setupPagination("discoverCommunities", cursor, limit,
                Constant.DEFAULT_COMMUNITY_LIST_LIMIT,
                Constant.MAX_COMMUNITY_LIST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        User user = (requesterId != null) ? userRepo.findById(requesterId).orElse(null) : null;
        List<Community> raw = (user != null && user.hasPincode())
                ? communityRepo.findDiscoverableForUser(
                user.getPincode(), user.getDistrictPrefix(), user.getStatePrefix(),
                setup.getSanitizedCursor(), pageable)
                : communityRepo.findDiscoverable(setup.getSanitizedCursor(), pageable);

        List<CommunitySummaryResponse> mapped;
        if (requesterId != null && raw != null && !raw.isEmpty()) {
            List<Long> communityIds = raw.stream().map(Community::getId).collect(Collectors.toList());
            Set<Long> memberCommunityIds = memberRepo.findActiveByUserIdAndCommunityIdIn(requesterId, communityIds)
                    .stream().map(cm -> cm.getCommunity().getId()).collect(Collectors.toSet());
            mapped = raw.stream()
                    .map(c -> toSummaryResponse(c, requesterId, memberCommunityIds)).collect(Collectors.toList());
        } else {
            mapped = raw.stream()
                    .map(c -> toSummaryResponse(c, requesterId, Collections.emptySet())).collect(Collectors.toList());
        }
        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                cr -> raw.get(mapped.indexOf(cr)).getId());
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunitySummaryResponse> searchCommunities(
            String query, Long requesterId, Long cursor, Integer limit) {
        if (query == null || query.isBlank()) throw new ValidationException("Search query cannot be empty.");
        PaginationSetup setup = PaginationUtils.setupPagination("searchCommunities", cursor, limit,
                Constant.DEFAULT_COMMUNITY_LIST_LIMIT,
                Constant.MAX_COMMUNITY_LIST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
        List<Community> raw = communityRepo.searchCommunities(query.trim(), pageable);
        List<CommunitySummaryResponse> mapped;
        if (requesterId != null && raw != null && !raw.isEmpty()) {
            List<Long> communityIds = raw.stream().map(Community::getId).collect(Collectors.toList());
            Set<Long> memberCommunityIds = memberRepo.findActiveByUserIdAndCommunityIdIn(requesterId, communityIds)
                    .stream().map(cm -> cm.getCommunity().getId()).collect(Collectors.toSet());
            mapped = raw.stream()
                    .map(c -> toSummaryResponse(c, requesterId, memberCommunityIds)).collect(Collectors.toList());
        } else {
            mapped = raw.stream()
                    .map(c -> toSummaryResponse(c, requesterId, Collections.emptySet())).collect(Collectors.toList());
        }
        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                cr -> raw.get(mapped.indexOf(cr)).getId());
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunitySummaryResponse> getCommunityByCategory(
            String category, Long requesterId, Long cursor, Integer limit) {
        PaginationSetup setup = PaginationUtils.setupPagination("getCommunityByCategory", cursor, limit,
                Constant.DEFAULT_COMMUNITY_LIST_LIMIT,
                Constant.MAX_COMMUNITY_LIST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
        List<Community> raw = communityRepo.findByCategory(category, setup.getSanitizedCursor(), pageable);
        List<CommunitySummaryResponse> mapped;
        if (requesterId != null && raw != null && !raw.isEmpty()) {
            List<Long> communityIds = raw.stream().map(Community::getId).collect(Collectors.toList());
            Set<Long> memberCommunityIds = memberRepo.findActiveByUserIdAndCommunityIdIn(requesterId, communityIds)
                    .stream().map(cm -> cm.getCommunity().getId()).collect(Collectors.toSet());
            mapped = raw.stream()
                    .map(c -> toSummaryResponse(c, requesterId, memberCommunityIds)).collect(Collectors.toList());
        } else {
            mapped = raw.stream()
                    .map(c -> toSummaryResponse(c, requesterId, Collections.emptySet())).collect(Collectors.toList());
        }
        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                cr -> raw.get(mapped.indexOf(cr)).getId());
    }

    @Transactional(readOnly = true)
    public CommunityDetailResponse getCommunityDetail(String idOrSlug, Long requesterId) {
        Community community = null;
        if (idOrSlug != null && idOrSlug.matches("\\d+")) {
            long numericId = Long.parseLong(idOrSlug);
            community = communityRepo.findById(numericId).orElse(null);
        } else {
            community = communityRepo.findBySlug(idOrSlug).orElse(null);
        }

        if (community == null) {
            throw new NoSuchElementException("Community not found: " + idOrSlug);
        }
        // PERMANENTLY_DELETED communities are gone for good - return 404 to everyone
        if (community.getStatus() == Community.CommunityStatus.PERMANENTLY_DELETED) {
            throw new NoSuchElementException("Community not found: " + idOrSlug);
        }
        if (community.isSecret() && !isMember(community.getId(), requesterId)) {
            throw new SecurityException("This is a secret community. You must be invited to view it.");
        }
        return toDetailResponse(community, requesterId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = Constant.CACHE_COMMUNITY_LIST, key = "#userId", condition = "#cursor == null", unless = "#result == null")
    public PaginatedResponse<CommunitySummaryResponse> getMyCommunities(
            Long userId, Long cursor, Integer limit) {
        CommunityValidationUtil.validateUserId(userId);
        PaginationSetup setup = PaginationUtils.setupPagination("getMyCommunities", cursor, limit,
                Constant.DEFAULT_COMMUNITY_LIST_LIMIT,
                Constant.MAX_COMMUNITY_LIST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
        List<CommunityMember> raw = memberRepo.findUserCommunitiesCursor(
                userId, setup.getSanitizedCursor(), pageable);
        List<CommunitySummaryResponse> mapped = raw.stream()
                .map(cm -> {
                    Community c = cm.getCommunity();
                    return CommunitySummaryResponse.builder()
                            .id(c.getId()).name(c.getName()).slug(c.getSlug())
                            .description(CommunityValidationUtil.truncate(c.getDescription(), 200))
                            .category(c.getCategory()).avatarUrl(c.getAvatarUrl()).coverImageUrl(c.getCoverImageUrl())
                            .privacy(c.getPrivacy() != null ? c.getPrivacy().name() : null)
                            .locationName(c.getLocationName())
                            .memberCount(c.getMemberCount()).postCount(c.getPostCount())
                            .isMember(true) // Always true since this is the user's community list
                            .isOwner(c.isOwnedBy(userId))
                            .isModerator(cm != null && cm.isModerator()) // Populate moderator status
                            .createdAt(c.getCreatedAt())
                            .feedEligible(c.isFeedEligible())
.feedSurfaceCount(c.getFeedSurfaceCount())
                            .healthScore(c.getHealthScore()).healthTier(c.getHealthTier()).healthTierEmoji(c.getHealthTierEmoji())
                            .isSystemSeeded(Boolean.TRUE.equals(c.getIsSystemSeeded())).wardName(c.getWardName())
                            .status(c.getStatus() != null ? c.getStatus().name() : null)
                            .isDeleted(c.getStatus() == Community.CommunityStatus.DELETED || c.getStatus() == Community.CommunityStatus.PERMANENTLY_DELETED)
                            .scheduledDeletionDate(c.getScheduledDeletionAt())
                            .build();
                }).collect(Collectors.toList());
        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                cr -> raw.get(mapped.indexOf(cr)).getId());
    }

    @Transactional(readOnly = true)
    public long getMyCommunitiesCount(Long userId) {
        CommunityValidationUtil.validateUserId(userId);
        return communityRepo.countMyCommunities(userId);
    }

    @Transactional(readOnly = true)
    public List<CommunitySummaryResponse> getOwnedCommunities(Long ownerId) {
        CommunityValidationUtil.validateUserId(ownerId);
        // Include DELETED (pending-deletion) communities so the owner can restore them;
        // only exclude PERMANENTLY_DELETED ones which are gone for good.
        List<Community> raw = communityRepo.findByOwnerIdAndStatusNot(ownerId, Community.CommunityStatus.PERMANENTLY_DELETED);
        List<CommunitySummaryResponse> mapped;
        if (ownerId != null && !raw.isEmpty()) {
            List<Long> communityIds = raw.stream().map(Community::getId).collect(Collectors.toList());
            Set<Long> memberCommunityIds = memberRepo.findActiveByUserIdAndCommunityIdIn(ownerId, communityIds)
                    .stream().map(cm -> cm.getCommunity().getId()).collect(Collectors.toSet());
            mapped = raw.stream()
                    .map(c -> toSummaryResponse(c, ownerId, memberCommunityIds)).collect(Collectors.toList());
        } else {
            mapped = raw.stream()
                    .map(c -> toSummaryResponse(c, ownerId, Collections.emptySet())).collect(Collectors.toList());
        }
        return mapped;
    }

    // =========================================================================
    // 7. EVENT HOOKS (called by other services)
    // =========================================================================

    /**
     * Looks up a Community for use during post creation and validates that the
     * requesting user is allowed to post in it.
     *
     * Rules enforced:
     *  - Community must exist and be ACTIVE.
     *  - Community must allow member posts (allowMemberPosts = true).
     *  - User must be an active member of the community.
     *
     * Returns an Optional so the caller can use ifPresent() without a try/catch.
     * Throws ValidationException / SecurityException directly so the error message
     * reaches the client instead of being swallowed.
     */
    public Optional<Community> findCommunityForPost(Long communityId, User user) {
        CommunityValidationUtil.validateCommunityId(communityId);

        Community community = communityRepo.findById(communityId)
                .orElseThrow(() -> new ValidationException(
                        "Community not found with id: " + communityId));

        CommunityValidationUtil.assertCommunityActive(community);

        if (!Boolean.TRUE.equals(community.getAllowMemberPosts())) {
            throw new ValidationException(
                    "This community does not allow member posts.");
        }

        if (!isMember(communityId, user.getId())) {
            throw new SecurityException(
                    "You must be a member of this community to post in it.");
        }

        return Optional.of(community);
    }

    /**
     * Called by SocialPostService.createPost() after saving a community post.
     *
     * What this does:
     *  1. Copies community.privacy + community.feedEligible into the post's
     *     denormalized columns (so feed query needs no JOIN)
     *  2. Increments community post counter + weekly stats
     */
    public void onPostPublished(SocialPost post, Long communityId) {
        // NOTE: syncCommunityDenormalizedFields() is now called inside
        // SocialPostService.buildSocialPost() BEFORE the initial save, so there is
        // no need to sync + re-save here. This method only updates community counters.
        if (communityRepo.existsById(communityId)) {
            communityRepo.incrementPostCount(communityId);
            communityRepo.incrementPostsLast7d(communityId);
            communityRepo.incrementActivePostersLast7d(communityId);
        } else {
            log.warn("[Community] onPostPublished: communityId={} not found â€” counters not updated", communityId);
        }
    }

    /** Called by SocialPostService.deletePost() for community posts. */
    public void onPostDeleted(Long communityId) {
        communityRepo.decrementPostCount(communityId);
    }

    /** Called by CommentService.addComment() when comment is on a community post. */
    public void onCommentAdded(Long communityId) {
        communityRepo.incrementTotalCommentCount(communityId);
    }

    /** Called by PostInteractionService.likePost() when like is on a community post. */
    public void onLikeAdded(Long communityId) {
        communityRepo.incrementTotalLikeCount(communityId);
    }

    // =========================================================================
    // 8. HEALTH SCORE
    // =========================================================================

    public void triggerHealthRecalculation(Long communityId) {
        healthScoreService.recalculateNow(communityId);
    }

    @Transactional(readOnly = true)
    public HealthInsightResponse getHealthInsights(Long communityId, Long requesterId) {
        assertModeratorOrAbove(findCommunityOrThrow(communityId), requesterId);
        return healthScoreService.getInsightsForOwner(communityId);
    }

    // =========================================================================
    // AUTHORIZATION
    // =========================================================================

    @Cacheable(value = "community-membership", key = "#communityId + '_' + #userId")
    public boolean isMember(Long communityId, Long userId) {
        return userId != null && memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(communityId, userId);
    }

    @Cacheable(value = "community-membership", key = "'MOD_' + #communityId + '_' + #userId")
    public boolean isModeratorOrAbove(Long communityId, Long userId) {
        return userId != null && memberRepo.isModeratorOrAbove(communityId, userId);
    }


    private void assertHierarchy(Community community, Long requesterId, CommunityMember targetMember) {
        if (community.isOwnedBy(requesterId)) return;
        if (community.isOwnedBy(targetMember.getUser().getId())) {
            throw new SecurityException("Cannot perform this action on the community owner.");
        }
        CommunityMember requester = memberRepo.findByCommunityIdAndUserId(community.getId(), requesterId)
                .orElseThrow(() -> new SecurityException("Access denied: you are not a member of this community."));
        
        int reqRank = requester.getMemberRole() != null ? requester.getMemberRole().getRank() : 0;
        int targetRank = targetMember.getMemberRole() != null ? targetMember.getMemberRole().getRank() : 0;
        
        if (reqRank <= targetRank) {
            throw new SecurityException("You do not have permission to modify a member with an equal or higher role.");
        }
    }

    private void assertAdminOrOwner(Community community, Long userId) {
        if (community.isOwnedBy(userId)) return;
        CommunityMember m = memberRepo.findByCommunityIdAndUserId(community.getId(), userId)
                .orElseThrow(() -> new SecurityException("Access denied: you are not a member of this community."));
        if (!m.isAdmin())
            throw new SecurityException("Only community admins can perform this action.");
    }

    private void assertModeratorOrAbove(Community community, Long userId) {
        if (community.isOwnedBy(userId)) return;
        CommunityMember m = memberRepo.findByCommunityIdAndUserId(community.getId(), userId)
                .orElseThrow(() -> new SecurityException("Access denied: you are not a member of this community."));
        if (!m.canModerate())
            throw new SecurityException("Only moderators or admins can perform this action.");
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    /**
     * Enforces read-access rules for community post feeds.
     *
     * <ul>
     *   <li>PUBLIC  â€” anyone (requesterId may be null for anonymous visitors)</li>
     *   <li>PRIVATE â€” authenticated members only</li>
     *   <li>SECRET  â€” authenticated members only</li>
     * </ul>
     */
    private void assertPostReadAccess(Community community, Long requesterId) {
        if (community.isPublic()) return;
        // PRIVATE and SECRET both require membership
        if (requesterId == null || !isMember(community.getId(), requesterId)) {
            throw new SecurityException("You must be a member to view this community's posts.");
        }
    }

    /**
     * Maps a {@link SocialPost} entity to the API-safe {@link CommunityPostResponse} DTO.
     *
     * <p>Anonymity is enforced here: when {@code post.isAnonymous()} is true, author
     * identity fields are intentionally left null so they are never serialised.</p>
     *
     * @param post        the entity fetched from the database
     * @param requesterId the authenticated caller's user-id (null for anonymous visitors)
     */
    private CommunityPostResponse toPostResponse(SocialPost post, Long requesterId,
            boolean isLiked, boolean isSaved, Map<Long, String> authorRoleMap) {
        return toPostResponse(post, requesterId, null, isLiked, isSaved, authorRoleMap);
    }

    private CommunityPostResponse toPostResponse(SocialPost post, Long requesterId, String requesterActorToken,
            boolean isLiked, boolean isSaved, Map<Long, String> authorRoleMap) {
        User author = post.getUser();

        // Build the lightweight community attribution badge
        CommunityPostAttributionInfo attribution = null;
        if (post.getCommunity() != null) {
            Community c = post.getCommunity();
            attribution = CommunityPostAttributionInfo.builder()
                    .communityId(c.getId())
                    .communityName(c.getName())
                    .communitySlug(c.getSlug())
                    .communityAvatarUrl(c.getAvatarUrl())
                    .healthTier(c.getHealthTier())
                    .healthTierEmoji(c.getHealthTierEmoji())
                    .isSystemSeeded(Boolean.TRUE.equals(c.getIsSystemSeeded()))
                    .wardName(c.getWardName())
                    .build();
        }

        String authorUsername = post.getAuthorUsername();
        String authorProfileImage = post.getAuthorProfileImage();
        if (authorUsername == null || authorUsername.isBlank() || authorUsername.startsWith("acc_") || authorUsername.startsWith("act_")) {
            if (author != null && !com.JanSahayak.AI.payload.PostUtility.isCitizen(author)) {
                authorUsername = author.getActualUsername();
            } else if (author != null && author.getUsername() != null && !author.getUsername().startsWith("acc_")) {
                authorUsername = author.getUsername();
            } else {
                authorUsername = "Citizen";
            }
        }
        if ((authorProfileImage == null || authorProfileImage.isBlank()) && author != null) {
            authorProfileImage = author.getProfileImage();
        }
        Long authorId = (author != null && !com.JanSahayak.AI.payload.PostUtility.isCitizen(author)) ? author.getId() : null;

        boolean isMyPost = (requesterId != null && author != null && requesterId.equals(author.getId()))
                || (requesterActorToken != null && !requesterActorToken.isBlank() && requesterActorToken.equals(post.getActorToken()));

        return CommunityPostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .imageUrl(post.getMediaUrls() != null && !post.getMediaUrls().isBlank()
                        ? post.getMediaUrls().split(",")[0].trim() : null)
                .mediaUrls(post.getMediaUrlsList())
                .postType(post.hasMedia() ? "IMAGE" : "TEXT")
                .isAnonymous(false)
                // Author — always available
                .authorId(authorId)
                .authorUsername(authorUsername)
                .authorProfileImage(authorProfileImage)
                // Engagement
                .likeCount(post.getLikeCount() != null ? post.getLikeCount() : 0)
                .commentCount(post.getCommentCount() != null ? post.getCommentCount() : 0)
                .shareCount(post.getShareCount() != null ? post.getShareCount() : 0)
                // Viewer-context
                .isLikedByMe(isLiked)
                .isSavedByMe(isSaved)
                .isPendingApproval(post.getStatus() == com.JanSahayak.AI.enums.PostStatus.PENDING_APPROVAL)
                .isMyPost(isMyPost)
                // Feed reach — map viralTier to feed reach label
                .feedReach(post.getViralTier() != null ? post.getViralTier() : "COMMUNITY_ONLY")
                // Attribution badge
                .community(attribution)
                // Timestamps
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private void enrichWithPolls(List<CommunityPostResponse> dtos, List<Long> postIds, Long requesterId) {
        if (postIds == null || postIds.isEmpty()) return;

        List<Poll> polls = pollRepository.findBySocialPostIdIn(postIds);
        if (polls.isEmpty()) return;
        Map<Long, Poll> pollMap = polls.stream().collect(Collectors.toMap(p -> p.getSocialPost().getId(), p -> p));

        Set<Long> votedPollIds = new java.util.HashSet<>();
        Map<Long, List<Long>> votedOptionsMap = new java.util.HashMap<>();

        if (requesterId != null) {
            List<Long> pIds = polls.stream().map(Poll::getId).collect(Collectors.toList());
            votedPollIds.addAll(pollVoteRepository.findVotedPollIdsByUserAndPollIds(requesterId, pIds));

            List<Object[]> votedOptions = pollVoteRepository.findVotedOptionsByUserAndPollIds(requesterId, pIds);
            for (Object[] row : votedOptions) {
                Long pId = (Long) row[0];
                Long optId = (Long) row[1];
                votedOptionsMap.computeIfAbsent(pId, k -> new java.util.ArrayList<>()).add(optId);
            }
        }

        for (CommunityPostResponse dto : dtos) {
            Poll poll = pollMap.get(dto.getId());
            if (poll != null) {
                boolean hasVoted = votedPollIds.contains(poll.getId());
                List<Long> userVotes = votedOptionsMap.getOrDefault(poll.getId(), java.util.Collections.emptyList());
                dto.setPoll(com.JanSahayak.AI.dto.SocialPostDto.buildPollSummary(poll, hasVoted, userVotes));
                dto.setPostType("POLL");
            }
        }
    }

    private void syncPostDenormalizedFields(Long communityId, Community community) {
        String  newPrivacy      = community.getPrivacy() != null ? community.getPrivacy().name() : null;
        boolean newFeedEligible = community.isFeedEligible();
        String  newStatus       = community.getStatus() != null ? community.getStatus().name() : null;
        socialPostRepo.syncCommunityDenormalizedFields(communityId, newPrivacy, newFeedEligible, newStatus);
        log.info("Synced feed denormalized fields on posts â€” community={} privacy={} feedEligible={} status={}",
                communityId, newPrivacy, newFeedEligible, newStatus);
    }

    // =========================================================================
    // MAPPERS
    // =========================================================================

    private CommunitySummaryResponse toSummaryResponse(Community c, Long requesterId) {
        return toSummaryResponse(c, requesterId, null);
    }

    private CommunitySummaryResponse toSummaryResponse(Community c, Long requesterId, Set<Long> memberCommunityIds) {
        boolean isMember = false;
        if (requesterId != null) {
            if (memberCommunityIds != null) {
                isMember = memberCommunityIds.contains(c.getId());
            } else {
                isMember = isMember(c.getId(), requesterId);
            }
        }

        return CommunitySummaryResponse.builder()
                .id(c.getId()).name(c.getName()).slug(c.getSlug())
                .description(CommunityValidationUtil.truncate(c.getDescription(), 200))
                .category(c.getCategory()).avatarUrl(c.getAvatarUrl()).coverImageUrl(c.getCoverImageUrl())
                .privacy(c.getPrivacy() != null ? c.getPrivacy().name() : null)
                .locationName(c.getLocationName())
                .memberCount(c.getMemberCount()).postCount(c.getPostCount())
                .isMember(isMember)
                .isOwner(requesterId != null && c.isOwnedBy(requesterId))
                .isModerator(requesterId != null && isModeratorOrAbove(c.getId(), requesterId))
                .createdAt(c.getCreatedAt())
                .feedEligible(c.isFeedEligible())
                .feedSurfaceCount(c.getFeedSurfaceCount())
                .healthScore(c.getHealthScore()).healthTier(c.getHealthTier()).healthTierEmoji(c.getHealthTierEmoji())
                .isSystemSeeded(Boolean.TRUE.equals(c.getIsSystemSeeded())).wardName(c.getWardName())
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .isDeleted(c.getStatus() == Community.CommunityStatus.DELETED || c.getStatus() == Community.CommunityStatus.PERMANENTLY_DELETED)
                .scheduledDeletionDate(c.getScheduledDeletionAt())
                .build();
    }

    private CommunityDetailResponse toDetailResponse(Community c, Long requesterId) {
        CommunityMember cm = (requesterId != null)
                ? memberRepo.findByCommunityIdAndUserId(c.getId(), requesterId).orElse(null) : null;
        boolean hasPending = (requesterId != null)
                && joinRequestRepo.existsByCommunityIdAndUserIdAndStatus(
                c.getId(), requesterId, CommunityJoinRequest.RequestStatus.PENDING);
        UserBriefResponse ownerResp = null;
        if (c.getOwner() != null) {
            User o = c.getOwner();
            ownerResp = UserBriefResponse.builder()
                    .id(o.getId())
                    .username(o.getActualUsername())
                    .profileImage(o.getProfileImage())
                    .build();
        }
        return CommunityDetailResponse.builder()
                .id(c.getId()).name(c.getName()).slug(c.getSlug())
                .description(c.getDescription()).category(c.getCategory()).tags(c.getTags())
                .avatarUrl(c.getAvatarUrl()).coverImageUrl(c.getCoverImageUrl())
                .privacy(c.getPrivacy() != null ? c.getPrivacy().name() : null)
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .locationName(c.getLocationName())
                .locationRestricted(Boolean.TRUE.equals(c.getLocationRestricted()))
                .allowMemberPosts(Boolean.TRUE.equals(c.getAllowMemberPosts()))
                .requirePostApproval(Boolean.TRUE.equals(c.getRequirePostApproval()))
                .allowAnonymousPosts(true)
                .memberCount(c.getMemberCount()).postCount(c.getPostCount())
                .feedEligible(c.isFeedEligible())
                .feedSurfaceCount(c.getFeedSurfaceCount())
                .createdAt(c.getCreatedAt()).lastActiveAt(c.getLastActiveAt())
                .isMember(cm != null && Boolean.TRUE.equals(cm.getIsActive()))
                .isOwner(requesterId != null && c.isOwnedBy(requesterId))
                .isModerator(cm != null && cm.isModerator())
                .currentUserRole(cm != null && cm.getMemberRole() != null ? cm.getMemberRole().name() : null)
                .hasPendingRequest(hasPending).owner(ownerResp)
                .healthScore(c.getHealthScore()).healthTier(c.getHealthTier()).healthTierEmoji(c.getHealthTierEmoji())
                .isSystemSeeded(Boolean.TRUE.equals(c.getIsSystemSeeded())).wardName(c.getWardName())
                .isDeleted(c.getStatus() == Community.CommunityStatus.DELETED || c.getStatus() == Community.CommunityStatus.PERMANENTLY_DELETED)
                .scheduledDeletionDate(c.getScheduledDeletionAt())
                .build();
    }

    private CommunityMemberResponse toMemberResponse(CommunityMember cm) {
        User u = cm.getUser();
        return CommunityMemberResponse.builder()
                .id(cm.getId()).userId(u != null ? u.getId() : null)
                .username(u != null ? u.getActualUsername() : null)
                .profileImage(u != null ? u.getProfileImage() : null)
                .memberRole(cm.getMemberRole() != null ? cm.getMemberRole().name() : null)
                .isMuted(Boolean.TRUE.equals(cm.getIsMuted()))
                .isBanned(Boolean.TRUE.equals(cm.getIsBanned()))
                .joinedAt(cm.getJoinedAt())
                .build();
    }

    private JoinRequestResponse toJoinRequestResponse(CommunityJoinRequest jr) {
        User u = jr.getUser();
        return JoinRequestResponse.builder()
                .id(jr.getId()).userId(u != null ? u.getId() : null)
                .username(u != null ? u.getActualUsername() : null)
                .profileImage(u != null ? u.getProfileImage() : null)
                .message(jr.getMessage())
                .status(jr.getStatus() != null ? jr.getStatus().name() : null)
                .requestedAt(jr.getRequestedAt()).reviewedAt(jr.getReviewedAt())
                .build();
    }

    // â”€â”€ Entity finders â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private Community findCommunityOrThrow(Long id) {
        return communityRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Community not found: " + id));
    }

    private User findUserOrThrow(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
    }

    private CommunityMember findMemberOrThrow(Long communityId, Long userId) {
        return memberRepo.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new NoSuchElementException("User is not a member of this community."));
    }
}
