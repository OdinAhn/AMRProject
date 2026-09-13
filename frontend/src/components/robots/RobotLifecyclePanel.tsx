import { useState } from "react";
import { labels } from "../../lib/labels";
import { Badge, Card, ConfirmDialog, Select } from "../ui";
import type { RobotLifecycleStatus, RobotResponse } from "../../types/api";

const LIFECYCLE_STATUSES: RobotLifecycleStatus[] = ["REGISTERED", "IN_SERVICE", "MAINTENANCE", "DECOMMISSIONED"];

const CONFIRM_MESSAGES: Record<RobotLifecycleStatus, string> = {
  REGISTERED: "로봇이 등록 상태로 돌아가 배정 대상에서 빠집니다.",
  IN_SERVICE: "로봇이 자동 배정 대상에 포함됩니다.",
  MAINTENANCE: "로봇이 배정 대상에서 빠집니다. 진행 중인 작업은 영향을 받지 않습니다.",
  DECOMMISSIONED: "로봇이 퇴역 처리되어 더 이상 배정되지 않습니다.",
};

interface RobotLifecyclePanelProps {
  robot: RobotResponse;
  onChangeStatus: (status: RobotLifecycleStatus) => void;
}

export function RobotLifecyclePanel({ robot, onChangeStatus }: RobotLifecyclePanelProps) {
  // 28단계: 드롭다운에서 고르는 즉시 바뀌던 것을 확인창을 거치게 한다.
  const [pendingStatus, setPendingStatus] = useState<RobotLifecycleStatus | null>(null);

  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">운영 상태</h2>
      <div className="flex items-center gap-3">
        <Badge tone={robot.lifecycleStatus === "IN_SERVICE" ? "green" : "gray"}>{labels.robotLifecycle(robot.lifecycleStatus)}</Badge>
        <Select
          className="w-48"
          value=""
          onChange={(e) => e.target.value && setPendingStatus(e.target.value as RobotLifecycleStatus)}
        >
          <option value="">상태 변경...</option>
          {LIFECYCLE_STATUSES.filter((s) => s !== robot.lifecycleStatus).map((status) => (
            <option key={status} value={status}>
              {labels.robotLifecycle(status)}
            </option>
          ))}
        </Select>
      </div>
      <ConfirmDialog
        open={pendingStatus !== null}
        title={pendingStatus ? `운영 상태를 '${labels.robotLifecycle(pendingStatus)}'(으)로 바꿀까요?` : ""}
        message={pendingStatus ? CONFIRM_MESSAGES[pendingStatus] : undefined}
        confirmLabel="변경"
        variant={pendingStatus === "DECOMMISSIONED" ? "danger" : "primary"}
        onCancel={() => setPendingStatus(null)}
        onConfirm={() => {
          if (pendingStatus) onChangeStatus(pendingStatus);
          setPendingStatus(null);
        }}
      />
    </Card>
  );
}
