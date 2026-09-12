CREATE TABLE obstacles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    map_version_id  BIGINT         NOT NULL,
    x               DECIMAL(10, 3) NOT NULL,
    y               DECIMAL(10, 3) NOT NULL,
    width           DECIMAL(10, 3) NOT NULL,
    height          DECIMAL(10, 3) NOT NULL,
    CONSTRAINT fk_obstacles_map_version FOREIGN KEY (map_version_id) REFERENCES map_versions (id)
) ENGINE = InnoDB;
