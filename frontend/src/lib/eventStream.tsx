import { useQueryClient } from "@tanstack/react-query";
import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { getToken } from "./apiClient";
import type { EventEnvelope } from "../types/api";
import type { ConnectionStatus } from "../components/events/ConnectionStatusBadge";

const MAX_EVENTS = 200;

/**
 * 이벤트 종류별로 무효화할 쿼리 키. 해당 aggregate를 보여주는 화면이 열려 있으면
 * TanStack Query가 즉시 다시 조회하고, 열려 있지 않으면 다음에 열 때 새로 조회한다.
 */
const INVALIDATION_KEYS: Record<string, string[][]> = {
  TASK: [["tasks"], ["worker"]],
  ALARM: [["alarms"]],
};

interface EventStreamContextValue {
  events: EventEnvelope[];
  status: ConnectionStatus;
  clear: () => void;
}

const EventStreamContext = createContext<EventStreamContextValue | undefined>(undefined);

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

  useEffect(() => {
    const token = getToken();
    const source = new EventSource(`/api/events/stream?access_token=${encodeURIComponent(token ?? "")}`);

    source.onopen = () => setStatus("open");
    source.onerror = () => setStatus("error");
    source.onmessage = (message) => {
      const envelope = JSON.parse(message.data) as EventEnvelope;
      setEvents((prev) => [envelope, ...prev].slice(0, MAX_EVENTS));
      for (const queryKey of INVALIDATION_KEYS[envelope.aggregateType] ?? []) {
        queryClient.invalidateQueries({ queryKey });
      }
    };

    return () => {
      source.close();
    };
  }, [queryClient]);

  const clear = () => setEvents([]);

  return <EventStreamContext.Provider value={{ events, status, clear }}>{children}</EventStreamContext.Provider>;
}

export function useEventStream(): EventStreamContextValue {
  const context = useContext(EventStreamContext);
  if (!context) {
    throw new Error("useEventStream은 EventStreamProvider 안에서만 사용할 수 있습니다.");
  }
  return context;
}
