-- Stage 3: 작업·명령 기반 (작업 단계, 상태 전이, 멱등성, 트랜잭션, Outbox)
-- 참고: docs/ERD.md §4.5~4.6, §4.9. target_node_id는 지도 스키마(6단계)까지 보류.
-- tasks.current_assignment_id ↔ task_assignments.task_id는 순환 참조이므로
-- tasks를 FK 없이 먼저 만들고, task_assignments 생성 후 ALTER로 FK를 추가한다.

CREATE TABLE tasks (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no                 VARCHAR(50)  NOT NULL,
    site_id                 BIGINT       NOT NULL,
    task_type               VARCHAR(20)  NOT NULL,
    priority                INT          NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    origin_station_id       BIGINT       NOT NULL,
    destination_station_id  BIGINT       NOT NULL,
    requested_by            BIGINT,
    idempotency_key         VARCHAR(100),
    external_ref            VARCHAR(100),
    current_assignment_id   BIGINT,
    completed_at            DATETIME(6),
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    CONSTRAINT uk_tasks_task_no UNIQUE (task_no),
    CONSTRAINT uk_tasks_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_tasks_site FOREIGN KEY (site_id) REFERENCES sites (id),
    CONSTRAINT fk_tasks_origin_station FOREIGN KEY (origin_station_id) REFERENCES stations (id),
    CONSTRAINT fk_tasks_destination_station FOREIGN KEY (destination_station_id) REFERENCES stations (id),
    CONSTRAINT fk_tasks_requested_by FOREIGN KEY (requested_by) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE task_steps (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id      BIGINT      NOT NULL,
    step_no      INT         NOT NULL,
    step_type    VARCHAR(30) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    started_at   DATETIME(6),
    completed_at DATETIME(6),
    CONSTRAINT uk_task_steps_task_step UNIQUE (task_id, step_no),
    CONSTRAINT fk_task_steps_task FOREIGN KEY (task_id) REFERENCES tasks (id)
) ENGINE = InnoDB;

CREATE TABLE task_assignments (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id      BIGINT      NOT NULL,
    robot_id     BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    assigned_at  DATETIME(6) NOT NULL,
    released_at  DATETIME(6),
    CONSTRAINT fk_task_assignments_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_assignments_robot FOREIGN KEY (robot_id) REFERENCES robots (id)
) ENGINE = InnoDB;

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_current_assignment FOREIGN KEY (current_assignment_id) REFERENCES task_assignments (id);

CREATE TABLE task_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT      NOT NULL,
    event_type  VARCHAR(50) NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20),
    created_by  BIGINT,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_task_events_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_events_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE robot_commands (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_no    VARCHAR(50) NOT NULL,
    task_step_id  BIGINT,
    robot_id      BIGINT      NOT NULL,
    command_type  VARCHAR(20) NOT NULL,
    payload_json  TEXT,
    status        VARCHAR(20) NOT NULL,
    sent_at       DATETIME(6),
    accepted_at   DATETIME(6),
    completed_at  DATETIME(6),
    CONSTRAINT uk_robot_commands_command_no UNIQUE (command_no),
    CONSTRAINT fk_robot_commands_task_step FOREIGN KEY (task_step_id) REFERENCES task_steps (id),
    CONSTRAINT fk_robot_commands_robot FOREIGN KEY (robot_id) REFERENCES robots (id)
) ENGINE = InnoDB;

CREATE TABLE command_events (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_id   BIGINT      NOT NULL,
    event_type   VARCHAR(30) NOT NULL,
    payload_json TEXT,
    occurred_at  DATETIME(6) NOT NULL,
    received_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_command_events_command FOREIGN KEY (command_id) REFERENCES robot_commands (id)
) ENGINE = InnoDB;

CREATE TABLE outbox_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type  VARCHAR(30) NOT NULL,
    aggregate_id    BIGINT      NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    payload_json    TEXT,
    status          VARCHAR(20) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    published_at    DATETIME(6)
) ENGINE = InnoDB;
