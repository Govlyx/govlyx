package com.JanSahayak.AI.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultBlobRequest {
    private String vaultBlob;
    private String vaultSalt;
    private String actorToken;
    private String clientSalt;
    private String username;
}

