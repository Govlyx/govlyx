package com.JanSahayak.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(
        name = "user_interest_profiles",
        uniqueConstraints = @UniqueConstraint(
                name   = "uk_uip_user_topic",
                columnNames = {"user_id", "topic"}
        ),
        indexes = {
                @Index(name = "idx_uip_user_weight",    columnList = "user_id, weight DESC"),
                @Index(name = "idx_uip_topic",          columnList = "topic"),
                @Index(name = "idx_uip_last_engaged",   columnList = "last_engaged_at"),
                @Index(name = "idx_uip_signals",        columnList = "user_id, total_signals_cache"),
                // Language preference lookup — used by HLIGFeedService to
                // filter candidates by preferred languages without a separate table.
                // DB migration:
                //   ALTER TABLE user_interest_profiles
                //     ADD INDEX idx_uip_user_langs (user_id, preferred_languages);
                @Index(name = "idx_uip_user_langs",     columnList = "user_id, preferred_languages")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class UserInterestProfile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Canonical topic key (lowercase, ASCII-safe, max 100 chars).
     * Examples: "roads", "cricket", "bollywood", "water_supply",
     *           "ipl", "local_politics", "gaming", "crypto"
     *
     * Language topics follow the pattern "lang:XX" where XX is a BCP-47 code:
     *   "lang:hi"  — user reads Hindi content
     *   "lang:ta"  — user reads Tamil content
     *   "lang:bn"  — user reads Bengali content
     *   etc.
     *
     * Language topics are updated by InterestProfileService.onView/onLike/onSave
     * using the same weight system as content topics. This means:
     *   - A user who consistently reads Tamil posts gets a high "lang:ta" weight.
     *   - The weight decays at the same λ=0.015 rate, so language preference is
     *     inferred continuously from behaviour rather than a one-time onboarding step.
     */
    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    /**
     * Accumulated weight (decayed separately in batch).
     * Zone thresholds:
     *   CORE     ≥ 8.0  → strong interest, show liberally
     *   CASUAL   ≥ 2.0  → moderate interest
     *   EMERGING  < 2.0 → newly forming, use for diversity
     */
    @Column(name = "weight", nullable = false, columnDefinition = "numeric(10,4) DEFAULT 0")
    @Builder.Default
    private Double weight = 0.0;

    /**
     * Total raw signal count for this topic (undecayed).
     * > 200 → bubble_risk = true → diversity injection doubles.
     */
    @Column(name = "interaction_count", nullable = false)
    @Builder.Default
    private Integer interactionCount = 0;

    /**
     * SUM of all interactionCounts across all topics for this user,
     * cached here and updated atomically. Used for phase detection
     * without a separate COUNT(*) query per feed request.
     *
     * Phase:
     *   0       → COLD    (popularity fallback)
     *   1–30    → WARMING (50% interest / 50% popularity blend)
     *   31+     → WARM    (85% HLIG + 15% diversity)
     */
    @Column(name = "total_signals_cache", nullable = false)
    @Builder.Default
    private Integer totalSignalsCache = 0;

    /**
     * True when interactionCount > 200 — signals echo-chamber risk.
     * Feed service doubles diversity injection for this user.
     */
    @Column(name = "bubble_risk_flag", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    private Boolean bubbleRiskFlag = false;

    @Column(name = "last_engaged_at")
    private Instant lastEngagedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;  // set in @PrePersist — do NOT use @Builder.Default with Instant.now()

    // ── Language preference (denormalized for fast feed candidate filtering) ──

    /**
     * Comma-separated list of BCP-47 language codes the user prefers,
     * ordered by descending preference weight (strongest first).
     *
     * Example: "hi,en,mr"
     *
     * HOW IT IS MAINTAINED:
     *   InterestProfileService.rebuildLanguagePreferences(userId) is called
     *   asynchronously after every language-topic weight update. It reads all
     *   "lang:XX" rows for the user, sorts by decayed weight, and writes the
     *   top-5 codes into this field on whichever UserInterestProfile row has
     *   the highest weight (the "anchor row").
     *
     *   In practice the field lives on many rows for the same userId but only
     *   the anchor row is read by the feed — callers use:
     *     repo.getPreferredLanguages(userId) → reads this column from the
     *     top-weight row via idx_uip_user_langs.
     *
     * WHY DENORMALIZED HERE (not a separate table):
     *   Adding a separate user_language_preferences table would require an extra
     *   join in every feed request. Denormalizing into the existing UIP table means
     *   zero extra queries — the feed scorer reads it in the same pass that loads
     *   the interest profile.
     *
     * DB migration:
     *   ALTER TABLE user_interest_profiles
     *     ADD COLUMN preferred_languages VARCHAR(50) DEFAULT NULL;
     */
    @Column(name = "preferred_languages", length = 50)
    private String preferredLanguages;

    // ── Constants ────────────────────────────────────────────────────────────

    private static final double LAMBDA           = 0.015; // 46-day half-life (India pace)
    private static final double CORE_THRESHOLD   = 8.0;
    private static final double CASUAL_THRESHOLD = 2.0;
    private static final int    BUBBLE_THRESHOLD = 200;

    /** Maximum number of preferred languages stored in the denormalized field. */
    public static final int MAX_PREFERRED_LANGUAGES = 5;

    // ── Business Logic ────────────────────────────────────────────────────────

    /**
     * In-memory decay — does NOT write to DB.
     * Called at scoring time so the DB row is only written during nightly batch.
     */
    public double decayedWeight() {
        if (lastEngagedAt == null || weight == null || weight <= 0) return 0.0;
        long days = ChronoUnit.DAYS.between(lastEngagedAt, Instant.now());
        if (days <= 0) return weight;
        return weight * Math.exp(-LAMBDA * days);
    }

    public Zone zone() {
        double w = decayedWeight();
        if (w >= CORE_THRESHOLD)   return Zone.CORE;
        if (w >= CASUAL_THRESHOLD) return Zone.CASUAL;
        return Zone.EMERGING;
    }

    /**
     * Named accessor for stream collectors and Spring projections.
     * Returns in-memory decayed weight (does NOT write to DB).
     */
    public double getDecayedWeight() {
        return decayedWeight();
    }

    /** User has over-consumed this topic — inject more diversity. */
    @JsonIgnore
    public boolean isBubbleRisk() {
        return Boolean.TRUE.equals(bubbleRiskFlag);
    }

    // ── Language Helpers ──────────────────────────────────────────────────────

    /**
     * Returns true if this row holds a language-preference topic (e.g. "lang:hi").
     * Used by InterestProfileService when separating content topics from language topics.
     */
    @JsonIgnore
    public boolean isLanguageTopic() {
        return topic != null && topic.startsWith("lang:");
    }

    /**
     * Extracts the BCP-47 language code from a language topic.
     * Example: topic="lang:ta" → returns "ta".
     * Returns null if this is not a language topic.
     */
    public String languageCode() {
        if (!isLanguageTopic()) return null;
        return topic.substring(5); // strip "lang:"
    }

    /**
     * Returns the ordered list of preferred languages parsed from the
     * denormalized {@code preferredLanguages} column.
     *
     * Returns an empty list when no preference has been established yet
     * (new users or users who have only read English content so far).
     */
    public List<String> getPreferredLanguageList() {
        if (preferredLanguages == null || preferredLanguages.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(preferredLanguages.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(MAX_PREFERRED_LANGUAGES)
                .collect(Collectors.toList());
    }

    /**
     * Serializes a list of language codes into the denormalized column format.
     * Caller is responsible for ordering (descending weight) before calling this.
     *
     * @param codes ordered list of BCP-47 codes, e.g. ["hi", "en", "mr"]
     */
    public void setPreferredLanguageList(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            this.preferredLanguages = null;
        } else {
            this.preferredLanguages = codes.stream()
                    .limit(MAX_PREFERRED_LANGUAGES)
                    .collect(Collectors.joining(","));
        }
    }

    /**
     * Returns true if the user has an established language preference.
     * False for brand-new users (COLD phase) who haven't read enough posts yet.
     */
    public boolean hasLanguagePreference() {
        return preferredLanguages != null && !preferredLanguages.isBlank();
    }

    /**
     * Returns true if the given BCP-47 language code is in the user's top
     * preferred languages list.
     */
    public boolean prefersLanguage(String langCode) {
        if (langCode == null) return false;
        return getPreferredLanguageList().contains(langCode);
    }

    public enum Zone { CORE, CASUAL, EMERGING }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        clampWeight();
    }

    @PreUpdate
    private void preUpdate() {
        clampWeight();
        bubbleRiskFlag = interactionCount != null && interactionCount > BUBBLE_THRESHOLD;
    }

    private void clampWeight() {
        if (weight == null || weight < 0) weight = 0.0;
    }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserInterestProfile)) return false;
        UserInterestProfile other = (UserInterestProfile) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
