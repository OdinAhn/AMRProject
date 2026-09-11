package com.amrproject.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "map_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"map_id", "version_no"}))
public class MapVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id", nullable = false)
    private MapEntity map;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "file_path", length = 255)
    private String filePath;

    @Column(name = "coordinate_system", length = 50)
    private String coordinateSystem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MapVersionStatus status;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    protected MapVersion() {
    }

    public MapVersion(MapEntity map, Integer versionNo, String filePath, String coordinateSystem, User createdBy) {
        this.map = map;
        this.versionNo = versionNo;
        this.filePath = filePath;
        this.coordinateSystem = coordinateSystem;
        this.createdBy = createdBy;
        this.status = MapVersionStatus.DRAFT;
    }

    public Long getId() {
        return id;
    }

    public MapEntity getMap() {
        return map;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getCoordinateSystem() {
        return coordinateSystem;
    }

    public MapVersionStatus getStatus() {
        return status;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }
}
