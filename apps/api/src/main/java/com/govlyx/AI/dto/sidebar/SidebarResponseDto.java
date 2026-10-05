package com.Govlyx.AI.dto.sidebar;

import com.Govlyx.AI.dto.PostResponse;
import com.Govlyx.AI.dto.SocialPostDto;
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
