import { Card, Table, Td, Th } from "../ui";
import type { TaskEventResponse } from "../../types/api";

interface TaskEventsTableProps {
  events: TaskEventResponse[];
}

export function TaskEventsTable({ events }: TaskEventsTableProps) {
  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold text-gray-700">이력</h2>
      <Table>
        <thead>
          <tr>
            <Th>시각</Th>
            <Th>이벤트</Th>
            <Th>전이</Th>
          </tr>
        </thead>
        <tbody>
          {events.map((event) => (
            <tr key={event.id}>
              <Td className="whitespace-nowrap text-xs">{new Date(event.createdAt).toLocaleString()}</Td>
              <Td>
                {event.eventType}
                {event.payload && <div className="text-xs text-gray-500">{event.payload}</div>}
              </Td>
              <Td className="text-xs">
                {event.fromStatus ?? "-"} → {event.toStatus ?? "-"}
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
