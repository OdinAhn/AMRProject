package com.amrproject.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/** 알람 상태 전이 감사 로그 (docs/ERD.md §4.10 alarm_events) — task_events와 동일한 append-only 패턴. */
@Entity
@Table(name = "alarm_events")
@EntityListeners(AuditingEntityListener.class)
public class AlarmEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alarm_id", nullable = false)
    private Alarm alarm;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AlarmEvent() {
    }

    public AlarmEvent(Alarm alarm, String eventType, String note, User createdBy) {
        this.alarm = alarm;
        this.eventType = eventType;
        this.note = note;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Alarm getAlarm() {
        return alarm;
    }

    public String getEventType() {
        return eventType;
    }

    public String getNote() {
        return note;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
