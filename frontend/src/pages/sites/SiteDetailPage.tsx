import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { StationCreateForm } from "../../components/sites/StationCreateForm";
import { StationTable } from "../../components/sites/StationTable";
import { PageHeader, Spinner } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type { CreateStationRequest, SiteResponse, StationResponse } from "../../types/api";

export function SiteDetailPage() {
  const { siteId } = useParams<{ siteId: string }>();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [resetToken, setResetToken] = useState(0);

  const siteQuery = useQuery({
    queryKey: ["sites", siteId],
    queryFn: () => api.get<SiteResponse>(`/api/admin/sites/${siteId}`),
  });

  const stationsQuery = useQuery({
    queryKey: ["sites", siteId, "stations"],
    queryFn: () => api.get<StationResponse[]>(`/api/admin/sites/${siteId}/stations`),
  });

  const createStation = useMutation({
    mutationFn: (body: CreateStationRequest) => api.post<StationResponse>(`/api/admin/sites/${siteId}/stations`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["sites", siteId, "stations"] });
      setResetToken((t) => t + 1);
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "생성에 실패했습니다."),
  });

  const updateCapacity = useMutation({
    mutationFn: ({ stationId, maxConcurrentRobots }: { stationId: number; maxConcurrentRobots: number | null }) =>
      api.patch(`/api/admin/stations/${stationId}`, { maxConcurrentRobots }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["sites", siteId, "stations"] }),
  });

  return (
    <div>
      <PageHeader
        title={siteQuery.data ? `${siteQuery.data.name} (${siteQuery.data.code})` : "작업장 관리"}
        actions={
          <Link to="/sites" className="text-sm text-indigo-600 hover:underline">
            ← 물류센터 목록
          </Link>
        }
      />

      <StationCreateForm
        onSubmit={(body) => createStation.mutate(body)}
        isPending={createStation.isPending}
        error={error}
        resetToken={resetToken}
      />

      {stationsQuery.isLoading && <Spinner />}
      {stationsQuery.data && (
        <StationTable
          stations={stationsQuery.data}
          onUpdateCapacity={(stationId, maxConcurrentRobots) => updateCapacity.mutate({ stationId, maxConcurrentRobots })}
        />
      )}
    </div>
  );
}
