import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import { VersionCreateForm } from "../../components/maps/VersionCreateForm";
import { VersionTable } from "../../components/maps/VersionTable";
import { Card, PageHeader, Spinner } from "../../components/ui";
import { api } from "../../lib/apiClient";
import type { MapVersionResponse } from "../../types/api";

export function MapVersionsPage() {
  const { mapId } = useParams<{ mapId: string }>();
  const queryClient = useQueryClient();

  const versionsQuery = useQuery({
    queryKey: ["maps", mapId, "versions"],
    queryFn: () => api.get<MapVersionResponse[]>(`/api/admin/maps/${mapId}/versions`),
  });

  const createVersion = useMutation({
    mutationFn: () => api.post<MapVersionResponse>(`/api/admin/maps/${mapId}/versions`, { filePath: null, coordinateSystem: "LOCAL_XY" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["maps", mapId, "versions"] }),
  });

  return (
    <div>
      <PageHeader
        title="지도 버전"
        actions={
          <div className="flex items-center gap-3">
            <Link to="/maps" className="text-sm text-indigo-600 hover:underline">
              ← 지도 목록
            </Link>
            <VersionCreateForm onSubmit={() => createVersion.mutate()} isPending={createVersion.isPending} />
          </div>
        }
      />

      <Card>
        {versionsQuery.isLoading && <Spinner />}
        {versionsQuery.data && <VersionTable mapId={mapId} versions={versionsQuery.data} />}
      </Card>
    </div>
  );
}
