package com.JanSahayak.AI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPostFeedResponse {

    private List<SocialPostDto> posts;
    private Boolean hasMore;
    private Long nextCursor;
    private Integer limit;
    private Integer count;

    // Feed metadata
    private String feedType; // HOME, TRENDING, LOCAL, SAVED
    private String userPincode;
    private String userLocation;

    public static SocialPostFeedResponse fromPaginatedResponse(
            PaginatedResponse<SocialPostDto> paginatedResponse,
            String feedType,
            String userPincode,
            String userLocation) {

        return SocialPostFeedResponse.builder()
                .posts(paginatedResponse.getData())
                .hasMore(paginatedResponse.isHasMore())
                .nextCursor(paginatedResponse.getNextCursor())
                .limit(paginatedResponse.getLimit())
                .count(paginatedResponse.getCount())
                .feedType(feedType)
                .userPincode(userPincode)
                .userLocation(userLocation)
                .build();
    }
}
