package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.ActorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActorProfileRepo extends JpaRepository<ActorProfile, String> {
    Optional<ActorProfile> findByActorToken(String actorToken);
    Optional<ActorProfile> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByActorToken(String actorToken);
}
