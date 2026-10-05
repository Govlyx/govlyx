package com.Govlyx.AI.repository;

import com.Govlyx.AI.model.BannedActor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BannedActorRepo extends JpaRepository<BannedActor, Long> {
    boolean existsByActorToken(String actorToken);
    Optional<BannedActor> findByActorToken(String actorToken);
}
