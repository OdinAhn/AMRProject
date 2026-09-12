import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { Button, Card, ErrorBanner, PageHeader, Select, Table, Td, Th } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import { useSites } from "../../lib/useSites";
import type { StationAssignmentResponse, StationResponse, UserResponse } from "../../types/api";

export function UserDetailPage() {
  const { userId } = useParams<{ userId: string }>();
  const queryClient = useQueryClient();
  const sitesQuery = useSites();
  const [siteId, setSiteId] = useState("");
  const [stationId, setStationId] = useState("");
  const [error, setError] = useState<string | null>(null);

  const userQuery = useQuery({
    queryKey: ["users", userId],
    queryFn: () => api.get<UserResponse>(`/api/admin/users/${userId}`),
  });

  const assignmentsQuery = useQuery({
    queryKey: ["users", userId, "station-assignments"],
    queryFn: () => api.get<StationAssignmentResponse[]>(`/api/admin/users/${userId}/station-assignments`),
  });

  const stationsQuery = useQuery({
    queryKey: ["sites", siteId, "stations"],
    queryFn: () => api.get<StationResponse[]>(`/api/admin/sites/${siteId}/stations`),
    enabled: !!siteId,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["users", userId, "station-assignments"] });

  const addAssignment = useMutation({
    mutationFn: () => api.post(`/api/admin/users/${userId}/station-assignments`, { stationId: Number(stationId) }),
    onSuccess: () => {
      invalidate();
      setStationId("");
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "권한 부여에 실패했습니다."),
  });

  const removeAssignment = useMutation({
    mutationFn: (targetStationId: number) => api.delete(`/api/admin/users/${userId}/station-assignments/${targetStationId}`),
    onSuccess: invalidate,
  });

  return (
    <div>
      <PageHeader
        title={userQuery.data ? `${userQuery.data.displayName} 작업장 권한` : "작업장 권한"}
        actions={
          <Link to="/users" className="text-sm text-indigo-600 hover:underline">
            ← 사용자 목록
          </Link>
        }
      />

      <Card className="mb-4">
        <h2 className="mb-3 text-sm font-semibold text-gray-700">권한 부여</h2>
        <div className="flex items-end gap-3">
          <div className="w-48">
            <Select value={siteId} onChange={(e) => { setSiteId(e.target.value); setStationId(""); }}>
              <option value="">물류센터 선택</option>
              {(sitesQuery.data ?? []).map((site) => (
                <option key={site.id} value={site.id}>
                  {site.name}
                </option>
              ))}
            </Select>
          </div>
          <div className="w-48">
            <Select value={stationId} onChange={(e) => setStationId(e.target.value)} disabled={!siteId}>
              <option value="">작업장 선택</option>
              {(stationsQuery.data ?? []).map((station) => (
                <option key={station.id} value={station.id}>
                  {station.name} ({station.code})
                </option>
              ))}
            </Select>
          </div>
          <Button onClick={() => addAssignment.mutate()} disabled={!stationId || addAssignment.isPending}>
            부여
          </Button>
        </div>
        <ErrorBanner message={error} />
      </Card>

      <Card>
        <Table>
          <thead>
            <tr>
              <Th>작업장 코드</Th>
              <Th>부여 시각</Th>
              <Th />
            </tr>
          </thead>
          <tbody>
            {(assignmentsQuery.data ?? []).map((assignment) => (
              <tr key={assignment.id}>
                <Td>{assignment.stationCode}</Td>
                <Td>{new Date(assignment.createdAt).toLocaleString()}</Td>
                <Td>
                  <button
                    className="text-sm text-red-600 hover:underline"
                    onClick={() => removeAssignment.mutate(assignment.stationId)}
                  >
                    회수
                  </button>
                </Td>
              </tr>
            ))}
          </tbody>
        </Table>
      </Card>
    </div>
  );
}
