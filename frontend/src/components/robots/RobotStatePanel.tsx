import { labels } from "../../lib/labels";
import { Badge, Card } from "../ui";
import type { RobotStateResponse } from "../../types/api";

interface RobotStatePanelProps {
  state: RobotStateResponse | undefined;
}

function connectionTone(state: RobotStateResponse["connectionState"]) {
  if (state === "ONLINE") return "green" as const;
  if (state === "STALE") return "yellow" as const;
  return "red" as const;
}

export function RobotStatePanel({ state }: RobotStatePanelProps) {
  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">현재 로봇 상태</h2>
      {state ? (
        <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
          <div>
            연결: <Badge tone={connectionTone(state.connectionState)}>{labels.connectionState(state.connectionState)}</Badge>
          </div>
          <div>운전 모드: {labels.operationMode(state.operationMode)}</div>
          <div>동작: {labels.motionState(state.motionState)}</div>
          <div>
            안전:{" "}
            <Badge tone={state.safetyState === "NORMAL" ? "green" : "red"}>{labels.safetyState(state.safetyState)}</Badge>
          </div>
          <div>적재: {labels.loadState(state.loadState)}</div>
          <div>위치 추정: {labels.localizationState(state.localizationState)}</div>
          <div>
            배터리: <span className={state.batteryPct <= 20 ? "font-semibold text-red-600" : ""}>{state.batteryPct}%</span>
          </div>
          <div className="text-xs text-gray-500">마지막 보고: {new Date(state.receivedAt).toLocaleString()}</div>
        </div>
      ) : (
        <p className="text-sm text-gray-500">아직 보고된 상태가 없습니다 (명령을 받은 적 없는 로봇).</p>
      )}
    </Card>
  );
}
