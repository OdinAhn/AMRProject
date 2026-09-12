import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { Badge, Card, PageHeader, Spinner } from "../../components/ui";
import { api } from "../../lib/apiClient";
import type { StationResponse } from "../../types/api";

export function WorkerStationsPage() {
  const stationsQuery = useQuery({
    queryKey: ["worker", "stations"],
    queryFn: () => api.get<StationResponse[]>("/api/worker/stations"),
  });

  return (
    <div>
      <PageHeader title="내 작업장" />
      {stationsQuery.isLoading && <Spinner />}
      {stationsQuery.data && stationsQuery.data.length === 0 && (
        <Card>
          <p className="text-sm text-gray-500">배정된 작업장이 없습니다. 관리자에게 작업장 권한을 요청하세요.</p>
        </Card>
      )}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 md:grid-cols-3">
        {(stationsQuery.data ?? []).map((station) => (
          <Link key={station.id} to={`/worker/stations/${station.id}/tasks`}>
            <Card className="hover:border-indigo-300">
              <div className="mb-2 flex items-center justify-between">
                <span className="font-medium text-gray-900">{station.name}</span>
                <Badge tone="blue">{station.stationType}</Badge>
              </div>
              <p className="text-xs text-gray-500">{station.code}</p>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
