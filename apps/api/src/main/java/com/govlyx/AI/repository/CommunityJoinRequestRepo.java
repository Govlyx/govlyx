package com.Govlyx.AI.repository;

import com.Govlyx.AI.model.CommunityJoinRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityJoinRequestRepo extends JpaRepository<CommunityJoinRequest, Long> {

    Optional<CommunityJoinRequest> findByCommunityIdAndUserId(Long communityId, Long userId);

    boolean existsByCommunityIdAndUserIdAndStatus(
            Long communityId, Long userId, CommunityJoinRequest.RequestStatus status);

    @Modifying
    @Query("DELETE FROM CommunityJoinRequest cjr WHERE cjr.community.id = :communityId")
    void deleteByCommunityId(@Param("communityId") Long communityId);

    // ── Cursor-based pending requests (PaginationUtils.createPageable(limit+1)) ──

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT jr FROM CommunityJoinRequest jr
            WHERE jr.community.id = :communityId
              AND jr.status       = com.Govlyx.AI.model.CommunityJoinRequest.RequestStatus.PENDING
              AND (:cursor IS NULL OR jr.id < :cursor)
            ORDER BY jr.id DESC
            """)
    List<CommunityJoinRequest> findPendingRequestsCursor(
            @Param("communityId") Long communityId,
            @Param("cursor")      Long cursor,
            Pageable pageable);
}
