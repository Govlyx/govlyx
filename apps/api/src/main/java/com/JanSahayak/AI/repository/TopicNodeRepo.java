package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.TopicNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

@Repository
public interface TopicNodeRepo extends JpaRepository<TopicNode, Long> {

    Optional<TopicNode> findByName(String name);

    @Modifying
    @Query("UPDATE TopicNode t SET t.velocityScore = t.velocityScore + 1, t.lastSeenAt = :now WHERE t.name = :name")
    void incrementVelocityAndLastSeen(@Param("name") String name, @Param("now") Date now);

    @Modifying
    @Query("DELETE FROM TopicNode t WHERE t.lastSeenAt < :thresholdDate AND t.velocityScore < :minVelocity")
    int pruneDeadTopics(@Param("thresholdDate") Date thresholdDate, @Param("minVelocity") int minVelocity);
}
