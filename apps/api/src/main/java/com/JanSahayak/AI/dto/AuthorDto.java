package com.JanSahayak.AI.dto;

import com.JanSahayak.AI.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Lightweight author snapshot attached to every SocialPostDto / CommentDto.
 *
 * SECURITY RULE: Only expose what the UI needs to render a post card.
 * NEVER include: email, contactNumber, address, password, or any PII
 * that the post viewer has no business seeing.
 *
 * Fields:
 *   id           — needed so frontend can navigate to author's profile
 *   username     — display name shown on the post card
 *   profileImage — avatar shown on the post card
 *   pincode      — kept for hyperlocal "same area" badge logic only
 *   roleName     — needed to show "Department" / "Admin" badges on posts
 *   isActive     — lets UI grey-out posts from deactivated accounts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorDto implements Serializable {

    private Long    id;
    private String  username;       // getActualUsername() — display name, NOT email
    private String  profileImage;
    private String  pincode;        // for "Posted near you" badge — no other PII
    private String  roleName;       // ROLE_USER / ROLE_DEPARTMENT / ROLE_ADMIN
    private Boolean isActive;

    // ── Factory ──────────────────────────────────────────────────────────────

    public static AuthorDto fromUser(User user) {
        if (user == null) return null;

        String roleName = null;
        try {
            if (user.getRole() != null && org.hibernate.Hibernate.isInitialized(user.getRole())) {
                roleName = user.getRole().getName();
            }
        } catch (Exception ignored) {
            // Defensive guard against LazyInitializationException when accessing proxy outside session
        }

        return fromUser(user, roleName);
    }

    public static AuthorDto fromUser(User user, String roleName) {
        if (user == null) return null;

        return AuthorDto.builder()
                .id(user.getId())
                .username(user.getActualUsername())   // display name, NOT email
                .profileImage(user.getProfileImage())
                .pincode(user.getPincode())
                .roleName(roleName)
                .isActive(user.getIsActive())
                .build();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    /** Display name for post card header — always the username. */
    public String getDisplayName() {
        return username;
    }

    /** True if the author has uploaded a profile picture. */
    public boolean hasProfileImage() {
        return profileImage != null && !profileImage.trim().isEmpty();
    }

    /** Show a "Department" badge on posts from government accounts. */
    public boolean isDepartment() {
        return "ROLE_DEPARTMENT".equalsIgnoreCase(roleName);
    }

    /** Show an "Admin" badge if needed. */
    public boolean isAdmin() {
        return "ROLE_ADMIN".equalsIgnoreCase(roleName);
    }

    /** True if the author is a regular citizen. */
    public boolean isNormalUser() {
        return "ROLE_USER".equalsIgnoreCase(roleName);
    }

    // ── Location helpers (for "Posted near you" badge) ────────────────────────

    public boolean hasPincode() {
        return pincode != null && pincode.matches("\\d{6}");
    }

    public String getStatePrefix() {
        return hasPincode() ? pincode.substring(0, 2) : null;
    }

    public String getDistrictPrefix() {
        return hasPincode() ? pincode.substring(0, 3) : null;
    }

    public boolean isInSameState(String otherPincode) {
        if (!hasPincode() || otherPincode == null || otherPincode.length() < 2) return false;
        return getStatePrefix().equals(otherPincode.substring(0, 2));
    }

    public boolean isInSameDistrict(String otherPincode) {
        if (!hasPincode() || otherPincode == null || otherPincode.length() < 3) return false;
        return getDistrictPrefix().equals(otherPincode.substring(0, 3));
    }
}
