package com.amrproject.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 31단계: 목록 API의 page/size 요청 파라미터를 Pageable로 바꾼다. 음수 페이지는 0으로, size는 1~{@link #MAX_SIZE}로 맞춘다 —
 * 잘못된 값으로 400을 내기보다 안전한 범위로 고정해 화면이 항상 결과를 받게 한다.
 */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    /** 최신순(생성 시각 내림차순, 같으면 id 내림차순) 정렬. */
    public static Pageable newestFirst(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }
}
