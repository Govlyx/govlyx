package com.Govlyx.AI.service;

import com.Govlyx.AI.config.Constant;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceCacheTest {

    @Mock
    private UserRepo userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PostInteractionService postInteractionService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache profileCache;

    @Mock
    private Cache authCache;

    @InjectMocks
    private UserService userService;

    private User user;
    private User admin;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(2L);
        // Assuming role setup might be needed if isAdmin check relies on it, 
        // but for this unit test, let's just make the user deactivate themselves
        
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setIsActive(true);
    }

    @Test
    void deactivateUser_shouldEvictProfileCacheWithOriginalEmail() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cacheManager.getCache(Constant.CACHE_USER_PROFILE)).thenReturn(profileCache);
        
        // Let's assume user deactivates themselves
        // Act
        userService.deactivateUser(1L, user);

        // Assert
        // We verify that the profileCache is evicted with the ORIGINAL email
        verify(profileCache, times(1)).evict("test@example.com");
        
        // Also verify the user is saved (to confirm successful flow)
        verify(userRepository, times(1)).save(user);
    }
}
