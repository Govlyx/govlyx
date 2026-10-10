package com.JanSahayak.AI.dto;

import com.JanSahayak.AI.enums.BroadcastScope;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastTargetUpdateDto {
    @NotNull(message = "Broadcast scope is required")
    private BroadcastScope broadcastScope;

    private List<String> targetStates;    // for STATE scope: ["40","41", ...]
    private List<String> targetDistricts; // for DISTRICT scope: ["400","401","411", ...]
    private List<String> targetPincodes;  // for PINCODE scope: ["400001", "411002", ...]
}
