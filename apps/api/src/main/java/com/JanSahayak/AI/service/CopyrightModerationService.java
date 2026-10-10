package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CopyrightModerationService {

    private final PostRepo postRepository;
    private final SocialPostRepo socialPostRepository;
    private final UserRepo userRepository;
    private final EmailService emailService;
    
    // Configurable strike threshold
    private static final int MAX_STRIKES = 3;

    @Transactional
    public void executeTakedownOnPost(Long postId, Long adminId, String reason) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
                
        post.takedownForCopyright(reason, adminId);
        postRepository.save(post);
        
        applyCopyrightStrike(post.getUser(), post.getContent(), reason);
    }
    
    @Transactional
    public void executeTakedownOnSocialPost(Long socialPostId, Long adminId, String reason) {
        SocialPost post = socialPostRepository.findById(socialPostId)
                .orElseThrow(() -> new RuntimeException("SocialPost not found"));
                
        post.takedownForCopyright(reason, adminId);
        if (post.hasPoll()) {
            post.getPoll().takedownForCopyright(reason);
        }
        socialPostRepository.save(post);
        
        applyCopyrightStrike(post.getUser(), post.getContent(), reason);
    }

    private void applyCopyrightStrike(User user, String contentSnippet, String reason) {
        user.incrementCopyrightStrikes();
        
        if (user.getCopyrightStrikes() >= MAX_STRIKES) {
            log.info("User {} reached max copyright strikes ({}). Banning account.", user.getId(), user.getCopyrightStrikes());
            user.banUser("Repeated copyright infringement (" + user.getCopyrightStrikes() + " strikes)");
            userRepository.save(user);
            
            // Email Ban Notice
            emailService.sendCopyrightBanEmail(user, reason);
        } else {
            userRepository.save(user);
            
            // Email Strike Notice
            emailService.sendCopyrightTakedownEmail(user, contentSnippet, user.getCopyrightStrikes(), reason);
        }
    }
}
