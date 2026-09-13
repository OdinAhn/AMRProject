import { useQueryClient } from "@tanstack/react-query";
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { getToken } from "./apiClient";
import type { EventEnvelope, RobotPositionPayload } from "../types/api";
import type { ConnectionStatus } from "../components/events/ConnectionStatusBadge";

const MAX_EVENTS = 200;
const POSITION_EVENT_TYPE = "robot.position";

/**
 * 이벤트 종류별로 무효화할 쿼리 키. 해당 aggregate를 보여주는 화면이 열려 있으면
 * TanStack Query가 즉시 다시 조회하고, 열려 있지 않으면 다음에 열 때 새로 조회한다.
 * 30단계: 로봇 상태 변화(robot.state_changed)는 대시보드 개요와 로봇 목록을 갱신한다.
 */
const INVALIDATION_KEYS: Record<string, string[][]> = {
  TASK: [["tasks"], ["worker"], ["overview"]],
  ALARM: [["alarms"], ["overview"]],
  ROBOT: [["overview"], ["robots"]],
};

interface EventStreamContextValue {
  events: EventEnvelope[];
  status: ConnectionStatus;
  clear: () => void;
}

export interface RobotPosition extends RobotPositionPayload {
  receivedAt: number;
}

const EventStreamContext = createContext<EventStreamContextValue | undefined>(undefined);
/**
 * 30단계: 시뮬레이터가 초당 여러 번 보내는 표시 전용 좌표(robot.position)는 이벤트 목록과 분리된 컨텍스트로 둔다 —
 * 같은 컨텍스트에 두면 좌표가 올 때마다 레이아웃 전체(사이드바 등)가 다시 그려진다.
 */
const RobotPositionsContext = createContext<Record<number, RobotPosition>>({});

/**
 * `GET /api/events/stream`(15단계) 구독을 레이아웃 수준에서 하나만 연다(28단계).
 * 19단계에는 `/events` 화면에서만 구독해서 나머지 화면은 새로고침(또는 5초 폴링) 전까지
 * 바뀐 상태가 안 보였다 — 이제 이벤트가 오면 관련 쿼리를 무효화해 모든 화면이 실시간으로 갱신된다.
 *
 * 브라우저 네이티브 EventSource는 커스텀 헤더를 못 보내 토큰을 쿼리 파라미터로 붙인다(19단계,
 * TokenAuthenticationFilter가 이 경로에 한해 허용) — docs/API.md §5에 트레이드오프 명시.
 * EventSource는 끊기면 Last-Event-ID를 붙여 스스로 재접속한다.
 */
export function EventStreamProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [events, setEvents] = useState<EventEnvelope[]>([]);
  const [status, setStatus] = useState<ConnectionStatus>("connecting");
  const [positions, setPositions] = useState<Record<number, RobotPosition>>({});

  useEffect(() => {
    const token = getToken();
    const source = new EventSource(`/api/events/stream?access_token=${encodeURIComponent(token ?? "")}`);

    source.onopen = () => setStatus("open");
    source.onerror = () => setStatus("error");
    source.onmessage = (message) => {
      const envelope = JSON.parse(message.data) as EventEnvelope;
      if (envelope.eventType === POSITION_EVENT_TYPE) {
        if (!envelope.payload) return;
        const payload = JSON.parse(envelope.payload) as RobotPositionPayload;
        setPositions((prev) => ({ ...prev, [envelope.aggregateId]: { ...payload, receivedAt: Date.now() } }));
        return;
      }
      setEvents((prev) => [envelope, ...prev].slice(0, MAX_EVENTS));
      for (const queryKey of INVALIDATION_KEYS[envelope.aggregateType] ?? []) {
        queryClient.invalidateQueries({ queryKey });
      }
    };

    return () => {
      source.close();
    };
  }, [queryClient]);

  const value = useMemo(() => ({ events, status, clear: () => setEvents([]) }), [events, status]);

  return (
    <EventStreamContext.Provider value={value}>
      <RobotPositionsContext.Provider value={positions}>{children}</RobotPositionsContext.Provider>
    </EventStreamContext.Provider>
  );
}

export function useEventStream(): EventStreamContextValue {
  const context = useContext(EventStreamContext);
  if (!context) {
    throw new Error("useEventStream은 EventStreamProvider 안에서만 사용할 수 있습니다.");
  }
  return context;
}

/** 30단계: 로봇 id → 마지막으로 받은 표시용 좌표. */
export function useRobotPositions(): Record<number, RobotPosition> {
  return useContext(RobotPositionsContext);
}
