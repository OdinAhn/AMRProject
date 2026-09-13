import { useState } from "react";
import { labels, PRIORITY_OPTIONS, priorityLabel, taskStatusTone } from "../../lib/labels";
import { Badge, Button, Card, ConfirmButton, ErrorBanner, Select } from "../ui";
import type { RobotResponse, TaskResponse } from "../../types/api";

interface TaskActionsPanelProps {
  task: TaskResponse;
  robots: RobotResponse[];
  onAssign: (robotId: number) => void;
  isAssigning: boolean;
  onCancel: () => void;
  isCanceling: boolean;
  onRecover: (resolution: "RESUME" | "FAIL") => void;
  isRecovering: boolean;
  onPause: () => void;
  onResume: () => void;
  onChangePriority: (priority: number) => void;
  isChangingFlow: boolean;
  error: string | null;
}

export function TaskActionsPanel({
  task,
  robots,
  onAssign,
  isAssigning,
  onCancel,
  isCanceling,
  onRecover,
  isRecovering,
  onPause,
  onResume,
  onChangePriority,
  isChangingFlow,
  error,
}: TaskActionsPanelProps) {
  const [robotId, setRobotId] = useState("");

  const canAssign = task.status === "QUEUED";
  const canCancel = task.allowedActions.includes("CANCEL");
  const canRecover = task.status === "RECOVERY_REQUIRED";
  // 28단계: 같은 물류센터의 운영 중(IN_SERVICE) 로봇만 배정 후보로 보여준다 — 다른 센터·정비 중 로봇은 어차피 배정에 실패한다.
  const assignableRobots = robots.filter((robot) => robot.siteId === task.siteId && robot.lifecycleStatus === "IN_SERVICE");

  return (
    <Card className="mb-4">
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <Badge tone={taskStatusTone(task.status)}>{labels.taskStatus(task.status)}</Badge>
        <span className="text-sm text-gray-500">{labels.taskType(task.taskType)}</span>
        <span className="text-sm text-gray-700">
          {task.originStationName} → {task.destinationStationName}
        </span>
        {task.assignedRobotCode && <span className="text-sm text-gray-500">로봇 {task.assignedRobotCode}</span>}
        <span className="text-sm text-gray-500">우선순위 {priorityLabel(task.priority)}</span>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        {canAssign && (
          <>
            <div className="w-56">
              <Select value={robotId} onChange={(e) => setRobotId(e.target.value)}>
                <option value="">배정할 로봇 선택</option>
                {assignableRobots.map((robot) => (
                  <option key={robot.id} value={robot.id}>
                    {robot.name} ({robot.code})
                  </option>
                ))}
              </Select>
              {assignableRobots.length === 0 && (
                <p className="mt-1 text-xs text-gray-500">이 물류센터에 운영 중인 로봇이 없습니다.</p>
              )}
            </div>
            <Button onClick={() => onAssign(Number(robotId))} disabled={!robotId || isAssigning}>
              배정
            </Button>
          </>
        )}
        {/* 33단계: 우선순위는 배정 전(QUEUED)에만, 일시정지·재개는 서버의 allowedActions로 판단한다 */}
        {task.allowedActions.includes("CHANGE_PRIORITY") && (
          <div className="w-28">
            <Select
              aria-label="우선순위 변경"
              value={PRIORITY_OPTIONS.some((o) => o.value === task.priority) ? task.priority : ""}
              disabled={isChangingFlow}
              onChange={(e) => e.target.value !== "" && onChangePriority(Number(e.target.value))}
            >
              {!PRIORITY_OPTIONS.some((o) => o.value === task.priority) && <option value="">{priorityLabel(task.priority)}</option>}
              {PRIORITY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </div>
        )}
        {task.allowedActions.includes("PAUSE") && (
          <ConfirmButton
            variant="secondary"
            title="작업을 일시정지할까요?"
            message="작업 흐름만 멈춥니다(작업자 버튼·다음 단계 진행이 멈춤). 로봇을 물리적으로 세우려면 로봇 비상정지를 쓰세요."
            confirmLabel="일시정지"
            onConfirm={onPause}
            disabled={isChangingFlow}
          >
            일시정지
          </ConfirmButton>
        )}
        {task.allowedActions.includes("RESUME") && (
          <Button onClick={onResume} disabled={isChangingFlow}>
            일시정지 해제
          </Button>
        )}
        {canCancel && (
          <ConfirmButton
            title="작업을 취소할까요?"
            message="적재 전이거나 하역이 끝났으면 바로 취소되고, 로봇에 물건이 실려 있으면 '복구 필요' 상태로 바뀝니다."
            confirmLabel="작업 취소"
            onConfirm={onCancel}
            disabled={isCanceling}
          >
            취소
          </ConfirmButton>
        )}
        {canRecover && (
          <>
            <ConfirmButton
              variant="primary"
              title="작업을 재개할까요?"
              message="실패했거나 결과가 불확실한 단계를 다시 시도합니다. 로봇의 실제 상태를 확인한 뒤 진행하세요."
              confirmLabel="재개"
              onConfirm={() => onRecover("RESUME")}
              disabled={isRecovering}
            >
              재개
            </ConfirmButton>
            <ConfirmButton
              title="최종 실패로 처리할까요?"
              message="작업이 실패로 종료되고 로봇 배정이 해제됩니다. 되돌릴 수 없습니다."
              confirmLabel="최종 실패 처리"
              onConfirm={() => onRecover("FAIL")}
              disabled={isRecovering}
            >
              최종 실패 처리
            </ConfirmButton>
          </>
        )}
      </div>
      <ErrorBanner message={error} />
    </Card>
  );
}
