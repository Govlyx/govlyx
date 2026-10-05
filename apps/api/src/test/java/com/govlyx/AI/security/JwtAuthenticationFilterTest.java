package com.Govlyx.AI.security;

import com.Govlyx.AI.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testDoFilterInternal_ValidSessionToken() throws Exception {
        String token = "valid_token";
        String sessionToken = "session-uuid";
        Long userId = 1L;

        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setSessionToken(sessionToken);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(token)).thenReturn(userId);
        when(customUserDetailsService.loadUserById(userId)).thenReturn(mockUser);
        when(jwtUtil.getSessionTokenFromToken(token)).thenReturn(sessionToken);

        // Reflection to call protected method doFilterInternal
        Method method = JwtAuthenticationFilter.class.getDeclaredMethod("doFilterInternal", HttpServletRequest.class, HttpServletResponse.class, FilterChain.class);
        method.setAccessible(true);
        method.invoke(jwtAuthenticationFilter, request, response, filterChain);

        // Verify that authentication is set in context
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    public void testDoFilterInternal_InvalidSessionToken_ThrowsException() throws Exception {
        String token = "valid_token";
        String oldSessionToken = "old-session-uuid";
        String newSessionToken = "new-session-uuid";
        Long userId = 1L;

        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setSessionToken(newSessionToken); // User logged in somewhere else

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(token)).thenReturn(userId);
        when(customUserDetailsService.loadUserById(userId)).thenReturn(mockUser);
        when(jwtUtil.getSessionTokenFromToken(token)).thenReturn(oldSessionToken);

        // Reflection to call protected method doFilterInternal
        Method method = JwtAuthenticationFilter.class.getDeclaredMethod("doFilterInternal", HttpServletRequest.class, HttpServletResponse.class, FilterChain.class);
        method.setAccessible(true);
        method.invoke(jwtAuthenticationFilter, request, response, filterChain);

        // Verify that authentication is NOT set because session tokens don't match
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    public void testDoFilterInternal_NullSessionToken_ThrowsException() throws Exception {
        String token = "valid_token";
        String dbSessionToken = "new-session-uuid";
        Long userId = 1L;

        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setSessionToken(dbSessionToken);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(token)).thenReturn(userId);
        when(customUserDetailsService.loadUserById(userId)).thenReturn(mockUser);
        when(jwtUtil.getSessionTokenFromToken(token)).thenReturn(null); // Missing claim

        // Reflection to call protected method doFilterInternal
        Method method = JwtAuthenticationFilter.class.getDeclaredMethod("doFilterInternal", HttpServletRequest.class, HttpServletResponse.class, FilterChain.class);
        method.setAccessible(true);
        method.invoke(jwtAuthenticationFilter, request, response, filterChain);

        // Verify that authentication is NOT set because session token is null
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
