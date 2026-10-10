package com.JanSahayak.AI.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TextSimilarityService {

    // Common English and Hindi stop words
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            // English
            "a", "an", "and", "are", "as", "at", "be", "but", "by",
            "for", "if", "in", "into", "is", "it",
            "no", "not", "of", "on", "or", "such",
            "that", "the", "their", "then", "there", "these",
            "they", "this", "to", "was", "will", "with",
            "please", "fix", "issue", "problem", "resolve", "help",
            "near", "outside", "behind", "front", "very", "too", "much",
            // Hindi / Hinglish
            "hai", "ki", "ka", "ke", "ko", "se", "mein", "par",
            "karo", "kijiye", "bhi", "toh", "hi", "aur", "ya", "ye",
            "wo", "kya", "kab", "kaise", "idhar", "udhar",
            "yahan", "wahan", "sir", "madam", "ji"
    ));

    private static final Pattern WORD_PATTERN = Pattern.compile("\\W+");

    /**
     * Calculates the Jaccard similarity between two texts.
     * Jaccard similarity = (Intersection of tokens) / (Union of tokens)
     *
     * @param text1 First string
     * @param text2 Second string
     * @return Similarity score between 0.0 and 1.0
     */
    public double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }
        
        // Fast-path: Exact match check before heavy tokenization
        if (text1.equalsIgnoreCase(text2)) {
            return 1.0;
        }

        Set<String> set1 = tokenizeAndClean(text1);
        Set<String> set2 = tokenizeAndClean(text2);

        if (set1.isEmpty() && set2.isEmpty()) {
            return 1.0; // Both texts are effectively empty or only contain stop words
        }
        if (set1.isEmpty() || set2.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        // Jaccard Similarity (Intersection over Union)
        double jaccard = (double) intersection.size() / union.size();

        // Overlap Coefficient (how much of the smaller text is contained in the larger text)
        int minSize = Math.min(set1.size(), set2.size());
        double overlap = minSize == 0 ? 0.0 : (double) intersection.size() / minSize;

        // Return the average of both to balance strict matching and substring-like matching
        return (jaccard + overlap) / 2.0;
    }

    /**
     * Tokenizes a string, converts to lowercase, removes punctuation,
     * and filters out common stop words and short tokens.
     *
     * @param text The input string
     * @return A set of significant words
     */
    public Set<String> tokenizeAndClean(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new HashSet<>();
        }

        return Arrays.stream(WORD_PATTERN.split(text.toLowerCase()))
                .filter(word -> word.length() > 2) // Ignore very short words
                .filter(word -> !STOP_WORDS.contains(word))
                .collect(Collectors.toSet());
    }
}
