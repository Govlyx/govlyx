package com.JanSahayak.AI.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final ClientIpResolver   ipResolver;
    private final ObjectMapper       objectMapper;   // Spring auto-configures this bean

    // ── HandlerInterceptor ─────────────────────────────────────────────────────

    /**
     * Runs BEFORE the controller method.
     *
     * @return {@code true}  → continue to controller
     *         {@code false} → short-circuit; 429 already written to response
     */
    @Override
    public boolean preHandle(HttpServletRequest  request,
                             HttpServletResponse response,
                             Object              handler) throws IOException {

        String key = resolveKey(request);

        // Snapshot current count BEFORE consuming (for accurate Remaining header)
        int usedBefore = rateLimiterService.currentCount(key);
        int remaining  = Math.max(0, RateLimiterService.MAX_REQUESTS - usedBefore - 1);

        // Attach informational headers on every response (RFC 6585 / common convention)
        response.setIntHeader("X-RateLimit-Limit",     RateLimiterService.MAX_REQUESTS);
        response.setIntHeader("X-RateLimit-Remaining", remaining);
        response.setIntHeader("X-RateLimit-Window",
                (int) (RateLimiterService.WINDOW_SIZE_MS / 1000));

        // Attempt to consume one token from the sliding window
        if (!rateLimiterService.tryConsume(key)) {
            log.warn("[RateLimit] BLOCKED key='{}' method={} uri='{}'",
                    key, request.getMethod(), request.getRequestURI());
            writeTooManyRequestsResponse(response);
            return false;   // do NOT invoke the controller
        }

        log.trace("[RateLimit] ALLOWED key='{}' remaining={}", key, remaining);
        return true;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Resolves the bucket key for the current request.
     *
     * Prefers the authenticated principal name (stable across IP changes).
     * Falls back to client IP for anonymous/public endpoints.
     */
    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            // auth.getName() returns the email/username used as the principal
            return "user:" + auth.getName();
        }

        return "ip:" + ipResolver.resolve(request);
    }

    /**
     * Writes a RFC-6585-compliant HTTP 429 response with a JSON body that
     * matches the project's existing {@code ApiResponse} envelope style.
     *
     * Uses Jackson ObjectMapper (already a Spring-managed bean) to ensure
     * correct serialization without manual string formatting risks.
     */
    private void writeTooManyRequestsResponse(HttpServletResponse response) throws IOException {
        int retryAfter = (int) (RateLimiterService.WINDOW_SIZE_MS / 1000);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setIntHeader("Retry-After", retryAfter);

        // Build response body as a Map so Jackson handles all escaping correctly
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",            HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error",             "Too Many Requests");
        body.put("message",           String.format(
                "Rate limit exceeded. Maximum %d requests per minute allowed.",
                RateLimiterService.MAX_REQUESTS));
        body.put("retryAfterSeconds", retryAfter);
        body.put("timestamp",         Instant.now().toString());
        body.put("toastMessage",      com.JanSahayak.AI.exception.ToastMessages.TOO_MANY_REQUESTS);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
