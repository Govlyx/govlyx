package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.PostResponse;
import com.JanSahayak.AI.dto.sidebar.AreaPulseDto;
import com.JanSahayak.AI.dto.sidebar.SidebarResponseDto;
import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SidebarServiceTest {

    @Mock
    private PostRepo postRepo;

    @Mock
    private SocialPostRepo socialPostRepo;

    @Mock
    private PostService postService;

    @Mock
    private SocialPostService socialPostService;

    @Mock
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private SidebarService sidebarService;

    private User mockUser;
    private final String MOCK_TOKEN = "Bearer mock_token";
    private final String MOCK_USERNAME = "testuser";
    private final String MOCK_PINCODE = "110001";

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setUsername(MOCK_USERNAME);
        mockUser.setPincode(MOCK_PINCODE);
    }

    @Test
    void testGetSidebarData_ForYouTab() {
        // Arrange
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn(MOCK_USERNAME);
        when(userService.findByUsername(MOCK_USERNAME)).thenReturn(mockUser);
        
        when(postRepo.countIssuesByDepartmentTagAndPincode(
                eq(MOCK_PINCODE), any())).thenReturn(10L);
        when(postRepo.countResolvedIssuesByDepartmentTagAndPincode(
                eq(MOCK_PINCODE), eq(true), any())).thenReturn(5L);
        when(socialPostRepo.countByCategoryAndPincodeInAndCommentCountAndStatus(
                any(), anyList(), eq(0), any())).thenReturn(3L);

        when(postRepo.findTopUnresolvedIssueByDepartmentTagAndPincode(any(), eq(false), any(), any()))
                .thenReturn(Collections.emptyList());
        when(socialPostRepo.findTopQAPostsByPincodes(any(), anyList(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        SidebarResponseDto result = sidebarService.getSidebarData(MOCK_TOKEN, "FOR-YOU");

        // Assert
        assertNotNull(result);
        assertEquals("FOR-YOU", result.getActiveTab());
        
        AreaPulseDto areaPulse = result.getAreaPulse();
        assertNotNull(areaPulse);
        assertEquals(10L, areaPulse.getTotalIssuesThisWeek());
        assertEquals(5L, areaPulse.getResolvedIssuesThisWeek());
        assertEquals(3L, areaPulse.getUnansweredQuestions());

        assertNull(result.getLatestOfficialAlert());
        // For-you tab calls top QA posts and top unresolved issue
        verify(socialPostRepo, times(1)).findTopQAPostsByPincodes(any(), anyList(), any(), any());
        verify(postRepo, times(1)).findTopUnresolvedIssueByDepartmentTagAndPincode(any(), eq(false), any(), any());
    }

    @Test
    void testGetSidebarData_UserNotFound() {
        // Arrange
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn(MOCK_USERNAME);
        when(userService.findByUsername(MOCK_USERNAME)).thenReturn(null);

        // Act
        SidebarResponseDto result = sidebarService.getSidebarData(MOCK_TOKEN, "FOR-YOU");

        // Assert
        assertNotNull(result);
        assertEquals("FOR-YOU", result.getActiveTab());
        assertNull(result.getAreaPulse());
        assertNull(result.getTopUnresolvedIssue());
        assertNull(result.getUnansweredQuestions());
        assertNull(result.getLatestOfficialAlert());
    }


    @Test
    void testGetSidebarData_FallbackTab() {
        // Arrange
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn(MOCK_USERNAME);
        when(userService.findByUsername(MOCK_USERNAME)).thenReturn(mockUser);

        when(postRepo.countIssuesByDepartmentTagAndPincode(
                any(), any())).thenReturn(0L);
        when(postRepo.countResolvedIssuesByDepartmentTagAndPincode(
                any(), anyBoolean(), any())).thenReturn(0L);
        when(socialPostRepo.countByCategoryAndPincodeInAndCommentCountAndStatus(
                any(), anyList(), anyInt(), any())).thenReturn(0L);

        // Act
        SidebarResponseDto result = sidebarService.getSidebarData(MOCK_TOKEN, "UNKNOWN-TAB");

        // Assert
        assertEquals("UNKNOWN-TAB", result.getActiveTab());
        verify(socialPostRepo, times(1)).findTopQAPostsByPincodes(any(), anyList(), any(), any());
        verify(postRepo, times(1)).findTopUnresolvedIssueByDepartmentTagAndPincode(any(), eq(false), any(), any());
    }
}
