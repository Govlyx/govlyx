package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.FeedbackStatus;
import com.JanSahayak.AI.model.Feedback;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.FeedbackRequest;
import com.JanSahayak.AI.repository.FeedbackRepository;
import com.JanSahayak.AI.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepo userRepository;

    public Feedback submitFeedback(FeedbackRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = null;

        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            String email = auth.getName();
            user = userRepository.findByEmail(email).orElse(null);
        }

        Feedback feedback = Feedback.builder()
                .user(user)
                .rating(request.getRating())
                .category(request.getCategory())
                .message(request.getMessage())
                .appVersion(request.getAppVersion())
                .deviceInfo(request.getDeviceInfo())
                .status(FeedbackStatus.UNREAD)
                .build();

        return feedbackRepository.save(feedback);
    }
}
