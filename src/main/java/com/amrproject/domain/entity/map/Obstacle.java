package com.amrproject.domain.entity.map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 지도 상의 통행 불가 사각형 영역(건물 기둥 등, 24단계). 노드/통로와 같은 좌표계를
 * 쓰며, (x, y)는 좌하단 모서리 기준이다. 통로가 이 영역과 겹치면 안 된다는 제약은
 * MapVersionService의 검증 단계에서 강제한다 — 그러면 활성화된 지도의 모든 통로는
 * 장애물을 자동으로 피하게 되어, 그 통로 그래프 위에서 찾은 어떤 경로도 안전하다.
 */
@Entity
@Table(name = "obstacles")
public class Obstacle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_version_id", nullable = false)
    private MapVersion mapVersion;

    @Column(nullable = false)
    private BigDecimal x;

    @Column(nullable = false)
    private BigDecimal y;

    @Column(nullable = false)
    private BigDecimal width;

    @Column(nullable = false)
    private BigDecimal height;

    protected Obstacle() {
    }

    public Obstacle(MapVersion mapVersion, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {
        this.mapVersion = mapVersion;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Long getId() {
        return id;
    }

    public MapVersion getMapVersion() {
        return mapVersion;
    }

    public BigDecimal getX() {
        return x;
    }

    public BigDecimal getY() {
        return y;
    }

    public BigDecimal getWidth() {
        return width;
    }

    public BigDecimal getHeight() {
        return height;
    }
}
