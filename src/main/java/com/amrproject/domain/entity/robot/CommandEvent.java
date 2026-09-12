package com.amrproject.domain.entity.robot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "command_events")
public class CommandEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "command_id", nullable = false)
    private RobotCommand command;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected CommandEvent() {
    }

    public CommandEvent(RobotCommand command, String eventType, String payloadJson, Instant occurredAt, Instant receivedAt) {
        this.command = command;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() {
        return id;
    }

    public RobotCommand getCommand() {
        return command;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
