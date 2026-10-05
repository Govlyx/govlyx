package com.Govlyx.AI.service;

import com.Govlyx.AI.dto.CommunityDto.CommunityInviteDto.*;
import com.Govlyx.AI.dto.PaginatedResponse;
import com.Govlyx.AI.exception.ValidationException;
import com.Govlyx.AI.model.*;
import com.Govlyx.AI.repository.*;
import com.Govlyx.AI.payload.PaginationUtils;
import com.Govlyx.AI.payload.PaginationUtils.PaginationSetup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommunityInviteService {

    private final CommunityInviteRepo  inviteRepo;
    private final CommunityRepo        communityRepo;
    private final CommunityMemberRepo  memberRepo;
    private final UserRepo             userRepo;
    private final NotificationService  notificationService;
    private final org.springframework.cache.CacheManager cacheManager;

    /**
     * Frontend base URL used to build invite links.
     * Set in application.properties:  app.frontend.base-url=https://Govlyx.in
     */
    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    // ── Limits ────────────────────────────────────────────────────────────────
    private static final int DEFAULT_INVITE_LIST_LIMIT = 20;
    private static final int MAX_INVITE_LIST_LIMIT      = 50;

    /** Max pending invites an admin can have open at once per community. */
    private static final int MAX_PENDING_INVITES_PER_COMMUNITY = 200;

    // =========================================================================
    // 1. SEND INVITE (admin/owner action)
    // =========================================================================

    /**
     * Creates a new invite.
     *
     * If req.inviteeUsername is present  → targeted single-use invite.
     * If req.inviteeUsername is absent   → shareable multi-use link invite.
     *
     * Both PRIVATE and SECRET communities are supported.
     * PUBLIC communities are rejected — no point in inviting to a public community.
     *
     * @param communityId  ID of the community
     * @param requesterId  ID of the admin/owner sending the invite
     * @param req          request body
     * @return InviteResponse with the generated token and frontend link
     */
    public InviteResponse sendInvite(Long communityId, Long requesterId, SendInviteRequest req) {

        // ── 1. Load & validate community ──────────────────────────────────────
        Community community = findCommunityOrThrow(communityId);


        if (!community.isActive()) {
            throw new ValidationException("Cannot send invites to an archived or suspended community.");
        }

        // ── 2. Check requester is admin, owner, or moderator ──────────────────────────────
        assertModeratorOrAbove(community, requesterId);

        // ── 3. Cap total pending invites ──────────────────────────────────────
        // (prevent abuse — e.g. spamming thousands of link invites)
        long pendingCount = inviteRepo.findPendingByCommunityIdCursor(
                communityId, null,
                org.springframework.data.domain.PageRequest.of(0, MAX_PENDING_INVITES_PER_COMMUNITY + 1)
        ).size();
        if (pendingCount >= MAX_PENDING_INVITES_PER_COMMUNITY) {
            throw new ValidationException(
                    "Too many pending invites. Revoke some before creating new ones.");
        }

        // ── 4. Resolve invitee (if targeted invite) ────────────────────────────
        User invitee = null;
        if (req.getInviteeId() != null) {
            invitee = findUserOrThrow(req.getInviteeId());
        } else if (req.getInviteeUsername() != null && !req.getInviteeUsername().isBlank()) {
            List<User> matches = userRepo.findByActualUsername(req.getInviteeUsername().trim());
            if (matches.isEmpty()) {
                throw new NoSuchElementException("User not found: " + req.getInviteeUsername());
            }
            invitee = matches.get(0);
        }
        
        if (invitee != null) {
            // Must not already be a member
            if (memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(communityId, invitee.getId())) {
                throw new ValidationException(
                        "@" + invitee.getActualUsername() + " is already a member of this community.");
            }

            // Must not already have a pending invite
            if (inviteRepo.existsPendingInviteForUser(communityId, invitee.getId())) {
                throw new ValidationException(
                        "@" + invitee.getActualUsername() + " already has a pending invite.");
            }
        }

        // ── 5. Load inviter ───────────────────────────────────────────────────
        User inviter = findUserOrThrow(requesterId);

        // ── 6. Build & save invite ────────────────────────────────────────────
        CommunityInvite invite = CommunityInvite.builder()
                .community(community)
                .inviter(inviter)
                .invitee(invitee)            // null = shareable link
                .singleUse(invitee != null)  // targeted = single-use; link = multi-use
                .message(req.getMessage() != null ? req.getMessage().trim() : null)
                .build();
        // token + expiresAt set in @PrePersist
        inviteRepo.save(invite);

        log.info("Invite created: communityId={} inviter={} invitee={} token={} singleUse={}",
                communityId, inviter.getActualUsername(),
                invitee != null ? invitee.getActualUsername() : "LINK",
                invite.getToken(), invite.getSingleUse());

        // Send notification to the invitee (only for targeted invites)
        if (invitee != null) {
            notificationService.notifyCommunityInvite(invite);
        }

        return toInviteResponse(invite);
    }

    // =========================================================================
    // 2. LIST PENDING INVITES (admin/owner action)
    // =========================================================================

    @Transactional(readOnly = true)
    public PaginatedResponse<InviteResponse> listPendingInvites(
            Long communityId, Long requesterId, Long cursor, Integer limit) {

        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, requesterId);

        PaginationSetup setup = PaginationUtils.setupPagination(
                "listPendingInvites", cursor, limit,
                DEFAULT_INVITE_LIST_LIMIT, MAX_INVITE_LIST_LIMIT);

        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        List<CommunityInvite> raw = inviteRepo.findPendingByCommunityIdCursor(
                communityId, setup.getSanitizedCursor(), pageable);

        List<InviteResponse> mapped = raw.stream()
                .map(this::toInviteResponse)
                .collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(
                mapped,
                setup.getValidatedLimit(),
                ir -> raw.get(mapped.indexOf(ir)).getId());
    }

    // =========================================================================
    // 3. REVOKE INVITE (admin/owner action)
    // =========================================================================

    public void revokeInvite(Long communityId, Long inviteId, Long requesterId) {
        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, requesterId);

        CommunityInvite invite = inviteRepo.findById(inviteId)
                .orElseThrow(() -> new NoSuchElementException("Invite not found: " + inviteId));

        if (!invite.getCommunity().getId().equals(communityId)) {
            throw new IllegalArgumentException("Invite does not belong to this community.");
        }
        if (!invite.isPending()) {
            throw new ValidationException("Only PENDING invites can be revoked.");
        }

        invite.revoke();
        inviteRepo.save(invite);

        log.info("Invite {} revoked by userId={} in communityId={}", inviteId, requesterId, communityId);
    }

    // =========================================================================
    // 4. PREVIEW INVITE — public endpoint, no auth required
    // =========================================================================

    /**
     * Returns enough info for the AcceptInvitePage to render community details
     * BEFORE the user logs in. Does not expose who was invited.
     */
    @Transactional(readOnly = true)
    public InvitePreviewResponse previewInvite(String token) {
        CommunityInvite invite = findInviteByTokenOrThrow(token);

        Community c = invite.getCommunity();

        boolean valid = invite.isUsable();

        return InvitePreviewResponse.builder()
                .communityName(c.getName())
                .communitySlug(c.getSlug())
                .communityDescription(c.getDescription())
                .communityPrivacy(c.getPrivacy() != null ? c.getPrivacy().name() : null)
                .memberCount(c.getMemberCount() != null ? c.getMemberCount() : 0)
                .inviterUsername(
                        invite.getInviter() != null
                                ? invite.getInviter().getActualUsername()
                                : null)
                .message(invite.getMessage())
                .expiresAt(invite.getExpiresAt())
                .valid(valid)
                .build();
    }

    // =========================================================================
    // 5. ACCEPT INVITE — authenticated user action
    // =========================================================================

    /**
     * The invitee (or any user, for link invites) calls this to join the community.
     *
     * Rules:
     *  - Token must be PENDING and not expired.
     *  - For single-use targeted invite: caller must match invite.invitee.
     *  - For multi-use link invite: any authenticated user can accept.
     *  - User must not already be a member.
     *  - Community must be ACTIVE.
     *
     * On success:
     *  - A CommunityMember row is created.
     *  - memberCount is incremented.
     *  - Single-use invite is flipped to ACCEPTED.
     *  - Multi-use invite stays PENDING (useCount++).
     */
    public AcceptInviteResponse acceptInvite(String token, Long acceptorUserId) {

        // ── 1. Load invite ────────────────────────────────────────────────────
        CommunityInvite invite = findInviteByTokenOrThrow(token);
        Community community = invite.getCommunity();

        // ── 2. Must not already be a member ───────────────────────────────────
        if (memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(community.getId(), acceptorUserId)) {
            // Return success-like response so the frontend can redirect
            return AcceptInviteResponse.builder()
                    .communityId(community.getId())
                    .communityName(community.getName())
                    .communitySlug(community.getSlug())
                    .joined(true)
                    .message("You are already a member of this community.")
                    .build();
        }

        // ── 3. Check usability ────────────────────────────────────────────────
        if (!invite.isPending()) {
            String reason = switch (invite.getStatus()) {
                case ACCEPTED  -> "This invite has already been used.";
                case REVOKED   -> "This invite has been revoked by the community admin.";
                case EXPIRED   -> "This invite has expired.";
                case DECLINED  -> "This invite was already declined.";
                default        -> "This invite is no longer valid.";
            };
            throw new ValidationException(reason);
        }
        if (invite.isExpired()) {
            invite.markExpired();
            inviteRepo.save(invite);
            throw new ValidationException("This invite link has expired.");
        }

        // ── 4. For targeted invite: verify caller is the intended recipient ───
        if (Boolean.TRUE.equals(invite.getSingleUse()) && invite.getInvitee() != null) {
            if (!invite.getInvitee().getId().equals(acceptorUserId)) {
                throw new ValidationException(
                        "This invite was sent to a specific user and cannot be used by you.");
            }
        }

        // ── 5. Community must still be active ─────────────────────────────────
        if (!community.isActive()) {
            throw new ValidationException("This community is no longer active.");
        }

        // ── 6. Load acceptor ──────────────────────────────────────────────────
        User acceptor = findUserOrThrow(acceptorUserId);

        // ── 7. Create membership ──────────────────────────────────────────────
        memberRepo.findByCommunityIdAndUserId(community.getId(), acceptorUserId)
            .ifPresentOrElse(
                m -> {
                    // User was once a member, reactivate/reset them
                    m.setIsActive(true);
                    m.setMemberRole(CommunityMember.MemberRole.MEMBER);
                    m.setIsBanned(false);
                    m.setJoinedAt(new java.util.Date());
                    memberRepo.save(m);
                },
                () -> {
                    // New member, insert fresh record
                    memberRepo.save(CommunityMember.builder()
                            .community(community)
                            .user(acceptor)
                            .memberRole(CommunityMember.MemberRole.MEMBER)
                            .build());
                }
            );

        communityRepo.incrementMemberCount(community.getId());
        communityRepo.incrementNewMembersLast7d(community.getId());

        // ── 8. Mark invite used ───────────────────────────────────────────────
        invite.markAccepted(acceptorUserId);
        inviteRepo.save(invite);

        // Notify the inviter that the invite was accepted
        try {
            notificationService.notifyCommunityInviteAccepted(invite, acceptor);
        } catch (Exception e) {
            log.error("Failed to notify inviter of invite acceptance", e);
        }

        // ── 9. Evict cache so user can immediately post ─────────────────────────────────
        if (cacheManager != null) {
            org.springframework.cache.Cache membershipCache = cacheManager.getCache("community-membership");
            if (membershipCache != null) {
                membershipCache.evict(community.getId() + "_" + acceptorUserId);
                membershipCache.evict("MOD_" + community.getId() + "_" + acceptorUserId);
            }
            org.springframework.cache.Cache listCache = cacheManager.getCache(com.Govlyx.AI.config.Constant.CACHE_COMMUNITY_LIST);
            if (listCache != null) {
                listCache.evict(acceptorUserId);
            }
        }

        log.info("Invite accepted: token={} communityId={} acceptorId={}",
                token, community.getId(), acceptorUserId);

        return AcceptInviteResponse.builder()
                .communityId(community.getId())
                .communityName(community.getName())
                .communitySlug(community.getSlug())
                .joined(true)
                .message("Welcome to " + community.getName() + "!")
                .build();
    }

    // =========================================================================
    // 6. DECLINE INVITE — authenticated user action
    // =========================================================================

    /**
     * Allows the targeted invitee to explicitly decline a pending invite.
     * Once DECLINED, the invite no longer appears in the "pending" list filter.
     */
    @Transactional
    public void declineInvite(String token, Long declinerUserId) {
        CommunityInvite invite = inviteRepo.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("Invite not found: " + token));

        // Only the targeted invitee can decline
        if (invite.getInvitee() == null || !invite.getInvitee().getId().equals(declinerUserId)) {
            throw new ValidationException("You cannot decline this invite.");
        }
        if (!invite.isPending()) {
            throw new ValidationException("This invite is no longer pending.");
        }

        invite.decline();
        inviteRepo.save(invite);

        log.info("Invite {} declined by userId={}", invite.getId(), declinerUserId);

        // Notify the inviter that the invite was declined
        User decliner = findUserOrThrow(declinerUserId);
        try {
            notificationService.notifyCommunityInviteDeclined(invite, decliner);
        } catch (Exception e) {
            log.error("Failed to notify inviter of invite declination", e);
        }
    }

    // =========================================================================
    // 7. SCHEDULED: expire overdue invites every hour
    // =========================================================================

    @Scheduled(cron = "0 0 * * * *")   // every hour, on the hour
    public void expireOverdueInvites() {
        int count = inviteRepo.expireOverdueInvites(new java.util.Date());
        if (count > 0) {
            log.info("Expired {} overdue community invites", count);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private CommunityInvite findInviteByTokenOrThrow(String token) {
        return inviteRepo.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException(
                        "Invalid or expired invite link. Please ask the community admin for a new one."));
    }

    private Community findCommunityOrThrow(Long id) {
        return communityRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Community not found: " + id));
    }

    private User findUserOrThrow(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
    }

    private void assertModeratorOrAbove(Community community, Long userId) {
        if (community.isOwnedBy(userId)) return;
        CommunityMember m = memberRepo.findByCommunityIdAndUserId(community.getId(), userId)
                .orElseThrow(() -> new SecurityException(
                        "Access denied: you are not a member of this community."));
        if (!m.canModerate()) {
            throw new SecurityException("Only community staff (admins, owners, or moderators) can manage invites.");
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private InviteResponse toInviteResponse(CommunityInvite invite) {
        String inviteLink = frontendBaseUrl + "/invite/" + invite.getToken();

        String inviteeUsername     = null;
        String inviteeProfileImage = null;
        if (invite.getInvitee() != null) {
            inviteeUsername     = invite.getInvitee().getActualUsername();
            inviteeProfileImage = invite.getInvitee().getProfileImage();
        }

        String inviterUsername = invite.getInviter() != null
                ? invite.getInviter().getActualUsername()
                : null;

        return InviteResponse.builder()
                .id(invite.getId())
                .token(invite.getToken())
                .inviteLink(inviteLink)
                .inviteeUsername(inviteeUsername)
                .inviteeProfileImage(inviteeProfileImage)
                .inviterUsername(inviterUsername)
                .message(invite.getMessage())
                .status(invite.getStatus() != null ? invite.getStatus().name() : null)
                .singleUse(Boolean.TRUE.equals(invite.getSingleUse()))
                .useCount(invite.getUseCount() != null ? invite.getUseCount() : 0)
                .createdAt(invite.getCreatedAt())
                .expiresAt(invite.getExpiresAt())
                .actionedAt(invite.getActionedAt())
                .build();
    }
}
