package com.Govlyx.AI.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "banned_actors", indexes = {
    @Index(name = "idx_banned_actor_token", columnList = "actor_token", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannedActor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_token", nullable = false, unique = true, length = 70)
    private String actorToken;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "banned_by_admin_id")
    private Long bannedByAdminId;

    @Column(name = "banned_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date bannedAt = new Date();

    @Column(name = "expires_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date expiresAt;
}
