package com.amrproject.repository.task;

import com.amrproject.domain.entity.task.ResourceReservation;
import com.amrproject.domain.entity.task.ResourceReservationStatus;
import com.amrproject.domain.entity.task.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceReservationRepository extends JpaRepository<ResourceReservation, Long> {

    int countByResourceTypeAndResourceIdAndStatus(ResourceType resourceType, Long resourceId, ResourceReservationStatus status);

    List<ResourceReservation> findByRobotIdAndStatus(Long robotId, ResourceReservationStatus status);
}
