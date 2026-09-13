package com.amrproject.service.task;

import com.amrproject.domain.entity.site.Site;
import com.amrproject.dto.response.task.DispatchResultResponse;
import com.amrproject.service.site.SiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 29단계: 디스패치 사이클 자동 실행. 7단계부터 {@link DispatchService#runCycle}은 관리자가
 * {@code POST /api/admin/sites/{siteId}/dispatch}를 눌러야만 돌아서, 작업자가 로봇을 호출해도
 * 누군가 버튼을 누르기 전까지 QUEUED로 머물렀다. 이제 운영 중(ACTIVE)인 모든 물류센터에 대해
 * 주기적으로 같은 사이클을 돌린다(수동 API도 그대로 유지).
 *
 * <p>{@code app.dispatch.auto.enabled}가 false(기본값)면 아무것도 하지 않는다 — 테스트 스위트는
 * 자동 배정이 끼어들면 "배정 전 상태"를 검증할 수 없어 끄고, local 프로필에서만 켠다.
 * 빈은 항상 등록해 테스트가 {@link #dispatchActiveSites()}를 직접 호출할 수 있게 한다.
 *
 * <p>한계: 다중 인스턴스로 띄우면 인스턴스마다 같은 사이클을 돌린다. 배정 자체는 로봇 중복 배정 검사로
 * 막히지만 경합(409)이 늘어난다 — 분산 락/리더 선출은 31단계(동시성) 범위다.
 */
@Component
public class AutoDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoDispatchScheduler.class);

    private final SiteService siteService;
    private final DispatchService dispatchService;
    private final boolean enabled;

    public AutoDispatchScheduler(
            SiteService siteService,
            DispatchService dispatchService,
            @Value("${app.dispatch.auto.enabled:false}") boolean enabled
    ) {
        this.siteService = siteService;
        this.dispatchService = dispatchService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.dispatch.auto.interval-ms:5000}")
    public void run() {
        if (enabled) {
            dispatchActiveSites();
        }
    }

    /** 물류센터 하나에서 예외가 나도 나머지 물류센터는 계속 처리한다. */
    public void dispatchActiveSites() {
        for (Site site : siteService.getActive()) {
            try {
                DispatchResultResponse result = dispatchService.runCycle(site.getId());
                if (!result.assignedTaskIds().isEmpty() || !result.chargeTaskIds().isEmpty()) {
                    log.info("자동 디스패치: siteId={}, 배정={}, 충전={}", site.getId(), result.assignedTaskIds(), result.chargeTaskIds());
                }
            } catch (Exception e) {
                log.warn("자동 디스패치 실패: siteId={} — 다음 주기에 재시도합니다.", site.getId(), e);
            }
        }
    }
}
