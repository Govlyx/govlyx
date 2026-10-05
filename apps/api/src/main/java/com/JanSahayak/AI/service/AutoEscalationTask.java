package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.repository.PostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoEscalationTask {

    private final PostRepo postRepo;

    /**
     * Runs hourly to escalate posts based on user engagement.
     * Escalation Waterfall: AREA -> NEARBY -> DISTRICT -> STATE -> COUNTRY
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void escalatePosts() {
        log.info("[AutoEscalationTask] Starting evaluation for post escalation.");
        
        List<Post> eligiblePosts = postRepo.findPostsEligibleForEscalation();
        
        if (eligiblePosts == null || eligiblePosts.isEmpty()) {
            log.info("[AutoEscalationTask] No posts eligible for escalation.");
            return;
        }

        int escalatedCount = 0;
        
        for (Post post : eligiblePosts) {
            int score = (post.getLikeCount() * 2) + (post.getCommentCount() * 3) + (post.getShareCount() * 4);
            boolean changed = false;

            if (post.getBroadcastScope() == BroadcastScope.AREA && score > 20) {
                post.setBroadcastScope(BroadcastScope.NEARBY);
                changed = true;
                log.info("[AutoEscalationTask] Escalated Post ID: {} to NEARBY (Score: {})", post.getId(), score);
            } else if (post.getBroadcastScope() == BroadcastScope.NEARBY && score > 50) {
                post.setBroadcastScope(BroadcastScope.DISTRICT);
                changed = true;
                log.info("[AutoEscalationTask] Escalated Post ID: {} to DISTRICT (Score: {})", post.getId(), score);
            } else if (post.getBroadcastScope() == BroadcastScope.DISTRICT && score > 200) {
                post.setBroadcastScope(BroadcastScope.STATE);
                changed = true;
                log.info("[AutoEscalationTask] Escalated Post ID: {} to STATE (Score: {})", post.getId(), score);
            } else if (post.getBroadcastScope() == BroadcastScope.STATE && score > 1000) {
                post.setBroadcastScope(BroadcastScope.COUNTRY);
                changed = true;
                log.info("[AutoEscalationTask] Escalated Post ID: {} to COUNTRY (Score: {})", post.getId(), score);
            }

            if (changed) {
                post.setAutoEscalated(true);
                postRepo.save(post);
                escalatedCount++;
            }
        }
        
        log.info("[AutoEscalationTask] Completed. Escalated {} posts.", escalatedCount);
    }
}
