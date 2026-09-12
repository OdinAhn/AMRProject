import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { TaskActionsPanel } from "../../components/tasks/TaskActionsPanel";
import { TaskEventsTable } from "../../components/tasks/TaskEventsTable";
import { TaskStepsTable } from "../../components/tasks/TaskStepsTable";
import { PageHeader } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type { RobotResponse, TaskEventResponse, TaskResponse } from "../../types/api";

export function TaskDetailPage() {
  const { taskId } = useParams<{ taskId: string }>();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["tasks", taskId] });
    queryClient.invalidateQueries({ queryKey: ["tasks", taskId, "events"] });
  };

  const taskQuery = useQuery({
    queryKey: ["tasks", taskId],
    queryFn: () => api.get<TaskResponse>(`/api/admin/tasks/${taskId}`),
  });

  const eventsQuery = useQuery({
    queryKey: ["tasks", taskId, "events"],
    queryFn: () => api.get<TaskEventResponse[]>(`/api/admin/tasks/${taskId}/events`),
  });

  const robotsQuery = useQuery({
    queryKey: ["robots"],
    queryFn: () => api.get<RobotResponse[]>("/api/admin/robots"),
  });

  const onError = (err: unknown) => setError(err instanceof ApiError ? err.message : "요청이 실패했습니다.");

  const assign = useMutation({
    mutationFn: (robotId: number) => api.post<TaskResponse>(`/api/admin/tasks/${taskId}/assign`, { robotId }),
    onSuccess: () => {
      invalidate();
      setError(null);
    },
    onError,
  });

  const cancel = useMutation({
    mutationFn: () => api.post<TaskResponse>(`/api/admin/tasks/${taskId}/cancel`),
    onSuccess: () => {
      invalidate();
      setError(null);
    },
    onError,
  });

  const recover = useMutation({
    mutationFn: (resolution: "RESUME" | "FAIL") => api.post<TaskResponse>(`/api/admin/tasks/${taskId}/recover`, { resolution }),
    onSuccess: () => {
      invalidate();
      setError(null);
    },
    onError,
  });

  const task = taskQuery.data;

  return (
    <div>
      <PageHeader
        title={task ? `작업 ${task.taskNo.slice(0, 8)}` : "작업 상세"}
        actions={
          <Link to="/tasks" className="text-sm text-indigo-600 hover:underline">
            ← 작업 목록
          </Link>
        }
      />

      {task && (
        <TaskActionsPanel
          task={task}
          robots={robotsQuery.data ?? []}
          onAssign={(robotId) => assign.mutate(robotId)}
          isAssigning={assign.isPending}
          onCancel={() => cancel.mutate()}
          isCanceling={cancel.isPending}
          onRecover={(resolution) => recover.mutate(resolution)}
          isRecovering={recover.isPending}
          error={error}
        />
      )}

      <div className="grid grid-cols-2 gap-4">
        <TaskStepsTable steps={task?.steps ?? []} />
        <TaskEventsTable events={eventsQuery.data ?? []} />
      </div>
    </div>
  );
}
