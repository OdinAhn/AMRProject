package com.amrproject.domain.entity;

public enum TaskStatus {
    QUEUED,
    ASSIGNED,
    RUNNING,
    PAUSED,
    CANCEL_REQUESTED,
    CANCELED,
    RECOVERY_REQUIRED,
    SUCCEEDED,
    FAILED
}
