package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.CacheConfig;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.PincodeLookup;
import com.JanSahayak.AI.repository.PincodeLookupRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {CacheConfig.class, PinCodeLookupService.class})
public class PinCodeLookupServiceTest {

    @MockBean
    private PincodeLookupRepo pincodeLookupRepo;

    @Autowired
    private PinCodeLookupService pinCodeLookupService;

    @Autowired
    private CacheManager cacheManager;

    private PincodeLookup punePincode;

    @BeforeEach
    void setUp() {
        punePincode = new PincodeLookup();
        punePincode.setPincode("411001");
        punePincode.setAreaName("Shivajinagar");
        punePincode.setCity("Pune");
        punePincode.setDistrict("Pune");
        punePincode.setState("Maharashtra");
        punePincode.setIsActive(true);

        // Clear cache before each test
        var cache = cacheManager.getCache(Constant.CACHE_PINCODE_DATA);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void testFindByPincode_ValidAndFound_ShouldCacheResultWithoutSpelException() {
        when(pincodeLookupRepo.findById("411001")).thenReturn(Optional.of(punePincode));

        // First call - should query repo and cache result
        Optional<PincodeLookup> result1 = pinCodeLookupService.findByPincode("411001");
        assertTrue(result1.isPresent());
        assertEquals("Pune", result1.get().getCity());

        // Second call - should be served from cache without querying repo again
        Optional<PincodeLookup> result2 = pinCodeLookupService.findByPincode("411001");
        assertTrue(result2.isPresent());
        assertEquals("Pune", result2.get().getCity());

        verify(pincodeLookupRepo, times(1)).findById("411001");
    }

    @Test
    void testFindByPincode_NotFound_ShouldReturnEmptyAndNotThrowSpelException() {
        when(pincodeLookupRepo.findById("411099")).thenReturn(Optional.empty());

        // Call for non-existent pincode - should return Optional.empty() without throwing SpEL exception
        Optional<PincodeLookup> result = pinCodeLookupService.findByPincode("411099");
        assertTrue(result.isEmpty());

        verify(pincodeLookupRepo, times(1)).findById("411099");
    }

    @Test
    void testFindByPincode_InvalidFormat_ShouldReturnEmptyWithoutCallingRepo() {
        // Pincode with letters or wrong length
        Optional<PincodeLookup> result1 = pinCodeLookupService.findByPincode("ABC");
        assertTrue(result1.isEmpty());

        Optional<PincodeLookup> result2 = pinCodeLookupService.findByPincode("12345");
        assertTrue(result2.isEmpty());

        Optional<PincodeLookup> result3 = pinCodeLookupService.findByPincode(null);
        assertTrue(result3.isEmpty());

        verify(pincodeLookupRepo, never()).findById(anyString());
    }
}
