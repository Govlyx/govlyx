package com.JanSahayak.AI.service;

import com.JanSahayak.AI.exception.MediaValidationException;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.payload.SocialPostUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Media service for SocialPost — delegates all storage to Cloudinary.
 * No local disk or Google Drive required; works identically on local dev and Render.
 *
 * Files are uploaded to Cloudinary under:
 *   jansahayak/social-posts/
 *
 * Returned URLs are Cloudinary secure URLs:
 *   https://res.cloudinary.com/<cloud>/image/upload/v.../jansahayak/social-posts/...jpg
 *
 * These URLs are stored as-is in the DB column socialPost.mediaUrls (JSON array of strings).
 *
 * NOTE: SocialPostUtility.validateMediaFiles / validateMediaFile still run
 * for size + type validation — only the storage backend has changed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialPostMediaService {

    private final CloudinaryStorageService cloudinaryStorageService;

    // =========================================================================
    // UPLOAD
    // =========================================================================

    /**
     * Upload multiple media files for a social post.
     * Validates all files first, then uploads them one by one.
     *
     * @return List of Cloudinary secure URLs, stored in socialPost.mediaUrls
     */
    public List<String> uploadMediaFiles(List<MultipartFile> files, Long userId) {
        try {
            SocialPostUtility.validateMediaFiles(files);

            List<String> uploadedUrls = new ArrayList<>();
            for (MultipartFile file : files) {
                String url = uploadSingleMediaFile(file, userId);
                uploadedUrls.add(url);
            }

            log.info("Uploaded {} media files to Cloudinary for user: {}", uploadedUrls.size(), userId);
            return uploadedUrls;

        } catch (MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload media files for user: {}", userId, e);
            throw new ServiceException("Failed to upload media files: " + e.getMessage(), e);
        }
    }

    /**
     * Upload a single media file.
     * Validates the file, then delegates to CloudinaryStorageService.
     *
     * @return Cloudinary secure URL
     */
    public String uploadSingleMediaFile(MultipartFile file, Long userId) {
        try {
            SocialPostUtility.validateMediaFile(file);

            String url = cloudinaryStorageService.uploadFile(
                    file, userId, CloudinaryStorageService.FOLDER_SOCIAL_POSTS);

            log.info("Social post media uploaded to Cloudinary: user={} url={}", userId, url);
            return url;

        } catch (MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload social post media for user: {}", userId, e);
            throw new MediaValidationException("Failed to upload media file: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    /** Delete a single media file from Cloudinary by URL or public ID. */
    public void deleteMediaFile(String fileUrl) {
        cloudinaryStorageService.deleteFile(fileUrl);
    }

    /** Delete multiple media files. Individual failures are logged and skipped. */
    public void deleteMediaFiles(List<String> fileUrls) {
        cloudinaryStorageService.deleteFiles(fileUrls);
    }

    // =========================================================================
    // INFO / VALIDATION
    // =========================================================================

    /**
     * Returns basic info about a Cloudinary file from its URL.
     * Size is not cached locally; add a Cloudinary Admin API call if needed.
     */
    public MediaFileInfo getMediaFileInfo(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return null;

        String publicId = CloudinaryStorageService.extractPublicId(fileUrl);
        String type     = guessTypeFromUrl(fileUrl);

        return MediaFileInfo.builder()
                .url(fileUrl)
                .fileName(publicId)
                .size(null)       // not cached; call Cloudinary Admin API if needed
                .type(type)
                .exists(true)     // optimistic: URL was stored on successful upload
                .build();
    }

    /**
     * Validates that media URLs look like real Cloudinary URLs.
     * Optimistic: does not hit the Cloudinary API per URL (avoids quota usage).
     */
    public boolean validateMediaUrlsExist(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) return true;
        for (String url : mediaUrls) {
            if (url == null || url.isBlank()) return false;
            if (!url.contains("res.cloudinary.com")) return false;
        }
        return true;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String guessTypeFromUrl(String url) {
        if (url == null) return "unknown";
        String lower = url.toLowerCase();
        if (lower.contains("/video/upload/") ||
                lower.contains(".mp4") || lower.contains(".mov") ||
                lower.contains(".avi") || lower.contains(".webm")) return "video";
        return "image";
    }

    // =========================================================================
    // INNER CLASS — kept exactly as before so no DTOs need to change
    // =========================================================================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MediaFileInfo {
        private String  url;
        private String  fileName;
        private Long    size;
        private String  type;       // "image" or "video"
        private Boolean exists;

        public String getFormattedSize() {
            if (size == null) return "Unknown";
            if (size < 1024)            return size + " B";
            if (size < 1024 * 1024)     return String.format("%.2f KB", size / 1024.0);
            return                             String.format("%.2f MB", size / 1024.0 / 1024.0);
        }

        public boolean isImage() { return "image".equals(type); }
        public boolean isVideo() { return "video".equals(type); }

        public Boolean getExists() { return exists; }
    }
}
