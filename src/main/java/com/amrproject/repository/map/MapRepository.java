package com.amrproject.repository.map;

import com.amrproject.domain.entity.map.MapEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MapRepository extends JpaRepository<MapEntity, Long> {

    List<MapEntity> findBySiteId(Long siteId);
}
