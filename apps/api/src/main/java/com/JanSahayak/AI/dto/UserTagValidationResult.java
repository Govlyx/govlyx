package com.JanSahayak.AI.dto;

import lombok.*;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTagValidationResult {
    private Boolean isValid; // Changed to Boolean for consistency
    private List<String> validUsernames;
    private List<String> invalidUsernames;
    private List<String> warnings;
    private Integer totalTaggedUsers; // Changed to Integer for consistency
    private String message; // Optional: overall validation message

    // Additional validation details
    private List<String> inactiveUsernames; // Valid usernames but inactive users
    private List<String> duplicateUsernames; // Duplicate usernames in the same content
    private Integer maxTagsAllowed; // Maximum tags allowed per post
    private Boolean exceedsMaxTags; // Whether tag count exceeds limit
    private List<String> suggestions; // Alternative username suggestions
}
