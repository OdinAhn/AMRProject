-- Stage 6: 지도·설정 전체 기능 (노드/통로/구역, 지도-작업장 매핑, 로봇 설정)
-- 참고: docs/ERD.md §4.3(지도)/§4.4(로봇 설정)

CREATE TABLE nodes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    map_version_id  BIGINT       NOT NULL,
    code            VARCHAR(50)  NOT NULL,
    x               DECIMAL(10, 3) NOT NULL,
    y               DECIMAL(10, 3) NOT NULL,
    node_type       VARCHAR(20)  NOT NULL,
    CONSTRAINT uk_nodes_version_code UNIQUE (map_version_id, code),
    CONSTRAINT fk_nodes_map_version FOREIGN KEY (map_version_id) REFERENCES map_versions (id)
) ENGINE = InnoDB;

CREATE TABLE edges (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    map_version_id  BIGINT       NOT NULL,
    from_node_id    BIGINT       NOT NULL,
    to_node_id      BIGINT       NOT NULL,
    direction       VARCHAR(20)  NOT NULL,
    distance        DECIMAL(10, 3),
    max_capacity    INT,
    CONSTRAINT fk_edges_map_version FOREIGN KEY (map_version_id) REFERENCES map_versions (id),
    CONSTRAINT fk_edges_from_node FOREIGN KEY (from_node_id) REFERENCES nodes (id),
    CONSTRAINT fk_edges_to_node FOREIGN KEY (to_node_id) REFERENCES nodes (id)
) ENGINE = InnoDB;

CREATE TABLE zones (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    map_version_id  BIGINT       NOT NULL,
    code            VARCHAR(50)  NOT NULL,
    zone_type       VARCHAR(20)  NOT NULL,
    max_capacity    INT,
    CONSTRAINT uk_zones_version_code UNIQUE (map_version_id, code),
    CONSTRAINT fk_zones_map_version FOREIGN KEY (map_version_id) REFERENCES map_versions (id)
) ENGINE = InnoDB;

CREATE TABLE zone_nodes (
    zone_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    PRIMARY KEY (zone_id, node_id),
    CONSTRAINT fk_zone_nodes_zone FOREIGN KEY (zone_id) REFERENCES zones (id),
    CONSTRAINT fk_zone_nodes_node FOREIGN KEY (node_id) REFERENCES nodes (id)
) ENGINE = InnoDB;

CREATE TABLE station_map_bindings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_id      BIGINT NOT NULL,
    map_version_id  BIGINT NOT NULL,
    node_id         BIGINT NOT NULL,
    CONSTRAINT uk_station_map_bindings_station_version UNIQUE (station_id, map_version_id),
    CONSTRAINT fk_smb_station FOREIGN KEY (station_id) REFERENCES stations (id),
    CONSTRAINT fk_smb_map_version FOREIGN KEY (map_version_id) REFERENCES map_versions (id),
    CONSTRAINT fk_smb_node FOREIGN KEY (node_id) REFERENCES nodes (id)
) ENGINE = InnoDB;

CREATE TABLE robot_configurations (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    robot_id                    BIGINT      NOT NULL,
    version_no                  INT         NOT NULL,
    max_payload_kg              DECIMAL(10, 3) NOT NULL,
    max_speed_mps               DECIMAL(10, 3) NOT NULL,
    battery_capacity_wh         DECIMAL(10, 3) NOT NULL,
    charging_low_threshold_pct  INT         NOT NULL,
    charging_target_pct         INT         NOT NULL,
    created_by                  BIGINT,
    CONSTRAINT uk_robot_configurations_robot_version UNIQUE (robot_id, version_no),
    CONSTRAINT fk_robot_configurations_robot FOREIGN KEY (robot_id) REFERENCES robots (id),
    CONSTRAINT fk_robot_configurations_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;

ALTER TABLE robots
    ADD COLUMN desired_config_id BIGINT,
    ADD COLUMN applied_config_id BIGINT,
    ADD CONSTRAINT fk_robots_desired_config FOREIGN KEY (desired_config_id) REFERENCES robot_configurations (id),
    ADD CONSTRAINT fk_robots_applied_config FOREIGN KEY (applied_config_id) REFERENCES robot_configurations (id);
