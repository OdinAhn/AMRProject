import { useEffect, useState } from "react";
import { labels, shortTaskNo, taskStatusTone } from "../../lib/labels";
import { Badge, Button, Card, ConfirmButton, Input } from "../ui";
import type { TaskResponse } from "../../types/api";

interface WorkerTaskCardProps {
  task: TaskResponse;
  /** 이 카드가 보여지는 작업장 — 출발지인지 도착지인지 표시하는 데 쓴다 */
  stationId: number;
  onDispatch: () => void;
  isDispatching: boolean;
  onConfirmUnload: () => void;
  isConfirmingUnload: boolean;
  onCancel: () => void;
  isCanceling: boolean;
  onReportUnavailable: (reason: string) => void;
  isReportingUnavailable: boolean;
  reportResetToken: number;
}

/**
 * 28단계: 현장 태블릿에서 장갑을 끼고도 누를 수 있게 버튼과 글씨를 키우고, 지금 해야 할 일(현재 단계)을
 * 한글로 크게 보여준다. 버튼은 모두 서버의 allowedActions만 보고 그린다('작업 불가' 포함).
 */
export function WorkerTaskCard({
  task,
  stationId,
  onDispatch,
  isDispatching,
  onConfirmUnload,
  isConfirmingUnload,
  onCancel,
  isCanceling,
  onReportUnavailable,
  isReportingUnavailable,
  reportResetToken,
}: WorkerTaskCardProps) {
  const [reason, setReason] = useState("");
  const currentStep = task.steps.find((s) => s.status === "RUNNING");
  const isOrigin = task.originStationId === stationId;

  useEffect(() => {
    setReason("");
  }, [reportResetToken]);

  return (
    <Card>
      <div className="mb-3 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Badge tone={isOrigin ? "blue" : "gray"}>{isOrigin ? "보내는 작업" : "받는 작업"}</Badge>
          <span className="font-mono text-xs text-gray-400">{shortTaskNo(task.taskNo)}</span>
        </div>
        <Badge tone={taskStatusTone(task.status)}>{labels.taskStatus(task.status)}</Badge>
      </div>

      <p className="text-base text-gray-900">
        <span className="font-semibold">{task.originStationName}</span>
        <span className="mx-2 text-gray-400">→</span>
        <span className="font-semibold">{task.destinationStationName}</span>
      </p>
      <p className="mt-1 text-sm text-gray-500">{task.assignedRobotCode ? `로봇 ${task.assignedRobotCode}` : "로봇 배정 대기 중"}</p>

      {currentStep && (
        <p className="mt-3 rounded-md bg-indigo-50 px-3 py-2 text-lg font-semibold text-indigo-800">
          {labels.taskStepType(currentStep.stepType)}
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-2">
        {task.allowedActions.includes("DISPATCH") && (
          <Button onClick={onDispatch} disabled={isDispatching} className="px-6 py-3 text-base">
            로봇 보내기
          </Button>
        )}
        {task.allowedActions.includes("CONFIRM_UNLOAD") && (
          <Button onClick={onConfirmUnload} disabled={isConfirmingUnload} className="px-6 py-3 text-base">
            하역 완료
          </Button>
        )}
        {task.allowedActions.includes("CANCEL") && (
          <ConfirmButton
            title="로봇 호출을 취소할까요?"
            message="물건이 로봇에 실려 있으면 바로 취소되지 않고 관리자 확인이 필요한 상태로 바뀝니다."
            confirmLabel="호출 취소"
            onConfirm={onCancel}
            disabled={isCanceling}
            className="px-6 py-3 text-base"
          >
            호출 취소
          </ConfirmButton>
        )}
      </div>

      {task.allowedActions.includes("REPORT_UNAVAILABLE") && (
        <div className="mt-4 flex items-center gap-2 border-t border-gray-100 pt-4">
          <Input placeholder="작업 불가 사유 (예: 통로 막힘)" value={reason} onChange={(e) => setReason(e.target.value)} className="py-2.5" />
          <Button
            variant="secondary"
            disabled={!reason || isReportingUnavailable}
            onClick={() => onReportUnavailable(reason)}
            className="shrink-0 px-4 py-2.5"
          >
            작업 불가
          </Button>
        </div>
      )}
    </Card>
  );
}
