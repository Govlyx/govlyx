package com.JanSahayak.AI.util;

import java.util.Set;

public class LevenshteinDistanceUtil {

    /**
     * Calculates the Levenshtein distance between two strings.
     */
    public static int calculate(String s1, String s2) {
        if (s1 == null || s2 == null) {
            throw new IllegalArgumentException("Strings must not be null");
        }

        int n = s1.length();
        int m = s2.length();

        if (n == 0) return m;
        if (m == 0) return n;

        int[] p = new int[n + 1];
        int[] d = new int[n + 1];
        int[] _d;

        int i, j, cost;
        char t_j;

        for (i = 0; i <= n; i++) {
            p[i] = i;
        }

        for (j = 1; j <= m; j++) {
            t_j = s2.charAt(j - 1);
            d[0] = j;

            for (i = 1; i <= n; i++) {
                cost = s1.charAt(i - 1) == t_j ? 0 : 1;
                // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }
            _d = p;
            p = d;
            d = _d;
        }

        return p[n];
    }

    /**
     * Finds the closest matching established topic for a candidate word.
     * To be highly performant, it only checks topics that:
     * 1. Start with the same first character.
     * 2. Have a length difference of <= 2.
     * 3. Have a Levenshtein distance of <= 1 (for words length 4-6) or <= 2 (for words length 7+).
     * 
     * @param candidate The typo candidate (e.g., "criket")
     * @param establishedTopics The set of established topics (e.g., ["cricket", "movie"])
     * @return The corrected topic if found, otherwise the original candidate.
     */
    public static String findClosestMatch(String candidate, Set<String> establishedTopics) {
        if (candidate == null || candidate.length() < 4) {
            return candidate; // Don't try to correct very short words
        }

        char firstChar = candidate.charAt(0);
        int candLength = candidate.length();

        int maxAllowedDistance = candLength >= 7 ? 2 : 1;

        String bestMatch = candidate;
        int bestDistance = Integer.MAX_VALUE;

        for (String topic : establishedTopics) {
            // Optimization: Only compare if lengths are similar and they start with the same letter
            if (topic.length() > 0 && topic.charAt(0) == firstChar) {
                if (Math.abs(topic.length() - candLength) <= 2) {
                    int dist = calculate(candidate, topic);
                    if (dist <= maxAllowedDistance && dist < bestDistance) {
                        bestDistance = dist;
                        bestMatch = topic;
                    }
                }
            }
        }

        return bestMatch;
    }
}
