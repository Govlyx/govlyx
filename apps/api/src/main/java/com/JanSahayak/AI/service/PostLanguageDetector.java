package com.JanSahayak.AI.service;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PostLanguageDetector — zero-latency, zero-dependency script-based language
 * detector for the 10 most-spoken Indian regional languages.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  HOW IT WORKS
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *  Every Indian language uses a distinct Unicode block. We scan the post
 *  content and count characters per script. Dominant script wins.
 *  Two scripts above threshold → "mixed".
 *  No script found → "hi" (safe default for JanSahayak's user base).
 *
 *  Zero ML, zero external calls, O(n) per post.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  SUPPORTED SCRIPTS & CODES
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *   Script      Language(s)        Code   Unicode block
 *   ─────────── ──────────────     ────   ────────────────────
 *   Devanagari  Hindi / Marathi    hi/mr  U+0900–U+097F
 *   Bengali     Bengali            bn     U+0980–U+09FF
 *   Telugu      Telugu             te     U+0C00–U+0C7F
 *   Tamil       Tamil              ta     U+0B80–U+0BFF
 *   Gujarati    Gujarati           gu     U+0A80–U+0AFF
 *   Kannada     Kannada            kn     U+0C80–U+0CFF
 *   Malayalam   Malayalam          ml     U+0D00–U+0D7F
 *   Gurmukhi    Punjabi            pa     U+0A00–U+0A7F
 *   Odia        Odia               or     U+0B00–U+0B7F
 *   Arabic      Urdu               ur     U+0600–U+06FF
 *   (none)      —                  mixed  two scripts ≥ threshold
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  CALL SITES
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *  SocialPostService.buildSocialPost()   → sets SocialPost.language on create
 *  SocialPostService.updateSocialPost()  → re-detects when content changes
 *
 *  Results consumed by:
 *    HLIGScorer.languageBoost()           — scoring multiplier
 *    InterestProfileService.onView/onLike — "lang:XX" topic weight update
 *    HLIGFeedService.widenByLanguage()    — candidate pool widening
 */
@Component
public class PostLanguageDetector {

    // ── Compiled patterns for each script block ───────────────────────────────

    private static final Pattern DEVANAGARI = Pattern.compile("[\u0900-\u097F]");
    private static final Pattern BENGALI    = Pattern.compile("[\u0980-\u09FF]");
    private static final Pattern TELUGU     = Pattern.compile("[\u0C00-\u0C7F]");
    private static final Pattern TAMIL      = Pattern.compile("[\u0B80-\u0BFF]");
    private static final Pattern GUJARATI   = Pattern.compile("[\u0A80-\u0AFF]");
    private static final Pattern KANNADA    = Pattern.compile("[\u0C80-\u0CFF]");
    private static final Pattern MALAYALAM  = Pattern.compile("[\u0D00-\u0D7F]");
    private static final Pattern GURMUKHI   = Pattern.compile("[\u0A00-\u0A7F]");
    private static final Pattern ODIA       = Pattern.compile("[\u0B00-\u0B7F]");
    private static final Pattern ARABIC     = Pattern.compile(
            "[\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]");

    /**
     * Min characters for a script to be counted as "present".
     * Prevents 1–2 loanword characters triggering false positives.
     */
    private static final int SCRIPT_THRESHOLD = 4;

    /**
     * If secondary script count / primary script count ≥ this → "mixed".
     * 0.40 means the secondary language makes up ≥40% of the script characters.
     */
    private static final double MIXED_RATIO_THRESHOLD = 0.40;

    /**
     * Marathi-exclusive high-frequency function words that do not appear
     * in standard Hindi. Used to disambiguate Devanagari posts.
     * Threshold: 2+ matches → Marathi, else → Hindi.
     *
     * Words: आहे, नाही, होते, केले, गेले, येते, म्हणजे, करतो, करते
     */
    private static final Pattern MARATHI_MARKERS = Pattern.compile(
            "\u0906\u0939\u0947|"                               // आहे
                    + "\u0928\u093E\u0939\u0940|"                        // नाही
                    + "\u0939\u094B\u0924\u0947|"                        // होते
                    + "\u0915\u0947\u0932\u0947|"                        // केले
                    + "\u0917\u0947\u0932\u0947|"                        // गेले
                    + "\u092F\u0947\u0924\u0947|"                        // येते
                    + "\u092E\u094D\u0939\u0923\u091C\u0947|"            // म्हणजे
                    + "\u0915\u0930\u0924\u094B|"                        // करतो
                    + "\u0915\u0930\u0924\u0947"                         // करते
    );

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Detects the dominant Indian regional language of the post content.
     *
     * @param content post body — may be null, blank, or Romanised
     * @return one of: "hi","mr","bn","te","ta","gu","kn","ml","pa","or","ur","mixed"
     *         Never null. Never throws.
     */
    public String detect(String content) {
        if (content == null || content.isBlank()) return "hi";

        int[] counts = {
                count(DEVANAGARI, content),  // 0 → devanagari
                count(BENGALI,    content),  // 1 → bn
                count(TELUGU,     content),  // 2 → te
                count(TAMIL,      content),  // 3 → ta
                count(GUJARATI,   content),  // 4 → gu
                count(KANNADA,    content),  // 5 → kn
                count(MALAYALAM,  content),  // 6 → ml
                count(GURMUKHI,   content),  // 7 → pa
                count(ODIA,       content),  // 8 → or
                count(ARABIC,     content),  // 9 → ur
        };
        String[] names = {"devanagari","bn","te","ta","gu","kn","ml","pa","or","ur"};

        int maxCount = 0, secondCount = 0;
        String dominant = null, second = null;

        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > maxCount) {
                secondCount = maxCount; second    = dominant;
                maxCount    = counts[i]; dominant = names[i];
            } else if (counts[i] > secondCount) {
                secondCount = counts[i]; second = names[i];
            }
        }

        if (dominant == null || maxCount < SCRIPT_THRESHOLD) {
            return "hi"; // Romanised / transliterated content — default to Hindi
        }

        // Two scripts meaningfully present → mixed
        if (second != null
                && secondCount >= SCRIPT_THRESHOLD
                && (double) secondCount / maxCount >= MIXED_RATIO_THRESHOLD) {
            return "mixed";
        }

        if ("devanagari".equals(dominant)) {
            return disambiguateDevanagari(content);
        }

        return dominant;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    /**
     * Disambiguates Devanagari between Hindi ("hi") and Marathi ("mr").
     * Bhojpuri, Maithili, Rajasthani → mapped to "hi" (same feed pool).
     */
    private String disambiguateDevanagari(String content) {
        Matcher m = MARATHI_MARKERS.matcher(content);
        int hits = 0;
        while (m.find()) {
            if (++hits >= 2) return "mr";
        }
        return "hi";
    }
}
