package com.amrproject.repository.site;

import com.amrproject.domain.entity.site.StationMapBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StationMapBindingRepository extends JpaRepository<StationMapBinding, Long> {

    Optional<StationMapBinding> findByStationIdAndMapVersionId(Long stationId, Long mapVersionId);
}
