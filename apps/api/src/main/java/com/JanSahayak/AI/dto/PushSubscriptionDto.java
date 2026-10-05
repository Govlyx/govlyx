package com.JanSahayak.AI.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PushSubscriptionDto {
    @NotBlank
    private String endpoint;
    
    @NotBlank
    private String p256dh;
    
    @NotBlank
    private String auth;
}
