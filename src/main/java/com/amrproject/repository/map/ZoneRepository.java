package com.amrproject.repository.map;

import com.amrproject.domain.entity.map.Zone;
import com.amrproject.domain.entity.map.ZoneType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findByMapVersionId(Long mapVersionId);

    boolean existsByMapVersionIdAndCode(Long mapVersionId, String code);

    int countByMapVersionId(Long mapVersionId);

    /** 48단계: 지도 버전의 한 유형 구역과 그 노드를 함께 읽는다(대기 구역 고르기). */
    @Query("select distinct z from Zone z left join fetch z.nodes where z.mapVersion.id = :mapVersionId and z.zoneType = :zoneType order by z.id")
    List<Zone> findWithNodesByMapVersionIdAndZoneType(Long mapVersionId, ZoneType zoneType);
}
