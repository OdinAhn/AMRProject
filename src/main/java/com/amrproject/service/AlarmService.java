package com.amrproject.service;

import com.amrproject.domain.entity.Alarm;
import com.amrproject.domain.entity.AlarmEvent;
import com.amrproject.domain.entity.AlarmSeverity;
import com.amrproject.domain.entity.AlarmSourceType;
import com.amrproject.domain.entity.AlarmStatus;
import com.amrproject.domain.entity.AlarmType;
import com.amrproject.domain.entity.OutboxEvent;
import com.amrproject.domain.entity.User;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.repository.AlarmEventRepository;
import com.amrproject.repository.AlarmRepository;
import com.amrproject.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 14단계: 알람 발생·확인·해제 (docs/STATE_MACHINES.md §7). 같은 (alarmType, sourceType, sourceId)
 * 조합의 열린(OPEN/ACKNOWLEDGED) 알람은 항상 최대 1개만 존재하도록 raise/autoResolve에서 보장한다.
 */
@Service
@Transactional(readOnly = true)
public class AlarmService {

    private static final List<AlarmStatus> OPEN_STATUSES = List.of(AlarmStatus.OPEN, AlarmStatus.ACKNOWLEDGED);

    private final AlarmRepository alarmRepository;
    private final AlarmEventRepository alarmEventRepository;
    private final OutboxEventRepository outboxEventRepository;

    public AlarmService(AlarmRepository alarmRepository, AlarmEventRepository alarmEventRepository, OutboxEventRepository outboxEventRepository) {
        this.alarmRepository = alarmRepository;
        this.alarmEventRepository = alarmEventRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public void raise(AlarmType alarmType, AlarmSeverity severity, AlarmSourceType sourceType, Long sourceId, String message) {
        if (alarmRepository.findFirstByAlarmTypeAndSourceTypeAndSourceIdAndStatusIn(alarmType, sourceType, sourceId, OPEN_STATUSES).isPresent()) {
            return;
        }
        Alarm alarm = alarmRepository.save(new Alarm(alarmType, severity, sourceType, sourceId, message));
        alarmEventRepository.save(new AlarmEvent(alarm, "RAISED", message, null));
        recordOutboxEvent(alarm, "alarm.raised", message);
    }

    @Transactional
    public void autoResolve(AlarmType alarmType, AlarmSourceType sourceType, Long sourceId) {
        alarmRepository.findFirstByAlarmTypeAndSourceTypeAndSourceIdAndStatusIn(alarmType, sourceType, sourceId, OPEN_STATUSES)
                .ifPresent(alarm -> {
                    alarm.autoResolve();
                    alarmEventRepository.save(new AlarmEvent(alarm, "RESOLVED", "자동 해소", null));
                    recordOutboxEvent(alarm, "alarm.resolved", null);
                });
    }

    @Transactional
    public Alarm acknowledge(Long alarmId, User actor) {
        Alarm alarm = getById(alarmId);
        if (alarm.getStatus() == AlarmStatus.ACKNOWLEDGED) {
            return alarm;
        }
        alarm.acknowledge(actor);
        alarmEventRepository.save(new AlarmEvent(alarm, "ACKNOWLEDGED", null, actor));
        recordOutboxEvent(alarm, "alarm.acknowledged", null);
        return alarm;
    }

    @Transactional
    public Alarm resolve(Long alarmId, User actor) {
        Alarm alarm = getById(alarmId);
        if (alarm.getStatus() == AlarmStatus.RESOLVED) {
            return alarm;
        }
        alarm.resolve(actor);
        alarmEventRepository.save(new AlarmEvent(alarm, "RESOLVED", null, actor));
        recordOutboxEvent(alarm, "alarm.resolved", null);
        return alarm;
    }

    private void recordOutboxEvent(Alarm alarm, String eventType, String payload) {
        outboxEventRepository.save(new OutboxEvent("ALARM", alarm.getId(), eventType, payload));
    }

    public Alarm getById(Long alarmId) {
        return alarmRepository.findById(alarmId)
                .orElseThrow(() -> new EntityNotFoundException("ALARM_NOT_FOUND", "알람을 찾을 수 없습니다: " + alarmId));
    }

    public List<Alarm> getAll(AlarmStatus status, AlarmSeverity severity, AlarmSourceType sourceType) {
        return alarmRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(alarm -> status == null || alarm.getStatus() == status)
                .filter(alarm -> severity == null || alarm.getSeverity() == severity)
                .filter(alarm -> sourceType == null || alarm.getSourceType() == sourceType)
                .toList();
    }
}
