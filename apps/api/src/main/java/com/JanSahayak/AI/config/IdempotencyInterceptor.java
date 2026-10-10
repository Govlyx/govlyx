package com.JanSahayak.AI.config;

import com.JanSahayak.AI.util.IdempotencyContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String key = request.getHeader("Idempotency-Key");
        if (key != null && !key.trim().isEmpty()) {
            IdempotencyContext.setKey(key);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        IdempotencyContext.clear();
    }
}
