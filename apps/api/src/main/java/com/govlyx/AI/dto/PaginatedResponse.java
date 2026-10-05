package com.govlyx.AI.dto;

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
    private Long totalElements;
    private Long totalCount;
    private boolean isFallback;

    public static <T> PaginatedResponse<T> of(List<T> data, boolean hasMore, Long nextCursor, Integer limit) {
        long sz = (data != null) ? (long) data.size() : 0L;
        return PaginatedResponse.<T>builder()
                .data(data)
                .hasMore(hasMore)
                .nextCursor(nextCursor)
                .limit(limit)
                .count(data != null ? data.size() : 0)
                .totalElements(sz)
                .totalCount(sz)
                .isFallback(false)
                .build();
    }

    public static <T> PaginatedResponse<T> of(List<T> data, boolean hasMore, Long nextCursor, Integer limit, boolean isFallback) {
        long sz = (data != null) ? (long) data.size() : 0L;
        return PaginatedResponse.<T>builder()
                .data(data)
                .hasMore(hasMore)
                .nextCursor(nextCursor)
                .limit(limit)
                .count(data != null ? data.size() : 0)
                .totalElements(sz)
                .totalCount(sz)
                .isFallback(isFallback)
                .build();
    }

    public static <T> PaginatedResponse<T> of(List<T> data, boolean hasMore, Long nextCursor, Integer limit, Long totalElements) {
        return PaginatedResponse.<T>builder()
                .data(data)
                .hasMore(hasMore)
                .nextCursor(nextCursor)
                .limit(limit)
                .count(data != null ? data.size() : 0)
                .totalElements(totalElements)
                .totalCount(totalElements)
                .isFallback(false)
                .build();
    }
}
