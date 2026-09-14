package com.amrproject.repository.site;

import com.amrproject.domain.entity.site.StationMapBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StationMapBindingRepository extends JpaRepository<StationMapBinding, Long> {

    Optional<StationMapBinding> findByStationIdAndMapVersionId(Long stationId, Long mapVersionId);

    /** 47단계: 지도 버전의 작업장 연결 전체(비키기 목적지 — 대기 작업장 노드 우선, 다른 작업장 노드는 마지막). */
    @Query("select b from StationMapBinding b join fetch b.station join fetch b.node where b.mapVersion.id = :mapVersionId")
    List<StationMapBinding> findByMapVersionIdWithStation(Long mapVersionId);
}
