package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.payload.request.*;
import com.JanSahayak.AI.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.JanSahayak.AI.service.SocialPostMediaService;
import com.JanSahayak.AI.payload.SocialPostUtility;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.exception.ServiceException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollService {

    private final PollRepository        pollRepository;
    private final PollOptionRepository  pollOptionRepository;
    private final PollVoteRepository    pollVoteRepository;
    private final SocialPostRepo        socialPostRepository;
    private final UserRepo              userRepository;
    private final SocialPostMediaService mediaService;
    private final CommunityRepo         communityRepository;
    private final CommunityMemberRepo   communityMemberRepository;

    // @Lazy breaks the circular dependency: CommunityService → SocialPostRepo,
    // PollService → CommunityService.
    @Lazy
    @Autowired
    private CommunityService communityService;

    @Lazy
    @Autowired
    private SocialPostService socialPostService;

    @Lazy
    @Autowired
    private com.JanSahayak.AI.security.IdentityBlindService identityBlindService;

    @Autowired(required = false)
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

    @Transactional(rollbackFor = Exception.class)
    public PollResponse createPollPost(CreatePollRequest req, User creator) {

        validatePollRequest(req);

        String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();
        if (idempotencyKey != null) {
            java.util.Optional<Poll> existingPoll = pollRepository.findByIdempotencyKey(idempotencyKey);
            if (existingPoll.isPresent()) {
                log.info("Idempotency hit: Returning existing Poll for key {}", idempotencyKey);
                return PollResponse.from(existingPoll.get(), false, true, List.of());
            }
        }

        Community community = null;
        if (req.getCommunityId() != null) {
            community = communityRepository.findById(req.getCommunityId())
                    .orElseThrow(() -> new RuntimeException("Community not found"));
            
            if (!communityMemberRepository.existsByCommunityIdAndUserIdAndIsActiveTrue(community.getId(), creator.getId())) {
                throw new SecurityException("User must be an active member to create a poll in this community");
            }
        }

        PostStatus status = PostStatus.ACTIVE;
        if (community != null && Boolean.TRUE.equals(community.getRequirePostApproval())) {
            boolean isModeratorOrAbove = communityMemberRepository.isModeratorOrAbove(community.getId(), creator.getId());
            if (!com.JanSahayak.AI.payload.PostUtility.isAdmin(creator) && !isModeratorOrAbove) {
                status = PostStatus.PENDING_APPROVAL;
            }
        }

        SocialPost socialPost = SocialPost.builder()
                .content(req.getQuestion())
                .user(creator)
                .status(status)
                .community(community)
                .allowComments(true)
                .category(req.getCategory() != null ? com.JanSahayak.AI.enums.SocialPostCategory.valueOf(req.getCategory()) : com.JanSahayak.AI.enums.SocialPostCategory.GENERAL)
                .ipAddress(com.JanSahayak.AI.util.IpUtils.getClientIpFromContext())
                .build();

        if (creator != null) {
            String authorUsername = creator.getActualUsername();
            String authorProfileImage = creator.getProfileImage();
            String actorToken = resolveActorToken(creator);
            if (actorToken != null && !actorToken.isBlank() && PostUtility.isCitizen(creator)) {
                socialPost.setActorToken(actorToken);
                socialPost.setUser(null);
                if (actorProfileRepo != null) {
                    ActorProfile ap = actorProfileRepo.findByActorToken(actorToken).orElse(null);
                    if (ap != null) {
                        authorUsername = ap.getUsername();
                        if (ap.getProfileImage() != null) authorProfileImage = ap.getProfileImage();
                    }
                }
            } else {
                socialPost.setUser(creator);
                socialPost.setActorToken(null);
            }
            socialPost.setAuthorUsername(authorUsername);
            socialPost.setAuthorProfileImage(authorProfileImage);
        }

        if (community != null) {
            socialPost.syncCommunityDenormalizedFields(community);
        }

        socialPost.inheritLocationFromUser(creator);

        SocialPost savedSocialPost = socialPostRepository.save(socialPost);
        log.info("Auto-created SocialPost {} for poll by user {}", savedSocialPost.getId(), creator.getId());

        if (savedSocialPost.getCommunityId() != null && savedSocialPost.getStatus() == PostStatus.ACTIVE) {
            try {
                communityService.onPostPublished(savedSocialPost, savedSocialPost.getCommunityId());
            } catch (Exception e) {
                log.warn("[Community] onPostPublished failed for poll post={} community={}: {}",
                        savedSocialPost.getId(), savedSocialPost.getCommunityId(), e.getMessage());
            }
        }

        Poll poll = buildPoll(req, creator, savedSocialPost);
        Poll savedPoll = pollRepository.save(poll);

        attachOptions(savedPoll, req.getOptions());

        log.info("Created Poll {} with {} options for SocialPost {}",
                savedPoll.getId(), req.getOptions().size(), savedSocialPost.getId());

        return PollResponse.from(savedPoll, false, true, List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public PollResponse createPollPostWithMedia(CreatePollRequest req, List<MultipartFile> mediaFiles, User creator) {
        validatePollRequest(req);
        
        String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();
        if (idempotencyKey != null) {
            java.util.Optional<Poll> existingPoll = pollRepository.findByIdempotencyKey(idempotencyKey);
            if (existingPoll.isPresent()) {
                log.info("Idempotency hit: Returning existing Poll with media for key {}", idempotencyKey);
                return PollResponse.from(existingPoll.get(), false, true, List.of());
            }
        }

        // 1. Upload media files with validation
        List<String> uploadedMediaUrls = new ArrayList<>();
        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            uploadedMediaUrls = uploadMediaFilesWithValidation(mediaFiles, creator.getId());
        }
        // 2. Build the parent SocialPost
        Community community = null;
        if (req.getCommunityId() != null) {
            community = communityRepository.findById(req.getCommunityId())
                    .orElseThrow(() -> new RuntimeException("Community not found"));
            
            if (!communityMemberRepository.existsByCommunityIdAndUserIdAndIsActiveTrue(community.getId(), creator.getId())) {
                throw new SecurityException("User must be an active member to create a poll in this community");
            }
        }

        SocialPost socialPost = SocialPost.builder()
                .content(req.getQuestion())
                .user(creator)
                .status(PostStatus.ACTIVE)
                .community(community)
                .allowComments(true)
                .category(req.getCategory() != null ? com.JanSahayak.AI.enums.SocialPostCategory.valueOf(req.getCategory()) : com.JanSahayak.AI.enums.SocialPostCategory.GENERAL)
                .ipAddress(com.JanSahayak.AI.util.IpUtils.getClientIpFromContext())
                .build();

        if (creator != null) {
            String authorUsername = creator.getActualUsername();
            String authorProfileImage = creator.getProfileImage();
            String actorToken = resolveActorToken(creator);
            if (actorToken != null && !actorToken.isBlank() && PostUtility.isCitizen(creator)) {
                socialPost.setActorToken(actorToken);
                socialPost.setUser(null);
                if (actorProfileRepo != null) {
                    ActorProfile ap = actorProfileRepo.findByActorToken(actorToken).orElse(null);
                    if (ap != null) {
                        authorUsername = ap.getUsername();
                        if (ap.getProfileImage() != null) authorProfileImage = ap.getProfileImage();
                    }
                }
            } else {
                socialPost.setUser(creator);
                socialPost.setActorToken(null);
            }
            socialPost.setAuthorUsername(authorUsername);
            socialPost.setAuthorProfileImage(authorProfileImage);
        }
                
        if (community != null) {
            socialPost.syncCommunityDenormalizedFields(community);
        }
        
        socialPost.inheritLocationFromUser(creator);
        // 3. Attach media URLs if uploaded
        if (!uploadedMediaUrls.isEmpty()) {
            socialPost.setMediaUrlsList(uploadedMediaUrls);
        }
        SocialPost savedSocialPost = socialPostRepository.save(socialPost);
        log.info("Auto-created SocialPost {} with {} media files for poll by user {}", 
                savedSocialPost.getId(), savedSocialPost.getMediaCount(), creator.getId());
        // 4. Trigger community published event if post belongs to a community
        if (savedSocialPost.getCommunityId() != null) {
            try {
                communityService.onPostPublished(savedSocialPost, savedSocialPost.getCommunityId());
            } catch (Exception e) {
                log.warn("[Community] onPostPublished failed for poll post={} community={}: {}",
                        savedSocialPost.getId(), savedSocialPost.getCommunityId(), e.getMessage());
            }
        }
        // 5. Build and attach the Poll object to the SocialPost
        Poll poll = buildPoll(req, creator, savedSocialPost);
        Poll savedPoll = pollRepository.save(poll);
        attachOptions(savedPoll, req.getOptions());
        log.info("Created Poll {} with {} options for SocialPost {}",
                savedPoll.getId(), req.getOptions().size(), savedSocialPost.getId());
        return PollResponse.from(savedPoll, false, true, List.of());
    }

    private List<String> uploadMediaFilesWithValidation(List<MultipartFile> files, Long userId) {
        try {
            SocialPostUtility.validateMediaFiles(files);
            return mediaService.uploadMediaFiles(files, userId);
        } catch (Exception e) {
            log.error("Failed to upload media files for user: {}", userId, e);
            throw new ServiceException("Failed to upload media files: " + e.getMessage(), e);
        }
    }


    @Transactional(rollbackFor = Exception.class)
    public PollResponse vote(Long pollId, List<Long> optionIds, User voter) {
        Poll poll = pollRepository.findByIdWithOptions(pollId)
                .orElseThrow(() -> new RuntimeException("Poll not found: " + pollId));

        if (!poll.isOpenForVoting()) {
            throw new IllegalStateException("This poll is closed.");
        }

        String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();
        String actorToken = resolveActorToken(voter);
        if (idempotencyKey != null) {
            java.util.Optional<PollVote> existingVote = pollVoteRepository.findByIdempotencyKey(idempotencyKey);
            if (existingVote.isPresent()) {
                log.info("Idempotency hit: Returning existing vote for key {}", idempotencyKey);
                List<Long> votedIds = pollVoteRepository.findOptionIdsByPollIdAndActorOrUser(pollId, actorToken, voter.getId());
                return PollResponse.from(poll, true, true, votedIds);
            }
        }

        // Check for existing votes (for re-voting/un-voting)
        List<PollVote> existingVotes = pollVoteRepository.findByPollIdAndActorOrUser(pollId, actorToken, voter.getId());
        if (!existingVotes.isEmpty()) {
            for (PollVote vote : existingVotes) {
                PollOption opt = vote.getPollOption();
                opt.decrementVoteCount();
                poll.decrementTotalVotes();
            }
            pollVoteRepository.deleteAll(existingVotes);
            pollOptionRepository.saveAll(existingVotes.stream()
                .map(PollVote::getPollOption)
                .collect(Collectors.toList()));
        }

        // If optionIds is empty, we consider it an "un-vote" and return early
        if (optionIds == null || optionIds.isEmpty()) {
            Poll updatedPoll = pollRepository.save(poll);
            return PollResponse.from(updatedPoll, false, true, List.of());
        }

        if (!Boolean.TRUE.equals(poll.getAllowMultipleVotes()) && optionIds.size() > 1) {
            throw new IllegalArgumentException("This poll only allows one choice.");
        }

        List<PollOption> newOptions = pollOptionRepository.findAllById(optionIds);
        if (newOptions.size() != optionIds.size()) {
            throw new RuntimeException("One or more poll options not found.");
        }

        List<PollVote> votes = new ArrayList<>();
        for (PollOption option : newOptions) {
            if (!option.getPoll().getId().equals(pollId)) {
                throw new IllegalArgumentException("Option " + option.getId() + " does not belong to this poll.");
            }
            votes.add(PollVote.builder()
                    .poll(poll)
                    .user(voter)
                    .actorToken(actorToken)
                    .pollOption(option)
                    .idempotencyKey(idempotencyKey)
                    .build());
            option.incrementVoteCount();
            poll.incrementTotalVotes();
        }

        pollVoteRepository.saveAll(votes);
        pollOptionRepository.saveAll(newOptions);

        Poll updatedPoll = pollRepository.save(poll);
        List<Long> votedIds = pollVoteRepository.findOptionIdsByPollIdAndActorOrUser(pollId, actorToken, voter.getId());
        return PollResponse.from(updatedPoll, !votedIds.isEmpty(), true, votedIds);
    }


    @Transactional(readOnly = true)
    public PollResponse getPollResponse(Long pollId, User requestingUser) {
        Poll poll = pollRepository.findByIdWithOptions(pollId)
                .orElseThrow(() -> new RuntimeException("Poll not found: " + pollId));

        if (poll.getSocialPost() != null && poll.getSocialPost().getCommunityId() != null) {
            SocialPost sp = poll.getSocialPost();
            if (requestingUser == null || !com.JanSahayak.AI.payload.PostUtility.isAdmin(requestingUser)) {
                String privacy = sp.getCommunityPrivacy();
                if (privacy == null && sp.getCommunity() != null && sp.getCommunity().getPrivacy() != null) {
                    privacy = sp.getCommunity().getPrivacy().name();
                }
                if ("PRIVATE".equalsIgnoreCase(privacy) || "SECRET".equalsIgnoreCase(privacy)) {
                    if (requestingUser == null || !communityService.isMember(sp.getCommunityId(), requestingUser.getId())) {
                        throw new SecurityException("User does not have permission to view this poll");
                    }
                }
            }
        }

        String actorToken = resolveActorToken(requestingUser);
        boolean userHasVoted = requestingUser != null && pollVoteRepository.existsByPollIdAndActorOrUser(pollId, actorToken, requestingUser.getId());
        boolean showResults  = poll.shouldShowResults(userHasVoted);
        List<Long> votedIds  = userHasVoted
                ? pollVoteRepository.findOptionIdsByPollIdAndActorOrUser(pollId, actorToken, requestingUser.getId())
                : List.of();

        return PollResponse.from(poll, userHasVoted, showResults, votedIds);
    }

    @Transactional(readOnly = true)
    public PollResponse getPollBySocialPostId(Long socialPostId, User requestingUser) {
        Poll poll = pollRepository.findBySocialPostId(socialPostId)
                .orElseThrow(() -> new RuntimeException("No poll found for this post."));
        return getPollResponse(poll.getId(), requestingUser);
    }

    @Transactional(rollbackFor = Exception.class)
    public void closePoll(Long pollId, User requestingUser) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new RuntimeException("Poll not found: " + pollId));

        String actorToken = resolveActorToken(requestingUser);
        boolean isOwner = (actorToken != null && poll.getCreatedByActorToken() != null && poll.getCreatedByActorToken().equals(actorToken))
                || (poll.getCreatedBy() != null && requestingUser != null && poll.getCreatedBy().getId().equals(requestingUser.getId()));
        if (!isOwner && !requestingUser.isAdmin()) {
            throw new SecurityException("Not allowed to close this poll.");
        }

        poll.deactivate();
        pollRepository.save(poll);
        log.info("Poll {} closed by user {}", pollId, requestingUser.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePoll(Long pollId, User requestingUser) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new RuntimeException("Poll not found: " + pollId));

        String actorToken = resolveActorToken(requestingUser);
        boolean isOwner = (actorToken != null && poll.getCreatedByActorToken() != null && poll.getCreatedByActorToken().equals(actorToken))
                || (poll.getCreatedBy() != null && requestingUser != null && poll.getCreatedBy().getId().equals(requestingUser.getId()));
        if (!isOwner && !requestingUser.isAdmin()) {
            throw new SecurityException("Not allowed to delete this poll.");
        }

        poll.deactivate();
        pollRepository.save(poll);
        
        SocialPost socialPost = poll.getSocialPost();
        if (socialPost != null) {
            socialPostService.deleteSocialPost(socialPost.getId(), requestingUser);
        }
        
        log.info("Poll {} deleted by user {}", pollId, requestingUser.getId());
    }

    private void validatePollRequest(CreatePollRequest req) {
        if (req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new IllegalArgumentException("Poll question cannot be empty.");
        }
        if (req.getQuestion().length() > 500) {
            throw new IllegalArgumentException("Poll question cannot exceed 500 characters.");
        }
        if (req.getOptions() == null || req.getOptions().size() < 2 || req.getOptions().size() > 4) {
            throw new IllegalArgumentException("A poll must have between 2 and 4 options.");
        }
        for (String opt : req.getOptions()) {
            if (opt == null || opt.isBlank()) {
                throw new IllegalArgumentException("Option text cannot be blank.");
            }
            if (opt.length() > 200) {
                throw new IllegalArgumentException("Option text cannot exceed 200 characters.");
            }
        }
    }

    private Poll buildPoll(CreatePollRequest req, User creator, SocialPost socialPost) {
        Date expiresAt = null;
        if (req.getExpiresIn() != null 
                && !req.getExpiresIn().equalsIgnoreCase("never") 
                && !req.getExpiresIn().equalsIgnoreCase("always")) {
            long durationMs = switch (req.getExpiresIn().toLowerCase()) {
                case "1h" -> 60 * 60 * 1000L;
                case "1d", "24h" -> 24 * 60 * 60 * 1000L;
                case "3d" -> 3 * 24 * 60 * 60 * 1000L;
                case "7d" -> 7 * 24 * 60 * 60 * 1000L;
                default   -> 24 * 60 * 60 * 1000L;
            };
            expiresAt = new Date(System.currentTimeMillis() + durationMs);
        }

        String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();
        String actorToken = resolveActorToken(creator);

        return Poll.builder()
                .question(req.getQuestion())
                .socialPost(socialPost)
                .createdBy(creator)
                .createdByActorToken(actorToken)
                .expiresAt(expiresAt)
                .idempotencyKey(idempotencyKey)
                .allowMultipleVotes(Boolean.TRUE.equals(req.getAllowMultipleVotes()))
                .showResultsBeforeExpiry(req.getShowResultsBeforeExpiry() == null || req.getShowResultsBeforeExpiry())
                .ipAddress(com.JanSahayak.AI.util.IpUtils.getClientIpFromContext())
                .build();
    }

    /**
     * FIX: Replaced per-option pollOptionRepository.save() inside a forEach loop with
     * a single pollOptionRepository.saveAll() call. The old code issued one INSERT
     * per option (up to 4 inserts per poll creation). saveAll() issues a single
     * batch INSERT regardless of how many options are being saved.
     */
    private void attachOptions(Poll poll, List<String> optionTexts) {
        List<PollOption> options = IntStream.range(0, optionTexts.size())
                .mapToObj(i -> PollOption.builder()
                        .poll(poll)
                        .optionText(optionTexts.get(i).trim())
                        .optionOrder(i + 1)
                        .build())
                .collect(Collectors.toList());

        pollOptionRepository.saveAll(options);  // single batch INSERT
        poll.getOptions().addAll(options);
    }
}
