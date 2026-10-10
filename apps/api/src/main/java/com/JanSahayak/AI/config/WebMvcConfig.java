package com.JanSahayak.AI.config;

import com.JanSahayak.AI.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final IdempotencyInterceptor idempotencyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/api/**");

        registry.addInterceptor(rateLimitInterceptor)
                // Apply to every /api/** route ...
                .addPathPatterns("/api/**")
                // ... except monitoring/ops paths that must never be throttled
                .excludePathPatterns(
                        "/api/*/health",    // e.g. /api/notifications/health
                        "/actuator/**"      // Spring Boot Actuator
                );
    }

    @Override
    public void addFormatters(org.springframework.format.FormatterRegistry registry) {
        registry.addConverter(String.class, com.JanSahayak.AI.enums.FeedSort.class, com.JanSahayak.AI.enums.FeedSort::fromString);
    }
}
