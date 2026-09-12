package com.amrproject.domain.entity;

import com.amrproject.exception.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 14단계: docs/ERD.md §4.10, docs/STATE_MACHINES.md §7의 알람 상태(OPEN/ACKNOWLEDGED/RESOLVED)를 구현한다.
 * 같은 (alarmType, sourceType, sourceId) 조합의 열린 알람은 최대 1개 — AlarmService가 보장한다.
 */
@Entity
@Table(name = "alarms")
public class Alarm extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alarm_type", nullable = false, length = 30)
    private AlarmType alarmType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private AlarmSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmStatus status;

    @Column(length = 500)
    private String message;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    protected Alarm() {
    }

    public Alarm(AlarmType alarmType, AlarmSeverity severity, AlarmSourceType sourceType, Long sourceId, String message) {
        this.alarmType = alarmType;
        this.severity = severity;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.message = message;
        this.status = AlarmStatus.OPEN;
    }

    public void acknowledge(User actor) {
        requireStatus(AlarmStatus.OPEN, "ACKNOWLEDGE");
        this.status = AlarmStatus.ACKNOWLEDGED;
        this.acknowledgedAt = Instant.now();
        this.acknowledgedBy = actor;
    }

    public void resolve(User actor) {
        if (status != AlarmStatus.OPEN && status != AlarmStatus.ACKNOWLEDGED) {
            throw new InvalidStateTransitionException(
                    "ALARM_RESOLVE_NOT_ALLOWED",
                    "현재 상태(" + status + ")에서는 해제할 수 없습니다."
            );
        }
        this.status = AlarmStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.resolvedBy = actor;
    }

    /** 시스템이 원인 소멸을 감지했을 때의 자동 해소 — 별도 조치자가 없다. */
    public void autoResolve() {
        if (status != AlarmStatus.OPEN && status != AlarmStatus.ACKNOWLEDGED) {
            return;
        }
        this.status = AlarmStatus.RESOLVED;
        this.resolvedAt = Instant.now();
    }

    private void requireStatus(AlarmStatus required, String action) {
        if (status != required) {
            throw new InvalidStateTransitionException(
                    "ALARM_" + action + "_NOT_ALLOWED",
                    "알람 상태가 " + required + "가 아니어서(현재: " + status + ") " + action + "을(를) 수행할 수 없습니다."
            );
        }
    }

    public Long getId() {
        return id;
    }

    public AlarmType getAlarmType() {
        return alarmType;
    }

    public AlarmSeverity getSeverity() {
        return severity;
    }

    public AlarmSourceType getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public AlarmStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public User getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public User getResolvedBy() {
        return resolvedBy;
    }
}
