-- Stage 2: 최소 운영 데이터 (사용자/역할, 작업장 권한, 시설/작업장, 로봇, 지도 스키마)
-- 참고: docs/ERD.md §4.1~4.4

CREATE TABLE roles (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50)  NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_roles_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role_id       BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB;

CREATE TABLE sites (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT uk_sites_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE stations (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id      BIGINT       NOT NULL,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    station_type VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uk_stations_site_code UNIQUE (site_id, code),
    CONSTRAINT fk_stations_site FOREIGN KEY (site_id) REFERENCES sites (id)
) ENGINE = InnoDB;

CREATE TABLE user_station_assignments (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    station_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_usa_user_station UNIQUE (user_id, station_id),
    CONSTRAINT fk_usa_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_usa_station FOREIGN KEY (station_id) REFERENCES stations (id)
) ENGINE = InnoDB;

CREATE TABLE robots (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    model            VARCHAR(100),
    manufacturer     VARCHAR(100),
    site_id          BIGINT       NOT NULL,
    lifecycle_status VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    CONSTRAINT uk_robots_code UNIQUE (code),
    CONSTRAINT fk_robots_site FOREIGN KEY (site_id) REFERENCES sites (id)
) ENGINE = InnoDB;

CREATE TABLE maps (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id BIGINT       NOT NULL,
    name    VARCHAR(100) NOT NULL,
    CONSTRAINT fk_maps_site FOREIGN KEY (site_id) REFERENCES sites (id)
) ENGINE = InnoDB;

CREATE TABLE map_versions (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    map_id             BIGINT      NOT NULL,
    version_no         INT         NOT NULL,
    file_path          VARCHAR(255),
    coordinate_system  VARCHAR(50),
    status             VARCHAR(20) NOT NULL,
    validated_at       DATETIME(6),
    activated_at       DATETIME(6),
    created_by         BIGINT,
    CONSTRAINT uk_map_versions_map_version UNIQUE (map_id, version_no),
    CONSTRAINT fk_map_versions_map FOREIGN KEY (map_id) REFERENCES maps (id),
    CONSTRAINT fk_map_versions_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;
