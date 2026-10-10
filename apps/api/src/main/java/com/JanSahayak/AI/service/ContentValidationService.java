package com.JanSahayak.AI.service;

import com.JanSahayak.AI.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentValidationService {

    private final BadWordService badWordService;

    /**
     * Sanitizes HTML content and validates for bad words before allowing post/comment creation.
     *
     * @param content Text content to validate
     * @return Sanitized text content (HTML escaped)
     * @throws ValidationException if bad word is found
     */
    public String sanitizeAndValidateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content; // Empty content validation handled elsewhere
        }

        // 1. Sanitize HTML to prevent Stored XSS
        String sanitizedContent = org.springframework.web.util.HtmlUtils.htmlEscape(content);

        // 2. Validate for bad words
        BadWordService.BadWordCheckResult result = badWordService.checkText(sanitizedContent);

        if (!result.isAllowed()) {
            log.warn("Content validation failed - bad word detected: {}", result.getBadWord());
            throw new ValidationException(result.getMessage());
        }

        log.debug("Content validation passed - no bad words found");
        return sanitizedContent;
    }

    public boolean isContentClean(String content) {
        try {
            sanitizeAndValidateContent(content);
            return true;
        } catch (ValidationException e) {
            return false;
        }
    }

    public BadWordService.BadWordCheckResult checkContent(String content) {
        return badWordService.checkText(content);
    }
}
