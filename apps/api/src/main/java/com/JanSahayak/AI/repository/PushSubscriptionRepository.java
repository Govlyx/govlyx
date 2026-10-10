package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.PushSubscription;
import com.JanSahayak.AI.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    
    List<PushSubscription> findByUser(User user);
    
    Optional<PushSubscription> findByEndpoint(String endpoint);
    
    void deleteByEndpoint(String endpoint);
}
