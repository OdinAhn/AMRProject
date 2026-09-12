import { Badge, Card } from "../ui";
import type { RobotStateResponse } from "../../types/api";

interface RobotStatePanelProps {
  state: RobotStateResponse | undefined;
}

export function RobotStatePanel({ state }: RobotStatePanelProps) {
  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">현재 로봇 상태</h2>
      {state ? (
        <div className="grid grid-cols-3 gap-3 text-sm">
          <div>
            연결: <Badge tone={state.connectionState === "ONLINE" ? "green" : "red"}>{state.connectionState}</Badge>
          </div>
          <div>운전 모드: {state.operationMode}</div>
          <div>동작: {state.motionState}</div>
          <div>안전: {state.safetyState}</div>
          <div>적재: {state.loadState}</div>
          <div>위치 추정: {state.localizationState}</div>
          <div>배터리: {state.batteryPct}%</div>
        </div>
      ) : (
        <p className="text-sm text-gray-500">아직 보고된 상태가 없습니다 (명령을 받은 적 없는 로봇).</p>
      )}
    </Card>
  );
}
