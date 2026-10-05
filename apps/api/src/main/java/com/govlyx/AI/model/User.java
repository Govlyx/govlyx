package com.govlyx.AI.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.govlyx.AI.enums.PostStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.govlyx.AI.enums.BroadcastScope;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.Hibernate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;
import java.util.stream.Collectors;

import org.hibernate.annotations.BatchSize;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "username",   name = "uk_user_username"),
                @UniqueConstraint(columnNames = "email_hash", name = "uk_user_email_hash")
        },
        indexes = {
                @Index(name = "idx_user_username",         columnList = "username"),
                @Index(name = "idx_user_email_hash",       columnList = "email_hash"),
                @Index(name = "idx_user_google_id",        columnList = "google_id"),
                @Index(name = "idx_user_pincode",          columnList = "pincode"),
                @Index(name = "idx_user_is_active",        columnList = "is_active"),
                @Index(name = "idx_user_created_at",       columnList = "created_at"),
                @Index(name = "idx_user_role",             columnList = "role_id")
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@BatchSize(size = 50)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    @Size(min = 4, max = 100, message = "Username must be between 4 and 100 characters")
    private String username;



    // nullable = true: Google OAuth users have no password.
    // @Size removed from field — still enforced in RegisterRequest DTO for email/password registration.
    @Column(nullable = true, length = 255)
    @JsonIgnore
    private String password;

    @Convert(converter = com.govlyx.AI.security.AesGcmEmailConverter.class)
    @Column(name = "email_encrypted", length = 512, nullable = true)
    @Email(message = "Invalid email format")
    @JsonIgnore
    private String email;

    @Column(name = "email_hash", length = 64, unique = true)
    @JsonIgnore
    private String emailHash;

    @Column(name = "actor_salt", length = 64)
    @JsonIgnore
    private String actorSalt;

    @Column(name = "vault_blob", columnDefinition = "TEXT")
    @JsonIgnore
    private String vaultBlob;

    @Column(name = "vault_salt", length = 64)
    @JsonIgnore
    private String vaultSalt;

    @Column(name = "seed_blind_salt", length = 64)
    @JsonIgnore
    private String seedBlindSalt;

    @Transient
    @JsonIgnore
    private String migrationStatus = "COMPLETED";

    public String getEmailEncrypted() {
        return this.email;
    }

    public void setEmailEncrypted(String val) {
    }



    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @Column(length = 1000)
    @Size(max = 1000, message = "Bio cannot exceed 1000 characters")
    private String bio;



    @Column(name = "pincode", length = 6)
    @Size(min = 6, max = 6, message = "Pincode must be exactly 6 digits")
    @jakarta.validation.constraints.Pattern(regexp = "^\\d{6}$", message = "Pincode must be exactly 6 digits")
    @JsonIgnore
    private String pincode;

    @Column(name = "home_latitude", precision = 10, scale = 8)
    @JsonIgnore
    private java.math.BigDecimal homeLatitude;

    @Column(name = "home_longitude", precision = 10, scale = 8)
    @JsonIgnore
    private java.math.BigDecimal homeLongitude;

    @Column(name = "has_invalid_pincode", columnDefinition = "boolean")
    @JsonIgnore
    private Boolean hasInvalidPincode;

    @Column(name = "is_adult", columnDefinition = "boolean")
    private Boolean isAdult;

    // ===== OAuth2 / Social Auth =====
    // googleId: the "sub" (subject) claim from Google's id_token.
    // Stored to detect duplicate Google accounts and enable future account linking.
    @Column(name = "google_id", length = 255, unique = true)
    @JsonIgnore
    private String googleId;

    // authProvider: tracks how the account was created.
    // "LOCAL"       — email/password registration
    // "GOOGLE"      — Google OAuth only (no password)
    // "LOCAL+GOOGLE" — started as LOCAL, then linked a Google account
    @Column(name = "auth_provider", length = 20)
    @Builder.Default
    private String authProvider = "LOCAL";

    // ===== Localization & Moderation Settings =====
    @Column(name = "interface_language", length = 10)
    @Builder.Default
    private String interfaceLanguage = "en";

    @Column(name = "preferred_language", length = 10)
    @Builder.Default
    private String preferredLanguage = "en";

    @Column(name = "auto_translate", columnDefinition = "boolean")
    @Builder.Default
    private Boolean autoTranslate = false;

    @Column(name = "profanity_filter_level", length = 20)
    @Builder.Default
    private String profanityFilterLevel = "STRICT";





    @Column(name = "muted_words", length = 1000)
    private String mutedWords;

    @Column(name = "blocked_actors", columnDefinition = "TEXT")
    @JsonIgnore
    private String blockedActors;


    @Column(name = "theme", length = 20)
    @Builder.Default
    private String theme = "light";

    public String getTheme() {
        return this.theme != null ? this.theme : "light";
    }

    // ===== Session & Security =====
    @Column(name = "session_token", length = 36)
    @JsonIgnore
    private String sessionToken;

    // ===== Legal & Moderation =====
    
    @Column(name = "copyright_strikes", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer copyrightStrikes = 0;

    @Column(name = "banned_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date bannedAt;

    @Column(name = "ban_reason", length = 500)
    private String banReason;

    @Column(name = "is_email_verified", columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isEmailVerified = false;

    public Boolean getIsEmailVerified() {
        return isEmailVerified != null && isEmailVerified;
    }

    @Column(name = "email_verification_token", length = 100)
    @JsonIgnore
    private String emailVerificationToken;

    @Column(name = "email_verification_token_expiry")
    @Temporal(TemporalType.TIMESTAMP)
    @JsonIgnore
    private Date emailVerificationTokenExpiry;

    @Column(name = "pending_email", length = 100)
    @Email(message = "Invalid email format")
    @JsonIgnore
    private String pendingEmail;

    @Column(name = "email_update_token", length = 100)
    @JsonIgnore
    private String emailUpdateToken;

    @Column(name = "email_update_token_expiry")
    @Temporal(TemporalType.TIMESTAMP)
    @JsonIgnore
    private Date emailUpdateTokenExpiry;

    /**
     * FIX: Added columnDefinition = "boolean" to explicitly tell Hibernate/PostgreSQL
     * to treat this column as a native BOOLEAN, not a BIT type.
     *
     * Root cause of error:
     *   ERROR: column "is_active" is of type bit but expression is of type boolean
     *
     * Without columnDefinition, Hibernate may generate/expect a BIT column in
     * PostgreSQL, which is NOT assignment-compatible with Java's Boolean/boolean.
     * PostgreSQL's BOOLEAN type IS compatible, and this annotation enforces that.
     *
     * If the column already exists as BIT in the DB, run this migration once:
     *   ALTER TABLE users ALTER COLUMN is_active TYPE boolean USING is_active::boolean;
     */
    @Column(name = "is_active", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date createdAt = new Date();

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    // ===== Relationships =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "fk_user_created_by"))
    @JsonIgnore
    private User createdBy;

    @OneToMany(mappedBy = "createdBy", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    @JsonIgnore
    private List<User> createdUsers = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Post> posts = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<SocialPost> socialPosts = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<PostLike> likes = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<PostView> postViews = new ArrayList<>();

    /**
     * FIX: Changed from FetchType.EAGER to FetchType.LAZY.
     *
     * EAGER loading caused the Role entity to be fetched alongside EVERY user lookup —
     * including the Spring Security authentication path that fires on every API request.
     * This doubled query count on the hottest path in the application.
     *
     * Role is now loaded lazily. The authentication path in CustomUserDetailsService uses
     * UserRepo.findByEmailWithRole() which does a JOIN FETCH in a single query,
     * so there is no extra round-trip for authentication.
     *
     * Any other code that needs the role (e.g. isAdmin(), isDepartment()) must be
     * called within a Hibernate session or via a query that JOIN FETCHes the role.
     * The role is always available within @Transactional service methods.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_role"))
    @JsonIgnore
    private Role role;

    @OneToMany(mappedBy = "taggedUser", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    @JsonIgnore
    private List<UserTag> receivedTags = new ArrayList<>();

    @OneToMany(mappedBy = "taggedBy", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    @JsonIgnore
    private List<UserTag> createdTags = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL,
            fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<Notification> notifications = new ArrayList<>();

    /**
     * PincodeLookup data enriched at runtime — NOT stored in the DB.
     */
    @Transient
    private PincodeLookup pincodeLookupData;

    // ===== Role Helper Methods =====

    @JsonIgnore
    public boolean isAdmin() {
        if (role == null || role.getName() == null) {
            return false;
        }
        return "ROLE_ADMIN".equalsIgnoreCase(role.getName());
    }

    @JsonIgnore
    public boolean isDepartment() {
        if (role == null || role.getName() == null) {
            return false;
        }
        return "ROLE_DEPARTMENT".equalsIgnoreCase(role.getName());
    }

    @JsonIgnore
    public boolean isNormalUser() {
        if (role == null || role.getName() == null) {
            return false;
        }
        return "ROLE_USER".equalsIgnoreCase(role.getName());
    }

    public boolean canCreateBroadcast() {
        return isAdmin() || isDepartment();
    }

    // ===== Pincode Helper Methods =====

    @JsonIgnore
    public String getStatePrefix() {
        return hasPincode() ? pincode.substring(0, 2) : null;
    }

    @JsonIgnore
    public String getDistrictPrefix() {
        return hasPincode() ? pincode.substring(0, 3) : null;
    }

    public boolean isInSameState(String otherPincode) {
        if (!hasPincode() || otherPincode == null || otherPincode.length() < 2) {
            return false;
        }
        return getStatePrefix().equals(otherPincode.substring(0, 2));
    }

    public boolean isInSameDistrict(String otherPincode) {
        if (!hasPincode() || otherPincode == null || otherPincode.length() < 3) {
            return false;
        }
        return getDistrictPrefix().equals(otherPincode.substring(0, 3));
    }

    // ===== Basic Helper Methods =====

    public String getDisplayName() {
        return getActualUsername();
    }

    public void setDisplayName(String displayName) {
    }


    @JsonIgnore
    public String getTaggableName() {
        return "@" + username;
    }

    @JsonIgnore
    public boolean hasProfileImage() {
        return profileImage != null && !profileImage.trim().isEmpty();
    }

    @JsonIgnore
    public boolean hasBio() {
        return bio != null && !bio.trim().isEmpty();
    }

    @JsonIgnore
    public boolean hasPincode() {
        return pincode != null && pincode.matches("\\d{6}");
    }

    @JsonIgnore
    public boolean isValidPincode() {
        return hasPincode();
    }

    @JsonIgnore
    public int getPostCount() {
        return (posts != null && Hibernate.isInitialized(posts)) ? posts.size() : 0;
    }

    @JsonIgnore
    public int getSocialPostCount() {
        return (socialPosts != null && Hibernate.isInitialized(socialPosts)) ? socialPosts.size() : 0;
    }

    @JsonIgnore
    public int getCommentCount() {
        return (comments != null && Hibernate.isInitialized(comments)) ? comments.size() : 0;
    }

    @JsonIgnore
    public int getLikeCount() {
        return (likes != null && Hibernate.isInitialized(likes)) ? likes.size() : 0;
    }

    @JsonIgnore
    public String getPrimaryLocation() {
        return hasPincode() ? pincode : null;
    }

    @JsonIgnore
    public boolean canParticipateInLocalDiscovery() {
        return hasPincode() && isActive != null && isActive;
    }

    // ===== Location Compatibility Methods =====

    @JsonIgnore
    public boolean hasLocation() {
        return hasPincode();
    }

    @JsonIgnore
    public String getLocation() {
        return hasPincode() ? pincode : null;
    }

    // ===== Spring Security UserDetails Methods =====

    /**
     * FIX: Added Hibernate.isInitialized() guard before accessing role.getName().
     *
     * Without this guard, if Role is a lazy proxy and the Hibernate session is
     * already closed (e.g. JWT filter running outside a transaction), calling
     * role.getName() throws LazyInitializationException → authentication fails → 403.
     *
     * The primary fix is in CustomUserDetailsService using JOIN FETCH queries so
     * Role is always initialized. This guard is a defensive second layer that
     * prevents a crash even if the Role proxy is somehow not yet initialized —
     * instead of throwing, it returns an empty authority list and logs nothing,
     * which will result in an access-denied rather than a 500/crash.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (role != null && Hibernate.isInitialized(role) && role.getName() != null) {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
        }
        return authorities;
    }

    /**
     * Spring Security uses this as the login credential — returns EMAIL, EMAIL_HASH, or ID.
     * Defensively handles nullified email post-contraction.
     * Use getActualUsername() to get the display username.
     */
    @Override
    public String getUsername() {
        if (this.email != null) return this.email;
        if (this.emailHash != null) return this.emailHash;
        return this.id != null ? String.valueOf(this.id) : "";
    }

    public String getDecryptedEmail(com.govlyx.AI.security.AesGcmEmailConverter emailConverter) {
        if (this.email != null && this.email.contains("@")) {
            return this.email;
        }
        if (emailConverter != null && this.email != null) {
            return emailConverter.convertToEntityAttribute(this.email);
        }
        return this.email;
    }

    public String getActualUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        if (bannedAt != null) return false;
        return isActive != null && isActive;
    }

    public void incrementCopyrightStrikes() {
        this.copyrightStrikes = (this.copyrightStrikes != null ? this.copyrightStrikes : 0) + 1;
    }

    public void banUser(String reason) {
        this.isActive = false;
        this.bannedAt = new Date();
        this.banReason = reason;
        this.updatedAt = new Date();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = new Date();
    }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User other = (User) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
