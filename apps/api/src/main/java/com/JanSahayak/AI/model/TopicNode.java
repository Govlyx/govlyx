package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Table(name = "topic_nodes", indexes = {
    @Index(name = "idx_topic_name", columnList = "name", unique = true)
})
@Getter
@Setter
public class TopicNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "is_hashtag", nullable = false)
    private boolean isHashtag = false;

    @Column(name = "velocity_score", nullable = false)
    private int velocityScore = 0;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_seen_at")
    private Date lastSeenAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
        lastSeenAt = new Date();
    }
}
