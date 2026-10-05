package com.govlyx.AI.dto;

import com.govlyx.AI.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Public-facing user profile DTO that strictly protects citizen privacy.
 * Excludes sensitive fields like homeLatitude, homeLongitude, email, and phone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserProfileDto {
    private Long id;
    private String username;
    private String displayName;
    private String profileImage;
    private String bio;
    private String pincode;
    private String roleName;
    private Date createdAt;
    private Boolean isAdult;

    public static PublicUserProfileDto fromUser(User user) {
        if (user == null) return null;
        String role = user.getRole() != null ? user.getRole().getName() : "ROLE_USER";
        return PublicUserProfileDto.builder()
                .id(user.getId())
                .username(user.getActualUsername())
                .displayName(user.getDisplayName())
                .profileImage(user.getProfileImage())
                .bio(user.getBio())
                .pincode(user.getPincode())
                .roleName(role)
                .createdAt(user.getCreatedAt())
                .isAdult(user.getIsAdult())
                .build();
    }

    public static PublicUserProfileDto fromActorProfile(com.govlyx.AI.model.ActorProfile profile) {
        if (profile == null) return null;
        return PublicUserProfileDto.builder()
                .username(profile.getUsername())
                .displayName(profile.getUsername())
                .profileImage(profile.getProfileImage())
                .bio(profile.getBio())
                .pincode(profile.getPincode())
                .roleName("ROLE_USER")
                .createdAt(profile.getCreatedAt())
                .isAdult(profile.getIsAdult())
                .build();
    }

    public String getActualUsername() {
        return this.username;
    }
}
