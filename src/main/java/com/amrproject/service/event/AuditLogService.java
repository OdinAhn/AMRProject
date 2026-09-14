package com.amrproject.service.event;

import com.amrproject.domain.entity.event.AuditLog;
import com.amrproject.repository.event.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * 37단계: 감사 로그 기록·조회.
 *
 * <p>기록은 항상 별도 트랜잭션(REQUIRES_NEW)이다. 로그인 실패처럼 호출한 쪽 트랜잭션이 예외로 롤백돼도(또는 읽기 전용이어도)
 * 감사 기록은 남아야 하기 때문이다. 기록에 실패해도 원래 요청을 실패시키지 않고 오류 로그만 남긴다 — 감사 로그 저장소 문제로
 * 비상정지 같은 운영 동작이 막히면 안 된다.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final JsonMapper jsonMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, JsonMapper jsonMapper) {
        this.auditLogRepository = auditLogRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEntry entry) {
        try {
            String payload = entry.payload() == null || entry.payload().isEmpty() ? null : jsonMapper.writeValueAsString(entry.payload());
            auditLogRepository.save(new AuditLog(
                    entry.actorUserId(),
                    entry.actorUsername(),
                    entry.action(),
                    entry.targetType(),
                    entry.targetId(),
                    entry.httpStatus(),
                    payload,
                    entry.ipAddress()
            ));
        } catch (RuntimeException e) {
            log.error("감사 로그 기록 실패: action={} actor={} target={}#{}", entry.action(), entry.actorUsername(),
                    entry.targetType(), entry.targetId(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String actorUsername, String targetType, Long targetId, Pageable pageable) {
        return auditLogRepository.search(blankToNull(actorUsername), blankToNull(targetType), targetId, pageable);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** payload는 JSON으로 저장할 부가 정보 — 요청 본문(비밀번호가 들어갈 수 있음)은 절대 넣지 않는다. */
    public record AuditEntry(
            Long actorUserId,
            String actorUsername,
            String action,
            String targetType,
            Long targetId,
            Integer httpStatus,
            Map<String, Object> payload,
            String ipAddress
    ) {
    }
}
