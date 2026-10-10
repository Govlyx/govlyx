package com.JanSahayak.AI.service;

import com.JanSahayak.AI.exception.ServiceException;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Cloudinary-backed file storage service for JanSahayak.
 *
 * ── Drop-in replacement for DriveStorageService ──────────────────────────────
 * Exposes the EXACT same public method signatures:
 * uploadFile(file, userId, folderName) → String (Cloudinary secure URL)
 * deleteFile(urlOrPublicId) → void
 * deleteFiles(List) → void
 * extractFileId(url) → String (static, for adapter compat)
 *
 * DrivePostMediaAdapter and SocialPostMediaService require NO changes beyond
 * swapping the injected bean name.
 *
 * ── Folder structure on Cloudinary ───────────────────────────────────────────
 * jansahayak/posts/ ← FOLDER_POSTS
 * jansahayak/social-posts/ ← FOLDER_SOCIAL_POSTS
 *
 * ── Returned URLs
 * ─────────────────────────────────────────────────────────────
 * https://res.cloudinary.com/<cloud>/image/upload/v.../jansahayak/posts/...jpg
 * These are stored in the DB exactly as-is (same pattern as Drive URLs were).
 *
 * ── Required env vars
 * ─────────────────────────────────────────────────────────
 * CLOUDINARY_CLOUD_NAME
 * CLOUDINARY_API_KEY
 * CLOUDINARY_API_SECRET
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryStorageService {

    // ── Folder name constants — mirror DriveStorageService so callers need no
    // changes
    public static final String FOLDER_POSTS = "posts";
    public static final String FOLDER_SOCIAL_POSTS = "social-posts";

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Cloudinary cloudinary;

    @Value("${cloudinary.folder.posts:jansahayak/posts}")
    private String folderPosts;

    @Value("${cloudinary.folder.social-posts:jansahayak/social-posts}")
    private String folderSocialPosts;

    // =========================================================================
    // INITIALISATION
    // =========================================================================

    @PostConstruct
    public void init() {
        if (cloudinary == null) {
            log.error("=== CLOUDINARY IS NOT CONFIGURED! File uploads will fail. Please set CLOUDINARY_CLOUD_NAME env var. ===");
            return;
        }
        String cloudName = (String) cloudinary.config.cloudName;
        if (cloudName == null || cloudName.isBlank()) {
            log.error("=== CLOUDINARY_CLOUD_NAME is not set! File uploads will fail. ===");
        } else {
            log.info("Cloudinary storage initialized. Cloud: {} | Posts folder: {} | Social folder: {}",
                    cloudName, folderPosts, folderSocialPosts);
        }
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Upload a file to Cloudinary.
     *
     * @param file       multipart file from the HTTP request
     * @param userId     used only for logging
     * @param folderName one of {@link #FOLDER_POSTS} or
     *                   {@link #FOLDER_SOCIAL_POSTS}
     * @return Cloudinary secure URL — store this in post.imageName /
     *         socialPost.mediaUrls
     */
    public String uploadFile(MultipartFile file, Long userId, String folderName) {
        if (cloudinary == null) {
            throw new ServiceException("Cloudinary is not configured. Please set the CLOUDINARY_CLOUD_NAME env var on Render or in your environment.");
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException("Cannot upload an empty or null file.");
        }
        try {
            String targetFolder = resolveFolder(folderName);
            boolean isVideo = isVideoFile(file);
            String resourceType = isVideo ? "video" : "image";

            Map<?, ?> params = ObjectUtils.asMap(
                    "folder", targetFolder,
                    "resource_type", resourceType,
                    "use_filename", true,
                    "unique_filename", true,
                    "overwrite", false);

            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), params);

            String url = (String) result.get("secure_url");
            if (url == null || url.isBlank()) {
                throw new ServiceException("Cloudinary returned no URL for the uploaded file.");
            }

            log.info("Uploaded file to Cloudinary: folder={} resourceType={} url={} user={}",
                    targetFolder, resourceType, url, userId);
            return url;

        } catch (IOException e) {
            log.error("Cloudinary upload failed: user={} folder={}", userId, folderName, e);
            throw new ServiceException(
                    "Failed to upload file to Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a single file from Cloudinary.
     * Accepts a full Cloudinary secure URL or a raw public ID.
     * Silently ignores null / blank input (mirrors Drive behaviour).
     */
    public void deleteFile(String urlOrPublicId) {
        if (urlOrPublicId == null || urlOrPublicId.isBlank())
            return;
        if (cloudinary == null) {
            log.warn("Cannot delete file {} - Cloudinary is not configured.", urlOrPublicId);
            return;
        }
        try {
            String publicId = extractPublicId(urlOrPublicId);

            // Try image first; if Cloudinary returns "not found", retry as video
            Map<?, ?> result = cloudinary.uploader()
                    .destroy(publicId, ObjectUtils.asMap("resource_type", "image"));

            if ("not found".equals(result.get("result"))) {
                cloudinary.uploader()
                        .destroy(publicId, ObjectUtils.asMap("resource_type", "video"));
                log.info("Deleted Cloudinary video: publicId={}", publicId);
            } else {
                log.info("Deleted Cloudinary image: publicId={}", publicId);
            }

        } catch (IOException e) {
            log.warn("Failed to delete Cloudinary file: {} — {}", urlOrPublicId, e.getMessage());
        }
    }

    /**
     * Delete multiple files. Individual failures are logged and skipped.
     */
    public void deleteFiles(List<String> urlsOrIds) {
        if (urlsOrIds == null || urlsOrIds.isEmpty())
            return;
        urlsOrIds.forEach(u -> {
            try {
                deleteFile(u);
            } catch (Exception e) {
                log.warn("Skipping delete failure for: {} — {}", u, e.getMessage());
            }
        });
    }

    // =========================================================================
    // STATIC UTILITIES
    // =========================================================================

    /**
     * Extract the Cloudinary public ID from a secure URL.
     *
     * Examples:
     * Input :
     * https://res.cloudinary.com/demo/image/upload/v1234/jansahayak/posts/photo.jpg
     * Output: jansahayak/posts/photo
     *
     * Input :
     * https://res.cloudinary.com/demo/video/upload/jansahayak/social-posts/vid.mp4
     * Output: jansahayak/social-posts/vid
     *
     * Input : jansahayak/posts/photo (already a public ID)
     * Output: jansahayak/posts/photo
     *
     * This method is static and named extractFileId() so that any code that
     * previously called DriveStorageService.extractFileId() continues to compile.
     */
    public static String extractFileId(String url) {
        return extractPublicId(url);
    }

    public static String extractPublicId(String url) {
        if (url == null)
            return null;
        if (!url.contains("res.cloudinary.com"))
            return url.trim();

        // Find "/upload/" boundary
        int uploadIdx = url.indexOf("/upload/");
        if (uploadIdx == -1)
            return url.trim();

        String afterUpload = url.substring(uploadIdx + "/upload/".length());

        // Strip optional version segment: v1234567890/
        if (afterUpload.startsWith("v")) {
            int firstSlash = afterUpload.indexOf('/');
            if (firstSlash > 1) {
                String versionPart = afterUpload.substring(1, firstSlash);
                if (versionPart.matches("\\d+")) {
                    afterUpload = afterUpload.substring(firstSlash + 1);
                }
            }
        }

        // Strip file extension
        int dotIdx = afterUpload.lastIndexOf('.');
        return (dotIdx > 0) ? afterUpload.substring(0, dotIdx) : afterUpload;
    }

    /**
     * Convert a Cloudinary public ID to a direct view URL.
     * Equivalent to DriveStorageService.toViewUrl() — kept for any callers that
     * used it.
     */
    public static String toViewUrl(String publicId) {
        // This cannot be computed without knowing cloud name + resource type;
        // in practice callers should use the URL returned directly by uploadFile().
        // Provided as a no-op stub for compilation compatibility.
        return publicId;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private String resolveFolder(String folderName) {
        if (FOLDER_SOCIAL_POSTS.equals(folderName))
            return folderSocialPosts;
        return folderPosts;
    }

    private boolean isVideoFile(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && ct.startsWith("video/"))
            return true;
        String name = file.getOriginalFilename();
        if (name == null || !name.contains("."))
            return false;
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "mp4", "mov", "avi", "webm", "mkv", "flv", "wmv" -> true;
            default -> false;
        };
    }
}
