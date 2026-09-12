import { Badge } from "../ui";

export type ConnectionStatus = "connecting" | "open" | "error";

const LABELS: Record<ConnectionStatus, string> = {
  connecting: "연결 중",
  open: "연결됨",
  error: "연결 오류",
};

const TONES: Record<ConnectionStatus, "gray" | "green" | "red"> = {
  connecting: "gray",
  open: "green",
  error: "red",
};

export function ConnectionStatusBadge({ status }: { status: ConnectionStatus }) {
  return <Badge tone={TONES[status]}>{LABELS[status]}</Badge>;
}
