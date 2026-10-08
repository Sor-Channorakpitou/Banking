package com.example.bank.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Our own page shape. Returning Spring's Page directly would make the JSON depend
 * on Spring Data internals, which have changed between versions.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
