import { useEffect, useState } from "react";
import { Badge, Button, Card, Input } from "../ui";
import type { TaskResponse } from "../../types/api";

function statusTone(status: TaskResponse["status"]) {
  if (status === "RECOVERY_REQUIRED") return "red" as const;
  if (status === "CANCEL_REQUESTED") return "yellow" as const;
  return "blue" as const;
}

interface WorkerTaskCardProps {
  task: TaskResponse;
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

export function WorkerTaskCard({
  task,
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

  useEffect(() => {
    setReason("");
  }, [reportResetToken]);

  return (
    <Card>
      <div className="mb-2 flex items-center justify-between">
        <span className="font-mono text-xs text-gray-500">{task.taskNo.slice(0, 8)}</span>
        <Badge tone={statusTone(task.status)}>{task.status}</Badge>
      </div>
      {currentStep && <p className="mb-3 text-sm text-gray-700">현재 단계: {currentStep.stepType}</p>}
      <div className="flex flex-wrap items-center gap-2">
        {task.allowedActions.includes("DISPATCH") && (
          <Button onClick={onDispatch} disabled={isDispatching}>
            로봇 보내기
          </Button>
        )}
        {task.allowedActions.includes("CONFIRM_UNLOAD") && (
          <Button onClick={onConfirmUnload} disabled={isConfirmingUnload}>
            하역 완료
          </Button>
        )}
        {task.allowedActions.includes("CANCEL") && (
          <Button variant="danger" onClick={onCancel} disabled={isCanceling}>
            호출 취소
          </Button>
        )}
      </div>
      <div className="mt-3 flex items-center gap-2 border-t border-gray-100 pt-3">
        <Input placeholder="작업 불가 사유" value={reason} onChange={(e) => setReason(e.target.value)} />
        <Button variant="secondary" disabled={!reason || isReportingUnavailable} onClick={() => onReportUnavailable(reason)}>
          작업 불가
        </Button>
      </div>
    </Card>
  );
}
