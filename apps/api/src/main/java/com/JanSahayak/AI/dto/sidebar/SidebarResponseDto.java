package com.JanSahayak.AI.dto.sidebar;

import com.JanSahayak.AI.dto.PostResponse;
import com.JanSahayak.AI.dto.SocialPostDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SidebarResponseDto {
    private String activeTab;
    private AreaPulseDto areaPulse;
    private List<SocialPostDto> unansweredQuestions;
    private PostResponse topUnresolvedIssue;
    private PostResponse latestOfficialAlert;
}
