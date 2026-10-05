package com.JanSahayak.AI.dto;

import com.JanSahayak.AI.enums.BroadcastScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateDto {
    @NotBlank(message = "Post content is required")
    @Size(max = 2000, message = "Content must not exceed 2000 characters")
    private String content;

    @Builder.Default
    private BroadcastScope broadcastScope = BroadcastScope.AREA;

    // Optional: Support for tagging users at creation
    private List<String> taggedUsernames;

    // ===== Broadcasting Target Fields =====
    @Size(max = 50, message = "Target country must not exceed 50 characters")
    private String targetCountry;

    @Size(max = 1000, message = "Target states must not exceed 1000 characters")
    private String targetStates;

    @Size(max = 2000, message = "Target districts must not exceed 2000 characters")
    private String targetDistricts;

    @Size(max = 6, min = 6, message = "Target pincode must be exactly 6 digits")
    private String targetPincode;

    @Size(max = 2000, message = "Target pincodes must not exceed 2000 characters")
    private String targetPincodes;

    // Flag to bypass duplicate check
    @Builder.Default
    private boolean forceSubmit = false;
}
