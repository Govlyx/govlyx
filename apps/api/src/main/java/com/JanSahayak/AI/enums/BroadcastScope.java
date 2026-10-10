package com.JanSahayak.AI.enums;



public enum BroadcastScope {
    COUNTRY("Country-wide broadcast"),
    STATE("State-level broadcast"),
    DISTRICT("District-level broadcast"),
    NEARBY("Nearby (50km radius)"),
    AREA("Area/Pincode-level broadcast");

    private final String description;

    BroadcastScope(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
