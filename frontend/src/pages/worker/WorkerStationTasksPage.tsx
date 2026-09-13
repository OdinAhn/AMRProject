import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { CallRobotForm } from "../../components/worker/CallRobotForm";
import { WorkerTaskCard } from "../../components/worker/WorkerTaskCard";
import { Card, ErrorBanner, PageHeader, Spinner } from "../../components/ui";
import { api, errorMessage } from "../../lib/apiClient";
import { useToast } from "../../lib/toastContext";
import type { StationResponse, TaskResponse } from "../../types/api";

export function WorkerStationTasksPage() {
  const { stationId } = useParams<{ stationId: string }>();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [error, setError] = useState<string | null>(null);
  const [callResetToken, setCallResetToken] = useState(0);
  const [reportResetTokens, setReportResetTokens] = useState<Record<number, number>>({});

  const myStationsQuery = useQuery({
    queryKey: ["worker", "stations"],
    queryFn: () => api.get<StationResponse[]>("/api/worker/stations"),
  });
  const myStation = myStationsQuery.data?.find((s) => String(s.id) === stationId);

  const siteStationsQuery = useQuery({
    queryKey: ["worker", "sites", myStation?.siteId, "stations"],
    queryFn: () => api.get<StationResponse[]>(`/api/worker/sites/${myStation!.siteId}/stations`),
    enabled: !!myStation,
  });

  const tasksQuery = useQuery({
    queryKey: ["worker", "stations", stationId, "tasks"],
    queryFn: () => api.get<TaskResponse[]>(`/api/worker/stations/${stationId}/tasks`),
    // 28단계: 작업 이벤트(SSE)가 오면 ["worker"] 쿼리가 무효화돼 바로 갱신된다. 폴링은 연결이 끊겼을 때를 위한 느린 안전장치.
    refetchInterval: 30000,
  });

  const invalidateTasks = () => queryClient.invalidateQueries({ queryKey: ["worker", "stations", stationId, "tasks"] });

  const onActionError = (err: unknown) => setError(errorMessage(err));
  const onActionSuccess = (message: string) => () => {
    invalidateTasks();
    setError(null);
    showToast(message);
  };

  const callRobot = useMutation({
    mutationFn: ({ destinationId, priority }: { destinationId: string; priority: number }) =>
      api.post<TaskResponse>(
        "/api/worker/tasks",
        { originStationId: Number(stationId), destinationStationId: Number(destinationId), priority },
        { "Idempotency-Key": crypto.randomUUID() },
      ),
    onSuccess: () => {
      onActionSuccess("로봇을 호출했습니다.")();
      setCallResetToken((t) => t + 1);
    },
    onError: onActionError,
  });

  const dispatch = useMutation({
    mutationFn: (taskId: number) => api.post<TaskResponse>(`/api/worker/tasks/${taskId}/dispatch`),
    onSuccess: onActionSuccess("로봇을 보냈습니다."),
    onError: onActionError,
  });

  const confirmUnload = useMutation({
    mutationFn: (taskId: number) => api.post<TaskResponse>(`/api/worker/tasks/${taskId}/confirm-unload`),
    onSuccess: onActionSuccess("하역 완료를 처리했습니다."),
    onError: onActionError,
  });

  const cancel = useMutation({
    mutationFn: (taskId: number) => api.post<TaskResponse>(`/api/worker/tasks/${taskId}/cancel`),
    onSuccess: onActionSuccess("호출 취소를 요청했습니다."),
    onError: onActionError,
  });

  const reportUnavailable = useMutation({
    mutationFn: ({ taskId, reason }: { taskId: number; reason: string }) =>
      api.post<TaskResponse>(`/api/worker/tasks/${taskId}/report-unavailable`, { reason }),
    onSuccess: (_, { taskId }) => {
      onActionSuccess("작업 불가를 보고했습니다. 관리자에게 알림이 전달됩니다.")();
      setReportResetTokens((prev) => ({ ...prev, [taskId]: (prev[taskId] ?? 0) + 1 }));
    },
    onError: onActionError,
  });

  // 28단계: mutation 하나를 모든 카드가 공유해 한 카드에서 누르면 모든 카드 버튼이 비활성화되던 문제 —
  // 지금 처리 중인 작업 id와 카드의 작업 id가 같을 때만 비활성화한다.
  const isPendingFor = (mutation: { isPending: boolean; variables?: unknown }, taskId: number) =>
    mutation.isPending && mutation.variables === taskId;

  return (
    <div>
      <PageHeader
        title={myStation ? `${myStation.name} (${myStation.code})` : "작업장"}
        actions={
          <Link to="/worker/stations" className="text-sm text-indigo-600 hover:underline">
            ← 내 작업장
          </Link>
        }
      />

      <CallRobotForm
        stationId={stationId}
        destinationOptions={siteStationsQuery.data ?? []}
        onSubmit={(destinationId, priority) => callRobot.mutate({ destinationId, priority })}
        isPending={callRobot.isPending}
        error={null}
        resetToken={callResetToken}
      />

      <ErrorBanner message={error ?? (tasksQuery.error ? errorMessage(tasksQuery.error) : null)} />

      {tasksQuery.isLoading && <Spinner />}
      <div className="space-y-3">
        {(tasksQuery.data ?? []).map((task) => (
          <WorkerTaskCard
            key={task.id}
            task={task}
            stationId={Number(stationId)}
            onDispatch={() => dispatch.mutate(task.id)}
            isDispatching={isPendingFor(dispatch, task.id)}
            onConfirmUnload={() => confirmUnload.mutate(task.id)}
            isConfirmingUnload={isPendingFor(confirmUnload, task.id)}
            onCancel={() => cancel.mutate(task.id)}
            isCanceling={isPendingFor(cancel, task.id)}
            onReportUnavailable={(reason) => reportUnavailable.mutate({ taskId: task.id, reason })}
            isReportingUnavailable={reportUnavailable.isPending && reportUnavailable.variables?.taskId === task.id}
            reportResetToken={reportResetTokens[task.id] ?? 0}
          />
        ))}
        {tasksQuery.data && tasksQuery.data.length === 0 && (
          <Card>
            <p className="text-sm text-gray-500">진행 중인 작업이 없습니다.</p>
          </Card>
        )}
      </div>
    </div>
  );
}
