package com.Govlyx.AI.controller;

import com.Govlyx.AI.dto.sidebar.SidebarResponseDto;
import com.Govlyx.AI.service.SidebarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SidebarControllerTest {

    @Mock
    private SidebarService sidebarService;

    @InjectMocks
    private SidebarController sidebarController;

    private SidebarResponseDto mockResponse;

    @BeforeEach
    void setUp() {
        mockResponse = SidebarResponseDto.builder()
                .activeTab("FOR-YOU")
                .build();
    }

    @Test
    void testGetSidebar() {
        // Arrange
        String token = "Bearer sample_token";
        String extractedToken = "sample_token";
        String tab = "FOR-YOU";

        when(sidebarService.getSidebarData(eq(extractedToken), eq(tab))).thenReturn(mockResponse);

        // Act
        ResponseEntity<SidebarResponseDto> responseEntity = sidebarController.getSidebar(token, tab);

        // Assert
        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCodeValue());
        assertEquals(mockResponse, responseEntity.getBody());
    }

    @Test
    void testGetSidebar_NoBearerPrefix() {
        // Arrange
        String token = "sample_token_no_bearer";
        String tab = "LOCATION";
        SidebarResponseDto locationResponse = SidebarResponseDto.builder().activeTab(tab).build();

        when(sidebarService.getSidebarData(eq(token), eq(tab))).thenReturn(locationResponse);

        // Act
        ResponseEntity<SidebarResponseDto> responseEntity = sidebarController.getSidebar(token, tab);

        // Assert
        assertEquals(200, responseEntity.getStatusCodeValue());
        assertEquals(locationResponse, responseEntity.getBody());
    }
}
