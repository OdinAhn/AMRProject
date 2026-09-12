package com.amrproject.domain.entity.map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.HashSet;
import java.util.Set;

/**
 * 참고: docs/ERD.md §4.3. zone_nodes는 추가 컬럼이 없는 순수 조인 테이블이라
 * 별도 엔티티 없이 @ManyToMany로 표현한다.
 */
@Entity
@Table(name = "zones", uniqueConstraints = @UniqueConstraint(columnNames = {"map_version_id", "code"}))
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_version_id", nullable = false)
    private MapVersion mapVersion;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 20)
    private ZoneType zoneType;

    @Column(name = "max_capacity")
    private Integer maxCapacity;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "zone_nodes",
            joinColumns = @JoinColumn(name = "zone_id"),
            inverseJoinColumns = @JoinColumn(name = "node_id")
    )
    private Set<Node> nodes = new HashSet<>();

    protected Zone() {
    }

    public Zone(MapVersion mapVersion, String code, ZoneType zoneType, Integer maxCapacity, Set<Node> nodes) {
        this.mapVersion = mapVersion;
        this.code = code;
        this.zoneType = zoneType;
        this.maxCapacity = maxCapacity;
        this.nodes = new HashSet<>(nodes);
    }

    public Long getId() {
        return id;
    }

    public MapVersion getMapVersion() {
        return mapVersion;
    }

    public String getCode() {
        return code;
    }

    public ZoneType getZoneType() {
        return zoneType;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public Set<Node> getNodes() {
        return nodes;
    }
}
