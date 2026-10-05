package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PollRepository extends JpaRepository<Poll, Long> {

    Optional<Poll> findByIdempotencyKey(String idempotencyKey);

    Optional<Poll> findBySocialPostId(Long socialPostId);

    @Query("SELECT p FROM Poll p LEFT JOIN FETCH p.options WHERE p.id = :id")
    Optional<Poll> findByIdWithOptions(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Poll p LEFT JOIN FETCH p.options WHERE p.socialPost.id IN :socialPostIds")
    List<Poll> findBySocialPostIdIn(@Param("socialPostIds") Collection<Long> socialPostIds);
}
