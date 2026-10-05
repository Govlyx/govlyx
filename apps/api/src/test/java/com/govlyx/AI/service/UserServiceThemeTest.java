package com.Govlyx.AI.service;

import com.Govlyx.AI.exception.ServiceException;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.Govlyx.AI.config.Constant;
import com.Govlyx.AI.repository.RoleRepo;
import com.Govlyx.AI.repository.UserPassRepository;
import com.Govlyx.AI.repository.UserTagRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;

@ExtendWith(MockitoExtension.class)
public class UserServiceThemeTest {

    @Mock
    private UserRepo userRepository;

    @Mock
    private RoleRepo roleRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserTagRepo userTagRepository;

    @Mock
    private PostInteractionService postInteractionService;

    @Mock
    private UserPassRepository userPassRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache authCache;

    @Mock
    private Cache profileCache;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("testuser@example.com");
        testUser.setTheme("light");
    }

    @Test
    void testUpdateTheme_Success() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("authUserDetails")).thenReturn(authCache);
        when(cacheManager.getCache(Constant.CACHE_USER_PROFILE)).thenReturn(profileCache);

        // Act
        userService.updateTheme(1L, "dark");

        // Assert
        assertEquals("dark", testUser.getTheme());
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(testUser);
        verify(authCache, times(1)).evict(1L);
        verify(profileCache, times(1)).evict("testuser@example.com");
    }

    @Test
    void testUpdateTheme_UserNotFound() {
        // Arrange
        when(userRepository.findById(2L)).thenThrow(new com.Govlyx.AI.exception.UserNotFoundException("User not found with ID: 2"));

        // Act & Assert
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            userService.updateTheme(2L, "dark");
        });

        assertTrue(exception.getMessage().contains("Failed to update theme"));
        verify(userRepository, times(1)).findById(2L);
        verify(userRepository, never()).save(any());
    }

    @Test
    void testUpdateTheme_NullUserEmail() {
        // Arrange: User with null email
        testUser.setEmail(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("authUserDetails")).thenReturn(authCache);

        // Act
        userService.updateTheme(1L, "dark");

        // Assert: evicts authCache, does not throw NPE for profileCache
        assertEquals("dark", testUser.getTheme());
        verify(authCache, times(1)).evict(1L);
        verify(profileCache, never()).evict(any());
    }

    @Test
    void testUpdateTheme_NullCacheEntries() {
        // Arrange: CacheManager returns null for caches
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("authUserDetails")).thenReturn(null);
        when(cacheManager.getCache(Constant.CACHE_USER_PROFILE)).thenReturn(null);

        // Act & Assert: should not throw NPE
        assertDoesNotThrow(() -> userService.updateTheme(1L, "light"));
        assertEquals("light", testUser.getTheme());
    }

    @Test
    void testUpdateTheme_RepositorySaveThrowsException() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("DB write failure"));

        // Act & Assert
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            userService.updateTheme(1L, "dark");
        });

        assertTrue(exception.getMessage().contains("Failed to update theme"));
    }
}
