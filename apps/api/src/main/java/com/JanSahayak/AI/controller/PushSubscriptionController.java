package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.PushSubscriptionDto;
import com.JanSahayak.AI.model.PushSubscription;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PushSubscriptionRepository;
import com.JanSahayak.AI.service.WebPushService;
import com.JanSahayak.AI.payload.PostUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import com.JanSahayak.AI.security.CurrentUser;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications/push")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final WebPushService webPushService;

    @GetMapping("/public-key")
    public ResponseEntity<String> getPublicKey() {
        return ResponseEntity.ok(webPushService.getPublicKey());
    }

    @PostMapping("/subscribe")
    @Transactional
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionDto dto, @CurrentUser User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<PushSubscription> existing = pushSubscriptionRepository.findByEndpoint(dto.getEndpoint());
        if (existing.isPresent()) {
            PushSubscription sub = existing.get();
            if (!sub.getUser().getId().equals(user.getId())) {
                sub.setUser(user);
                pushSubscriptionRepository.save(sub);
            }
            return ResponseEntity.ok().build();
        }

        PushSubscription subscription = PushSubscription.builder()
                .user(user)
                .endpoint(dto.getEndpoint())
                .p256dh(dto.getP256dh())
                .auth(dto.getAuth())
                .build();
                
        pushSubscriptionRepository.save(subscription);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/unsubscribe")
    @Transactional
    public ResponseEntity<Void> unsubscribe(@RequestBody PushSubscriptionDto dto, @CurrentUser User user) {
        pushSubscriptionRepository.deleteByEndpoint(dto.getEndpoint());
        return ResponseEntity.ok().build();
    }
}
