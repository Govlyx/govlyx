package com.JanSahayak.AI.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum FeedbackCategory {
    BUG, FEATURE_REQUEST, UI_UX, GENERAL;

    @JsonCreator
    public static FeedbackCategory fromString(String key) {
        return key == null ? null : FeedbackCategory.valueOf(key.toUpperCase());
    }
}
