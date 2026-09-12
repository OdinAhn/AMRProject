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

import java.math.BigDecimal;

/**
 * 참고: docs/ERD.md §4.3. from/to 노드는 같은 map_version에 속해야 한다 (MapVersionService에서 검증).
 */
@Entity
@Table(name = "edges")
public class Edge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_version_id", nullable = false)
    private MapVersion mapVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", nullable = false)
    private Node fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", nullable = false)
    private Node toNode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EdgeDirection direction;

    private BigDecimal distance;

    @Column(name = "max_capacity")
    private Integer maxCapacity;

    protected Edge() {
    }

    public Edge(MapVersion mapVersion, Node fromNode, Node toNode, EdgeDirection direction, BigDecimal distance, Integer maxCapacity) {
        this.mapVersion = mapVersion;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.direction = direction;
        this.distance = distance;
        this.maxCapacity = maxCapacity;
    }

    public Long getId() {
        return id;
    }

    public MapVersion getMapVersion() {
        return mapVersion;
    }

    public Node getFromNode() {
        return fromNode;
    }

    public Node getToNode() {
        return toNode;
    }

    public EdgeDirection getDirection() {
        return direction;
    }

    public BigDecimal getDistance() {
        return distance;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }
}
