package com.JanSahayak.AI.payload;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for SocialPost operations
 * Reuses validation and helper methods from PostUtility to avoid duplication
 */
@Slf4j
public class SocialPostUtility {

    // ===== VALIDATION METHODS =====

    public static void validateSocialPost(SocialPost post) {
        if (post == null) {
            throw new PostNotFoundException("Social post cannot be null");
        }
        if (post.getId() == null) {
            throw new PostNotFoundException("Social post ID cannot be null");
        }
        if (post.getContent() == null) {
            throw new ValidationException("Social post content cannot be null");
        }
        if (post.getStatus() == null) {
            throw new ValidationException("Social post status cannot be null");
        }
    }

    public static void validateSocialPostContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ValidationException("Social post content cannot be empty");
        }
        if (content.length() > Constant.MAX_SOCIAL_POST_CONTENT_LENGTH) {
            throw new ValidationException("Social post content cannot exceed " +
                    Constant.MAX_SOCIAL_POST_CONTENT_LENGTH + " characters");
        }
        if (content.trim().length() < 3) {
            throw new ValidationException("Social post content must be at least 3 characters long");
        }
    }

    public static void validateSocialPostId(Long postId) {
        if (postId == null || postId <= 0) {
            throw new ValidationException("Social post ID must be a positive number");
        }
    }

    // ===== REUSE METHODS FROM PostUtility =====

    public static void validateUser(User user) {
        PostUtility.validateUser(user);
    }

    public static void validateUserId(Long userId) {
        PostUtility.validateUserId(userId);
    }

    // ===== POST OWNERSHIP =====

    public static boolean isSocialPostOwner(SocialPost post, User user) {
        return post != null &&
                post.getUser() != null &&
                post.getUser().getId() != null &&
                user != null &&
                user.getId() != null &&
                post.getUser().getId().equals(user.getId());
    }

    public static boolean canUserModifySocialPost(SocialPost post, User user) {
        if (post == null || user == null) {
            return false;
        }
        return isSocialPostOwner(post, user) || PostUtility.isAdmin(user);
    }

    public static boolean canUserDeleteSocialPost(SocialPost post, User user) {
        if (post == null || user == null) {
            return false;
        }
        return isSocialPostOwner(post, user) || PostUtility.isAdmin(user);
    }

    // ===== VISIBILITY LOGIC =====

    public static boolean isSocialPostVisibleToUser(SocialPost post, User user) {
        try {
            if (post == null || user == null) {
                return false;
            }

            // Admin can see all posts
            if (PostUtility.isAdmin(user)) {
                return true;
            }

            // Check if post is eligible for display
            if (!post.isEligibleForDisplay()) {
                return false;
            }

            // Use viral expansion logic
            return post.isVisibleToUserWithViralExpansion(user);

        } catch (Exception e) {
            log.warn("Error checking social post visibility for post {} and user {}",
                    post.getId(), user.getActualUsername(), e);
            // BUG FIX #8: original returned true (visible) on any exception.
            // A visibility check failure (e.g. lazy-load error, NPE on location fields)
            // should NOT silently expose flagged or restricted posts to everyone.
            // Defaulting to false is the safe, secure choice.
            return false;
        }
    }

    // ===== RECOMMENDATION SCORING =====

    public static List<SocialPost> rankPostsForUser(List<SocialPost> posts, User user) {
        if (posts == null || posts.isEmpty() || user == null) {
            return Collections.emptyList();
        }

        // BUG FIX #9: original called checkAndUpdateViralStatus() inside
        // calculateFinalRecommendationScore(), which is called inside the sort comparator.
        // Mutating entity fields (viralTier, expansionLevel, isViral) during a sort
        // comparator is unpredictable — the comparator may be called multiple times per
        // element, producing inconsistent ordering.
        // Fix: update viral status for every post BEFORE sorting, so the comparator
        // only reads already-stable scores without any side effects.
        posts.forEach(post -> {
            try {
                post.checkAndUpdateViralStatus();
            } catch (Exception e) {
                log.warn("Failed to update viral status for post {}", post.getId(), e);
            }
        });

        return posts.stream()
                .filter(post -> isSocialPostVisibleToUser(post, user))
                .sorted((p1, p2) -> {
                    double score1 = calculateFinalRecommendationScore(p1, user);
                    double score2 = calculateFinalRecommendationScore(p2, user);
                    return Double.compare(score2, score1); // Descending order
                })
                .collect(Collectors.toList());
    }

    public static double calculateFinalRecommendationScore(SocialPost post, User user) {
        if (post == null || user == null) {
            return 0.0;
        }

        // NOTE: viral status is intentionally NOT updated here anymore (BUG FIX #9).
        // It is updated once before sorting in rankPostsForUser() and filterForHomeFeed().
        // Scores read here are already up-to-date and stable.

        // Calculate component scores — guard against null wrapper fields
        double engagementScore = post.getEngagementScore() != null ? post.getEngagementScore() : 0.0;
        double viralityScore   = post.getViralityScore()   != null ? post.getViralityScore()   : 0.0;

        double engagementWeight = engagementScore * 0.4;
        double viralityWeight   = viralityScore   * 0.3;
        double relevanceWeight  = post.calculateRelevanceScoreWithViralBoost(user) * 0.2;
        double freshnessWeight  = calculateFreshnessScore(post) * 0.1;

        return engagementWeight + viralityWeight + relevanceWeight + freshnessWeight;
    }

    private static double calculateFreshnessScore(SocialPost post) {
        if (post == null || post.getCreatedAt() == null) {
            return 0.0;
        }

        long ageInHours = post.getAgeInHours();

        if (ageInHours <= 1)   return 100.0;
        if (ageInHours <= 6)   return 80.0;
        if (ageInHours <= 24)  return 60.0;
        if (ageInHours <= 72)  return 40.0;
        if (ageInHours <= 168) return 20.0; // 7 days

        return 10.0;
    }

    // ===== HASHTAG PROCESSING - REUSES PostUtility PATTERN =====

    public static List<String> extractHashtagsFromContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> hashtags = new HashSet<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("#[a-zA-Z0-9_]+");
        java.util.regex.Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String hashtag = matcher.group().toLowerCase();
            if (hashtag.length() > 2 && hashtag.length() <= 50) { // Valid hashtag length
                hashtags.add(hashtag);
            }
        }

        return new ArrayList<>(hashtags);
    }

    // ===== USER MENTIONS - REUSES PostUtility =====

    public static List<String> extractMentionsFromContent(String content) {
        return PostUtility.extractUserTags(content);
    }

    // ===== TRENDING DETECTION =====

    public static List<SocialPost> filterTrendingPosts(List<SocialPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        return posts.stream()
                .filter(SocialPost::isTrendingPost)
                .sorted((p1, p2) -> {
                    double v1 = p1.getViralityScore() != null ? p1.getViralityScore() : 0.0;
                    double v2 = p2.getViralityScore() != null ? p2.getViralityScore() : 0.0;
                    return Double.compare(v2, v1);
                })
                .collect(Collectors.toList());
    }

    public static List<SocialPost> filterLocalPosts(List<SocialPost> posts, User user) {
        if (posts == null || posts.isEmpty() || user == null || !user.hasPincode()) {
            return Collections.emptyList();
        }

        return posts.stream()
                .filter(post -> post.isFromSameDistrict(user) || post.isFromSameArea(user))
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                .collect(Collectors.toList());
    }

    // ===== LOCATION DISPLAY =====

    public static String getDisplayLocation(SocialPost post) {
        if (post == null || !post.hasLocation()) {
            return null;
        }

        if (!post.getShowLocation()) {
            return "India"; // Generic location if user doesn't want to show exact location
        }

        if (post.getLocationName() != null && !post.getLocationName().isEmpty()) {
            return post.getLocationName();
        }

        return post.getPincode();
    }

    // ===== STATISTICS =====

    public static Map<String, Object> calculatePostStatistics(SocialPost post) {
        Map<String, Object> stats = new HashMap<>();

        if (post == null) {
            return stats;
        }

        stats.put("totalEngagement", post.getTotalEngagementCount());
        stats.put("engagementRate", String.format("%.2f%%", post.getEngagementRate()));
        stats.put("viralTier", post.getViralTier());
        stats.put("isViral", post.getIsViral());
        stats.put("expansionLevel", post.getExpansionLevel());
        stats.put("ageInHours", post.getAgeInHours());
        stats.put("engagementScore", String.format("%.2f", post.getEngagementScore()));
        stats.put("viralityScore", String.format("%.2f", post.getViralityScore()));
        stats.put("qualityScore", String.format("%.2f", post.getQualityScore()));

        return stats;
    }

    // ===== FEED FILTERING =====

    public static List<SocialPost> filterForHomeFeed(List<SocialPost> posts, User user, int limit) {
        if (posts == null || posts.isEmpty() || user == null) {
            return Collections.emptyList();
        }

        // BUG FIX #9 (same fix as rankPostsForUser): update viral status for all posts
        // before the sort comparator runs, so the comparator only reads stable values.
        posts.forEach(post -> {
            try {
                post.checkAndUpdateViralStatus();
            } catch (Exception e) {
                log.warn("Failed to update viral status for post {}", post.getId(), e);
            }
        });

        return posts.stream()
                .filter(post -> isSocialPostVisibleToUser(post, user))
                .filter(SocialPost::isEligibleForRecommendation)
                .sorted((p1, p2) -> {
                    double score1 = calculateFinalRecommendationScore(p1, user);
                    double score2 = calculateFinalRecommendationScore(p2, user);
                    return Double.compare(score2, score1);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ===== TIME FORMATTING - REUSES PostUtility =====

    public static String formatTimeAgo(SocialPost post) {
        if (post == null || post.getCreatedAt() == null) {
            return "Unknown";
        }
        return PostUtility.calculateTimeAgo(post.getCreatedAt());
    }

    // ===== CONTENT TRUNCATION - REUSES PostUtility =====

    public static String truncateContent(String content, int maxLength) {
        return PostUtility.truncateText(content, maxLength);
    }

    // ===== MEDIA VALIDATION =====

    public static void validateMediaFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        if (files.size() > Constant.MAX_SOCIAL_POST_TOTAL_MEDIA) {
            throw new ValidationException("Cannot upload more than " +
                    Constant.MAX_SOCIAL_POST_TOTAL_MEDIA + " media files");
        }

        int imageCount = 0;
        int videoCount = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new ValidationException("Empty file detected");
            }

            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.trim().isEmpty()) {
                throw new ValidationException("Invalid file name");
            }

            String extension = PostUtility.getFileExtension(fileName).toLowerCase();

            if (Constant.ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
                imageCount++;
                if (file.getSize() > Constant.MAX_SOCIAL_POST_IMAGE_SIZE) {
                    throw new ValidationException("Image size exceeds maximum allowed size of " +
                            (Constant.MAX_SOCIAL_POST_IMAGE_SIZE / 1024 / 1024) + "MB");
                }
            } else if (Constant.ALLOWED_VIDEO_EXTENSIONS.contains(extension)) {
                videoCount++;
                if (file.getSize() > Constant.MAX_SOCIAL_POST_VIDEO_SIZE) {
                    throw new ValidationException("Video size exceeds maximum allowed size of " +
                            (Constant.MAX_SOCIAL_POST_VIDEO_SIZE / 1024 / 1024) + "MB");
                }
            } else {
                throw new ValidationException("Unsupported file type: " + extension);
            }
        }

        if (imageCount > Constant.MAX_SOCIAL_POST_IMAGES) {
            throw new ValidationException("Cannot upload more than " +
                    Constant.MAX_SOCIAL_POST_IMAGES + " images");
        }

        if (videoCount > Constant.MAX_SOCIAL_POST_VIDEOS) {
            throw new ValidationException("Cannot upload more than " +
                    Constant.MAX_SOCIAL_POST_VIDEOS + " videos");
        }
    }

    public static void validateMediaFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("File cannot be empty");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new ValidationException("Invalid file name");
        }

        String extension = PostUtility.getFileExtension(fileName).toLowerCase();

        if (Constant.ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            PostUtility.validateMediaFile(file, Constant.MAX_SOCIAL_POST_IMAGE_SIZE,
                    Constant.MAX_SOCIAL_POST_VIDEO_SIZE);
        } else if (Constant.ALLOWED_VIDEO_EXTENSIONS.contains(extension)) {
            PostUtility.validateMediaFile(file, Constant.MAX_SOCIAL_POST_IMAGE_SIZE,
                    Constant.MAX_SOCIAL_POST_VIDEO_SIZE);
        } else {
            throw new ValidationException("Unsupported file type: " + extension +
                    ". Allowed types: Images (" + String.join(", ", Constant.ALLOWED_IMAGE_EXTENSIONS) +
                    "), Videos (" + String.join(", ", Constant.ALLOWED_VIDEO_EXTENSIONS) + ")");
        }
    }

    public static void validateMediaUrls(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return;
        }

        if (mediaUrls.size() > Constant.MAX_SOCIAL_POST_TOTAL_MEDIA) {
            throw new ValidationException("Cannot have more than " +
                    Constant.MAX_SOCIAL_POST_TOTAL_MEDIA + " media URLs");
        }

        for (String url : mediaUrls) {
            if (url == null || url.trim().isEmpty()) {
                throw new ValidationException("Media URL cannot be empty");
            }
            if (url.length() > 500) {
                throw new ValidationException("Media URL too long (max 500 characters)");
            }
        }
    }

    public static void validateHashtags(List<String> hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return;
        }

        if (hashtags.size() > Constant.MAX_HASHTAGS_PER_POST) {
            throw new ValidationException("Cannot have more than " +
                    Constant.MAX_HASHTAGS_PER_POST + " hashtags");
        }

        for (String hashtag : hashtags) {
            if (hashtag == null || hashtag.trim().isEmpty()) {
                throw new ValidationException("Hashtag cannot be empty");
            }
            String cleaned = hashtag.startsWith("#") ? hashtag.substring(1) : hashtag;
            if (cleaned.length() < 2 || cleaned.length() > 50) {
                throw new ValidationException("Hashtag must be between 2 and 50 characters");
            }
            if (!cleaned.matches("[a-zA-Z0-9_]+")) {
                throw new ValidationException("Hashtag can only contain letters, numbers, and underscores");
            }
        }
    }

    // ===== MEDIA TYPE - REUSES PostUtility =====

    public static String getMediaType(String url) {
        return PostUtility.getMediaType(url);
    }

    public static Map<String, Integer> getMediaStatistics(List<String> mediaUrls) {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("totalMedia", 0);
        stats.put("imageCount", 0);
        stats.put("videoCount", 0);

        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return stats;
        }

        int imageCount = 0;
        int videoCount = 0;

        for (String url : mediaUrls) {
            String type = getMediaType(url);
            if ("image".equals(type)) {
                imageCount++;
            } else if ("video".equals(type)) {
                videoCount++;
            }
        }

        stats.put("totalMedia", mediaUrls.size());
        stats.put("imageCount", imageCount);
        stats.put("videoCount", videoCount);

        return stats;
    }
}
