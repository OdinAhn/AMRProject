import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router";
import { Badge, Button, Card, ErrorBanner, Field, Input, PageHeader, Select, Spinner } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type { StationResponse, TaskResponse } from "../../types/api";

function statusTone(status: TaskResponse["status"]) {
  if (status === "RECOVERY_REQUIRED") return "red" as const;
  if (status === "CANCEL_REQUESTED") return "yellow" as const;
  return "blue" as const;
}

export function WorkerStationTasksPage() {
  const { stationId } = useParams<{ stationId: string }>();
  const queryClient = useQueryClient();
  const [destinationId, setDestinationId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [reasonDraft, setReasonDraft] = useState<Record<number, string>>({});

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
    mutationFn: () =>
      api.post<TaskResponse>(
        "/api/worker/tasks",
        { originStationId: Number(stationId), destinationStationId: Number(destinationId) },
        { "Idempotency-Key": crypto.randomUUID() },
      ),
    onSuccess: () => {
      invalidateTasks();
      setDestinationId("");
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
      setReasonDraft((prev) => ({ ...prev, [taskId]: "" }));
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

      <Card className="mb-4">
        <h2 className="mb-3 text-sm font-semibold text-gray-700">로봇 호출</h2>
        <form
          className="flex items-end gap-3"
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            if (!destinationId) return;
            callRobot.mutate();
          }}
        >
          <div className="w-56">
            <Field label="목적지 작업장">
              <Select value={destinationId} onChange={(e) => setDestinationId(e.target.value)} required>
                <option value="">선택</option>
                {(siteStationsQuery.data ?? [])
                  .filter((s) => String(s.id) !== stationId)
                  .map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name} ({s.code})
                    </option>
                  ))}
              </Select>
            </Field>
          </div>
          <Button type="submit" disabled={!destinationId || callRobot.isPending}>
            로봇 호출
          </Button>
        </form>
        <ErrorBanner message={error} />
      </Card>

      {tasksQuery.isLoading && <Spinner />}
      <div className="space-y-3">
        {(tasksQuery.data ?? []).map((task) => {
          const currentStep = task.steps.find((s) => s.status === "RUNNING");
          return (
            <Card key={task.id}>
              <div className="mb-2 flex items-center justify-between">
                <span className="font-mono text-xs text-gray-500">{task.taskNo.slice(0, 8)}</span>
                <Badge tone={statusTone(task.status)}>{task.status}</Badge>
              </div>
              {currentStep && <p className="mb-3 text-sm text-gray-700">현재 단계: {currentStep.stepType}</p>}
              <div className="flex flex-wrap items-center gap-2">
                {task.allowedActions.includes("DISPATCH") && (
                  <Button onClick={() => dispatch.mutate(task.id)} disabled={dispatch.isPending}>
                    로봇 보내기
                  </Button>
                )}
                {task.allowedActions.includes("CONFIRM_UNLOAD") && (
                  <Button onClick={() => confirmUnload.mutate(task.id)} disabled={confirmUnload.isPending}>
                    하역 완료
                  </Button>
                )}
                {task.allowedActions.includes("CANCEL") && (
                  <Button variant="danger" onClick={() => cancel.mutate(task.id)} disabled={cancel.isPending}>
                    호출 취소
                  </Button>
                )}
              </div>
              <div className="mt-3 flex items-center gap-2 border-t border-gray-100 pt-3">
                <Input
                  placeholder="작업 불가 사유"
                  value={reasonDraft[task.id] ?? ""}
                  onChange={(e) => setReasonDraft((prev) => ({ ...prev, [task.id]: e.target.value }))}
                />
                <Button
                  variant="secondary"
                  disabled={!reasonDraft[task.id] || reportUnavailable.isPending}
                  onClick={() => reportUnavailable.mutate({ taskId: task.id, reason: reasonDraft[task.id] })}
                >
                  작업 불가
                </Button>
              </div>
            </Card>
          );
        })}
        {tasksQuery.data && tasksQuery.data.length === 0 && (
          <Card>
            <p className="text-sm text-gray-500">진행 중인 작업이 없습니다.</p>
          </Card>
        )}
      </div>
    </div>
  );
}
