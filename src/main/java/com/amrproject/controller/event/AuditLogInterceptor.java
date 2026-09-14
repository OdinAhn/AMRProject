package com.amrproject.controller.event;

import com.amrproject.service.event.AuditLogService;
import com.amrproject.service.event.AuditLogService.AuditEntry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 37단계: 상태를 바꾸는 모든 API 호출(POST/PUT/PATCH/DELETE)을 감사 로그로 남긴다.
 *
 * <p>엔드포인트마다 표시를 달지 않고 한곳에서 기록해, 새 API를 추가해도 빠지지 않는다. 컨트롤러까지 도달한 요청만 대상이다 —
 * 인증(401)·역할(403)에서 막힌 요청은 보안 필터가 먼저 거절하므로 남지 않는다. 처리 결과(409 등 실패 포함)는 응답 상태로 남긴다.
 *
 * <ul>
 *   <li>action: {@code "POST /api/admin/tasks/{taskId}/cancel"}처럼 메서드 + 경로 패턴(값이 아닌 템플릿)</li>
 *   <li>target: 첫 번째 경로 변수(예: taskId → TASK). 경로 변수가 없는 생성 API는 target이 없고, 201이면 만든 리소스 id를 payload.createdId로 남긴다</li>
 *   <li>payload: 경로 변수·쿼리 파라미터. <b>요청 본문은 저장하지 않는다</b>(비밀번호가 들어갈 수 있다)</li>
 * </ul>
 *
 * <p>로그인은 행위자를 요청만으로 알 수 없어 제외하고 {@code AuthService}가 결과별로 직접 기록한다.
 */
@Component
public class AuditLogInterceptor implements HandlerInterceptor {

    /** {@link CreatedIdCapturingAdvice}가 응답 본문의 id를 넣어 두는 요청 속성 */
    static final String CREATED_ID_ATTRIBUTE = AuditLogInterceptor.class.getName() + ".createdId";

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> EXCLUDED_PATTERNS = Set.of("/api/auth/login");
    private static final Map<String, String> TARGET_TYPES = Map.of(
            "siteId", "SITE",
            "stationId", "STATION",
            "robotId", "ROBOT",
            "mapId", "MAP",
            "versionId", "MAP_VERSION",
            "taskId", "TASK",
            "userId", "USER",
            "alarmId", "ALARM"
    );

    private final AuditLogService auditLogService;

    public AuditLogInterceptor(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod) || !MUTATING_METHODS.contains(request.getMethod())) {
            return;
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = pattern != null ? pattern.toString() : request.getRequestURI();
        if (EXCLUDED_PATTERNS.contains(path)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        // 처리되지 않은 예외는 응답이 쓰이기 전이라 상태가 아직 200일 수 있다 — 서버 오류로 남긴다
        int status = ex != null && response.getStatus() < 400 ? HttpStatus.INTERNAL_SERVER_ERROR.value() : response.getStatus();

        Map<String, Object> payload = new LinkedHashMap<>();
        if (pathVariables != null && !pathVariables.isEmpty()) {
            payload.put("pathVariables", pathVariables);
        }
        if (request.getQueryString() != null) {
            payload.put("query", UriComponentsBuilder.fromUriString("?" + request.getQueryString()).build().getQueryParams().toSingleValueMap());
        }
        Object createdId = request.getAttribute(CREATED_ID_ATTRIBUTE);
        if (status == HttpStatus.CREATED.value() && createdId != null) {
            payload.put("createdId", createdId);
        }

        Map.Entry<String, String> target = pathVariables == null ? null : pathVariables.entrySet().stream().findFirst().orElse(null);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        auditLogService.record(new AuditEntry(
                authentication != null && authentication.getDetails() instanceof Long userId ? userId : null,
                authentication != null ? authentication.getName() : null,
                request.getMethod() + " " + path,
                target == null ? null : TARGET_TYPES.getOrDefault(target.getKey(), target.getKey().replaceAll("Id$", "").toUpperCase()),
                target == null ? null : parseLongOrNull(target.getValue()),
                status,
                payload,
                request.getRemoteAddr()
        ));
    }

    private static Long parseLongOrNull(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
