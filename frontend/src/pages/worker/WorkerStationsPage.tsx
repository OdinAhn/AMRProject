import { useQuery } from "@tanstack/react-query";
import { StationCard } from "../../components/worker/StationCard";
import { Card, PageHeader, Spinner } from "../../components/ui";
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
          <StationCard key={station.id} station={station} />
        ))}
      </div>
    </div>
  );
}
