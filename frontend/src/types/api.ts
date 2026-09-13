// 백엔드 DTO(com.amrproject.dto.*)와 1:1로 대응하는 타입.
// 필드가 바뀌면 이 파일도 같이 갱신해야 한다 (OpenAPI 코드 생성기는 아직 도입하지 않음).

export type SiteStatus = "ACTIVE" | "INACTIVE";
export type StationStatus = "ACTIVE" | "INACTIVE";
export type StationType = "PICKUP" | "DROPOFF" | "WAITING" | "CHARGING" | "GENERIC";
export type RobotLifecycleStatus = "REGISTERED" | "IN_SERVICE" | "MAINTENANCE" | "DECOMMISSIONED";
export type NodeType = "WAYPOINT" | "STATION" | "CHARGING";
export type EdgeDirection = "ONE_WAY" | "TWO_WAY";
export type ZoneType = "MUTEX_ZONE" | "SAFETY_ZONE";
export type MapVersionStatus = "DRAFT" | "VALIDATED" | "ACTIVE" | "ARCHIVED";
export type TaskType = "TRANSPORT" | "CHARGE";
export type TaskStatus =
  | "QUEUED"
  | "ASSIGNED"
  | "RUNNING"
  | "PAUSED"
  | "CANCEL_REQUESTED"
  | "CANCELED"
  | "RECOVERY_REQUIRED"
  | "SUCCEEDED"
  | "FAILED";
export type TaskStepStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";
export type TaskStepType =
  | "MOVE_TO_ORIGIN"
  | "CONFIRM_LOAD"
  | "MOVE_TO_DESTINATION"
  | "CONFIRM_UNLOAD"
  | "MOVE_TO_CHARGING_STATION"
  | "DOCK_AND_CHARGE";
export type UserStatus = "ACTIVE" | "DISABLED";
export type ConnectionState = "ONLINE" | "STALE" | "OFFLINE";
export type OperationMode = "AUTO" | "MANUAL" | "MAINTENANCE";
export type MotionState = "IDLE" | "MOVING" | "DOCKING" | "CHARGING";
export type SafetyState = "NORMAL" | "PROTECTIVE_STOP" | "EMERGENCY_STOP";
export type LoadState = "EMPTY" | "LOADED" | "UNKNOWN";
export type LocalizationState = "LOCALIZED" | "LOST" | "UNKNOWN";
export type RecoveryResolution = "RESUME" | "FAIL";

/** 31단계: 목록 API 공통 페이지 응답(page는 0부터) */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ErrorResponse {
  code: string;
  message: string;
  details?: unknown;
}

export interface SiteResponse {
  id: number;
  code: string;
  name: string;
  status: SiteStatus;
  createdAt: string;
  updatedAt: string;
}

export interface StationResponse {
  id: number;
  siteId: number;
  code: string;
  name: string;
  stationType: StationType;
  status: StationStatus;
  maxConcurrentRobots: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface RobotResponse {
  id: number;
  code: string;
  name: string;
  model: string | null;
  manufacturer: string | null;
  siteId: number;
  lifecycleStatus: RobotLifecycleStatus;
  desiredConfigVersion: number | null;
  appliedConfigVersion: number | null;
  createdAt: string;
  updatedAt: string;
  connectionState: ConnectionState | null;
  batteryPct: number | null;
}

export interface RobotStateResponse {
  robotId: number;
  connectionState: ConnectionState;
  operationMode: OperationMode;
  motionState: MotionState;
  safetyState: SafetyState;
  loadState: LoadState;
  localizationState: LocalizationState;
  batteryPct: number;
  stateVersion: number | null;
  reportedAt: string;
  receivedAt: string;
  currentNodeId: number | null;
}

export interface RobotConfigurationResponse {
  id: number;
  robotId: number;
  versionNo: number;
  maxPayloadKg: number;
  maxSpeedMps: number;
  batteryCapacityWh: number;
  chargingLowThresholdPct: number;
  chargingTargetPct: number;
}

export interface MapResponse {
  id: number;
  siteId: number;
  name: string;
}

export interface MapVersionResponse {
  id: number;
  mapId: number;
  versionNo: number;
  filePath: string | null;
  coordinateSystem: string | null;
  status: MapVersionStatus;
  validatedAt: string | null;
  activatedAt: string | null;
  nodeCount: number;
  edgeCount: number;
  zoneCount: number;
}

export interface MapVersionValidationResponse {
  valid: boolean;
  errors: string[];
  mapVersion: MapVersionResponse;
}

export interface NodeResponse {
  id: number;
  mapVersionId: number;
  code: string;
  x: number;
  y: number;
  nodeType: NodeType;
}

export interface EdgeResponse {
  id: number;
  mapVersionId: number;
  fromNodeId: number;
  toNodeId: number;
  direction: EdgeDirection;
  distance: number | null;
  maxCapacity: number | null;
}

export interface ObstacleResponse {
  id: number;
  mapVersionId: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface ZoneResponse {
  id: number;
  mapVersionId: number;
  code: string;
  zoneType: ZoneType;
  maxCapacity: number | null;
  nodeIds: number[];
}

export interface StationMapBindingResponse {
  id: number;
  stationId: number;
  mapVersionId: number;
  nodeId: number;
}

export interface TaskStepResponse {
  id: number;
  stepNo: number;
  stepType: TaskStepType;
  status: TaskStepStatus;
  startedAt: string | null;
  completedAt: string | null;
}

export interface TaskResponse {
  id: number;
  taskNo: string;
  siteId: number;
  taskType: TaskType;
  priority: number;
  status: TaskStatus;
  originStationId: number;
  originStationName: string;
  destinationStationId: number;
  destinationStationName: string;
  currentAssignmentId: number | null;
  assignedRobotId: number | null;
  assignedRobotCode: string | null;
  steps: TaskStepResponse[];
  allowedActions: string[];
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface TaskEventResponse {
  id: number;
  eventType: string;
  fromStatus: TaskStatus | null;
  toStatus: TaskStatus | null;
  payload: string | null;
  createdAt: string;
}

export interface UserResponse {
  id: number;
  username: string;
  displayName: string;
  roleCode: string;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}

export interface StationAssignmentResponse {
  id: number;
  stationId: number;
  stationCode: string;
  createdAt: string;
}

export interface DispatchResultResponse {
  assignedTaskIds: number[];
  chargeTaskIds: number[];
}

export interface LoginResponse {
  token: string;
  user: UserResponse;
}

// ---- 요청 타입 ----

export interface LoginRequest {
  username: string;
  password: string;
}

export interface CreateSiteRequest {
  code: string;
  name: string;
}

export interface UpdateSiteRequest {
  name?: string | null;
  status?: SiteStatus | null;
}

export interface CreateStationRequest {
  code: string;
  name: string;
  stationType: StationType;
}

export interface UpdateStationRequest {
  name?: string | null;
  status?: StationStatus | null;
  maxConcurrentRobots?: number | null;
}

export interface CreateRobotRequest {
  code: string;
  name: string;
  model?: string | null;
  manufacturer?: string | null;
  siteId: number;
}

export interface UpdateRobotLifecycleStatusRequest {
  lifecycleStatus: RobotLifecycleStatus;
}

export interface UpdateRobotRequest {
  name?: string | null;
  model?: string | null;
  manufacturer?: string | null;
}

export interface CreateRobotConfigurationRequest {
  maxPayloadKg: number;
  maxSpeedMps: number;
  batteryCapacityWh: number;
  chargingLowThresholdPct: number;
  chargingTargetPct: number;
}

export interface CreateMapRequest {
  name: string;
}

export interface CreateMapVersionRequest {
  filePath?: string | null;
  coordinateSystem?: string | null;
}

export interface CreateNodeRequest {
  code: string;
  x: number;
  y: number;
  nodeType: NodeType;
}

export interface CreateEdgeRequest {
  fromNodeId: number;
  toNodeId: number;
  direction: EdgeDirection;
  distance?: number | null;
  maxCapacity?: number | null;
}

export interface CreateObstacleRequest {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface CreateZoneRequest {
  code: string;
  zoneType: ZoneType;
  maxCapacity?: number | null;
  nodeIds: number[];
}

export interface BindStationMapRequest {
  mapVersionId: number;
  nodeId: number;
}

export interface CreateTaskRequest {
  originStationId: number;
  destinationStationId: number;
}

export interface AssignRobotRequest {
  robotId: number;
}

export interface RecoverTaskRequest {
  resolution: RecoveryResolution;
}

export interface ReportUnavailableRequest {
  reason: string;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  displayName: string;
  roleCode: string;
}

export interface UpdateUserRequest {
  displayName?: string | null;
  status?: UserStatus | null;
  /** 32단계 */
  roleCode?: string | null;
}

/** 32단계: 관리자 비밀번호 초기화 */
export interface ResetPasswordRequest {
  newPassword: string;
}

/** 32단계: 본인 비밀번호 변경 — 응답은 새 토큰이 담긴 LoginResponse */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface CreateStationAssignmentRequest {
  stationId: number;
}

export type AlarmType = "ROBOT_COMM_LOST" | "LOCALIZATION_LOST" | "TASK_FAILED" | "LOAD_EXCEPTION" | "TASK_RECOVERY_REQUIRED";
export type AlarmSeverity = "INFO" | "WARNING" | "CRITICAL";
export type AlarmStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED";
export type AlarmSourceType = "ROBOT" | "TASK" | "STATION" | "SYSTEM";

export interface AlarmResponse {
  id: number;
  alarmType: AlarmType;
  severity: AlarmSeverity;
  sourceType: AlarmSourceType;
  sourceId: number;
  status: AlarmStatus;
  message: string | null;
  acknowledgedAt: string | null;
  acknowledgedBy: number | null;
  resolvedAt: string | null;
  resolvedBy: number | null;
  allowedActions: string[];
  createdAt: string;
  updatedAt: string;
}

export interface RobotSummary {
  robotId: number;
  code: string;
  name: string;
  lifecycleStatus: RobotLifecycleStatus;
  connectionState: ConnectionState | null;
  motionState: MotionState | null;
  batteryPct: number | null;
  currentNodeId: number | null;
  currentTaskId: number | null;
  currentTaskNo: string | null;
}

export interface SiteOverviewResponse {
  siteId: number;
  activeMapVersionId: number | null;
  robots: RobotSummary[];
  openTaskCounts: Partial<Record<TaskStatus, number>>;
  openAlarmCount: number;
}

/** 30단계 robot.position 이벤트의 payload(JSON 문자열)를 파싱한 값 */
export interface RobotPositionPayload {
  mapVersionId: number;
  x: number;
  y: number;
  arrived: boolean;
}

export interface EventEnvelope {
  /** outbox 이벤트 id — 표시 전용 이벤트(robot.position)는 null */
  eventId: number | null;
  eventType: string;
  aggregateType: string;
  aggregateId: number;
  stateVersion: number | null;
  payload: string | null;
  occurredAt: string;
}
