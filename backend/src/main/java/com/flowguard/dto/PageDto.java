package com.flowguard.dto;

/**
 * Page wrapper for paginated admin responses.
 */
public record PageDto<T>(
    java.util.List<T> content,
    long totalElements,
    int  totalPages,
    int  page,
    int  size
) {
    public static <T> PageDto<T> of(java.util.List<T> content, long total, int page, int size) {
        int pages = size == 0 ? 1 : (int) Math.ceil((double) total / size);
        return new PageDto<>(content, total, pages, page, size);
    }
}
