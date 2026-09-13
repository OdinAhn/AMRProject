package com.amrproject.controller.alarm;

import com.amrproject.domain.entity.alarm.AlarmSeverity;
import com.amrproject.domain.entity.alarm.AlarmSourceType;
import com.amrproject.domain.entity.alarm.AlarmStatus;
import com.amrproject.domain.entity.user.User;
import com.amrproject.controller.PageRequests;
import com.amrproject.dto.response.PageResponse;
import com.amrproject.dto.response.alarm.AlarmResponse;
import com.amrproject.service.alarm.AlarmService;
import com.amrproject.service.user.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/alarms")
public class AlarmController {

    private final AlarmService alarmService;
    private final UserService userService;

    public AlarmController(AlarmService alarmService, UserService userService) {
        this.alarmService = alarmService;
        this.userService = userService;
    }

    @GetMapping
    /** 31단계: 페이지 응답(최신순). 응답 모양이 배열에서 PageResponse로 바뀌었다(docs/API.md §3). */
    public PageResponse<AlarmResponse> getAll(
            @RequestParam(required = false) AlarmStatus status,
            @RequestParam(required = false) AlarmSeverity severity,
            @RequestParam(required = false) AlarmSourceType sourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size
    ) {
        return PageResponse.of(
                alarmService.getPage(status, severity, sourceType, PageRequests.newestFirst(page, size)),
                AlarmResponse::from
        );
    }

    @GetMapping("/{alarmId}")
    public AlarmResponse getById(@PathVariable Long alarmId) {
        return AlarmResponse.from(alarmService.getById(alarmId));
    }

    @PostMapping("/{alarmId}/acknowledge")
    public AlarmResponse acknowledge(@PathVariable Long alarmId, Authentication authentication) {
        User actor = userService.getByUsername(authentication.getName());
        return AlarmResponse.from(alarmService.acknowledge(alarmId, actor));
    }

    @PostMapping("/{alarmId}/resolve")
    public AlarmResponse resolve(@PathVariable Long alarmId, Authentication authentication) {
        User actor = userService.getByUsername(authentication.getName());
        return AlarmResponse.from(alarmService.resolve(alarmId, actor));
    }
}
