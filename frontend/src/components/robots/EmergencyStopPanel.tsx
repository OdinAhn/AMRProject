import { Button, Card, ConfirmButton } from "../ui";
import type { RobotStateResponse } from "../../types/api";

interface EmergencyStopPanelProps {
  state: RobotStateResponse | undefined;
  onStop: () => void;
  isStopping: boolean;
  onSimulatedRelease: () => void;
  isReleasing: boolean;
}

/**
 * 33단계: 로봇 비상정지. 정지 여부는 요청 결과가 아니라 로봇이 보고한 안전 상태(safetyState)로 보여준다(DESIGN.md §3).
 * 실제 로봇의 비상정지는 현장에서만 해제된다고 가정해, 해제 버튼은 시뮬레이터 전용으로 분명히 표시한다.
 */
export function EmergencyStopPanel({ state, onStop, isStopping, onSimulatedRelease, isReleasing }: EmergencyStopPanelProps) {
  const stopped = state?.safetyState === "EMERGENCY_STOP";
  return (
    <Card className={`mb-4 ${stopped ? "border-red-300 bg-red-50" : ""}`}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold text-gray-700">비상정지</h2>
          <p className="mt-1 text-sm text-gray-600">
            {stopped
              ? "로봇이 비상정지 상태를 보고했습니다. 현장에서 안전을 확인한 뒤 해제해야 새 명령을 받습니다."
              : "로봇을 즉시 멈추고, 맡고 있던 작업은 '복구 필요'로 바뀝니다."}
          </p>
        </div>
        <div className="flex gap-2">
          {!stopped && (
            <ConfirmButton
              title="이 로봇을 비상정지할까요?"
              message="로봇이 즉시 멈추고 진행 중인 작업은 복구 필요 상태가 됩니다. 해제는 현장에서만 할 수 있습니다."
              confirmLabel="비상정지"
              onConfirm={onStop}
              disabled={isStopping}
              className="px-5 py-2"
            >
              비상정지
            </ConfirmButton>
          )}
          {stopped && (
            <Button variant="secondary" onClick={onSimulatedRelease} disabled={isReleasing}>
              현장 해제 (시뮬레이터)
            </Button>
          )}
        </div>
      </div>
    </Card>
  );
}
