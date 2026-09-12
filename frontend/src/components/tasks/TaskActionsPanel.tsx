import { useState } from "react";
import { Badge, Button, Card, ErrorBanner, Select } from "../ui";
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
  error,
}: TaskActionsPanelProps) {
  const [robotId, setRobotId] = useState("");

  const canAssign = task.status === "QUEUED";
  const canCancel = ["QUEUED", "ASSIGNED", "RUNNING", "PAUSED"].includes(task.status);
  const canRecover = task.status === "RECOVERY_REQUIRED";

  return (
    <Card className="mb-4">
      <div className="mb-3 flex items-center gap-3">
        <Badge tone="blue">{task.status}</Badge>
        <span className="text-sm text-gray-500">{task.taskType}</span>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        {canAssign && (
          <>
            <div className="w-56">
              <Select value={robotId} onChange={(e) => setRobotId(e.target.value)}>
                <option value="">배정할 로봇 선택</option>
                {robots.map((robot) => (
                  <option key={robot.id} value={robot.id}>
                    {robot.name} ({robot.code})
                  </option>
                ))}
              </Select>
            </div>
            <Button onClick={() => onAssign(Number(robotId))} disabled={!robotId || isAssigning}>
              배정
            </Button>
          </>
        )}
        {canCancel && (
          <Button variant="danger" onClick={onCancel} disabled={isCanceling}>
            취소
          </Button>
        )}
        {canRecover && (
          <>
            <Button onClick={() => onRecover("RESUME")} disabled={isRecovering}>
              재개(RESUME)
            </Button>
            <Button variant="danger" onClick={() => onRecover("FAIL")} disabled={isRecovering}>
              최종 실패 처리(FAIL)
            </Button>
          </>
        )}
      </div>
      <ErrorBanner message={error} />
    </Card>
  );
}
