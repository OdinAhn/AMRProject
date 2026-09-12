import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { Badge, Button, Card, ErrorBanner, PageHeader, Select, Table, Td, Th } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type { RobotResponse, TaskEventResponse, TaskResponse } from "../../types/api";

export function TaskDetailPage() {
  const { taskId } = useParams<{ taskId: string }>();
  const queryClient = useQueryClient();
  const [robotId, setRobotId] = useState("");
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
    mutationFn: () => api.post<TaskResponse>(`/api/admin/tasks/${taskId}/assign`, { robotId: Number(robotId) }),
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
  const canAssign = task?.status === "QUEUED";
  const canCancel = ["QUEUED", "ASSIGNED", "RUNNING", "PAUSED"].includes(task?.status ?? "");
  const canRecover = task?.status === "RECOVERY_REQUIRED";

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
        <Card className="mb-4">
          <div className="mb-3 flex items-center gap-3">
            <Badge tone="blue">{task.status}</Badge>
            <span className="text-sm text-gray-500">{task.taskType}</span>
          </div>

          <div className="flex flex-wrap items-end gap-3">
            {canAssign && (
              <>
                <div className="w-56">
                  <Select value={robotId} onChange={(e) => setRobotId(e.target.value)}>
                    <option value="">배정할 로봇 선택</option>
                    {(robotsQuery.data ?? []).map((robot) => (
                      <option key={robot.id} value={robot.id}>
                        {robot.name} ({robot.code})
                      </option>
                    ))}
                  </Select>
                </div>
                <Button onClick={() => assign.mutate()} disabled={!robotId || assign.isPending}>
                  배정
                </Button>
              </>
            )}
            {canCancel && (
              <Button variant="danger" onClick={() => cancel.mutate()} disabled={cancel.isPending}>
                취소
              </Button>
            )}
            {canRecover && (
              <>
                <Button onClick={() => recover.mutate("RESUME")} disabled={recover.isPending}>
                  재개(RESUME)
                </Button>
                <Button variant="danger" onClick={() => recover.mutate("FAIL")} disabled={recover.isPending}>
                  최종 실패 처리(FAIL)
                </Button>
              </>
            )}
          </div>
          <ErrorBanner message={error} />
        </Card>
      )}

      <div className="grid grid-cols-2 gap-4">
        <Card>
          <h2 className="mb-3 text-sm font-semibold text-gray-700">단계</h2>
          <Table>
            <thead>
              <tr>
                <Th>#</Th>
                <Th>유형</Th>
                <Th>상태</Th>
              </tr>
            </thead>
            <tbody>
              {(task?.steps ?? []).map((step) => (
                <tr key={step.id}>
                  <Td>{step.stepNo}</Td>
                  <Td>{step.stepType}</Td>
                  <Td>
                    <Badge tone={step.status === "SUCCEEDED" ? "green" : step.status === "FAILED" ? "red" : "gray"}>
                      {step.status}
                    </Badge>
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>

        <Card>
          <h2 className="mb-3 text-sm font-semibold text-gray-700">이력</h2>
          <Table>
            <thead>
              <tr>
                <Th>시각</Th>
                <Th>이벤트</Th>
                <Th>전이</Th>
              </tr>
            </thead>
            <tbody>
              {(eventsQuery.data ?? []).map((event) => (
                <tr key={event.id}>
                  <Td className="whitespace-nowrap text-xs">{new Date(event.createdAt).toLocaleString()}</Td>
                  <Td>
                    {event.eventType}
                    {event.payload && <div className="text-xs text-gray-500">{event.payload}</div>}
                  </Td>
                  <Td className="text-xs">
                    {event.fromStatus ?? "-"} → {event.toStatus ?? "-"}
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      </div>
    </div>
  );
}
