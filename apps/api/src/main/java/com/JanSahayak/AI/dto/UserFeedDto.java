package com.JanSahayak.AI.dto;

import com.JanSahayak.AI.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedDto {
    private Long id;
    private String username;
    private String displayName;
    private String profileImage;
    private String role;
    private String pincode;
    private Boolean isActive;
    private Long createdAt;
    // Removed lastActiveAt since User entity doesn't have lastActive field
    private Boolean hasLocation;
    private String bio;

    // Additional  for feed context
    private String locationInfo; // State/district derived from pincode
    private Long totalPosts;
    private Double resolutionRate; // For department users

    // Helper method to convert User entity to UserFeedDto
    public static UserFeedDto fromUser(User user) {
        return UserFeedDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .profileImage(user.getProfileImage())
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .pincode(user.getPincode())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().getTime() : null)

                .hasLocation(user.hasLocation())
                .bio(user.getBio())
                .build();
    }
}
