import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import { Badge, Button, Card, PageHeader, Spinner, Table, Td, Th } from "../../components/ui";
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

  const statusTone = (status: string) =>
    status === "ACTIVE" ? "green" : status === "VALIDATED" ? "blue" : status === "ARCHIVED" ? "gray" : "yellow";

  return (
    <div>
      <PageHeader
        title="지도 버전"
        actions={
          <div className="flex items-center gap-3">
            <Link to="/maps" className="text-sm text-indigo-600 hover:underline">
              ← 지도 목록
            </Link>
            <Button onClick={() => createVersion.mutate()} disabled={createVersion.isPending}>
              새 버전(DRAFT) 만들기
            </Button>
          </div>
        }
      />

      <Card>
        {versionsQuery.isLoading && <Spinner />}
        {versionsQuery.data && (
          <Table>
            <thead>
              <tr>
                <Th>버전</Th>
                <Th>상태</Th>
                <Th>노드/통로/구역</Th>
                <Th />
              </tr>
            </thead>
            <tbody>
              {versionsQuery.data.map((version) => (
                <tr key={version.id}>
                  <Td>v{version.versionNo}</Td>
                  <Td>
                    <Badge tone={statusTone(version.status)}>{version.status}</Badge>
                  </Td>
                  <Td>
                    {version.nodeCount} / {version.edgeCount} / {version.zoneCount}
                  </Td>
                  <Td>
                    <Link to={`/maps/${mapId}/versions/${version.id}`} className="text-indigo-600 hover:underline">
                      편집
                    </Link>
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </div>
  );
}
