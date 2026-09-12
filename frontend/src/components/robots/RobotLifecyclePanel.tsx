import { Badge, Card, Select } from "../ui";
import type { RobotLifecycleStatus, RobotResponse } from "../../types/api";

const LIFECYCLE_STATUSES: RobotLifecycleStatus[] = ["REGISTERED", "IN_SERVICE", "MAINTENANCE", "DECOMMISSIONED"];

interface RobotLifecyclePanelProps {
  robot: RobotResponse;
  onChangeStatus: (status: RobotLifecycleStatus) => void;
}

export function RobotLifecyclePanel({ robot, onChangeStatus }: RobotLifecyclePanelProps) {
  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">운영 상태</h2>
      <div className="flex items-center gap-3">
        <Badge tone={robot.lifecycleStatus === "IN_SERVICE" ? "green" : "gray"}>{robot.lifecycleStatus}</Badge>
        <Select
          className="w-48"
          value=""
          onChange={(e) => e.target.value && onChangeStatus(e.target.value as RobotLifecycleStatus)}
        >
          <option value="">상태 변경...</option>
          {LIFECYCLE_STATUSES.filter((s) => s !== robot.lifecycleStatus).map((status) => (
            <option key={status} value={status}>
              {status}
            </option>
          ))}
        </Select>
      </div>
    </Card>
  );
}
