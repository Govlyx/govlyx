package com.govlyx.AI.service;

import com.govlyx.AI.model.TopicNode;
import com.govlyx.AI.model.SocialPost;
import com.govlyx.AI.repository.TopicNodeRepo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicAggregationWorker {

    private final TopicNodeRepo topicNodeRepo;
    private final DynamicNLPProcessor dynamicNLPProcessor;

    // Threshold of occurrences before promoting a candidate to a full TopicNode
    private static final int VELOCITY_THRESHOLD = 3;

    // Accumulator tracks candidate occurrences
    private Cache<String, Integer> candidateAccumulator;

    // In-memory set of established topics for ultra-fast lookup by TopicExtractor
    private final Set<String> establishedTopics = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        // Cache evicts after 2 hours. If a candidate doesn't hit the threshold in 2 hours, it's dropped.
        candidateAccumulator = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(2, TimeUnit.HOURS)
                .build();

        // Load all existing topics into fast-lookup memory set
        log.info("Loading established topics into memory...");
        topicNodeRepo.findAll().forEach(t -> establishedTopics.add(t.getName()));
        log.info("Loaded {} topics.", establishedTopics.size());
    }

    /**
     * Parses the post and accumulates candidates asynchronously.
     */
    @Async
    @Transactional
    public void processPostAsync(SocialPost post) {
        try {
            if (post.isCommunityPost() && !"PUBLIC".equals(post.getCommunityPrivacy()) && !Boolean.TRUE.equals(post.getCommunityFeedEligible())) {
                return; // Do not aggregate topics/hashtags from private/secret communities unless marked to show in feed
            }
            Map<String, Double> candidates = dynamicNLPProcessor.parseCandidates(post);
            accumulateCandidates(candidates);
        } catch (Exception e) {
            log.warn("Failed to process candidates for post {}: {}", post.getId(), e.getMessage());
        }
    }

    /**
     * Internal accumulation logic.
     */
    public void accumulateCandidates(Map<String, Double> candidates) {
        Date now = new Date();
        for (Map.Entry<String, Double> entry : candidates.entrySet()) {
            String candidate = entry.getKey();
            boolean isHashtag = entry.getValue() >= 1.0;

            if (establishedTopics.contains(candidate)) {
                // Already an established topic, just increment its velocity score in DB
                topicNodeRepo.incrementVelocityAndLastSeen(candidate, now);
            } else {
                // Not yet established. Check if it's a typo of an established topic.
                String correctedCandidate = com.govlyx.AI.util.LevenshteinDistanceUtil.findClosestMatch(candidate, establishedTopics);
                
                if (!correctedCandidate.equals(candidate)) {
                    // It was a typo! Map it to the established topic.
                    log.info("Typo caught: mapped '{}' -> '{}'", candidate, correctedCandidate);
                    topicNodeRepo.incrementVelocityAndLastSeen(correctedCandidate, now);
                    // Also update the extractor's map mentally by ensuring the rest of the flow is skipped
                    continue; 
                }

                // Not yet established and not a typo. Accumulate occurrences.
                Integer count = candidateAccumulator.get(candidate, k -> 0);
                count = (count == null ? 0 : count) + 1;

                if (count >= VELOCITY_THRESHOLD || isHashtag) {
                    // Promote to official TopicNode (Hashtags bypass threshold for immediate discovery)
                    promoteToTopic(candidate, isHashtag, now);
                } else {
                    candidateAccumulator.put(candidate, count);
                }
            }
        }
    }

    private void promoteToTopic(String candidate, boolean isHashtag, Date now) {
        try {
            // Check if another thread just inserted it to avoid unique constraint violations
            if (topicNodeRepo.findByName(candidate).isEmpty()) {
                TopicNode node = new TopicNode();
                node.setName(candidate);
                node.setHashtag(isHashtag);
                node.setVelocityScore(1);
                node.setLastSeenAt(now);
                topicNodeRepo.save(node);
                log.info("Promoted new dynamic topic: {}", candidate);
            }
            // Add to fast lookup set
            establishedTopics.add(candidate);
            // Remove from accumulator
            candidateAccumulator.invalidate(candidate);
        } catch (Exception e) {
            log.warn("Failed to promote topic '{}' - possibly concurrent insert. Continuing...", candidate);
            establishedTopics.add(candidate);
        }
    }

    /**
     * Ultra-fast lookup for TopicExtractor
     */
    public boolean isEstablishedTopic(String topic) {
        return establishedTopics.contains(topic);
    }
    
    /**
     * Can be called by scheduled tasks to refresh the memory set or clean up.
     */
    public void removeEstablishedTopic(String topic) {
        establishedTopics.remove(topic);
    }
}
