package com.amrproject.domain.entity.site;

import com.amrproject.domain.entity.map.MapVersion;
import com.amrproject.domain.entity.map.Node;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 참고: docs/ERD.md §4.3/§5. station을 nodes.id로 직접 참조하지 않고 map_version 단위로
 * 매핑을 분리해, 지도 버전이 바뀌어도(nodes row가 새로 생겨도) 기존 매핑이 깨지지 않게 한다.
 */
@Entity
@Table(name = "station_map_bindings", uniqueConstraints = @UniqueConstraint(columnNames = {"station_id", "map_version_id"}))
public class StationMapBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_version_id", nullable = false)
    private MapVersion mapVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    protected StationMapBinding() {
    }

    public StationMapBinding(Station station, MapVersion mapVersion, Node node) {
        this.station = station;
        this.mapVersion = mapVersion;
        this.node = node;
    }

    public void rebind(Node node) {
        this.node = node;
    }

    public Long getId() {
        return id;
    }

    public Station getStation() {
        return station;
    }

    public MapVersion getMapVersion() {
        return mapVersion;
    }

    public Node getNode() {
        return node;
    }
}
