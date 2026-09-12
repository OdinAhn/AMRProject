package com.amrproject.repository;

import com.amrproject.domain.entity.MapEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MapRepository extends JpaRepository<MapEntity, Long> {

    List<MapEntity> findBySiteId(Long siteId);
}
