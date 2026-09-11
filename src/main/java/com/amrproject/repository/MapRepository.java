package com.amrproject.repository;

import com.amrproject.domain.entity.MapEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapRepository extends JpaRepository<MapEntity, Long> {
}
