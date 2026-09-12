package com.amrproject.repository.user;

import com.amrproject.domain.entity.user.UserStationAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserStationAssignmentRepository extends JpaRepository<UserStationAssignment, Long> {

    List<UserStationAssignment> findByUserId(Long userId);

    boolean existsByUserIdAndStationId(Long userId, Long stationId);

    void deleteByUserIdAndStationId(Long userId, Long stationId);
}
