import { createContext, useContext } from "react";
import type { EventEnvelope, RobotPositionPayload } from "../types/api";
import type { ConnectionStatus } from "../components/events/ConnectionStatusBadge";

// 32단계: 컨텍스트·훅을 EventStreamProvider 컴포넌트 파일과 분리했다(Fast Refresh, oxlint react/only-export-components).

export interface EventStreamContextValue {
  events: EventEnvelope[];
  status: ConnectionStatus;
  clear: () => void;
}

export type RobotPosition = RobotPositionPayload;

export const EventStreamContext = createContext<EventStreamContextValue | undefined>(undefined);

/**
 * 30단계: 시뮬레이터가 초당 여러 번 보내는 표시 전용 좌표(robot.position)는 이벤트 목록과 분리된 컨텍스트로 둔다 —
 * 같은 컨텍스트에 두면 좌표가 올 때마다 레이아웃 전체(사이드바 등)가 다시 그려진다.
 */
export const RobotPositionsContext = createContext<Record<number, RobotPosition>>({});

export function useEventStream(): EventStreamContextValue {
  const context = useContext(EventStreamContext);
  if (!context) {
    throw new Error("useEventStream은 EventStreamProvider 안에서만 사용할 수 있습니다.");
  }
  return context;
}

/** 30단계: 로봇 id → 최근에 받은 표시용 좌표. 좌표가 한동안 오지 않으면 목록에서 빠진다(EventStreamProvider가 정리). */
export function useRobotPositions(): Record<number, RobotPosition> {
  return useContext(RobotPositionsContext);
}
