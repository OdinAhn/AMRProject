package com.amrproject.repository;

import com.amrproject.domain.entity.ResourceReservation;
import com.amrproject.domain.entity.ResourceReservationStatus;
import com.amrproject.domain.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceReservationRepository extends JpaRepository<ResourceReservation, Long> {

    int countByResourceTypeAndResourceIdAndStatus(ResourceType resourceType, Long resourceId, ResourceReservationStatus status);

    List<ResourceReservation> findByRobotIdAndStatus(Long robotId, ResourceReservationStatus status);
}
