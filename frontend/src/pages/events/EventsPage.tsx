import { EventFeed } from "../../components/events/EventFeed";
import { ConnectionStatusBadge } from "../../components/events/ConnectionStatusBadge";
import { Button, PageHeader } from "../../components/ui";
import { useEventStream } from "../../lib/useEventStream";

export function EventsPage() {
  const { events, status, clear } = useEventStream();

  return (
    <div>
      <PageHeader
        title="실시간 이벤트"
        actions={
          <div className="flex items-center gap-3">
            <ConnectionStatusBadge status={status} />
            <Button variant="secondary" onClick={clear}>
              지우기
            </Button>
          </div>
        }
      />
      <EventFeed events={events} />
    </div>
  );
}
