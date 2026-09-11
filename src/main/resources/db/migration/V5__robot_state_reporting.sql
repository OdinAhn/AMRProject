-- Stage 4: 로봇 어댑터·시뮬레이터 (상태 보고)
-- 참고: docs/ERD.md §4.4 "robot_current_states"/"robot_state_snapshots".
-- current_node_id/pose_x/pose_y/pose_heading은 지도 스키마(6단계)까지 보류.

CREATE TABLE robot_current_states (
    robot_id             BIGINT PRIMARY KEY,
    connection_state     VARCHAR(20) NOT NULL,
    operation_mode       VARCHAR(20) NOT NULL,
    motion_state         VARCHAR(20) NOT NULL,
    safety_state         VARCHAR(20) NOT NULL,
    load_state           VARCHAR(20) NOT NULL,
    localization_state   VARCHAR(20) NOT NULL,
    battery_pct          INT         NOT NULL,
    state_version        INT         NOT NULL,
    reported_at          DATETIME(6) NOT NULL,
    received_at          DATETIME(6) NOT NULL,
    CONSTRAINT fk_robot_current_states_robot FOREIGN KEY (robot_id) REFERENCES robots (id)
) ENGINE = InnoDB;

CREATE TABLE robot_state_snapshots (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    robot_id              BIGINT      NOT NULL,
    connection_state      VARCHAR(20) NOT NULL,
    operation_mode        VARCHAR(20) NOT NULL,
    motion_state          VARCHAR(20) NOT NULL,
    safety_state          VARCHAR(20) NOT NULL,
    load_state            VARCHAR(20) NOT NULL,
    localization_state    VARCHAR(20) NOT NULL,
    battery_pct           INT         NOT NULL,
    reported_at           DATETIME(6) NOT NULL,
    received_at           DATETIME(6) NOT NULL,
    CONSTRAINT fk_robot_state_snapshots_robot FOREIGN KEY (robot_id) REFERENCES robots (id)
) ENGINE = InnoDB;
