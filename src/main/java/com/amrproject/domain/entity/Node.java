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

import java.math.BigDecimal;

/**
 * 참고: docs/ERD.md §4.3. 특정 지도 버전에 속한다 — 버전이 바뀌면 새 노드 row가 생긴다.
 */
@Entity
@Table(name = "nodes", uniqueConstraints = @UniqueConstraint(columnNames = {"map_version_id", "code"}))
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_version_id", nullable = false)
    private MapVersion mapVersion;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private BigDecimal x;

    @Column(nullable = false)
    private BigDecimal y;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 20)
    private NodeType nodeType;

    protected Node() {
    }

    public Node(MapVersion mapVersion, String code, BigDecimal x, BigDecimal y, NodeType nodeType) {
        this.mapVersion = mapVersion;
        this.code = code;
        this.x = x;
        this.y = y;
        this.nodeType = nodeType;
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

    public BigDecimal getX() {
        return x;
    }

    public BigDecimal getY() {
        return y;
    }

    public NodeType getNodeType() {
        return nodeType;
    }
}
