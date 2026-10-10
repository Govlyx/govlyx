package com.JanSahayak.AI.enums;

public enum ReportCategory {
    HARASSMENT("Harassment, Defamation & Gender Insult"),
    MISINFORMATION("Misinformation & Fake News (Misleading)"),
    SPAM("Unsolicited Commercial Ads & Spam (Marketing)"),
    HATE_SPEECH("Hate Speech & Public Incitement"),
    OBSCENITY("Obscenity & Bodily Privacy"),
    IMPERSONATION("Impersonation & Fake Profiles"),
    IP_INFRINGEMENT("Intellectual Property Violation"),
    NATIONAL_SECURITY("Threats to National Security & Sovereignty"),
    MALWARE("Harmful Software / Malware");

    private final String description;

    ReportCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
