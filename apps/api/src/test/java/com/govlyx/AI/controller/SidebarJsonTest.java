package com.Govlyx.AI.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // Disable security filters to bypass JWT
@ActiveProfiles("test")
public class SidebarJsonTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sidebarEndpointHandlesNoAuth() throws Exception {
        // With the fix, missing auth header should return 200 with empty structure (not 400/500)
        mockMvc.perform(get("/api/v1/sidebar")
               .param("tab", "all"))
               .andDo(print())
               .andExpect(status().isOk());
    }

    @Test
    void sidebarEndpointHandlesBadJwt() throws Exception {
        // A bad JWT token should return 200 with empty structure (not 500)
        mockMvc.perform(get("/api/v1/sidebar")
               .header("Authorization", "Bearer this.is.a.garbage.token")
               .param("tab", "all"))
               .andDo(print())
               .andExpect(status().isOk());
    }

    @Test
    void sidebarEndpointHandlesAllTabs() throws Exception {
        // All tab names the frontend sends should return 200
        for (String tab : new String[]{"all", "location", "following", "official", "neighborhood_qa"}) {
            mockMvc.perform(get("/api/v1/sidebar")
                   .header("Authorization", "Bearer bad.token.but.ok")
                   .param("tab", tab))
                   .andExpect(status().isOk());
        }
    }
}
