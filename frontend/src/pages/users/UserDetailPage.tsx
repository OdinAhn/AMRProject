import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { StationAssignmentForm } from "../../components/users/StationAssignmentForm";
import { UserAccountPanel } from "../../components/users/UserAccountPanel";
import { StationAssignmentTable } from "../../components/users/StationAssignmentTable";
import { PageHeader } from "../../components/ui";
import { ApiError, api, errorMessage } from "../../lib/apiClient";
import { useToast } from "../../lib/toastContext";
import { useSites } from "../../lib/useSites";
import type { StationAssignmentResponse, StationResponse, UpdateUserRequest, UserResponse } from "../../types/api";

export function UserDetailPage() {
  const { userId } = useParams<{ userId: string }>();
  const queryClient = useQueryClient();
  const sitesQuery = useSites();
  const [siteId, setSiteId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [resetToken, setResetToken] = useState(0);
  const [accountError, setAccountError] = useState<string | null>(null);
  const { showToast } = useToast();

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

  // 32단계: 역할·상태 변경과 비밀번호 초기화
  const updateUser = useMutation({
    mutationFn: (body: UpdateUserRequest) => api.patch<UserResponse>(`/api/admin/users/${userId}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      setAccountError(null);
      showToast("계정 정보를 변경했습니다.");
    },
    onError: (err) => setAccountError(errorMessage(err)),
  });

  const resetPassword = useMutation({
    mutationFn: (newPassword: string) => api.put(`/api/admin/users/${userId}/password`, { newPassword }),
    onSuccess: () => {
      setAccountError(null);
      showToast("비밀번호를 초기화했습니다.");
    },
    onError: (err) => setAccountError(errorMessage(err)),
  });

  const removeAssignment = useMutation({
    mutationFn: (targetStationId: number) => api.delete(`/api/admin/users/${userId}/station-assignments/${targetStationId}`),
    onSuccess: invalidate,
  });

  return (
    <div>
      <PageHeader
        title={userQuery.data ? `사용자 — ${userQuery.data.displayName}` : "사용자 상세"}
        actions={
          <Link to="/users" className="text-sm text-indigo-600 hover:underline">
            ← 사용자 목록
          </Link>
        }
      />

      {userQuery.data && (
        <UserAccountPanel
          user={userQuery.data}
          onUpdate={(body) => updateUser.mutate(body)}
          isUpdating={updateUser.isPending}
          onResetPassword={(newPassword) => resetPassword.mutate(newPassword)}
          isResettingPassword={resetPassword.isPending}
          error={accountError}
        />
      )}

      <h2 className="mb-2 text-sm font-semibold text-gray-700">작업장 권한</h2>
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
