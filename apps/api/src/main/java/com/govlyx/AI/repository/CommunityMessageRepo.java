package com.govlyx.AI.repository;

import com.govlyx.AI.model.CommunityMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityMessageRepo extends JpaRepository<CommunityMessage, Long> {

    Optional<CommunityMessage> findByIdempotencyKey(String idempotencyKey);

    /**
     * Fetch initial history page. Uses JOIN FETCH to eagerly load sender and role, 
     * and LEFT JOIN FETCH for sharedPost to avoid any LazyInitializationException.
     */
    @Query("SELECT cm FROM CommunityMessage cm " +
           "JOIN FETCH cm.sender s " +
           "JOIN FETCH s.role r " +
           "LEFT JOIN FETCH cm.sharedPost p " +
           "WHERE cm.communityId = :communityId " +
           "AND cm.isFlagged = false " +
           "AND cm.isQuarantined = false " +
           "ORDER BY cm.createdAt DESC, cm.id DESC")
    List<CommunityMessage> findRecentMessages(@Param("communityId") Long communityId, Pageable pageable);

    /**
     * Cursor-based history query. Returns messages older than a given cursor ID.
     * Eagerly loads relationships to prevent lazy loading bugs.
     */
    @Query("SELECT cm FROM CommunityMessage cm " +
           "JOIN FETCH cm.sender s " +
           "JOIN FETCH s.role r " +
           "LEFT JOIN FETCH cm.sharedPost p " +
           "WHERE cm.communityId = :communityId " +
           "AND cm.id < :cursorId " +
           "AND cm.isFlagged = false " +
           "AND cm.isQuarantined = false " +
           "ORDER BY cm.createdAt DESC, cm.id DESC")
    List<CommunityMessage> findRecentMessagesBeforeCursor(
            @Param("communityId") Long communityId,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /**
     * Fetch pinned messages. Eagerly loads sender to avoid lazy loading bugs.
     */
    @Query("SELECT cm FROM CommunityMessage cm " +
           "JOIN FETCH cm.sender s " +
           "JOIN FETCH s.role r " +
           "LEFT JOIN FETCH cm.sharedPost p " +
           "WHERE cm.communityId = :communityId " +
           "AND cm.isPinned = true " +
           "AND cm.isFlagged = false " +
           "AND cm.isQuarantined = false " +
           "ORDER BY cm.createdAt DESC")
    List<CommunityMessage> findPinnedMessages(@Param("communityId") Long communityId);

    /**
     * Unpins all messages in a community. Used to enforce single-pinned message limit.
     */
    @Modifying
    @Query("UPDATE CommunityMessage cm SET cm.isPinned = false WHERE cm.communityId = :communityId AND cm.isPinned = true")
    void unpinAllMessagesInCommunity(@Param("communityId") Long communityId);

    /**
     * Delete expired messages.
     */
    @Modifying
    @Query("DELETE FROM CommunityMessage cm WHERE cm.expiresAt <= :now")
    int deleteExpiredMessages(@Param("now") Instant now);

    /**
     * Delete all messages in a community.
     */
    @Modifying
    @Query("DELETE FROM CommunityMessage cm WHERE cm.communityId = :communityId")
    void deleteByCommunityId(@Param("communityId") Long communityId);
}
