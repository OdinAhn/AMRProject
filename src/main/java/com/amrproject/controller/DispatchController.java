package com.amrproject.controller;

import com.amrproject.dto.response.task.DispatchResultResponse;
import com.amrproject.service.DispatchService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 디스패치 사이클 실행 API (docs/API.md §3, 7단계). 스케줄러가 없어
 * 관리자가 필요할 때 호출해 자동 배정·자동 충전을 한 번 수행시킨다.
 */
@RestController
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping("/api/admin/sites/{siteId}/dispatch")
    public DispatchResultResponse dispatch(@PathVariable Long siteId) {
        return dispatchService.runCycle(siteId);
    }
}
