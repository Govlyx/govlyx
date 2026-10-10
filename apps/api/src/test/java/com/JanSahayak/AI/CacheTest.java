package com.JanSahayak.AI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

@SpringBootTest(classes = com.JanSahayak.AI.config.CacheConfig.class)
class CacheTest {

    @Autowired
    CacheManager cacheManager;

    @Test
    void test() {
        System.out.println("Cache Manager: " + cacheManager);
        System.out.println("authUserDetails cache: " + cacheManager.getCache("authUserDetails"));
    }
}
