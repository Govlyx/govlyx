package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.SocialPost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * TopicExtractor — dynamically extracts canonical topics from a SocialPost.
 * 
 * V4 Architecture:
 * - Removed 600+ lines of hardcoded Roman/Native maps.
 * - Uses DynamicNLPProcessor to parse raw candidate words and hashtags.
 * - Uses TopicAggregationWorker to verify which candidates are established (velocity threshold met).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TopicExtractor {

    private final DynamicNLPProcessor dynamicNLPProcessor;
    private final TopicAggregationWorker topicAggregationWorker;

    public Map<String, Double> extract(SocialPost post) {
        Map<String, Double> result = new HashMap<>();
        
        try {
            Map<String, Double> candidates = dynamicNLPProcessor.parseCandidates(post);
            
            for (Map.Entry<String, Double> entry : candidates.entrySet()) {
                String candidate = entry.getKey();
                // Only return topics that have passed the velocity threshold and are established in DB
                if (topicAggregationWorker.isEstablishedTopic(candidate)) {
                    result.put(candidate, entry.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract dynamic topics for post {}: {}", post.getId(), e.getMessage());
        }

        return result;
    }

    /** Fast path: hashtags only. Used for lightweight scroll-past signals. */
    public Map<String, Double> extractFromHashtagsOnly(SocialPost post) {
        Map<String, Double> result = new HashMap<>();
        if (!post.hasHashtags()) return result;
        
        for (String tag : post.getHashtagsList()) {
            String raw = tag.replaceAll("^#", "").trim().toLowerCase();
            if (raw.isBlank()) continue;
            
            if (topicAggregationWorker.isEstablishedTopic(raw)) {
                result.put(raw, 1.00);
            }
        }
        return result;
    }
}
