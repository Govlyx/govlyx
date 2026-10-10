package com.JanSahayak.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "poll_votes",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"poll_id", "user_id", "poll_option_id"},
                        name = "uk_poll_vote_user_option"
                )
        },
        indexes = {
                @Index(name = "idx_poll_vote_poll",      columnList = "poll_id"),
                @Index(name = "idx_poll_vote_user",      columnList = "user_id"),
                @Index(name = "idx_poll_vote_option",    columnList = "poll_option_id"),
                @Index(name = "idx_poll_vote_poll_user", columnList = "poll_id, user_id"),
                @Index(name = "idx_poll_vote_voted_at",  columnList = "voted_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", unique = true, length = 36)
    private String idempotencyKey;

    // ===== Relationships =====

    /** Denormalized poll reference for fast queries (poll_id, user_id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_poll_vote_poll"))
    private Poll poll;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_poll_vote_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_option_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_poll_vote_option"))
    private PollOption pollOption;

    // ===== Timestamps =====

    @Column(name = "voted_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date votedAt = new Date();

    // ===== Helper Methods =====

    public Long getPollId()       { return poll       != null ? poll.getId()       : null; }
    public Long getUserId()       { return user       != null ? user.getId()       : null; }
    public Long getPollOptionId() { return pollOption != null ? pollOption.getId() : null; }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PollVote)) return false;
        PollVote other = (PollVote) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
