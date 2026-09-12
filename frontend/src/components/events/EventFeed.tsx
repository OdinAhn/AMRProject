import { Card, Table, Td, Th } from "../ui";
import type { EventEnvelope } from "../../types/api";
import { EventFeedItem } from "./EventFeedItem";

export function EventFeed({ events }: { events: EventEnvelope[] }) {
  return (
    <Card>
      <Table>
        <thead>
          <tr>
            <Th>시각</Th>
            <Th>종류</Th>
            <Th>ID</Th>
            <Th>이벤트</Th>
            <Th>페이로드</Th>
          </tr>
        </thead>
        <tbody>
          {events.map((event) => (
            <EventFeedItem key={event.eventId} event={event} />
          ))}
          {events.length === 0 && (
            <tr>
              <Td className="text-gray-500">아직 도착한 이벤트가 없습니다.</Td>
            </tr>
          )}
        </tbody>
      </Table>
    </Card>
  );
}
