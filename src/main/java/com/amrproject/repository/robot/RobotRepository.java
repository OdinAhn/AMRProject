package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.Robot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RobotRepository extends JpaRepository<Robot, Long> {

    List<Robot> findBySiteId(Long siteId);

    /** 46단계: 물류센터 로봇 id(잠그지 않는다 — 교통 관리 참고). */
    @Query("select r.id from Robot r where r.site.id = :siteId")
    List<Long> findIdsBySiteId(Long siteId);

    boolean existsByCode(String code);

    Optional<Robot> findByCode(String code);

    /** 31단계: 같은 로봇을 여러 작업에 동시에 배정하지 못하게 배정을 로봇 단위로 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Robot r where r.id = :id")
    Optional<Robot> findByIdForUpdate(Long id);
}
