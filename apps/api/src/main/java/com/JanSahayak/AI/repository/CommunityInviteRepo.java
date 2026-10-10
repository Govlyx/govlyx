package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.CommunityInvite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityInviteRepo extends JpaRepository<CommunityInvite, Long> {

    // ── Lookup by token ───────────────────────────────────────────────────────

    @EntityGraph(attributePaths = {"invitee", "inviter", "community"})
    Optional<CommunityInvite> findByToken(String token);

    // ── Check for duplicate pending invite to same user ───────────────────────

    @Query("""
        SELECT COUNT(i) > 0 FROM CommunityInvite i
        WHERE i.community.id = :communityId
          AND i.invitee.id   = :inviteeId
          AND i.status       = 'PENDING'
    """)
    boolean existsPendingInviteForUser(
            @Param("communityId") Long communityId,
            @Param("inviteeId")   Long inviteeId);

    // ── Delete by community ID ────────────────────────────────────────────────

    @Modifying
    @Query("DELETE FROM CommunityInvite ci WHERE ci.community.id = :communityId")
    void deleteByCommunityId(@Param("communityId") Long communityId);

    // ── Paginated list of PENDING invites for admin panel ────────────────────
    // cursor-based: id > cursor (newest first using id DESC)

    @EntityGraph(attributePaths = {"invitee", "inviter", "community"})
    @Query("""
        SELECT i FROM CommunityInvite i
        WHERE i.community.id = :communityId
          AND i.status       = 'PENDING'
          AND (:cursor IS NULL OR i.id < :cursor)
        ORDER BY i.id DESC
    """)
    List<CommunityInvite> findPendingByCommunityIdCursor(
            @Param("communityId") Long communityId,
            @Param("cursor")      Long cursor,
            Pageable pageable);

    // ── Bulk expire overdue invites (run by scheduler) ────────────────────────

    @Modifying
    @Query("""
        UPDATE CommunityInvite i
        SET i.status = 'EXPIRED'
        WHERE i.status    = 'PENDING'
          AND i.expiresAt < :now
    """)
    int expireOverdueInvites(@Param("now") Date now);
}
