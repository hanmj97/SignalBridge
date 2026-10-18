package dev.signalbridge.api.dto;

import java.util.List;

public record ApiPage<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        long totalPages
) {
    public static <T> ApiPage<T> of(List<T> items, int page, int size, long totalElements) {
        long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
        return new ApiPage<>(items, page, size, totalElements, totalPages);
    }
}
