package com.govlyx.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.*;
import java.util.Date;

@Entity
@Table(name = "community_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"community_id", "user_id"}, name = "uk_community_member"),
        indexes = {
                @Index(name = "idx_cm_community", columnList = "community_id"),
                @Index(name = "idx_cm_user",      columnList = "user_id"),
                @Index(name = "idx_cm_role",      columnList = "community_id, member_role"),
                @Index(name = "idx_cm_active",    columnList = "is_active"),
                @Index(name = "idx_cm_joined",    columnList = "joined_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CommunityMember {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cm_community"))
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cm_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 20)
    @Builder.Default private MemberRole memberRole = MemberRole.MEMBER;

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean") @Builder.Default private Boolean isActive = true;
    @Column(name = "is_muted", nullable = false, columnDefinition = "boolean") @Builder.Default private Boolean isMuted = false;
    @Column(name = "is_banned", nullable = false, columnDefinition = "boolean") @Builder.Default private Boolean isBanned = false;

    @Column(name = "joined_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP) @Builder.Default private Date joinedAt = new Date();

    @Column(name = "role_updated_at") @Temporal(TemporalType.TIMESTAMP) private Date roleUpdatedAt;
    @Column(name = "banned_at")       @Temporal(TemporalType.TIMESTAMP) private Date bannedAt;
    @Column(name = "ban_reason", length = 500) private String banReason;

    public enum MemberRole { 
        MEMBER(0), 
        MODERATOR(1), 
        ADMIN(2); 

        private final int rank;
        MemberRole(int rank) { this.rank = rank; }
        public int getRank() { return rank; }
    }

    @JsonIgnore
    public boolean isAdmin()     { return MemberRole.ADMIN.equals(memberRole); }
    @JsonIgnore
    public boolean isModerator() { return MemberRole.MODERATOR.equals(memberRole); }

    public boolean canModerate() {
        return Boolean.TRUE.equals(isActive)
                && !Boolean.TRUE.equals(isBanned)
                && (isAdmin() || isModerator());
    }

    public void ban(String reason) {
        isBanned = true; isActive = false;
        banReason = reason; bannedAt = new Date();
    }

    public void unban() {
        isBanned = false; isActive = true;
        banReason = null; bannedAt = null;
    }

    @PrePersist
    private void prePersist() { if (joinedAt == null) joinedAt = new Date(); }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommunityMember)) return false;
        CommunityMember other = (CommunityMember) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
