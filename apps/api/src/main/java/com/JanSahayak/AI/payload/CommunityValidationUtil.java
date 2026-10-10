package com.JanSahayak.AI.payload;

import com.JanSahayak.AI.config.Constant;

import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.User;

/**
 * Community-specific validation helpers.
 *
 * Delegate pattern:
 *  - User-level validation (null check, active, banned) → PostUtility.validateUser() / validateUserId()
 *  - Community-level guards are handled here
 *
 * All methods throw com.JanSahayak.AI.exception.ValidationException (your existing class)
 * which is caught by GlobalExceptionHandler → HTTP 400.
 */
public final class CommunityValidationUtil {

    private CommunityValidationUtil() {}

    // ── Pagination constants ──────────────────────────────────────────────────

    // ── User validation (delegates to PostUtility) ────────────────────────────

    /**
     * Validates user ID is not null and positive.
     * Delegates to PostUtility for consistency with the rest of the app.
     */
    public static void validateUserId(Long userId) {
        PostUtility.validateUserId(userId);
    }

    /**
     * Validates the User entity is active and not banned.
     * Delegates to PostUtility for consistency with the rest of the app.
     */
    public static void validateUser(User user) {
        PostUtility.validateUser(user);
    }

    // ── Community field validation ────────────────────────────────────────────

    public static void validateCommunityId(Long communityId) {
        if (communityId == null || communityId <= 0) {
            throw new ValidationException("Community ID must be a positive number.");
        }
    }

    public static void validateCommunityName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Community name is required.");
        }
        String trimmed = name.trim();
        if (trimmed.length() < 3) {
            throw new ValidationException("Community name must be at least 3 characters.");
        }
        if (trimmed.length() > 100) {
            throw new ValidationException("Community name cannot exceed 100 characters.");
        }
    }

    public static void validateCommunityDescription(String description) {
        if (description != null && description.length() > 1000) {
            throw new ValidationException("Community description cannot exceed 1000 characters.");
        }
    }

    // ── Community state guards ────────────────────────────────────────────────

    /**
     * Throws if the community is not in ACTIVE status.
     */
    public static void assertCommunityActive(Community community) {
        if (community == null) {
            throw new ValidationException("Community not found.");
        }
        if (!community.isActive()) {
            throw new ValidationException("This community is " + community.getStatus().name().toLowerCase() +
                    " and cannot accept new interactions.");
        }
    }

    /**
     * Throws if the community is not PUBLIC (used to gate feed surfacing operations).
     */
    public static void assertCommunityPublic(Community community) {
        if (!community.isPublic()) {
            throw new ValidationException("This operation is only allowed for PUBLIC communities.");
        }
    }

    // ── Member state guards ───────────────────────────────────────────────────

    public static void assertNotBanned(boolean isBanned) {
        if (isBanned) {
            throw new ValidationException("You have been banned from this community.");
        }
    }

    public static void assertNotMuted(boolean isMuted) {
        if (isMuted) {
            throw new ValidationException("You are currently muted in this community.");
        }
    }

    public static void assertActiveMembership(boolean isActive) {
        if (!isActive) {
            throw new ValidationException("Your membership in this community is no longer active.");
        }
    }

    // ── Limit validation (mirrors PaginationUtils pattern) ───────────────────

    public static int validateCommunityListLimit(Integer limit) {
        if (limit == null || limit <= 0)        return Constant.DEFAULT_COMMUNITY_LIST_LIMIT;
        if (limit > Constant.MAX_COMMUNITY_LIST_LIMIT)   return Constant.MAX_COMMUNITY_LIST_LIMIT;
        return limit;
    }

    public static int validateMemberListLimit(Integer limit) {
        if (limit == null || limit <= 0)        return Constant.DEFAULT_MEMBER_LIST_LIMIT;
        if (limit > Constant.MAX_MEMBER_LIST_LIMIT)       return Constant.MAX_MEMBER_LIST_LIMIT;
        return limit;
    }

    public static int validateJoinRequestLimit(Integer limit) {
        if (limit == null || limit <= 0)        return Constant.DEFAULT_JOIN_REQUEST_LIMIT;
        if (limit > Constant.MAX_JOIN_REQUEST_LIMIT)     return Constant.MAX_JOIN_REQUEST_LIMIT;
        return limit;
    }


    public static String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) return null;
        return java.util.Arrays.stream(tags.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(t -> !t.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }


    public static String truncate(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
