package com.JanSahayak.AI.dto;

import com.JanSahayak.AI.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserMeResponse {
    private Long id;
    private String username;
    private String actualUsername;
    private String email;
    private String pendingEmail;
    private String profileImage;
    private String bio;
    private String pincode;
    private Boolean hasInvalidPincode;
    private String preferredLanguage;
    private String interfaceLanguage;
    private String theme;
    private Boolean autoTranslate;
    private String profanityFilterLevel;
    private String mutedWords;
    private Boolean isEmailVerified;
    private Boolean isActive;
    private Date createdAt;
    private Date updatedAt;
    private List<String> authorities;
    /** Non-null when the user has granted location permission — used by frontend to decide
     * whether to show the GPS prompt banner. Values are the pincode-center coordinates
     * until the user provides a live device fix. */
    private java.math.BigDecimal homeLatitude;
    private java.math.BigDecimal homeLongitude;

    
    public UserMeResponse(User user) {
        this.id = user.getId();
        this.username = user.getUsername(); // returns email due to UserDetails
        this.actualUsername = user.getActualUsername(); // true username
        this.email = user.getEmail();
        this.pendingEmail = user.getPendingEmail();
        this.profileImage = user.getProfileImage();
        this.bio = user.getBio();
        this.pincode = user.getPincode();
        this.hasInvalidPincode = user.getHasInvalidPincode();
        this.preferredLanguage = user.getPreferredLanguage();
        this.interfaceLanguage = user.getInterfaceLanguage();
        this.theme = user.getTheme();
        this.autoTranslate = user.getAutoTranslate();
        this.profanityFilterLevel = user.getProfanityFilterLevel();
        this.mutedWords = user.getMutedWords();
        this.isEmailVerified = user.getIsEmailVerified();
        this.isActive = user.getIsActive();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
        this.homeLatitude  = user.getHomeLatitude();
        this.homeLongitude = user.getHomeLongitude();
        
        if (user.getAuthorities() != null) {
            this.authorities = user.getAuthorities().stream()
                    .map(auth -> auth.getAuthority())
                    .collect(Collectors.toList());
        }
    }
}
