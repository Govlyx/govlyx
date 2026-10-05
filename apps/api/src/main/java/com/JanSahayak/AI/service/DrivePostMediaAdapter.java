package com.JanSahayak.AI.service;

import com.JanSahayak.AI.payload.PostUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Adapter that gives PostService a Cloudinary-backed upload/delete API
 * while keeping PostUtility (static utility class) completely unchanged.
 *
 * PostService injects this bean and calls:
 *   drivePostMedia.upload(file, userId)   → returns Cloudinary secure URL
 *   drivePostMedia.delete(url)            → deletes from Cloudinary by URL or public ID
 *   drivePostMedia.getPath(url)           → returns URL as-is (already a public CDN URL)
 *
 * Files land in Cloudinary under:
 *   jansahayak/posts/
 *
 * NOTE: The bean and field name "drivePostMedia" in PostService is intentionally
 * kept unchanged — only the underlying CloudinaryStorageService is swapped in.
 * This means PostService.java requires ZERO changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DrivePostMediaAdapter {

    private final CloudinaryStorageService cloudinaryStorageService;

    /**
     * Upload a post media file to Cloudinary.
     *
     * @param file   multipart file
     * @param userId for logging
     * @return Cloudinary secure URL — store this in post.imageName
     */
    public String upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) return null;
        // PostUtility.validateMediaFile runs size + type checks before upload
        PostUtility.validateMediaFile(file);
        return cloudinaryStorageService.uploadFile(file, userId, CloudinaryStorageService.FOLDER_POSTS);
    }

    /**
     * Delete a post media file from Cloudinary.
     * Accepts Cloudinary secure URL or raw public ID.
     * No-op on null/blank input.
     */
    public void delete(String urlOrId) {
        cloudinaryStorageService.deleteFile(urlOrId);
    }

    /**
     * Returns the URL as-is (Cloudinary URL is already the public CDN path).
     * Replaces PostUtility.getMediaFilePath() in PostService.
     */
    public String getPath(String urlOrId) {
        if (urlOrId == null || urlOrId.isBlank()) return null;
        return urlOrId;
    }
}
