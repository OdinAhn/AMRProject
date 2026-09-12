-- Stage 14: 알람 (docs/ERD.md §4.10, docs/STATE_MACHINES.md §7)

CREATE TABLE alarms (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    alarm_type      VARCHAR(30)  NOT NULL,
    severity        VARCHAR(20)  NOT NULL,
    source_type     VARCHAR(20)  NOT NULL,
    source_id       BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    message         VARCHAR(500),
    acknowledged_at DATETIME(6),
    acknowledged_by BIGINT,
    resolved_at     DATETIME(6),
    resolved_by     BIGINT,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_alarms_acknowledged_by FOREIGN KEY (acknowledged_by) REFERENCES users (id),
    CONSTRAINT fk_alarms_resolved_by FOREIGN KEY (resolved_by) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX idx_alarms_source ON alarms (alarm_type, source_type, source_id, status);

CREATE TABLE alarm_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    alarm_id    BIGINT      NOT NULL,
    event_type  VARCHAR(20) NOT NULL,
    note        VARCHAR(500),
    created_by  BIGINT,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_alarm_events_alarm FOREIGN KEY (alarm_id) REFERENCES alarms (id),
    CONSTRAINT fk_alarm_events_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;
