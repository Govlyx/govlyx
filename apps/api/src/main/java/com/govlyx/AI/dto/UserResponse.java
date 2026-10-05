package com.Govlyx.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String displayName;
    private String profileImage;
    private String bio;
    private String pincode;
    private Boolean isActive;
    private Date createdAt;
    private Date updatedAt;
    private String role;

    public String getActualUsername() {
        return this.username;
    }
}
