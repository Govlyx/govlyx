package com.govlyx.AI.service;

import com.govlyx.AI.model.BannedActor;
import com.govlyx.AI.repository.BannedActorRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActorBanService {

    private final BannedActorRepo bannedActorRepo;
    private final Map<String, Long> banCache = new ConcurrentHashMap<>();

    public boolean isActorBanned(String actorToken) {
        if (actorToken == null || actorToken.isBlank()) {
            return false;
        }

        Long cachedExpiry = banCache.get(actorToken);
        long now = System.currentTimeMillis();
        if (cachedExpiry != null) {
            if (cachedExpiry == -1L || cachedExpiry > now) {
                return true;
            } else {
                banCache.remove(actorToken);
            }
        }

        return bannedActorRepo.findByActorToken(actorToken)
                .map(ban -> {
                    if (ban.getExpiresAt() == null) {
                        banCache.put(actorToken, -1L);
                        return true;
                    } else if (ban.getExpiresAt().after(new Date())) {
                        banCache.put(actorToken, ban.getExpiresAt().getTime());
                        return true;
                    } else {
                        return false;
                    }
                })
                .orElse(false);
    }

    @Transactional
    public void banActor(String actorToken, String reason, Long adminId, Date expiresAt) {
        if (actorToken == null || actorToken.isBlank()) return;

        BannedActor ban = bannedActorRepo.findByActorToken(actorToken)
                .orElseGet(() -> BannedActor.builder().actorToken(actorToken).build());

        ban.setReason(reason);
        ban.setBannedByAdminId(adminId);
        ban.setExpiresAt(expiresAt);
        ban.setBannedAt(new Date());

        bannedActorRepo.save(ban);
        banCache.put(actorToken, expiresAt != null ? expiresAt.getTime() : -1L);
        log.warn("[ACTOR_BAN] ActorToken {} banned. Reason: {}", actorToken, reason);
    }

    @Transactional
    public void unbanActor(String actorToken) {
        if (actorToken == null || actorToken.isBlank()) return;

        bannedActorRepo.findByActorToken(actorToken).ifPresent(bannedActorRepo::delete);
        banCache.remove(actorToken);
        log.info("[ACTOR_BAN] ActorToken {} unbanned.", actorToken);
    }
}
