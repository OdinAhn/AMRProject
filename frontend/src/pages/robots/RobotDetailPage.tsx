import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { RobotBasicInfoForm } from "../../components/robots/RobotBasicInfoForm";
import { RobotConfigForm } from "../../components/robots/RobotConfigForm";
import { RobotConfigHistoryTable } from "../../components/robots/RobotConfigHistoryTable";
import { RobotLifecyclePanel } from "../../components/robots/RobotLifecyclePanel";
import { RobotStatePanel } from "../../components/robots/RobotStatePanel";
import { PageHeader } from "../../components/ui";
import { ApiError, api, errorMessage } from "../../lib/apiClient";
import { useToast } from "../../lib/toastContext";
import type {
  CreateRobotConfigurationRequest,
  RobotConfigurationResponse,
  RobotLifecycleStatus,
  RobotResponse,
  RobotStateResponse,
  UpdateRobotRequest,
} from "../../types/api";

export function RobotDetailPage() {
  const { robotId } = useParams<{ robotId: string }>();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [configError, setConfigError] = useState<string | null>(null);
  const [basicInfoError, setBasicInfoError] = useState<string | null>(null);

  const robotQuery = useQuery({
    queryKey: ["robots", robotId],
    queryFn: () => api.get<RobotResponse>(`/api/admin/robots/${robotId}`),
  });

  const stateQuery = useQuery({
    queryKey: ["robots", robotId, "state"],
    queryFn: () => api.get<RobotStateResponse>(`/api/admin/robots/${robotId}/state`),
    retry: false,
  });

  const configsQuery = useQuery({
    queryKey: ["robots", robotId, "configurations"],
    queryFn: () => api.get<RobotConfigurationResponse[]>(`/api/admin/robots/${robotId}/configurations`),
  });

  const updateBasicInfo = useMutation({
    mutationFn: (body: UpdateRobotRequest) => api.patch<RobotResponse>(`/api/admin/robots/${robotId}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["robots", robotId] });
      setBasicInfoError(null);
    },
    onError: (err) => setBasicInfoError(err instanceof ApiError ? err.message : "기본정보 수정에 실패했습니다."),
  });

  const updateLifecycle = useMutation({
    mutationFn: (lifecycleStatus: RobotLifecycleStatus) =>
      api.patch<RobotResponse>(`/api/admin/robots/${robotId}/lifecycle-status`, { lifecycleStatus }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["robots", robotId] });
      showToast("운영 상태를 변경했습니다.");
    },
    onError: (err) => showToast(errorMessage(err, "운영 상태 변경에 실패했습니다."), "error"),
  });

  const createConfig = useMutation({
    mutationFn: (body: CreateRobotConfigurationRequest) =>
      api.post<RobotConfigurationResponse>(`/api/admin/robots/${robotId}/configurations`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["robots", robotId, "configurations"] });
      queryClient.invalidateQueries({ queryKey: ["robots", robotId] });
      setConfigError(null);
    },
    onError: (err) => setConfigError(err instanceof ApiError ? err.message : "설정 생성에 실패했습니다."),
  });

  const robot = robotQuery.data;

  return (
    <div>
      <PageHeader
        title={robot ? `${robot.name} (${robot.code})` : "로봇 상세"}
        actions={
          <Link to="/robots" className="text-sm text-indigo-600 hover:underline">
            ← 로봇 목록
          </Link>
        }
      />

      {robot && (
        <RobotBasicInfoForm
          key={robot.id}
          robot={robot}
          onSubmit={(body) => updateBasicInfo.mutate(body)}
          isPending={updateBasicInfo.isPending}
          error={basicInfoError}
        />
      )}

      {robot && <RobotLifecyclePanel robot={robot} onChangeStatus={(status) => updateLifecycle.mutate(status)} />}

      <RobotStatePanel state={stateQuery.data} />

      <RobotConfigForm onSubmit={(body) => createConfig.mutate(body)} isPending={createConfig.isPending} error={configError} />

      <RobotConfigHistoryTable configs={configsQuery.data ?? []} />
    </div>
  );
}
