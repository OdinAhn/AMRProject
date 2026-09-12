package com.amrproject.domain.entity.event;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
