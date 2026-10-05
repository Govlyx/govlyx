package com.Govlyx.AI.repository;

import com.Govlyx.AI.model.CommunitySharedPostSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

@Repository
public interface CommunitySharedPostSnapshotRepo extends JpaRepository<CommunitySharedPostSnapshot, Long> {

    @Modifying
    @Query("DELETE FROM CommunitySharedPostSnapshot s WHERE s.message.expiresAt <= :now")
    int deleteSnapshotsOfExpiredMessages(@Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM CommunitySharedPostSnapshot s WHERE s.message.communityId = :communityId")
    int deleteSnapshotsByCommunityId(@Param("communityId") Long communityId);
}
