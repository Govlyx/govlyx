package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    Optional<PollVote> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COUNT(pv) > 0 FROM PollVote pv WHERE pv.poll.id = :pollId AND pv.user.id = :userId")
    boolean existsByPollIdAndUserId(@Param("pollId") Long pollId, @Param("userId") Long userId);

    @Query("SELECT pv FROM PollVote pv WHERE pv.poll.id = :pollId AND pv.user.id = :userId")
    List<PollVote> findByPollIdAndUserId(@Param("pollId") Long pollId, @Param("userId") Long userId);

    @Query("SELECT pv.pollOption.id FROM PollVote pv WHERE pv.poll.id = :pollId AND pv.user.id = :userId")
    List<Long> findOptionIdsByPollIdAndUserId(@Param("pollId") Long pollId, @Param("userId") Long userId);


    @Query("SELECT pv.poll.id FROM PollVote pv " +
            "WHERE pv.user.id = :userId AND pv.poll.id IN :pollIds")
    List<Long> findVotedPollIdsByUserAndPollIds(
            @Param("userId") Long userId,
            @Param("pollIds") Collection<Long> pollIds);


    @Query("SELECT pv.poll.id, pv.pollOption.id FROM PollVote pv " +
            "WHERE pv.user.id = :userId AND pv.poll.id IN :pollIds")
    List<Object[]> findVotedOptionsByUserAndPollIds(
            @Param("userId") Long userId,
            @Param("pollIds") Collection<Long> pollIds);
}
