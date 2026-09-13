// 28단계: 백엔드 enum 값을 화면용 한글 라벨로 바꾼다. API 계약(영문 enum)은 그대로 두고 표시만 바꾼다.
// 매핑에 없는 값(백엔드에 새 enum이 추가된 경우)은 원래 값을 그대로 보여준다.
import type {
  AlarmSeverity,
  AlarmSourceType,
  AlarmStatus,
  AlarmType,
  ConnectionState,
  LoadState,
  LocalizationState,
  MotionState,
  OperationMode,
  RobotLifecycleStatus,
  SafetyState,
  StationType,
  TaskStatus,
  TaskStepStatus,
  TaskStepType,
  TaskType,
  UserStatus,
} from "../types/api";

const TASK_STATUS: Record<TaskStatus, string> = {
  QUEUED: "대기",
  ASSIGNED: "배정됨",
  RUNNING: "진행 중",
  PAUSED: "일시정지",
  CANCEL_REQUESTED: "취소 요청됨",
  CANCELED: "취소됨",
  RECOVERY_REQUIRED: "복구 필요",
  SUCCEEDED: "완료",
  FAILED: "실패",
};

const TASK_TYPE: Record<TaskType, string> = {
  TRANSPORT: "운반",
  CHARGE: "충전",
};

const TASK_STEP_TYPE: Record<TaskStepType, string> = {
  MOVE_TO_ORIGIN: "출발지로 이동",
  CONFIRM_LOAD: "적재 확인 대기",
  MOVE_TO_DESTINATION: "목적지로 이동",
  CONFIRM_UNLOAD: "하역 확인 대기",
  MOVE_TO_CHARGING_STATION: "충전소로 이동",
  DOCK_AND_CHARGE: "도킹·충전",
};

const TASK_STEP_STATUS: Record<TaskStepStatus, string> = {
  PENDING: "대기",
  RUNNING: "진행 중",
  SUCCEEDED: "완료",
  FAILED: "실패",
  SKIPPED: "건너뜀",
};

const TASK_EVENT_TYPE: Record<string, string> = {
  CREATED: "생성",
  ASSIGNED: "로봇 배정",
  STARTED: "시작",
  SUCCEEDED: "완료",
  CANCEL_REQUESTED: "취소 요청",
  CANCELED: "취소",
  RECOVERY_REQUIRED: "복구 필요",
  RECOVERY_RESUMED: "복구 재개",
  RECOVERY_FAILED: "최종 실패 처리",
  REPORT_UNAVAILABLE: "작업 불가 보고",
};

const ALARM_TYPE: Record<AlarmType, string> = {
  ROBOT_COMM_LOST: "로봇 통신 두절",
  LOCALIZATION_LOST: "위치 추정 실패",
  TASK_FAILED: "작업 최종 실패",
  LOAD_EXCEPTION: "적재 센서 이상",
  TASK_RECOVERY_REQUIRED: "작업 복구 필요",
};

const ALARM_SEVERITY: Record<AlarmSeverity, string> = {
  INFO: "정보",
  WARNING: "경고",
  CRITICAL: "심각",
};

const ALARM_STATUS: Record<AlarmStatus, string> = {
  OPEN: "미확인",
  ACKNOWLEDGED: "확인됨",
  RESOLVED: "해제됨",
};

const ALARM_SOURCE_TYPE: Record<AlarmSourceType, string> = {
  ROBOT: "로봇",
  TASK: "작업",
  STATION: "작업장",
  SYSTEM: "시스템",
};

const ROBOT_LIFECYCLE: Record<RobotLifecycleStatus, string> = {
  REGISTERED: "등록됨",
  IN_SERVICE: "운영 중",
  MAINTENANCE: "정비 중",
  DECOMMISSIONED: "퇴역",
};

const CONNECTION_STATE: Record<ConnectionState, string> = {
  ONLINE: "온라인",
  STALE: "응답 지연",
  OFFLINE: "오프라인",
};

const OPERATION_MODE: Record<OperationMode, string> = {
  AUTO: "자동",
  MANUAL: "수동",
  MAINTENANCE: "정비",
};

const MOTION_STATE: Record<MotionState, string> = {
  IDLE: "대기",
  MOVING: "이동 중",
  DOCKING: "도킹 중",
  CHARGING: "충전 중",
};

const SAFETY_STATE: Record<SafetyState, string> = {
  NORMAL: "정상",
  PROTECTIVE_STOP: "보호 정지",
  EMERGENCY_STOP: "비상 정지",
};

const LOAD_STATE: Record<LoadState, string> = {
  EMPTY: "비어 있음",
  LOADED: "적재됨",
  UNKNOWN: "알 수 없음",
};

const LOCALIZATION_STATE: Record<LocalizationState, string> = {
  LOCALIZED: "정상",
  LOST: "실패",
  UNKNOWN: "알 수 없음",
};

const ROLE: Record<string, string> = {
  ADMIN: "관리자",
  WORKER: "작업자",
};

const USER_STATUS: Record<UserStatus, string> = {
  ACTIVE: "활성",
  DISABLED: "비활성",
};

const STATION_TYPE: Record<StationType, string> = {
  PICKUP: "입고",
  DROPOFF: "출고",
  WAITING: "대기",
  CHARGING: "충전",
  GENERIC: "일반",
};

function lookup<K extends string>(table: Record<K, string>, value: K): string {
  return table[value] ?? value;
}

export const labels = {
  taskStatus: (v: TaskStatus) => lookup(TASK_STATUS, v),
  taskType: (v: TaskType) => lookup(TASK_TYPE, v),
  taskStepType: (v: TaskStepType) => lookup(TASK_STEP_TYPE, v),
  taskStepStatus: (v: TaskStepStatus) => lookup(TASK_STEP_STATUS, v),
  taskEventType: (v: string) => TASK_EVENT_TYPE[v] ?? v,
  alarmType: (v: AlarmType) => lookup(ALARM_TYPE, v),
  alarmSeverity: (v: AlarmSeverity) => lookup(ALARM_SEVERITY, v),
  alarmStatus: (v: AlarmStatus) => lookup(ALARM_STATUS, v),
  alarmSourceType: (v: AlarmSourceType) => lookup(ALARM_SOURCE_TYPE, v),
  robotLifecycle: (v: RobotLifecycleStatus) => lookup(ROBOT_LIFECYCLE, v),
  connectionState: (v: ConnectionState) => lookup(CONNECTION_STATE, v),
  operationMode: (v: OperationMode) => lookup(OPERATION_MODE, v),
  motionState: (v: MotionState) => lookup(MOTION_STATE, v),
  safetyState: (v: SafetyState) => lookup(SAFETY_STATE, v),
  loadState: (v: LoadState) => lookup(LOAD_STATE, v),
  localizationState: (v: LocalizationState) => lookup(LOCALIZATION_STATE, v),
  stationType: (v: StationType) => lookup(STATION_TYPE, v),
  role: (v: string) => ROLE[v] ?? v,
  userStatus: (v: UserStatus) => lookup(USER_STATUS, v),
};

/** 작업 상태 배지 색 — 목록·상세·작업자 화면이 모두 같은 규칙을 쓴다. */
export function taskStatusTone(status: TaskStatus) {
  if (status === "SUCCEEDED") return "green" as const;
  if (status === "FAILED" || status === "RECOVERY_REQUIRED") return "red" as const;
  if (status === "CANCEL_REQUESTED" || status === "PAUSED") return "yellow" as const;
  if (status === "CANCELED") return "gray" as const;
  return "blue" as const;
}

/** 목록에 보여줄 짧은 작업 번호(UUID 앞 8자리). */
export function shortTaskNo(taskNo: string) {
  return taskNo.slice(0, 8);
}
