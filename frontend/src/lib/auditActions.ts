/**
 * 37단계: 감사 로그 action(메서드 + 경로 패턴 또는 AUTH_*)을 화면용 한글로 바꾼다.
 * 새 API가 생겨 여기 없으면 원래 값(예: "POST /api/admin/...")을 그대로 보여준다 — 감사 기록 자체는 서버가 자동으로 남긴다.
 */
const AUDIT_ACTION: Record<string, string> = {
  AUTH_LOGIN_SUCCEEDED: "로그인 성공",
  AUTH_LOGIN_FAILED: "로그인 실패",
  AUTH_LOGIN_RATE_LIMITED: "로그인 차단(실패 누적)",
  "POST /api/auth/logout": "로그아웃",
  "PUT /api/auth/password": "내 비밀번호 변경",

  "POST /api/admin/users": "사용자 생성",
  "PATCH /api/admin/users/{userId}": "사용자 수정(역할·상태)",
  "PUT /api/admin/users/{userId}/password": "비밀번호 초기화",
  "POST /api/admin/users/{userId}/station-assignments": "작업장 배정",
  "DELETE /api/admin/users/{userId}/station-assignments/{stationId}": "작업장 배정 해제",

  "POST /api/admin/sites": "물류센터 생성",
  "PATCH /api/admin/sites/{siteId}": "물류센터 수정",
  "POST /api/admin/sites/{siteId}/emergency-stop": "물류센터 전체 비상정지",
  "POST /api/admin/sites/{siteId}/dispatch": "디스패치 실행",
  "POST /api/admin/sites/{siteId}/stations": "작업장 생성",
  "PATCH /api/admin/stations/{stationId}": "작업장 수정",
  "PUT /api/admin/stations/{stationId}/map-binding": "작업장 지도 연결",

  "POST /api/admin/sites/{siteId}/maps": "지도 생성",
  "POST /api/admin/maps/{mapId}/versions": "지도 버전 생성",
  "POST /api/admin/map-versions/{versionId}/nodes": "노드 추가",
  "POST /api/admin/map-versions/{versionId}/edges": "통로 추가",
  "POST /api/admin/map-versions/{versionId}/zones": "구역 추가",
  "POST /api/admin/map-versions/{versionId}/obstacles": "장애물 추가",
  "POST /api/admin/map-versions/{versionId}/validate": "지도 검증",
  "POST /api/admin/map-versions/{versionId}/activate": "지도 활성화",

  "POST /api/admin/robots": "로봇 등록",
  "PATCH /api/admin/robots/{robotId}": "로봇 정보 수정",
  "PATCH /api/admin/robots/{robotId}/lifecycle-status": "로봇 운영 상태 변경",
  "POST /api/admin/robots/{robotId}/configurations": "로봇 설정 변경",
  "POST /api/admin/robots/{robotId}/emergency-stop": "로봇 비상정지",
  "POST /api/admin/robots/{robotId}/simulator/emergency-stop-release": "비상정지 해제(시뮬레이터)",
  "POST /api/admin/robots/{robotId}/simulator/faults": "고장 주입(시뮬레이터)",

  "POST /api/admin/tasks/{taskId}/cancel": "작업 취소",
  "POST /api/admin/tasks/{taskId}/assign": "로봇 배정",
  "POST /api/admin/tasks/{taskId}/pause": "작업 일시정지",
  "POST /api/admin/tasks/{taskId}/resume": "작업 재개",
  "PATCH /api/admin/tasks/{taskId}/priority": "우선순위 변경",
  "POST /api/admin/tasks/{taskId}/recover": "복구 처리",

  "POST /api/admin/alarms/{alarmId}/acknowledge": "알람 확인",
  "POST /api/admin/alarms/{alarmId}/resolve": "알람 해제",

  "POST /api/worker/tasks": "로봇 호출(작업 생성)",
  "POST /api/worker/tasks/{taskId}/dispatch": "로봇 보내기",
  "POST /api/worker/tasks/{taskId}/confirm-unload": "하역 완료",
  "POST /api/worker/tasks/{taskId}/report-unavailable": "작업 불가 보고",
  "POST /api/worker/tasks/{taskId}/cancel": "호출 취소",
  "POST /api/external/tasks": "외부 시스템 작업 생성",
};

export function auditActionLabel(action: string): string {
  return AUDIT_ACTION[action] ?? action;
}

export const AUDIT_TARGET_TYPES: { value: string; label: string }[] = [
  { value: "TASK", label: "작업" },
  { value: "ROBOT", label: "로봇" },
  { value: "USER", label: "사용자" },
  { value: "SITE", label: "물류센터" },
  { value: "STATION", label: "작업장" },
  { value: "MAP", label: "지도" },
  { value: "MAP_VERSION", label: "지도 버전" },
  { value: "ALARM", label: "알람" },
];

/** 대상 상세 화면 경로 — 화면이 있는 대상만 링크한다(지도 버전은 지도 id가 필요해 링크하지 않음). */
export function auditTargetPath(targetType: string | null, targetId: number | null): string | null {
  if (targetType === null || targetId === null) return null;
  switch (targetType) {
    case "TASK":
      return `/tasks/${targetId}`;
    case "ROBOT":
      return `/robots/${targetId}`;
    case "USER":
      return `/users/${targetId}`;
    case "SITE":
      return `/sites/${targetId}`;
    case "MAP":
      return `/maps/${targetId}`;
    default:
      return null;
  }
}

export function auditTargetLabel(targetType: string): string {
  return AUDIT_TARGET_TYPES.find((t) => t.value === targetType)?.label ?? targetType;
}
