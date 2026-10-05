package com.govlyx.AI.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = CacheConfig.class)
public class CacheConfigTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    public void testCacheManagerInitializesCachesProperly() {
        assertNotNull(cacheManager, "CacheManager should be loaded in the context");

        // Verify that caches were properly initialized by afterPropertiesSet()
        assertNotNull(cacheManager.getCache("authUserDetails"), "authUserDetails cache should exist");
        assertNotNull(cacheManager.getCache("user-profiles"), "user-profiles cache should exist");
        assertNotNull(cacheManager.getCache("community-list"), "community-list cache should exist");
        assertNotNull(cacheManager.getCache("pincode-data"), "pincode-data cache should exist");
        assertNotNull(cacheManager.getCache("pincode_nearby"), "pincode_nearby cache should exist");
        assertNotNull(cacheManager.getCache("hlig_feed"), "hlig_feed cache should exist");
    }
}
