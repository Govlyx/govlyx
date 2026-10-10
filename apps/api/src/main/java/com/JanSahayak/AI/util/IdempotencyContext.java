package com.JanSahayak.AI.util;

public class IdempotencyContext {
    private static final ThreadLocal<String> IDEMPOTENCY_KEY = new ThreadLocal<>();

    public static void setKey(String key) {
        IDEMPOTENCY_KEY.set(key);
    }

    public static String getKey() {
        return IDEMPOTENCY_KEY.get();
    }

    public static void clear() {
        IDEMPOTENCY_KEY.remove();
    }
}
