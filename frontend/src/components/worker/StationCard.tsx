import { labels } from "../../lib/labels";
import { Link } from "react-router";
import { Badge, Card } from "../ui";
import type { StationResponse } from "../../types/api";

interface StationCardProps {
  station: StationResponse;
}

export function StationCard({ station }: StationCardProps) {
  return (
    <Link to={`/worker/stations/${station.id}/tasks`}>
      <Card className="hover:border-indigo-300">
        <div className="mb-2 flex items-center justify-between">
          <span className="font-medium text-gray-900">{station.name}</span>
          <Badge tone="blue">{labels.stationType(station.stationType)}</Badge>
        </div>
        <p className="text-xs text-gray-500">{station.code}</p>
      </Card>
    </Link>
  );
}
