package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.CommentInteraction;
import com.JanSahayak.AI.model.InteractionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentInteractionRepository extends JpaRepository<CommentInteraction, Long> {

    Optional<CommentInteraction> findByCommentIdAndUserId(Long commentId, Long userId);

    @Query("SELECT ci.interactionType, COUNT(ci) FROM CommentInteraction ci WHERE ci.comment.id = :commentId GROUP BY ci.interactionType")
    List<Object[]> countInteractionsGroupedByCommentId(@Param("commentId") Long commentId);

    @Query("SELECT ci FROM CommentInteraction ci WHERE ci.user.id = :userId AND ci.comment.id IN :commentIds")
    List<CommentInteraction> findByUserIdAndCommentIdIn(@Param("userId") Long userId, @Param("commentIds") List<Long> commentIds);

    @Modifying
    @Query("DELETE FROM CommentInteraction ci WHERE ci.comment.id = :commentId")
    void deleteByCommentId(@Param("commentId") Long commentId);
}
