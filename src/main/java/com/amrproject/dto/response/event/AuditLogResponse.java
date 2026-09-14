package com.amrproject.dto.response.event;

import com.amrproject.domain.entity.event.AuditLog;

import java.time.Instant;

/** 37단계: 감사 로그 항목. payload는 저장된 JSON 문자열 그대로(경로 변수·쿼리·createdId, 로그인이면 사유 등). */
public record AuditLogResponse(
        Long id,
        Instant createdAt,
        Long actorUserId,
        String actorUsername,
        String action,
        String targetType,
        Long targetId,
        Integer httpStatus,
        String payload,
        String ipAddress
) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getCreatedAt(),
                log.getActorUserId(),
                log.getActorUsername(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getHttpStatus(),
                log.getPayloadJson(),
                log.getIpAddress()
        );
    }
}
