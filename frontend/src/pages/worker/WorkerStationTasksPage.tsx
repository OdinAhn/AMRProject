import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { CallRobotForm } from "../../components/worker/CallRobotForm";
import { WorkerTaskCard } from "../../components/worker/WorkerTaskCard";
import { Card, PageHeader, Spinner } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type { StationResponse, TaskResponse } from "../../types/api";

export function WorkerStationTasksPage() {
  const { stationId } = useParams<{ stationId: string }>();
  const queryClient = useQueryClient();
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
    refetchInterval: 5000,
  });

  const invalidateTasks = () => queryClient.invalidateQueries({ queryKey: ["worker", "stations", stationId, "tasks"] });

  const onActionError = (err: unknown) => setError(err instanceof ApiError ? err.message : "요청이 실패했습니다.");

  const callRobot = useMutation({
    mutationFn: (destinationId: string) =>
      api.post<TaskResponse>(
        "/api/worker/tasks",
        { originStationId: Number(stationId), destinationStationId: Number(destinationId) },
        { "Idempotency-Key": crypto.randomUUID() },
      ),
    onSuccess: () => {
      invalidateTasks();
      setCallResetToken((t) => t + 1);
      setError(null);
    },
    onError: onActionError,
  });

  const dispatch = useMutation({
    mutationFn: (taskId: number) => api.post<TaskResponse>(`/api/worker/tasks/${taskId}/dispatch`),
    onSuccess: invalidateTasks,
    onError: onActionError,
  });

  const confirmUnload = useMutation({
    mutationFn: (taskId: number) => api.post<TaskResponse>(`/api/worker/tasks/${taskId}/confirm-unload`),
    onSuccess: invalidateTasks,
    onError: onActionError,
  });

  const cancel = useMutation({
    mutationFn: (taskId: number) => api.post<TaskResponse>(`/api/worker/tasks/${taskId}/cancel`),
    onSuccess: invalidateTasks,
    onError: onActionError,
  });

  const reportUnavailable = useMutation({
    mutationFn: ({ taskId, reason }: { taskId: number; reason: string }) =>
      api.post<TaskResponse>(`/api/worker/tasks/${taskId}/report-unavailable`, { reason }),
    onSuccess: (_, { taskId }) => {
      invalidateTasks();
      setReportResetTokens((prev) => ({ ...prev, [taskId]: (prev[taskId] ?? 0) + 1 }));
    },
    onError: onActionError,
  });

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
        onSubmit={(destinationId) => callRobot.mutate(destinationId)}
        isPending={callRobot.isPending}
        error={error}
        resetToken={callResetToken}
      />

      {tasksQuery.isLoading && <Spinner />}
      <div className="space-y-3">
        {(tasksQuery.data ?? []).map((task) => (
          <WorkerTaskCard
            key={task.id}
            task={task}
            onDispatch={() => dispatch.mutate(task.id)}
            isDispatching={dispatch.isPending}
            onConfirmUnload={() => confirmUnload.mutate(task.id)}
            isConfirmingUnload={confirmUnload.isPending}
            onCancel={() => cancel.mutate(task.id)}
            isCanceling={cancel.isPending}
            onReportUnavailable={(reason) => reportUnavailable.mutate({ taskId: task.id, reason })}
            isReportingUnavailable={reportUnavailable.isPending}
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
