package com.amrproject.controller.task;

import com.amrproject.dto.response.task.DispatchResultResponse;
import com.amrproject.service.task.DispatchService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 디스패치 사이클 실행 API (docs/API.md §3, 7단계). 자동 배정·자동 충전을 즉시 한 번 수행시킨다.
 * 29단계부터 같은 사이클이 {@link com.amrproject.service.task.AutoDispatchScheduler}로 주기 실행되므로,
 * 이 API는 결과를 바로 확인하거나 자동 실행이 꺼진 환경에서 쓴다.
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
