package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaRetentionCleanupTask {

    private final PostRepo postRepository;
    private final SocialPostRepo socialPostRepository;
    private final DrivePostMediaAdapter drivePostMediaAdapter;
    private final SocialPostMediaService socialPostMediaService;

    // Runs once a day at 2 AM
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredMedia() {
        log.info("Running Media Retention Cleanup Task for copyright takedowns...");
        Date now = new Date();

        // 1. Clean up expired media in Posts
        List<Post> expiredPosts = postRepository.findAll().stream()
                .filter(p -> p.getStatus() == PostStatus.TAKEN_DOWN 
                          && p.getRetentionExpiresAt() != null 
                          && p.getRetentionExpiresAt().before(now)
                          && p.hasImage())
                .collect(Collectors.toList());

        for (Post post : expiredPosts) {
            try {
                log.info("Deleting expired media for Post ID {}", post.getId());
                // Delete from Cloudinary
                List<String> urls = post.getMediaUrlsList();
                for (String url : urls) {
                    drivePostMediaAdapter.delete(url);
                }
                // Clear media from DB
                post.setMediaUrlsList(null);
                post.setImageName(null);
                postRepository.save(post);
            } catch (Exception e) {
                log.error("Failed to cleanup media for Post ID {}: {}", post.getId(), e.getMessage());
            }
        }

        // 2. Clean up expired media in SocialPosts
        List<SocialPost> expiredSocialPosts = socialPostRepository.findAll().stream()
                .filter(p -> p.getStatus() == PostStatus.TAKEN_DOWN 
                          && p.getRetentionExpiresAt() != null 
                          && p.getRetentionExpiresAt().before(now)
                          && p.hasMedia())
                .collect(Collectors.toList());

        for (SocialPost sp : expiredSocialPosts) {
            try {
                log.info("Deleting expired media for SocialPost ID {}", sp.getId());
                // Delete from Cloudinary
                List<String> urls = sp.getMediaUrlsList();
                socialPostMediaService.deleteMediaFiles(urls);
                
                // Clear media from DB
                sp.setMediaUrlsList(null);
                socialPostRepository.save(sp);
            } catch (Exception e) {
                log.error("Failed to cleanup media for SocialPost ID {}: {}", sp.getId(), e.getMessage());
            }
        }

        log.info("Media Retention Cleanup Task completed. Cleaned {} Posts, {} SocialPosts.", 
                expiredPosts.size(), expiredSocialPosts.size());
    }
}
