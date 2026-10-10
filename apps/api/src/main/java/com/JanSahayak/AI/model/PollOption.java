package com.JanSahayak.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * PollOption entity — one row per choice in a Poll.
 * Each Poll has 2–4 PollOptions.
 */
@Entity
@Table(name = "poll_options", indexes = {
        @Index(name = "idx_poll_option_poll",  columnList = "poll_id"),
        @Index(name = "idx_poll_option_order", columnList = "poll_id, option_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== Relationships =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_poll_option_poll"))
    private Poll poll;

    @OneToMany(mappedBy = "pollOption", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<PollVote> votes = new ArrayList<>();

    // ===== Content =====

    /** Option text — e.g. "Traffic congestion", "Housing costs" */
    @Column(name = "option_text", nullable = false, length = 200)
    private String optionText;

    /** 1-based display order so options always appear in correct sequence */
    @Column(name = "option_order", nullable = false)
    private Integer optionOrder;

    // ===== Counters =====

    /** Denormalized vote count for fast reads — synced on every vote */
    @Column(name = "vote_count", nullable = false)
    @Builder.Default
    private Integer voteCount = 0;

    // ===== Timestamps =====

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date createdAt = new Date();

    // ===== Helper Methods =====

    public void incrementVoteCount() {
        this.voteCount = (this.voteCount != null ? this.voteCount : 0) + 1;
    }

    public void decrementVoteCount() {
        this.voteCount = Math.max(0, (this.voteCount != null ? this.voteCount : 0) - 1);
    }

    /**
     * Returns vote percentage rounded to 1 decimal place.
     * e.g. 400 votes out of 1247 total → 32.1%
     *
     * @param totalPollVotes total votes in the parent poll
     */
    public double getVotePercentage(int totalPollVotes) {
        if (totalPollVotes <= 0 || voteCount == null || voteCount == 0) return 0.0;
        return Math.round(((double) voteCount / totalPollVotes) * 1000.0) / 10.0;
    }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PollOption)) return false;
        PollOption other = (PollOption) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
