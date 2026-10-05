package com.govlyx.AI.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Denormalized snapshot of a SocialPost shared into a Community Chat.
 * Ensures the chat history can still render the shared post even if the original
 * post is deleted from the platform.
 */
@Entity
@Table(name = "community_shared_post_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunitySharedPostSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private CommunityMessage message;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "author_username", length = 100)
    private String authorUsername;

    @Column(name = "author_avatar", length = 500)
    private String authorAvatar;

    @Column(name = "created_at", updatable = false, nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
