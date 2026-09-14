package com.amrproject.controller.event;

import com.amrproject.controller.PageRequests;
import com.amrproject.dto.response.PageResponse;
import com.amrproject.dto.response.event.AuditLogResponse;
import com.amrproject.service.event.AuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 37단계: 감사 로그 조회(관리자). 최신순 페이지. 조회(GET) 자체는 감사 로그에 남지 않는다. */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> getAll(
            @RequestParam(required = false) String actorUsername,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size
    ) {
        return PageResponse.of(
                auditLogService.search(actorUsername, targetType, targetId, PageRequests.newestFirst(page, size)),
                AuditLogResponse::from
        );
    }
}
