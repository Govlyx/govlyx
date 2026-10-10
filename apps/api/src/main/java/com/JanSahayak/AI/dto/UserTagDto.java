package com.JanSahayak.AI.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTagDto {
    @NotEmpty(message = "User IDs list cannot be empty")
    private List<Long> userIds;
}
