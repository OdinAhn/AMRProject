import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { TaskActionsPanel } from "../../components/tasks/TaskActionsPanel";
import { TaskEventsTable } from "../../components/tasks/TaskEventsTable";
import { TaskStepsTable } from "../../components/tasks/TaskStepsTable";
import { PageHeader } from "../../components/ui";
import { api, errorMessage } from "../../lib/apiClient";
import { shortTaskNo } from "../../lib/labels";
import { useToast } from "../../lib/toastContext";
import type { RobotResponse, TaskEventResponse, TaskResponse } from "../../types/api";

export function TaskDetailPage() {
  const { taskId } = useParams<{ taskId: string }>();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
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

  const mutationHandlers = (successMessage: string) => ({
    onSuccess: () => {
      invalidate();
      setError(null);
      showToast(successMessage);
    },
    onError: (err: unknown) => setError(errorMessage(err)),
  });

  const assign = useMutation({
    mutationFn: (robotId: number) => api.post<TaskResponse>(`/api/admin/tasks/${taskId}/assign`, { robotId }),
    ...mutationHandlers("로봇을 배정했습니다."),
  });

  const cancel = useMutation({
    mutationFn: () => api.post<TaskResponse>(`/api/admin/tasks/${taskId}/cancel`),
    ...mutationHandlers("취소를 요청했습니다."),
  });

  const recover = useMutation({
    mutationFn: (resolution: "RESUME" | "FAIL") => api.post<TaskResponse>(`/api/admin/tasks/${taskId}/recover`, { resolution }),
    ...mutationHandlers("복구 처리를 반영했습니다."),
  });

  // 33단계
  const pause = useMutation({
    mutationFn: () => api.post<TaskResponse>(`/api/admin/tasks/${taskId}/pause`),
    ...mutationHandlers("작업을 일시정지했습니다."),
  });
  const resume = useMutation({
    mutationFn: () => api.post<TaskResponse>(`/api/admin/tasks/${taskId}/resume`),
    ...mutationHandlers("일시정지를 해제했습니다."),
  });
  const changePriority = useMutation({
    mutationFn: (priority: number) => api.patch<TaskResponse>(`/api/admin/tasks/${taskId}/priority`, { priority }),
    ...mutationHandlers("우선순위를 변경했습니다."),
  });

  const task = taskQuery.data;

  return (
    <div>
      <PageHeader
        title={task ? `작업 ${shortTaskNo(task.taskNo)}` : "작업 상세"}
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
          onPause={() => pause.mutate()}
          onResume={() => resume.mutate()}
          onChangePriority={(priority) => changePriority.mutate(priority)}
          isChangingFlow={pause.isPending || resume.isPending || changePriority.isPending}
          error={error}
        />
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <TaskStepsTable steps={task?.steps ?? []} />
        <TaskEventsTable events={eventsQuery.data ?? []} />
      </div>
    </div>
  );
}
