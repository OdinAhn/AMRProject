package com.amrproject.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 31단계: 목록 API 공통 페이지 응답. Spring의 {@link Page}를 그대로 직렬화하면 내부 구현 필드(pageable, sort 등)가
 * 노출되고 버전에 따라 모양이 바뀌므로, 화면에 필요한 값만 담은 DTO로 감싼다(AGENTS.md: 내부 구현을 응답에 노출하지 않음).
 *
 * @param page 0부터 시작하는 페이지 번호
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
