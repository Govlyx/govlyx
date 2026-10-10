package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.PostResponse;
import com.JanSahayak.AI.dto.SocialPostDto;
import com.JanSahayak.AI.model.PostTranslation;
import com.JanSahayak.AI.repository.PostTranslationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Collections;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationService {

    // Limit to exactly 1 concurrent translation request to avoid 429s from free API
    private final java.util.concurrent.Semaphore googleApiSemaphore = new java.util.concurrent.Semaphore(1);
    private static final long FEED_TRANSLATION_TIMEOUT_MS = 300L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PostTranslationRepository postTranslationRepository;

    private TranslationService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy TranslationService self) {
        this.self = self;
    }

    /**
     * Translates a batch of SocialPostDto.
     */
    public void translateSocialPosts(List<SocialPostDto> dtos, String targetLanguage) {
        if (dtos == null || dtos.isEmpty() || targetLanguage == null || targetLanguage.equalsIgnoreCase("en")) {
            return;
        }

        List<Long> postIds = dtos.stream().map(SocialPostDto::getId).collect(Collectors.toList());
        
        // Fetch cached translations
        List<PostTranslation> cachedTranslations = postTranslationRepository
                .findByReferenceIdInAndReferenceTypeAndTargetLanguage(postIds, "SOCIAL_POST", targetLanguage);
        
        Map<Long, String> cacheMap = cachedTranslations.stream()
                .collect(Collectors.toMap(PostTranslation::getReferenceId, PostTranslation::getTranslatedText, (a, b) -> a));

        List<PostTranslation> newTranslations = Collections.synchronizedList(new ArrayList<>());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (SocialPostDto dto : dtos) {
                if (dto.getContent() == null || dto.getContent().trim().isEmpty()) continue;

                if (cacheMap.containsKey(dto.getId())) {
                    dto.setTranslatedContent(cacheMap.get(dto.getId()));
                    dto.setIsTranslated(true);
                } else {
                    futures.add(executor.submit(() -> {
                        try {
                            String translated = self.translateText(dto.getContent(), targetLanguage);
                            if (translated != null && !translated.equals(dto.getContent())) {
                                dto.setTranslatedContent(translated);
                                dto.setIsTranslated(true);
                                
                                PostTranslation pt = PostTranslation.builder()
                                        .referenceId(dto.getId())
                                        .referenceType("SOCIAL_POST")
                                        .targetLanguage(targetLanguage)
                                        .translatedText(translated)
                                        .build();
                                newTranslations.add(pt);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to translate social post {}: {}", dto.getId(), e.getMessage());
                        }
                    }));
                }
            }
            
            // Wait for tasks to complete or fast timeout (capped to prevent feed stalling)
            long startTime = System.currentTimeMillis();
            for (Future<?> future : futures) {
                long timeLeft = FEED_TRANSLATION_TIMEOUT_MS - (System.currentTimeMillis() - startTime);
                if (timeLeft <= 0) {
                    future.cancel(true);
                    continue;
                }
                try {
                    future.get(timeLeft, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                } catch (Exception e) {
                    log.warn("Translation future failed: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Virtual thread execution failed for social post translations", e);
        }

        // Save all new translations in batch via isolated transaction
        if (!newTranslations.isEmpty()) {
            try {
                self.saveTranslationsInNewTransaction(newTranslations);
            } catch (Exception e) {
                log.warn("[Translation] Failed to save translation batch in isolated transaction: {}", e.getMessage());
            }
        }
    }

    /**
     * Translates a batch of PostResponse (issue posts / official broadcasts).
     */
    public void translatePosts(List<PostResponse> dtos, String targetLanguage) {
        if (dtos == null || dtos.isEmpty() || targetLanguage == null || targetLanguage.equalsIgnoreCase("en")) {
            return;
        }

        List<Long> postIds = dtos.stream().map(PostResponse::getId).collect(Collectors.toList());
        
        // Fetch cached translations
        List<PostTranslation> cachedTranslations = postTranslationRepository
                .findByReferenceIdInAndReferenceTypeAndTargetLanguage(postIds, "POST", targetLanguage);
        
        Map<Long, String> cacheMap = cachedTranslations.stream()
                .collect(Collectors.toMap(PostTranslation::getReferenceId, PostTranslation::getTranslatedText, (a, b) -> a));

        List<PostTranslation> newTranslations = Collections.synchronizedList(new ArrayList<>());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (PostResponse dto : dtos) {
                if (dto.getContent() == null || dto.getContent().trim().isEmpty()) continue;

                if (cacheMap.containsKey(dto.getId())) {
                    dto.setTranslatedContent(cacheMap.get(dto.getId()));
                    dto.setIsTranslated(true);
                } else {
                    futures.add(executor.submit(() -> {
                        try {
                            String translated = self.translateText(dto.getContent(), targetLanguage);
                            if (translated != null && !translated.equals(dto.getContent())) {
                                dto.setTranslatedContent(translated);
                                dto.setIsTranslated(true);
                                
                                PostTranslation pt = PostTranslation.builder()
                                        .referenceId(dto.getId())
                                        .referenceType("POST")
                                        .targetLanguage(targetLanguage)
                                        .translatedText(translated)
                                        .build();
                                newTranslations.add(pt);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to translate broadcast post {}: {}", dto.getId(), e.getMessage());
                        }
                    }));
                }
            }
            
            // Wait for tasks to complete or fast timeout (capped to prevent feed stalling)
            long startTime = System.currentTimeMillis();
            for (Future<?> future : futures) {
                long timeLeft = FEED_TRANSLATION_TIMEOUT_MS - (System.currentTimeMillis() - startTime);
                if (timeLeft <= 0) {
                    future.cancel(true);
                    continue;
                }
                try {
                    future.get(timeLeft, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                } catch (Exception e) {
                    log.warn("Translation future failed: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Virtual thread execution failed for broadcast post translations", e);
        }

        // Save all new translations in batch via isolated transaction
        if (!newTranslations.isEmpty()) {
            try {
                self.saveTranslationsInNewTransaction(newTranslations);
            } catch (Exception e) {
                log.warn("[Translation] Failed to save translation batch in isolated transaction: {}", e.getMessage());
            }
        }
    }

    /**
     * Translates text using the free unofficial Google Translate API (GTX client).
     */
    @Cacheable(value = "translationsApi", key = "#text + '_' + #targetLanguage", unless = "#result == null or #result == #text")
    public String translateText(String text, String targetLanguage) {
        if (text == null || text.trim().isEmpty() || targetLanguage == null || targetLanguage.equalsIgnoreCase("en")) {
            return text;
        }

        try {
            googleApiSemaphore.acquire();
            Thread.sleep(100);

            // 1. Try Lingva API first
            try {
                String url = "https://lingva.ml/api/v1/auto/{target}/{query}";
                String response = restTemplate.getForObject(url, String.class, targetLanguage, text);

                if (response != null) {
                    JsonNode rootNode = objectMapper.readTree(response);
                    if (rootNode.has("translation")) {
                        String translatedText = rootNode.get("translation").asText();
                        if (translatedText != null && !translatedText.trim().isEmpty()) {
                            return translatedText;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Lingva translation failed for language {}, falling back. Error: {}", targetLanguage, e.getMessage());
            }

            // 2. Fallback to Google Translate dict-chrome-ex API (less heavily rate limited than gtx)
            try {
                String url = "https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=auto&tl={target}&dt=t&q={query}";
                String response = restTemplate.getForObject(url, String.class, targetLanguage, text);

                if (response != null) {
                    JsonNode rootNode = objectMapper.readTree(response);
                    if (rootNode.isArray() && rootNode.size() > 0) {
                        JsonNode sentencesNode = rootNode.get(0);
                        StringBuilder translatedStringBuilder = new StringBuilder();

                        if (sentencesNode.isArray()) {
                            for (JsonNode sentenceNode : sentencesNode) {
                                if (sentenceNode.isArray() && sentenceNode.size() > 0) {
                                    JsonNode translatedPartNode = sentenceNode.get(0);
                                    if (translatedPartNode != null && !translatedPartNode.isNull() && translatedPartNode.isTextual()) {
                                        translatedStringBuilder.append(translatedPartNode.asText());
                                    }
                                }
                            }
                            String translatedText = translatedStringBuilder.toString().trim();
                            if (!translatedText.isEmpty()) {
                                return translatedText;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Google dict-chrome-ex translation failed for language {}, falling back. Error: {}", targetLanguage, e.getMessage());
            }

            // 3. Fallback to MyMemory API
            try {
                String url = "https://api.mymemory.translated.net/get?q={query}&langpair=Autodetect|{target}&de=support@example.com";
                String response = restTemplate.getForObject(url, String.class, text, targetLanguage);

                if (response != null) {
                    JsonNode rootNode = objectMapper.readTree(response);
                    if (rootNode.has("responseData") && rootNode.get("responseData").has("translatedText")) {
                        String translatedText = rootNode.get("responseData").get("translatedText").asText();
                        if (translatedText != null && !translatedText.trim().isEmpty() && !translatedText.contains("MYMEMORY WARNING")) {
                            return translatedText;
                        } else {
                            log.warn("MyMemory API returned warning or empty: {}", translatedText);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to translate text to language: {} using fallback. Error: {}", targetLanguage, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Translation interrupted or failed: {}", e.getMessage());
        } finally {
            googleApiSemaphore.release();
        }

        return text;
    }

    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class
    )
    public void saveTranslationsInNewTransaction(List<PostTranslation> translations) {
        if (translations == null || translations.isEmpty()) return;
        try {
            postTranslationRepository.saveAll(translations);
        } catch (Exception e) {
            log.warn("[Translation] Failed to save translation batch in isolated transaction (likely duplicate key/race): {}", e.getMessage());
        }
    }
}
