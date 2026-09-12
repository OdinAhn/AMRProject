import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { StationAssignmentForm } from "../../components/users/StationAssignmentForm";
import { StationAssignmentTable } from "../../components/users/StationAssignmentTable";
import { PageHeader } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import { useSites } from "../../lib/useSites";
import type { StationAssignmentResponse, StationResponse, UserResponse } from "../../types/api";

export function UserDetailPage() {
  const { userId } = useParams<{ userId: string }>();
  const queryClient = useQueryClient();
  const sitesQuery = useSites();
  const [siteId, setSiteId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [resetToken, setResetToken] = useState(0);

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
    mutationFn: (stationId: number) => api.post(`/api/admin/users/${userId}/station-assignments`, { stationId }),
    onSuccess: () => {
      invalidate();
      setResetToken((t) => t + 1);
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

      <StationAssignmentForm
        sites={sitesQuery.data ?? []}
        stations={stationsQuery.data ?? []}
        onSubmit={(stationId) => addAssignment.mutate(stationId)}
        isPending={addAssignment.isPending}
        error={error}
        resetToken={resetToken}
        onSiteChange={setSiteId}
      />

      <StationAssignmentTable
        assignments={assignmentsQuery.data ?? []}
        onRemove={(stationId) => removeAssignment.mutate(stationId)}
      />
    </div>
  );
}
