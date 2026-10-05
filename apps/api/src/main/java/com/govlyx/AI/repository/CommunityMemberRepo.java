package com.govlyx.AI.repository;

import com.govlyx.AI.model.CommunityMember;
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
public interface CommunityMemberRepo extends JpaRepository<CommunityMember, Long> {

    Optional<CommunityMember> findByCommunityIdAndUserId(Long communityId, Long userId);

    boolean existsByCommunityIdAndUserIdAndIsActiveTrue(Long communityId, Long userId);

    boolean existsByCommunityIdAndUserIdAndIsActiveTrueAndIsBannedFalse(Long communityId, Long userId);

    @Query("SELECT cm.user.id FROM CommunityMember cm WHERE cm.community.id = :communityId AND cm.isActive = true")
    org.springframework.data.domain.Slice<Long> findActiveMemberIdsByCommunityId(@Param("communityId") Long communityId, Pageable pageable);

    /**
     * Batch-loads membership records for a single user across multiple communities.
     * Used by SocialPostService.convertToDtoBatch() to set isMember on community
     * post DTOs without issuing a separate query per post.
     *
     * Only returns active, non-banned memberships.
     */
    @EntityGraph(attributePaths = {"community"})
    @Query("""
            SELECT cm FROM CommunityMember cm
            WHERE cm.user.id       = :userId
              AND cm.community.id IN :communityIds
              AND cm.isActive      = true
              AND cm.isBanned      = false
              AND cm.community.status <> com.govlyx.AI.model.Community.CommunityStatus.PERMANENTLY_DELETED
            """)
    List<CommunityMember> findActiveByUserIdAndCommunityIdIn(
            @Param("userId")       Long userId,
            @Param("communityIds") List<Long> communityIds);



    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT cm FROM CommunityMember cm
            WHERE cm.community.id = :communityId
              AND cm.isActive = true
              AND cm.isBanned = false
              AND (:cursor IS NULL OR cm.id > :cursor)
            ORDER BY cm.id ASC
            """)
    List<CommunityMember> findActiveMembersCursor(
            @Param("communityId") Long communityId,
            @Param("cursor")      Long cursor,
            Pageable pageable);

    // â”€â”€ User's communities (cursor-based) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @EntityGraph(attributePaths = {"community"})
    @Query("""
            SELECT cm FROM CommunityMember cm
            WHERE cm.user.id   = :userId
              AND cm.isActive  = true
              AND cm.isBanned  = false
              AND cm.community.status <> com.govlyx.AI.model.Community.CommunityStatus.PERMANENTLY_DELETED
              AND (:cursor IS NULL OR cm.id < :cursor)
            ORDER BY cm.id DESC
            """)
    List<CommunityMember> findUserCommunitiesCursor(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // â”€â”€ Authorization checks â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Query("""
            SELECT CASE WHEN COUNT(cm) > 0 THEN true ELSE false END
            FROM CommunityMember cm
            WHERE cm.community.id = :communityId
              AND cm.user.id      = :userId
              AND cm.isActive     = true
              AND cm.isBanned     = false
              AND cm.memberRole IN (com.govlyx.AI.model.CommunityMember.MemberRole.MODERATOR, com.govlyx.AI.model.CommunityMember.MemberRole.ADMIN)
            """)
    boolean isModeratorOrAbove(
            @Param("communityId") Long communityId,
            @Param("userId")      Long userId);

    // â”€â”€ Mutations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Modifying
    @Query("UPDATE CommunityMember cm SET cm.isActive = false WHERE cm.community.id = :communityId AND cm.user.id = :userId")
    void deactivateMember(@Param("communityId") Long communityId, @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE CommunityMember cm SET cm.isActive = false WHERE cm.community.id = :communityId")
    void deactivateAllByCommunity(@Param("communityId") Long communityId);

    // â”€â”€ Hyperlocal seed helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Query("""
            SELECT cm.user.id FROM CommunityMember cm
            WHERE cm.community.id = :communityId AND cm.isActive = true
            """)
    List<Long> findActiveMemberUserIds(@Param("communityId") Long communityId);
    @EntityGraph(attributePaths = {"user", "community"})
    @Query("SELECT cm FROM CommunityMember cm " +
            "WHERE cm.user.id IN :userIds AND cm.community.id IN :communityIds AND cm.isActive = true")
    List<CommunityMember> findActiveByUserIdInAndCommunityIdIn(
            @Param("userIds") List<Long> userIds,
            @Param("communityIds") List<Long> communityIds);

    /** Bulk-removes all membership rows for a permanently-deleted community. */
    @Modifying
    @Query("DELETE FROM CommunityMember cm WHERE cm.community.id = :communityId")
    void deleteByCommunityId(@Param("communityId") Long communityId);

    /**
     * Batch-fetches (userId, memberRole) pairs for a set of authors in ONE community.
     * Used by CommunityService feed methods to populate authorCommunityRole for an
     * entire paginated post page in a single query — eliminates N+1 per-post lookups.
     *
     * Only returns elevated roles (MODERATOR, ADMIN). Plain MEMBERs are excluded
     * to keep the result set small and avoid unnecessary data transfer.
     *
     * Result format: Object[] { userId (Long), memberRole (String enum name) }
     */
    @Query("""
            SELECT cm.user.id, cm.memberRole
            FROM   CommunityMember cm
            WHERE  cm.community.id = :communityId
              AND  cm.user.id      IN :userIds
              AND  cm.isActive     = true
              AND  cm.memberRole  IN (com.govlyx.AI.model.CommunityMember.MemberRole.MODERATOR, com.govlyx.AI.model.CommunityMember.MemberRole.ADMIN)
            """)
    List<Object[]> findElevatedRolesByUserIds(
            @Param("communityId") Long communityId,
            @Param("userIds")     List<Long> userIds);
}