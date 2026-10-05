package com.Govlyx.AI.repository;

import com.Govlyx.AI.model.CommentInteraction;
import com.Govlyx.AI.model.InteractionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM CommentInteraction ci WHERE ci.comment.id IN :commentIds")
    void deleteByCommentIdIn(@Param("commentIds") List<Long> commentIds);
}
