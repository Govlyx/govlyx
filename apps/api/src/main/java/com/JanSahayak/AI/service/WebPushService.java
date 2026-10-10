package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.PushSubscription;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebPushService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    
    @Value("${vapid.public.key:BC1qGk_bS8P_N5K5y0N49d1M_145O4bN7Qe-1zQ569yv-F8-s1D-5VvJtE2c_-Fv3QYFhGgQv03rJv90c1mQO3I=}")
    private String publicKey;

    @Value("${vapid.private.key:A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q7R8S9T0U1V=}")
    private String privateKey;

    @Value("${vapid.subject:mailto:admin@govlyx.com}")
    private String subject;

    private PushService pushService;

    @PostConstruct
    public void init() throws GeneralSecurityException {
        Security.addProvider(new BouncyCastleProvider());
        // For testing purposes, if you generate actual keys, inject them.
        try {
            pushService = new PushService(publicKey, privateKey, subject);
        } catch (Exception e) {
            log.warn("Failed to initialize PushService. Web Push will not work. Please set valid vapid.public.key and vapid.private.key in application.properties.", e);
        }
    }

    public String getPublicKey() {
        return publicKey;
    }

    @Async
    public void sendPushNotification(User user, String payload) {
        if (pushService == null) {
            return;
        }
        
        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUser(user);
        for (PushSubscription sub : subscriptions) {
            try {
                Subscription subscription = new Subscription(sub.getEndpoint(), new Subscription.Keys(sub.getP256dh(), sub.getAuth()));
                Notification notification = new Notification(subscription, payload);
                pushService.send(notification);
            } catch (Exception e) {
                log.warn("Failed to send push notification to endpoint {}: {}", sub.getEndpoint(), e.getMessage());
                // Remove invalid subscriptions
                if (e.getMessage().contains("410") || e.getMessage().contains("404")) {
                    pushSubscriptionRepository.delete(sub);
                }
            }
        }
    }
}
