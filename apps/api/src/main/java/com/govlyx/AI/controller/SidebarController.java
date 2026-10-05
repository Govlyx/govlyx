package com.govlyx.AI.controller;

import com.govlyx.AI.dto.sidebar.SidebarResponseDto;
import com.govlyx.AI.service.SidebarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sidebar")
@RequiredArgsConstructor
public class SidebarController {

    private final SidebarService sidebarService;

    @GetMapping
    public ResponseEntity<SidebarResponseDto> getSidebar(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "all") String tab) {

        if (token == null || token.isBlank()) {
            // No token — return empty sidebar structure instead of 401/500
            return ResponseEntity.ok(SidebarResponseDto.builder().activeTab(tab).build());
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        SidebarResponseDto sidebarData = sidebarService.getSidebarData(token, tab);
        return ResponseEntity.ok(sidebarData);
    }
}
