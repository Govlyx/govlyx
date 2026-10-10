package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.*;

import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTagSuggestionDto {
    private Long id;
    private String username;
    private String displayName;
    private String pincode;
    private String profileImage;
    private Boolean isActive;
    private String role;
    private String bio;
    private String taggableName; // @username format
    private Boolean hasLocation;
    private Long totalTaggedPosts;
    private Double resolutionRate;
    private String specialization;
    private Boolean isVerified;
    private String contactInfo;
    private String tier;
}
