package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

/**
 * The Civic Persona entity (Approach A).
 * Holds the public social/civic identity of an actor completely decoupled from their private auth credentials.
 */
@Entity
@Table(name = "actor_profiles", indexes = {
    @Index(name = "idx_actor_profile_token",    columnList = "actor_token", unique = true),
    @Index(name = "idx_actor_profile_username", columnList = "username",    unique = true),
    @Index(name = "idx_actor_profile_pincode",  columnList = "pincode"),
    @Index(name = "idx_actor_profile_coords",   columnList = "home_latitude, home_longitude")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActorProfile {

    @Id
    @Column(name = "actor_token", length = 70, nullable = false, unique = true)
    private String actorToken;

    @Column(name = "username", length = 100, nullable = false, unique = true)
    private String username;  // e.g. "BraveTiger4821"


    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @Column(name = "bio", length = 1000)
    private String bio;

    @Column(name = "pincode", length = 6)
    private String pincode;

    // ===== Geo-Radius Feed Coordinates (Used for 5km / 15km / 30km nearby posts) =====
    @Column(name = "home_latitude", precision = 10, scale = 8)
    private BigDecimal homeLatitude;

    @Column(name = "home_longitude", precision = 10, scale = 8)
    private BigDecimal homeLongitude;

    // ===== Content Moderation & Safety Settings (Civic Persona Level) =====
    @Column(name = "muted_words", length = 1000)
    private String mutedWords;

    @Column(name = "blocked_actors", columnDefinition = "TEXT")
    private String blockedActors;

    @Column(name = "profanity_filter_level", length = 20)
    @Builder.Default
    private String profanityFilterLevel = "STRICT";

    @Column(name = "copyright_strikes", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer copyrightStrikes = 0;

    @Column(name = "is_adult", columnDefinition = "boolean")
    @Builder.Default
    private Boolean isAdult = true;

    // ===== UX & Display Preferences =====
    @Column(name = "theme", length = 20)
    @Builder.Default
    private String theme = "light";

    @Column(name = "interface_language", length = 10)
    @Builder.Default
    private String interfaceLanguage = "en";

    @Column(name = "preferred_language", length = 10)
    @Builder.Default
    private String preferredLanguage = "en";

    @Column(name = "auto_translate", columnDefinition = "boolean")
    @Builder.Default
    private Boolean autoTranslate = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date createdAt = new Date();

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    public String getDisplayName() {
        return this.username;
    }

    public void setDisplayName(String displayName) {
    }
}
