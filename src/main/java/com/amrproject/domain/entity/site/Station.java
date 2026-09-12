package com.amrproject.domain.entity.site;

import com.amrproject.domain.entity.BaseAuditableEntity;
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

@Entity
@Table(name = "stations", uniqueConstraints = @UniqueConstraint(columnNames = {"site_id", "code"}))
public class Station extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "station_type", nullable = false, length = 20)
    private StationType stationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StationStatus status;

    @Column(name = "max_concurrent_robots")
    private Integer maxConcurrentRobots;

    protected Station() {
    }

    public Station(Site site, String code, String name, StationType stationType) {
        this.site = site;
        this.code = code;
        this.name = name;
        this.stationType = stationType;
        this.status = StationStatus.ACTIVE;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeStatus(StationStatus status) {
        this.status = status;
    }

    public void changeMaxConcurrentRobots(Integer maxConcurrentRobots) {
        this.maxConcurrentRobots = maxConcurrentRobots;
    }

    public Long getId() {
        return id;
    }

    public Site getSite() {
        return site;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public StationType getStationType() {
        return stationType;
    }

    public StationStatus getStatus() {
        return status;
    }

    public Integer getMaxConcurrentRobots() {
        return maxConcurrentRobots;
    }
}
