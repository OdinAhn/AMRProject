package com.amrproject.repository.task;

import com.amrproject.domain.entity.task.TaskStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskStepRepository extends JpaRepository<TaskStep, Long> {

    List<TaskStep> findByTaskIdOrderByStepNoAsc(Long taskId);

    /** 31단계: 목록 응답용 — 여러 작업의 단계를 한 번에 읽는다(작업마다 단계 조회가 나가던 N+1 제거). */
    List<TaskStep> findByTaskIdInOrderByTaskIdAscStepNoAsc(Collection<Long> taskIds);

    Optional<TaskStep> findByTaskIdAndStepNo(Long taskId, Integer stepNo);
}
