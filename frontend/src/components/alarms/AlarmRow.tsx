import { Badge, Button, Td } from "../ui";
import type { AlarmResponse } from "../../types/api";
import { alarmStatusTone, severityTone } from "./alarmTone";

interface AlarmRowProps {
  alarm: AlarmResponse;
  onAcknowledge: (id: number) => void;
  onResolve: (id: number) => void;
  isMutating: boolean;
}

export function AlarmRow({ alarm, onAcknowledge, onResolve, isMutating }: AlarmRowProps) {
  return (
    <tr>
      <Td>{alarm.alarmType}</Td>
      <Td>
        <Badge tone={severityTone(alarm.severity)}>{alarm.severity}</Badge>
      </Td>
      <Td>
        {alarm.sourceType} #{alarm.sourceId}
      </Td>
      <Td>
        <Badge tone={alarmStatusTone(alarm.status)}>{alarm.status}</Badge>
      </Td>
      <Td className="max-w-xs truncate">
        <span title={alarm.message ?? undefined}>{alarm.message ?? "-"}</span>
      </Td>
      <Td>{new Date(alarm.createdAt).toLocaleString()}</Td>
      <Td>
        <div className="flex gap-2">
          {alarm.allowedActions.includes("ACKNOWLEDGE") && (
            <Button variant="secondary" disabled={isMutating} onClick={() => onAcknowledge(alarm.id)}>
              확인
            </Button>
          )}
          {alarm.allowedActions.includes("RESOLVE") && (
            <Button disabled={isMutating} onClick={() => onResolve(alarm.id)}>
              해제
            </Button>
          )}
        </div>
      </Td>
    </tr>
  );
}
