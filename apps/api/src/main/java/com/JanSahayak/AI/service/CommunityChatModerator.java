package com.JanSahayak.AI.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
@Slf4j
public class CommunityChatModerator {

    // Regex pattern to match URLs
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/\\S*)?",
            Pattern.CASE_INSENSITIVE
    );

    // Moderation keywords associated with extremist coordination or violence
    private static final List<String> HIGH_RISK_KEYWORDS = Arrays.asList(
            "bomb", "blast", "ied", "rpg", "terrorist", "terrorism", "jihad", "mujahideen",
            "explode", "khalistan", "naxalite", "isis", "al-qaeda", "overthrow",
            "hatred", "kill all", "death to", "attack on", "infiltrate", "sovereignty threat"
    );

    // Forbidden domains/redirectors frequently used by malicious networks
    private static final List<String> SUSPICIOUS_DOMAINS = Arrays.asList(
            "onion", "bit.ly", "tinyurl.com", "is.gd", "t.me/joinchat", "discord.gg"
    );

    /**
     * Checks if the message content contains indicators of extremist activity or malicious link structures.
     * @param content Message body to scan.
     * @return true if content violates security policies and should be quarantined/blocked.
     */
    public boolean scanMessage(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        String lowerContent = content.toLowerCase();

        // 1. Keyword check for extremism/threat indicators
        for (String word : HIGH_RISK_KEYWORDS) {
            if (lowerContent.contains(word)) {
                log.warn("[Security Scanner] Extremist keyword trigger: '{}'", word);
                return true;
            }
        }

        // 2. Link scanning for suspicious domains or anonymous channels
        java.util.regex.Matcher matcher = URL_PATTERN.matcher(content);
        while (matcher.find()) {
            String url = matcher.group().toLowerCase();
            for (String domain : SUSPICIOUS_DOMAINS) {
                if (url.contains(domain)) {
                    log.warn("[Security Scanner] Suspicious link structure blocked: '{}'", url);
                    return true;
                }
            }
        }

        return false;
    }
}
