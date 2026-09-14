package com.amrproject.domain.entity.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 37단계: 감사 로그(docs/ERD.md §4.10) — append-only. 행위자는 User 연관 대신 id와 이름을 값으로 함께 남긴다:
 * 없는 아이디로 로그인 실패했거나 외부 시스템(API 키)처럼 사용자 행이 없는 경우도 이름은 기록해야 하기 때문이다.
 */
@Entity
@Table(name = "audit_logs")
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Column(nullable = false, length = 150)
    private String action;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(
            Long actorUserId,
            String actorUsername,
            String action,
            String targetType,
            Long targetId,
            Integer httpStatus,
            String payloadJson,
            String ipAddress
    ) {
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.httpStatus = httpStatus;
        this.payloadJson = payloadJson;
        this.ipAddress = ipAddress;
    }

    public Long getId() {
        return id;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
