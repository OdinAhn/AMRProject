import { Badge, Td } from "../ui";
import type { EventEnvelope } from "../../types/api";

function aggregateTone(aggregateType: string) {
  if (aggregateType === "ALARM") return "red" as const;
  if (aggregateType === "TASK") return "blue" as const;
  return "gray" as const;
}

export function EventFeedItem({ event }: { event: EventEnvelope }) {
  return (
    <tr>
      <Td>{new Date(event.occurredAt).toLocaleTimeString()}</Td>
      <Td>
        <Badge tone={aggregateTone(event.aggregateType)}>{event.aggregateType}</Badge>
      </Td>
      <Td className="font-mono text-xs">#{event.aggregateId}</Td>
      <Td>{event.eventType}</Td>
      <Td className="max-w-md truncate">
        <span title={event.payload ?? undefined}>{event.payload ?? "-"}</span>
      </Td>
    </tr>
  );
}
