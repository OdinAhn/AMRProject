package com.amrproject.domain.entity.map;

import com.amrproject.domain.entity.user.User;
import com.amrproject.exception.InvalidStateTransitionException;
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

    public void markValidated() {
        requireStatus(MapVersionStatus.DRAFT, "VALIDATE");
        this.status = MapVersionStatus.VALIDATED;
        this.validatedAt = Instant.now();
    }

    public void activate() {
        requireStatus(MapVersionStatus.VALIDATED, "ACTIVATE");
        this.status = MapVersionStatus.ACTIVE;
        this.activatedAt = Instant.now();
    }

    public void archive() {
        requireStatus(MapVersionStatus.ACTIVE, "ARCHIVE");
        this.status = MapVersionStatus.ARCHIVED;
    }

    private void requireStatus(MapVersionStatus required, String action) {
        if (status != required) {
            throw new InvalidStateTransitionException(
                    "MAP_VERSION_" + action + "_NOT_ALLOWED",
                    "지도 버전 상태가 " + required + "가 아니어서(현재: " + status + ") " + action + "을(를) 수행할 수 없습니다."
            );
        }
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
