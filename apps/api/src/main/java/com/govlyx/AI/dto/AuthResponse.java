package com.govlyx.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String token;
    private String serverActorToken;
    private String vaultBlob;
    private String vaultSalt;
    private String seedBlindSalt;
    private Boolean hasVault;
    private String role;
    private UserResponse user;

    public AuthResponse(String token) {
        this.token = token;
    }
}
