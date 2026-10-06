package com.govlyx.AI.payload;

import com.govlyx.AI.config.Constant;
import com.govlyx.AI.enums.BroadcastScope;
import com.govlyx.AI.enums.PostStatus;
import com.govlyx.AI.exception.*;
import com.govlyx.AI.model.Post;
import com.govlyx.AI.model.User;
import com.govlyx.AI.service.PinCodeLookupService;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.web.util.HtmlUtils;

@Slf4j
public class PostUtility {

    // ===== State/District Pincode Mapping Constants =====
    public static final Map<String, String> STATE_TO_PINCODE_PREFIX = new HashMap<String, String>() {{
        put("Andhra Pradesh", "51,52,53");
        put("Arunachal Pradesh", "79");
        put("Assam", "78");
        put("Bihar", "80,81,82,83,84,85");
        put("Chhattisgarh", "49");
        put("Delhi", "11");
        put("Goa", "40");
        put("Gujarat", "36,37,38,39");
        put("Haryana", "12,13");
        put("Himachal Pradesh", "17");
        put("Jharkhand", "81,82,83,84,85");
        put("Karnataka", "56,57,58,59");
        put("Kerala", "67,68,69");
        put("Madhya Pradesh", "45,46,47,48");
        put("Maharashtra", "40,41,42,43,44");
        put("Manipur", "79");
        put("Meghalaya", "79");
        put("Mizoram", "79");
        put("Nagaland", "79");
        put("Odisha", "75,76,77");
        put("Punjab", "14,15,16");
        put("Rajasthan", "30,31,32,33,34");
        put("Sikkim", "73");
        put("Tamil Nadu", "60,61,62,63,64");
        put("Telangana", "50,51,52,53,54");
        put("Tripura", "79");
        put("Uttar Pradesh", "20,21,22,23,24,25,26,27,28");
        // BUG FIX #5: was "24,24" (duplicated prefix 24, which is actually UP).
        // Uttarakhand correct prefixes are 24,25,26 — shared with UP which is intentional
        // in India's postal system; district-level prefix (3 digits) distinguishes them.
        put("Uttarakhand", "24,25,26");
        put("West Bengal", "70,71,72,73,74");
        put("Andaman and Nicobar Islands", "74");
        put("Chandigarh", "16");
        put("Dadra and Nagar Haveli", "39");
        put("Daman and Diu", "39");
        put("Jammu and Kashmir", "18,19");
        put("Ladakh", "19");
        put("Lakshadweep", "68");
        put("Puducherry", "60,63");
    }};

    // BUG FIX #11: SecureRandom instance — reused (thread-safe) rather than recreated per call.
    // Original code used java.util.Random which is predictable and not suitable for
    // generating file name tokens used in upload paths.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ===== CONSOLIDATED VALIDATION METHODS =====

    /**
     * Centralized user validation
     */
    public static void validateUser(User user) {
        if (user == null) {
            throw new UserNotFoundException("User cannot be null");
        }
        if (user.getId() == null) {
            throw new UserNotFoundException("User ID cannot be null");
        }
        if (user.getActualUsername() == null || user.getActualUsername().trim().isEmpty()) {
            throw new UserNotFoundException("User username cannot be null or empty");
        }
        // BUG FIX #4: getIsActive() returns Boolean (wrapper), not boolean (primitive).
        // The original "!user.getIsActive()" auto-unboxes the wrapper and throws
        // NullPointerException when getIsActive() returns null (possible on legacy rows).
        // Boolean.TRUE.equals() safely handles null — returns false without crashing.
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new ValidationException("User account is inactive");
        }
    }

    /**
     * Centralized post validation
     */
    public static void validatePost(Post post) {
        if (post == null) {
            throw new PostNotFoundException("Post cannot be null");
        }
        if (post.getId() == null) {
            throw new PostNotFoundException("Post ID cannot be null");
        }
        if (post.getContent() == null) {
            throw new ValidationException("Post content cannot be null");
        }
        if (post.getStatus() == null) {
            throw new ValidationException("Post status cannot be null");
        }
    }

    /**
     * Enhanced user validation with role checks
     */
    public static void validateUserWithRole(User user, String requiredRole) {
        validateUser(user);
        if (user.getRole() == null) {
            throw new ValidationException("User role is required");
        }
        if (!user.getRole().getName().equals(requiredRole)) {
            throw new SecurityException("User does not have required role: " + requiredRole);
        }
    }

    /**
     * Validate post content with detailed checks
     */
    public static void validatePostContentDetailed(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ValidationException("Post content cannot be empty");
        }
        if (content.length() > Constant.MAX_POST_CONTENT_LENGTH) {
            throw new ValidationException("Post content cannot exceed " + Constant.MAX_POST_CONTENT_LENGTH + " characters");
        }
        if (content.trim().length() < 3) {
            throw new ValidationException("Post content must be at least 3 characters long");
        }
    }

    public static void validatePostContent(String content) {
        validatePostContentDetailed(content);
    }

    public static void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("User ID must be a positive number");
        }
    }

    public static void validatePostId(Long postId) {
        if (postId == null || postId <= 0) {
            throw new ValidationException("Post ID must be a positive number");
        }
    }

    // ===== CONSOLIDATED ROLE CHECK METHODS =====

    /**
     * Check if user is admin
     */
    public static boolean isAdmin(User user) {
        try {
            return user != null &&
                    user.getRole() != null &&
                    user.getRole().getName() != null &&
                    Constant.ROLE_ADMIN.equals(user.getRole().getName());
        } catch (Exception ex) {
            log.warn("Error checking admin status for user: {}",
                    user != null ? user.getActualUsername() : "null", ex);
            return false;
        }
    }

    /**
     * Check if user is department
     */
    public static boolean isDepartment(User user) {
        try {
            return user != null &&
                    user.getRole() != null &&
                    user.getRole().getName() != null &&
                    Constant.ROLE_DEPARTMENT.equals(user.getRole().getName());
        } catch (Exception ex) {
            log.warn("Error checking department status for user: {}",
                    user != null ? user.getActualUsername() : "null", ex);
            return false;
        }
    }

    /**
     * Check if user is normal user
     */
    public static boolean isNormalUser(User user) {
        return isCitizen(user);
    }

    /**
     * Check if user is a citizen (ROLE_USER or not admin/department)
     */
    public static boolean isCitizen(User user) {
        try {
            if (user == null) return true;
            if (isAdmin(user) || isDepartment(user)) return false;
            return true;
        } catch (Exception ex) {
            return true;
        }
    }

    /**
     * Check if user can create broadcast posts
     */
    public static boolean canCreateBroadcast(User user) {
        return isAdmin(user) || isDepartment(user);
    }

    /**
     * Check if user can modify post (owner, admin, or department).
     * ZK Blind Shield Fix (Bug 4): pass actorToken so decoupled citizen posts
     * are owned by their actor_token, not by user_id.
     */
    public static boolean canUserModifyPost(Post post, User user, String actorToken) {
        if (post == null || user == null) {
            return false;
        }
        return isPostOwner(post, user, actorToken) || isAdmin(user) || isDepartment(user);
    }

    /**
     * Legacy 2-arg overload — delegates to the 3-arg version with no actorToken.
     * Authority/admin callers that don't carry an X-Actor-Token header use this path.
     */
    public static boolean canUserModifyPost(Post post, User user) {
        return canUserModifyPost(post, user, null);
    }

    /**
     * Check if user can resolve posts in specific pincode
     */
    public static boolean canUserResolvePostsInPincode(User user, String pincode) {
        try {
            if (!Constant.isValidIndianPincode(pincode)) {
                return false;
            }

            if (isAdmin(user)) {
                return true;
            }

            if (isDepartment(user) && user.hasPincode() && Constant.isValidIndianPincode(user.getPincode())) {
                // Exact pincode match
                if (user.getPincode().equals(pincode)) {
                    return true;
                }
                // District-level permission (same district prefix)
                if (user.isInSameDistrict(pincode)) {
                    return true;
                }
                // State-level permission (same state prefix)
                if (user.isInSameState(pincode)) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            log.warn("Failed to validate user resolution permissions for user: {} and pincode: {}",
                    user.getActualUsername(), pincode, e);
            return false;
        }
    }

    // ===== POST VISIBILITY AND ACCESS METHODS =====

    /**
     * Check if post is visible to user with geographic awareness
     */
    public static boolean isPostVisibleToUser(Post post, User user) {
        try {
            if (post == null || user == null) {
                return false;
            }

            // Admin can see all posts
            if (isAdmin(user)) {
                return true;
            }

            // Check post status visibility
            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                return false;
            }

            // Handle broadcast posts
            if (post.isBroadcastPost()) {
                return post.isVisibleToUser(user);
            }

            // For regular posts, check geographic relevance
            if (!user.hasPincode() || !post.hasPincodeLocation()) {
                return true; // Default to visible if location data is missing
            }

            // Use pincode prefix logic for geographic relevance
            return user.isInSameState(post.getPostPincode()) ||
                    user.isInSameDistrict(post.getPostPincode()) ||
                    (post.getUser() != null && isDepartment(post.getUser()));

        } catch (Exception e) {
            log.warn("Error checking post visibility for post {} and user {}",
                    post.getId(), user.getActualUsername(), e);
            return true; // Default to visible on error
        }
    }

    /**
     * Check if post is visible to anonymous users
     */
    public static boolean isPostVisibleToAnonymousUser(Post post) {
        try {
            if (post == null) {
                return false;
            }

            // Only show ACTIVE posts
            if (post.getStatus() != PostStatus.ACTIVE) {
                return false;
            }

            // Only show posts from active users
            // BUG FIX #4 (same pattern): guard getIsActive() against null
            if (post.getUser() == null || !Boolean.TRUE.equals(post.getUser().getIsActive())) {
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("Error checking post visibility for post {} for anonymous user", post.getId(), e);
            return true;
        }
    }

    /**
     * Check if post is geographically relevant to user
     */
    public static boolean isPostGeographicallyRelevantToUser(Post post, User user) {
        try {
            if (post == null || user == null) {
                return false;
            }

            // If post has broadcast scope, use the post's own visibility logic
            if (post.isBroadcastPost()) {
                return post.isVisibleToUser(user);
            }

            // For regular posts, check if user and post author are in same geographic area
            if (!user.hasPincode() || !post.hasPincodeLocation()) {
                return true; // Default to visible if location data is missing
            }

            // Use pincode prefix logic for geographic relevance
            return user.isInSameState(post.getPostPincode()) ||
                    user.isInSameDistrict(post.getPostPincode());
        } catch (Exception e) {
            log.warn("Failed to check geographic relevance for post {} and user {}",
                    post.getId(), user.getActualUsername(), e);
            return true; // Default to visible on error
        }
    }

    // ===== TEXT PROCESSING METHODS =====

    /**
     * Truncate text to specified length with suffix
     */
    public static String truncateText(String text, int maxLength) {
        if (text == null) return "";
        String unescaped = HtmlUtils.htmlUnescape(text);
        if (unescaped.length() <= maxLength) return unescaped;
        return unescaped.substring(0, maxLength) + Constant.POST_CONTENT_TRUNCATION_SUFFIX;
    }

    /**
     * Truncate text with custom suffix
     */
    public static String truncateText(String text, int maxLength, String suffix) {
        if (text == null) return "";
        String unescaped = HtmlUtils.htmlUnescape(text);
        if (unescaped.length() <= maxLength) return unescaped;
        return unescaped.substring(0, maxLength) + (suffix != null ? suffix : "...");
    }

    /**
     * Calculate time ago string
     */
    public static String calculateTimeAgo(Date createdAt) {
        if (createdAt == null) {
            return "Unknown";
        }

        long diffInMillies = new Date().getTime() - createdAt.getTime();
        long diffInMinutes = diffInMillies / (60 * 1000);
        long diffInHours = diffInMillies / (60 * 60 * 1000);
        long diffInDays = diffInMillies / (24 * 60 * 60 * 1000);

        if (diffInMinutes < 1) {
            return "Just now";
        } else if (diffInMinutes < 60) {
            return diffInMinutes + " minute" + (diffInMinutes == 1 ? "" : "s") + " ago";
        } else if (diffInHours < 24) {
            return diffInHours + " hour" + (diffInHours == 1 ? "" : "s") + " ago";
        } else if (diffInDays < 7) {
            return diffInDays + " day" + (diffInDays == 1 ? "" : "s") + " ago";
        } else {
            long weeks = diffInDays / 7;
            return weeks + " week" + (weeks == 1 ? "" : "s") + " ago";
        }
    }

    // ===== USERNAME VALIDATION METHODS =====

    /**
     * Check if username format is valid
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        // Username validation: alphanumeric and underscore only, 3-50 characters
        return username.matches("^[a-zA-Z0-9_]{3,50}$");
    }

    /**
     * Extract usernames from user tags in content
     */
    public static List<String> extractUserTags(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> usernames = new ArrayList<>();
        java.util.regex.Matcher matcher = Constant.USERNAME_TAG_PATTERN.matcher(content);

        while (matcher.find()) {
            String username = matcher.group(1);
            if (!usernames.contains(username) && isValidUsername(username)) {
                usernames.add(username);
            }
        }

        return usernames;
    }

    // ===== PINCODE PREFIX CONVERSION METHODS =====

    /**
     * Convert list of state names to pincode prefixes
     */
    public static List<String> convertStatesToPincodePrefixes(List<String> stateNames,
                                                               PinCodeLookupService pinCodeLookupService) {
        if (stateNames == null || stateNames.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> prefixes = new HashSet<>();
        for (String state : stateNames) {
            if (state == null || state.trim().isEmpty()) continue;
            
            // Get sample 2-digit prefixes for this state from database (case-insensitive)
            List<String> statePrefixes = pinCodeLookupService.getDistinctPrefixesByState(state.trim(), 2);
            if (statePrefixes != null) {
                prefixes.addAll(statePrefixes);
            }
        }

        if (prefixes.isEmpty()) {
            log.warn("Could not find any pincode prefixes for states: {}. Fallback to map resolution.", stateNames);
            // Optional: Keep map as second fallback if DB is missing data
            return stateNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(state -> !state.isEmpty())
                .map(state -> STATE_TO_PINCODE_PREFIX.entrySet().stream()
                        .filter(entry -> entry.getKey().equalsIgnoreCase(state))
                        .map(Map.Entry::getValue)
                        .findFirst().orElse(null))
                .filter(Objects::nonNull)
                .flatMap(p -> Arrays.stream(p.split(",")))
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        }

        return new ArrayList<>(prefixes);
    }

    /**
     * Convert list of district names to 3-digit pincode prefixes
     */
    public static List<String> convertDistrictsToPincodePrefixes(List<String> districtNames,
                                                                 PinCodeLookupService pinCodeLookupService) {
        if (districtNames == null || districtNames.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> prefixes = new HashSet<>();
        for (String district : districtNames) {
            if (district == null || district.trim().isEmpty()) continue;
            
            // Get sample 3-digit prefixes for this district
            List<String> districtPrefixes = pinCodeLookupService.getDistinctPrefixesByDistrict(district.trim(), 3);
            if (districtPrefixes != null) {
                prefixes.addAll(districtPrefixes);
            }
        }

        if (prefixes.isEmpty()) {
            log.warn("Could not find any 3-digit pincode prefixes for districts: {}", districtNames);
            return new ArrayList<>();
        }

        return prefixes.stream().sorted().collect(Collectors.toList());
    }

    /**
     * Get state name from pincode prefix
     */
    public static String getStateFromPincodePrefix(String pincodePrefix) {
        if (pincodePrefix == null || pincodePrefix.length() < 2) {
            return null;
        }

        String prefix = pincodePrefix.substring(0, 2);
        return STATE_TO_PINCODE_PREFIX.entrySet().stream()
                .filter(entry -> Arrays.asList(entry.getValue().split(",")).contains(prefix))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * Validate if pincode belongs to specific state prefix
     */
    public static boolean isPincodeInStatePrefix(String pincode, String statePrefix) {
        if (pincode == null || pincode.length() < 2 || statePrefix == null) {
            return false;
        }
        return pincode.startsWith(statePrefix);
    }

    // ===== BROADCASTING HELPER METHODS =====

    /**
     * Convert state names to their corresponding pincode prefixes for broadcasting
     */
    public static String convertStatesToTargetString(List<String> stateNames,
                                                     PinCodeLookupService pinCodeLookupService) {
        if (stateNames == null || stateNames.isEmpty()) {
            return null;
        }

        List<String> prefixes = convertStatesToPincodePrefixes(stateNames, pinCodeLookupService);
        return prefixes.isEmpty() ? null : String.join(",", prefixes);
    }

    /**
     * Convert district names to their corresponding pincode prefixes for broadcasting
     */
    public static String convertDistrictsToTargetString(List<String> districtNames,
                                                        PinCodeLookupService pinCodeLookupService) {
        if (districtNames == null || districtNames.isEmpty()) {
            return null;
        }

        List<String> prefixes = convertDistrictsToPincodePrefixes(districtNames, pinCodeLookupService);
        return prefixes.isEmpty() ? null : String.join(",", prefixes);
    }

    /**
     * Convert pincode list to target string for broadcasting
     */
    public static String convertPincodesToTargetString(List<String> pincodes) {
        if (pincodes == null || pincodes.isEmpty()) {
            return null;
        }

        return pincodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(pincode -> pincode.matches("\\d{6}"))
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * Get readable state names from pincode prefixes
     */
    public static List<String> getStateNamesFromPrefixes(List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return new ArrayList<>();
        }

        return prefixes.stream()
                .map(PostUtility::getStateFromPincodePrefix)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Resolve stored comma-separated prefixes to readable district names.
     */
    public static List<String> resolvePrefixesToDistrictNames(String prefixesStr, PinCodeLookupService service) {
        if (prefixesStr == null || prefixesStr.isEmpty()) return new ArrayList<>();
        
        return Arrays.stream(prefixesStr.split(","))
                .map(String::trim)
                .map(service::getDistrictNameByPrefix)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Resolve stored comma-separated prefixes to readable state names.
     */
    public static List<String> resolvePrefixesToStateNames(String prefixesStr) {
        if (prefixesStr == null || prefixesStr.isEmpty()) return new ArrayList<>();
        
        return Arrays.stream(prefixesStr.split(","))
                .map(String::trim)
                .map(PostUtility::getStateFromPincodePrefix)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    // ===== ENHANCED BROADCAST PERMISSION VALIDATION =====

    /**
     * Enhanced broadcast permission validation for India-only app
     */
    public static void validateBroadcastPermission(User user) {
        validateUser(user);
        if (user.getRole() == null) {
            throw new ValidationException("User role is required for broadcasting");
        }

        if (!canCreateBroadcast(user)) {
            throw new SecurityException("Only admin and department users can create broadcast posts");
        }
    }

    /**
     * Enhanced broadcast scope validation with India-only enforcement
     */
    public static void validateBroadcastScope(BroadcastScope scope) {
        if (scope == null) {
            throw new ValidationException("Broadcast scope cannot be null");
        }
    }

    /**
     * Comprehensive broadcast scope validation for India-only app
     */
    public static void validateBroadcastScope(BroadcastScope scope, String targetCountry,
                                              List<String> targetStates, List<String> targetDistricts,
                                              List<String> targetPincodes) {
        validateBroadcastScope(scope);

        String validatedCountry = Constant.normalizeTargetCountry(targetCountry);

        switch (scope) {
            case COUNTRY:
                if (!Constant.DEFAULT_TARGET_COUNTRY.equals(validatedCountry)) {
                    log.warn("Overriding target country {} to {} for India-only app",
                            targetCountry, Constant.DEFAULT_TARGET_COUNTRY);
                }
                if ((targetStates != null && !targetStates.isEmpty()) ||
                        (targetDistricts != null && !targetDistricts.isEmpty()) ||
                        (targetPincodes != null && !targetPincodes.isEmpty())) {
                    throw new ValidationException("Country-wide broadcasts cannot specify regional targets");
                }
                break;
            case STATE:
                if (targetStates == null || targetStates.isEmpty()) {
                    throw new ValidationException("Target states are required for state-level broadcasts");
                }
                if (targetStates.size() > Constant.MAX_TARGET_STATES) {
                    throw new ValidationException("Cannot target more than " + Constant.MAX_TARGET_STATES + " states");
                }
                validateTargetStates(targetStates);
                break;
            case DISTRICT:
                if (targetDistricts == null || targetDistricts.isEmpty()) {
                    throw new ValidationException("Target districts are required for district-level broadcasts");
                }
                if (targetDistricts.size() > Constant.MAX_TARGET_DISTRICTS) {
                    throw new ValidationException("Cannot target more than " + Constant.MAX_TARGET_DISTRICTS + " districts");
                }
                validateTargetDistricts(targetDistricts);
                break;
            case AREA:
                if (targetPincodes == null || targetPincodes.isEmpty()) {
                    throw new ValidationException("Target pincodes are required for area-level broadcasts");
                }
                if (targetPincodes.size() > Constant.MAX_TARGET_PINCODES) {
                    throw new ValidationException("Cannot target more than " + Constant.MAX_TARGET_PINCODES + " pincodes");
                }
                validateTargetPincodes(targetPincodes);
                break;
        }
    }

    public static void validateTargetStates(List<String> targetStates) {
        if (targetStates == null || targetStates.isEmpty()) {
            throw new ValidationException("Target states cannot be empty");
        }

        for (String state : targetStates) {
            if (state == null || state.trim().isEmpty()) {
                throw new ValidationException("State name cannot be empty");
            }
            String trimmedState = state.trim();
            if (!STATE_TO_PINCODE_PREFIX.containsKey(trimmedState)) {
                boolean foundByCode = STATE_TO_PINCODE_PREFIX.keySet().stream()
                        .anyMatch(stateName -> stateName.toLowerCase().contains(trimmedState.toLowerCase()));

                if (!foundByCode) {
                    throw new ValidationException("Invalid or unsupported state: " + state +
                            ". Supported states: " + String.join(", ", STATE_TO_PINCODE_PREFIX.keySet()));
                }
            }
        }
    }

    public static void validateTargetDistricts(List<String> targetDistricts) {
        if (targetDistricts == null || targetDistricts.isEmpty()) {
            throw new ValidationException("Target districts cannot be empty");
        }

        for (String district : targetDistricts) {
            if (district == null || district.trim().isEmpty()) {
                throw new ValidationException("District name cannot be empty");
            }
        }
    }

    public static void validateTargetPincodes(List<String> targetPincodes) {
        if (targetPincodes == null || targetPincodes.isEmpty()) {
            throw new ValidationException("Target pincodes cannot be empty");
        }

        for (String pincode : targetPincodes) {
            if (!Constant.isValidIndianPincode(pincode)) {
                throw new ValidationException("Invalid Indian pincode format: " + pincode);
            }
        }
    }

    public static void validateTargetPincodesWithLookup(List<String> targetPincodes,
                                                        PinCodeLookupService pinCodeLookupService) {
        validateTargetPincodes(targetPincodes);

        for (String pincode : targetPincodes) {
            if (!pinCodeLookupService.isValidPincode(pincode)) {
                throw new ValidationException("Pincode not found in system: " + pincode);
            }
        }
    }

    // ===== MEDIA FILE HANDLING METHODS =====
    // uploadMediaFile / deleteMediaFile / getMediaFilePath removed — all storage
    // is now handled by DriveStorageService via DrivePostMediaAdapter.
    // validateMediaFile, validateFileContent, validateImageFile, validateVideoFile
    // are kept because DrivePostMediaAdapter still calls them for validation.

    public static void validateMediaFile(MultipartFile file) {
        validateMediaFile(file, 5242880L, 536870912L); // Default sizes: 5MB for images, 512MB for videos
    }

    public static void validateMediaFile(MultipartFile file, long maxImageSize, long maxVideoSize) {
        if (file == null || file.isEmpty()) {
            throw new MediaValidationException("File cannot be empty");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new MediaValidationException("Invalid file name");
        }

        String extension = getFileExtension(fileName).toLowerCase();
        String contentType = file.getContentType();

        validateFileContent(file, extension);

        if (Constant.ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            validateImageFile(file, extension, contentType, maxImageSize);
        } else if (Constant.ALLOWED_VIDEO_EXTENSIONS.contains(extension)) {
            validateVideoFile(file, extension, contentType, maxVideoSize);
        } else {
            throw new MediaValidationException("File type not supported. Allowed types: " +
                    "Images: " + String.join(", ", Constant.ALLOWED_IMAGE_EXTENSIONS) +
                    ", Videos: " + String.join(", ", Constant.ALLOWED_VIDEO_EXTENSIONS));
        }
    }

    public static void validateFileContent(MultipartFile file, String extension) {
        try {
            // Read first 512 bytes directly from InputStream to avoid loading entire file into memory
            int maxHeaderSize = 512;
            byte[] header = new byte[maxHeaderSize];
            int bytesRead;
            
            try (java.io.InputStream is = file.getInputStream()) {
                bytesRead = is.read(header, 0, maxHeaderSize);
            }

            if (bytesRead <= 0) {
                throw new MediaValidationException("File is empty");
            }
            
            // Trim the array if we read fewer bytes than maxHeaderSize
            if (bytesRead < maxHeaderSize) {
                header = Arrays.copyOf(header, bytesRead);
            }

            String hexHeader = bytesToHex(header).toUpperCase();

            switch (extension.toLowerCase()) {
                case ".jpg":
                case ".jpeg":
                    if (!hexHeader.startsWith("FFD8FF")) {
                        throw new MediaValidationException("File content doesn't match JPEG format");
                    }
                    break;
                case ".png":
                    if (!hexHeader.startsWith("89504E47")) {
                        throw new MediaValidationException("File content doesn't match PNG format");
                    }
                    break;
                case ".webp":
                    // WebP files start with "RIFF" followed by file size, then "WEBP"
                    if (!hexHeader.startsWith("52494646") || !hexHeader.contains("57454250")) {
                        throw new MediaValidationException("File content doesn't match WebP format");
                    }
                    break;
                case ".mp4":
                    // MP4 files have various signatures, check for common ones
                    if (!hexHeader.contains("667479") && // "ftyp"
                            !hexHeader.contains("6D6F6F76") && // "moov"
                            !hexHeader.contains("6D646174") && // "mdat"
                            !hexHeader.contains("66726565")) { // "free"
                        log.warn("MP4 file signature validation inconclusive for file: {}", file.getOriginalFilename());
                    }
                    break;
                case ".mov":
                    // MOV files are similar to MP4
                    if (!hexHeader.contains("667479") && // "ftyp"
                            !hexHeader.contains("6D6F6F76") && // "moov"
                            !hexHeader.contains("717420") && // "qt "
                            !hexHeader.contains("6D646174")) { // "mdat"
                        log.warn("MOV file signature validation inconclusive for file: {}", file.getOriginalFilename());
                    }
                    break;
                default:
                    log.debug("No content validation implemented for extension: {}", extension);
            }

            log.debug("File content validation passed for: {} (extension: {})",
                    file.getOriginalFilename(), extension);

        } catch (IOException e) {
            throw new MediaValidationException("Failed to read file content: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during file content validation: {}", e.getMessage(), e);
            throw new MediaValidationException("Failed to validate file content: " + e.getMessage());
        }
    }

    public static void validateImageFile(MultipartFile file, String extension, String contentType, long maxImageSize) {
        if (file.getSize() > maxImageSize) {
            throw new MediaValidationException("Image size exceeds maximum allowed size of " +
                    (maxImageSize / 1024 / 1024) + "MB");
        }

        if (contentType == null || !contentType.startsWith("image/")) {
            throw new MediaValidationException("Invalid image content type: " + contentType);
        }

        Map<String, String> extensionToContentType = Map.of(
                ".jpg", "image/jpeg",
                ".jpeg", "image/jpeg",
                ".png", "image/png",
                ".webp", "image/webp"
        );

        String expectedContentType = extensionToContentType.get(extension.toLowerCase());
        if (expectedContentType != null && !expectedContentType.equals(contentType)) {
            log.warn("Content type mismatch for file: {} (expected: {}, actual: {})",
                    file.getOriginalFilename(), expectedContentType, contentType);
        }

        log.debug("Image validation passed: {} ({:.2f}MB, type: {})",
                file.getOriginalFilename(), file.getSize() / 1024.0 / 1024.0, contentType);
    }

    public static void validateVideoFile(MultipartFile file, String extension, String contentType, long maxVideoSize) {
        if (file.getSize() > maxVideoSize) {
            throw new MediaValidationException("Video size exceeds maximum allowed size of " +
                    (maxVideoSize / 1024 / 1024) + "MB");
        }

        if (contentType == null || !contentType.startsWith("video/")) {
            throw new MediaValidationException("Invalid video content type: " + contentType);
        }

        if (".mp4".equals(extension) && !"video/mp4".equals(contentType)) {
            log.warn("Content type mismatch for MP4 file: {} (type: {})", file.getOriginalFilename(), contentType);
        }
        if (".mov".equals(extension) && contentType != null && !contentType.contains("quicktime") && !contentType.contains("mov")) {
            log.warn("Content type mismatch for MOV file: {} (type: {})", file.getOriginalFilename(), contentType);
        }

        log.info("Video validation passed: {} ({:.2f}MB, type: {})",
                file.getOriginalFilename(), file.getSize() / 1024.0 / 1024.0, contentType);
    }

    // ===== POST BUSINESS LOGIC HELPER METHODS =====

    public static boolean isPostOwner(Post post, User user) {
        return isPostOwner(post, user, null);
    }

    public static boolean isPostOwner(Post post, User user, String actorToken) {
        if (post == null) return false;
        // 1. Zero-knowledge cryptographic proof of ownership
        if (actorToken != null && !actorToken.isBlank() && post.getActorToken() != null) {
            if (post.getActorToken().equals(actorToken.trim())) {
                return true;
            }
        }
        // 2. Legacy / Authority relational ownership
        if (post.getUser() != null &&
                post.getUser().getId() != null &&
                user != null &&
                user.getId() != null &&
                post.getUser().getId().equals(user.getId())) {
            return true;
        }
        // 3. Username matching fallback
        if (post.getUser() != null && user != null) {
            if (post.getUser().getActualUsername() != null && user.getActualUsername() != null &&
                    post.getUser().getActualUsername().equalsIgnoreCase(user.getActualUsername())) {
                return true;
            }
            if (post.getUser().getUsername() != null && user.getUsername() != null &&
                    post.getUser().getUsername().equalsIgnoreCase(user.getUsername())) {
                return true;
            }
        }
        return false;
    }


    public static boolean canUserModifyPostTags(Post post, User currentUser) {
        if (post == null || currentUser == null) {
            return false;
        }

        return isPostOwner(post, currentUser) ||
                isAdmin(currentUser) ||
                isDepartment(currentUser);
    }

    public static boolean canUserRemovePostTag(Post post, User currentUser, Long taggedUserId) {
        if (post == null || currentUser == null || taggedUserId == null) {
            return false;
        }
        return isPostOwner(post, currentUser) || isAdmin(currentUser);
    }

    // ===== FILE HELPER METHODS =====

    public static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
    }

    public static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        String nameWithoutExt = fileName.contains(".") ?
                fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
        return nameWithoutExt.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * BUG FIX #11: method was named generateSecureRandomString but used java.util.Random
     * which is predictable and NOT cryptographically secure. File upload tokens generated
     * with predictable random can be guessed, enabling unauthorised file access.
     * Now uses SecureRandom (static instance reused for efficiency — SecureRandom is thread-safe).
     */
    public static String generateSecureRandomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(SECURE_RANDOM.nextInt(characters.length())));
        }
        return sb.toString();
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    // ===== MEDIA TYPE HELPER METHODS =====

    public static boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        String extension = getFileExtension(fileName);
        return Constant.ALLOWED_IMAGE_EXTENSIONS.contains(extension);
    }

    public static boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        String extension = getFileExtension(fileName);
        return Constant.ALLOWED_VIDEO_EXTENSIONS.contains(extension);
    }

    public static String getMediaType(String fileName) {
        if (fileName == null) return "unknown";
        if (isImageFile(fileName)) return "image";
        if (isVideoFile(fileName)) return "video";
        return "unknown";
    }

    // ===== STATISTICS HELPER METHODS =====

    public static Map<String, Object> createMediaConstraints(long maxImageSize, long maxVideoSize) {
        Map<String, Object> constraints = new HashMap<>();

        Map<String, Object> imageConstraints = new HashMap<>();
        imageConstraints.put("maxSize", maxImageSize);
        imageConstraints.put("maxSizeMB", Math.round(maxImageSize / 1024.0 / 1024.0 * 100.0) / 100.0);
        imageConstraints.put("allowedFormats", new ArrayList<>(Constant.ALLOWED_IMAGE_EXTENSIONS));

        Map<String, Object> videoConstraints = new HashMap<>();
        videoConstraints.put("maxSize", maxVideoSize);
        videoConstraints.put("maxSizeMB", Math.round(maxVideoSize / 1024.0 / 1024.0 * 100.0) / 100.0);
        videoConstraints.put("allowedFormats", new ArrayList<>(Constant.ALLOWED_VIDEO_EXTENSIONS));

        Map<String, Object> generalConstraints = new HashMap<>();
        generalConstraints.put("maxContentLength", Constant.MAX_POST_CONTENT_LENGTH);

        constraints.put("image", imageConstraints);
        constraints.put("video", videoConstraints);
        constraints.put("general", generalConstraints);

        return constraints;
    }

    // ===== INDIA-ONLY APP SPECIFIC METHODS =====

    /**
     * CRITICAL: Create post for country-wide broadcast with India defaults
     */
    public static Post createCountryBroadcastPost(String content, User user, String mediaFileName) {
        validateUser(user);
        validateBroadcastPermission(user);
        validatePostContent(content);

        Post post = new Post();
        post.setContent(content.trim());
        post.setUser(user);
        post.setImageName(mediaFileName);
        post.setStatus(PostStatus.ACTIVE);
        post.setCreatedAt(new Date());

        // Set country-wide broadcast targeting all of India
        post.setBroadcastScope(BroadcastScope.COUNTRY);
        post.setTargetCountry(Constant.DEFAULT_TARGET_COUNTRY);

        return post;
    }

    /**
     * CRITICAL: Check if a post is visible to all Indian users
     */
    public static boolean isVisibleToAllIndianUsers(Post post) {
        return post != null &&
                post.isBroadcastPost() &&
                post.getBroadcastScope() == BroadcastScope.COUNTRY &&
                Constant.APP_COUNTRY_CODE.equals(post.getTargetCountry()) &&
                post.getStatus() != null &&
                post.getStatus().isVisible();
    }

    /**
     * CRITICAL: Check if this is a government country-wide broadcast
     */
    public static boolean isAllIndiaGovernmentBroadcast(Post post) {
        return post != null &&
                post.isCountryWideGovernmentBroadcast() &&
                post.getStatus() == PostStatus.ACTIVE;
    }

    /**
     * Create comprehensive post visibility filter for Indian users
     */
    public static List<Post> filterPostsForIndianUser(List<Post> posts, User user) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        if (user == null) {
            return Collections.emptyList();
        }

        return posts.stream()
                .filter(post -> {
                    if (isAllIndiaGovernmentBroadcast(post)) {
                        return true;
                    }
                    if (isVisibleToAllIndianUsers(post)) {
                        return true;
                    }
                    return post.isVisibleToUser(user);
                })
                .collect(Collectors.toList());
    }

    /**
     * Log country broadcast creation for audit purposes
     */
    public static void logCountryBroadcast(Post post, User user) {
        if (post != null && user != null && isVisibleToAllIndianUsers(post)) {
            String broadcastType = isAllIndiaGovernmentBroadcast(post) ?
                    "GOVERNMENT COUNTRY BROADCAST (ALL USERS)" : "COUNTRY BROADCAST";

            log.info("{} - Post ID: {}, User: {} ({}), Content Preview: {}",
                    broadcastType,
                    post.getId(),
                    user.getActualUsername(),
                    user.getRole().getName(),
                    post.getContent().length() > 50 ?
                            post.getContent().substring(0, 50) + "..." : post.getContent());
        }
    }

    /**
     * Validate that user is in India (has valid Indian pincode)
     */
    public static void validateIndianUser(User user) {
        validateUser(user);

        if (!user.hasPincode()) {
            throw new ValidationException("User must have a valid Indian pincode");
        }

        if (!Constant.isValidIndianPincode(user.getPincode())) {
            throw new ValidationException("User must have a valid Indian pincode format");
        }
    }

    /**
     * Get user's state prefix for geographic targeting
     */
    public static String getUserStatePrefix(User user) {
        if (user == null || !user.hasPincode()) {
            return null;
        }

        return Constant.getStatePrefixFromPincode(user.getPincode());
    }

    public static void validateTargetPincodeForUser(String targetPincode) {
        if (targetPincode != null && !targetPincode.trim().isEmpty()) {
            if (!Constant.isValidIndianPincode(targetPincode.trim())) {
                throw new ValidationException("Invalid Indian pincode format: " + targetPincode);
            }
        }
    }

    /**
     * Get user's district prefix for geographic targeting
     */
    public static String getUserDistrictPrefix(User user) {
        if (user == null || !user.hasPincode()) {
            return null;
        }

        return Constant.getDistrictPrefixFromPincode(user.getPincode());
    }

    /**
     * Validate broadcasting targets are within India
     */
    public static void validateIndianBroadcastTargets(BroadcastScope scope, List<String> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }

        switch (scope) {
            case AREA:
                for (String pincode : targets) {
                    if (!Constant.isValidIndianPincode(pincode)) {
                        throw new ValidationException("Invalid Indian pincode for broadcast: " + pincode);
                    }
                }
                break;

            case DISTRICT:
                for (String districtPrefix : targets) {
                    if (districtPrefix == null || !districtPrefix.matches(Constant.INDIAN_DISTRICT_PREFIX_PATTERN)) {
                        throw new ValidationException("Invalid Indian district prefix: " + districtPrefix);
                    }
                }
                break;

            case STATE:
                for (String statePrefix : targets) {
                    if (statePrefix == null || !statePrefix.matches(Constant.INDIAN_STATE_PREFIX_PATTERN)) {
                        throw new ValidationException("Invalid Indian state prefix: " + statePrefix);
                    }
                }
                break;

            case COUNTRY:
                throw new ValidationException("Country-wide broadcasts should not specify regional targets");

            default:
                throw new ValidationException("Invalid broadcast scope: " + scope);
        }
    }

    // ===== RESOLUTION RATE CALCULATION =====

    /**
     * Calculate user resolution rate for department users
     */
    public static double calculateUserResolutionRate(User user, Long totalTagged, Long resolved) {
        try {
            if (!isDepartment(user)) {
                return 0.0;
            }

            if (totalTagged == null || totalTagged == 0) {
                return 0.0;
            }

            if (resolved == null) {
                resolved = 0L;
            }

            return Math.round((double) resolved / totalTagged * 100.0 * 100.0) / 100.0;
        } catch (Exception e) {
            log.warn("Failed to calculate resolution rate for user: {}",
                    user != null ? user.getActualUsername() : "null", e);
            return 0.0;
        }
    }

    // ===== POST STATUS VALIDATION =====

    /**
     * Check if post status allows updates
     */
    public static boolean postAllowsUpdates(Post post) {
        return post != null &&
                post.getStatus() != null &&
                post.getStatus().allowsUpdates();
    }

    /**
     * Check if post status is visible
     */
    public static boolean isPostStatusVisible(Post post) {
        return post != null &&
                post.getStatus() != null &&
                post.getStatus().isVisible();
    }

    /**
     * Check if post is eligible for display
     */
    public static boolean isPostEligibleForDisplay(Post post) {
        if (post == null || !post.isEligibleForDisplay()) {
            return false;
        }
        // Decoupled citizen posts under Zero-Knowledge Blind Shield have user == null
        if (post.getUser() == null) {
            return true;
        }
        return Boolean.TRUE.equals(post.getUser().getIsActive());
    }

    /**
     * Sanitizes input strings intended to be used in SQL LIKE clauses.
     * Escapes '%', '_', and '\' to prevent wildcard injection (DoS).
     */
    public static String sanitizeSqlLike(String input) {
        if (input == null) return null;
        return input.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
    }
}
