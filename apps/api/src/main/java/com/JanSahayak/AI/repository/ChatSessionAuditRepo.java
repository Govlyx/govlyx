package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.ChatSessionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatSessionAuditRepo extends JpaRepository<ChatSessionAudit, Long> {
    Optional<ChatSessionAudit> findBySessionId(String sessionId);

    @Query("SELECT COUNT(c) FROM ChatSessionAudit c WHERE (c.user1Id = :userId OR c.user2Id = :userId) AND c.startedAt >= :since")
    int countSessionsForUserSince(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("since") java.util.Date since);

    @Query("SELECT COUNT(c) FROM ChatSessionAudit c WHERE ((c.user1Id = :userId AND c.user1UsedMedia = true) OR (c.user2Id = :userId AND c.user2UsedMedia = true)) AND c.startedAt >= :since")
    int countMediaSessionsForUserSince(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("since") java.util.Date since);
}
