package com.amrproject.repository;

import com.amrproject.domain.entity.MapVersion;
import com.amrproject.domain.entity.MapVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MapVersionRepository extends JpaRepository<MapVersion, Long> {

    List<MapVersion> findByMapId(Long mapId);

    Optional<MapVersion> findByMapIdAndVersionNo(Long mapId, Integer versionNo);

    Optional<MapVersion> findByMapIdAndStatus(Long mapId, MapVersionStatus status);
}
