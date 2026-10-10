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
public class PaginatedResponse<T> {
    private List<T> data;
    private boolean hasMore;
    private Long nextCursor;
    private Integer limit;
    private Integer count;
    private boolean isFallback;

    public static <T> PaginatedResponse<T> of(List<T> data, boolean hasMore, Long nextCursor, Integer limit) {
        return PaginatedResponse.<T>builder()
                .data(data)
                .hasMore(hasMore)
                .nextCursor(nextCursor)
                .limit(limit)
                .count(data.size())
                .isFallback(false)
                .build();
    }

    public static <T> PaginatedResponse<T> of(List<T> data, boolean hasMore, Long nextCursor, Integer limit, boolean isFallback) {
        return PaginatedResponse.<T>builder()
                .data(data)
                .hasMore(hasMore)
                .nextCursor(nextCursor)
                .limit(limit)
                .count(data.size())
                .isFallback(isFallback)
                .build();
    }
}
