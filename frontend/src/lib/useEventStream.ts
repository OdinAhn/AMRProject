import { useEffect, useRef, useState } from "react";
import { getToken } from "./apiClient";
import type { EventEnvelope } from "../types/api";
import type { ConnectionStatus } from "../components/events/ConnectionStatusBadge";

const MAX_EVENTS = 200;

/**
 * `GET /api/events/stream`(15단계) 구독. 브라우저 네이티브 EventSource는 커스텀
 * 헤더를 못 보내 토큰을 쿼리 파라미터로 붙인다(19단계, TokenAuthenticationFilter가
 * 이 경로에 한해 허용) — docs/API.md §5에 트레이드오프 명시.
 */
export function useEventStream() {
  const [events, setEvents] = useState<EventEnvelope[]>([]);
  const [status, setStatus] = useState<ConnectionStatus>("connecting");
  const sourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    const token = getToken();
    const source = new EventSource(`/api/events/stream?access_token=${encodeURIComponent(token ?? "")}`);
    sourceRef.current = source;

    source.onopen = () => setStatus("open");
    source.onerror = () => setStatus("error");
    source.onmessage = (message) => {
      const envelope = JSON.parse(message.data) as EventEnvelope;
      setEvents((prev) => [envelope, ...prev].slice(0, MAX_EVENTS));
    };

    return () => {
      source.close();
    };
  }, []);

  const clear = () => setEvents([]);

  return { events, status, clear };
}
