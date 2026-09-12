-- Stage 7: 다중 로봇 운영 (자동 배정, station 단위 자원 예약, 충전)
-- 참고: docs/ERD.md §4.7

ALTER TABLE stations
    ADD COLUMN max_concurrent_robots INT;

CREATE TABLE resource_reservations (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_type  VARCHAR(20) NOT NULL,
    resource_id    BIGINT      NOT NULL,
    robot_id       BIGINT      NOT NULL,
    task_step_id   BIGINT,
    status         VARCHAR(20) NOT NULL,
    reserved_at    DATETIME(6) NOT NULL,
    released_at    DATETIME(6),
    expires_at     DATETIME(6),
    CONSTRAINT fk_resource_reservations_robot FOREIGN KEY (robot_id) REFERENCES robots (id),
    CONSTRAINT fk_resource_reservations_task_step FOREIGN KEY (task_step_id) REFERENCES task_steps (id)
) ENGINE = InnoDB;

CREATE TABLE charging_sessions (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    robot_id           BIGINT      NOT NULL,
    station_id         BIGINT      NOT NULL,
    task_id            BIGINT,
    status             VARCHAR(20) NOT NULL,
    battery_start_pct  INT         NOT NULL,
    battery_end_pct    INT,
    started_at         DATETIME(6),
    completed_at       DATETIME(6),
    CONSTRAINT fk_charging_sessions_robot FOREIGN KEY (robot_id) REFERENCES robots (id),
    CONSTRAINT fk_charging_sessions_station FOREIGN KEY (station_id) REFERENCES stations (id),
    CONSTRAINT fk_charging_sessions_task FOREIGN KEY (task_id) REFERENCES tasks (id)
) ENGINE = InnoDB;
