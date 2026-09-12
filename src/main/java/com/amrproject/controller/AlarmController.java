package com.amrproject.controller;

import com.amrproject.domain.entity.AlarmSeverity;
import com.amrproject.domain.entity.AlarmSourceType;
import com.amrproject.domain.entity.AlarmStatus;
import com.amrproject.domain.entity.User;
import com.amrproject.dto.response.AlarmResponse;
import com.amrproject.service.AlarmService;
import com.amrproject.service.UserService;
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
    public List<AlarmResponse> getAll(
            @RequestParam(required = false) AlarmStatus status,
            @RequestParam(required = false) AlarmSeverity severity,
            @RequestParam(required = false) AlarmSourceType sourceType
    ) {
        return alarmService.getAll(status, severity, sourceType).stream().map(AlarmResponse::from).toList();
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
