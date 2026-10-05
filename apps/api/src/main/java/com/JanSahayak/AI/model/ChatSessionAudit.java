package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(
        name = "chat_session_audit",
        indexes = {
                @Index(name = "idx_chat_audit_user1", columnList = "user1_id"),
                @Index(name = "idx_chat_audit_user2", columnList = "user2_id"),
                @Index(name = "idx_chat_audit_started", columnList = "started_at")
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ChatSessionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The actual chat session ID (though ephemeral in memory, we record the ID)
    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "user1_id", nullable = false)
    private Long user1Id;

    @Column(name = "user2_id", nullable = false)
    private Long user2Id;

    @Column(name = "user1_ip")
    private String user1Ip;

    @Column(name = "user2_ip")
    private String user2Ip;

    @Column(name = "user1_used_media")
    @Builder.Default
    private Boolean user1UsedMedia = false;

    @Column(name = "user2_used_media")
    @Builder.Default
    private Boolean user2UsedMedia = false;

    @Column(name = "started_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date startedAt = new Date();

    @Column(name = "ended_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date endedAt;
}
