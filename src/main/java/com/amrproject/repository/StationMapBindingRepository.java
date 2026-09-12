package com.amrproject.repository;

import com.amrproject.domain.entity.StationMapBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StationMapBindingRepository extends JpaRepository<StationMapBinding, Long> {

    Optional<StationMapBinding> findByStationIdAndMapVersionId(Long stationId, Long mapVersionId);
}
